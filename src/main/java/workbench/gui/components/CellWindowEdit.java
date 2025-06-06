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
package workbench.gui.components;

import java.awt.Frame;
import java.awt.Window;

import javax.swing.SwingUtilities;
import javax.swing.table.TableCellEditor;

import workbench.resource.ResourceMgr;

import workbench.db.TableIdentifier;

/**
 *
 * @author Thomas Kellerer
 */
public class CellWindowEdit
{
  private WbTable table;

  public CellWindowEdit(WbTable table)
  {
    this.table = table;
  }

  public void openEditWindow()
  {
    if (!table.isEditing()) return;

    int col = table.getEditingColumn();
    int row = table.getEditingRow();
    String data = null;
    TableCellEditor editor = table.getCellEditor();

    if (editor instanceof WbTextCellEditor)
    {
      WbTextCellEditor wbeditor = (WbTextCellEditor)editor;
      if (table.isEditing() && wbeditor.isModified())
      {
        data = wbeditor.getText();
      }
      else
      {
        data = table.getValueAsString(row, col);
      }
    }
    else
    {
      data = (String)editor.getCellEditorValue();
    }

    Window owner = SwingUtilities.getWindowAncestor(table);
    Frame ownerFrame = null;
    if (owner instanceof Frame)
    {
      ownerFrame = (Frame)owner;
    }

    String column = table.getColumnName(col);
    TableIdentifier updateTable = table.getDataStore().getUpdateTable();
    if (updateTable != null)
    {
      column = updateTable.getTableExpression() + "." + column;
    }
    
    String title = ResourceMgr.getString("TxtEditWindowTitle") + " - " + column;
    EditWindow w = new EditWindow(ownerFrame, title, data);
    try
    {
      w.setVisible(true);
      // we need to "cancel" the editor so that the data
      // in the editor component will not be written into the
      // table model!
      editor.cancelCellEditing();
      if (!w.isCancelled())
      {
        table.setValueAt(w.getText(), row, col);
      }
    }
    catch (Throwable th)
    {
      // ignore, should not happen
    }
    finally
    {
      w.dispose();
    }

  }
}
