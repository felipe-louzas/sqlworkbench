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
package workbench.sql;

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import workbench.RunMode;
import workbench.WbManager;
import workbench.interfaces.ParameterPrompter;
import workbench.interfaces.ResultLogger;
import workbench.interfaces.ResultSetConsumer;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.ConnectionProfile;
import workbench.db.DBID;
import workbench.db.DbSettings;
import workbench.db.ErrorPositionReader;
import workbench.db.JdbcUtils;
import workbench.db.WbConnection;
import workbench.db.oracle.OracleUtils;

import workbench.storage.DataStore;
import workbench.storage.DefaultRefCursorConsumer;
import workbench.storage.RefCursorConsumer;
import workbench.storage.ResultInfo;
import workbench.storage.RowActionMonitor;

import workbench.sql.macros.MacroManager;
import workbench.sql.wbcommands.CommonArgs;
import workbench.sql.wbcommands.ConditionCheck;
import workbench.sql.wbcommands.WbEnableOraOutput;

import workbench.util.ArgumentParser;
import workbench.util.ArgumentType;
import workbench.util.ArgumentValue;
import workbench.util.CollectionUtil;
import workbench.util.DdlObjectInfo;
import workbench.util.ExceptionUtil;
import workbench.util.FileDialogUtil;
import workbench.util.FileUtil;
import workbench.util.SqlParsingUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;
import workbench.util.WbFile;

/**
 * A single SQL command.
 *
 * This class is used if no special class was found for a given SQL verb.
 * The execute method checks if the command returned a result set or simply an update count.
 *
 * An instance of a SQL command should always be executed in a separate Thread
 * to allow cancelling of the running statement.
 *
 * @author  Thomas Kellerer
 */
public class SqlCommand
{
  protected Statement currentStatement;
  protected WbConnection currentConnection;
  protected boolean isCancelled;
  protected RowActionMonitor rowMonitor;
  protected boolean isUpdatingCommand;
  protected ErrorReportLevel errorLevel;
  protected ResultLogger messageLogger;
  protected StatementRunner runner;
  protected int queryTimeout;
  protected int maxRows;
  protected DataStore currentRetrievalData;
  protected ParameterPrompter prompter;
  protected ArgumentParser cmdLine;
  protected boolean includeStatementInError;
  protected boolean showDataLoading = true;
  protected boolean alwaysBufferResults;
  protected boolean useMessageLoggerForResults = true;
  protected String variablePoolID = null;
  protected boolean bufferMessages;

  public void setRowMonitor(RowActionMonitor monitor)
  {
    this.rowMonitor = monitor;
  }

  public void setMessageLogger(ResultLogger logger)
  {
    this.messageLogger = logger;
  }

  public ArgumentParser getArgumentParser()
  {
    return this.cmdLine;
  }

  public void enableMessageBuffering()
  {
    this.bufferMessages = true;
  }

  public void setUseMessageLoggerForResults(boolean flag)
  {
    this.useMessageLoggerForResults = flag;
  }

  protected StatementRunnerResult createResult(String sql)
  {
    StatementRunnerResult result;
    if (useMessageLoggerForResults)
    {
      result = new StatementRunnerResult(sql, messageLogger);
    }
    else
    {
      result = new StatementRunnerResult(sql);
      if (bufferMessages)
      {
        result.enableMessageBuffering();
      }
    }
    return result;
  }

  public String getVariablePoolID()
  {
    return variablePoolID;
  }

  public void setVariablePoolID(String variablePoolID)
  {
    this.variablePoolID = variablePoolID;
  }

  public void setErrorReportLevel(ErrorReportLevel level)
  {
    this.errorLevel = level;
  }

  public ErrorReportLevel getErrorReportLevel()
  {
    return this.errorLevel;
  }

  public boolean isWbCommand()
  {
    return false;
  }

  public void setAlwaysBufferResults(boolean flag)
  {
    this.alwaysBufferResults = flag;
  }

  public String getParameterHelp()
  {
    StringBuilder msg = new StringBuilder(100);
    int maxLength = Integer.MIN_VALUE;

    for (String arg : cmdLine.getRegisteredArguments())
    {
      if (arg.length() > maxLength) maxLength = arg.length();
    }

    maxLength += 3;

    maxLength = Math.max(10, maxLength);

    msg.append("Parameters for " + getVerb() + "\n\n");
    String header = String.format("%-" + maxLength + "s %-20s %s\n", "NAME", "TYPE", "VALUES");
    msg.append(header);
    msg.append(StringUtil.padRight("-", header.length(), '-') + "\n");

    for (String arg : cmdLine.getRegisteredArguments())
    {
      if (arg.equalsIgnoreCase(CommonArgs.ARG_HELP)) continue;

      Collection<ArgumentValue> values = cmdLine.getAllowedValues(arg);
      ArgumentType argType = cmdLine.getArgumentType(arg);

      String valueList = "";

      if (argType == ArgumentType.ListArgument)
      {
        if (CollectionUtil.isNonEmpty(values))
        {
          boolean first = true;
          for (ArgumentValue val : values)
          {
            if (!first) valueList += ", ";
            if (first) first = false;
            valueList += val.getDisplay();
          }
        }
      }
      else if (argType == ArgumentType.BoolArgument)
      {
        valueList = "true,false";
      }
      msg.append(String.format("%-" + maxLength + "s %-20s %s \n", "-" + arg, argType, valueList));
    }
    return msg.toString();
  }

  protected WbFile findXsltFile(String fileName)
  {
    if (StringUtil.isEmpty(fileName)) return null;

    if (!fileName.toLowerCase().endsWith(".xslt"))
    {
      fileName += ".xslt";
    }

    WbFile fileArg = new WbFile(fileName);

    if (fileArg.exists()) return fileArg;

    // User provided an absolute file path, don't search for the file
    if (fileArg.isAbsolute()) return fileArg;

    File[] path = new File[]{new WbFile(getBaseDir()), Settings.getInstance().getDefaultXsltDirectory()};

    WbFile result = FileUtil.searchFile(fileName, path);

    if (result != null && result.exists())
    {
      LogMgr.logInfo(new CallerInfo(){}, "Found XSLT file \"" + fileName + "\" in directory: " + result.getParentFile().getAbsolutePath());
    }
    else
    {
      LogMgr.logError(new CallerInfo(){}, "XSLT file \"" + fileName + "\" not found in " + Arrays.toString(path), null);
    }
    return result;
  }

  protected File getXsltBaseDir()
  {
    String dir = runner == null ? null : runner.getBaseDir();
    if (dir == null)
    {
      return Settings.getInstance().getDefaultXsltDirectory();
    }
    return new File(dir);
  }

  protected SqlParsingUtil getParsingUtil()
  {
    return SqlParsingUtil.getInstance(currentConnection);
  }

  protected String getBaseDir()
  {
    String dir = runner == null ? null : runner.getBaseDir();
    if (dir == null)
    {
      dir = ".";
    }
    return dir;
  }

  protected int getMacroClientId()
  {
    if (runner == null) return MacroManager.DEFAULT_STORAGE;
    return runner.getMacroClientId();
  }

  protected String getDefaultSuccessMessage(StatementRunnerResult result)
  {
    String verb = getVerb();
    if (StringUtil.isEmpty(verb) && result != null)
    {
      verb = getParsingUtil().getSqlVerb(result.getSourceCommand());
    }

    DbSettings dbs = currentConnection != null ? currentConnection.getDbSettings() : null;
    if (dbs != null && !dbs.showSuccessMessageForVerb(verb)) return null;

    String msg = result == null ? null : getSuccessMessage(verb, result.getSourceCommand());
    if (msg != null) return msg;

    String dbid = null;
    if (currentConnection != null)
    {
      dbid = currentConnection.getDbId();
    }
    msg = ResourceMgr.getDynamicString("MsgStmtSuccess", verb, dbid);

    if (msg != null) return msg;

    if (StringUtil.isEmpty(verb))
    {
      verb = ResourceMgr.getString("TxtStatement");
    }
    return ResourceMgr.getFormattedString("MsgKnownStatementOK", verb);
  }

  protected String getSuccessMessage(String verb, String sql)
  {
    DdlObjectInfo info = SqlUtil.getDDLObjectInfo(sql, currentConnection);
    if (info != null)
    {
      String msg = getSuccessMessage(info, verb);
      if (msg != null)
      {
        return msg;
      }
    }
    return null;
  }

  protected String getSuccessMessage(DdlObjectInfo info, String verb)
  {
    if ("DROP".equals(verb))
    {
      if (info == null || info.getObjectType() == null)
      {
        return ResourceMgr.getString("MsgGenDropSuccess");
      }
      if (StringUtil.isNotBlank(info.getObjectName()))
      {
        return ResourceMgr.getFormattedString("MsgDropSuccess", info.getDisplayType(), info.getObjectName());
      }
      else
      {
        return ResourceMgr.getFormattedString("MsgDropTypeSuccess", info.getObjectType());
      }
    }
    else if ("CREATE".equals(verb) || "RECREATE".equals(verb))
    {
      if (info == null || info.getObjectType() == null)
      {
        return ResourceMgr.getString("MsgGenCreateSuccess");
      }
      if (StringUtil.isNotBlank(info.getObjectName()))
      {
        return ResourceMgr.getFormattedString("MsgCreateSuccess", info.getDisplayType(), info.getObjectName());
      }
      else
      {
        String typeName = info.getObjectType();
        if ("INDEX".equals(typeName))
        {
          // this is essentially for Postgres which allows to create an index without a name (a name will be generated)
          typeName = info.getDisplayType();
        }
        return ResourceMgr.getFormattedString("MsgCreateTypeSuccess", typeName);
      }
    }
    else if ("ALTER".equals(verb) && info != null)
    {
      String display = info.getObjectType();
      if (info.getObjectName() != null)
      {
        display += " " + info.getObjectName();
      }
      return ResourceMgr.getFormattedString("MsgDMLSuccess", verb, display);
    }
    else if ("ANALYZE".equals(verb) && info != null)
    {
      String name = currentConnection.getMetadata().adjustObjectnameCase(info.getObjectName());
      return ResourceMgr.getFormattedString("MsgObjectAnalyzed", StringUtil.capitalize(info.getObjectType()), name);
    }
    return null;
  }

  protected void appendSuccessMessage(StatementRunnerResult result)
  {
    if (result.wasConsumed()) return;

    String msg = getDefaultSuccessMessage(result);
    if (msg != null)
    {
      result.addMessage(msg);
    }
  }

  public void setParameterPrompter(ParameterPrompter p)
  {
    this.prompter = p;
  }

  public void setShowDataLoading(boolean flag)
  {
    this.showDataLoading = flag;
  }

  protected void appendOutput(StatementRunnerResult result)
  {
    String s = this.currentConnection.getOutputMessages();
    boolean hideLabel = WbEnableOraOutput.HIDE_HINT.equals(runner.getSessionAttribute(StatementRunner.SERVER_MSG_PROP));
    if (StringUtil.isNotBlank(s))
    {
      if (result.hasMessages())
      {
        result.addMessageNewLine();
      }
      if (!hideLabel)
      {
        result.addMessageByKey("TxtServerOutput");
      }
      result.addMessage(s);
    }
  }

  /**
   * Append any warnings from the given Statement and Connection to the given StringBuilder.
   *
   * If the connection is a connection to Oracle then any messages written with dbms_output are appended as well
   * This behaviour is then similar to MS SQL Server where any messages displayed using the PRINT function are
   * returned in the Warnings.
   *
   * @see workbench.util.SqlUtil#getWarnings(WbConnection, Statement)
   */
  protected boolean appendWarnings(StatementRunnerResult result, boolean addLabel)
  {
    if (this.runner == null || this.runner.getHideWarnings()) return false;

    CharSequence warn = SqlUtil.getWarnings(this.currentConnection, this.currentStatement);
    if (warn == null || warn.length() == 0) return false;

    if (result.hasMessages()) result.addMessageNewLine();

    // Only add the "Warnings:" text if the message returned from the
    // server does not already start with "Warning"
    if (addLabel && warn.length() > 7 && !warn.subSequence(0, 7).toString().equalsIgnoreCase("Warning"))
    {
      result.addMessageByKey("TxtWarnings");
    }
    result.addWarning(warn);
    result.addMessageNewLine();

    return true;
  }

  protected void setUnknownMessage(StatementRunnerResult result, ArgumentParser cmdline, String help)
  {
    if (!cmdLine.hasUnknownArguments()) return;

    StringBuilder msg = new StringBuilder(ResourceMgr.getString("ErrUnknownParameter"));
    msg.append(' ');
    msg.append(cmdLine.getUnknownArguments());

    result.addErrorMessage(msg.toString());
    if (!WbManager.getInstance().isBatchMode() && help != null)
    {
      result.addMessageNewLine();
      result.addMessage(help);
    }
  }

  /**
   * Cancels this statements execution.
   *
   * Cancelling is done by calling <tt>cancel()</tt> on the current JDBC Statement object.
   * This requires that the JDBC driver actually supports cancelling of statements <b>and</b>
   * that this method is called from a different thread.
   *
   * It also sets the internal cancelled flag so that <tt>SqlCommand</tt>s
   * that process data in a loop can check this flag and exit the loop
   * (e.g. {@link workbench.sql.wbcommands.WbExport})
   *
   */
  public void cancel()
    throws SQLException
  {
    final CallerInfo ci = new CallerInfo(){};

    synchronized (this)
    {
      isCancelled = true;
      if (currentRetrievalData != null)
      {
        currentRetrievalData.cancelRetrieve();
      }

      SqlUtil.cancelStatement(ci, currentStatement);
    }
  }

  /**
   * Cleanup all resource after running the statement.
   *
   * This method should be called, once the caller is finished with running
   * the SQL command.
   *
   * This releases any database resources that were obtained during the execution of the statement (especially it
   * closes the JDBC statement object that was used to run this command).
   * <br/>
   * Warnings are cleared for the current statement and the connection.
   */
  public void done()
  {
    synchronized (this)
    {
      if (currentStatement != null)
      {
        try { currentStatement.clearBatch(); } catch (Throwable th) {}
        try { currentStatement.clearWarnings(); } catch (Throwable th) {}
        try { currentConnection.clearWarnings(); } catch (Throwable e) {}

        try
        {
          currentStatement.close();
        }
        catch (Throwable th)
        {
          LogMgr.logError(new CallerInfo(){}, "Error when closing the current statement for: " + getClass().getSimpleName(), th);
        }
      }
      currentStatement = null;
      isCancelled = false;
      currentRetrievalData = null;
    }
  }

  /**
   * Define the StatementRunner that runs this statement.
   * This is needed e.g. to determine the base directory for
   * relative file paths (WbInclude, WbImport, etc)
   *
   * @param r The runner running this statement.
   */
  public void setStatementRunner(StatementRunner r)
  {
    this.runner = r;
  }

  public boolean isModeSupported(RunMode mode)
  {
    return true;
  }

  public boolean shouldEndTransaction()
  {
    return false;
  }

  /**
   * Checks if this statement needs a connection to a database to run.
   *
   * This should be overridden by an ancestor not requiring a connection.
   *
   * @return true
   */
  protected boolean isConnectionRequired()
  {
    return true;
  }

  protected void setMaxRowsForStatement(Statement stmt)
  {
    try
    {
      stmt.setMaxRows(this.maxRows >= 0 ? maxRows : 0);
    }
    catch (Throwable e)
    {
      LogMgr.logWarning(new CallerInfo(){}, "The JDBC driver does not support the setMaxRows() function! (" + e.getMessage() + ")");
    }
  }

  public boolean displayHelp(StatementRunnerResult result)
  {
    if (cmdLine.getBoolean(CommonArgs.ARG_HELP))
    {
      result.addMessage(getParameterHelp());
      result.setSuccess();
      return true;
    }
    return false;
  }

  /**
   * Should be overridden by a specialised SqlCommand.
   * setConnection must be called before calling execute()
   *
   * @see #setConnection(workbench.db.WbConnection)
   */
  public StatementRunnerResult execute(String sql)
    throws SQLException, Exception
  {
    StatementRunnerResult result = createResult(sql);

    this.currentStatement = this.currentConnection.createStatement();
    this.isCancelled = false;

    sql = getSqlToExecute(sql);
    String verb = getParsingUtil().getSqlVerb(sql);

    result.ignoreUpdateCounts(currentConnection.getDbSettings().verbsWithoutUpdateCount().contains(verb));

    if (runner.useSavepointForDML())
    {
      runner.setSavepoint();
    }

    if (currentConnection.getDbSettings().useMaxRows(verb))
    {
      setMaxRowsForStatement(currentStatement);
    }

    try
    {
      boolean hasResult = this.currentStatement.execute(sql);
      if (hasResult && shouldCallGetMoreResultsFirst(verb))
      {
        // workaround for an Oracle driver bug
        // see: https://community.oracle.com/message/13952214#13952214
        hasResult = currentStatement.getMoreResults();
      }
      result.setSuccess();
      processResults(result, hasResult);
      appendSuccessMessage(result);
      runner.releaseSavepoint();
    }
    catch (Exception e)
    {
      runner.rollbackSavepoint();
      // SQL Server seems to return results (e.g. from a stored procedure) even if an error occurred.
      processResults(result, false);
      addErrorInfo(result, sql, e);
      LogMgr.logUserSqlError(new CallerInfo(){}, sql, e);
    }
    finally
    {
      this.done();
    }
    return result;
  }

  private boolean shouldCallGetMoreResultsFirst(String verb)
  {
    if (verb == null) return false;
    if (currentConnection == null) return false;
    if (DBID.Oracle.isDB(currentConnection) && OracleUtils.fixPLSQLResultSetBug())
    {
      // this is only necessary for anonymous PL/SQL blocks
      return (verb.equalsIgnoreCase("begin") || verb.equalsIgnoreCase("declare"));
    }
    return false;
  }

  protected void processResults(StatementRunnerResult result, boolean hasResult)
    throws SQLException
  {
    processResults(result, hasResult, null);
  }

  protected RefCursorConsumer createRefCursorConsumer(ResultInfo info)
  {
    return new DefaultRefCursorConsumer(info, currentConnection);
  }

  /**
   * Process any ResultSets or updatecounts generated by the last statement execution.
   *
   * {@link  DbSettings#getMaxResults()} defines an upper limit on how many ResultSets
   * are being considered.
   *
   * @param result the result object to which any DataStore or update count should be added
   * @param hasResult the return value of the execute() method that was called before
   * @param firstResult the ResultSet that was obtained by calling executeQuery() before
   *
   * @see DbSettings#getMaxResults()
   */
  protected void processResults(StatementRunnerResult result, boolean hasResult, ResultSet firstResult)
    throws SQLException
  {
    if (result == null) return;

    final CallerInfo ci = new CallerInfo(){};
    if (currentConnection == null || currentConnection.isClosed())
    {
      LogMgr.logError(ci, "Current connection has been closed. Aborting...", null);
      return;
    }

    appendOutput(result);

    // The Postgres JDBC driver clears the warnings if getMoreResults() is called.
    // So we add the warnings before calling getMoreResults().
    // This doesn't seem to do any harm for other DBMS.
    appendWarnings(result, true);

    if (!runner.getStatementHook().fetchResults())
    {
      return;
    }

    boolean fetchOnly = runner.getStatementHook().fetchResults() && !runner.getStatementHook().displayResults();

    int updateCount = -1;
    boolean moreResults;

    ResultProcessor processor = new ResultProcessor(currentStatement, firstResult, currentConnection);

    if (hasResult)
    {
      moreResults = true;
    }
    else
    {
      // the initial execute() did not return a result set, so the first "result" is an updateCount
      try
      {
        if (currentStatement != null) updateCount = this.currentStatement.getUpdateCount();
      }
      catch (Exception e)
      {
        LogMgr.logError(ci, "Error when calling getUpdateCount()", e);
        updateCount = -1;
      }

      moreResults = processor.hasMoreResults();
    }

    if (currentConnection == null || currentConnection.isClosed() || currentConnection.getMetadata() == null)
    {
      LogMgr.logError(ci, "Current connection has been closed. Aborting...", null);
      return;
    }

    boolean retrieveResultWarnings = currentConnection.getDbSettings().retrieveWarningsForEachResult();
    boolean multipleUpdateCounts = currentConnection.getDbSettings().allowsMultipleGetUpdateCounts();

    int counter = 0;
    int maxLoops = currentConnection.getDbSettings().getMaxResults();

    ResultSet rs;
    while (moreResults || updateCount > -1)
    {

      if (currentConnection == null || currentConnection.isClosed())
      {
        LogMgr.logError(ci, "Current connection has been closed. Aborting...", null);
        return;
      }

      if (updateCount > -1)
      {
        result.addUpdateCountMsg(updateCount);
      }

      if (moreResults)
      {
        rs = processor.getResult();

        if (rs == null) break;

        // this is for SQL Server messages sent with "PRINT"
        // they need to be retrieved for each result set.
        if (retrieveResultWarnings)
        {
          appendWarnings(result, false);
        }

        ResultSetConsumer consumer = runner.getConsumer();
        if (consumer != null && !alwaysBufferResults)
        {
          result.addResultSet(rs);
          consumer.consumeResult(result);
        }
        else
        {
          RowActionMonitor monitorToUse = null;
          boolean displayResults = runner.getStatementHook().displayResults();
          if (showDataLoading && displayResults)
          {
            monitorToUse = this.rowMonitor;
          }

          // we have to use an instance variable for the retrieval, otherwise the retrieval cannot be cancelled!
          this.currentRetrievalData = new DataStore(rs, false, monitorToUse, maxRows, this.currentConnection);

          RefCursorConsumer refCursorConsumer = createRefCursorConsumer(currentRetrievalData.getResultInfo());

          try
          {
            // Not reading the data in the constructor enables us
            // to cancel the retrieval of the data from the ResultSet
            // without using statement.cancel()
            // The DataStore checks for the cancel flag during processing of the ResulSet
            this.currentRetrievalData.setGeneratingSql(result.getSourceCommand());

            if (fetchOnly)
            {
              int rows = currentRetrievalData.fetchOnly(rs);
              result.setRowsProcessed(rows);
            }
            else
            {
              currentRetrievalData.initData(rs, maxRows, refCursorConsumer);
            }
          }
          catch (SQLException e)
          {
            // Some JDBC driver throw an exception when a statement is
            // cancelled. But in this case, we do not want to throw away the
            // data that was retrieved until now. We only add a warning
            if (this.currentRetrievalData != null && this.currentRetrievalData.isCancelled())
            {
              result.addWarningByKey("MsgErrorDuringRetrieve");
              result.addMessage(ExceptionUtil.getAllExceptions(e));
            }
            else
            {
              // if the statement was not cancelled, make sure
              // the error is displayed to the user.
              throw e;
            }
          }
          finally
          {
            JdbcUtils.closeResult(rs);
          }

          result.addRowsProcessed(currentRetrievalData.getRowCount());

          if (displayResults)
          {
            if (refCursorConsumer.containsOnlyRefCursors() == false)
            {
              result.addDataStore(this.currentRetrievalData);
            }
            for (DataStore ds : refCursorConsumer.getResults())
            {
              result.addDataStore(ds);
            }
          }
        }
      }

      if (currentConnection == null || currentConnection.isClosed())
      {
        LogMgr.logError(ci, "Current connection has been closed. Aborting...", null);
        return;
      }

      moreResults = processor.hasMoreResults();

      if (multipleUpdateCounts)
      {
        try
        {
          updateCount = this.currentStatement.getUpdateCount();
        }
        catch (Throwable e)
        {
          LogMgr.logWarning(ci, "Error when calling getUpdateCount(): " + ExceptionUtil.getDisplay(e));
          updateCount = -1;
          multipleUpdateCounts = false;
        }
      }
      else
      {
        updateCount = -1;
      }

      counter ++;

      // some JDBC drivers do not implement getMoreResults() and getUpdateCount()
      // correctly, so this is a safety to prevent an endless loop
      if (counter >= maxLoops)
      {
        LogMgr.logWarning(ci, "Breaking out of loop because " + maxLoops + " iterations reached");
        break;
      }
    }

    this.currentRetrievalData = null;
  }

  /**
   * Remove comments from the SQL if the current connection profile has the
   * corresponding option defined.
   *
   * @param originalSql
   * @return the sql with comments removed if necessary.
   *
   * @see workbench.db.ConnectionProfile#getRemoveComments()
   *
   * @see workbench.db.DbSettings#removeNewLinesInSQL()
   */
  protected String getSqlToExecute(String originalSql)
  {
    try
    {
      boolean removeComments = currentConnection.getRemoveComments();
      boolean removeNewLines = currentConnection.getRemoveNewLines();

      if (!removeComments && !removeNewLines ) return originalSql;

      return SqlUtil.makeCleanSql(originalSql, !removeNewLines, !removeComments, false, currentConnection);
    }
    catch (Exception ex)
    {
      // Just in case ...
      return originalSql;
    }
  }

  /**
   * Define the connection for this command.
   * <br/>
   * The StatementRunner keeps only one instance of each command, so
   * this must be called every time before execute() is called to make
   * sure the command is acting on the correct connection.
   * <br/>
   *
   * @param conn the new current connection
   * @see #execute(java.lang.String)
   */
  public void setConnection(WbConnection conn)
  {
    this.currentConnection = conn;
  }

  /**
   *  Should be overridden by a specialised SqlCommand
   */
  public String getVerb()
  {
    return StringUtil.EMPTY_STRING;
  }

  public String getAlternateVerb()
  {
    return null;
  }

  public Collection<String> getAllVerbs()
  {
    if (getAlternateVerb() == null)
    {
      return Collections.singleton(getVerb());
    }
    return CollectionUtil.arrayList(getVerb(), getAlternateVerb());
  }

  /**
   * Check if this verb for this statement is considered an updating command in
   * all circumstances.
   */
  public boolean isUpdatingCommand()
  {
    return this.isUpdatingCommand;
  }

  /**
   * Check if this statement needs a confirmation by the user
   * to be run.
   *
   * @param con the current connection
   * @param sql the statement to be executed
   * @return true if the user should confirm to run this statement
   * @see #getModificationTarget(workbench.db.WbConnection, java.lang.String)
   * @see workbench.db.ConnectionProfile#getConfirmUpdates()
   */
  public boolean needConfirmation(WbConnection con, String sql)
  {
    if (con == null || con.isClosed()) return false;

    // Avoid calling isUpdatingCommand() if it's not necessary.
    if (con.confirmUpdatesInSession())
    {
      if (isUpdatingCommand(con, sql))
      {
        return true;
      }
    }

    ConnectionProfile prof = getModificationTarget(con, sql);
    if (prof == null) return false;

    if (prof.getPreventDMLWithoutWhere())
    {
      return SqlUtil.isUnRestrictedDML(sql, con);
    }
    return false;
  }

  /**
   * Check if the given SQL statement may update the database.
   *
   * This method will also consider additional keywords
   * added by the user (globally or DBMS specific) and check if
   * the SELECT statement is a disguised CREATE TABLE.
   *
   * @param con the database connection to check against
   * @param sql the SQL statement to check
   * @return true if the SQL might update the database
   *
   * @see workbench.db.DbSettings#isUpdatingCommand(java.lang.String)
   * @see workbench.db.DbMetadata#isSelectIntoNewTable(java.lang.String)
   */
  public boolean isUpdatingCommand(WbConnection con, String sql)
  {
    if (this.isUpdatingCommand) return true;
    if (con == null) return isUpdatingCommand;
    if (con.isClosed()) return isUpdatingCommand;
    String verb = con.getParsingUtil().getSqlVerb(sql);
    DbSettings dbs = con.getDbSettings();

    boolean updating = dbs.isUpdatingCommand(verb);
    if (updating) return true;

    if ("WITH".equalsIgnoreCase(verb))
    {
      CommandMapper mapper = new CommandMapper(false);
      List<String> verbs = con.getParsingUtil().getCTEVerbs(con, sql);
      for (String cteVerb : verbs)
      {
        if (dbs.isUpdatingCommand(cteVerb)) return true;
        SqlCommand cmd = mapper.getCommandFromVerb(cteVerb);
        if (cmd != null && cmd.isUpdatingCommand()) return true;
      }
    }

    return con.getMetadata().isSelectIntoNewTable(sql);
  }

  /**
   * Returns the profile that is attached to the connection where the
   * data would be modified.
   * The default implementation returns the profile attached to the
   * passed connection. WbCopy will check the commandline and will
   * return the profile specified in the -targetProfile parameter
   *
   * @param con the connection to check
   * @param sql the SQL statement to check (ignored)
   * @return the profile attached to the connection
   *
   * @see workbench.sql.wbcommands.WbCopy#getModificationTarget(workbench.db.WbConnection, java.lang.String)
   */
  public ConnectionProfile getModificationTarget(WbConnection con, String sql)
  {
    if (con == null) return null;
    return con.getProfile();
  }

  /**
   * Check if the current profile allows running of the given SQL. This
   * will check for the read-only option and will disallowe statements
   * where {@link #isUpdatingCommand(workbench.db.WbConnection, java.lang.String)}
   * returns true.
   *
   * @param con
   * @param sql
   * @return true if the profile is not read-only, or if the statement does not update anything
   * @see #isUpdatingCommand(workbench.db.WbConnection, java.lang.String)
   */
  public boolean isModificationAllowed(WbConnection con, String sql)
  {
    if (con == null || con.isClosed()) return true;
    ConnectionProfile prof = getModificationTarget(con, sql);
    if (prof == null) return true;

    // if the modification target is not the profile of the current connection
    // only the readOnly property of the profile is relevant
    if (!con.getProfile().equals(prof) && prof.isReadOnly())
    {
      return false;
    }

    // the command modifies the profile of the current connection,
    // the state of the current connection is relevant.
    if (con.getProfile().equals(prof) && con.isSessionReadOnly())
    {
      if (isUpdatingCommand(con, sql)) return false;
    }
    return true;
  }

  public void setQueryTimeout(int timeout)
  {
    this.queryTimeout = timeout;
  }

  public void setMaxRows(int max)
  {
    maxRows = max;
  }


  /**
   * Get a "clean" version of the sql with the verb stripped off
   * and all comments and newlines removed for processing the
   * parameters to a Workbench command
   *
   * @param sql the sql to "clean"
   * @return the sql with the verb, comments and newlines removed
   * @see workbench.util.SqlUtil#makeCleanSql(java.lang.String, boolean)
   * @see workbench.util.SqlUtil#getSqlVerb(String)
   */
  protected String getCommandLine(String sql)
  {
    return SqlUtil.stripVerb(SqlUtil.makeCleanSql(sql, false, false, true, currentConnection));
  }

  /**
   * Assumes the given parameter is a filename supplied by the end user.
   * <br/>
   * If the filename is absolute, a file object with that path is returned.
   * If the filename does not contain a full path, the current baseDir of the
   * StatementRunner is added to the returned file.
   *
   * @param fileName
   * @return a File object pointing to the file indicated by the user.
   *
   * @see workbench.sql.StatementRunner#getBaseDir()
   * @see #evaluateWildcardFileArgs(java.lang.String)
   */
  public WbFile evaluateFileArgument(String fileName)
  {
    return evaluateFileArgument(fileName, false);
  }

  public WbFile evaluateFileArgument(String fileName, boolean replaceCommonPlaceholders)
  {
    if (StringUtil.isEmpty(fileName)) return null;

    String fname = StringUtil.trimQuotes(fileName);
    if (replaceCommonPlaceholders)
    {
      fname = FileDialogUtil.replaceConfigDir(fname);
      fname = FileDialogUtil.replaceProgramDir(fname);
    }

    if (fname.startsWith("~"))
    {
      fname = "/" + System.getProperty("user.home") + fname.substring(1);
    }
    WbFile f  = new WbFile(fname);
    if (f.isAbsolute()) return f;

    // Use the "current" directory of the StatementRunner
    // for the path of the file, if no path is specified.
    if (this.runner != null)
    {
      String dir = this.runner.getBaseDir();
      if (StringUtil.isNotEmpty(dir))
      {
        f = new WbFile(dir, fname);
      }
    }
    return f;
  }

  /**
   * Evaluates all files denoted by the argument (which can contain wildcards).
   * <br/>
   * If the filename does not contain a full path, the current baseDir of the
   * StatementRunner is used to find all files matching the pattern.
   *
   * @param arg   the (wildcard) argument provided by the user
   *
   * @return a List of files, never null
   *
   * @see workbench.sql.StatementRunner#getBaseDir()
   * @see #evaluateFileArgument(java.lang.String)
   * @see FileUtil#listFiles(java.lang.String, java.lang.String)
   */
  public List<WbFile> evaluateWildcardFileArgs(String arg)
  {
    return FileUtil.listFiles(arg, getBaseDir());
  }

  /**
   * Adds the SQL statement (or part of it) to the passed StatementRunnerResult.
   *
   * If errorLevel is none then nothing is added
   * If errorLevel is limited, only the beginning of the SQL statement is added
   * If errorLevel is full, the complete SQL statement is added to the result
   *
   * @param result  the result to which the statement should be added
   * @param sql     the failing SQL statement
   *
   * @see #setErrorReportLevel(workbench.sql.ErrorReportLevel)
   * @see Settings#getStatementErrorReportLevel()
   * @see Settings#getMaxErrorStatementLength()
   */
  protected void addErrorStatement(StatementRunnerResult result, String sql)
  {
    if (errorLevel != ErrorReportLevel.none)
    {
      StringBuilder msg = new StringBuilder(150);
      msg.append(ResourceMgr.getString("MsgExecuteError"));
      msg.append('\n');
      if (errorLevel == ErrorReportLevel.full)
      {
        msg.append(sql);
      }
      else
      {
        msg.append(StringUtil.getMaxSubstring(sql.trim(), Settings.getInstance().getMaxErrorStatementLength()));
      }
      result.addMessage(msg);
      result.addMessageNewLine();
    }
  }

  protected void addErrorInfo(StatementRunnerResult result, String executedSql, Exception e)
  {
    addErrorStatement(result, executedSql);
    addErrorPosition(result, executedSql, e);
  }

  protected void addErrorPosition(StatementRunnerResult result, String executedSql, Exception e)
  {
    try
    {
      ErrorPositionReader reader = ErrorPositionReader.Factory.createPositionReader(currentConnection);

      // the source SQL should be preferred over the executedSql
      // because for e.g. WbInclude the executedSql will be WbInclude
      // the sourceCommand() from the result will be the real SQL that generated the error

      String sql;
      if (result.getSourceCommand() == null)
      {
        sql = executedSql;
      }
      else
      {
        // the source SQL is not necessarily the one that got executed
        // if "remove comments is enabled"
        sql = getSqlToExecute(result.getSourceCommand());
      }

      ErrorDescriptor error = reader.getErrorPosition(currentConnection, sql, e);
      if (error != null && error.getErrorMessage() != null)
      {
        error.setIncludeErrorCode(currentConnection.getDbSettings().getIncludeErrorCodeInMessage());
        String fullMsg = reader.enhanceErrorMessage(sql, error.getErrorMessage(), error);
        result.addErrorMessage(error, fullMsg);
      }
      else
      {
        result.addErrorMessage(e);
      }
    }
    catch (Throwable th)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not retrieve error position!", th);
      result.addErrorMessage(ExceptionUtil.getAllExceptions(e).toString());
    }
  }

  @Override
  public String toString()
  {
    return getVerb();
  }

  protected boolean checkConditions(StatementRunnerResult result)
  {
    if (!ConditionCheck.isCommandLineOK(result, cmdLine))
    {
      return false;
    }

    ConditionCheck.Result check = ConditionCheck.checkConditions(cmdLine, currentConnection,
                                                                 this::evaluateFileArgument, variablePoolID);
    if (check.isOK())
    {
      return true;
    }
    result.addMessage(ConditionCheck.getMessage(getVerb(), check));
    result.setSuccess();
    return false;
  }
}
