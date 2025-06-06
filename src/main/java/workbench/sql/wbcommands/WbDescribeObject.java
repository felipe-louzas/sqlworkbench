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
import java.util.Collection;

import workbench.WbManager;
import workbench.console.ConsoleSettings;
import workbench.console.RowDisplay;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;

import workbench.util.ArgumentParser;
import workbench.util.ArgumentType;
import workbench.util.CollectionUtil;
import workbench.util.ExceptionUtil;

/**
 * Display the definition of a database object
 * <br>
 * This command will return multiple result sets:
 * <br>
 * For tables, the following DataStores are returned
 * <ol>
 *    <li>The table definition (columns)</li>
 *    <li>A list of indexes defined for the table</li>
 *    <li>A list of triggers defined for the table</li>
 * </ol>
 *
 * For Views, the view definiton and the view source is returned.
 *
 * @author  Thomas Kellerer
 * @see workbench.db.DbMetadata#getTableDefinition(workbench.db.TableIdentifier)
 * @see workbench.db.IndexReader#getTableIndexInformation(workbench.db.TableIdentifier)
 * @see workbench.db.TriggerReader#getTableTriggers(workbench.db.TableIdentifier)
 * @see workbench.db.ViewReader#getExtendedViewSource(workbench.db.TableIdentifier, boolean)
 */
public class WbDescribeObject
  extends SqlCommand
{
  public static final String VERB = "WbDescribe";
  public static final String VERB_SHORT = "DESC";
  public static final String VERB_LONG = "DESCRIBE";
  public static final String ARG_DEPEND = "dependencies";
  public static final String ARG_OBJECT = "object";

  public WbDescribeObject()
  {
    super();
    isUpdatingCommand = false;
    cmdLine = new ArgumentParser();
    cmdLine.addArgument(ARG_DEPEND, ArgumentType.BoolArgument);
    cmdLine.addArgument(ARG_OBJECT, ArgumentType.TableArgument);
  }

  @Override
  public Collection<String> getAllVerbs()
  {
    return CollectionUtil.arrayList(VERB, VERB_SHORT, VERB_LONG);
  }

  @Override
  public String getVerb()
  {
    return VERB;
  }

  @Override
  public String getAlternateVerb()
  {
    return VERB_LONG;
  }

  @Override
  public StatementRunnerResult execute(String sql)
    throws SQLException
  {
    try
    {
      ConsoleSettings.getInstance().setNextRowDisplay(RowDisplay.SingleLine);

      String args = getCommandLine(sql);
      cmdLine.parse(args);

      boolean includeDependencies = true;
      String object = cmdLine.getValue(ARG_OBJECT, cmdLine.getNonArguments());

      if (cmdLine.hasArguments())
      {
        includeDependencies = cmdLine.getBoolean(ARG_DEPEND, true);
      }

      boolean includeSource = true;
      if (WbManager.getInstance().isConsoleMode())
      {
        if (cmdLine.isArgPresent(ARG_DEPEND))
        {
          includeSource = includeDependencies;
        }
        else
        {
          includeSource = false;
        }
      }

      ObjectInfo info = new ObjectInfo();
      StatementRunnerResult result = info.getObjectInfo(currentConnection, object, includeDependencies, includeSource);
      result.setSourceCommand(sql);
      result.setShowRowCount(false);
      return result;
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error getting object details", e);
      StatementRunnerResult result = new StatementRunnerResult(sql);
      result.addErrorMessage(ExceptionUtil.getDisplay(e));
      return result;
    }
  }

  @Override
  public boolean isWbCommand()
  {
    return true;
  }

}
