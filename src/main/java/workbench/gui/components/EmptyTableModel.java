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

import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;


/**
 *
 * @author Thomas Kellerer
 */
public class EmptyTableModel
  implements TableModel
{
  public static final TableModel EMPTY_MODEL = new EmptyTableModel();

  private EmptyTableModel()
  {
  }

  @Override
  public Object getValueAt(int row, int col)
  {
    return "";
  }

  @Override
  public void setValueAt(Object aValue, int row, int column)
  {
  }

  @Override
  public int getColumnCount()
  {
    return 0;
  }

  @Override
  public int getRowCount()
  {
    return 0;
  }

  @Override
  public boolean isCellEditable(int row, int column)
  {
    return false;
  }

  @Override
  public void addTableModelListener(TableModelListener l)
  {
  }

  @Override
  public Class getColumnClass(int columnIndex)
  {
    return String.class;
  }

  @Override
  public String getColumnName(int columnIndex)
  {
    return "";
  }

  @Override
  public void removeTableModelListener(TableModelListener l)
  {
  }

}
