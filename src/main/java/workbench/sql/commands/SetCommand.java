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
package workbench.sql.commands;

import java.sql.SQLException;
import java.util.List;
import java.util.Set;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.DBID;
import workbench.db.DbSearchPath;
import workbench.db.DbSettings;
import workbench.db.WbConnection;
import workbench.db.firebird.FirebirdStatementHook;
import workbench.db.oracle.OracleUtils;

import workbench.sql.SqlCommand;
import workbench.sql.StatementRunner;
import workbench.sql.StatementRunnerResult;
import workbench.sql.lexer.SQLLexer;
import workbench.sql.lexer.SQLLexerFactory;
import workbench.sql.lexer.SQLToken;
import workbench.sql.wbcommands.WbFetchSize;

import workbench.util.CollectionUtil;
import workbench.util.ExceptionUtil;
import workbench.util.StringUtil;

import static workbench.sql.wbcommands.WbEnableOraOutput.*;

/**
 * This class implements a wrapper for the SET command.
 *
 * Oracle's SET command is only valid from within SQL*Plus.
 * By supplying an implementation for the Workbench, we can ignore the errors
 * reported by the JDBC interface, so that SQL scripts intended for SQL*Plus
 * can also be run from within the workbench
 * <br/>
 * For other DBMS this enables the support for controlling autocommit
 * through a SQL command.
 *
 * @author  Thomas Kellerer
 */
public class SetCommand
  extends SqlCommand
{
  public static final String VERB = "SET";

  @Override
  public StatementRunnerResult execute(String userSql)
    throws SQLException
  {
    StatementRunnerResult result = null;

    String command = null;
    String param = null;
    try
    {
      SQLLexer l = SQLLexerFactory.createLexer(currentConnection, userSql);
      SQLToken t = l.getNextToken(false, false); // ignore the verb
      t = l.getNextToken(false, false);
      if (t != null)
      {
        command = t.getContents();
      }
      t = l.getNextToken(false, false);

      // Ignore a possible equal sign
      if (t != null && (t.getContents().equals("=") || t.getContents().equals("TO")))
      {
        t = l.getNextToken(false, false);
      }

      if (t != null)
      {
        param = userSql.substring(t.getCharBegin()).trim();
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not parse statement", e);
      result = new StatementRunnerResult();
      result.addErrorMessage(ExceptionUtil.getDisplay(e));
      return result;
    }

    boolean execSql = true;
    boolean schemaChange = false;

    if (command != null)
    {
      // those SET commands that have a SQL Workbench equivalent (JDBC API)
      // will be "executed" by calling the approriate functions.
      // We don't need to send the SQL to the server in this case
      // Any other command is sent to the server without modification.
      if (command.equalsIgnoreCase("autocommit"))
      {
        result = this.setAutocommit(currentConnection, param);
        execSql = false;
      }
      else if (command.equalsIgnoreCase("maxrows"))
      {
        result = new StatementRunnerResult();
        execSql = false;
        try
        {
          int rows = Integer.parseInt(param);
          this.runner.setMaxRows(rows);
          result.setSuccess();
          result.addMessageByKey("MsgSetSuccess", command, rows);
        }
        catch (Exception e)
        {
          result.addErrorMessageByKey("MsgSetFailure", param, command);
        }
      }
      else if (command.equalsIgnoreCase("timeout"))
      {
        result = new StatementRunnerResult();
        execSql = false;
        try
        {
          int timeout = Integer.parseInt(param);
          this.runner.setQueryTimeout(timeout);
          result.setSuccess();
          result.addMessageByKey("MsgSetSuccess", command, timeout);
        }
        catch (Exception e)
        {
          result.addErrorMessageByKey("MsgSetFailure", param, command);
        }
      }
      else if ((command.equalsIgnoreCase("schema") || command.equalsIgnoreCase("search_path")) && canChangeSchema())
      {
        schemaChange = true;
      }
      else if (command.equalsIgnoreCase("fetchsize"))
      {
        result = new StatementRunnerResult(userSql);
        WbFetchSize.setFetchSize(param, result, currentConnection);
        execSql = false;
      }
      else if (command.equalsIgnoreCase("TERM") && DBID.Firebird.isDB(currentConnection))
      {
        result = new StatementRunnerResult(userSql);
        result.setSuccess();
        execSql = false;
      }
      else if (DBID.Oracle.isDB(currentConnection))
      {
        Set<String> allowed = CollectionUtil.caseInsensitiveSet("constraints","constraint","transaction","role");
        List<String> options = Settings.getInstance().getListProperty("workbench.db.oracle.set.options", true, "");
        allowed.addAll(options);

        execSql = false;
        if (command.equalsIgnoreCase("serveroutput"))
        {
          result = this.setServeroutput(currentConnection, param);
        }
        else if (command.equalsIgnoreCase("feedback"))
        {
          result = this.setFeedback(param);
        }
        else if (command.equalsIgnoreCase("autotrace"))
        {
          result = handleAutotrace(param.trim());
        }
        else if (allowed.contains(command))
        {
          execSql = true;
        }
        else
        {
          result = new StatementRunnerResult();
          if (Settings.getInstance().getShowIgnoredWarning())
          {
            result.addMessageByKey("MsgCommandIgnored", getParsingUtil().getSqlVerb(userSql));
          }
          result.setSuccess();
        }
      }
      else if (DBID.Firebird.isDB(currentConnection))
      {
        result = new StatementRunnerResult(userSql);
        boolean handled = handleFirebird(result, userSql);
        execSql = !handled;
      }
    }

    if (!execSql)
    {
      return result;
    }

    try
    {
      String newSchemaArg = null;
      String oldSchema = null;
      if (schemaChange)
      {
        newSchemaArg = param;
        oldSchema = currentConnection.getCurrentSchema();
      }
      result = new StatementRunnerResult();
      result.ignoreUpdateCounts(true);
      String toExecute = getSqlToExecute(userSql);
      this.currentStatement = currentConnection.createStatement();

      // Using a generic execute ensures that DBMS which can process more than one statement with a single SQL
      // are treated correctly. E.g. when sending a SET and a SELECT as one statement for SQL Server
      boolean hasResult = this.currentStatement.execute(toExecute);
      processResults(result, hasResult);
      result.setSuccess();

      if (schemaChange)
      {
        String newSchema = handleSchemaChange(command, newSchemaArg, oldSchema);
        result.addMessageByKey("MsgSchemaChanged", newSchema);
      }
      else
      {
        appendSuccessMessage(result);
      }

    }
    catch (Exception e)
    {
      result = new StatementRunnerResult();
      result.clear();
      if (DBID.Oracle.isDB(currentConnection))
      {
        // for oracle we'll simply ignore the error as the SET command is a SQL*Plus command
        result.setSuccess();
        result.addWarning(ResourceMgr.getString("MsgSetErrorIgnored") + ": " + e.getMessage());
      }
      else
      {
        // for other DBMS this is an error
        result.addErrorMessage(e.getMessage());
      }
    }
    finally
    {
      this.done();
    }

    return result;
  }

  private boolean canChangeSchema()
  {
    if (currentConnection == null) return false;
    DbSettings set = currentConnection.getDbSettings();
    if (set == null) return false;
    return set.supportsSetSchema();
  }

  private boolean handleFirebird(StatementRunnerResult result, String sql)
  {
    SQLLexer lexer = SQLLexerFactory.createLexer(currentConnection, sql);
    SQLToken token = lexer.getNextToken(false, false);

    // ignore the first verb, that will be the SET
    token = lexer.getNextToken(false, false);
    if (token == null) return false;

    if (token.getText().equalsIgnoreCase("plan"))
    {
      token = lexer.getNextToken(false, false);
      boolean on = false;
      if (token == null)
      {
        on = !runner.getBoolSessionAttribute(FirebirdStatementHook.SESS_ATTR_SHOWPLAN);
      }
      else
      {
        on = token.getText().equalsIgnoreCase("ON");
      }
      runner.setSessionProperty(FirebirdStatementHook.SESS_ATTR_SHOWPLAN, Boolean.toString(on));
      String flagKey = on ? "TxtOn" : "TxtOff";
      result.addMessageByKey("MsgFbExecPlan", ResourceMgr.getString(flagKey));
      return true;
    }
    else if (token.getText().equalsIgnoreCase("planonly"))
    {
      token = lexer.getNextToken(false, false);
      token = lexer.getNextToken(false, false);
      boolean on = false;
      if (token == null)
      {
        on = !runner.getBoolSessionAttribute(FirebirdStatementHook.SESS_ATTR_PLAN_ONLY);
      }
      else
      {
        on = token.getText().equalsIgnoreCase("ON");
      }

      runner.setSessionProperty(FirebirdStatementHook.SESS_ATTR_PLAN_ONLY, Boolean.toString(on));

      if (on)
      {
        result.addMessageByKey("MsgFbExecPlanNoResult");
      }
      else
      {
        result.addMessageByKey("MsgFbExecPlanResult");
      }
      return true;
    }
    return false;
  }

  private String handleSchemaChange(String what, String schemaArg, String oldSchema)
  {
    boolean busy = currentConnection.isBusy();
    String result = null;
    try
    {
      // getCurrentSchema() will not work if the connection is marked as busy
      // the StatementRunner will set it to busy when calling execute()
      // so we need to clear it here.
      currentConnection.setBusy(false);
      currentConnection.getMetadata().clearCachedSchemaInformation();

      LogMgr.logDebug(new CallerInfo(){}, "Updating current schema");

      String newSchema = null;

      DbSearchPath pathHandler = DbSearchPath.Factory.getSearchPathHandler(currentConnection);
      if ("search_path".equalsIgnoreCase(what) && pathHandler.isRealSearchPath())
      {
        List<String> path = pathHandler.getSearchPath(currentConnection, null);
        if (path.size() > 1 && path.get(0).equals("pg_catalog") && !schemaArg.toLowerCase().contains("pg_catalog"))
        {
          path.remove("pg_catalog");
        }

        if (path.size() > 0)
        {
          newSchema = path.get(0);
        }
        result = StringUtil.listToString(path, ",", false);
      }
      if (newSchema == null)
      {
        newSchema = currentConnection.getCurrentSchema();
      }
      // this is for DBMS and JDBC drivers that don't properly support getCurrentSchema()
      // mainly Progress OpenEdge. We simply assume the new schema is the one supplied in the command
      if (newSchema == null)
      {
        newSchema = schemaArg;
      }

      if (result == null)
      {
        result = newSchema;
      }

      // schemaChanged will trigger an update of the ConnectionInfo
      // but that only retrieves the current schema if the connection isn't busy
      currentConnection.schemaChanged(oldSchema, newSchema);
    }
    finally
    {
      currentConnection.setBusy(busy);
    }
    return result;
  }

  private StatementRunnerResult handleAutotrace(String parameter)
  {
    StatementRunnerResult result = new StatementRunnerResult();
    result.setSuccess();
    if ("off".equalsIgnoreCase(parameter))
    {
      runner.removeSessionProperty("autotrace");
      result.addMessageByKey("MsgAutoTraceOff");
      return result;
    }
    List<String> flags = StringUtil.stringToList(parameter.toLowerCase(), " ");

    if (flags.contains("on") || flags.contains("traceonly") || flags.contains("trace"))
    {
      runner.setSessionProperty("autotrace", StringUtil.listToString(flags, ','));
      result.addMessageByKey("MsgAutoTraceOn");
    }
    else
    {
      result.addErrorMessageByKey("MsgAutoTraceUsage");
    }
    return result;
  }

  private StatementRunnerResult setServeroutput(WbConnection connection, String param)
  {
    StatementRunnerResult result = new StatementRunnerResult();
    result.setSuccess();

    if ("off".equalsIgnoreCase(param))
    {
      connection.getMetadata().disableOutput();
      if (OracleUtils.showSetServeroutputFeedback())
      {
        result.addMessageByKey("MsgDbmsOutputDisabled");
      }
      else
      {
        runner.removeSessionProperty(StatementRunner.SERVER_MSG_PROP);
      }
    }
    else if ("on".equalsIgnoreCase(param))
    {
      connection.getMetadata().enableOutput();
      if (OracleUtils.showSetServeroutputFeedback())
      {
        result.addMessageByKey("MsgDbmsOutputEnabled");
      }
      else
      {
        // if no feedback should be shown, behave like SQL*Plus and also don't show the
        // "Server output" label when displaying the messages
        runner.setSessionProperty(StatementRunner.SERVER_MSG_PROP, HIDE_HINT);
      }
    }
    else
    {
      result.addErrorMessageByKey("ErrServeroutputWrongParameter");
    }
    return result;
  }

  private StatementRunnerResult setAutocommit(WbConnection connection, String param)
  {
    StatementRunnerResult result = new StatementRunnerResult();

    if (StringUtil.isEmpty(param))
    {
      result.addErrorMessageByKey("ErrAutocommitWrongParameter");
      return result;
    }

    Set<String> offValues = CollectionUtil.caseInsensitiveSet("off", "false", "0");
    Set<String> onValues = CollectionUtil.caseInsensitiveSet("on", "true", "1");

    try
    {
      if (offValues.contains(param))
      {
        connection.setAutoCommit(false);
        result.addMessageByKey("MsgAutocommitDisabled");
        result.setSuccess();
      }
      else if (onValues.contains(param))
      {
        connection.setAutoCommit(true);
        result.addMessageByKey("MsgAutocommitEnabled");
        result.setSuccess();
      }
      else
      {
        result.addErrorMessageByKey("ErrAutocommitWrongParameter");
      }
    }
    catch (SQLException e)
    {
      result.addErrorMessage(e.getMessage());
    }
    return result;
  }

  private StatementRunnerResult setFeedback(String param)
  {
    StatementRunnerResult result = new StatementRunnerResult();
    if (StringUtil.isEmpty(param))
    {
      result.addErrorMessageByKey("ErrFeedbackWrongParameter");
      return result;
    }

    Set<String> offValues = CollectionUtil.caseInsensitiveSet("off", "false", "0");
    Set<String> onValues = CollectionUtil.caseInsensitiveSet("on", "true", "1");

    if (offValues.contains(param))
    {
      this.runner.setVerboseLogging(false);
      result.addMessageByKey("MsgFeedbackDisabled");
      result.setSuccess();
    }
    else if (onValues.contains(param))
    {
      this.runner.setVerboseLogging(true);
      result.addMessageByKey("MsgFeedbackEnabled");
      result.setSuccess();
    }
    else
    {
      result.addErrorMessageByKey("ErrFeedbackWrongParameter");
    }

    return result;
  }

  @Override
  public String getVerb()
  {
    return VERB;
  }

}
