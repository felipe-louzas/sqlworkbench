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
import java.sql.Types;
import java.util.List;

import workbench.console.ConsoleSettings;
import workbench.console.RowDisplay;

import workbench.db.DBID;
import workbench.db.oracle.OracleUtils;
import workbench.db.postgres.PostgresUtil;

import workbench.storage.DataStore;

import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;
import workbench.sql.annotations.ResultNameAnnotation;

import workbench.util.ArgumentParser;
import workbench.util.ArgumentType;
import workbench.util.StringUtil;

/**
 *
 * @author  Thomas Kellerer
 */
public class WbListCatalogs
  extends SqlCommand
{

  public static final String VERB = "WbListDB";
  public static final String VERB_ALTERNATE = "WbListCat";

  public WbListCatalogs()
  {
    super();
    this.isUpdatingCommand = false;
    cmdLine = new ArgumentParser();
    cmdLine.addArgument(CommonArgs.ARG_VERBOSE, ArgumentType.BoolSwitch);
  }

  @Override
  public String getVerb()
  {
    return VERB;
  }

  @Override
  public String getAlternateVerb()
  {
    return VERB_ALTERNATE;
  }

  @Override
  public StatementRunnerResult execute(String sql)
    throws SQLException
  {
    StatementRunnerResult result = new StatementRunnerResult();

    DataStore ds = null;
    String catName = StringUtil.capitalize(currentConnection.getMetadata().getCatalogTerm());

    DBID dbid = DBID.fromConnection(currentConnection);
    if (dbid == DBID.Postgres)
    {
      cmdLine.parse(getCommandLine(sql));
      boolean verbose = cmdLine.getBoolean(CommonArgs.ARG_VERBOSE, false);
      ds = PostgresUtil.listPgDatabases(currentConnection, verbose);
    }
    else if (dbid == DBID.Oracle)
    {
      ds = OracleUtils.getPDBs(currentConnection);
      ds.setResultName("PDBS");
    }
    else
    {
      List<String> cats = currentConnection.getMetadata().getCatalogs();
      String[] cols = {catName};
      int[] types = {Types.VARCHAR};
      int[] sizes = {10};

      ds = new DataStore(cols, types, sizes);
      for (String cat : cats)
      {
        int row = ds.addRow();
        ds.setValue(row, 0, cat);
      }
      ds.setResultName(catName);
      ds.setGeneratingSql(sql);
    }

    ConsoleSettings.getInstance().setNextRowDisplay(RowDisplay.SingleLine);
    ResultNameAnnotation.setResultName(ds, sql);

    ds.resetStatus();
    result.addDataStore(ds);
    result.setSuccess();
    return result;
  }

  @Override
  public boolean isWbCommand()
  {
    return true;
  }
}
