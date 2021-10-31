/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2021, Thomas Kellerer
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
package workbench.db.postgres;

import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.DbMetadata;
import workbench.db.DbObject;
import workbench.db.DomainIdentifier;
import workbench.db.JdbcUtils;
import workbench.db.ObjectListDataStore;
import workbench.db.ObjectListExtender;
import workbench.db.WbConnection;

import workbench.storage.DataStore;

import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * A class to read information about defined DOMAINs in Postgres.
 *
 * @author Thomas Kellerer
 */
public class PostgresDomainReader
  implements ObjectListExtender
{
  final String baseSql =
    "SELECT null::text as domain_catalog,  \n" +
    "       n.nspname as domain_schema, \n" +
    "       t.typname as domain_name, \n" +
    "       pg_catalog.format_type(t.typbasetype, t.typtypmod) as data_type, \n" +
    "       not t.typnotnull as nullable, \n" +
    "       t.typdefault as default_value, \n" +
    "       c.conname as constraint_name, \n" +
    "       pg_catalog.pg_get_constraintdef(c.oid, true) as constraint_definition, \n" +
    "       pg_catalog.obj_description(t.oid) as remarks \n" +
    "FROM pg_catalog.pg_type t \n" +
    "  LEFT JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace \n" +
    "  LEFT JOIN pg_catalog.pg_constraint c ON t.oid = c.contypid \n" +
    "WHERE t.typtype = 'd' \n" +
    "  AND n.nspname <> 'pg_catalog' \n" +
    "  AND n.nspname <> 'information_schema' \n";


  public Map<String, DomainIdentifier> getDomainInfo(WbConnection connection, String schema)
  {
    List<DomainIdentifier> domains = getDomainList(connection, schema, null);
    Map<String, DomainIdentifier> result = new HashMap<>(domains.size());
    for (DomainIdentifier d : domains)
    {
      result.put(d.getObjectName(), d);
    }
    return result;
  }

  private String getSql(WbConnection con, String schema, String name)
  {
    StringBuilder sql = new StringBuilder(baseSql.length() + 40);

    sql.append("SELECT * FROM ( ");
    sql.append(baseSql);
    sql.append(") di \n");

    boolean whereAdded = false;
    if (StringUtil.isNonBlank(name))
    {
      sql.append("\n WHERE ");
      SqlUtil.appendExpression(sql, "domain_name", name, con);
      whereAdded = true;
    }

    if (StringUtil.isNonBlank(schema))
    {
      sql.append(whereAdded ? " AND " : " WHERE ");
      SqlUtil.appendExpression(sql, "domain_schema", schema, con);
    }

    sql.append("\n ORDER BY 1, 2 ");

    LogMgr.logMetadataSql(new CallerInfo(){}, "domains", sql);

    return sql.toString();
  }

  public List<DomainIdentifier> getDomainList(WbConnection connection, String schemaPattern, String namePattern)
  {
    Statement stmt = null;
    ResultSet rs = null;
    Savepoint sp = null;
    String sql = null;
    List<DomainIdentifier> result = new ArrayList<>();
    try
    {
      sp = connection.setSavepoint();
      stmt = connection.createStatementForQuery();
      sql = getSql(connection, schemaPattern, namePattern);
      rs = stmt.executeQuery(sql);
      while (rs.next())
      {
        String cat = rs.getString("domain_catalog");
        String schema = rs.getString("domain_schema");
        String name = rs.getString("domain_name");
        String constraintName = rs.getString("constraint_name");
        String constraintDef = rs.getString("constraint_definition");
        DomainIdentifier domain = new DomainIdentifier(cat, schema, name);
        if (StringUtil.isBlank(constraintName))
        {
          domain.setCheckConstraint(constraintDef);
        }
        else
        {
          domain.setCheckConstraint("CONSTRAINT " + constraintName + " " + constraintDef);
        }
        domain.setDataType(rs.getString("data_type"));
        domain.setNullable(rs.getBoolean("nullable"));
        domain.setDefaultValue(rs.getString("default_value"));
        domain.setComment(rs.getString("remarks"));
        result.add(domain);
      }
      connection.releaseSavepoint(sp);
    }
    catch (Exception e)
    {
      connection.rollback(sp);
      LogMgr.logMetadataError(new CallerInfo(){}, e, "domains", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return result;
  }

  @Override
  public DomainIdentifier getObjectDefinition(WbConnection connection, DbObject object)
  {
    List<DomainIdentifier> domains = getDomainList(connection, object.getSchema(), object.getObjectName());
    if (CollectionUtil.isEmpty(domains)) return null;
    return domains.get(0);
  }

  public String getDomainSource(DomainIdentifier domain)
  {
    if (domain == null) return null;
    String name = SqlUtil.buildExpression(null, domain);
    StringBuilder result = new StringBuilder(50);
    result.append("CREATE DOMAIN ");
    result.append(name);
    result.append(" AS ");
    result.append(domain.getDataType());
    if (domain.getDefaultValue() != null)
    {
      result.append("\n   DEFAULT ");
      result.append(domain.getDefaultValue());
    }
    if (!domain.isNullable()) result.append("\n  NOT NULL");
    if (StringUtil.isNonBlank(domain.getCheckConstraint()))
    {
      result.append("\n  ");
      result.append(domain.getCheckConstraint());
    }
    result.append(";\n");
    if (StringUtil.isNonBlank(domain.getComment()))
    {
      result.append("\nCOMMENT ON DOMAIN ").append(name).append(" IS '");
      result.append(SqlUtil.escapeQuotes(domain.getComment()));
      result.append("';\n");
    }
    return result.toString();
  }

  @Override
  public boolean extendObjectList(WbConnection con, ObjectListDataStore result,
                                   String catalog, String schema, String objects, String[] requestedTypes)
  {
    if (!handlesType(requestedTypes)) return false;
    if (!DbMetadata.typeIncluded("DOMAIN", requestedTypes)) return false;

    List<DomainIdentifier> domains = getDomainList(con, schema, objects);
    if (domains.isEmpty()) return false;

    for (DomainIdentifier domain : domains)
    {
      result.addDbObject(domain);
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
    return StringUtil.equalStringIgnoreCase("DOMAIN", type);
  }

  @Override
  public boolean handlesType(String[] types)
  {
    if (types == null) return true;
    for (String type : types)
    {
      if (handlesType(type)) return true;
    }
    return false;
  }

  @Override
  public DataStore getObjectDetails(WbConnection con, DbObject object)
  {
    if (object == null) return null;
    if (!handlesType(object.getObjectType())) return null;

    DomainIdentifier domain = getObjectDefinition(con, object);
    if (domain == null) return null;

    String[] columns = new String[] { "DOMAIN", "DATA_TYPE", "NULLABLE", "CONSTRAINT", "REMARKS" };
    int[] types = new int[] { Types.VARCHAR, Types.VARCHAR, Types.BOOLEAN, Types.VARCHAR, Types.VARCHAR };
    int[] sizes = new int[] { 20, 10, 5, 30, 30 };
    DataStore result = new DataStore(columns, types, sizes);
    result.addRow();
    result.setValue(0, 0, domain.getObjectName());
    result.setValue(0, 1, domain.getDataType());
    result.setValue(0, 2, domain.isNullable());
    result.setValue(0, 3, domain.getCheckConstraint());
    result.setValue(0, 4, domain.getComment());
    result.resetStatus();
    return result;
  }

  @Override
  public List<String> supportedTypes()
  {
    return Collections.singletonList("DOMAIN");
  }

  @Override
  public String getObjectSource(WbConnection con, DbObject object)
  {
    return getDomainSource(getObjectDefinition(con, object));
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
