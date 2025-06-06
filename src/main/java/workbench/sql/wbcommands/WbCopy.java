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

import java.sql.SQLException;
import java.util.List;

import workbench.AppArguments;
import workbench.WbManager;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;

import workbench.db.ConnectionMgr;
import workbench.db.ConnectionProfile;
import workbench.db.DbSettings;
import workbench.db.TableIdentifier;
import workbench.db.TableNotFoundException;
import workbench.db.WbConnection;
import workbench.db.datacopy.DataCopier;
import workbench.db.importer.TableStatements;

import workbench.gui.profiles.ProfileKey;

import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;

import workbench.util.ArgumentParser;
import workbench.util.ArgumentType;
import workbench.util.CollectionUtil;
import workbench.util.ExceptionUtil;
import workbench.util.StringUtil;

/**
 * A command to copy data from one DBMS to another. This is the commandline
 * version of the DataPumper.
 * @author  Thomas Kellerer
 */
public class WbCopy
  extends SqlCommand
{
  public static final String VERB = "WbCopy";

  public static final String PARAM_SOURCETABLE = "sourceTable";
  public static final String PARAM_SOURCESCHEMA = "sourceSchema";
  public static final String PARAM_TARGETSCHEMA = "targetSchema";
  public static final String PARAM_SOURCEQUERY = "sourceQuery";
  public static final String PARAM_TARGETTABLE = "targetTable";
  public static final String PARAM_SOURCEPROFILE = "sourceProfile";
  public static final String PARAM_SOURCEPROFILE_GROUP = "sourceGroup";
  public static final String PARAM_TARGETPROFILE = "targetProfile";
  public static final String PARAM_TARGETPROFILE_GROUP = "targetGroup";
  public static final String PARAM_COLUMNS = "columns";
  public static final String PARAM_SOURCEWHERE = "sourceWhere";
  public static final String PARAM_KEYS = "keyColumns";
  public static final String PARAM_DROPTARGET = "dropTarget";
  public static final String PARAM_CREATETARGET = "createTarget";
  public static final String PARAM_REMOVE_DEFAULTS = "removeDefaults";
  public static final String PARAM_SOURCE_CONN = "sourceConnection";
  public static final String PARAM_TARGET_CONN = "targetConnection";
  public static final String PARAM_ADJUST_NAMES = "fixTableNameCase";
  public static final String PARAM_INCLUDE_VIEWS = "includeViews";
  public static final String PARAM_MAX_ROWS = "maxRows";

  /**
   * If PARAM_CREATETARGET is set to true, this parameter defines
   * the table type (template) to be used when creating the table.
   *
   * @see workbench.db.DbSettings#getCreateTableTemplate(java.lang.String)
   */
  public static final String PARAM_TABLE_TYPE = "tableType";
  public static final String PARAM_SKIP_TARGET_CHECK = "skipTargetCheck";

  public static final String PARAM_DELETE_SYNC = "syncDelete";

  private static final String ID_PREFIX = "$Wb-Copy$";

  private static int runCount;
  private int runId;

  private CopyTask copier;

  // for testing purposes
  private long lastCopyCount;

  public WbCopy()
  {
    super();
    this.isUpdatingCommand = true;
    cmdLine = new ArgumentParser();
    CommonArgs.addCommitParameter(cmdLine);
    CommonArgs.addImportModeParameter(cmdLine);
    CommonArgs.addContinueParameter(cmdLine);
    CommonArgs.addProgressParameter(cmdLine);
    CommonArgs.addCommitAndBatchParams(cmdLine);
    CommonArgs.addCheckDepsParameter(cmdLine);
    CommonArgs.addTableStatements(cmdLine);
    CommonArgs.addTransactionControL(cmdLine);
    ConditionCheck.addParameters(cmdLine);

    cmdLine.addArgument(PARAM_SOURCETABLE);
    cmdLine.addArgument(AppArguments.ARG_IGNORE_DROP, ArgumentType.BoolArgument);
    cmdLine.addArgument(CommonArgs.ARG_IGNORE_IDENTITY, ArgumentType.BoolArgument);
    cmdLine.addArgument(PARAM_SOURCEQUERY);
    cmdLine.addArgument(PARAM_SOURCESCHEMA);
    cmdLine.addArgument(PARAM_TARGETTABLE);
    cmdLine.addArgument(PARAM_TARGETSCHEMA);
    cmdLine.addArgument(PARAM_SOURCE_CONN);
    cmdLine.addArgument(PARAM_TARGET_CONN);
    cmdLine.addArgument(PARAM_SOURCEPROFILE, ArgumentType.ProfileArgument);
    cmdLine.addArgument(PARAM_TARGETPROFILE, ArgumentType.ProfileArgument);
    cmdLine.addArgument(PARAM_SOURCEPROFILE_GROUP);
    cmdLine.addArgument(PARAM_TARGETPROFILE_GROUP);
    cmdLine.addArgument(PARAM_COLUMNS);
    cmdLine.addArgument(PARAM_SOURCEWHERE);
    cmdLine.addArgument(PARAM_INCLUDE_VIEWS, ArgumentType.BoolArgument);
    cmdLine.addArgument(PARAM_MAX_ROWS);
    cmdLine.addArgument(PARAM_ADJUST_NAMES, ArgumentType.BoolArgument);
    cmdLine.addArgument(CommonArgs.ARG_DELETE_TARGET, ArgumentType.BoolArgument);
    cmdLine.addArgument(CommonArgs.ARG_TRUNCATE_TABLE, ArgumentType.BoolArgument);
    cmdLine.addArgument(CommonArgs.ARG_EXCLUDE_TABLES, ArgumentType.TableArgument);
    cmdLine.addArgument(PARAM_KEYS);
    cmdLine.addArgument(PARAM_DROPTARGET, CollectionUtil.arrayList("false", "true", "cascade"));
    cmdLine.addArgument(PARAM_SKIP_TARGET_CHECK, ArgumentType.BoolSwitch);

    cmdLine.addArgument(PARAM_CREATETARGET, ArgumentType.BoolArgument);
    cmdLine.addArgument(PARAM_REMOVE_DEFAULTS, ArgumentType.BoolArgument);
    cmdLine.addArgument(PARAM_DELETE_SYNC, ArgumentType.BoolArgument);
    cmdLine.addArgument(WbImport.ARG_USE_SAVEPOINT, ArgumentType.BoolArgument);
    cmdLine.addArgument(WbExport.ARG_TRIM_CHARDATA, ArgumentType.BoolSwitch);
    cmdLine.addArgument(WbImport.ARG_ADJUST_SEQ, ArgumentType.BoolSwitch);
    cmdLine.addArgumentWithValues(PARAM_TABLE_TYPE, DbSettings.getCreateTableTypes());
  }

  long getAffectedRows()
  {
    return lastCopyCount;
  }

  @Override
  public String getVerb()
  {
    return VERB;
  }

  @Override
  protected boolean isConnectionRequired() { return false; }

  private void addWrongParams(StatementRunnerResult result)
  {
    if (!WbManager.getInstance().isBatchMode())
    {
      result.addMessageNewLine();
      result.addMessageByKey("ErrCopyWrongParameters");
    }
  }

  @Override
  public StatementRunnerResult execute(final String sql)
    throws SQLException
  {
    StatementRunnerResult result = new StatementRunnerResult(messageLogger);

    cmdLine.parse(getCommandLine(sql));

    if (displayHelp(result))
    {
      return result;
    }

    if (cmdLine.hasUnknownArguments())
    {
      setUnknownMessage(result, cmdLine, ResourceMgr.getString("ErrCopyWrongParameters"));
      return result;
    }

    if (!checkConditions(result))
    {
      return result;
    }

    synchronized (VERB)
    {
      runId = ++runCount;
    }

    String sourcetable = cmdLine.getValue(PARAM_SOURCETABLE);
    String sourceSchema = cmdLine.getValue(PARAM_SOURCESCHEMA);
    String sourcequery = cmdLine.getValue(PARAM_SOURCEQUERY);

    boolean doSyncDelete = cmdLine.getBoolean(PARAM_DELETE_SYNC, false);
    if (doSyncDelete && StringUtil.isNotBlank(sourcequery))
    {
      result.addErrorMessageByKey("ErrCopySyncNoQuery");
      return result;
    }

    if (StringUtil.isBlank(sourcetable) && StringUtil.isBlank(sourcequery) && StringUtil.isBlank(sourceSchema))
    {
      result.addErrorMessageByKey("ErrCopyNoSourceSpecified");
      addWrongParams(result);
      return result;
    }

    if (StringUtil.isNotBlank(sourcetable) && StringUtil.isNotBlank(sourcequery))
    {
      result.addErrorMessageByKey("ErrCopyTargetAndQuery");
      return result;
    }

    CommandLineConnectionHandler targetHandler = new CommandLineConnectionHandler(cmdLine, PARAM_TARGETPROFILE, PARAM_TARGETPROFILE_GROUP, PARAM_TARGET_CONN);
    WbConnection targetCon = targetHandler.getConnection(result, currentConnection, getBaseDir(), ID_PREFIX + "-Target-"+ runId + "$");
    if (targetCon == null || !result.isSuccess())
    {
      LogMgr.logError(new CallerInfo(){}, "Could not create target connection!", null);
      return result;
    }

    CommandLineConnectionHandler sourceHandler = new CommandLineConnectionHandler(cmdLine, PARAM_SOURCEPROFILE, PARAM_SOURCEPROFILE_GROUP, PARAM_SOURCE_CONN);
    WbConnection sourceCon = sourceHandler.getConnection(result, currentConnection, getBaseDir(), ID_PREFIX + "-Source-" + runId + "$");
    if (sourceCon == null || !result.isSuccess())
    {
      LogMgr.logError(new CallerInfo(){}, "Could not create source connection!", null);
      return result;
    }

    boolean includeSourceViews = cmdLine.getBoolean(PARAM_INCLUDE_VIEWS, false);
    List<TableIdentifier> tablesToExport = null;
    SourceTableArgument sourceTables = null;
    try
    {
      String excluded = cmdLine.getValue(CommonArgs.ARG_EXCLUDE_TABLES);
      String[] types;
      if (includeSourceViews)
      {
        types = sourceCon.getMetadata().getTablesAndViewTypes();
      }
      else
      {
        types = sourceCon.getMetadata().getTableTypesArray();
      }
      sourceTables = new SourceTableArgument(sourcetable, excluded, sourceSchema, types, sourceCon);
      tablesToExport = sourceTables.getTables();
      if (tablesToExport.isEmpty() && sourceTables.wasWildcardArgument())
      {
        result.addErrorMessageByKey("ErrExportNoTablesFound", sourcetable);
        return result;
      }
    }
    catch (SQLException e)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not retrieve table list", e);
      result.addErrorMessage(ExceptionUtil.getDisplay(e));
      return result;
    }

    if (tablesToExport.size() > 1 || sourceTables.wasWildcardArgument() || cmdLine.isArgPresent(PARAM_SOURCESCHEMA))
    {
      String targetTable = cmdLine.getValue(PARAM_TARGETTABLE);
      if (tablesToExport.size() > 1 && StringUtil.isNotBlank(targetTable))
      {
        result.addErrorMessageByKey("ErrCopyMultiSrcNoTarget");
        return result;
      }

      SchemaCopy schemaCopy = new SchemaCopy(tablesToExport);
      if (cmdLine.isArgPresent(PARAM_ADJUST_NAMES))
      {
        schemaCopy.setAdjustNameCase(cmdLine.getBoolean(PARAM_ADJUST_NAMES, true));
      }
      copier = schemaCopy;
      // TODO: add support for catalogs
      copier.setTargetSchemaAndCatalog(cmdLine.getValue(PARAM_TARGETSCHEMA), null);
    }
    else
    {
      copier = new TableCopy();
    }

    try
    {
      if (!copier.init(sourceCon, targetCon, result, cmdLine, rowMonitor))
      {
        result.addMessage(copier.getMessages());
        return result;
      }

      copier.setAdjustSequences(cmdLine.getBoolean(WbImport.ARG_ADJUST_SEQ, false));

      this.lastCopyCount = copier.copyData();
      if (copier.isSuccess())
      {
        result.setSuccess();
      }
      else
      {
        result.setFailure();
      }
      if (copier.hasWarnings())
      {
        result.setWarning();
      }
      result.addMessage(copier.getMessages());
    }
    catch (TableNotFoundException tnf)
    {
      String err = ResourceMgr.getFormattedString("ErrTargetTableNotFound", tnf.getTableName());
      result.addErrorMessage(err);
    }
    catch (SQLException e)
    {
      LogMgr.logError(new CallerInfo(){}, "SQL Error when copying data", e);
      CharSequence msg = copier.getMessages();
      if (msg.length() == 0)
      {
        String err = ResourceMgr.getFormattedString("ErrCopy", ExceptionUtil.getDisplay(e, false));
        result.addErrorMessage(err);
      }
      else
      {
        result.addErrorMessage(msg.toString());
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error when copying data", e);
      result.setFailure();
      addErrorInfo(result, sql, e);
      result.addMessage(copier.getMessages());
    }
    finally
    {
      closeConnections(sourceCon, targetCon);
    }

    return result;
  }

  @Override
  public void done()
  {
    super.done();
    this.copier = null;
  }

  @Override
  public void cancel()
    throws SQLException
  {
    super.cancel();
    if (this.copier != null)
    {
      this.copier.cancel();
    }
  }

  private void closeConnections(WbConnection sourceCon, WbConnection targetCon)
  {
    try
    {
      if (sourceCon != null && sourceCon.getId().startsWith(ID_PREFIX))
      {
        sourceCon.disconnect();
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error when disconnecting source connection", e);
    }

    try
    {
      if (targetCon != null && targetCon.getId().startsWith(ID_PREFIX))
      {
        targetCon.disconnect();
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error when disconnecting target connection", e);
    }
  }

  /**
   * Extracts the target profile from the passed SQL statement.
   */
  @Override
  public ConnectionProfile getModificationTarget(WbConnection con, String sql)
  {
    cmdLine.parse(getCommandLine(sql));
    CommandLineConnectionHandler handler = new CommandLineConnectionHandler(cmdLine, PARAM_TARGETPROFILE, PARAM_TARGETPROFILE_GROUP, PARAM_TARGET_CONN);
    ProfileKey key = handler.getProfileKey();
    if (key == null) return null;
    ConnectionProfile prof = ConnectionMgr.getInstance().getProfile(key);
    return prof;
  }

  /**
   * Factory method to create a DataCopier instance initialized from the passed commandline arguments.
   *
   * @param cmdLine  the commandline arguments
   * @param db       the DbSettings for thet target connection
   * @return an initialized DataCopier
   */
  static DataCopier createDataCopier(ArgumentParser cmdLine, DbSettings db)
  {
    DataCopier copier = new DataCopier();
    copier.setIgnoreColumnDefaults(cmdLine.getBoolean(WbCopy.PARAM_REMOVE_DEFAULTS, false));
    copier.setMaxRows(cmdLine.getIntValue(WbCopy.PARAM_MAX_ROWS, -1));
    if (cmdLine.isArgPresent(WbExport.ARG_TRIM_CHARDATA))
    {
      copier.setTrimCharData(cmdLine.getBoolean(WbExport.ARG_TRIM_CHARDATA, false));
    }
    copier.setPerTableStatements(new TableStatements(cmdLine));
    copier.setTransactionControl(cmdLine.getBoolean(CommonArgs.ARG_TRANS_CONTROL, true));
    copier.setIgnoreIdentityColumns(cmdLine.getBoolean(CommonArgs.ARG_IGNORE_IDENTITY, false));
    copier.setContinueOnError(cmdLine.getBoolean(CommonArgs.ARG_CONTINUE));
    copier.setDeleteTarget(CommonArgs.getDeleteType(cmdLine));
    copier.setUseSavepoint(cmdLine.getBoolean(WbImport.ARG_USE_SAVEPOINT, db.useSavepointForImport()));

    CommonArgs.setProgressInterval(copier, cmdLine);
    CommonArgs.setCommitAndBatchParams(copier, cmdLine);

    return copier;
  }

  @Override
  public boolean isWbCommand()
  {
    return true;
  }

}
