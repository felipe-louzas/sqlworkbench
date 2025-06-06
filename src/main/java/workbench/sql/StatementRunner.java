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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import workbench.WbManager;
import workbench.interfaces.ExecutionController;
import workbench.interfaces.ParameterPrompter;
import workbench.interfaces.ResultLogger;
import workbench.interfaces.ResultSetConsumer;
import workbench.interfaces.ScriptErrorHandler;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.ConnectionProfile;
import workbench.db.WbConnection;

import workbench.storage.DataStore;
import workbench.storage.DatastoreTransposer;
import workbench.storage.RowActionMonitor;

import workbench.sql.annotations.CrossTabAnnotation;
import workbench.sql.annotations.RemoveEmptyResultsAnnotation;
import workbench.sql.annotations.RemoveResultAnnotation;
import workbench.sql.annotations.WbAnnotation;
import workbench.sql.commands.AlterSessionCommand;
import workbench.sql.commands.SetCommand;
import workbench.sql.commands.TransactionEndCommand;
import workbench.sql.wbcommands.WbEndBatch;
import workbench.sql.wbcommands.WbStartBatch;

import workbench.util.ArgumentParser;
import workbench.util.CollectionUtil;
import workbench.util.SqlParsingUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

import static workbench.sql.commands.TransactionStartCommand.*;

/**
 *
 * @author  Thomas Kellerer
 */
public class StatementRunner
  implements PropertyChangeListener
{
  public static final String SERVER_MSG_PROP = "server_messages";

  // used to restore the "real" connection if WbConnect changes the "current"
  // connection during script execution
  private WbConnection mainConnection;

  private WbConnection currentConnection;

  private SqlCommand currentCommand;
  private String currentSqlVerb;
  private boolean isTransactionCommand;
  private StatementHook statementHook = StatementHookFactory.DEFAULT_HOOK;
  private ResultSetConsumer currentConsumer;
  private String baseDir;

  private RowActionMonitor rowMonitor;
  private ExecutionController controller;
  private WbStartBatch batchCommand;
  private ResultLogger resultLogger;
  private boolean verboseLogging;
  private boolean hideWarnings;
  private ErrorReportLevel errorLevel;
  private ParameterPrompter prompter;
  private boolean ignoreDropErrors;
  protected CommandMapper cmdMapper;
  private SavepointStrategy useSavepoint = SavepointStrategy.whenConfigured;
  private OutputPrinter messageOutput;
  private boolean traceStatements;
  private Savepoint savepoint;
  private final List<PropertyChangeListener> changeListeners = new ArrayList<>();
  private int maxRows = -1;
  private int queryTimeout = -1;
  private boolean showDataLoadingProgress = true;
  private boolean useMessageLoggerForResult = true;

  private final Map<String, String> sessionAttributes = new HashMap<>();
  private final CrossTabAnnotation crossTab = new CrossTabAnnotation();
  private final RemoveEmptyResultsAnnotation removeEmpty = new RemoveEmptyResultsAnnotation();
  private final RemoveResultAnnotation removeResult = new RemoveResultAnnotation();
  private int macroClientId;
  private String variablePoolID;
  private ScriptErrorHandler retryHandler;

  // The history provider is here to give SqlCommands access to the command history.
  // Currently this is only used in WbHistory to show a list of executed statements.
  private StatementHistory history;

  public StatementRunner()
  {
    verboseLogging = !Settings.getInstance().getConsolidateLogMsg();
    errorLevel = Settings.getInstance().getStatementErrorReportLevel();
    cmdMapper = new CommandMapper();
    Settings.getInstance().addPropertyChangeListener(this,
      Settings.PROPERTY_CONSOLIDATE_LOG_MESSAGES,
      Settings.PROPERTY_ERROR_STATEMENT_LOG_LEVEL);
  }

  public void setMacroClientId(int id)
  {
    macroClientId = id;
  }

  public String getVariablePoolID()
  {
    return variablePoolID;
  }

  public void setVariablePoolID(String variablePoolID)
  {
    this.variablePoolID = variablePoolID;
  }

  public int getMacroClientId()
  {
    return macroClientId;
  }

  public ScriptErrorHandler getRetryHandler()
  {
    return retryHandler;
  }

  public void setRetryHandler(ScriptErrorHandler handler)
  {
    this.retryHandler = handler;
  }

  public void setSqlHistory(StatementHistory provider)
  {
    this.history = provider;
  }

  public StatementHistory getSqlHistory()
  {
    return this.history;
  }

  public void dispose()
  {
    Settings.getInstance().removePropertyChangeListener(this);
  }

  public void addChangeListener(PropertyChangeListener l)
  {
    this.changeListeners.add(l);
  }

  public void removeChangeListener(PropertyChangeListener l)
  {
    this.changeListeners.remove(l);
  }

  public void fireConnectionChanged()
  {
    PropertyChangeEvent evt = new PropertyChangeEvent(this, "connection", null, this.currentConnection);
    for (PropertyChangeListener l : changeListeners)
    {
      l.propertyChange(evt);
    }
  }

  public boolean getTraceStatements()
  {
    return traceStatements;
  }

  public void setTraceStatements(boolean flag)
  {
    this.traceStatements = flag;
  }

  public void setMessagePrinter(OutputPrinter output)
  {
    this.messageOutput = output;
  }

  public void setSessionProperty(String name, String value)
  {
    sessionAttributes.put(name, value);
  }

  public void removeSessionProperty(String name)
  {
    sessionAttributes.remove(name);
  }

  public String getSessionAttribute(String name)
  {
    return sessionAttributes.get(name);
  }

  public boolean getBoolSessionAttribute(String name)
  {
    String value = sessionAttributes.get(name);
    return StringUtil.stringToBool(value);
  }

  public void setUseMessageLoggerForResult(boolean flag)
  {
    this.useMessageLoggerForResult = flag;
  }


  @Override
  public void propertyChange(PropertyChangeEvent evt)
  {
    if (Settings.PROPERTY_CONSOLIDATE_LOG_MESSAGES.equals(evt.getPropertyName()))
    {
      this.verboseLogging = !Settings.getInstance().getConsolidateLogMsg();
    }
    else if (Settings.PROPERTY_ERROR_STATEMENT_LOG_LEVEL.equals(evt.getPropertyName()))
    {
      errorLevel = Settings.getInstance().getStatementErrorReportLevel();
    }
  }

  public void setShowDataLoadingProgress(boolean flag)
  {
    this.showDataLoadingProgress = false;
  }

  public void setErrorReportLevel(ErrorReportLevel level)
  {
    this.errorLevel = level;
  }

  public ExecutionController getExecutionController()
  {
    return this.controller;
  }

  public void setExecutionController(ExecutionController control)
  {
    this.controller = control;
  }

  public boolean getHideWarnings()
  {
    return this.hideWarnings;
  }

  public void setHideWarnings(boolean flag)
  {
    this.hideWarnings = flag;
  }

  public void setIgnoreDropErrors(boolean flag)
  {
    this.ignoreDropErrors = flag;
  }

  public boolean getIgnoreDropErrors()
  {
    return this.ignoreDropErrors;
  }

  public boolean hasPendingActions()
  {
    if (this.currentConsumer != null) return true;
    return statementHook.isPending();
  }

  /**
   * For testing purposes only, so that non-default commands can be added during a JUnit test.
   */
  public void addCommand(SqlCommand command)
  {
    cmdMapper.addCommand(command);
  }

  public Collection<String> getAllWbCommands()
  {
    return cmdMapper.getAllWbCommands();
  }

  public void setMaxRows(int rows)
  {
    this.maxRows = rows;
  }

  public void setQueryTimeout(int timeout)
  {
    this.queryTimeout = timeout;
  }

  public void setParameterPrompter(ParameterPrompter filter)
  {
    this.prompter = filter;
  }

  public void setBaseDir(String dir)
  {
    this.baseDir = dir;
  }

  public String getBaseDir()
  {
    return this.baseDir;
  }

  public SqlParsingUtil getParsingUtil()
  {
    return SqlParsingUtil.getInstance(currentConnection);
  }

  public WbConnection getConnection()
  {
    return this.currentConnection;
  }

  public boolean restoreMainConnection()
  {
    if (mainConnection != null)
    {
      this.currentConnection.disconnect();
      this.setConnection(this.mainConnection);
      this.mainConnection = null;
      return true;
    }
    return false;
  }

  /**
   * Temporarily change the connection, but keep the old connection open.
   * If changeConnection() has already been called once, the current connection
   * is closed
   * @param newConn
   */
  public void changeConnection(WbConnection newConn)
  {
    if (newConn == null) return;
    if (newConn == currentConnection) return;

    if (mainConnection == null)
    {
      this.mainConnection = currentConnection;
    }
    else
    {
      this.currentConnection.disconnect();
    }
    this.setConnection(newConn);
  }

  public void setConnection(WbConnection aConn)
  {
    if (statementHook != null)
    {
      statementHook.close(aConn);
    }

    this.releaseSavepoint();
    this.cmdMapper.setConnection(aConn);
    this.currentConnection = aConn;

    fireConnectionChanged();

    if (currentConnection == null) return;

    ConnectionProfile profile = currentConnection.getProfile();
    if (profile != null)
    {
      this.ignoreDropErrors = profile.getIgnoreDropErrors();
      this.hideWarnings = profile.isHideWarnings();
    }

    statementHook = StatementHookFactory.getStatementHook(this);
    sessionAttributes.clear();
  }

  private boolean shouldEndTransactionForCommand(SqlCommand command)
  {
    if (command == null) return false;
    if (command.isUpdatingCommand()) return false;
    if (command instanceof TransactionEndCommand) return false; // commit or rollback
    if (command instanceof AlterSessionCommand) return false;
    if (command instanceof SetCommand) return false;
    if (command.isWbCommand()) return command.shouldEndTransaction();
    if (isTransactionCommand) return false;
    if (isInManualTransaction()) return false;
    if (currentSqlVerb != null)
    {
      Set<String> commands = this.currentConnection.getDbSettings().getNeverEndTransactionCommands();
      if (commands.contains(currentSqlVerb)) return false;
    }
    return true;
  }

  private boolean isInManualTransaction()
  {
    String prop = getSessionAttribute(MANUAL_TRANSACTION_IN_PROGRESS);
    return ("true".equalsIgnoreCase(prop));
  }

  private void endReadOnlyTransaction()
  {
    if (currentConnection == null) return;
    if (currentConnection.getAutoCommit()) return;
    if (currentConnection.getDbSettings() == null) return;

    if (!shouldEndTransactionForCommand(currentCommand)) return;

    if (currentConnection.endReadOnlyTransaction(new CallerInfo(){}))
    {
      LogMgr.logInfo(new CallerInfo(){}, "Ended the current transaction started by: " + currentCommand);
    }
  }

  public void setRowMonitor(RowActionMonitor monitor)
  {
    this.rowMonitor = monitor;
  }

  public void setMessageLogger(ResultLogger logger)
  {
    this.resultLogger = logger;
  }

  public SqlCommand getCommandToUse(String sql)
  {
    return this.cmdMapper.getCommandToUse(sql).getCommand();
  }

  public StatementRunnerResult runStatement(String aSql)
    throws SQLException, Exception
  {
    StatementRunnerResult result = null;

    if (this.prompter != null)
    {
      boolean goOn = this.prompter.processParameterPrompts(aSql);
      if (!goOn)
      {
        result = new StatementRunnerResult(aSql);
        result.setPromptingWasCancelled();
        return result;
      }
    }

    CommandCtx ctx = this.cmdMapper.getCommandToUse(aSql);
    this.currentCommand = ctx == null ? null : ctx.getCommand();
    this.currentSqlVerb = ctx == null ? null : ctx.getVerb();

    if (this.currentCommand == null)
    {
      return null;
    }

    final CallerInfo ci = new CallerInfo(){};
    this.currentCommand.setVariablePoolID(variablePoolID);

    if (!this.currentCommand.isModeSupported(WbManager.getInstance().getRunMode()))
    {
      result = new StatementRunnerResult();
      result.setSuccess();
      LogMgr.logWarning(ci, currentCommand.getVerb() + " not supported in mode " + WbManager.getInstance().getRunMode().toString() + ". The statement has been ignored.");
      return result;
    }

    if (this.currentConnection == null && this.currentCommand.isConnectionRequired())
    {
      SQLException ex = new SQLException("Cannot execute command '" + currentSqlVerb + "' without a connection!")
      {
        @Override
        public String getLocalizedMessage()
        {
          return ResourceMgr.getFormattedString("ErrConnRequired", currentSqlVerb);
        }
      };
      throw ex;
    }

    this.isTransactionCommand = false;
    if (currentCommand instanceof SqlCommand && currentConnection != null && currentConnection.getDbSettings() != null)
    {
      // We only need to check additional "transaction commands" for generic commands.
      // see endReadonlyTransaction()
      Set<String> commands = currentConnection.getDbSettings().getAdditionalTransactionCommands();
      this.isTransactionCommand = commands.contains(currentSqlVerb);
    }

    this.currentCommand.setStatementRunner(this);
    this.currentCommand.setRowMonitor(this.rowMonitor);
    this.currentCommand.setMessageLogger(this.resultLogger);
    if (currentConsumer != null)
    {
      this.currentCommand.enableMessageBuffering();
    }
    this.currentCommand.setUseMessageLoggerForResults(this.useMessageLoggerForResult);
    if (currentConsumer != null && currentConsumer.ignoreMaxRows())
    {
      this.currentCommand.setMaxRows(0);
    }
    else
    {
      this.currentCommand.setMaxRows(maxRows);
    }
    this.currentCommand.setQueryTimeout(queryTimeout);
    this.currentCommand.setConnection(this.currentConnection);
    this.currentCommand.setParameterPrompter(this.prompter);
    this.currentCommand.setErrorReportLevel(errorLevel);
    this.currentCommand.setShowDataLoading(this.showDataLoadingProgress);

    String realSql = aSql;
    if (VariablePool.getInstance(currentCommand.getVariablePoolID()).getParameterCount() > 0)
    {
      realSql = VariablePool.getInstance(currentCommand.getVariablePoolID()).replaceAllParameters(aSql);
      if (Settings.getInstance().getLogParameterSubstitution() && LogMgr.isDebugEnabled())
      {
        if (StringUtil.equalString(aSql, realSql))
        {
          LogMgr.logDebug(ci, "No variables replaced for:\n" + aSql);
        }
        else
        {
          LogMgr.logDebug(ci, "Variable substitution:\n--- [statement before] ---\n" + aSql + "\n--- [statement after] ---\n" + realSql  + "\n--- [end] ---");
        }
      }
    }

    if (!currentCommand.isModificationAllowed(currentConnection, realSql))
    {
      ConnectionProfile target = currentCommand.getModificationTarget(currentConnection, aSql);
      String profileName = (target == null ? "" : target.getName());
      result = new StatementRunnerResult();
      String verb = SqlParsingUtil.getInstance(currentConnection).getSqlVerb(aSql);
      String msg = ResourceMgr.getFormattedString("MsgReadOnlyMode", profileName, verb);
      LogMgr.logWarning(ci, "Statement " + verb + " ignored because connection is set to read only!");
      result.addWarning(msg);
      result.setSuccess();
      return result;
    }

    if (controller != null && currentCommand.needConfirmation(currentConnection, realSql))
    {
      boolean doExecute = this.controller.confirmStatementExecution(realSql);
      if (!doExecute)
      {
        result = new StatementRunnerResult();
        String msg = ResourceMgr.getString("MsgStatementCancelled");
        result.addWarning(msg);
        result.setSuccess();
        return result;
      }
    }

    realSql = statementHook.preExec(this, realSql);
    if (traceStatements && messageOutput != null)
    {
      messageOutput.printMessage(realSql);
    }

    List<WbAnnotation> statementAnnotations = WbAnnotation.readAllAnnotations(realSql, crossTab, removeEmpty, removeResult);
    int crosstabIndex = statementAnnotations.indexOf(crossTab);

    currentCommand.setAlwaysBufferResults(crosstabIndex >= 0);

    long sqlExecStart = System.currentTimeMillis();

    if (realSql == null)
    {
      // this can happen when the statement hook signalled to not execute the statement
      result = new StatementRunnerResult();
    }
    else
    {
      result = this.currentCommand.execute(realSql);
    }

    if (this.currentCommand instanceof WbStartBatch && result.isSuccess())
    {
      this.batchCommand = (WbStartBatch)this.currentCommand;
    }
    else if (this.batchCommand != null && this.currentCommand instanceof WbEndBatch)
    {
      result = this.batchCommand.executeBatch();
    }

    if (statementAnnotations.contains(removeEmpty))
    {
      removeEmptyResults(result);
    }

    if (statementAnnotations.contains(removeResult))
    {
      result.clearResultData();
    }

    if (crosstabIndex > -1)
    {
      processCrossTab(result, statementAnnotations.get(crosstabIndex));
    }

    if (this.currentConsumer != null && currentCommand != currentConsumer)
    {
      this.currentConsumer.consumeResult(result);
    }

    long time = (System.currentTimeMillis() - sqlExecStart);
    statementHook.postExec(this, realSql, result);
    result.setExecutionDuration(time);

    if (Settings.getInstance().getLogAllStatements())
    {
      logStatement(realSql, time, currentConnection);
    }
    return result;
  }

  private void processCrossTab(StatementRunnerResult result, WbAnnotation annotation)
  {
    if (!result.isSuccess()) return;
    List<DataStore> dataStores = result.getDataStores();

    ArgumentParser cmdLine = new ArgumentParser(false);
    cmdLine.addArgument("labelColumn");
    cmdLine.addArgument("addLabel");

    String parameter = annotation.getValue();
    cmdLine.parse(parameter);
    String column = StringUtil.trimToNull(cmdLine.getValue("labelColumn"));
    String addLabel = cmdLine.getValue("addLabel");

    for (int i = 0; i < dataStores.size(); i++)
    {
      DataStore ds = dataStores.get(i);
      DatastoreTransposer transposer = new DatastoreTransposer(ds);
      DataStore crossTabData = transposer.transposeWithLabel(column, addLabel, null);
      dataStores.set(i, crossTabData);
      ds.reset();
    }
  }

  private void removeEmptyResults(StatementRunnerResult result)
  {
    if (!result.isSuccess()) return;
    List<DataStore> dataStores = result.getDataStores();
    if (CollectionUtil.isEmpty(dataStores)) return;

    Iterator<DataStore> itr = dataStores.iterator();
    while (itr.hasNext())
    {
      DataStore ds = itr.next();
      if (ds.getRowCount() == 0)
      {
        itr.remove();
        if (Settings.getInstance().showRemovedResultMessage())
        {
          String query = StringUtil.getMaxSubstring(SqlUtil.makeCleanSql(ds.getGeneratingSql(), false, false, true, currentConnection), 150, " [...]");
          result.addMessageByKey("MsgResultRemoved", query);
        }
      }
    }
  }

  public static void logStatement(String sql, long time, WbConnection conn)
  {
    StringBuilder msg = new StringBuilder(sql.length() + 25);
    msg.append("Executed: ");
    if (conn != null)
    {
      msg.append('(');
      msg.append(conn.toString());
      msg.append(')');
    }

    if (Settings.getInstance().getBoolProperty(Settings.PROP_LOG_CLEAN_SQL, false))
    {
      msg.append(SqlUtil.makeCleanSql(sql, false, true, true, conn));
      msg.append(' ');
    }
    else
    {
      msg.append('\n');
      msg.append(sql);
      msg.append('\n');
    }

    if (time > -1)
    {
      msg.append('(');
      msg.append(Long.toString(time));
      msg.append("ms)");
    }
    LogMgr.logInfo(new CallerInfo(){}, msg);
  }

  public StatementHook getStatementHook()
  {
    return statementHook;
  }

  public ResultSetConsumer getConsumer()
  {
    return currentConsumer;
  }

  public void setConsumer(ResultSetConsumer consumer)
  {
    this.currentConsumer = consumer;
  }

  public void setVerboseLogging(boolean flag)
  {
    this.verboseLogging = flag;
  }

  public boolean getVerboseLogging()
  {
    return this.verboseLogging;
  }

  public void statementDone()
  {
    endReadOnlyTransaction();
    if (this.currentCommand != null && currentCommand != currentConsumer)
    {
      this.currentCommand.done();
      this.currentCommand = null;
    }
  }

  public void cancel()
  {
    synchronized (this)
    {
      try
      {
        if (this.currentConsumer != null)
        {
          this.currentConsumer.cancel();
        }

        if (currentConnection != null && Settings.getInstance().useOracleNativeCancel())
        {
          currentConnection.oracleCancel();
        }

        if (this.currentCommand != null)
        {
          this.currentCommand.cancel();
        }

      }
      catch (Exception th)
      {
        LogMgr.logWarning(new CallerInfo(){}, "Error when cancelling statement", th);
      }
    }
  }

  public void abort()
  {
    endReadOnlyTransaction();
    this.savepoint = null;
    this.currentCommand = null;
    this.currentConsumer = null;

    if (mainConnection != null)
    {
      this.currentConnection = mainConnection;
      mainConnection = null;
    }
  }

  public void done()
  {
    synchronized (this)
    {
      endReadOnlyTransaction();
      this.releaseSavepoint();
      this.currentConsumer = null;
      this.restoreMainConnection();
      if (currentConnection != null)
      {
        this.currentConnection.clearWarnings();
      }
    }
  }

  public SavepointStrategy getSavepointStrategy()
  {
    return useSavepoint;
  }

  public void setSavepointStrategy(SavepointStrategy newStrategy)
  {
    useSavepoint = newStrategy;
  }

  public void setUseSavepoint(boolean flag)
  {
    if (flag)
    {
      this.useSavepoint = SavepointStrategy.always;
    }
    else
    {
      this.useSavepoint = SavepointStrategy.never;
    }
  }

  public boolean useSavepointForDML()
  {
    if (currentConnection == null) return false;

    switch (useSavepoint)
    {
      case always:
        return true;
      case never:
        return false;
      default:
        return currentConnection.getDbSettings().useSavePointForDML();
    }
  }

  public boolean useSavepointForDDL()
  {
    if (currentConnection == null) return false;

    switch (useSavepoint)
    {
      case always:
        return true;
      case never:
        return false;
      default:
        return currentConnection.getDbSettings().useSavePointForDDL();
    }
  }

  public void setSavepoint()
  {
    if (this.savepoint != null) return;

    try
    {
      this.savepoint = this.currentConnection.setSavepoint(new CallerInfo(){});
    }
    catch (SQLException e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error creating savepoint", e);
      this.savepoint = null;
    }
    catch (Throwable th)
    {
      LogMgr.logError(new CallerInfo(){}, "Savepoints not supported!", th);
      this.savepoint = null;
    }
  }

  public void releaseSavepoint()
  {
    if (this.savepoint == null || this.currentConnection == null) return;
    try
    {
      this.currentConnection.releaseSavepoint(savepoint, new CallerInfo(){});
    }
    finally
    {
      this.savepoint = null;
    }
  }

  public void rollbackSavepoint()
  {
    if (this.savepoint == null) return;
    try
    {
      this.currentConnection.rollback(savepoint, new CallerInfo(){});
    }
    finally
    {
      this.savepoint = null;
    }
  }

}
