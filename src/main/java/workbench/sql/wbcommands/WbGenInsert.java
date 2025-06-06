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

import workbench.interfaces.ScriptGenerationMonitor;
import workbench.resource.ResourceMgr;

import workbench.db.DummyInsert;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;
import workbench.db.importer.TableDependencySorter;

import workbench.storage.RowActionMonitor;

import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;

import workbench.util.ArgumentParser;
import workbench.util.ArgumentType;
import workbench.util.CollectionUtil;
import workbench.util.FileUtil;
import workbench.util.StringUtil;
import workbench.util.WbFile;

/**
 * A SqlCommand to create a script of INSERT statements for a list of tables respecting FK constraints.
 *
 * @author Thomas Kellerer
 */
public class WbGenInsert
  extends SqlCommand
  implements ScriptGenerationMonitor
{
  public static final String VERB = "WbGenerateInsert";

  public static final String PARAM_FULL_INSERT = "fullInsert";

  private TableDependencySorter tableSorter;

  public WbGenInsert()
  {
    super();
    this.isUpdatingCommand = true;
    cmdLine = new ArgumentParser();
    cmdLine.addArgument(CommonArgs.ARG_TABLES, ArgumentType.TableArgument);
    cmdLine.addArgument(CommonArgs.ARG_FILE, ArgumentType.Filename);
    cmdLine.addArgument(PARAM_FULL_INSERT, ArgumentType.BoolArgument);
  }

  @Override
  public StatementRunnerResult execute(String sql)
    throws SQLException, Exception
  {
    StatementRunnerResult result = new StatementRunnerResult();
    String args = getCommandLine(sql);
    cmdLine.parse(args);

    if (displayHelp(result))
    {
      return result;
    }

    if (cmdLine.hasUnknownArguments())
    {
      setUnknownMessage(result, cmdLine, ResourceMgr.getString("ErrGenInsertWrongParam"));
      return result;
    }

    if (!cmdLine.hasArguments())
    {
      result.addErrorMessageByKey("ErrGenInsertWrongParam");
      return result;
    }

    String names = cmdLine.getValue(CommonArgs.ARG_TABLES);
    boolean fullInsert = cmdLine.getBoolean(PARAM_FULL_INSERT, false);
    SourceTableArgument tableArgs = new SourceTableArgument(names, currentConnection);

    List<TableIdentifier> tables = tableArgs.getTables();

    if (CollectionUtil.isEmpty(tables))
    {
      result.addErrorMessageByKey("ErrTableNotFound", names);
      return result;
    }

    WbFile output = evaluateFileArgument(cmdLine.getValue(CommonArgs.ARG_FILE));

    List<String> missingTables = tableArgs.getMissingTables();

    tableSorter = new TableDependencySorter(this.currentConnection);

    // tables have been retrieved by SourceTableArgument
    // no need to validate them again
    tableSorter.setValidateTables(false);

    if (this.rowMonitor != null)
    {
      rowMonitor.setMonitorType(RowActionMonitor.MONITOR_PROCESS_TABLE);
      tableSorter.setProgressMonitor(this);
    }

    List<TableIdentifier> sorted = tableSorter.sortForInsert(tables);

    if (this.rowMonitor != null)
    {
      rowMonitor.jobFinished();
    }

    if (this.isCancelled)
    {
      result.addMessageByKey("MsgStatementCancelled");
    }
    else
    {
      if (!fullInsert)
      {
        result.addMessageByKey("MsgInsertSeq");
        result.addMessageNewLine();
      }

      String nl = StringUtil.LINE_TERMINATOR;
      StringBuilder script = new StringBuilder(sorted.size() * 40);
      for (TableIdentifier table : sorted)
      {
        if (fullInsert)
        {
          DummyInsert insert = new DummyInsert(table);
          insert.setLineEnd(nl);
          insert.setDoFormatSql(false);
          String source = insert.getSource(currentConnection).toString();
          script.append(source);
          script.append(nl);
        }
        else
        {
          script.append("    " + table.getTableExpression());
        }
        script.append(nl);
      }

      if (output != null)
      {
        FileUtil.writeString(output, script.toString(), "UTF-8", false);
        result.addMessageByKey("MsgScriptWritten", output.getFullPath());
      }
      else
      {
        result.addMessage(script);
      }
      result.setSuccess();

      if (CollectionUtil.isNonEmpty(missingTables))
      {
        result.addMessageNewLine();
        result.addMessageByKey("MsgTablesNotFound");
        result.addMessageNewLine();
        for (String tbl : missingTables)
        {
          result.addMessage("    " + tbl);
        }
        result.setWarning();
      }

    }
    return result;
  }


  @Override
  public void cancel()
    throws SQLException
  {
    super.cancel();
    if (tableSorter != null)
    {
      tableSorter.cancel();
    }
  }

  @Override
  public void done()
  {
    super.done();
    tableSorter = null;
  }

  @Override
  public boolean isUpdatingCommand(WbConnection con, String sql)
  {
    return false;
  }

  @Override
  public String getVerb()
  {
    return VERB;
  }

  @Override
  public void setCurrentObject(String anObject, int current, int count)
  {
    if (this.rowMonitor != null)
    {
      if (anObject.indexOf(' ') > -1)
      {
        try
        {
          rowMonitor.saveCurrentType("genDel");
          rowMonitor.setMonitorType(RowActionMonitor.MONITOR_PLAIN);
          rowMonitor.setCurrentObject(anObject, current, count);
        }
        finally
        {
          rowMonitor.restoreType("genDel");
        }
      }
      else
      {
        rowMonitor.setCurrentObject(anObject, current, count);
      }
    }
  }

  @Override
  public boolean isWbCommand()
  {
    return true;
  }

}
