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
package workbench.db.mssql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.DbMetadata;
import workbench.db.DbObject;
import workbench.db.JdbcUtils;
import workbench.db.ObjectListDataStore;
import workbench.db.ObjectListExtender;
import workbench.db.WbConnection;

import workbench.storage.DataStore;

import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * A class to read information about rules from SQL Server.
 *
 * @author Thomas Kellerer
 */
public class SqlServerRuleReader
  implements ObjectListExtender
{

  public List<SqlServerRule> getRuleList(WbConnection connection, String schemaPattern, String namePattern, String ruleTable)
  {
    List<SqlServerRule> result = new ArrayList<>();

    Statement stmt = null;
    ResultSet rs = null;
    String sql = null;
    try
    {
      stmt = connection.createStatementForQuery();
      sql = getSql(connection, schemaPattern, namePattern);
      LogMgr.logMetadataSql(new CallerInfo(){}, "rules", sql, sql, schemaPattern, namePattern);
      rs = stmt.executeQuery(sql);
      while (rs.next())
      {
        String cat = rs.getString(1);
        String schema = rs.getString(2);
        String name = rs.getString(3);
        SqlServerRule rule = new SqlServerRule(cat, schema, name);
        result.add(rule);
      }
    }
    catch (SQLException e)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, e, "rules", sql, schemaPattern, namePattern);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return result;
  }

  @Override
  public SqlServerRule getObjectDefinition(WbConnection connection, DbObject object)
  {
    List<SqlServerRule> rules = getRuleList(connection, object.getSchema(), object.getObjectName(), null);
    if (rules == null || rules.isEmpty()) return null;
    SqlServerRule rule = rules.get(0);

    // getObjectDefinition is called from within the DbExplorer with the selected object
    // we might have already retrieved the comment for the rule here due to the SqlServerObjectListEnhancer.
    // So we just copy the comment of the request object to the retrieved rule in order to avoid a second round-trip to the database.
    if (rule.getComment() == null && object.getComment() != null)
    {
      rule.setComment(object.getComment());
    }
    return rule;
  }

  @Override
  public boolean extendObjectList(WbConnection con, ObjectListDataStore result, String catalog, String schema, String objectNamePattern, String[] requestedTypes)
  {
    if (!DbMetadata.typeIncluded("RULE", requestedTypes)) return false;

    List<SqlServerRule> rules = getRuleList(con, schema, objectNamePattern, null);
    if (rules.isEmpty()) return false;
    for (SqlServerRule rule : rules)
    {
      result.addDbObject(rule);
    }
    return true;
  }

  @Override
  public boolean isDerivedType()
  {
    return false;
  }

  @Override
  public boolean handlesType(String type)
  {
    return StringUtil.equalStringIgnoreCase("RULE", type);
  }

  @Override
  public DataStore getObjectDetails(WbConnection con, DbObject object)
  {
    if (object == null) return null;
    if (!handlesType(object.getObjectType())) return null;

    SqlServerRule rule = getObjectDefinition(con, object);
    if (rule == null) return null;

    String[] columns = new String[] { "RULE", "REMARKS" };
    int[] types = new int[] { Types.VARCHAR, Types.VARCHAR };
    int[] sizes = new int[] { 20, 20 };
    DataStore result = new DataStore(columns, types, sizes);
    result.addRow();
    result.setValue(0, 0, rule.getObjectName());
    result.setValue(0, 1, rule.getComment());
    return result;
  }

  @Override
  public List<String> supportedTypes()
  {
    return CollectionUtil.arrayList("RULE");
  }

  @Override
  public String getObjectSource(WbConnection con, DbObject object)
  {
    Statement stmt = null;
    ResultSet rs = null;
    StringBuilder result = new StringBuilder(50);
    try
    {
      stmt = con.createStatementForQuery();
      rs = stmt.executeQuery("sp_helptext '" + object.getFullyQualifiedName(con) + "'");
      while (rs.next())
      {
        String src = rs.getString(1);
        result.append(src);
      }
    }
    catch (SQLException e)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not retrieve rule source: ", e);
      return null;
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }

    if (result.length() > 0)
    {
      result.append(";\n");
      return result.toString();
    }
    return null;
  }

  private String getSql(WbConnection con, String ruleSchemaPattern, String ruleNamePattern)
  {
    StringBuilder sql = new StringBuilder(150);
    if (SqlServerUtil.isSqlServer2005(con))
    {
      // "Modern" versions of SQL Server
      String baseSql =
        "select db_name() as rule_catalog,  \n" +
        "       sc.name as rule_schema, \n" +
        "       ao.name as rule_name \n" +
        "from sys.all_objects ao with (nolock) \n" +
        "  join sys.schemas sc with (nolock) on ao.schema_id = sc.schema_id \n" +
        "where ao.type = 'R'";

      sql.append(baseSql);
      if (StringUtil.isNotBlank(ruleNamePattern))
      {
        sql.append("\n AND ");
        SqlUtil.appendExpression(sql, "ao.name", ruleNamePattern, con);
      }

      if (StringUtil.isNotBlank(ruleSchemaPattern))
      {
        sql.append("\n AND ");
        SqlUtil.appendExpression(sql, "sc.name", ruleSchemaPattern, con);
      }
      sql.append("\n ORDER BY 1, 2 ");
    }
    else
    {
      // SQL Server 2000
      String query =
          "select db_name() as rule_catalog, \n" +
          "       convert(sysname, user_name(uid)) as rule_schema, \n" +
          "       name as rule_name \n" +
          "from sysobjects with (nolock) \n" +
          "where type = 'R' ";
      sql.append(query);
      if (StringUtil.isNotBlank(ruleNamePattern))
      {
        sql.append("\n AND ");
        SqlUtil.appendExpression(sql, "name", ruleNamePattern, con);
      }

      if (StringUtil.isNotBlank(ruleSchemaPattern))
      {
        sql.append("\n AND ");
        SqlUtil.appendExpression(sql, "convert(sysname, user_name(uid))", ruleSchemaPattern, con);
      }
    }
    return sql.toString();
  }

  @Override
  public List<ColumnIdentifier> getColumns(WbConnection con, DbObject object)
  {
    return null;
  }

  @Override
  public boolean hasColumns()
  {
    return false;
  }
}
