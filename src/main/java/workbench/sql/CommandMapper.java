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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.DBID;
import workbench.db.DbMetadata;
import workbench.db.WbConnection;

import workbench.sql.commands.AlterSessionCommand;
import workbench.sql.commands.DdlCommand;
import workbench.sql.commands.IgnoredCommand;
import workbench.sql.commands.SelectCommand;
import workbench.sql.commands.SetCommand;
import workbench.sql.commands.TransactionEndCommand;
import workbench.sql.commands.TransactionStartCommand;
import workbench.sql.commands.UpdatingCommand;
import workbench.sql.commands.UseCommand;
import workbench.sql.wbcommands.MySQLShow;
import workbench.sql.wbcommands.PgCopyCommand;
import workbench.sql.wbcommands.WbCall;
import workbench.sql.wbcommands.WbConfirm;
import workbench.sql.wbcommands.WbConnInfo;
import workbench.sql.wbcommands.WbConnect;
import workbench.sql.wbcommands.WbCopy;
import workbench.sql.wbcommands.WbDataDiff;
import workbench.sql.wbcommands.WbDefinePk;
import workbench.sql.wbcommands.WbDefineVar;
import workbench.sql.wbcommands.WbDelimiter;
import workbench.sql.wbcommands.WbDescribeObject;
import workbench.sql.wbcommands.WbDisableOraOutput;
import workbench.sql.wbcommands.WbEcho;
import workbench.sql.wbcommands.WbEnableOraOutput;
import workbench.sql.wbcommands.WbEndBatch;
import workbench.sql.wbcommands.WbExport;
import workbench.sql.wbcommands.WbFeedback;
import workbench.sql.wbcommands.WbFetchSize;
import workbench.sql.wbcommands.WbGenDelete;
import workbench.sql.wbcommands.WbGenDrop;
import workbench.sql.wbcommands.WbGenImpTable;
import workbench.sql.wbcommands.WbGenInsert;
import workbench.sql.wbcommands.WbGenerateFKScript;
import workbench.sql.wbcommands.WbGenerateScript;
import workbench.sql.wbcommands.WbGrepData;
import workbench.sql.wbcommands.WbGrepSource;
import workbench.sql.wbcommands.WbHelp;
import workbench.sql.wbcommands.WbHideWarnings;
import workbench.sql.wbcommands.WbHistory;
import workbench.sql.wbcommands.WbImport;
import workbench.sql.wbcommands.WbInclude;
import workbench.sql.wbcommands.WbIsolationLevel;
import workbench.sql.wbcommands.WbList;
import workbench.sql.wbcommands.WbListCatalogs;
import workbench.sql.wbcommands.WbListDependencies;
import workbench.sql.wbcommands.WbListIndexes;
import workbench.sql.wbcommands.WbListPkDef;
import workbench.sql.wbcommands.WbListProcedures;
import workbench.sql.wbcommands.WbListSchemas;
import workbench.sql.wbcommands.WbListTriggers;
import workbench.sql.wbcommands.WbListVars;
import workbench.sql.wbcommands.WbLoadPkMapping;
import workbench.sql.wbcommands.WbMessage;
import workbench.sql.wbcommands.WbMode;
import workbench.sql.wbcommands.WbObjectGrants;
import workbench.sql.wbcommands.WbOraShow;
import workbench.sql.wbcommands.WbProcSource;
import workbench.sql.wbcommands.WbRemoveVar;
import workbench.sql.wbcommands.WbRestoreConnection;
import workbench.sql.wbcommands.WbRowCount;
import workbench.sql.wbcommands.WbRunLB;
import workbench.sql.wbcommands.WbRunResult;
import workbench.sql.wbcommands.WbSavePkMapping;
import workbench.sql.wbcommands.WbSchemaDiff;
import workbench.sql.wbcommands.WbSchemaReport;
import workbench.sql.wbcommands.WbSelectBlob;
import workbench.sql.wbcommands.WbSetProp;
import workbench.sql.wbcommands.WbSetSchema;
import workbench.sql.wbcommands.WbShowEncoding;
import workbench.sql.wbcommands.WbShowProps;
import workbench.sql.wbcommands.WbStartBatch;
import workbench.sql.wbcommands.WbSwitchDB;
import workbench.sql.wbcommands.WbSysExec;
import workbench.sql.wbcommands.WbSysOpen;
import workbench.sql.wbcommands.WbTableSource;
import workbench.sql.wbcommands.WbTriggerSource;
import workbench.sql.wbcommands.WbViewSource;
import workbench.sql.wbcommands.WbXslt;
import workbench.sql.wbcommands.console.WbAbout;
import workbench.sql.wbcommands.console.WbCreateProfile;
import workbench.sql.wbcommands.console.WbDefineDriver;
import workbench.sql.wbcommands.console.WbDefineMacro;
import workbench.sql.wbcommands.console.WbDeleteMacro;
import workbench.sql.wbcommands.console.WbDeleteProfile;
import workbench.sql.wbcommands.console.WbDisconnect;
import workbench.sql.wbcommands.console.WbDisplay;
import workbench.sql.wbcommands.console.WbListDrivers;
import workbench.sql.wbcommands.console.WbListMacros;
import workbench.sql.wbcommands.console.WbListProfiles;
import workbench.sql.wbcommands.console.WbRemoveMasterPwd;
import workbench.sql.wbcommands.console.WbRun;
import workbench.sql.wbcommands.console.WbSaveProfiles;
import workbench.sql.wbcommands.console.WbSetDisplaySize;
import workbench.sql.wbcommands.console.WbSetMasterPwd;
import workbench.sql.wbcommands.console.WbStoreProfile;
import workbench.sql.wbcommands.console.WbToggleDisplay;

import workbench.util.CaseInsensitiveComparator;
import workbench.util.CollectionUtil;
import workbench.util.SqlParsingUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * @author Thomas Kellerer
 */
public class CommandMapper
{
  private final Map<String, SqlCommand> cmdDispatch;
  private final List<String> dbSpecificCommands;
  private final Set<String> passThrough = CollectionUtil.caseInsensitiveSet();
  private boolean supportsSelectInto;
  private DbMetadata metaData;
  private final boolean allowAbbreviated;

  public CommandMapper()
  {
    this(Settings.getInstance().getBoolProperty("workbench.sql.allow.abbreviation", false));
  }

  public CommandMapper(boolean allowAbbreviations)
  {
    cmdDispatch = new TreeMap<>(CaseInsensitiveComparator.INSTANCE);
    cmdDispatch.put("*", new SqlCommand());

    // Workbench specific commands
    addCommand(new WbList());
    addCommand(new WbListProcedures());
    addCommand(new WbDefineVar());
    addCommand(new WbEnableOraOutput());
    addCommand(new WbDisableOraOutput());
    addCommand(new WbStartBatch());
    addCommand(new WbEndBatch());
    addCommand(new WbXslt());
    addCommand(new WbRemoveVar());
    addCommand(new WbListVars());
    addCommand(new WbExport());
    addCommand(new WbImport());
    addCommand(new WbCopy());
    addCommand(new WbSchemaReport());
    addCommand(new WbSchemaDiff());
    addCommand(new WbDataDiff());
    addCommand(new WbFeedback());
    addCommand(new WbDefinePk());
    addCommand(new WbListPkDef());
    addCommand(new WbLoadPkMapping());
    addCommand(new WbSavePkMapping());
    addCommand(new WbConfirm());
    addCommand(new WbMessage());
    addCommand(new WbCall());
    addCommand(new WbConnect());
    addCommand(new WbRestoreConnection());
    addCommand(new WbInclude());
    addCommand(new WbListCatalogs());
    addCommand(new WbListSchemas());
    addCommand(new WbHelp());
    addCommand(new WbSelectBlob());
    addCommand(new WbHideWarnings());
    addCommand(new WbProcSource());
    addCommand(new WbListTriggers());
    addCommand(new WbListIndexes());
    addCommand(new WbTriggerSource());
    addCommand(new WbViewSource());
    addCommand(new WbTableSource());
    addCommand(new WbDescribeObject());
    addCommand(new WbGrepSource());
    addCommand(new WbGrepData());
    addCommand(new WbMode());
    addCommand(new WbFetchSize());
    addCommand(new WbAbout());
    addCommand(new WbRunLB());
    addCommand(new WbIsolationLevel());
    addCommand(new WbConnInfo());
    addCommand(new WbSysExec());
    addCommand(new WbSysOpen());
    addCommand(new WbShowProps());
    WbSetProp set = new WbSetProp();
    addCommand(set);
    cmdDispatch.put(WbSetProp.SET_DB_CONFIG_VERB, set);
    addCommand(new WbGenDrop());
    addCommand(new WbGenerateScript());
    addCommand(new WbGenerateFKScript());
    addCommand(new WbGenDelete());
    addCommand(new WbGenInsert());
    addCommand(new WbRunResult());
    addCommand(new WbGenImpTable());
    addCommand(new WbObjectGrants());
    addCommand(new WbEcho());
    addCommand(new WbShowEncoding());
    addCommand(new WbRowCount());

    addCommand(new WbDisconnect());
    addCommand(new WbDisplay());
    addCommand(new WbToggleDisplay());
    addCommand(new WbSetDisplaySize());
    addCommand(new WbRun());
    addCommand(new WbHistory());
    addCommand(new WbListMacros());
    addCommand(new WbDefineMacro());
    addCommand(new WbDeleteMacro());

    addCommand(new WbStoreProfile());
    addCommand(new WbSaveProfiles());
    addCommand(new WbDeleteProfile());
    addCommand(new WbCreateProfile());
    addCommand(new WbDefineDriver());
    addCommand(new WbListProfiles());
    addCommand(new WbListDrivers());
    addCommand(new WbListDependencies());

    // Wrappers for standard SQL statements
    addCommand(TransactionEndCommand.getCommit());
    addCommand(TransactionEndCommand.getRollback());

    addCommand(UpdatingCommand.getDeleteCommand());
    addCommand(UpdatingCommand.getInsertCommand());
    addCommand(UpdatingCommand.getUpdateCommand());
    addCommand(UpdatingCommand.getTruncateCommand());
    addCommand(UpdatingCommand.getMergeCommand());

    addCommand(new WbSwitchDB());
    addCommand(new SetCommand());
    addCommand(new SelectCommand());
    addCommand(new WbSetSchema());

    addCommand(new WbSetMasterPwd());
    addCommand(new WbRemoveMasterPwd());
    addCommand(new WbDelimiter());

    for (DdlCommand cmd : DdlCommand.getDdlCommands())
    {
      addCommand(cmd);
    }
    this.cmdDispatch.put("CREATE OR REPLACE", DdlCommand.getCreateCommand());

    this.dbSpecificCommands = new ArrayList<>();
    this.allowAbbreviated = allowAbbreviations;
    registerExtensions();
  }

  private void registerExtensions()
  {
    List<SqlCommand> commands = CommandRegistry.getInstance().getCommands();
    for (SqlCommand cmd : commands)
    {
      addCommand(cmd);
    }
  }

  public Collection<String> getAllWbCommands()
  {
    Collection<SqlCommand> commands = cmdDispatch.values();
    TreeSet<String> result = new TreeSet<>();
    for (SqlCommand cmd : commands)
    {
      if (cmd.isWbCommand())
      {
        result.addAll(cmd.getAllVerbs());
      }
    }
    return result;
  }

  /**
   * Add a new command definition during runtime.
   */
  public final void addCommand(SqlCommand command)
  {
    for (String verb : command.getAllVerbs())
    {
      cmdDispatch.put(verb, command);
    }
  }

  private void addDBMSCommand(String verb, SqlCommand command)
  {
    this.cmdDispatch.put(verb, command);
    this.dbSpecificCommands.add(verb);
  }
  /**
   * Initialize the CommandMapper with a database connection.
   * This will add DBMS specific commands to the internal dispatch.
   *
   * This method can be called multiple times.
   */
  public void setConnection(WbConnection aConn)
  {
    this.cmdDispatch.keySet().removeAll(dbSpecificCommands);
    this.dbSpecificCommands.clear();
    this.supportsSelectInto = false;

    if (aConn == null) return;

    this.metaData = aConn.getMetadata();

    if (metaData == null)
    {
      LogMgr.logError(new CallerInfo(){}, "Received connection without metaData!", null);
      return;
    }

    DBID id = DBID.fromConnection(aConn);
    if (id == DBID.Oracle)
    {
      SqlCommand wbcall = this.cmdDispatch.get(WbCall.VERB);

      addDBMSCommand(WbCall.EXEC_VERB_LONG, wbcall);
      addDBMSCommand(WbCall.EXEC_VERB_SHORT, wbcall);

      AlterSessionCommand alter = new AlterSessionCommand();
      addDBMSCommand(alter.getVerb(), alter);
      addDBMSCommand(WbOraShow.VERB, new WbOraShow());

      WbFeedback echo = new WbFeedback("ECHO");
      addDBMSCommand(echo.getVerb(), echo);

      SqlCommand wbEcho = this.cmdDispatch.get(WbEcho.VERB);
      addDBMSCommand("prompt", wbEcho);

      SqlCommand confirm = this.cmdDispatch.get(WbConfirm.VERB);
      addDBMSCommand("pause", confirm);
    }

    if (id.isAny(DBID.SQL_Server, DBID.MySQL, DBID.MariaDB))
    {
      UseCommand cmd = new UseCommand();
      addDBMSCommand(cmd.getVerb(), cmd);
    }

    if (id == DBID.Firebird)
    {
      DdlCommand recreate = DdlCommand.getRecreateCommand();
      addDBMSCommand(recreate.getVerb(), recreate);
      WbDelimiter delim = new WbDelimiter("SET TERM");
      addDBMSCommand(delim.getVerb(), delim);
    }

    if (id == DBID.Postgres)
    {
      PgCopyCommand copy = new PgCopyCommand();

      this.cmdDispatch.put(copy.getVerb(), copy);
      this.dbSpecificCommands.add(copy.getVerb());
    }

    if (id.isAny(DBID.Postgres, DBID.Greenplum, DBID.Redshift))
    {
      // support manual transactions in auto commit mode
      addDBMSCommand(TransactionStartCommand.BEGIN.getVerb(), TransactionStartCommand.BEGIN);
      addDBMSCommand(TransactionStartCommand.START_TRANSACTION.getVerb(), TransactionStartCommand.START_TRANSACTION);
      addDBMSCommand(TransactionStartCommand.BEGIN_TRANSACTION.getVerb(), TransactionStartCommand.BEGIN_TRANSACTION);
      addDBMSCommand(TransactionStartCommand.BEGIN_WORK.getVerb(), TransactionStartCommand.BEGIN_WORK);
    }

    if (id == DBID.SQL_Server)
    {
      addDBMSCommand(TransactionStartCommand.BEGIN_TRANSACTION.getVerb(), TransactionStartCommand.BEGIN_TRANSACTION);
      addDBMSCommand(TransactionStartCommand.BEGIN_TRAN.getVerb(), TransactionStartCommand.BEGIN_TRAN);
    }

    if (id == DBID.Vertica)
    {
      addDBMSCommand(TransactionStartCommand.BEGIN_TRANSACTION.getVerb(), TransactionStartCommand.BEGIN_TRANSACTION);
      addDBMSCommand(TransactionStartCommand.BEGIN_WORK.getVerb(), TransactionStartCommand.BEGIN_WORK);
      addDBMSCommand(TransactionStartCommand.START_TRANSACTION.getVerb(), TransactionStartCommand.START_TRANSACTION);
    }

    if (id.isAny(DBID.MariaDB, DBID.MySQL))
    {
      MySQLShow show = new MySQLShow();
      addDBMSCommand(show.getVerb(), show);
      WbDelimiter delim = new WbDelimiter(WbDelimiter.ALTERNATE_VERB);
      addDBMSCommand(delim.getVerb(), delim);
    }

    List<String> startTrans = aConn.getDbSettings().getListProperty("start_transaction");
    for (String sql : startTrans)
    {
      sql = SqlUtil.makeCleanSql(sql, false, false);

      if (this.cmdDispatch.containsKey(sql))
      {
        LogMgr.logInfo(new CallerInfo(){}, "Configured command " + sql.toUpperCase() + " is already registered as a transaction start command");
      }
      else
      {
        LogMgr.logInfo(new CallerInfo(){}, "Adding " + sql.toUpperCase() + " as a transaction start command");
        TransactionStartCommand startCmd = TransactionStartCommand.fromVerb(sql);
        addDBMSCommand(startCmd.getVerb(), startCmd);
      }
    }

    if (metaData.getDbSettings().useWbProcedureCall())
    {
      SqlCommand wbcall = this.cmdDispatch.get(WbCall.VERB);
      addDBMSCommand("CALL", wbcall);
    }

    List<String> verbs = aConn.getDbSettings().getListProperty("ignore");
    for (String verb : verbs)
    {
      if (verb == null) continue;
      IgnoredCommand cmd = new IgnoredCommand(verb);
      addDBMSCommand(verb, cmd);
    }

    List<String> silentVerbs = aConn.getDbSettings().getListProperty("ignore.silent");
    for (String verb : silentVerbs)
    {
      if (verb == null) continue;
      IgnoredCommand cmd = new IgnoredCommand(verb);
      cmd.setSilent(true);
      addDBMSCommand(verb, cmd);
    }

    List<String> passVerbs = aConn.getDbSettings().getListProperty("passthrough");
    passThrough.clear();
    if (passVerbs != null)
    {
      for (String v : passVerbs)
      {
        passThrough.add(v);
      }
    }

    // this is stored in an instance variable for performance
    // reasons, so we can skip the call to isSelectIntoNewTable() in
    // getCommandToUse()
    // For a single call this doesn't matter, but when executing
    // huge scripts the repeated call to getCommandToUse should
    // be as quick as possible
    this.supportsSelectInto = metaData.supportsSelectIntoNewTable();
  }

  /**
   *
   * Returns the command context to be used for the given SQL string.
   *
   * This also checks for "SELECT ... INTO ... " style statments that
   * don't actually select something but create a new table.
   * As those aren't "real" queries they need to be run and handled
   * differently - for those statements SelectCommand will not be used.
   *
   * @param sql the statement to be executed
   *
   * @return the CommandCtx with instance of SqlCommand to be used to run the sql. Never null
   */
  public CommandCtx getCommandToUse(String sql)
  {
    WbConnection conn = metaData == null ? null : metaData.getWbConnection();
    String verb = SqlParsingUtil.getInstance(conn).getSqlVerb(sql);
    if (this.supportsSelectInto && "SELECT".equals(verb) && this.metaData != null && this.metaData.isSelectIntoNewTable(sql))
    {
      LogMgr.logDebug(new CallerInfo(){}, "Found 'SELECT ... INTO new_table'");
      // use the generic SqlCommand implementation for this and not the SelectCommand
      return new CommandCtx(this.cmdDispatch.get("*"), verb);
    }
    return new CommandCtx(getCommandFromVerb(verb), verb);
  }

  public SqlCommand getCommandFromVerb(String verb)
  {
    SqlCommand cmd = null;

    if (StringUtil.isEmpty(verb)) return null;

    // checking for the collection size before checking for the presence
    // is a bit faster because of the hashing that is necessary to look up
    // the entry. Again this doesn't matter for a single command, but when
    // running a large script this does make a difference
    if (passThrough.size() > 0 && passThrough.contains(verb))
    {
      cmd = this.cmdDispatch.get("*");
    }
    else
    {
      cmd = this.cmdDispatch.get(verb);
    }

    if (cmd == null && allowAbbreviated)
    {
      Set<String> verbs = cmdDispatch.keySet();
      int found = 0;
      String lastVerb = null;
      String lverb = verb.toLowerCase();
      for (String toTest : verbs)
      {
        if (cmdDispatch.get(toTest).isWbCommand())
        {
          if (toTest.toLowerCase().startsWith(lverb))
          {
            lastVerb = toTest;
            found++;
          }
        }
      }
      if (found == 1)
      {
        LogMgr.logDebug(new CallerInfo(){}, "Found workbench command " + lastVerb + " for abbreviation " + verb);
        cmd = cmdDispatch.get(lastVerb);
      }
    }

    if (cmd == null)
    {
      cmd = this.cmdDispatch.get("*");
    }
    return cmd;
  }

}
