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
package workbench.db.mssql;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ColumnDefinitionEnhancer;
import workbench.db.ColumnIdentifier;
import workbench.db.GeneratedColumnType;
import workbench.db.JdbcUtils;
import workbench.db.TableDefinition;
import workbench.db.WbConnection;

import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * A class to read additional column level information for a table.
 *
 * The following additional information is retrieved:
 * <ul>
 * <li>
 *     column remarks by using stored procedures for accessing "extended properties".
 *     This workaround is necessary because SQL Server does not support real (ANSI SQL) comments and the driver
 *     consequently does not return them either.
 * </li>
 * <li>Definition of computed (virtual) columns </li>
 * <li>Column level collation definition</li>
 * </ul>
 *
 * @author Thomas Kellerer
 */
public class SqlServerColumnEnhancer
  implements ColumnDefinitionEnhancer
{
  private final Map<String, String> defaultCollations = new HashMap<>();

  @Override
  public void updateColumnDefinition(TableDefinition table, WbConnection conn)
  {
    if (SqlServerUtil.isSqlServer2000(conn))
    {
      updateColumnInformation(table, conn);
    }

    if (conn.getDbSettings().getBoolProperty("remarks.column.retrieve", true))
    {
      updateColumnRemarks(table, conn);
    }
  }

  private void updateColumnInformation(TableDefinition table, WbConnection conn)
  {
    String tablename;
    String sql;

    if (SqlServerUtil.isSqlServer2005(conn))
    {
      sql =
        "select c.name as column_name, \n" +
        "       cc.definition, \n" +
        "       cc.is_persisted, \n" +
        "       t.name as data_type, \n" +
        "       t.is_user_defined, \n" +
        "       c.collation_name \n" +
        "from sys.columns c \n" +
        "  left join sys.types t on c.user_type_id = t.user_type_id \n" +
        "  left join sys.computed_columns cc on cc.column_id = c.column_id and cc.object_id = c.object_id \n" +
        "where c.object_id = object_id(?)";

      tablename = table.getTable().getTableExpression(conn);
    }
    else
    {
      // This is for SQL Server 2000
      sql =
        "select c.name, t.[text], 0 as is_persisted, null as data_type, 0 as is_user_defined, c.collation \n" +
        "from sysobjects o with (nolock) \n" +
        "  join syscolumns c with (nolock) on o.id = c.id \n" +
        "  left join syscomments t with (nolock) on t.number = c.colid and t.id = c.id \n" +
        "where o.xtype = 'U' \n" +
        "  and o.name = ?";
      tablename = table.getTable().getRawTableName();
    }

    LogMgr.logMetadataSql(new CallerInfo(){}, "column information", sql, tablename);

    PreparedStatement stmt = null;
    ResultSet rs = null;

    String defaultCollation = getDatabaseCollation(table.getTable().getRawCatalog(), conn);

    String types = conn.getDbSettings().getProperty("adjust.datatypes", "geometry,geography");

    Set<String> nonJdbcTypes = CollectionUtil.caseInsensitiveSet();
    nonJdbcTypes.addAll(StringUtil.stringToList(types, ",", true, true));

    List<ColumnIdentifier> columns = table.getColumns();

    try
    {
      stmt = conn.getSqlConnection().prepareStatement(sql);
      stmt.setString(1, tablename);
      rs = stmt.executeQuery();
      while (rs.next())
      {
        String colname = rs.getString(1);
        String def = StringUtil.trim(rs.getString(2));
        boolean isPersisted = rs.getBoolean(3);
        String dataType = rs.getString(4);
        boolean isUserType = rs.getBoolean(5);
        String collation = rs.getString(6);

        ColumnIdentifier col = ColumnIdentifier.findColumnInList(columns, colname);
        if (col == null) continue; // shouldn't happen

        if (StringUtil.isNotEmpty(def))
        {
          if (!def.startsWith("("))
          {
            def += "(" + def + ")";
          }
          def = "AS " + def;
          if (isPersisted)
          {
            def = def + " PERSISTED";
          }
          col.setGeneratedExpression(def, GeneratedColumnType.computed);
        }

        if (isNonDefault(collation, defaultCollation))
        {
          String fullType = col.getDbmsType() + " COLLATE " + collation;
          col.setDbmsType(fullType);
          col.setCollation(collation);
        }

        if (isUserType)
        {
          col.setDefaultClause(null);
          col.setDefaultValue(null);
          col.setDbmsType(dataType);
        }

        // the JDBC driver returns geometry and geography as "VARBINARY"
        // which is incorrect and breaks many things
        if (nonJdbcTypes.contains(dataType))
        {
          col.setDbmsType(dataType);
          col.setDataType(Types.OTHER);
        }
        if (col.getDbmsType().toLowerCase().contains("identity"))
        {
          col.setGeneratedColumnType(GeneratedColumnType.identity);
        }
      }
    }
    catch (Exception e)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, e, "column information", sql, tablename);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
  }

  private String getDatabaseCollation(String database, WbConnection conn)
  {
    String collation = defaultCollations.get(database);
    if (collation != null)
    {
      return collation;
    }

    Statement info = null;
    ResultSet rs = null;

    String sql = "select cast(databasepropertyex('" + database + "', 'Collation') as varchar(128))";
    try
    {

      LogMgr.logMetadataSql(new CallerInfo(){}, "database collation", sql);

      info = conn.createStatement();
      rs = info.executeQuery(sql);
      if (rs.next())
      {
        collation = rs.getString(1);
      }
    }
    catch (SQLException e)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, e, "database collation", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, info);
    }
    defaultCollations.put(database, collation);
    return collation;
  }


  private void updateColumnRemarks(TableDefinition table, WbConnection conn)
  {
    PreparedStatement stmt = null;
    ResultSet rs = null;

    String tablename = SqlUtil.removeObjectQuotes(table.getTable().getTableName());
    String schema = SqlUtil.removeObjectQuotes(table.getTable().getSchema());

    String propName = conn.getDbSettings().getProperty(SqlServerObjectListEnhancer.REMARKS_PROP_NAME, SqlServerObjectListEnhancer.REMARKS_PROP_DEFAULT);

    // I have to cast to a varchar with a specified length otherwise
    // the remarks will be truncated at 31 characters for some strange reason
    // varchar(8000) character should work on any sensible SQL Server version  (varchar(max) wouldn't work on SQL Server 2000)
    String sql = "SELECT objname, cast(value as varchar(8000)) as value \nFROM ";

    if (SqlServerUtil.isSqlServer2005(conn))
    {
      sql += "fn_listextendedproperty ('" + propName + "','schema', ?, 'table', ?, 'column', null)";
    }
    else
    {
      // SQL Server 2000 (and probably before) uses a different function name and parameters
      sql += "::fn_listextendedproperty ('" + propName + "','user', ?, 'table', ?, 'column', null)";
    }

    LogMgr.logMetadataSql(new CallerInfo(){}, "column remarks", sql, schema, tablename);

    List<ColumnIdentifier> columns = table.getColumns();

    try
    {
      stmt = conn.getSqlConnection().prepareStatement(sql);
      stmt.setString(1, schema);
      stmt.setString(2, tablename);
      rs = stmt.executeQuery();
      while (rs.next())
      {
        String colname = StringUtil.trim(rs.getString(1));
        String remark = rs.getString(2);
        if (colname != null && remark != null)
        {
          ColumnIdentifier col = ColumnIdentifier.findColumnInList(columns, colname);
          if (col != null)
          {
            col.setComment(remark);
          }
        }
      }
    }
    catch (Exception e)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, e, "column remarks", sql, schema, tablename);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
  }

  private boolean isNonDefault(String value, String defaultValue)
  {
    if (StringUtil.isEmpty(value)) return false;
    return !value.equals(defaultValue);
  }
}
