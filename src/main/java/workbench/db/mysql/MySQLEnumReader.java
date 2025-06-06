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
package workbench.db.mysql;

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.TableDefinition;
import workbench.db.WbConnection;

import workbench.db.JdbcUtils;

/**
 * A class to retrieve enum definitions from a MySQL database.
 * <br/>
 * The method readEnums() can be used to post-process a TableDefinition
 *
 * @author  Thomas Kellerer
 * @see workbench.db.DbMetadata#getTableDefinition(workbench.db.TableIdentifier)
 */
public class MySQLEnumReader
{

  /**
   * Update the passed TableDefinition with information about the enums used in the columns.
   *
   * For each ColumnIdentier in the table that is defined as an enum the dbms type is updated
   * with the enum name.
   *
   * @param tbl  the table definition to check
   * @param connection the connection to use
   */
  public void readEnums(TableDefinition tbl, WbConnection connection)
  {
    if (!hasEnums(tbl)) return;

    Statement stmt = null;
    ResultSet rs = null;
    HashMap<String, String> defs = new HashMap<>(17);

    String sql = "SHOW COLUMNS FROM " + tbl.getTable().getTableExpression(connection);
    LogMgr.logMetadataSql(new CallerInfo(){}, "table enums", sql);

    try
    {
      stmt = connection.createStatement();
      rs = stmt.executeQuery(sql);

      while (rs.next())
      {
        String column = rs.getString(1);
        if (column == null) continue;

        String type = rs.getString(2);
        if (type == null) continue;

        String ltype = type.toLowerCase();
        if (ltype.startsWith("enum") || ltype.startsWith("set"))
        {
          defs.put(column, type);
        }
      }

      List<ColumnIdentifier> columns = tbl.getColumns();
      for (ColumnIdentifier col : columns)
      {
        String name = col.getColumnName();
        String type = defs.get(name);
        if (type != null)
        {
          col.setDbmsType(type);
        }
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "table enums", e);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
  }

  private boolean hasEnums(TableDefinition tbl)
  {
    for (ColumnIdentifier col : tbl.getColumns())
    {
      String typeName = col.getDbmsType();
      if (typeName.toLowerCase().startsWith("enum") || typeName.toLowerCase().startsWith("set"))
      {
        return true;
      }
    }
    return false;
  }
}
