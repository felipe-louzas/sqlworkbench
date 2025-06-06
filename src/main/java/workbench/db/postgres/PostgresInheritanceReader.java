/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
 * that restricts the use for certain governments.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.sql-workbench.eu/manual/license.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.eu
 */
package workbench.db.postgres;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.JdbcUtils;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

/**
 *
 * @author Thomas Kellerer
 */
public class PostgresInheritanceReader
{
  public List<InheritanceEntry> getChildren(WbConnection dbConnection, TableIdentifier table)
  {
    List<InheritanceEntry> result = new ArrayList<>();

    if (table == null) return result;

    PreparedStatement pstmt = null;
    ResultSet rs = null;

    String sql83 =
      "-- SQL Workbench/J \n" +
      "select bt.relname as table_name, bns.nspname as table_schema, 0 as level \n" +
      "from pg_catalog.pg_class ct \n" +
      "    join pg_catalog.pg_namespace cns on ct.relnamespace = cns.oid and cns.nspname = ? \n" +
      "    join pg_catalog.pg_inherits i on i.inhparent = ct.oid and ct.relname = ? \n" +
      "    join pg_catalog.pg_class bt on i.inhrelid = bt.oid \n" +
      "    join pg_catalog.pg_namespace bns on bt.relnamespace = bns.oid ";
    if (dbConnection.getDbSettings().returnAccessibleTablesOnly())
    {
      sql83 += "\nwhere has_table_privilege(ct.oid, 'select') \n";
    }

    // Recursive version for 8.4+ based Craig Ringer's statement from here: https://stackoverflow.com/a/12139506/330315
    String sql84 =
      "-- SQL Workbench/J \n" +
      "with recursive inh as ( \n" +
      "\n" +
      "  select i.inhrelid, 1 as level, array[inhrelid] as path \n" +
      "  from pg_catalog.pg_inherits i  \n" +
      "    join pg_catalog.pg_class cl on i.inhparent = cl.oid \n" +
      "    join pg_catalog.pg_namespace nsp on cl.relnamespace = nsp.oid \n" +
      "  where nsp.nspname = ? \n" +
      "    and cl.relname = ? \n" +
      "" +
      "  union all \n" +
      "\n" +
      "  select i.inhrelid, inh.level + 1, inh.path||i.inhrelid \n" +
      "  from inh \n" +
      "    join pg_catalog.pg_inherits i on (inh.inhrelid = i.inhparent) \n" +
      ") \n" +
      "select ct.relname as table_name, nsp.nspname as table_schema, inh.level \n" +
      "from inh \n" +
      "  join pg_catalog.pg_class ct on (inh.inhrelid = ct.oid) \n" +
      "  join pg_catalog.pg_namespace nsp on (ct.relnamespace = nsp.oid) \n";

    if (dbConnection.getDbSettings().returnAccessibleTablesOnly())
    {
      sql84 += "where has_table_privilege(ct.oid, 'select') \n";
    }
    sql84 += "order by path";

    boolean is84 = JdbcUtils.hasMinimumServerVersion(dbConnection, "8.4");

    // wenn putting the "?" expression directly into the prepareStatement() call, this generates an error with Java 8
    final String sqlToUse = is84 ? sql84 : sql83;

    Savepoint sp = null;
    try
    {
      // Retrieve direct child table(s) for this table
      // this does not handle multiple inheritance
      sp = dbConnection.setSavepoint();
      pstmt = dbConnection.getSqlConnection().prepareStatement(sqlToUse);
      pstmt.setString(1, table.getSchema());
      pstmt.setString(2, table.getTableName());
      LogMgr.logMetadataSql(new CallerInfo(){}, "child tables", sqlToUse, table.getSchema(), table.getTableName());
      rs = pstmt.executeQuery();
      while (rs.next())
      {
        String tableName = rs.getString(1);
        String schemaName = rs.getString(2);
        int level = rs.getInt(3);
        TableIdentifier tbl = new TableIdentifier(schemaName, tableName);
        result.add(new InheritanceEntry(tbl, level));
      }
      dbConnection.releaseSavepoint(sp);
    }
    catch (Exception e)
    {
      dbConnection.rollback(sp);
      LogMgr.logMetadataError(new CallerInfo(){}, e, "child tables", sqlToUse, table.getSchema(), table.getTableName());
      return new ArrayList<>();
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }
    return result;
  }


  public List<TableIdentifier> getParents(WbConnection dbConnection, TableIdentifier table)
  {
    if (table == null) return null;

    PreparedStatement pstmt = null;
    ResultSet rs = null;

    String sql =
      "-- SQL Workbench/J \n" +
      "select bt.relname as table_name, bns.nspname as table_schema \n" +
      "from pg_catalog.pg_class ct \n" +
      "  join pg_catalog.pg_namespace cns on ct.relnamespace = cns.oid and cns.nspname = ? \n" +
      "  join pg_catalog.pg_inherits i on i.inhrelid = ct.oid and ct.relname = ? \n" +
      "  join pg_catalog.pg_class bt on i.inhparent = bt.oid \n" +
      "  join pg_catalog.pg_namespace bns on bt.relnamespace = bns.oid \n" +
      "where bt.relkind <> 'p'";

    if (dbConnection.getDbSettings().returnAccessibleTablesOnly())
    {
      sql += "\n  and pg_catalog.has_table_privilege(ct.oid, 'select') \n";
    }

    Savepoint sp = null;
    List<TableIdentifier> result = new ArrayList<>();
    try
    {
      // Retrieve parent table(s) for this table
      sp = dbConnection.setSavepoint();
      pstmt = dbConnection.getSqlConnection().prepareStatement(sql);
      pstmt.setString(1, table.getSchema());
      pstmt.setString(2, table.getTableName());

      LogMgr.logMetadataSql(new CallerInfo(){}, "table inheritance", sql, table.getSchema(), table.getTableName());

      rs = pstmt.executeQuery();
      while (rs.next())
      {
        String tableName = rs.getString(1);
        String schema = rs.getString(2);
        result.add(new TableIdentifier(schema, tableName));
      }
      dbConnection.releaseSavepoint(sp);
    }
    catch (Exception e)
    {
      dbConnection.rollback(sp);
      LogMgr.logMetadataError(new CallerInfo(){}, e, "table inheritance" + sql, table.getSchema(), table.getTableName());
      return null;
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }
    return result;
  }

}
