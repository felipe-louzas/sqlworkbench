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
package workbench.db.h2database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.ColumnIdentifier;
import workbench.db.DomainIdentifier;
import workbench.db.DropType;
import workbench.db.IndexDefinition;
import workbench.db.JdbcUtils;
import workbench.db.ObjectExpressionParser;
import workbench.db.TableIdentifier;
import workbench.db.TableSourceBuilder;
import workbench.db.WbConnection;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class H2TableSourceBuilder
  extends TableSourceBuilder
{
  public H2TableSourceBuilder(WbConnection con)
  {
    super(con);
  }

  @Override
  public String getTableSource(TableIdentifier table, DropType drop, boolean includeFk, boolean includeGrants)
    throws SQLException
  {
    if ("TABLE LINK".equals(table.getType()))
    {
      String sql = getLinkedTableSource(table, drop != DropType.none);
      if (sql != null) return sql;
    }
    return super.getTableSource(table, drop, includeFk, includeGrants);
  }

  @Override
  protected String getAdditionalTableInfo(TableIdentifier table, List<ColumnIdentifier> columns, List<IndexDefinition> indexList)
  {
    StringBuilder domainInfo = getDomainInformation(columns, table.getRawSchema());
    return domainInfo.length() > 0 ? domainInfo.toString() : null;
  }

  private String getLinkedTableSource(TableIdentifier table, boolean includeDrop)
    throws SQLException
  {
    PreparedStatement stmt = null;
    ResultSet rs = null;

    String sql =
      "SELECT sql FROM information_schema.tables " +
      " WHERE table_schema = ? " +
      "   AND table_name = ? " +
      "   AND table_type = 'TABLE LINK'";

    StringBuilder createSql = new StringBuilder(100);

    LogMgr.logMetadataSql(new CallerInfo(){}, "linked table source", sql);

    if (includeDrop)
    {
      createSql.append("DROP TABLE ");
      createSql.append(table.getTableExpression(dbConnection));
      createSql.append(";\n\n");
    }
    try
    {
      stmt = dbConnection.getSqlConnection().prepareStatement(sql);
      stmt.setString(1, table.getSchema());
      stmt.setString(2, table.getTableName());
      rs = stmt.executeQuery();
      if (rs.next())
      {
        String create = rs.getString(1);
        if (StringUtil.isNotEmpty(create))
        {
          create = create.replace("/*--hide--*/", "");
        }
        createSql.append(create.trim());
        createSql.append(";\n");
      }
    }
    catch (SQLException ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "linked table source", sql);
      return null;
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return createSql.toString();
  }

  @Override
  public void readTableOptions(TableIdentifier tbl, List<ColumnIdentifier> columns)
  {
    if (tbl.getSourceOptions().isInitialized()) return;

    PreparedStatement pstmt = null;
    ResultSet rs = null;
    String sql =
      "select storage_type, \n" +
      "       (select value from information_schema.settings where name = 'DEFAULT_TABLE_TYPE') as default_type \n" +
      "from information_schema.tables \n" +
      "where table_name = ? \n" +
      "and table_schema = ?";

    boolean alwaysShowType = Settings.getInstance().getBoolProperty("workbench.db.h2.table_type.show_always", false);

    try
    {
      pstmt = this.dbConnection.getSqlConnection().prepareStatement(sql);
      pstmt.setString(1, tbl.getTableName());
      pstmt.setString(2, tbl.getSchema());
      LogMgr.logMetadataSql(new CallerInfo(){}, "table options", sql, tbl.getTableName(), tbl.getSchema());
      rs = pstmt.executeQuery();
      if (rs.next())
      {
        String type = rs.getString(1);
        String defaultType = rs.getString(2);
        if ("0".equals(defaultType))
        {
          defaultType = "CACHED";
        }
        else
        {
          defaultType = "MEMORY";
        }

        if (alwaysShowType || !defaultType.equals(type))
        {
          tbl.getSourceOptions().setTypeModifier(type);
        }
      }
    }
    catch (SQLException e)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, e, "table options", sql, tbl.getTableName(), tbl.getSchema());
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }
    tbl.getSourceOptions().setInitialized();
  }

  private StringBuilder getDomainInformation(List<ColumnIdentifier> columns, String tableSchema)
  {
    H2DomainReader reader = new H2DomainReader();
    StringBuilder result = new StringBuilder();

    for (ColumnIdentifier col : columns)
    {
      if (col.isDomain())
      {
        ObjectExpressionParser obj = new ObjectExpressionParser(col.getDbmsType());

        String domainSchema = StringUtil.coalesce(SqlUtil.removeObjectQuotes(obj.getSchema()), tableSchema);
        String domainName = SqlUtil.removeObjectQuotes(obj.getName());

        DomainIdentifier domain = reader.getDomain(dbConnection, domainSchema, domainName);
        if (domain != null)
        {
          result.append("\n-- domain ");
          result.append(col.getDbmsType());
          result.append(": ");
          result.append(domain.getSummary());
        }
      }
    }
    return result;
  }

}
