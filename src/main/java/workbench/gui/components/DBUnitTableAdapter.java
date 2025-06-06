/*
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
package workbench.gui.components;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import workbench.db.ColumnIdentifier;

import workbench.storage.DataStore;

import workbench.util.WbDateFormatter;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.DataSetException;
import org.dbunit.dataset.ITable;
import org.dbunit.dataset.ITableMetaData;
import org.dbunit.dataset.RowOutOfBoundsException;
import org.dbunit.dataset.datatype.DataType;

/**
 *
 * @author Brian Bonner
 */
public class DBUnitTableAdapter
  implements ITable
{
  private int[] selectedRows;
  private final DataStore dataStore;
  private final WbDateFormatter timestampFormatter;
  private final WbDateFormatter dateFormatter;

  public DBUnitTableAdapter(DataStore dataStore)
  {
    this.dataStore = dataStore;
    dateFormatter = new WbDateFormatter("yyyy-MM-dd");
    timestampFormatter = new WbDateFormatter("yyyy-MM-dd HH:mm:ss.SSS");
  }

  @Override
  public Object getValue(int row, String column)
    throws DataSetException
  {
    if (row < 0 || row >= getRowCount())
    {
      throw new RowOutOfBoundsException();
    }

    if (selectedRows != null)
    {
      row = selectedRows[row];
    }
    Object value = dataStore.getValue(row, column);
    if (value instanceof LocalDate)
    {
      return dateFormatter.formatDate((LocalDate)value);
    }
    if (value instanceof Timestamp)
    {
      return timestampFormatter.formatTimestamp((Timestamp)value);
    }
    if (value instanceof LocalDateTime)
    {
      return timestampFormatter.formatTimestamp((LocalDateTime)value);
    }
    if (value instanceof OffsetDateTime)
    {
      return timestampFormatter.formatTimestamp((OffsetDateTime)value);
    }
    if (value instanceof ZonedDateTime)
    {
      return timestampFormatter.formatTimestamp((ZonedDateTime)value);
    }
    return value;
  }

  public void setSelectedRows(int[] rowsToCopy)
  {
    if (rowsToCopy == null)
    {
      selectedRows = null;
    }
    else
    {
      selectedRows = Arrays.copyOf(rowsToCopy, rowsToCopy.length);
    }
  }

  @Override
  public ITableMetaData getTableMetaData()
  {
    return new ITableMetaData()
    {

      @Override
      public String getTableName()
      {
        if (dataStore.getUpdateTable() != null)
        {
          return dataStore.getUpdateTable().getTableName();
        }
        else if (dataStore.getResultInfo() != null && dataStore.getResultInfo().getUpdateTable() != null)
        {
          return dataStore.getResultInfo().getUpdateTable().getTableName();
        }
        else if (dataStore.getInsertTable() != null)
        {
          return dataStore.getInsertTable();
        }
        else
        {
          return "UNKNOWN";
        }
      }

      @Override
      public Column[] getPrimaryKeys()
        throws DataSetException
      {
        if (!dataStore.hasPkColumns()) return null;

        ColumnIdentifier[] columns = dataStore.getColumns();

        if (columns == null) return null;

        List<Column> pkCols = new ArrayList<>(1);
        for (ColumnIdentifier col : columns)
        {
          if (col.isPkColumn())
          {
            DataType type = DataType.forSqlType(col.getDataType());
            Column pk = new Column(col.getColumnName(), type);
            pkCols.add(pk);
          }
        }
        if (pkCols.isEmpty()) return null;

        Column[] result = pkCols.toArray(Column[]::new);
        return result;
      }

      @Override
      public Column[] getColumns()
        throws DataSetException
      {
        List<Column> columns = new ArrayList<>();
        for (int i = 0; i < dataStore.getColumns().length; i++)
        {
          String columnName = dataStore.getColumnName(i);
          DataType columnType = DataType.forSqlType(dataStore.getColumnType(i));
          Column column = new Column(columnName, columnType);
          columns.add(column);
        }
        return columns.toArray(Column[]::new);
      }

      @Override
      public int getColumnIndex(String columnName)
        throws DataSetException
      {
        return dataStore.getColumnIndex(columnName);
      }

    };
  }

  @Override
  public int getRowCount()
  {
    if (selectedRows != null) return selectedRows.length;
    return dataStore.getRowCount();
  }

}
