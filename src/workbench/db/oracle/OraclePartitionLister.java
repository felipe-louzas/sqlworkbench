/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2020 Thomas Kellerer.
 *
 * Licensed under a modified Apache License, Version 2.0 (the "License")
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
package workbench.db.oracle;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.DbObject;
import workbench.db.PartitionLister;
import workbench.db.SubPartitionState;
import workbench.db.TableIdentifier;
import workbench.db.TablePartition;
import workbench.db.WbConnection;

import workbench.util.SqlUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class OraclePartitionLister
  extends OracleTablePartition
  implements PartitionLister
{
  private final WbConnection conn;

  public OraclePartitionLister(WbConnection conn)
    throws SQLException
  {
    super(conn, true);
    this.conn = conn;
  }

  @Override
  public List<TablePartition> getPartitions(TableIdentifier table)
  {
    List<TablePartition> result = new ArrayList<>();
    try
    {
      boolean isPartitioned = super.retrieveDefinition(table, conn);
      if (!isPartitioned) return null;

      SubPartitionState state = SubPartitionState.unknown;
      if (!this.hasSubPartitions())
      {
        state = SubPartitionState.none;
      }
      List<OraclePartitionDefinition> partitions = super.loadPartitions(table, conn);
      for (OraclePartitionDefinition oraPart : partitions)
      {
        TablePartition partition = new TablePartition();
        partition.setSchema(table.getRawSchema());
        partition.setPartitionName(oraPart.getName());
        partition.setHasSubPartitions(state);
        result.add(partition);
      }
    }
    catch (SQLException ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not load partitions",ex);
    }
    return result;
  }

  @Override
  public List<TablePartition> getSubPartitions(TableIdentifier baseTable, DbObject partition)
  {
    PreparedStatement pstmt = null;
    ResultSet rs = null;
    String sql =
        "-- SQL Workbench \n" +
        "select subpartition_name \n" +
        "from all_tab_subpartitions \n" +
        "where table_owner = ? \n" +
        "  and table_name = ? \n" +
        "  and partition_name = ? \n" +
        "order by subpartition_position";

    List<TablePartition> result = new ArrayList<>();

    String mainPartName = SqlUtil.removeObjectQuotes(partition.getObjectName());
    long start = System.currentTimeMillis();
    try
    {
      pstmt = conn.getSqlConnection().prepareStatement(sql);
      LogMgr.logMetadataSql(new CallerInfo(){}, "sub-partitions", sql, baseTable.getRawSchema(), baseTable.getRawTableName(), mainPartName);

      pstmt.setString(1, baseTable.getRawSchema());
      pstmt.setString(2, baseTable.getRawTableName());
      pstmt.setString(3, mainPartName);
      rs = pstmt.executeQuery();

      while (rs.next())
      {
        String name = rs.getString("subpartition_name");
        TablePartition part = new TablePartition();
        part.setName(name);
        part.setSchema(baseTable.getRawSchema());
        result.add(part);
      }
    }
    catch (SQLException ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "sub-partitions", sql, baseTable.getRawSchema(), baseTable.getRawTableName(), mainPartName);
    }
    finally
    {
      SqlUtil.closeAll(rs, pstmt);
    }
    long duration = System.currentTimeMillis() - start;
    LogMgr.logDebug(new CallerInfo(){}, "Retrieving sub partitions " + baseTable.getObjectName() + " took: " + duration + "ms");

    return result;
  }

  @Override
  public boolean supportsSubPartitions()
  {
    return true;
  }

}
