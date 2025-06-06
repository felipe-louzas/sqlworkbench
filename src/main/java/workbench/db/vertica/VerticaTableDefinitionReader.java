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
package workbench.db.vertica;


import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.DataTypeResolver;
import workbench.db.GeneratedColumnType;
import workbench.db.JdbcTableDefinitionReader;
import workbench.db.JdbcUtils;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.util.StringUtil;

/**
 * A class to retrieve meta-information from a Vertica database.
 *
 * It is used to get columns for internal tables.
 *
 * @author Tatiana Saltykova
 */
public class VerticaTableDefinitionReader
  extends JdbcTableDefinitionReader
{
  private final String retrieveTableColumns =
      "SELECT attname as name, \n" +
      "       atttypid as typeid, \n" +
      "       typname as type, \n" +
      "       attnotnull as notnullable, \n" +
      "       atthasdef as hasdefault, \n" +
      "       attisidentity as isidentity, \n" +
      "       attnum as position \n" +
      "FROM v_internal.vs_columns \n" +
      "WHERE relname = ? and nspname = ? \n" +
      "ORDER BY attnum";

  private final String retrieveViewColumns =
      "SELECT column_name as name, \n" +
      "       data_type_id as typeid, \n" +
      "       data_type as type, \n" +
      "       null as notnullable, \n" +
      "       null as isidentity, \n" +
      "       ordinal_position as position \n" +
      "FROM v_internal.vs_view_columns \n" +
      "WHERE table_name = ? and table_schema = ? \n" +
      "ORDER BY ordinal_position";

  public VerticaTableDefinitionReader(WbConnection conn)
  {
    super(conn);
  }

  @Override
  public List<ColumnIdentifier> getTableColumns(TableIdentifier table, DataTypeResolver typeResolver)
    throws SQLException
  {
    if (!table.getSchema().equals("v_internal"))
    {
      return super.getTableColumns(table, typeResolver);
    }

    String schema = StringUtil.trimQuotes(table.getSchema());
    String tablename = StringUtil.trimQuotes(table.getTableName());

    List<ColumnIdentifier> columns = new ArrayList<>();

    ResultSet rs = null;
    PreparedStatement stmt = null;
    String sql;

    if (table.getObjectType().equals("SYSTEM TABLE"))
    {
      sql = retrieveTableColumns;
    }
    else
    {
      sql = retrieveViewColumns;
    }

    try
    {

      stmt = dbConnection.getSqlConnection().prepareStatement(sql);
      LogMgr.logMetadataSql(new CallerInfo(){}, "columns", sql, tablename, schema);

      stmt.setString(1, tablename);
      stmt.setString(2, schema);

      rs = stmt.executeQuery();
      while (rs != null && rs.next())
      {
        ColumnIdentifier col = new ColumnIdentifier(rs.getString("name"));
        col.setDbmsType(rs.getString("type"));
        col.setIsNullable(!rs.getBoolean("notnullable"));
        if (rs.getBoolean("isidentity"))
        {
          col.setGeneratedColumnType(GeneratedColumnType.autoIncrement);
        }
        col.setPosition(rs.getInt("position"));
        columns.add(col);
      }
    }
    catch (SQLException se)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, se, "columns", sql, tablename, schema);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return columns;
  }

}
