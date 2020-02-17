/*
 * SqlServerTableSourceBuilder.java
 *
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2020, Thomas Kellerer
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
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.DependencyNode;
import workbench.db.IndexDefinition;
import workbench.db.ObjectSourceOptions;
import workbench.db.PkDefinition;
import workbench.db.TableIdentifier;
import workbench.db.TableSourceBuilder;
import workbench.db.WbConnection;
import workbench.db.sqltemplates.TemplateHandler;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class SqlServerTableSourceBuilder
  extends TableSourceBuilder
{
  public static final String CLUSTERED_PLACEHOLDER = "%clustered_attribute%";

  public SqlServerTableSourceBuilder(WbConnection con)
  {
    super(con);
  }

  @Override
  public CharSequence getPkSource(TableIdentifier table, PkDefinition pk, boolean forInlineUse, boolean useFQN)
  {
    CharSequence pkSource = super.getPkSource(table, pk, forInlineUse, useFQN);
    if (StringUtil.isEmptyString(pkSource))
    {
      return pkSource;
    }
    String sql = pkSource.toString();

    String type = pk.getPkIndexDefinition().getIndexType();
    String clustered = "CLUSTERED";
    if ("NORMAL".equals(type))
    {
      clustered = "NONCLUSTERED";
    }

    if (StringUtil.isBlank(clustered))
    {
      sql = TemplateHandler.removePlaceholder(sql, CLUSTERED_PLACEHOLDER, true);
    }
    else
    {
      sql = TemplateHandler.replacePlaceholder(sql, CLUSTERED_PLACEHOLDER, clustered, true);
    }
    return sql;
  }

  @Override
  protected String getAdditionalFkSql(TableIdentifier table, DependencyNode fk, String template)
  {
    if (!fk.isValidated())
    {
      template = template.replace("%nocheck%", "WITH NOCHECK ");
    }
    else
    {
      template = template.replace("%nocheck%", "");
    }

    if (!fk.isEnabled())
    {
      template += "\nALTER TABLE " + table.getObjectExpression(dbConnection) + " NOCHECK CONSTRAINT " + dbConnection.getMetadata().quoteObjectname(fk.getFkName());
    }
    return template;
  }

  @Override
  public String getAdditionalTableInfo(TableIdentifier table, List<ColumnIdentifier> columns, List<IndexDefinition> indexList)
  {
    if (SqlServerUtil.isSqlServer2012(dbConnection))
    {
      String stats = readExtendeStats(table);
      return "\n" + stats;
    }
    return null;
  }

  private String readExtendeStats(TableIdentifier table)
  {
    if (table == null) return null;

    String sql =
      "select st.name as stats_name, c.name as column_name, st.has_filter, st.filter_definition\n" +
      "from sys.stats st\n" +
      "  join sys.stats_columns sc on sc.stats_id = st.stats_id and sc.object_id = st.object_id\n" +
      "  join sys.columns c on c.column_id = sc.column_id and c.object_id = st.object_id\n" +
      "where st.object_id = object_id(?)\n" +
      "order by st.stats_id, sc.stats_column_id";

    ResultSet rs = null;
    PreparedStatement pstmt = null;

    StringBuilder result = new StringBuilder();

    String tableName = table.getTableExpression(dbConnection);
    final CallerInfo ci = new CallerInfo(){};
    LogMgr.logMetadataSql(ci, "column statistics", sql, tableName);

    ObjectSourceOptions option = table.getSourceOptions();

    String lastStat = null;
    String currentFilter = null;
    try
    {
      pstmt = dbConnection.getSqlConnection().prepareStatement(sql);
      pstmt.setString(1, table.getFullyQualifiedName(dbConnection));
      rs = pstmt.executeQuery();
      while (rs.next())
      {
        String statName = rs.getString(1);
        String colName = rs.getString(2);
        String filter = rs.getString(4);
        if (lastStat == null || !lastStat.equals(statName))
        {
          if (result.length() > 0)
          {
            result.append(")");
            if (currentFilter != null)
            {
              result.append("\nWHERE ");
              result.append(currentFilter);
            }
            result.append(";\n\n");
          }
          result.append("CREATE STATISTICS ");
          result.append(dbConnection.getMetadata().quoteObjectname(statName));
          result.append("\n  ON ");
          result.append(tableName);
          result.append("(");
          result.append(colName);
          currentFilter = filter;
          lastStat = statName;
        }
        else
        {
          result.append(",");
          result.append(colName);
        }
      }
      result.append(")");
      if (currentFilter != null)
      {
        result.append("\nWHERE ");
        result.append(currentFilter);
      }
      result.append(";");
    }
    catch (Exception e)
    {
      LogMgr.logMetadataError(ci, e, "column statistics", sql, tableName);
      result = null;
    }
    finally
    {
      SqlUtil.closeAll(rs, pstmt);
    }
    if (result.length() > 0)
    {
      option.addConfigSetting("column_statistics", result.toString());
    }

    if (result.length() == 0) return null;
    return result.toString();
  }
}
