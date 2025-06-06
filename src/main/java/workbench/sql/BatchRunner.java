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
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.swing.JOptionPane;

import workbench.AppArguments;
import workbench.WbManager;
import workbench.console.ConsolePrompter;
import workbench.console.ConsoleSettings;
import workbench.console.ConsoleStatusBar;
import workbench.console.DataStorePrinter;
import workbench.console.ExternalPager;
import workbench.console.RowDisplay;
import workbench.interfaces.ExecutionController;
import workbench.interfaces.ParameterPrompter;
import workbench.interfaces.ResultLogger;
import workbench.interfaces.ResultSetConsumer;
import workbench.interfaces.ScriptErrorHandler;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;
import workbench.ssh.SshConfig;
import workbench.ssh.SshException;
import workbench.ssh.SshHostConfig;

import workbench.db.ConnectionMgr;
import workbench.db.ConnectionProfile;
import workbench.db.WbConnection;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.GenericRowMonitor;
import workbench.gui.profiles.ProfileKey;

import workbench.storage.DataStore;
import workbench.storage.RowActionMonitor;

import workbench.sql.parser.ScriptParser;
import workbench.sql.wbcommands.CommonArgs;
import workbench.sql.wbcommands.ConnectionDescriptor;
import workbench.sql.wbcommands.InvalidConnectionDescriptor;
import workbench.sql.wbcommands.WbConnect;

import workbench.util.ArgumentParser;
import workbench.util.CollectionUtil;
import workbench.util.DurationFormatter;
import workbench.util.EncodingUtil;
import workbench.util.ExceptionUtil;
import workbench.util.FileDialogUtil;
import workbench.util.MessageBuffer;
import workbench.util.Replacer;
import workbench.util.StringUtil;
import workbench.util.WbFile;
import workbench.util.WbStringTokenizer;

/**
 * A class to run several statements from a script file.
 * This is used when running SQL Workbench in batch/console mode and
 * for the {@link workbench.sql.wbcommands.WbInclude} command.
 *
 * @see workbench.console.SQLConsole
 * @see workbench.sql.wbcommands.WbInclude
 *
 * @author  Thomas Kellerer
 */
public class BatchRunner
  implements PropertyChangeListener
{
  public static final String CMD_LINE_PROFILE_NAME = "$Wb$CommandLineProfile";
  private List<String> filenames;
  private final StatementRunner stmtRunner;
  private WbConnection connection;
  private boolean abortOnError;
  private String successScript;
  private String errorScript;
  private DelimiterDefinition delimiter;
  private boolean showResultSets;
  private boolean showTiming = true;
  private ExecutionStatus status = ExecutionStatus.Success;
  private ConnectionProfile profile;
  private ResultLogger resultDisplay;
  private boolean cancelExecution;
  private RowActionMonitor rowMonitor;
  private boolean verboseLogging = true;
  private boolean checkEscapedQuotes;
  private String encoding;
  private boolean showProgress;
  private PrintStream console = System.out;
  private boolean consolidateMessages;
  private boolean showStatementWithResult = true;
  private boolean showSummary = verboseLogging;
  private boolean showRowCounts = showSummary;
  private boolean optimizeCols = true;
  private boolean showStatementTiming = true;
  private boolean printStatements;
  private String connectionId = "BatchRunner";
  private String command;
  private boolean storeErrorMessages;
  private MessageBuffer errors;
  private final List<DataStore> queryResults = new ArrayList<>();
  private Replacer replacer;
  private boolean isBusy;
  private ErrorDescriptor lastError;
  private int errorStatementIndex;
  private ScriptErrorHandler retryHandler;
  private int maxDisplaySize = Integer.MAX_VALUE;
  private WbFile pagerToUse;
  private String variablePoolID;

  public BatchRunner()
  {
    this.stmtRunner = new StatementRunner();
    this.stmtRunner.setUseMessageLoggerForResult(false);
    this.stmtRunner.setErrorReportLevel(ErrorReportLevel.full);
    this.stmtRunner.addChangeListener(this);
  }

  public BatchRunner(String aFilelist)
  {
    this();
    WbStringTokenizer tok = new WbStringTokenizer(aFilelist, ",", false, "\"'", false);
    List<String> names = tok.getAllTokens();
    filenames = new ArrayList<>(names.size());
    for (String name : names)
    {
      filenames.add(name.trim());
    }
  }

  public BatchRunner(List<WbFile> files)
  {
    this();
    filenames = new ArrayList<>(files.size());
    for (File f : files)
    {
      filenames.add(f.getAbsolutePath());
    }
  }

  public void done()
  {
    if (errors != null) errors.clear();
    queryResults.clear();
    stmtRunner.done();
  }

  public void setVariablePoolID(String variablePoolID)
  {
    this.variablePoolID = variablePoolID;
    this.stmtRunner.setVariablePoolID(variablePoolID);
  }

  public void setMaxColumnDisplayLength(int maxLength)
  {
    this.maxDisplaySize = maxLength;
  }

  public void setRetryHandler(ScriptErrorHandler handler)
  {
    this.retryHandler = handler;
  }

  public void setTraceOutput(OutputPrinter tracer)
  {
    this.stmtRunner.setMessagePrinter(tracer);
  }

  public void setReplacer(Replacer replacer)
  {
    this.replacer = replacer;
  }

  public void setStoreErrors(boolean flag)
  {
    this.storeErrorMessages = flag;
  }

  public void setPrintStatements(boolean flag)
  {
    this.printStatements = flag;
  }

  public void setErrorStatementLogging(ErrorReportLevel level)
  {
    stmtRunner.setErrorReportLevel(level);
  }

  public ErrorDescriptor getLastError()
  {
    return lastError;
  }

  public int getLastErrorStatementIndex()
  {
    return errorStatementIndex;
  }

  public boolean isBusy()
  {
    return isBusy;
  }

  /**
   * The baseDir is used when including other scripts using WbInclude.
   *
   * If the filename of the included script is a relative filename
   * then the StatementRunner will assume the script is located relative
   * to the baseDir. This call is delegated to
   * {@link StatementRunner#setBaseDir(String)}
   *
   * @param dir the base directory to be used
   * @see StatementRunner#setBaseDir(String)
   */
  public void setBaseDir(String dir)
  {
    this.stmtRunner.setBaseDir(dir);
  }

  public void setHistoryProvider(StatementHistory provider)
  {
    this.stmtRunner.setSqlHistory(provider);
  }

  public void setUseSavepoint(boolean flag)
  {
    stmtRunner.setUseSavepoint(flag);
  }

  public void setConnectionId(String id)
  {
    this.connectionId = id;
  }

  public WbConnection getConnection()
  {
    return this.connection;
  }

  public boolean hasPendingActions()
  {
    return stmtRunner.hasPendingActions();
  }

  public SqlCommand getCommand(String verb)
  {
    return stmtRunner.cmdMapper.getCommandFromVerb(verb);
  }

  public void addCommand(SqlCommand cmd)
  {
    stmtRunner.cmdMapper.addCommand(cmd);
  }

  public void setExecutionController(ExecutionController controller)
  {
    this.stmtRunner.setExecutionController(controller);
  }

  public void setParameterPrompter(ParameterPrompter p)
  {
    this.stmtRunner.setParameterPrompter(p);
  }

  public void setExternalPager(WbFile pager)
  {
    this.pagerToUse = pager;
  }

  public void setConsole(PrintStream output)
  {
    this.console = output;
  }

  public void setShowDataLoading(boolean flag)
  {
    stmtRunner.setShowDataLoadingProgress(flag);
  }

  public void setShowStatementWithResult(boolean flag)
  {
    this.showStatementWithResult = flag;
  }

  public void showResultSets(boolean flag)
  {
    this.showResultSets = flag;
  }

  public void setOptimizeColWidths(boolean flag)
  {
    this.optimizeCols = flag;
  }

  public void setShowStatementSummary(boolean flag)
  {
    this.showSummary = flag;
  }

  public void setShowRowCounts(boolean flag)
  {
    this.showRowCounts = flag;
  }

  public boolean getVerboseLogging()
  {
    return this.verboseLogging;
  }

  public void setVerboseLogging(boolean flag)
  {
    this.verboseLogging = flag;
    this.showSummary = flag;
    this.stmtRunner.setVerboseLogging(flag);
    this.showTiming = this.verboseLogging;
    this.showRowCounts = flag;
  }

  public void setConsolidateLog(boolean flag)
  {
    this.consolidateMessages = flag;
  }

  public void setIgnoreDropErrors(boolean flag)
  {
    this.stmtRunner.setIgnoreDropErrors(flag);
  }

  public ConnectionProfile getProfile()
  {
    return this.profile;
  }

  public boolean hasProfile()
  {
    return this.profile != null;
  }

  public void setProfile(ConnectionProfile aProfile)
  {
    this.profile = aProfile;
  }

  public void setDelimiter(DelimiterDefinition delim)
  {
    if (delim != null) this.delimiter = delim;
  }

  public void setConnection(WbConnection conn)
  {
    this.connection = conn;
    this.stmtRunner.setConnection(this.connection);
  }

  public boolean isConnected()
  {
    return this.connection != null;
  }

  public boolean wasCancelled()
  {
    return cancelExecution;
  }

  public void setShowProgress(boolean flag)
  {
    showProgress = flag;
    if (!showProgress)
    {
      setRowMonitor(null);
    }
    else if (this.rowMonitor == null && !WbManager.getInstance().isGUIMode())
    {
      // make sure we have some row monitor available
      // in case no row monitor is set later using setRowMonitor()
      setRowMonitor(new GenericRowMonitor(new ConsoleStatusBar()));
    }
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt)
  {
    if (evt.getSource() == this.stmtRunner)
    {
      this.connection = stmtRunner.getConnection();
    }
  }

  public static void loginPrompt(ConnectionProfile profile, ExecutionController controller)
  {
    boolean promptPwd = profile.needsPasswordPrompt();

    if (profile.getPromptForUsername())
    {
      if (controller == null)
      {
        LogMgr.logError(new CallerInfo(){}, "A login prompt is needed but no ExecutionController was provided.", new NullPointerException("No ExecutionController"));
        return;
      }
      String user = controller.getInput(ResourceMgr.getString("LblUsername"));
      profile.setTemporaryUsername(user);
      profile.setInputPassword(null);
      promptPwd = true;
    }

    if (promptPwd)
    {
      if (controller == null)
      {
        LogMgr.logError(new CallerInfo(){}, "A passwort prompt is needed but no ExecutionController was provided.", null);
        return;
      }
      String pwd = controller.getPassword(ResourceMgr.getFormattedString("MsgInputPwd", profile.getUrlInfo()));
      profile.setInputPassword(pwd);
    }

    if (profile.needsSSHPasswordPrompt())
    {
      SshHostConfig config = profile.getSshConfig().getSshHostConfig();
      String key;

      if (config.getPrivateKeyFile() == null)
      {
        key = "MsgInputSshPwd";
      }
      else
      {
        key = "MsgInputSshPassPhrase";
      }
      String pwd = controller.getPassword(ResourceMgr.getString(key));
      config.setTemporaryPassword(pwd);
    }
  }

  public void connect()
    throws SQLException, ClassNotFoundException, SshException
  {
    this.connection = null;

    if (this.profile == null)
    {
      // Allow batch runs without a profile for e.g. running a single WbCopy
      LogMgr.logWarning(new CallerInfo(){}, "No profile defined, proceeding without a connection.");
      status = ExecutionStatus.Success;
      return;
    }

    loginPrompt(profile, stmtRunner.getExecutionController());

    try
    {
      ConnectionMgr mgr = ConnectionMgr.getInstance();
      WbConnection c = mgr.getConnection(this.profile, connectionId, variablePoolID);

      this.setConnection(c);
      String info = c.getDisplayString();
      LogMgr.logInfo(new CallerInfo(){},  ResourceMgr.getFormattedString("MsgBatchConnectOk", c.getDisplayString()));
      if (verboseLogging)
      {
        this.printMessage(ResourceMgr.getFormattedString("MsgBatchConnectOk", info));
        String warn = c.getWarnings();
        if (!StringUtil.isEmpty(warn) && !c.getProfile().isHideWarnings())
        {
          printMessage(warn);
          LogMgr.logWarning(new CallerInfo(){}, "Connection returned warnings: " + warn);
        }
      }
      status = ExecutionStatus.Success;
    }
    catch (ClassNotFoundException e)
    {
      String error = ResourceMgr.getString("ErrDriverNotFound");
      error = StringUtil.replace(error, "%class%", profile.getDriverclass());
      LogMgr.logError(new CallerInfo(){}, error, null);
      printMessage(error);
      status = ExecutionStatus.Error;
      throw e;
    }
    catch (SQLException e)
    {
      status = ExecutionStatus.Error;
      LogMgr.logError(new CallerInfo(){}, "Connection failed", e);
      printMessage(ResourceMgr.getString("ErrConnectFailed"));
      printMessage(ExceptionUtil.getDisplay(e));
      throw e;
    }
  }

  public void setMaxRows(int rows)
  {
    if (this.stmtRunner != null)
    {
      this.stmtRunner.setMaxRows(rows);
    }
  }

  public void setPersistentConnect(boolean flag)
  {
    WbConnect connect = (WbConnect)getCommand(WbConnect.VERB);
    connect.setPersistentChange(flag);
  }

  public List<DataStore> getQueryResults()
  {
    return queryResults;
  }

  public boolean isSuccess()
  {
    return status != ExecutionStatus.Error;
  }

  public void setSuccessScript(String aFilename)
  {
    if (aFilename == null) return;
    WbFile f = new WbFile(aFilename);
    if (f.exists() && !f.isDirectory())
    {
      this.successScript = aFilename;
    }
    else
    {
      LogMgr.logWarning(new CallerInfo(){}, "File '" + aFilename + "' specified for success script not found. No success script is used!");
      this.successScript = null;
    }
  }

  public void setErrorScript(String aFilename)
  {
    if (aFilename == null) return;
    File f = new WbFile(aFilename);
    if (f.exists() && !f.isDirectory())
    {
      this.errorScript = aFilename;
    }
    else
    {
      LogMgr.logWarning(new CallerInfo(){}, "File '" + aFilename + "' specified for error script not found. No error script is used!");
      this.errorScript = null;
    }
  }

  public void setRowMonitor(RowActionMonitor mon)
  {
    this.rowMonitor = mon;
    this.stmtRunner.setRowMonitor(this.rowMonitor);
  }

  /**
   * Define a sql script that should be run instead of a list of files.
   * @param sql
   */
  public void setScriptToRun(String sql)
  {
    command = sql;
  }

  protected void runScript()
  {

    String typeKey = "batchRunnerScriptLoop";

    if (this.rowMonitor  != null)
    {
      this.rowMonitor.setMonitorType(RowActionMonitor.MONITOR_PLAIN);
      this.rowMonitor.saveCurrentType(typeKey);
    }

    try
    {
      status = runScript(command);
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, ResourceMgr.getString("MsgBatchStatementError"), e);
      String msg = ExceptionUtil.getDisplay(e);
      if (showProgress) printMessage(""); // force newline in case progress reporting was turned on
      printMessage(ResourceMgr.getString("TxtError") + ": " + msg);
      status = ExecutionStatus.Error;
    }
    finally
    {
      if (rowMonitor != null)
      {
        rowMonitor.restoreType(typeKey);
      }
    }
  }

  public void execute()
  {
    queryResults.clear();
    try
    {
      if (CollectionUtil.isNonEmpty(filenames))
      {
        runFiles();
      }
      else
      {
        runScript();
      }
    }
    finally
    {
      stmtRunner.done();
    }
  }

  protected void runFiles()
  {
    int count = this.filenames.size();

    String typeKey = "batchRunnerFileLoop";

    if (this.rowMonitor  != null)
    {
      this.rowMonitor.setMonitorType(RowActionMonitor.MONITOR_PLAIN);
      this.rowMonitor.saveCurrentType(typeKey);
    }

    final CallerInfo ci = new CallerInfo(){};
    int currentFileIndex = 0;

    for (String file : filenames)
    {
      currentFileIndex ++;

      WbFile fo = new WbFile(file);

      if (this.rowMonitor != null)
      {
        this.rowMonitor.restoreType(typeKey);
        this.rowMonitor.setCurrentObject(file, currentFileIndex, count);
      }

      try
      {
        String msg = ResourceMgr.getString("MsgBatchProcessingFile") + " " + fo.getFullPath();
        LogMgr.logInfo(new CallerInfo(){}, msg);
        if (this.resultDisplay != null && verboseLogging)
        {
          this.resultDisplay.appendToLog(msg);
          this.resultDisplay.appendToLog("\n");
        }
        String dir = fo.getAbsoluteFile().getParent();
        this.setBaseDir(dir);

        status = this.executeScript(fo);
      }
      catch (Exception e)
      {
        status = ExecutionStatus.Error;
        LogMgr.logError(ci, ResourceMgr.getString("MsgBatchScriptFileError") + " " + file, e);
        String msg = null;

        if (e instanceof FileNotFoundException)
        {
          msg = ResourceMgr.getFormattedString("ErrFileNotFound", file);
        }
        else
        {
          msg = e.getMessage();
        }
        if (showProgress) printMessage(""); // force newline in case progress reporting was turned on
        printMessage(ResourceMgr.getString("TxtError") + ": " + msg);
      }
      if (status == ExecutionStatus.Error && abortOnError)
      {
        break;
      }
    }

    if (this.rowMonitor  != null)
    {
      this.rowMonitor.jobFinished();
    }

    if (abortOnError && status == ExecutionStatus.Error)
    {
      try
      {
        if (this.errorScript != null)
        {
          WbFile f = new WbFile(errorScript);
          LogMgr.logInfo(ci, ResourceMgr.getString("MsgBatchExecutingErrorScript") + " " + f.getFullPath());
          this.executeScript(f);
        }
      }
      catch (Exception e)
      {
        LogMgr.logError(ci, ResourceMgr.getString("MsgBatchScriptFileError") + " " + this.errorScript, e);
      }
    }
    else
    {
      try
      {
        if (this.successScript != null)
        {
          WbFile f = new WbFile(successScript);
          LogMgr.logInfo(ci, ResourceMgr.getString("MsgBatchExecutingSuccessScript") + " " + f.getFullPath());
          this.executeScript(f);
        }
      }
      catch (Exception e)
      {
        LogMgr.logError(ci, ResourceMgr.getString("MsgBatchScriptFileError") + " " + this.successScript, e);
      }
    }
  }

  public String getMessages()
  {
    if (errors == null) return null;
    return errors.getBuffer().toString();
  }

  public void cancel()
  {
    this.cancelExecution = true;
    if (this.stmtRunner != null)
    {
      this.stmtRunner.cancel();
    }
  }

  public void abort()
  {
    this.cancelExecution = true;
    if (this.stmtRunner != null)
    {
      this.stmtRunner.abort();
    }
  }

  public ResultSetConsumer getResultSetConsumer()
  {
    if (this.stmtRunner != null)
    {
      return stmtRunner.getConsumer();
    }
    return null;
  }

  public void setResultSetConsumer(ResultSetConsumer consumer)
  {
    if (this.stmtRunner != null)
    {
      this.stmtRunner.setConsumer(consumer);
    }
  }

  private ScriptParser createParser()
  {
    ScriptParser parser = new ScriptParser(connection);
    // If no delimiter has been defined, then use the alternate delimiter from the connection
    if (this.delimiter == null)
    {
      DelimiterDefinition altDelim = Settings.getInstance().getAlternateDelimiter(this.connection, null);
      parser.setDelimiters(DelimiterDefinition.STANDARD_DELIMITER, altDelim);
    }
    else
    {
      // If the ScriptParser supports dynamic alternate delimiters
      // use the defined delimiter as the alternate delimiter
      if (parser.supportsMixedDelimiter())
      {
        parser.setAlternateDelimiter(this.delimiter);
      }
      else
      {
        // if the parser only supports a single delimiter
        // use the defined one, and don't use an alternate delimiter
        parser.setDelimiters(this.delimiter, null);
      }
    }

    parser.setCheckEscapedQuotes(this.checkEscapedQuotes);
    return parser;
  }

  private ExecutionStatus executeScript(WbFile scriptFile)
    throws IOException
  {
    try
    {
      isBusy = true;
      ScriptParser parser = createParser();
      parser.setFile(scriptFile, this.encoding);
      return executeScript(parser);
    }
    finally
    {
      isBusy = false;
    }
  }

  /**
   * Execute the given script
   * @param script
   * @return true if an error occurred
   * @throws IOException
   */
  public ExecutionStatus runScript(String script)
    throws IOException
  {
    ScriptParser parser = createParser();
    parser.setScript(script);
    try
    {
      isBusy = true;
      status = executeScript(parser);
      if (this.rowMonitor  != null)
      {
        this.rowMonitor.jobFinished();
      }
      return status;
    }
    finally
    {
      isBusy = false;
    }
  }

  private ExecutionStatus executeScript(ScriptParser parser)
    throws IOException
  {
    status = ExecutionStatus.Success;
    errors = null;

    this.cancelExecution = false;

    int currentStatementNr = 0;
    long start, end;

    int interval = 1;

    start = System.currentTimeMillis();

    parser.startIterator();
    long totalRows = 0;
    long errorCount = 0;

    final CallerInfo ci = new CallerInfo(){};

    lastError = null;
    errorStatementIndex = -1;
    boolean ignoreAllErrors = false;
    int commandIndex = 0;
    int numStatements = parser.getStatementCount();

    String msgFormat = numStatements > 0 ? ResourceMgr.getString("TxtBatchProcess") : ResourceMgr.getString("TxtBatchProcessUnknown");

    if (parser.getScriptFile() != null && Settings.getInstance().showScriptNameForInclude())
    {
      msgFormat += " (" + parser.getScriptFile().getName()+ ")";
    }

    String sql = null;
    while ((sql = parser.getNextCommand()) != null)
    {
      boolean ignoreThisError = false;

      if (sql.isEmpty())
      {
        continue;
      }

      try
      {
        if (replacer != null)
        {
          sql = replacer.replace(sql);
        }

        if (printStatements)
        {
          printMessage(sql.trim());
        }

        if (this.rowMonitor != null && (currentStatementNr % interval == 0))
        {
          String currentMsg = MessageFormat.format(msgFormat, currentStatementNr + 1, numStatements);
          this.rowMonitor.setCurrentObject(currentMsg, currentStatementNr, numStatements);
          if (currentStatementNr >= 100)
          {
            // for the first 100 statements show each one
            // Then change the progress interval to improve performance for long scripts
            interval = 10;
          }
        }

        int monitorType = 0;
        if (rowMonitor != null)
        {
          monitorType = rowMonitor.getMonitorType();
        }

        long verbstart = System.currentTimeMillis();
        StatementRunnerResult result = this.stmtRunner.runStatement(sql);
        long verbend = System.currentTimeMillis();

        if (rowMonitor != null)
        {
          rowMonitor.setMonitorType(monitorType);
        }

        status = ExecutionStatus.Success;

        if (result != null)
        {
          result.setExecutionDuration(verbend - verbstart);
          status = result.getStatus();

          // We have to store the result of hasMessages()
          // as the getMessages() will clear the buffer
          // and a subsequent call to hasMessages() will return false;
          boolean hasMessage = result.hasMessages();
          MessagePriority msgPrio = result.getMessagePriority();
          String feedback = hasMessage ? result.getMessages().toString() : "";

          if (status == ExecutionStatus.Error)
          {
            lastError = result.getErrorDescriptor();
            if (lastError != null)
            {
              lastError.setScriptFile(parser.getScriptFile());
            }
            errorStatementIndex = commandIndex;
            errorCount++;
            if (parser.getScriptFile() != null)
            {
              feedback += "\n" + ResourceMgr.getFormattedString("MsgInFile", parser.getScriptFile().getFullPath());
            }
            feedback += "\n";

            if (retryHandler != null && !ignoreAllErrors)
            {
              if (lastError == null)
              {
                // can happen when having multiple levels of WbInclude calls
                lastError = new ErrorDescriptor();
                lastError.setErrorMessage(feedback);
              }
              int choice = retryHandler.scriptErrorPrompt(commandIndex, lastError, parser, 0);
              switch (choice)
              {
                case WbSwingUtilities.IGNORE_ALL:
                  ignoreAllErrors = true;
                  break;
                case WbSwingUtilities.IGNORE_ONE:
                  ignoreThisError = true;
                  break;
                case JOptionPane.CANCEL_OPTION:
                  ignoreAllErrors = false;
                  ignoreThisError = false;
                  cancelExecution = true;
              }
            }

            if (storeErrorMessages)
            {
              if (errors == null)
              {
                errors = new MessageBuffer();
              }
              errors.appendNewLine();
              errors.append(feedback);
            }

            if (ignoreAllErrors)
            {
              ignoreThisError = true;
            }
          }
          else
          {
            if (result.hasWarning() && StringUtil.isNotBlank(feedback))
            {
              LogMgr.logWarning(ci, feedback);
            }
            totalRows += result.getTotalUpdateCount();
          }

          printResults(sql, result);

          if (hasMessage && (this.stmtRunner.getVerboseLogging() || status == ExecutionStatus.Error || msgPrio == MessagePriority.high))
          {
            if (!this.consolidateMessages)
            {
              if (!showResultSets) printMessage(""); // force newline
              this.printMessage(feedback);
            }
          }
          else if (result.hasWarning() && stmtRunner.getVerboseLogging())
          {
            String verb = stmtRunner.getParsingUtil().getSqlVerb(sql);
            String msg = StringUtil.replace(ResourceMgr.getString("MsgStmtCompletedWarn"), "%verb%", verb);
            this.printMessage("\n" + msg);
          }
          currentStatementNr ++;

          if (this.showTiming && showStatementTiming && !consolidateMessages)
          {
            this.printMessage(result.getTimingMessage());
          }
        }

        if (result != null && result.stopScript())
        {
          String cancelMsg = ResourceMgr.getString("MsgScriptCancelled");
          printMessage(cancelMsg);
          break;
        }

        if (this.cancelExecution)
        {
          if (verboseLogging) this.printMessage(ResourceMgr.getString("MsgStatementCancelled"));
          break;
        }

        commandIndex++;
      }
      catch (Exception e)
      {
        VariablePool.getInstance(variablePoolID).setLastError(e);
        LogMgr.logError(ci, ResourceMgr.getString("MsgBatchStatementError") + " "  + sql, e);
        printMessage(ExceptionUtil.getDisplay(e));
        status = ExecutionStatus.Error;
        break;
      }
      finally
      {
        stmtRunner.statementDone();
      }

      if (status == ExecutionStatus.Error && abortOnError && !ignoreThisError) break;
    }

    end = System.currentTimeMillis();

    if (showSummary)
    {
      StringBuilder msg = new StringBuilder(50);
      WbFile scriptFile = parser.getScriptFile();
      if (scriptFile != null)
      {
        msg.append(scriptFile.getFullPath());
        msg.append(": ");
      }
      msg.append(ResourceMgr.getFormattedString("MsgTotalStatementsExecuted", currentStatementNr));
      msg.append('\n');
      if (resultDisplay == null) msg.insert(0, '\n'); // force newline on console
      this.printMessage(msg.toString());
    }

    if (consolidateMessages)
    {
      if (errorCount > 0)
      {
        printMessage((resultDisplay == null ? "\n" : "") + ResourceMgr.getFormattedString("MsgTotalStatementsFailed", errorCount));
      }
      if (verboseLogging) this.printMessage(ResourceMgr.getFormattedString("MsgRowsAffected", totalRows));
    }

    parser.done();

    if (this.showTiming)
    {
      long millis = (end - start);
      DurationFormatter f = new DurationFormatter();
      boolean includeFraction = (millis < DurationFormatter.ONE_MINUTE);
      String time = f.formatDuration(millis, Settings.getInstance().getDurationFormat(), includeFraction);
      String m = ResourceMgr.getString("MsgExecTime") + " " + time;
      this.printMessage(m);
    }

    return status;
  }

  private void printResults(String sql, StatementRunnerResult result)
  {
    if (!this.showResultSets) return;

    if (result == null) return;
    if (!result.isSuccess()) return;
    if (!result.hasDataStores()) return;

    List<DataStore> data = result.getDataStores();

    if (console == null)
    {
      for (DataStore ds : data)
      {
        queryResults.add(ds);
      }
      return;
    }

    if (showStatementWithResult && !printStatements)
    {
      console.println(sql.trim());
    }

    RowDisplay current = ConsoleSettings.getInstance().getNextRowDisplay();
    boolean rowsAsLine = (current != null && current == RowDisplay.SingleLine);

    boolean showRowCount = result.getShowRowCount() && showRowCounts;

    PrintStream output = this.console;
    ExternalPager pager = null;
    if (pagerToUse != null && pagerToUse.exists())
    {
      pager = new ExternalPager(this.pagerToUse);
      if (pager.isValid())
      {
        pager.initialize();
        output = pager.getOutput();
      }
    }

    try
    {
      for (int i=0; i < data.size(); i++)
      {
        DataStore ds = data.get(i);
        if (ds != null)
        {
          DataStorePrinter printer = new DataStorePrinter(ds);
          printer.setMaxDisplaySize(maxDisplaySize);
          printer.setFormatColumns(optimizeCols);
          printer.setPrintRowCount(showRowCount);
          printer.setPrintRowsAsLine(rowsAsLine);
          printer.printTo(output);
          if (i < data.size() -1) output.println();
        }
      }
    }
    finally
    {
      if (pager != null)
      {
        pager.waitFor();
        pager.done();
      }
    }
  }

  public void setEncoding(String enc)
    throws UnsupportedEncodingException
  {
    if (enc == null)
    {
      this.encoding = null;
    }
    else
    {
      if (!EncodingUtil.isEncodingSupported(enc))
        throw new UnsupportedEncodingException(enc + " encoding not supported!");
      this.encoding = EncodingUtil.cleanupEncoding(enc);
    }
  }

  public void setShowTiming(boolean flag)
  {
    this.showTiming = flag;
    this.showStatementTiming = flag;
  }

  public void setShowStatementTiming(boolean flag)
  {
    this.showStatementTiming = flag;
  }

  public boolean getAbortOnError()
  {
    return abortOnError;
  }

  public void setAbortOnError(boolean aFlag)
  {
    this.abortOnError = aFlag;
  }

  public void setCheckEscapedQuotes(boolean flag)
  {
    this.checkEscapedQuotes = flag;
  }

  public void setMessageLogger(ResultLogger logger)
  {
    this.resultDisplay = logger;
    if (this.stmtRunner != null)
    {
      this.stmtRunner.setMessageLogger(logger);
    }
  }

  private void printMessage(String msg)
  {
    if (msg == null) return;

    if (this.resultDisplay == null)
    {
      if (msg.length() > 0)
      {
        System.out.print('\r');
        System.out.println(msg);
      }
    }
    else
    {
      this.resultDisplay.appendToLog(msg);
      this.resultDisplay.appendToLog("\n");
    }
  }

  public static boolean hasConnectionArgument(ArgumentParser cmdLine)
  {
    if (!cmdLine.hasArguments()) return false;
    if (cmdLine.isArgPresent(AppArguments.ARG_CONN_DESCRIPTOR)) return true;
    if (cmdLine.isArgPresent(AppArguments.ARG_CONN_URL)) return true;
    if (cmdLine.isArgPresent(AppArguments.ARG_CONN_USER)) return true;
    if (cmdLine.isArgPresent(AppArguments.ARG_CONN_JAR)) return true;
    if (cmdLine.isArgPresent(AppArguments.ARG_CONN_DRIVER)) return true;
    if (cmdLine.isArgPresent(AppArguments.ARG_CONN_PWD)) return true;
    return false;
  }

  public static ConnectionProfile createCmdLineProfile(ArgumentParser cmdLine)
    throws InvalidConnectionDescriptor
  {
    return createCmdLineProfile(cmdLine, null, null, true);
  }

  public static ConnectionProfile createCmdLineProfile(ArgumentParser cmdLine, boolean checkDriver)
    throws InvalidConnectionDescriptor
  {
    return createCmdLineProfile(cmdLine, null, null, checkDriver);
  }

  public static ConnectionProfile createCmdLineProfile(ArgumentParser cmdLine, WbConnection currentConnection, String baseDir)
    throws InvalidConnectionDescriptor
  {
    return createCmdLineProfile(cmdLine, currentConnection, baseDir, true);
  }

  public static ConnectionProfile createCmdLineProfile(ArgumentParser cmdLine, WbConnection currentConnection, String baseDir, boolean checkDriver)
    throws InvalidConnectionDescriptor
  {
    if (!hasConnectionArgument(cmdLine)) return null;

    ConnectionProfile result = null;

    String url = cmdLine.getValue(AppArguments.ARG_CONN_URL);
    String driverclass = null;

    if (checkDriver)
    {
      driverclass = cmdLine.getValue(AppArguments.ARG_CONN_DRIVER);
      if (driverclass == null)
      {
        driverclass = cmdLine.getValue(AppArguments.ARG_CONN_DRIVER_CLASS);
      }
    }

    String descriptor = cmdLine.getValue(AppArguments.ARG_CONN_DESCRIPTOR);

    if (descriptor == null)
    {
      if (url == null)
      {
        LogMgr.logWarning(new CallerInfo(){}, "Cannot connect using command line settings without a connection URL!", null);
        return null;
      }

      if (driverclass == null && checkDriver)
      {
        driverclass = ConnectionDescriptor.findDriverClassFromUrl(url);
      }

      if (driverclass == null && checkDriver)
      {
        LogMgr.logWarning(new CallerInfo(){}, "Cannot connect using command line settings without a driver class!", null);
        return null;
      }

      String user = cmdLine.getValue(AppArguments.ARG_CONN_USER);
      String pwd = cmdLine.getValue(AppArguments.ARG_CONN_PWD);
      String jar = cmdLine.getValue(AppArguments.ARG_CONN_JAR);

      if (jar != null && checkDriver)
      {
        WbFile jarFile = new WbFile(jar);
        ConnectionMgr.getInstance().registerDriver(driverclass, jarFile.getFullPath());
      }
      result = ConnectionProfile.createEmptyProfile();
      result.setDriverclass(driverclass);
      result.setUrl(url);

      if (cmdLine.isArgPresent(AppArguments.ARG_CONN_PWD))
      {
        result.setPassword(pwd);
        result.setStorePassword(true);
      }
      else
      {
        result.setStorePassword(false);
      }
      result.setUsername(user);
    }
    else
    {
      ConnectionDescriptor parser = new ConnectionDescriptor();
      result = parser.parseDefinition(descriptor, currentConnection);
    }

    try
    {
      String commit =  cmdLine.getValue(AppArguments.ARG_CONN_AUTOCOMMIT);
      String wksp = cmdLine.getValue(AppArguments.ARG_WORKSPACE);
      String delimDef = cmdLine.getValue(AppArguments.ARG_ALT_DELIMITER);
      String title = cmdLine.getValue(AppArguments.ARG_CONN_NAME, CMD_LINE_PROFILE_NAME);
      DelimiterDefinition delim = DelimiterDefinition.parseCmdLineArgument(delimDef);
      boolean trimCharData = cmdLine.getBoolean(AppArguments.ARG_CONN_TRIM_CHAR, false);
      boolean rollback = cmdLine.getBoolean(AppArguments.ARG_CONN_ROLLBACK, false);
      boolean separate = cmdLine.getBoolean(AppArguments.ARG_CONN_SEPARATE, true);
      boolean ignoreDrop = cmdLine.getBoolean(AppArguments.ARG_IGNORE_DROP, false);

      Map<String, String> props = cmdLine.getMapValue(AppArguments.ARG_CONN_PROPS);

      result.setTemporaryProfile(true);
      result.setName(title);
      result.setDriverName(null);
      result.setStoreExplorerSchema(false);
      result.setRollbackBeforeDisconnect(rollback);
      result.setIgnoreDropErrors(ignoreDrop);
      result.setAlternateDelimiter(delim);
      result.setTrimCharData(trimCharData);
      result.setUseSeparateConnectionPerTab(separate);
      result.setEmptyStringIsNull(cmdLine.getBoolean(AppArguments.ARG_CONN_EMPTYNULL, false));
      result.setRemoveComments(cmdLine.getBoolean(AppArguments.ARG_CONN_REMOVE_COMMENTS, false));
      result.setDetectOpenTransaction(cmdLine.getBoolean(AppArguments.ARG_CONN_CHECK_OPEN_TRANS, false));
      result.setReadOnly(cmdLine.getBoolean(AppArguments.ARG_READ_ONLY, false));
      result.setHideWarnings(cmdLine.getBoolean(AppArguments.ARG_HIDE_WARNINGS, false));
      int fetchSize = cmdLine.getIntValue(AppArguments.ARG_CONN_FETCHSIZE, -1);
      if (fetchSize > -1)
      {
        result.setDefaultFetchSize(fetchSize);
      }

      if (props != null && props.size() > 0)
      {
        Properties p = new Properties();
        p.putAll(props);
        result.setConnectionProperties(p);
      }

      result.setSshConfig(getSshConfig(cmdLine));

      if (!StringUtil.isEmpty(wksp))
      {
        wksp = FileDialogUtil.replaceConfigDir(wksp);
        File f = new WbFile(wksp);
        if (!f.exists() && !f.isAbsolute())
        {
          f = new WbFile(Settings.getInstance().getConfigDir(), wksp);
        }
        if (f.exists())
        {
          result.setWorkspaceFile(f.getAbsolutePath());
        }
      }
      if (!StringUtil.isEmpty(commit))
      {
        result.setAutocommit(StringUtil.stringToBool(commit));
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error creating temporary profile", e);
      result = null;
    }
    return result;
  }

  private static SshConfig getSshConfig(ArgumentParser cmdLine)
  {
    String sshHost = cmdLine.getValue(AppArguments.ARG_CONN_SSH_HOST);
    String sshUser = cmdLine.getValue(AppArguments.ARG_CONN_SSH_USER);
    String sshPwd = cmdLine.getValue(AppArguments.ARG_CONN_SSH_PWD);
    String sshKeyfile = cmdLine.getValue(AppArguments.ARG_CONN_SSH_KEYFILE);
    String sshLocalPort = cmdLine.getValue(AppArguments.ARG_CONN_SSH_LOCAL_PORT);
    String sshPort = cmdLine.getValue(AppArguments.ARG_CONN_SSH_PORT);
    String dbPort = cmdLine.getValue(AppArguments.ARG_CONN_SSH_DB_PORT);
    String dbHost = cmdLine.getValue(AppArguments.ARG_CONN_SSH_DB_HOST);

    if (sshHost != null && sshUser != null)
    {
      SshHostConfig hostConfig = new SshHostConfig();
      hostConfig.setUsername(sshUser);
      hostConfig.setHostname(sshHost);
      hostConfig.setPassword(sshPwd);
      hostConfig.setPrivateKeyFile(sshKeyfile);
      hostConfig.setSshPort(StringUtil.getIntValue(sshPort, 0));
      SshConfig config = new SshConfig();
      config.setHostConfig(hostConfig);
      config.setLocalPort(StringUtil.getIntValue(sshLocalPort, 0));
      config.setDbPort(StringUtil.getIntValue(dbPort,0));
      config.setDbHostname(dbHost);
      return config;
    }

    return null;
  }

  public static BatchRunner createBatchRunner(ArgumentParser cmdLine)
  {
    return createBatchRunner(cmdLine, true);
  }

  public static BatchRunner createBatchRunner(ArgumentParser cmdLine, boolean checkScriptPresence)
  {
    String scripts = cmdLine.getValue(AppArguments.ARG_SCRIPT);
    String sqlcmd = cmdLine.getValue(AppArguments.ARG_COMMAND);

    if (checkScriptPresence && StringUtil.isBlank(scripts) && StringUtil.isBlank(sqlcmd)) return null;

    String profilename = cmdLine.getValue(AppArguments.ARG_PROFILE);

    boolean abort = true;
    if (cmdLine.isArgPresent(AppArguments.ARG_ABORT))
    {
      abort = cmdLine.getBoolean(AppArguments.ARG_ABORT, true);
    }
    else
    {
      abort = !cmdLine.getBoolean(CommonArgs.ARG_CONTINUE, false);
    }
    boolean showResult = cmdLine.getBoolean(AppArguments.ARG_DISPLAY_RESULT);
    boolean showProgress = cmdLine.getBoolean(AppArguments.ARG_SHOWPROGRESS, false);
    boolean consolidateLog = cmdLine.getBoolean(AppArguments.ARG_CONSOLIDATE_LOG, false);

    String encoding = cmdLine.getValue(AppArguments.ARG_SCRIPT_ENCODING);

    ConnectionProfile profile = null;
    if (profilename == null)
    {
      try
      {
        profile = createCmdLineProfile(cmdLine);
      }
      catch (InvalidConnectionDescriptor ex)
      {
        LogMgr.logError(new CallerInfo(){}, "Invalid connection descriptor specified", ex);
      }
    }
    else
    {
      String group = cmdLine.getValue(AppArguments.ARG_PROFILE_GROUP);
      ProfileKey def = new ProfileKey(StringUtil.trimQuotes(profilename), StringUtil.trimQuotes(group));

      profile = ConnectionMgr.getInstance().getProfile(def);
      if (profile == null)
      {
        String msg = ResourceMgr.getFormattedString("ErrProfileNotFound", def);
        LogMgr.logError(new CallerInfo(){}, msg, null);
      }
      else
      {
        boolean readOnly = cmdLine.getBoolean(AppArguments.ARG_READ_ONLY, false);
        if (readOnly)
        {
          profile.setReadOnly(readOnly);
          // Reset the changed flag to make sure the "modified" profile is not saved
          profile.resetChangedFlags();
        }

        if (cmdLine.isArgPresent(AppArguments.ARG_IGNORE_DROP))
        {
          profile.setIgnoreDropErrors(cmdLine.getBoolean(AppArguments.ARG_IGNORE_DROP));
        }
      }
    }

    if (cmdLine.hasUnknownArguments())
    {
      StringBuilder err = new StringBuilder(ResourceMgr.getString("ErrUnknownParameter"));
      if (!WbManager.getInstance().isGUIMode())
      {
        err.append(' ');
        err.append(cmdLine.getUnknownArguments());
        System.err.println(err.toString());
      }
      LogMgr.logWarning(new CallerInfo(){}, err.toString());
    }

    String success = cmdLine.getValue(AppArguments.ARG_SUCCESS_SCRIPT);
    String error = cmdLine.getValue(AppArguments.ARG_ERROR_SCRIPT);
    boolean feedback = cmdLine.getBoolean(AppArguments.ARG_FEEDBACK, true);
    boolean interactive = cmdLine.getBoolean(AppArguments.ARG_INTERACTIVE, false);

    BatchRunner runner = null;
    if (StringUtil.isNotBlank(scripts))
    {
      runner = new BatchRunner(scripts);
    }
    else
    {
      runner = new BatchRunner();
      runner.setScriptToRun(sqlcmd);
    }

    runner.showResultSets(showResult);
    try
    {
      runner.setEncoding(encoding);
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Invalid encoding '" + encoding + "' specified. Using platform default'", null);
    }

    runner.setAbortOnError(abort);
    runner.setErrorScript(error);
    runner.setSuccessScript(success);
    runner.setProfile(profile);
    runner.setVerboseLogging(feedback);
    if (!feedback)
    {
      runner.setShowStatementSummary(false);
      runner.setShowStatementWithResult(false);
    }
    runner.setConsolidateLog(consolidateLog);
    runner.setShowProgress(showProgress);

    if (interactive)
    {
      ConsolePrompter prompter = new ConsolePrompter();
      runner.setExecutionController(prompter);
      runner.setParameterPrompter(prompter);
    }

    // if no showTiming argument was provided but feedback was disabled
    // disable the display of the timing information as well.
    if (cmdLine.isArgNotPresent(AppArguments.ARG_SHOW_TIMING) && cmdLine.isArgPresent(AppArguments.ARG_FEEDBACK) && !feedback)
    {
      runner.showTiming = false;
    }
    else
    {
      runner.showTiming = cmdLine.getBoolean(AppArguments.ARG_SHOW_TIMING, true);
    }

    DelimiterDefinition delim = DelimiterDefinition.parseCmdLineArgument(cmdLine.getValue(AppArguments.ARG_DELIMITER));

    if (delim != null)
    {
      runner.setDelimiter(delim);
    }

    return runner;
  }
}
