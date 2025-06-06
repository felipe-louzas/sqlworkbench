/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
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
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.DefaultFKHandler;
import workbench.db.FKMatchType;
import workbench.db.JdbcUtils;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.storage.DataStore;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class PostgresFKHandler
  extends DefaultFKHandler
{
  public PostgresFKHandler(WbConnection conn)
  {
    super(conn);
  }

  @Override
  public boolean supportsRemarks()
  {
    return true;
  }

  @Override
  public boolean shouldGenerate(FKMatchType type)
  {
    return type != null && type != FKMatchType.SIMPLE;
  }

  @Override
  public boolean supportsMatchType()
  {
    return true;
  }

  @Override
  protected DataStore getRawKeyList(TableIdentifier tbl, boolean exported)
    throws SQLException
  {
    DataStore ds = super.getRawKeyList(tbl, exported);
    ds.addColumn(MATCH_TYPE_COLUMN);
    ds.addColumn(REMARKS_COLUMN);
    updateConstraintResult(tbl, ds);
    return ds;
  }

  private void updateConstraintResult(TableIdentifier tbl, DataStore keys)
  {
    int remarksColumn = keys.getColumnIndex(COLUMN_NAME_REMARKS);
    int matchColumn = keys.getColumnIndex(COLUMN_NAME_MATCH_TYPE);
    if (keys.getRowCount() <= 0) return;

    if (remarksColumn < 0 || matchColumn < 0) return;

    int nameColumn = keys.getColumnIndex("FK_NAME");

    String sql =
      "-- SQL Workbench/J \n" +
      "select c.conname, \n" +
      "       c.confmatchtype, \n" +
      "       pg_catalog.obj_description(c.oid, 'pg_constraint') as remarks\n" +
      "from pg_catalog.pg_constraint c\n" +
      "  join pg_catalog.pg_namespace s on s.oid = c.connamespace\n" +
      "where contype = 'f' \n" +
      "  and s.nspname = ? \n" +
      "  and conname in (";

    // The DataStore contains one row per column that is part of a FK.
    // But we only need each FK name once in the query for the details
    Set<String> fkNames = new HashSet<>();
    for (int row = 0; row < keys.getRowCount(); row++)
    {
      String fkName = SqlUtil.quoteLiteral(keys.getValueAsString(row, nameColumn));
      fkNames.add(fkName);
    }
    String condition = fkNames.stream().collect(Collectors.joining(","));
    sql += condition + ")";

    String type = "foreign key details for table " + tbl.getTableExpression(dbConnection);
    LogMgr.logMetadataSql(new CallerInfo(){}, type, sql, tbl.getRawSchema());

    PreparedStatement pstmt = null;
    ResultSet rs = null;

    try
    {
      pstmt = this.dbConnection.getSqlConnection().prepareStatement(sql);
      pstmt.setString(1, tbl.getRawSchema());
      rs = pstmt.executeQuery();
      while (rs.next())
      {
        String conname = rs.getString(1);
        FKMatchType matchType = getMatchType(rs.getString(2));
        String comment = rs.getString(3);
        int row = findConstraint(keys, nameColumn, conname);
        if (row > -1)
        {
          keys.setValue(row, remarksColumn, comment);
          keys.setValue(row, matchColumn, matchType.toString());
        }
      }
    }
    catch (Throwable th)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, th, type, sql, tbl.getRawSchema());
    }
    finally
    {
      JdbcUtils.close(pstmt, rs);
    }
  }

  private FKMatchType getMatchType(String pgType)
  {
    if (StringUtil.isBlank(pgType)) return FKMatchType.UNKNOWN;
    switch (pgType)
    {
      case "f":
        return FKMatchType.FULL;
      case "p":
        return FKMatchType.PARTIAL;
      case "s":
        return FKMatchType.SIMPLE;
    }
    return FKMatchType.UNKNOWN;
  }

  private int findConstraint(DataStore keys, int nameColumn, String name)
  {
    for (int row = 0; row < keys.getRowCount(); row++)
    {
      String fkName = keys.getValueAsString(row, nameColumn);
      if (StringUtil.equalStringIgnoreCase(fkName, name))
      {
        return row;
      }
    }
    return -1;
  }

}
