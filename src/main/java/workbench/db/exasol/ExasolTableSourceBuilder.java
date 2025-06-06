/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
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
package workbench.db.exasol;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.JdbcUtils;
import workbench.db.TableIdentifier;
import workbench.db.TableSourceBuilder;
import workbench.db.WbConnection;

import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class ExasolTableSourceBuilder
  extends TableSourceBuilder

{
  public ExasolTableSourceBuilder(WbConnection con)
  {
    super(con);
  }

  @Override
  public void readTableOptions(TableIdentifier tbl, List<ColumnIdentifier> columns)
  {
    if (tbl.getSourceOptions().isInitialized()) return;

    String sql =
      "select column_name  \n" +
      "from exa_all_columns \n" +
      "where column_is_distribution_key \n" +
      "  and column_schema = ? \n" +
      "  and column_table = ? \n" +
      "order by column_ordinal_position";

    PreparedStatement pstmt = null;
    ResultSet rs = null;
    try
    {
      LogMgr.logMetadataSql(new CallerInfo(){}, "table source options", sql, tbl.getSchema(), tbl.getTableName());

      List<String> cols = new ArrayList<>(2);
      pstmt = dbConnection.getSqlConnection().prepareStatement(sql);
      pstmt.setString(1, tbl.getRawSchema());
      pstmt.setString(2, tbl.getRawTableName());
      rs = pstmt.executeQuery();
      while (rs.next())
      {
        String col = rs.getString(1);
        cols.add(dbConnection.getMetadata().quoteObjectname(col));
      }
      String colList = StringUtil.listToString(cols, ", ", false);
      String options = "DISTRIBUTE BY " + colList;
      tbl.getSourceOptions().setInlineOption(options);
      tbl.getSourceOptions().addConfigSetting("distribute_by", colList);
    }
    catch (Exception ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "table source options", sql, tbl.getSchema(), tbl.getTableName());
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }
  }
}
