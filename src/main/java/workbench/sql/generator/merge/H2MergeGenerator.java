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
package workbench.sql.generator.merge;

import workbench.db.TableIdentifier;

import workbench.storage.ResultInfo;
import workbench.storage.RowData;
import workbench.storage.RowDataContainer;
import workbench.storage.SqlLiteralFormatter;

/**
 *
 * @author Thomas Kellerer
 */
public class H2MergeGenerator
  extends AbstractMergeGenerator
{
  public H2MergeGenerator()
  {
    super(new SqlLiteralFormatter(SqlLiteralFormatter.ANSI_DATE_LITERAL_TYPE));
  }

  @Override
  public String generateMergeStart(RowDataContainer data)
  {
    StringBuilder result = new StringBuilder(100);
    generateStart(result, data, false);
    return result.toString();
  }

  @Override
  public String addRow(ResultInfo info, RowData row, long rowIndex)
  {
    StringBuilder sql = new StringBuilder(100);
    if (rowIndex > 0) sql.append(",\n  ");
    sql.append('(');
    appendValues(sql, info, row);
    sql.append(')');
    return sql.toString();
  }

  @Override
  public String generateMergeEnd(RowDataContainer data)
  {
    return ";\n";
  }

  @Override
  public String generateMerge(RowDataContainer data)
  {
    StringBuilder sql = new StringBuilder(data.getRowCount());
    generateStart(sql, data, true);
    sql.append(";\n");
    return sql.toString();
  }

  private void generateStart(StringBuilder sql, RowDataContainer data, boolean withData)
  {
    TableIdentifier tbl = data.getUpdateTable();
    sql.append("MERGE INTO ");
    sql.append(tbl.getTableExpression(data.getOriginalConnection()));
    sql.append(" (");

    ResultInfo info = data.getResultInfo();
    int colNr = 0;
    for (int col=0; col < info.getColumnCount(); col ++)
    {
      if (!includeColumn(info.getColumn(col))) continue;
      if (colNr > 0) sql.append(", ");
      sql.append(quoteObjectname(info.getColumnName(col)));
      colNr ++;
    }
    sql.append(")\n  KEY (");
    int pkCount = 0;
    for (int col=0; col < info.getColumnCount(); col ++)
    {
      if (info.getColumn(col).isPkColumn())
      {
        if (pkCount > 0) sql.append(", ");
        sql.append(quoteObjectname(info.getColumnName(col)));
        pkCount ++;
      }
    }
    sql.append(")\nVALUES\n  ");
    if (withData)
    {
      for (int row=0; row < data.getRowCount(); row++)
      {
        if (row > 0) sql.append(",\n  ");
        sql.append('(');
        appendValues(sql, info, data.getRow(row));
        sql.append(')');
      }
    }
  }

}

