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

import javax.swing.JTable;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import workbench.util.NumberStringCache;

/**
 *
 * @author Thomas Kellerer
 */
public class TableRowHeaderModel
  implements TableModel
{
  private JTable table;

  public TableRowHeaderModel(JTable client)
  {
    this.table = client;
  }

  @Override
  public int getRowCount()
  {
    return table.getRowCount();
  }

  @Override
  public int getColumnCount()
  {
    return 1;
  }

  @Override
  public Object getValueAt(int rowIndex, int columnIndex)
  {
    return NumberStringCache.getNumberString(rowIndex + 1);
  }

  @Override
  public String getColumnName(int columnIndex)
  {
    return "";
  }

  @Override
  public Class<?> getColumnClass(int columnIndex)
  {
    return String.class;
  }

  @Override
  public boolean isCellEditable(int rowIndex, int columnIndex)
  {
    return false;
  }

  @Override
  public void setValueAt(Object aValue, int rowIndex, int columnIndex)
  {
  }

  @Override
  public void addTableModelListener(TableModelListener l)
  {
  }

  @Override
  public void removeTableModelListener(TableModelListener l)
  {
  }
}
