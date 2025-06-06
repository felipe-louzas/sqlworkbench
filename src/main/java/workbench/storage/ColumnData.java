/*
 * ColumnData.java
 *
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
package workbench.storage;

import workbench.db.ColumnIdentifier;

/**
 * A wrapper class do hold the current value of a column and it's definition.
 *
 * The column definition is represented by a {@link workbench.db.ColumnIdentifier}<br/>
 * The column value can be any Java object<br/>
 *
 * This is used by {@link workbench.storage.DmlStatement} to store the values when creating PreparedStatements
 *
 * @author Thomas Kellerer
 */
public class ColumnData
{
  private Object data;
  private final ColumnIdentifier column;

  /**
   * Creates a new instance of ColumnData
   *
   * @param value The current value of the column
   * @param colid The definition of the column
   */
  public ColumnData(Object value, ColumnIdentifier colid)
  {
    data = value;
    column = colid;
  }

  public void setValue(Object value)
  {
    this.data = value;
  }
  
  public Object getValue()
  {
    return data;
  }

  public ColumnIdentifier getIdentifier()
  {
    return column;
  }

  public boolean isNull()
  {
    return (data == null);
  }

  @Override
  public boolean equals(Object obj)
  {
    if (obj == null) return false;
    if (obj instanceof ColumnData)
    {
      final ColumnData other = (ColumnData) obj;
      return this.column.equals(other.column);
    }
    else if (obj instanceof ColumnIdentifier)
    {
      return this.column.equals(obj);
    }
    return false;
  }

  @Override
  public int hashCode()
  {
    return column.hashCode();
  }

  @Override
  public String toString()
  {
    return column.getColumnName() + " = " + (data == null ? "NULL" : data.toString());
  }

  public ColumnData createCopy(Object newValue)
  {
    return new ColumnData(newValue, column);
  }
}
