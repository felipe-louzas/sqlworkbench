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

import workbench.resource.ResourceMgr;

import workbench.db.DbObject;
import workbench.db.search.ObjectSourceSearcher;

import workbench.storage.DataStore;

import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;

import workbench.util.ArgumentParser;
import workbench.util.ArgumentType;
import workbench.util.CollectionUtil;

/**
 * A class to search for text in the source code of all database objects
 * <br/>
 * This SQL commands parses the input and then uses {@link workbench.db.search.ObjectSourceSearcher}
 * to perform the search.
 *
 * @author Thomas Kellerer
 */
public class WbGrepSource
  extends SqlCommand
{
  public static final String VERB = "WbGrepSource";

  public static final String PARAM_SEARCH_EXP = "searchValues";
  public static final String PARAM_MATCHALL = "matchAll";
  public static final String PARAM_IGNORE_CASE = "ignoreCase";
  public static final String PARAM_USE_REGEX = "useRegex";

  private ObjectSourceSearcher searcher;

  public WbGrepSource()
  {
    super();
    this.isUpdatingCommand = false;

    cmdLine = new ArgumentParser();
    cmdLine.addArgument(CommonArgs.ARG_TYPES, ArgumentType.ObjectTypeArgument);
    cmdLine.addArgument(CommonArgs.ARG_SCHEMAS, ArgumentType.SchemaArgument);
    cmdLine.addArgument(CommonArgs.ARG_OBJECTS, ArgumentType.TableArgument);
    cmdLine.addArgument(CommonArgs.ARG_CATALOG, ArgumentType.CatalogArgument);
    cmdLine.addArgument(PARAM_SEARCH_EXP);
    cmdLine.addArgument(PARAM_USE_REGEX);
    cmdLine.addArgument(PARAM_MATCHALL, ArgumentType.BoolArgument);
    cmdLine.addArgument(PARAM_IGNORE_CASE, ArgumentType.BoolArgument);
  }

  @Override
  public String getVerb()
  {
    return VERB;
  }

  @Override
  public StatementRunnerResult execute(String sql)
    throws SQLException
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
      setUnknownMessage(result, cmdLine, ResourceMgr.getString("ErrSrcSearchWrongParameters"));
      return result;
    }

    List<String> values = cmdLine.getListValue(PARAM_SEARCH_EXP);
    if (CollectionUtil.isEmpty(values))
    {
      result.addMessageByKey("ErrScrSearchValueReq");
      result.addErrorMessageByKey("ErrSrcSearchWrongParameters");
      return result;
    }

    boolean matchAll = cmdLine.getBoolean(PARAM_MATCHALL, false);
    boolean ignoreCase = cmdLine.getBoolean(PARAM_IGNORE_CASE, true);
    boolean regEx = cmdLine.getBoolean(PARAM_USE_REGEX, false);

    List<String> schemas = cmdLine.getListValue(CommonArgs.ARG_SCHEMAS);
    List<String> types = cmdLine.getListValue(CommonArgs.ARG_TYPES);
    List<String> names = cmdLine.getListValue(CommonArgs.ARG_OBJECTS);

    searcher = new ObjectSourceSearcher(this.currentConnection);
    searcher.setRowMonitor(this.rowMonitor);
    searcher.setSchemasToSearch(schemas);
    searcher.setTypesToSearch(types);
    searcher.setNamesToSearch(names);
    searcher.setCatalog(cmdLine.getValue(CommonArgs.ARG_CATALOG));

    try
    {
      List<DbObject> found = searcher.searchObjects(values, matchAll, ignoreCase, regEx);
      DataStore ds = new ObjectResultListDataStore(currentConnection, found, true);
      ds.setGeneratingSql(sql);
      String msg = ResourceMgr.getFormattedString("MsgGrepSourceFinished", searcher.getNumberOfObjectsSearched(), ds.getRowCount());
      result.addDataStore(ds);
      result.setExecutionDuration(0);
      result.addMessage(msg);
      result.setSuccess();
    }
    finally
    {
      if (rowMonitor != null)
      {
        rowMonitor.jobFinished();
      }
    }
    return result;
  }

  @Override
  public void cancel()
    throws SQLException
  {
    super.cancel();
    if (searcher != null)
    {
      searcher.cancelSearch();
    }
  }

  @Override
  public void done()
  {
    super.done();
    searcher = null;
  }

  @Override
  public boolean isWbCommand()
  {
    return true;
  }

}
