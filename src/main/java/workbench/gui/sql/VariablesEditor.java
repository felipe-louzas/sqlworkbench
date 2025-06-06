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
package workbench.gui.sql;

import java.awt.BorderLayout;
import java.awt.EventQueue;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.table.TableCellEditor;

import workbench.WbManager;
import workbench.interfaces.ValidatingComponent;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.ColumnWidthOptimizer;
import workbench.gui.components.DataStoreTableModel;
import workbench.gui.components.ValidatingDialog;
import workbench.gui.components.WbScrollPane;
import workbench.gui.components.WbTable;
import workbench.gui.components.WbTextCellEditor;
import workbench.gui.renderer.RendererSetup;

import workbench.storage.DataStore;

import workbench.sql.VariablePool;

/**
 * A panel to enter the value for Workbench variables inside SQL statements.
 *
 * @see workbench.sql.VariablePool
 *
 * @author  Thomas Kellerer
 */
public class VariablesEditor
  extends JPanel
  implements ValidatingComponent
{
  private final DataStore varData;
  private final WbTable variablesTable;
  private ValidatingDialog dialog;
  private final boolean autoAdvance;
  private final boolean autoCloseOnAdvance;
  private final String variablePoolID;

  public VariablesEditor(DataStore data, String variablePoolID)
  {
    super();
    autoAdvance = Settings.getInstance().getBoolProperty("workbench.gui.variables.editor.autoadvance", true);
    autoCloseOnAdvance = Settings.getInstance().getBoolProperty("workbench.gui.variables.editor.autoclose", autoAdvance);
    this.variablePoolID = variablePoolID;
    this.variablesTable = new VariablesTable(variablePoolID)
    {
      @Override
      public void userStoppedEditing(int row)
      {
        if (autoAdvance)
        {
          closeOrAdvance(row);
        }
      }
    };

    this.variablesTable.setRendererSetup(new RendererSetup(false));

    this.variablesTable.setRowSelectionAllowed(false);
    this.variablesTable.setColumnSelectionAllowed(false);
    this.varData = data;
    DataStoreTableModel model = new DataStoreTableModel(data);
    model.setLockedColumn(0);
    this.variablesTable.setModel(model);
    this.variablesTable.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);

    JLabel l = new JLabel(ResourceMgr.getString("TxtVariableInputText"));
    Border b = BorderFactory.createEmptyBorder(5, 2, 5, 2);
    l.setBorder(b);
    l.setBackground(UIManager.getColor("TextArea.background"));
    l.setForeground(UIManager.getColor("TextArea.foreground"));
    l.setOpaque(true);
    l.setHorizontalAlignment(SwingConstants.CENTER);

    this.setLayout(new BorderLayout(0,8));
    WbScrollPane scroll = new WbScrollPane(this.variablesTable);
    this.add(l, BorderLayout.NORTH);
    this.add(scroll, BorderLayout.CENTER);
  }

  private void closeOrAdvance(final int editedRow)
  {
    if (editedRow == variablesTable.getRowCount() - 1 && autoCloseOnAdvance)
    {
      dialog.approveAndClose();
    }
    else if (editedRow >= 0)
    {
      EventQueue.invokeLater(() ->
      {
        startEditRow(editedRow + 1);
      });
    }
  }

  @Override
  public void componentWillBeClosed()
  {
    // nothing to do
  }

  @Override
  public void componentDisplayed()
  {
    startEditRow(0);
  }

  private void startEditRow(int row)
  {
    this.variablesTable.setColumnSelectionInterval(1,1);
    this.variablesTable.editCellAt(row, 1);
    TableCellEditor editor = this.variablesTable.getCellEditor();
    if (editor instanceof WbTextCellEditor)
    {
      WbTextCellEditor wbedit = (WbTextCellEditor)editor;
      wbedit.selectAll();
      wbedit.requestFocus();
    }
  }

  @Override
  public boolean validateInput()
  {
    this.variablesTable.stopEditing();
    int rows = this.varData.getRowCount();
    for (int i=0; i < rows; i++)
    {
      String varName = this.varData.getValueAsString(i, 0);
      if (!VariablePool.getInstance(variablePoolID).isValidVariableName(varName))
      {
        String msg = ResourceMgr.getString("ErrIllegalVariableName");
        msg = msg.replace("%varname%", varName);
        msg = msg + "\n" + ResourceMgr.getString("ErrVarDefWrongName");
        WbSwingUtilities.showErrorMessage(this, msg);
        return false;
      }
    }
    return true;
  }

  private static boolean dialogResult;

  public static boolean showVariablesDialog(final DataStore vardata, final String variablePoolID)
  {

    WbSwingUtilities.invoke(() ->
    {
      VariablesEditor editor = new VariablesEditor(vardata, variablePoolID);
      String settingsId = "workbench.gui.variables.dialog";
      JFrame window = WbManager.getInstance().getCurrentWindow();
      editor.dialog = ValidatingDialog.createDialog(window, editor, ResourceMgr.getString("TxtEditVariablesWindowTitle"), null, 0, false);
      int width1 = -1;
      if (Settings.getInstance().restoreWindowSize(editor.dialog, settingsId))
      {
        editor.dialog.setLocationRelativeTo(window);
        width1 = (int)(editor.dialog.getWidth() * 0.92);
      }
      else
      {
        width1 = (int)(editor.dialog.getPreferredSize().getWidth() * 0.92);
      }
      // make the first column use as much space as needed
      // and the second one all the rest
      ColumnWidthOptimizer optimizer = new ColumnWidthOptimizer(editor.variablesTable);
      optimizer.optimizeColWidth(0, true);
      int w1 = editor.variablesTable.getColumnModel().getColumn(0).getWidth();
      int w2 = width1 - w1;
      editor.variablesTable.getColumnModel().getColumn(1).setPreferredWidth(w2);
      editor.dialog.setVisible(true);
      dialogResult = !editor.dialog.isCancelled();
      Settings.getInstance().storeWindowSize(editor.dialog, settingsId);
    });

    boolean result = false;
    if (dialogResult)
    {
      try
      {
        vardata.updateDb(null,null);
        result = true;
      }
      catch (Exception e)
      {
        LogMgr.logError(new CallerInfo(){}, "Error when saving values", e);
        result = false;
      }
    }
    return result;
  }

}
