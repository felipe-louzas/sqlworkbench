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
package workbench.gui.filter;

import workbench.gui.components.WbTable;

import workbench.storage.filter.AndExpression;
import workbench.storage.filter.ColumnComparator;
import workbench.storage.filter.ComparatorFactory;
import workbench.storage.filter.ComplexExpression;
import workbench.storage.filter.IsNullComparator;
import workbench.storage.filter.OrExpression;

/**
 * @author Thomas Kellerer
 */
public class SelectionFilter
{
  private final WbTable client;

  public SelectionFilter(WbTable tbl)
  {
    this.client = tbl;
  }

  public void applyFilter()
  {
    if (client == null) return;
    int rowCount = client.getSelectedRowCount();
    int colCount = client.getSelectedColumnCount();

    if (rowCount < 1 || (rowCount > 1 && colCount != 1)) return;
    int[] columns = null;
    // if whole rows are selected, use the currently
    // focused column for the filter
    if (colCount == client.getColumnCount())
    {
      columns = new int[]{client.getSelectedColumn()};
    }
    else
    {
      columns = client.getSelectedColumns();
    }
    if (columns == null || columns.length == 0) return;
    ComplexExpression expr = null;
    if (rowCount == 1)
    {
      expr = new AndExpression();
    }
    else
    {
      expr = new OrExpression();
    }

    int[] rows = client.getSelectedRows();
    for (int ri = 0; ri < rows.length; ri++)
    {
      int row = rows[ri];

      for (int i = 0; i < columns.length; i++)
      {
        String name = client.getColumnName(columns[i]);
        Object value = client.getValueAt(row, columns[i]);
        ColumnComparator comparator = null;

        if (value == null)
        {
          comparator = new IsNullComparator();
        }
        else
        {
          ComparatorFactory factory = new ComparatorFactory();
          comparator = factory.findEqualityComparatorFor(value.getClass());
        }

        if (comparator != null)
        {
          expr.addColumnExpression(name, comparator, value);
        }
      }
    }
    if (expr.hasFilter()) client.applyFilter(expr);
  }
}
