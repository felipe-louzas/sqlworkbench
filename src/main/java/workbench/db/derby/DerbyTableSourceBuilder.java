/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2024 Thomas Kellerer
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
package workbench.db.derby;

import workbench.db.ColumnIdentifier;
import workbench.db.TableSourceBuilder;
import workbench.db.WbConnection;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class DerbyTableSourceBuilder
  extends TableSourceBuilder
{

  public DerbyTableSourceBuilder(WbConnection con)
  {
    super(con);
  }

  @Override
  protected String getColumnSQL(ColumnIdentifier column, int maxTypeLength, String columnConstraint)
  {
    String defaultValue = column.getDefaultValue();
    if (StringUtil.isNotBlank(defaultValue) && defaultValue.startsWith("AUTOINCREMENT:"))
    {
      StringBuilder sql = new StringBuilder(100);
      sql.append(StringUtil.padRight(column.getDbmsType(), maxTypeLength));
      sql.append(" GENERATED ALWAYS AS IDENTITY");
      // "start 5 increment 10";
      String options = defaultValue.substring("AUTOINCREMENT:".length() + 1).toLowerCase();
      options = options.replace("start", "START WITH");
      options = options.replace(" increment", ", INCREMENT BY");
      sql.append(" (");
      sql.append(options);
      sql.append(")");
      return sql.toString();
    }
    else if (StringUtil.isNotBlank(defaultValue) && defaultValue.equals("GENERATED_BY_DEFAULT"))
    {
      StringBuilder sql = new StringBuilder(100);
      sql.append(StringUtil.padRight(column.getDbmsType(), maxTypeLength));
      sql.append(" GENERATED BY DEFAULT AS IDENTITY");
      return sql.toString();
    }
    else
    {
      return super.getColumnSQL(column, maxTypeLength, columnConstraint);
    }
  }

}
