/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
 * that restricts the use for certain governments.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at.
 *
 *     https://www.sql-workbench.eu/manual/license.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.eu
 *
 */
package workbench.db.oracle;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.JdbcUtils;
import workbench.db.WbConnection;

import workbench.storage.DataStore;

import workbench.sql.StatementHook;
import workbench.sql.StatementRunner;
import workbench.sql.StatementRunnerResult;
import workbench.sql.annotations.ResultNameAnnotation;
import workbench.sql.commands.SetCommand;
import workbench.sql.commands.TransactionEndCommand;
import workbench.sql.lexer.SQLLexer;
import workbench.sql.lexer.SQLLexerFactory;
import workbench.sql.lexer.SQLToken;
import workbench.sql.parser.ParserType;
import workbench.sql.wbcommands.CommandTester;

import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * A StatementHook that mimic's the autotrace feature from SQL*PLus.
 *
 * @author Thomas Kellerer
 */
public class OracleStatementHook
  implements StatementHook
{

  private static final String RETRIEVE_STATS =
      "-- SQL Workbench/J \n " +
      "select a.name, coalesce(s.value,0) as value, s.statistic# \n" +
      "from v$sesstat s \n" +
      "  join v$statname a on a.statistic# = s.statistic# \n" +
      "where sid = userenv('SID') \n" +
      "and a.name in";

  public static final List<String> DEFAULT_STAT_NAMES = CollectionUtil.arrayList(
    "recursive calls",
    "db block gets",
    "consistent gets",
    "physical reads",
    "redo size",
    "bytes sent via SQL*Net to client",
    "bytes received via SQL*Net from client",
    "SQL*Net roundtrips to/from client",
    "sorts (memory)",
    "sorts (disk)\n, " +
    "db block changes, \n " +
    "consistent gets from cache",
    "consistent gets from cache (fastpath)",
    "logical read bytes from cache, \n " +
    "Requests to/from client",
    "session logical reads");

  /**
   * A list of statistic names formatted to be used inside an IN clause.
   */
  private static final String DEFAULT_STATS = StringUtil.listToString(DEFAULT_STAT_NAMES, ",", true, '\'');

  // See: https://docs.oracle.com/cd/E11882_01/server.112/e26088/statements_9010.htm#SQLRF54985
  private static final Set<String> EXPLAINABLE = CollectionUtil.caseInsensitiveSet("SELECT", "INSERT", "UPDATE", "DELETE", "CREATE", "ALTER");

  /**
   * A list of SQL commands where no statistics should be shown.
   */
  private final Set<String> noStatistics = CollectionUtil.caseInsensitiveSet(SetCommand.VERB,
    TransactionEndCommand.COMMIT_VERB, TransactionEndCommand.ROLLBACK_VERB, "SET", "SHOW", "EXPLAIN");

  /**
   * Stores the statistic values before the execution of the statement.
   */
  private Map<String, Long> values;

  /** flag to indicate if the statisticsviews are available */
  private boolean statisticViewsAvailable;

  /** if true, autotrace is turned on */
  private boolean autotrace;

  private boolean traceOnly;
  private boolean showExecutionPlan;
  private boolean showRealPlan;
  private boolean showStatistics;

  private String lastExplainID;

  private String lastStatisticsLevel;

  private PreparedStatement statisticsStmt;
  private final Object lock = new Object();
  private final CommandTester wbTester = new CommandTester();
  private final SQLLexer lexer = SQLLexerFactory.createLexer(ParserType.Oracle, "");

  public OracleStatementHook()
  {
  }

  @Override
  public String preExec(StatementRunner runner, String sql)
  {
    if (!autotrace || !shouldTraceStatement(sql))
    {
      return sql;
    }

    lastExplainID = null;

    if (showStatistics)
    {
      storeSessionStats(runner);
    }

    if (showRealPlan)
    {
      sql = adjustSql(sql);
      LogMgr.logDebug(new CallerInfo(){}, "Using modified SQL to display the real execution plan:\n" + sql);
    }

    return sql;
  }

  private boolean useStatisticsHint()
  {
    return Settings.getInstance().getBoolProperty("workbench.db.oracle.realplan.usehint", true);
  }

  private boolean alwaysUsePrefix()
  {
    return Settings.getInstance().getBoolProperty("workbench.db.oracle.realplan.always.inject.id", true);
  }

  @Override
  public boolean isPending()
  {
    return autotrace;
  }

  @Override
  public void postExec(StatementRunner runner, String sql, StatementRunnerResult result)
  {
    checkRunnerSession(runner, sql);
    if (!autotrace || !shouldTraceStatement(sql))
    {
      return;
    }

    // Retrieving statistics MUST be called before retrieving the execution plan!
    // Otherwise the statistics for retrieving the plan will be counted as well!
    if (showStatistics)
    {
      if (!statisticViewsAvailable)
      {
        result.addWarningByKey("ErrNoAutoTrace");
      }
      else
      {
        DataStore stats = retrieveStatistics(runner, sql);
        if (stats != null)
        {
          int valueCol = getValueColIndex();
          int nameCol = getNameColIndex();

          long rows = result.getRowsProcessed();
          int row = stats.addRow();
          stats.setValue(row, nameCol, "rows processed");
          stats.setValue(row, valueCol, rows);
          stats.setGeneratingSql(sql);
          stats.resetStatus();
          result.addDataStore(stats);
        }
      }
    }

    DataStore plan = null;
    if (showRealPlan)
    {
      plan = retrieveRealExecutionPlan(runner, sql);
    }

    if (showExecutionPlan && plan == null)
    {
      plan = retrieveExecutionPlan(runner, sql);
    }

    if (plan != null)
    {
      // If the result data is not shown, use the result name
      // for the execution plan
      if (traceOnly)
      {
        ResultNameAnnotation.setResultName(plan, sql);
      }
      result.addDataStore(plan);
    }
  }

  /**
   * Change the SQL to be executed so that it can be found in V$SQL later.
   *
   * In order to be able to find the SQL statement in postExec() a comment with a unique marker string is added to the
   * front of the statement so that it can correctly found in V$SQL and V$SQL_PLAN.
   *
   * Additionally the gather_plan_statistics hint is added to get detailed information
   * in the generated plan.
   *
   * @param sql  the sql to execute
   * @return the changed sql
   * @see #getIDPrefix()
   */
  private String adjustSql(String sql)
  {
    lastExplainID = UUID.randomUUID().toString();

    if (useStatisticsHint())
    {
      sql = injectHint(sql);
    }

    if (showStatistics || alwaysUsePrefix())
    {
      // if statistics should be displayed we have to get the execution plan
      // after retrieving the statistics. In that case we must make the SQL "identifiable" using the prefix
      return getIDPrefix() + "\n" + sql;
    }

    // if no statistics are required we can use dbms_xplan() without parameters to get the plan
    // of the last statement (as our own statistics retrieval will not be the "last" statement
    return sql;
  }

  /**
   * Inject the gather_plan_statistics hint into the SQL.
   *
   * The hint is injected after the first verb of the statement.
   * For CTEs it is injected to the main query i.e. the one following the CTE.
   *
   * @param sql
   * @return the SQL string with the hint injected
   */
  protected String injectHint(String sql)
  {
    boolean addComment = false;
    int pos = -1;

    SQLToken verb = null;
    SQLToken secondElement = null;
    synchronized (lexer)
    {
      lexer.setInput(sql);
      verb = lexer.getNextToken(false, false);
      if (verb == null) return getIDPrefix() + "  " + sql;
      if ("with".equalsIgnoreCase(verb.getContents()))
      {
        verb = SqlUtil.skipCTE(lexer);
      }
      secondElement = lexer.getNextToken(true, false);
    }

    if (secondElement == null) return getIDPrefix() + "  " + sql;

    addComment = true;
    if (secondElement.isComment())
    {
      String comment = secondElement.getContents();
      if (comment.startsWith("/*+"))
      {
        addComment = false;
        pos = secondElement.getCharBegin();
      }
    }

    if (pos < 0)
    {
      pos = verb.getCharEnd();
    }

    if (pos < 0) return sql;

    if (addComment)
    {
      // no comment with a hint found
      sql = sql.substring(0, pos) + " /*+ gather_plan_statistics */ " + sql.substring(pos + 1);
    }
    else if (sql.indexOf("gather_plan_statistics", pos) < 0)
    {
      sql = sql.substring(0, pos + 3) + " gather_plan_statistics " + sql.substring(pos + 3);
    }

    // if no statistics are required we can use dbms_xplan() without parameters to get the plan
    // of the last statement (as our own statistics retrieval will not be the "last" statement
    return sql;
  }


  private String getIDPrefix()
  {
    if (lastExplainID == null)
    {
      return "";
    }
    return "-- wb$" + lastExplainID;
  }

  private void storeSessionStats(StatementRunner runner)
  {
    WbConnection con = runner.getConnection();
    values = new HashMap<>(10);

    ResultSet rs = null;
    try
    {
      synchronized (lock)
      {
        prepareStatisticsStatement(con);
        rs = statisticsStmt.executeQuery();

        while (rs.next())
        {
          String key = rs.getString(1);
          long val = rs.getLong(2);
          values.put(key, val);
        }
        statisticViewsAvailable = true;
      }
    }
    catch (SQLException ex)
    {
      if (ex.getErrorCode() == 942)
      {
        statisticViewsAvailable = false;
      }
      LogMgr.logError(new CallerInfo(){}, "Could not retrieve session statistics", ex);
    }
    finally
    {
      JdbcUtils.closeResult(rs);
    }
  }

  private String buildStatisticsQuery()
  {
    String stats = Settings.getInstance().getProperty("workbench.db.oracle.autotrace.statname", DEFAULT_STATS);
    return RETRIEVE_STATS + " (" + stats + ") \n ORDER BY lower(a.name)";
  }

  private boolean showStatvalueFirst()
  {
    return Settings.getInstance().getBoolProperty("workbench.db.oracle.autotrace.statistics.valuefirst", true);
  }

  private boolean shouldTraceStatement(String sql)
  {
    synchronized (lexer)
    {
      lexer.setInput(sql);
      SQLToken verb = lexer.getNextToken(false, false);
      if (verb == null) return false;
      String sqlVerb = verb.getContents();
      if (noStatistics.contains(sqlVerb))
      {
        return false;
      }
      return true;
    }
  }

  /**
   * Retrieve the real execution plan of the last statement using dbms_xplan.display_cursor().
   *
   * If statements statistics are requested (set autotrace statistics ...) then it is expected
   * that the statement has been "marked" using getIDPrefix(). This method will then search v$sql
   * for that prefix to find the correct sql_id. dbms_xplan.display_cursor() is then called
   * with a SQL_ID and a child number
   *
   * If no statistics should be displayed, dbms_xplan.display_cursor() is then called with the
   * format parameter only.
   *
   * @param runner
   * @param sql the sql that was executed
   * @return the execution plan, might be null if the SQL statement was not found in v$sql
   */
  private DataStore retrieveRealExecutionPlan(StatementRunner runner, String sql)
  {
    WbConnection con = runner.getConnection();

    PreparedStatement planStatement = null;
    Statement stmt = null;
    ResultSet rs = null;
    DataStore result = null;

    String defaultOptions = "PARTITION ALIAS BYTES COST NOTE ROWS ALLSTATS LAST";
    String options = Settings.getInstance().getProperty("workbench.db.oracle.xplan.options", defaultOptions);
    if (StringUtil.isEmpty(options))
    {
      options = defaultOptions;
    }

    CallerInfo ci = new CallerInfo(){};

    boolean searchSQL = false;
    try
    {
      String retrievePlan;

      if ((alwaysUsePrefix() || showStatistics) && lastExplainID != null)
      {
        // if statistics were retrieved, the last statement was the statistic retrieval.
        // Therefor we have to find the SQL_ID for the statement that was executed.
        retrievePlan = "SELECT * FROM table(dbms_xplan.display_cursor(?, ?, ?))";
        searchSQL = true;
      }
      else
      {
        // if statistics were not retrieved, there is no need to search V$SQL (which is quite expensive)
        retrievePlan = "SELECT * FROM table(dbms_xplan.display_cursor(format => ?))";
        searchSQL = false;
      }

      planStatement = con.getSqlConnection().prepareStatement(retrievePlan);

      stmt = con.createStatementForQuery();
      if (searchSQL)
      {
        String findSql =
          "-- SQL Workbench/J \n " +
          "select sql.sql_id, sql.child_number \n" +
          "from v$sql sql \n" +
          "where sql_text like '" + getIDPrefix() + "%' \n" +
          "order by last_active_time desc";

        LogMgr.logDebug(ci, "SQL to find last explained statement: \n" + findSql);
        rs = stmt.executeQuery(findSql);
        if (rs.next())
        {
          String sqlid = rs.getString(1);
          int childNumber = rs.getInt(2);
          planStatement.setString(1, sqlid);
          planStatement.setInt(2, childNumber);
          planStatement.setString(3, options);
          JdbcUtils.closeResult(rs);
          LogMgr.logDebug(ci, "Getting plan for sqlid=" + sqlid + ", child=" + childNumber + " using:\n" +
            SqlUtil.replaceParameters(retrievePlan, sqlid, childNumber, options));
        }
      }
      else
      {
        planStatement.setString(1, options);
        LogMgr.logDebug(ci, "Retrieving execution plan for last SQL using:\n" + SqlUtil.replaceParameters(retrievePlan, options));
      }

      rs = planStatement.executeQuery();
      result = new DataStore(rs, true);
      result.setGeneratingSql(sql);
      result.setResultName("Execution plan");
      result.resetStatus();
    }
    catch (SQLException ex)
    {
      LogMgr.logError(ci, "Could not retrieve real execution plan", ex);
    }
    finally
    {
      JdbcUtils.closeStatement(planStatement);
      JdbcUtils.closeAll(rs, stmt);
    }
    return result;
  }

  private void changeStatisticsLevel(WbConnection con, String newLevel)
  {
    final CallerInfo ci = new CallerInfo(){};
    Statement stmt = null;
    try
    {
      if (StringUtil.isEmpty(newLevel))
      {
        // should not happen, but just in case.
        newLevel = "TYPICAL";
      }
      LogMgr.logInfo(ci, "Setting STATISTICS_LEVEL to " + newLevel);
      stmt = con.createStatement();
      stmt.execute("alter session set statistics_level=" + newLevel);
    }
    catch (SQLException ex)
    {
      LogMgr.logError(ci, "Could not enable statistics level: " + newLevel, ex);
    }
    finally
    {
      JdbcUtils.closeStatement(stmt);
    }
  }

  private DataStore retrieveExecutionPlan(StatementRunner runner, String sql)
  {
    if (!canExplain(sql))
    {
      return null;
    }

    WbConnection con = runner.getConnection();
    final CallerInfo ci = new CallerInfo(){};

    String explainSql = "EXPLAIN PLAN FOR " + sql;
    String retrievePlan = "SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY(format => 'TYPICAL ALIAS PROJECTION'))";

    LogMgr.logDebug(ci, "Running EXPLAIN PLAN for last SQL statement");

    Statement stmt = null;
    ResultSet rs = null;
    DataStore result = null;
    try
    {
      stmt = con.createStatementForQuery();
      stmt.execute(explainSql);
      rs = stmt.executeQuery(retrievePlan);
      result = new DataStore(rs, true);
      result.setGeneratingSql(sql);
      result.setResultName("Execution plan");
      result.resetStatus();
    }
    catch (SQLException ex)
    {
      LogMgr.logError(ci, "Could not retrieve session statistics", ex);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return result;
  }

  private int getNameColIndex()
  {
    return showStatvalueFirst() ? 1 : 0;
  }

  private int getValueColIndex()
  {
    return showStatvalueFirst() ? 0 : 1;
  }

  private void prepareStatisticsStatement(WbConnection con)
    throws SQLException
  {
    if (statisticsStmt == null)
    {
      statisticsStmt = con.getSqlConnection().prepareStatement(buildStatisticsQuery());
    }
  }

  private boolean skipStatistics(String verb)
  {
    if (wbTester.isWbCommand(verb)) return true;
    return noStatistics.contains(verb);
  }

  private DataStore retrieveStatistics(StatementRunner runner, String sql)
  {
    WbConnection con = runner.getConnection();
    if (con == null) return null;

    String verb = con.getParsingUtil().getSqlVerb(sql);

    if (skipStatistics(verb))
    {
      return null;
    }

    ResultSet rs = null;
    DataStore statValues = createResult();

    int valueCol = getValueColIndex();
    int nameCol = getNameColIndex();

    try
    {
      synchronized (lock)
      {
        prepareStatisticsStatement(con);
        rs = statisticsStmt.executeQuery();
        while (rs.next())
        {
          String statName = rs.getString(1);
          long value = rs.getLong(2);
          long startValue = nvl(values.get(statName));
          int row = statValues.addRow();
          statValues.setValue(row, nameCol, statName);
          statValues.setValue(row, valueCol, (value - startValue));
        }
      }
      statValues.setResultName("Statistics");
    }
    catch (SQLException ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not retrieve session statistics", ex);
    }
    finally
    {
      JdbcUtils.closeResult(rs);
    }
    return statValues;
  }

  private Long nvl(Long value)
  {
    if (value != null) return value;
    return Long.valueOf(0);
  }

  /**
   * Check if the SQL can be EXPLAIN'ed.
   *
   * See: https://docs.oracle.com/cd/E11882_01/server.112/e26088/statements_9010.htm#SQLRF54985

   * @param sql the sql to run
   * @return true if EXPLAIN PLAN supports the statement.
   */
  private boolean canExplain(String sql)
  {
    synchronized (lexer)
    {
      lexer.setInput(sql);
      SQLToken verb = lexer.getNextToken(false, false);
      if (verb == null) return false;
      if (!EXPLAINABLE.contains(verb.getContents())) return false;

      String sqlVerb = verb.getContents();
      if ("CREATE".equalsIgnoreCase(sqlVerb))
      {
        SQLToken type = lexer.getNextToken(false, false);
        if (type == null) return false;
        String typeName = type.getContents();
        return typeName.equalsIgnoreCase("TABLE") || typeName.equalsIgnoreCase("INDEX");
      }
      if ("ALTER".equalsIgnoreCase(sqlVerb))
      {
        SQLToken token = lexer.getNextToken(false, false);
        if (token == null) return false;
        token = lexer.getNextToken(false, false);
        if (token == null) return false;
        return "REBUILD".equalsIgnoreCase(token.getContents());
      }
      return true;
    }
  }

  @Override
  public boolean displayResults()
  {
    if (!autotrace) return true;
    return !traceOnly;
  }

  @Override
  public boolean fetchResults()
  {
    if (!autotrace) return true;
    if (traceOnly)
    {
      return showStatistics || showRealPlan;
    }
    return true;
  }

  private void checkRunnerSession(StatementRunner runner, String sql)
  {
    String trace = runner.getSessionAttribute("autotrace");
    if (trace == null)
    {
      if (autotrace)
      {
        // autotrace was turned off, so close the statistics statement.
        close(runner.getConnection());
      }
      autotrace = false;
      return;
    }
    Set<String> flags = CollectionUtil.caseInsensitiveSet();
    flags.addAll(StringUtil.stringToList(trace, ",", true, true, false, false));
    this.traceOnly = flags.contains("traceonly");
    this.autotrace = flags.contains("on") || traceOnly;
    this.showExecutionPlan =  flags.contains("explain") || (autotrace && flags.size() == 1);
    this.showStatistics = flags.contains("statistics")  || (autotrace && flags.size() == 1);
    this.showRealPlan =  autotrace && flags.contains("realplan");
    if (showRealPlan)
    {
      // enable "regular" explain plan as well in case retrieving the real plan doesn't work for some reason
      showExecutionPlan = showRealPlan;
      String verb = runner.getConnection().getParsingUtil().getSqlVerb(sql);
      if (!useStatisticsHint() && StringUtil.equalStringIgnoreCase("SET", verb))
      {
        if (this.lastStatisticsLevel == null)
        {
          this.lastStatisticsLevel = retrieveStatisticsLevel(runner.getConnection());
        }
        changeStatisticsLevel(runner.getConnection(), "ALL");
      }
    }
  }

  private DataStore createResult()
  {
    DataStore result = null;
    if (showStatvalueFirst())
    {
      String[] columnNames = new String[] { "VALUE", "STATISTIC" };
      int[] types = new int[] { Types.BIGINT, Types.VARCHAR };
      result = new DataStore(columnNames, types);
    }
    else
    {
      String[] columnNames = new String[] { "STATISTIC", "VALUE" };
      int[] types = new int[] { Types.VARCHAR, Types.BIGINT };
      result = new DataStore(columnNames, types);
    }
    return result;
  }

  @Override
  public void close(WbConnection conn)
  {
    JdbcUtils.closeStatement(statisticsStmt);
    statisticsStmt = null;
    lastExplainID = null;
    if (lastStatisticsLevel != null && conn != null)
    {
      changeStatisticsLevel(conn, lastStatisticsLevel);
      lastStatisticsLevel = null;
    }
  }

  private String retrieveStatisticsLevel(WbConnection conn)
  {
    CallableStatement cstmt = null;
    String level = null;
    String call = "{? = call dbms_utility.get_parameter_value('STATISTICS_LEVEL', ?, ?)}";
    CallerInfo ci = new CallerInfo(){};
    LogMgr.logMetadataSql(ci, "statistics level", call);

    try
    {
      cstmt = conn.getSqlConnection().prepareCall(call);
      cstmt.registerOutParameter(1, Types.INTEGER);
      cstmt.registerOutParameter(2, Types.INTEGER);
      cstmt.registerOutParameter(3, Types.VARCHAR);
      cstmt.execute();
      level = cstmt.getString(3);
      LogMgr.logDebug(ci, "Current level: " + level);
    }
    catch (SQLException sql)
    {
      LogMgr.logMetadataError(ci, sql, "statistics level", call);
    }
    return level;
  }
}
