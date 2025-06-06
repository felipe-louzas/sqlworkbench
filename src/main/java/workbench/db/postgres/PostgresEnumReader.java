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
package workbench.db.postgres;

import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Types;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.DbMetadata;
import workbench.db.DbObject;
import workbench.db.EnumIdentifier;
import workbench.db.JdbcUtils;
import workbench.db.ObjectListDataStore;
import workbench.db.ObjectListExtender;
import workbench.db.WbConnection;

import workbench.storage.DataStore;

import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * A class to read the defined ENUM types from Postgres.
 *
 * @author Thomas Kellerer
 */
public class PostgresEnumReader
  implements ObjectListExtender
{
  final String baseSql =
    "select current_database() as enum_catalog, \n" +
    "       n.nspname as enum_schema,  \n" +
    "       t.typname as enum_name,  \n" +
    "       e.enumlabel as enum_value,  \n" +
    "       obj_description(t.oid) as remarks \n" +
    "from pg_catalog.pg_type t \n" +
    "   join pg_catalog.pg_enum e on t.oid = e.enumtypid  \n" +
    "   join pg_catalog.pg_namespace n ON n.oid = t.typnamespace";

  @Override
  public EnumIdentifier getObjectDefinition(WbConnection con, DbObject obj)
  {
    if (obj == null) return null;

    Statement stmt = null;
    ResultSet rs = null;
    Savepoint sp = null;

    String enumName = obj.getObjectName();
    String sql =
    "-- SQL Workbench/J \n" +
      "SELECT * \n" +
      "FROM (\n" + baseSql + "\n) ei\n" +
      "WHERE enum_name = '" + enumName + "' ";

    String schema = obj.getSchema();
    if (StringUtil.isNotBlank(schema))
    {
      sql += "\n  AND enum_schema = '"  + schema + "'";
    }

    EnumIdentifier enumDef = null;

    LogMgr.logMetadataSql(new CallerInfo(){}, "enum values", sql);

    try
    {
      sp = con.setSavepoint();
      stmt = con.createStatementForQuery();
      rs = stmt.executeQuery(sql);

      boolean first = true;
      while (rs.next())
      {
        String cat = rs.getString("enum_catalog");
        String eschema = rs.getString("enum_schema");
        String name = rs.getString("enum_name");
        String value = rs.getString("enum_value");
        String comment = rs.getString("remarks");
        if (first)
        {
          enumDef = new EnumIdentifier(cat, eschema, name);
          enumDef.setComment(comment);
          first = false;
        }
        enumDef.addEnumValue(value);
      }

      con.releaseSavepoint(sp);
    }
    catch (Exception e)
    {
      con.rollback(sp);
      LogMgr.logMetadataError(new CallerInfo(){}, e, "enum values", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return enumDef;
  }

  private String getSql(WbConnection con, String schema, String namePattern)
  {
    StringBuilder sql = new StringBuilder(baseSql.length() + 50);
    sql.append("-- SQL Workbench/J \n");
    sql.append("SELECT * FROM (\n");
    sql.append(baseSql);
    sql.append("\n) ei ");

    boolean whereAdded = false;

    if (StringUtil.isNotBlank(namePattern))
    {
      sql.append("\nWHERE ");
      SqlUtil.appendExpression(sql, "enum_name", namePattern, con);
      whereAdded = true;
    }
    if (StringUtil.isNotBlank(schema))
    {
      sql.append(whereAdded ? "\n  AND " : "\nWHERE ");
      SqlUtil.appendExpression(sql, "enum_schema", schema, con);
    }

    sql.append("\n ORDER BY 2");
    LogMgr.logMetadataSql(new CallerInfo(){}, "enums", sql);
    return sql.toString();
  }

  public Collection<EnumIdentifier> getDefinedEnums(WbConnection con, String schema, String namePattern)
  {
    Map<String, EnumIdentifier> enums = getEnumInfo(con, schema, namePattern);
    return enums.values();
  }

  public Map<String, EnumIdentifier> getEnumInfo(WbConnection con, String schemaName, String namePattern)
  {
    if (!JdbcUtils.hasMinimumServerVersion(con, "8.3")) return Collections.emptyMap();

    String sql = getSql(con, schemaName, namePattern);

    Statement stmt = null;
    ResultSet rs = null;
    Savepoint sp = null;
    Map<String, EnumIdentifier> enums = new HashMap<>();

    try
    {
      sp = con.setSavepoint();
      stmt = con.createStatementForQuery();
      rs = stmt.executeQuery(sql);
      while (rs.next())
      {
        String cat = rs.getString("enum_catalog");
        String schema = rs.getString("enum_schema");
        String name = rs.getString("enum_name");
        String value = rs.getString("enum_value");
        String comment = rs.getString("remarks");
        EnumIdentifier enumDef = enums.get(name);
        if (enumDef == null)
        {
          enumDef = new EnumIdentifier(cat, schema, name);
          enumDef.setComment(comment);
          enums.put(name, enumDef);
        }
        enumDef.addEnumValue(value);
      }
      con.releaseSavepoint(sp);
    }
    catch (Exception e)
    {
      con.rollback(sp);
      LogMgr.logMetadataError(new CallerInfo(){}, e, "enum values", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return enums;
  }

  @Override
  public boolean extendObjectList(WbConnection con, ObjectListDataStore result,
                                  String catalog, String schema, String objects, String[] requestedTypes)
  {
    if (!handlesType(requestedTypes)) return false;
    if (!DbMetadata.typeIncluded("ENUM", requestedTypes)) return false;

    Collection<EnumIdentifier> enums = getDefinedEnums(con, schema, objects);
    if (CollectionUtil.isEmpty(enums)) return false;
    result.addObjects(enums);
    return true;
  }

  @Override
  public List<String> supportedTypes()
  {
    return Collections.singletonList("ENUM");
  }

  @Override
  public boolean handlesType(String type)
  {
    return StringUtil.equalStringIgnoreCase("ENUM", type);
  }

  @Override
  public boolean isDerivedType()
  {
    return false;
  }

  @Override
  public DataStore getObjectDetails(WbConnection con, DbObject object)
  {
    EnumIdentifier id = getObjectDefinition(con, object);
    if (id == null) return null;

    String[] columns = new String[] { "ENUM", "VALUES", "REMARKS" };
    int[] types = new int[] { Types.VARCHAR, Types.VARCHAR, Types.VARCHAR };
    int[] sizes = new int[] { 20, 30, 30 };
    DataStore result = new DataStore(columns, types, sizes);
    result.addRow();
    result.setValue(0, 0, id.getObjectName());
    result.setValue(0, 1, StringUtil.listToString(id.getValues(), ','));
    result.setValue(0, 2, id.getComment());
    return result;
  }

  @Override
  public String getObjectSource(WbConnection con, DbObject object)
  {
    if (object == null) return null;
    EnumIdentifier id = getObjectDefinition(con, object);
    if (id == null) return null;

    StringBuilder result = new StringBuilder(50);
    result.append("CREATE TYPE ");
    result.append(id.getObjectName());
    result.append(" AS ENUM (");
    String values = StringUtil.listToString(id.getValues(), ",", true, '\'');
    result.append(values);
    result.append(");\n");
    if (StringUtil.isNotBlank(id.getComment()))
    {
      result.append("\nCOMMENT ON TYPE ");
      result.append(id.getObjectName());
      result.append(" IS '");
      result.append(SqlUtil.escapeQuotes(id.getComment()));
      result.append("';\n");
    }
    return result.toString();
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
