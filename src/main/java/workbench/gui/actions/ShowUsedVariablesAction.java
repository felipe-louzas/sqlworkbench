/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer.
 *
 * Licensed under a modified Apache License, Version 2.0 (the "License")
 * that restricts the use for certain governments.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.sql-workbench.eu/manual/license.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.eu
 */
package workbench.gui.actions;

import java.awt.event.ActionEvent;
import java.sql.SQLException;
import java.util.Set;

import workbench.resource.ResourceMgr;

import workbench.gui.sql.SqlPanel;

import workbench.storage.DataStore;

import workbench.sql.StatementRunnerResult;
import workbench.sql.VariablePool;
import workbench.sql.VariablesDataStore;
import workbench.sql.wbcommands.WbListVars;

/**
 *
 * @author Thomas Kellerer
 */
public class ShowUsedVariablesAction
  extends WbAction
{
  private final SqlPanel display;

  public ShowUsedVariablesAction(SqlPanel display)
  {
    this.display = display;
    setMenuItemName(ResourceMgr.MNU_TXT_SQL);
    initMenuDefinition("MnuTxtShowUsedVars");
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    String sql = display.getEditor().getText();
    VariablePool pool = VariablePool.getInstance(display.getVariablePoolID());
    Set<String> names = pool.getAllUsedVariables(sql);
    DataStore values = new VariablesDataStore(display.getVariablePoolID());
    for (String var : names)
    {
      int row = values.addRow();
      values.setValue(row, 0, var);
      values.setValue(row, 1, pool.getParameterValue(var));
    }
    values.sortByColumn(0, true);
    values.resetStatus();

    StatementRunnerResult result = new StatementRunnerResult(WbListVars.VERB);
    result.addDataStore(values);
    result.setSuccess();

    try
    {
      display.addResult(result);
    }
    catch (SQLException ex)
    {
      // can't happen
    }
  }

}
