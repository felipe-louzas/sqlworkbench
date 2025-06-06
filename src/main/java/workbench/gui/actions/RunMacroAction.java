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
package workbench.gui.actions;

import java.awt.event.ActionEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.swing.Action;
import javax.swing.KeyStroke;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import workbench.resource.ResourceMgr;
import workbench.resource.StoreableKeyStroke;

import workbench.gui.MainWindow;
import workbench.gui.components.WbTable;
import workbench.gui.editor.MacroExpander;
import workbench.gui.macros.MacroRunner;
import workbench.gui.sql.SqlPanel;

import workbench.storage.DataStore;
import workbench.storage.RowData;

import workbench.sql.macros.MacroDefinition;

import workbench.util.NumberStringCache;
import workbench.util.StringUtil;

/**
 * @author Thomas Kellerer
 */
public class RunMacroAction
  extends WbAction
  implements ListSelectionListener
{
  private final MainWindow client;
  private MacroDefinition macro;
  private WbTable dataTable;
  private Map<String, String> columnMap;
  private static int internalId;
  private boolean refreshCurrentResult;

  public RunMacroAction(MainWindow macroClient, MacroDefinition def, int index)
  {
    super();
    internalId++;

    macro = def;
    client = macroClient;

    putValue(ACTION_COMMAND_KEY, "run-macro-" + Integer.toString(internalId));

    if (def == null)
    {
      String title = ResourceMgr.getPlainString("LblRunMacro");
      setMenuText(title);
      String desc = ResourceMgr.getDescription("MnuTxtRunMacro", true);
      desc = desc.replaceAll("[ ]*(%macro%)[ ]*", " ");
      putValue(Action.SHORT_DESCRIPTION, desc);
    }
    else
    {
      String title = def.getName();
      if (index < 10 && index > 0)
      {
        title = "&" + NumberStringCache.getNumberString(index) + " - " + def.getName();
      }

      StoreableKeyStroke key = macro.getShortcut();
      if (key != null)
      {
        KeyStroke stroke = key.getKeyStroke();
        setAccelerator(stroke);
      }

      setMenuText(title);
      initTooltip();
    }

    setMenuItemName(ResourceMgr.MNU_TXT_MACRO);
    setIcon(null);
    setEnabled(macro != null && client != null);
  }

  public void setRefreshCurrentResult(boolean flag)
  {
    this.refreshCurrentResult = flag;
  }

  public void setDataTable(WbTable table, Map<String, String> colMap)
  {
    this.dataTable = table;
    this.columnMap = new HashMap<>(colMap);
    if (columnMap == null)
    {
      columnMap = Collections.emptyMap();
    }
    if (dataTable != null)
    {
      dataTable.getSelectionModel().addListSelectionListener(this);
      setEnabled(dataTable.getSelectedRowCount() == 1);
    }
  }

  @Override
  public void dispose()
  {
    super.dispose();
    if (dataTable != null)
    {
      dataTable.getSelectionModel().removeListSelectionListener(this);
    }
  }

  private void initTooltip()
  {
    if (macro == null) return;
    String desc = macro.getDisplayTooltip();
    if (desc == null)
    {
      desc = ResourceMgr.getDescription("MnuTxtRunMacro", true);
      desc = StringUtil.replace(desc, "%macro%", "'" + macro.getName() + "'");
    }
    setTooltip(desc);
  }

  public void setMacro(MacroDefinition def)
  {
    this.macro = def;
    initTooltip();
  }

  private void executeStandardMacro(ActionEvent e)
  {
    SqlPanel sql = this.client.getCurrentSqlPanel();
    if (sql == null) return;

    if (macro.getExpandWhileTyping())
    {
      MacroExpander expander = sql.getEditor().getMacroExpander();
      if (expander != null)
      {
        expander.insertMacroText(macro.getText());
        sql.selectEditorLater();
      }
    }
    else
    {
      boolean shiftPressed = isShiftPressed(e) && invokedByMouse(e);
      MacroRunner runner = new MacroRunner();
      runner.runMacro(macro, sql, shiftPressed);
    }
  }

  private void executeDataMacro()
  {
    if (dataTable == null) return;

    SqlPanel sql = this.client.getCurrentSqlPanel();
    if (sql == null) return;

    int row = dataTable.getSelectedRow();
    if (row < 0) return;

    DataStore ds = dataTable.getDataStore();
    if (ds == null) return;

    RowData rowData = ds.getRow(row);
    if (rowData == null) return;

    MacroRunner runner = new MacroRunner();
    runner.runDataMacro(macro, ds.getResultInfo(), rowData, sql, this.client.getVariablePoolID(), columnMap, refreshCurrentResult);
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    if (this.client == null || this.macro == null) return;

    if (this.dataTable != null)
    {
      executeDataMacro();
    }
    else
    {
      executeStandardMacro(e);
    }
  }

  @Override
  public void valueChanged(ListSelectionEvent e)
  {
    if (e.getValueIsAdjusting()) return;

    if (dataTable == null)
    {
      setEnabled(false);
      return;
    }
    setEnabled(dataTable.getSelectedRowCount() == 1);
  }

  @Override
  public boolean useInToolbar()
  {
    return false;
  }
}
