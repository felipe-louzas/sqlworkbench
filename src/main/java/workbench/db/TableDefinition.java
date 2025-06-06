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
package workbench.db;

import java.util.Collections;
import java.util.List;

import workbench.util.CollectionUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class TableDefinition
{
  private TableIdentifier table;
  private List<ColumnIdentifier> columns;

  public TableDefinition(TableIdentifier id)
  {
    this(id, null);
  }

  public TableDefinition(TableIdentifier id, List<ColumnIdentifier> cols)
  {
    table = id;
    columns = cols;
  }

  public TableIdentifier getTable()
  {
    return table;
  }

  public List<ColumnIdentifier> getColumns()
  {
    if (columns == null) return Collections.emptyList();
    return columns;
  }

  public int getColumnCount()
  {
    if (columns == null) return 0;
    return columns.size();
  }

  public ColumnIdentifier findColumn(String colName)
  {
    if (CollectionUtil.isEmpty(columns)) return null;
    return ColumnIdentifier.findColumnInList(columns, colName);
  }

  @Override
  public String toString()
  {
    StringBuilder result = new StringBuilder(getColumnCount() * 10 + 25);
    result.append(table.toString());
    result.append(" (");
    List<ColumnIdentifier> cols = getColumns();
    for (int i=0; i < cols.size(); i++)
    {
      if (i > 0)
      {
        result.append(", ");
      }
      result.append(cols.get(i).getColumnName());
    }
    result.append(')');
    return result.toString();
  }
}
