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
package workbench.db.firebird;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.JdbcUtils;
import workbench.db.SequenceAdjuster;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;


/**
 * A class to sync the sequences related to the columns of a table with the current values.
 *
 * This is intended to be used after doing bulk inserts into the database.
 *
 * @author Thomas Kellerer
 */
public class FirebirdSequenceAdjuster
  implements SequenceAdjuster
{
  public FirebirdSequenceAdjuster()
  {
  }

  @Override
  public int adjustTableSequences(WbConnection connection, TableIdentifier table, boolean includeCommit)
    throws SQLException
  {
    List<String> columns = getIdentityColumns(connection, table);

    for (String column : columns)
    {
      syncSingleSequence(connection, table, column);
    }

    if (includeCommit && !connection.getAutoCommit())
    {
      connection.commit();
    }
    return columns.size();
  }

  private void syncSingleSequence(WbConnection dbConnection, TableIdentifier table, String column)
    throws SQLException
  {
    Statement stmt = null;
    ResultSet rs = null;
    String ddl = null;

    try
    {
      stmt = dbConnection.createStatement();

      long maxValue = -1;
      rs = stmt.executeQuery("select max(" + column + ") from " + table.getTableExpression(dbConnection));

      if (rs.next())
      {
        maxValue = rs.getLong(1) + 1;
        JdbcUtils.closeResult(rs);
      }

      if (maxValue > 0)
      {
        ddl = "alter table " + table.getTableExpression(dbConnection) + " alter column " + column + " restart with " + Long.toString(maxValue);
        LogMgr.logDebug(new CallerInfo(){}, "Syncing sequence using: " + ddl);
        stmt.execute(ddl);
      }
    }
    catch (SQLException ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not adjust sequence using:\n" + ddl, ex);
      throw ex;
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
  }

  private List<String> getIdentityColumns(WbConnection dbConnection, TableIdentifier table)
  {
    List<String> result = new ArrayList<>(1);
    try
    {
      List<ColumnIdentifier> columns = dbConnection.getMetadata().getTableColumns(table, false);
      for (ColumnIdentifier col : columns)
      {
        if (col.isAutoincrement())
        {
          result.add(col.getColumnName());
        }
      }
    }
    catch (SQLException ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not read sequence columns", ex);
    }
    return result;
  }

}
