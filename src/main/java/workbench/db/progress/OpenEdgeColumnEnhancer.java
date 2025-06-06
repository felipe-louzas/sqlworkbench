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
package workbench.db.progress;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.TreeMap;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.ColumnDefinitionEnhancer;
import workbench.db.ColumnIdentifier;
import workbench.db.DBID;
import workbench.db.TableDefinition;
import workbench.db.WbConnection;

import workbench.util.CaseInsensitiveComparator;

import workbench.db.JdbcUtils;

import workbench.util.SqlUtil;

/**
 * A class to read additional column level information for a table.
 *
 * The following additional information is retrieved:
 *
 * @author Thomas Kellerer
 */
public class OpenEdgeColumnEnhancer
  implements ColumnDefinitionEnhancer
{

  @Override
  public void updateColumnDefinition(TableDefinition table, WbConnection conn)
  {
    if (Settings.getInstance().getBoolProperty("workbench.db." + DBID.OPENEDGE.getId() + ".remarks.columns.retrieve", true))
    {
      updateColumnRemarks(table, conn);
    }
  }

  private void updateColumnRemarks(TableDefinition table, WbConnection conn)
  {
    PreparedStatement stmt = null;
    ResultSet rs = null;

    String sql =
      "select col, description \n" +
      "from sysprogress.syscolumns_full \n" +
      "where owner = ? \n" +
      "  and tbl = ? ";

    String tablename = SqlUtil.removeObjectQuotes(table.getTable().getTableName());
    String schema = SqlUtil.removeObjectQuotes(table.getTable().getSchema());

    LogMgr.logMetadataSql(new CallerInfo(){}, "column remarks", sql, schema, tablename);


    Map<String, String> remarks = new TreeMap<>(CaseInsensitiveComparator.INSTANCE);
    try
    {
      stmt = conn.getSqlConnection().prepareStatement(sql);
      stmt.setString(1, schema);
      stmt.setString(2, tablename);
      rs = stmt.executeQuery();
      while (rs.next())
      {
        String col = rs.getString(1);
        String remark = rs.getString(2);
        if (col != null && remark != null)
        {
          remarks.put(col.trim(), remark);
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

    for (ColumnIdentifier col : table.getColumns())
    {
      String remark = remarks.get(SqlUtil.removeObjectQuotes(col.getColumnName()));
      col.setComment(remark);
    }
  }
}
