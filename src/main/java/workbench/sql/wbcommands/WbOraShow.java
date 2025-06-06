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
package workbench.sql.wbcommands;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.JdbcUtils;
import workbench.db.oracle.OracleErrorInformationReader;
import workbench.db.oracle.OracleUtils;

import workbench.storage.DataStore;

import workbench.sql.ErrorDescriptor;
import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;
import workbench.sql.lexer.SQLLexer;
import workbench.sql.lexer.SQLLexerFactory;
import workbench.sql.lexer.SQLToken;

import workbench.util.CaseInsensitiveComparator;
import workbench.util.CollectionUtil;
import workbench.util.DdlObjectInfo;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * An implementation of various SQL*Plus "show" commands.
 *
 * Currently supported commands:
 * <ul>
 *    <li>parameters</li>
 *    <li>spparameters</li>
 *    <li>pdbs</li>
 *    <li>con_name</li>
 *    <li>con_id</li>
 *    <li>logsource</li>
 *    <li>edition</li>
 *    <li>user</li>
 *    <li>appinfo</li>
 *    <li>errors</li>
 *    <li>sga</li>
 *    <li>sgainfo (non-standard)</li>
 *    <li>recyclebin</li>
 *    <li>autocommit</li>
 *    <li>version</li>
 *    <li>release</li>
 * </ul>
 *
 * @author Thomas Kellerer
 */
public class WbOraShow
  extends SqlCommand
{
  public static final String VERB = "SHOW";

  private final long ONE_KB = 1024;
  private final long ONE_MB = ONE_KB * 1024;

  private final Set<String> types = CollectionUtil.caseInsensitiveSet(
    "FUNCTION", "PROCEDURE", "PACKAGE", "PACKAGE BODY", "TRIGGER", "VIEW", "TYPE", "TYPE BODY", "DIMENSION",
    "JAVA SOURCE", "JAVA CLASS");

  private final Map<String, String> propertyUnits = new TreeMap<>(CaseInsensitiveComparator.INSTANCE);

  public WbOraShow()
  {
    propertyUnits.put("result_cache_max_size", "kb");
    propertyUnits.put("sga_max_size", "mb");
    propertyUnits.put("sga_target", "mb");
    propertyUnits.put("memory_max_target", "mb");
    propertyUnits.put("memory_target", "mb");
    propertyUnits.put("db_recovery_file_dest_size", "mb");
    propertyUnits.put("db_recycle_cache_size", "mb");
    propertyUnits.put("db_cache_size", "mb");
    propertyUnits.put("result_cache_max_size", "mb");
    propertyUnits.put("java_pool_size", "mb");
    propertyUnits.put("pga_aggregate_target", "mb");
  }

  @Override
  public StatementRunnerResult execute(String sql)
    throws SQLException, Exception
  {
    StatementRunnerResult result = new StatementRunnerResult(sql);

    String clean = getCommandLine(sql);
    SQLLexer lexer = SQLLexerFactory.createLexer(currentConnection, clean);
    SQLToken token = lexer.getNextToken(false, false);
    if (token == null)
    {
      result.addErrorMessage(getErrorMessage());
      return result;
    }
    String verb = token.getText().toLowerCase();
    if (verb.startsWith("parameter"))
    {
      SQLToken name = lexer.getNextToken(false, false);
      String parm = null;
      if (name != null)
      {
        parm = clean.substring(name.getCharBegin());
      }
      return getParameterValues(parm, false);
    }
    if (verb.startsWith("spparameter"))
    {
      SQLToken name = lexer.getNextToken(false, false);
      String parm = null;
      if (name != null)
      {
        parm = clean.substring(name.getCharBegin());
      }
      return getParameterValues(parm, true);
    }
    else if (verb.equals("sga"))
    {
      return getSGAInfo(true);
    }
    else if (verb.equals("sgainfo"))
    {
      return getSGAInfo(false);
    }
    else if (verb.equals("logsource"))
    {
      return getLogSource();
    }
    else if (verb.equals("recyclebin"))
    {
      return showRecycleBin();
    }
    else if (verb.equals("user"))
    {
      result.addMessage("USER is " + currentConnection.getCurrentUser());
    }
    else if (verb.equals("appinfo"))
    {
      return getAppInfo(sql);
    }
    else if (verb.equals("autocommit"))
    {
      if (currentConnection.getAutoCommit())
      {
        result.addMessage("autocommit ON");
      }
      else
      {
        result.addMessage("autocommit OFF");
      }
    }
    else if (verb.startsWith("error"))
    {
      return getErrors(lexer, sql);
    }
    else if (verb.equals("release") || verb.equals("version"))
    {
      return showVersion();
    }
    else if (verb.equals("edition"))
    {
      return showUserEnv("SESSION_EDITION_NAME", "EDITION");
    }
    else if (verb.equals("pdbs") && JdbcUtils.hasMinimumServerVersion(currentConnection, "12.0"))
    {
      return showPdbs();
    }
    else if (verb.startsWith("con_") && JdbcUtils.hasMinimumServerVersion(currentConnection, "12.0"))
    {
      return showUserEnv(verb);
    }
    else
    {
      result.addMessage(getErrorMessage());
      result.setFailure();
    }
    return result;
  }

  private String getErrorMessage()
  {
    String msg = ResourceMgr.getString("ErrOraShow");
    if (JdbcUtils.hasMinimumServerVersion(currentConnection, "12.1"))
    {
      msg += "\n" +
        "- pdbs\n" +
        "- con_name\n" +
        "- con_id";
    }
    return msg;
  }

  private StatementRunnerResult showUserEnv(String attribute)
  {
    return showUserEnv(attribute, attribute);
  }

  private StatementRunnerResult showVersion()
  {
    String query;

    if (JdbcUtils.hasMinimumServerVersion(currentConnection, "18.0"))
    {
      query = "select version_full from product_component_version where upper(product) like 'ORACLE%'";
    }
    else
    {
      query = "select version from product_component_version where upper(product) like 'ORACLE%'";
    }


    StatementRunnerResult result = new StatementRunnerResult("SHOW release");
    DataStore ds = SqlUtil.getResult(currentConnection, query);
    if (ds.getRowCount() > 0)
    {
      result.addMessage("Release " + ds.getValueAsString(0, 0));
    }
    else
    {
      result.addMessage(currentConnection.getDatabaseProductVersion());
    }
    return result;
  }

  private StatementRunnerResult showUserEnv(String attribute, String displayName)
  {
    String query = "select sys_context('userenv', '" + attribute.toUpperCase()+ "') as " + displayName + " from dual";

    StatementRunnerResult result = new StatementRunnerResult("SHOW " + attribute);
    try
    {
      DataStore ds = SqlUtil.getResultData(currentConnection, query, false);
      ds.setResultName(displayName.toUpperCase());
      result.addDataStore(ds);
    }
    catch (Exception ex)
    {
      result.addErrorMessage(ex.getMessage());
    }
    return result;
  }

  private StatementRunnerResult showPdbs()
  {
    StatementRunnerResult result = new StatementRunnerResult("SHOW pdbs");

    try
    {
      DataStore ds = OracleUtils.getPDBs(currentConnection);
      ds.setResultName("PDBS");
      result.addDataStore(ds);
    }
    catch (SQLException ex)
    {
      result.addErrorMessage(ex.getMessage());
    }
    return result;
  }

  private StatementRunnerResult showRecycleBin()
  {
    StatementRunnerResult result = new StatementRunnerResult("SHOW RECYCLEBIN");
    result.ignoreUpdateCounts(true);
    String sql =
      "SELECT original_name as \"ORIGINAL NAME\", \n" +
      "       object_name as \"RECYCLEBIN NAME\", \n" +
      "       type as \"OBJECT TYPE\", \n" +
      "       droptime as \"DROP TIME\" \n" +
      "FROM user_recyclebin \n" +
      "WHERE can_undrop = 'YES' \n" +
      "ORDER BY original_name, \n" +
      "         droptime desc, \n" +
      "         object_name";

    ResultSet rs = null;

    try
    {
      currentStatement = this.currentConnection.createStatementForQuery();
      rs = currentStatement.executeQuery(sql);
      processResults(result, true, rs);
      if (result.hasDataStores() && result.getDataStores().get(0).getRowCount() == 0)
      {
        result.clear();
        result.addMessageByKey("MsgRecyclebinEmpty");
      }
      result.setSuccess();
    }
    catch (SQLException ex)
    {
      result.addErrorMessage(ex.getMessage());
    }
    finally
    {
      JdbcUtils.closeAll(rs, currentStatement);
    }
    return result;
  }

  private StatementRunnerResult getErrors(SQLLexer lexer, String sql)
  {
    StatementRunnerResult result = new StatementRunnerResult(sql);

    SQLToken token = lexer.getNextToken(false, false);

    String schema = null;
    String object = null;
    String type = null;

    if (token != null && types.contains(token.getText()))
    {
      type = token.getContents();
      token = lexer.getNextToken(false, false);
    }

    if (token != null)
    {
      String v = token.getText();
      int pos = v.indexOf('.');

      if (pos > 0)
      {
        schema = v.substring(0, pos - 1);
        object = v.substring(pos);
      }
      else
      {
        object = v;
      }
    }

    if (object == null)
    {
      DdlObjectInfo info = currentConnection.getLastDdlObjectInfo();
      if (info != null)
      {
        object = info.getObjectName();
        type = info.getObjectType();
      }
    }

    ErrorDescriptor errors = null;

    if (object != null)
    {
      OracleErrorInformationReader reader = new OracleErrorInformationReader(currentConnection);
      errors = reader.getErrorInfo(null, schema, object, type, true);
    }

    if (errors != null)
    {
      result.addMessage(errors.getErrorMessage());
    }
    else
    {
      result.addMessageByKey("TxtOraNoErr");
    }
    return result;
  }

  private StatementRunnerResult getAppInfo(String sql)
  {
    String query = "SELECT module FROM v$session WHERE audsid = USERENV('SESSIONID')";
    Statement stmt = null;
    ResultSet rs = null;
    StatementRunnerResult result = new StatementRunnerResult(sql);

    try
    {
      stmt = this.currentConnection.createStatementForQuery();
      rs = stmt.executeQuery(query);
      if (rs.next())
      {
        String appInfo = rs.getString(1);
        if (appInfo == null)
        {
          result.addMessage("appinfo is OFF");
        }
        else
        {
          result.addMessage("appinfo is \"" + appInfo + "\"");
        }
      }
    }
    catch (SQLException ex)
    {
      result.addErrorMessage(ex.getMessage());
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return result;
  }

  private StatementRunnerResult getParameterValues(String parameter, boolean useSpParam)
  {
    boolean hasDisplayValue = JdbcUtils.hasMinimumServerVersion(currentConnection, "10.0");
    boolean useDisplayValue = Settings.getInstance().getBoolProperty("workbench.db.oracle.showparameter.display_value", hasDisplayValue);

    int nameIndex = 0;
    int valueIndex = 2;

    String query;

    if (useSpParam)
    {
      query =
        "select sid, \n" +
        "       name,  \n" +
        "       type,  \n" +
        "       display_value as value, \n" +
        "       update_comment \n" +
        "from v$spparameter \n";
      nameIndex = 0;
      valueIndex = 2;
    }
    else
    {
      query =
        "select name,  \n" +
        "       case type \n" +
        "         when 1 then 'boolean'  \n" +
        "         when 2 then 'string' \n" +
        "         when 3 then 'integer' \n" +
        "         when 4 then 'parameter file' \n" +
        "         when 5 then 'reserved' \n" +
        "         when 6 then 'big integer' \n" +
        "         else to_char(type) \n" +
        "       end as type,  \n" +
        "       " + (useDisplayValue ? "display_value" : "value") + " as value, \n" +
        "       description, \n" +
        "       update_comment \n" +
        "from v$parameter \n ";
    }

    ResultSet rs = null;

    List<String> names = StringUtil.stringToList(parameter, ",", true, true, false, false);

    if (names.size() > 0)
    {
      query += "where";

      for (int i = 0; i < names.size(); i++)
      {
        if (i > 0) query += "  or";
        query += " name like lower('%" + names.get(i) + "%') \n";
      }
    }
    query += "order by name";
    StatementRunnerResult result = new StatementRunnerResult(query);
    result.ignoreUpdateCounts(true);

    LogMgr.logMetadataSql(new CallerInfo(){}, "system parameters", query);

    try
    {
      // processResults needs currentStatement
      currentStatement = this.currentConnection.createStatementForQuery();
      rs = currentStatement.executeQuery(query);
      processResults(result, true, rs);
      if (result.hasDataStores())
      {
        DataStore ds = result.getDataStores().get(0);
        for (int row = 0; row < ds.getRowCount(); row++)
        {
          String property = ds.getValueAsString(row, nameIndex);
          String value = ds.getValueAsString(row, valueIndex);
          if (!useDisplayValue)
          {
            String formatted = formatMemorySize(property, value);
            if (formatted != null)
            {
              ds.setValue(row, 2, formatted);
            }
          }
        }
        ds.resetStatus();
      }
    }
    catch (SQLException ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "system parameters", query);
      result.addErrorMessage(ex.getMessage());
    }
    finally
    {
      JdbcUtils.closeAll(rs, currentStatement);
    }
    return result;
  }

  public static final List<String> getOptions()
  {
    return CollectionUtil.arrayList(
      "appinfo",
      "autocommit",
      "con_id",
      "con_name",
      "edition",
      "error",
      "logsource",
      "parameters",
      "pdbs",
      "recyclebin",
      "sga",
      "sgainfo",
      "spparameters",
      "user",
      "release"
    );
  }

  @Override
  public String getVerb()
  {
    return VERB;
  }

  protected String formatMemorySize(String property, String value)
  {
    String unit = propertyUnits.get(property);
    if (unit == null) return null;
    try
    {
      long lvalue = Long.parseLong(value);
      if (lvalue == 0) return null;

      if ("kb".equals(unit))
      {
        return Long.toString(roundToKb(lvalue)) + "K";
      }
      if ("mb".equals(unit))
      {
        return Long.toString(roundToMb(lvalue)) + "M";
      }
    }
    catch (NumberFormatException nfe)
    {
    }
    return null;
  }

  protected StatementRunnerResult getLogSource()
  {
    StatementRunnerResult result = new StatementRunnerResult();

    String sql =
      "SELECT destination \n" +
      "FROM v$archive_dest \n " +
      "WHERE status = 'VALID'";

    Statement stmt = null;
    ResultSet rs = null;

    LogMgr.logMetadataSql(new CallerInfo(){}, "log source", sql);

    try
    {
      stmt = this.currentConnection.createStatementForQuery();
      rs = stmt.executeQuery(sql);
      DataStore ds = new DataStore(new String[] {"LOGSOURCE", "VALUE"}, new int[] {Types.VARCHAR, Types.VARCHAR});
      while (rs.next())
      {
        String dest = rs.getString(1);
        if ("USE_DB_RECOVERY_FILE_DEST".equals(dest))
        {
          dest = "";
        }
        int row = ds.addRow();
        ds.setValue(row, 0, "LOGSOURCE");
        ds.setValue(row, 1, dest);
      }
      ds.setGeneratingSql("-- show logsource\n" + sql);
      ds.setResultName("LOGSOURCE");
      ds.resetStatus();
      result.addDataStore(ds);
      result.setSuccess();
    }
    catch (SQLException ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "log source", sql);
      result.addErrorMessage(ex.getMessage());
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return result;
  }

  protected StatementRunnerResult getSGAInfo(boolean sqlPlusMode)
  {
    StatementRunnerResult result = new StatementRunnerResult();

    String sql = null;
    if (sqlPlusMode)
    {
      sql =
        "SELECT 'Total System Global Area' as \"Memory\", \n" +
        "       sum(value) as \"Value\", \n" +
        "       'bytes' as unit \n" +
        "FROM v$sga \n" +
        "UNION ALL \n" +
        "SELECT name, \n" +
        "       value, \n" +
        "       'bytes' \n" +
        "FROM v$sga";
    }
    else
    {
      sql = "SELECT *\nFROM v$sgainfo";
    }

    LogMgr.logMetadataSql(new CallerInfo(){}, "SGA info", sql);

    try
    {
      DataStore ds = SqlUtil.getResultData(currentConnection, sql, false);
      ds.setGeneratingSql(sql);
      ds.setResultName("SGA Size");
      result.addDataStore(ds);
      result.setSuccess();
    }
    catch (SQLException ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "SGA info", sql);
      result.addErrorMessage(ex.getMessage());
    }
    return result;
  }

  private long roundToKb(long input)
  {
    if (input < ONE_KB) return input;
    return input / ONE_KB;
  }

  private long roundToMb(long input)
  {
    if (input < ONE_MB) return input;
    return input / ONE_MB;
  }

}
