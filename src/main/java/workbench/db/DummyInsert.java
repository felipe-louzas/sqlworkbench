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

import java.sql.SQLException;
import java.util.List;

import workbench.resource.Settings;

import workbench.sql.formatter.FormatterUtil;


/**
 * @author Thomas Kellerer
 */
public class DummyInsert
  extends DummyDML
  implements DbObject
{
  public DummyInsert(TableIdentifier tbl)
  {
    super(tbl);
    doFormat = Settings.getInstance().getDoFormatInserts();
  }

  public DummyInsert(TableIdentifier tbl, List<ColumnIdentifier> cols)
  {
    super(tbl, cols);
    doFormat = Settings.getInstance().getDoFormatInserts();
  }

  @Override
  public String getObjectType()
  {
    return "INSERT";
  }

  @Override
  public CharSequence getSource(WbConnection con)
    throws SQLException
  {
    List<ColumnIdentifier> columns = getColumns(con);

    StringBuilder sql = new StringBuilder(columns.size() * 20 + 100);
    sql.append(FormatterUtil.getKeyword("insert into"));
    sql.append(' ');
    sql.append(FormatterUtil.getIdentifier(table.getTableExpression(con)));
    sql.append(lineEnd);
    sql.append("  (");
    for (int i=0; i < columns.size(); i++)
    {
      if (i > 0) sql.append(", ");

      sql.append(getColumnName(columns.get(i), con));
    }
    sql.append(')');
    sql.append(lineEnd);
    sql.append(FormatterUtil.getKeyword("VALUES"));
    sql.append(lineEnd);
    sql.append("  (");
    for (int i=0; i < columns.size(); i++)
    {
      ColumnIdentifier col = columns.get(i);
      if (i > 0) sql.append(", ");
      sql.append(getValueString(col));
    }
    sql.append(");");
    return formatSql(sql.toString(), con);
  }

}
