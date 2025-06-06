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
package workbench.db.hana;


import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.TableIdentifier;
import workbench.db.TableSourceBuilder;
import workbench.db.WbConnection;

import workbench.db.JdbcUtils;

/**
 *
 * @author Thomas Kellerer
 */
public class HanaTableSourceBuilder
  extends TableSourceBuilder
{

  public HanaTableSourceBuilder(WbConnection con)
  {
    super(con);
  }

  @Override
  public void readTableOptions(TableIdentifier tbl, List<ColumnIdentifier> columns)
  {
    if (tbl == null) return;
    if (tbl.getSourceOptions().isInitialized()) return;

    PreparedStatement pstmt = null;
    ResultSet rs = null;
    String sql =
      "select table_type \n" +
      "from sys.tables\n" +
      "where table_name = ? \n" +
      "and schema_name = ?";

    long start = System.currentTimeMillis();
    try
    {
      pstmt = this.dbConnection.getSqlConnection().prepareStatement(sql);
      pstmt.setString(1, tbl.getRawTableName());
      pstmt.setString(2, tbl.getRawSchema());
      LogMgr.logMetadataSql(new CallerInfo(){}, "table type", sql, tbl.getRawTableName(), tbl.getRawSchema());
      rs = pstmt.executeQuery();
      if (rs.next())
      {
        String type = rs.getString(1);
        tbl.getSourceOptions().setTypeModifier(type);
      }
      long duration = System.currentTimeMillis() - start;
      LogMgr.logDebug(new CallerInfo(){}, "Retrieving table type took: " + duration + "ms");
    }
    catch (SQLException e)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, e, "table type", sql, tbl.getRawTableName(), tbl.getRawSchema());
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }
    tbl.getSourceOptions().setInitialized();
  }

}
