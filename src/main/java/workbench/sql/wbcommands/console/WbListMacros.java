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
package workbench.sql.wbcommands.console;

import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

import workbench.WbManager;
import workbench.resource.ResourceMgr;

import workbench.storage.DataStore;
import workbench.storage.SortDefinition;

import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;
import workbench.sql.macros.MacroDefinition;
import workbench.sql.macros.MacroGroup;
import workbench.sql.macros.MacroManager;
import workbench.sql.macros.MacroStorage;

/**
 * List all defined profiles
 *
 * @author Thomas Kellerer
 */
public class WbListMacros
  extends SqlCommand
{
  public static final String VERB = "WbListMacros";

  public WbListMacros()
  {
    super();
  }

  @Override
  public String getVerb()
  {
    return VERB;
  }

  @Override
  protected boolean isConnectionRequired()
  {
    return false;
  }

  @Override
  public StatementRunnerResult execute(String sql)
    throws SQLException, Exception
  {
    StatementRunnerResult result = new StatementRunnerResult();

    MacroStorage storage = MacroManager.getInstance().getMacros(getMacroClientId());
    List<MacroGroup> groups = storage.getGroups();

    String lblName = ResourceMgr.getString("LblMacroName");
    String lblGroup = ResourceMgr.getString("LblMacroGrpName");
    String lblText = ResourceMgr.getString("LblMacroDef");

    DataStore ds;
    boolean showGroup;
    if (WbManager.getInstance().isGUIMode())
    {
      showGroup = true;
      ds = new DataStore(new String[] {lblGroup, lblName, lblText}, new int[] {Types.VARCHAR, Types.VARCHAR, Types.CLOB});
    }
    else
    {
      showGroup = false;
      ds = new DataStore(new String[] {lblName, lblText}, new int[] {Types.VARCHAR, Types.CLOB});
    }

    for (MacroGroup group : groups)
    {
      List<MacroDefinition> macros = group.getMacros();
      for (MacroDefinition def : macros)
      {
        String name = def.getName();
        if (def.getExpandWhileTyping())
        {
          name += "(+)";
        }
        int row = ds.addRow();
        int col = 0;
        if (showGroup)
        {
          ds.setValue(row, col++, group.getName());
        }
        ds.setValue(row, col++, name);
        ds.setValue(row, col++, def.getText());
      }
    }

    if (!showGroup)
    {
      // sort macros by name in console mode
      SortDefinition sort = new SortDefinition(0, true);
      sort.setIgnoreCase(true);
      ds.sort(sort);
    }

    ds.resetStatus();
    ds.setGeneratingSql("WbListMacros");
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
