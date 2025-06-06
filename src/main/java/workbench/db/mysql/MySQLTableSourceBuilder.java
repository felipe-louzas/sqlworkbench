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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.*;

import workbench.db.JdbcUtils;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class MySQLTableSourceBuilder
  extends TableSourceBuilder
{

  public MySQLTableSourceBuilder(WbConnection con)
  {
    super(con);
  }

  @Override
  public void readTableOptions(TableIdentifier table, List<ColumnIdentifier> columns)
  {
    if (table == null) return;
    if (table.getSourceOptions().isInitialized()) return;

    StringBuilder result = null;

    PreparedStatement pstmt = null;
    ResultSet rs = null;
    String sql =
      "select engine, \n"  +
      "       table_comment, \n" +
      "       table_collation \n" +
      "from information_schema.tables \n" +
      "where table_schema = ? \n " +
      "  and table_name = ? ";

    String defaultCollation = getDefaultCollation();
    try
    {
      pstmt = dbConnection.getSqlConnection().prepareStatement(sql);
      pstmt.setString(1, table.getCatalog());
      pstmt.setString(2, table.getRawTableName());

      rs = pstmt.executeQuery();
      if (rs.next())
      {
        result = new StringBuilder(100);
        String engine = rs.getString("engine");
        appendOption(result, "ENGINE", engine);
        table.getSourceOptions().addConfigSetting("engine", engine);

        String comment = rs.getString("table_comment");
        if (StringUtil.isNotBlank(comment))
        {
          comment = comment.replaceAll("'", "''");
          comment = "'" + comment + "'";
          appendOption(result, "COMMENT", comment);
        }
        String collation = rs.getString("table_collation");
        boolean alwaysShowCollation = Settings.getInstance().getBoolProperty("workbench.db.mysql.tablesource.showcollation.always", false);
        if (alwaysShowCollation || !StringUtil.equalStringIgnoreCase(defaultCollation, collation))
        {
          appendOption(result, "COLLATE", collation);
        }
        table.getSourceOptions().addConfigSetting("collation", collation);
      }
    }
    catch (SQLException ex)
    {
      LogMgr.logDebug(new CallerInfo(){}, "Could not read table status", ex);
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }
    if (result != null)
    {
      table.getSourceOptions().setTableOption(result.toString());
    }
    table.getSourceOptions().setInitialized();
  }

  private void appendOption(StringBuilder result, String option, String value)
  {
    if (StringUtil.isNotEmpty(value))
    {
      if (result.length() > 0) result.append('\n');
      result.append(option);
      result.append('=');
      result.append(value);
    }
  }

  private String getDefaultCollation()
  {
    Statement stmt = null;
    ResultSet rs = null;

    String defaultCollation = null;
    try
    {
      stmt = dbConnection.createStatement();
      rs = stmt.executeQuery("show variables like 'collation_database'");
      if (rs.next())
      {
        defaultCollation = rs.getString("value");
      }
    }
    catch (SQLException ex)
    {
      LogMgr.logDebug(new CallerInfo(){}, "Could not read collation", ex);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return defaultCollation;
  }

  @Override
  protected void appendTableComments(StringBuilder result, TableIdentifier table, List<ColumnIdentifier> columns, String lineEnding)
  {
    // nothing to do.
    // the table comment is added in readTableOptions and MySQL does not support column comments
  }
}
