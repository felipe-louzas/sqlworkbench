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
package workbench.db.duckdb;

import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.DbMetadata;
import workbench.db.DbObject;
import workbench.db.JdbcUtils;
import workbench.db.ObjectListDataStore;
import workbench.db.ObjectListExtender;
import workbench.db.ProcedureDefinition;
import workbench.db.RoutineType;
import workbench.db.WbConnection;

import workbench.storage.DataStore;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 *
 * @author thomas
 */
public class DuckDbMacroReader
  implements ObjectListExtender
{
  private final String retrieveMacrosSQL =
    "-- SQL Workbench/J \n" +
    "select database_name, schema_name, function_name, parameters, function_type, comment, macro_definition \n" +
    "from duckdb_functions() \n" +
    "where function_type in ('macro', 'table_macro') \n" +
    "  and not internal";

  public static final String MACRO_TYPE_NAME = "macro";

  @Override
  public List<String> supportedTypes()
  {
    return List.of(MACRO_TYPE_NAME.toUpperCase());
  }

  @Override
  public boolean isDerivedType()
  {
    return false;
  }

  @Override
  public boolean handlesType(String type)
  {
    return MACRO_TYPE_NAME.equalsIgnoreCase(type);
  }

  @Override
  public DataStore getObjectDetails(WbConnection con, DbObject object)
  {
    if (object instanceof ProcedureDefinition)
    {
      ProcedureDefinition proc = (ProcedureDefinition)object;
      DataStore ds = new DataStore(new String[] {"PARAMETER"}, new int[] {Types.VARCHAR}, new int[]{30});
      for (String parameter : proc.getParameterNames())
      {
        int row = ds.addRow();
        ds.setValue(row, 0, parameter);
      }
      return ds;
    }
    return null;
  }

  @Override
  public ProcedureDefinition getObjectDefinition(WbConnection con, DbObject name)
  {
    List<ProcedureDefinition> macros = getMacros(con, name.getCatalog(), name.getSchema(), name.getObjectName());
    if (macros.size() > 0)
    {
      return macros.get(0);
    }
    return null;
  }

  @Override
  public String getObjectSource(WbConnection con, DbObject object)
  {
    if (object instanceof ProcedureDefinition)
    {
      CharSequence src = ((ProcedureDefinition)object).getSource();
      if (src == null)
      {
        ProcedureDefinition proc = getObjectDefinition(con, object);
        src = proc.getSource();
      }
      return src == null ? null : src.toString();
    }
    return null;
  }

  @Override
  public List<ColumnIdentifier> getColumns(WbConnection con, DbObject object)
  {
    if (object instanceof ProcedureDefinition)
    {
      return ((ProcedureDefinition)object).getParameters(null);
    }
    return null;
  }

  @Override
  public boolean hasColumns()
  {
    return true;
  }

  @Override
  public boolean extendObjectList(WbConnection con, ObjectListDataStore result, String aCatalog, String aSchema, String objects, String[] requestedTypes)
  {
    if (!DbMetadata.typeIncluded(MACRO_TYPE_NAME, requestedTypes)) return false;
    List<ProcedureDefinition> macros = getMacros(con, aCatalog, aSchema, objects);
    result.addObjects(macros);
    return macros.size() > 0;
  }

  public List<ProcedureDefinition> getMacros(WbConnection con, String catalogPattern, String schemaPattern, String objectPattern)
  {
    List<ProcedureDefinition> result = new ArrayList<>();

    StringBuilder query = new StringBuilder(retrieveMacrosSQL);

    if (StringUtil.isNotBlank(catalogPattern))
    {
      SqlUtil.appendAndCondition(query, "database_name", catalogPattern, con);
    }
    else
    {
      query.append("\n  and database_name = current_catalog()");
    }
    query.append(" \n");

    if (StringUtil.isNotBlank(objectPattern))
    {
      SqlUtil.appendAndCondition(query, "function_name", objectPattern, con);
      query.append("\n");
    }

    if (StringUtil.isNotBlank(schemaPattern))
    {
      SqlUtil.appendAndCondition(query, "schema_name", schemaPattern, con);
    }

    query.append("\nORDER BY database_name, schema_name, function_name");
    LogMgr.logMetadataSql(new CallerInfo(){}, "macros", query);

    Statement stmt = null;
    ResultSet rs = null;
    try
    {
      stmt = con.createStatementForQuery();
      rs = stmt.executeQuery(query.toString());
      while (rs.next())
      {
        String cat = rs.getString("database_name");
        String schema = rs.getString("schema_name");
        String name = rs.getString("function_name");
        String remarks = rs.getString("comment");
        String type = rs.getString("function_type");
        String def = rs.getString("macro_definition");
        Object[] parameters = JdbcUtils.getArray(rs, "parameters", Object[].class);

        List<ColumnIdentifier> plist = null;
        if (parameters != null)
        {
          plist = Arrays.stream(parameters).
            filter(p -> p !=null).
            map(p -> new ColumnIdentifier(p.toString())).
            collect(Collectors.toList());
        }

        RoutineType resultType;
        if ("table_macro".equals(type))
        {
          resultType = RoutineType.tableFunction;
        }
        else
        {
          resultType = RoutineType.function;
        }

        ProcedureDefinition proc = new ProcedureDefinition(cat, schema, name, resultType);
        proc.setComment(remarks);
        proc.setParameters(plist);
        proc.setSource(buildSource(con, proc, def));
        result.add(proc);
      }
    }
    catch (Exception ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "macros", query);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return result;
  }

  private CharSequence buildSource(WbConnection conn, ProcedureDefinition proc, String macroDef)
  {
    String nameExpr = proc.getObjectExpression(conn);
    StringBuilder source = new StringBuilder();
    RoutineType resultType = proc.getRoutineType();
    source.append("CREATE MACRO ");
    source.append(nameExpr);
    source.append("(");
    source.append(proc.getParameterNames().stream().collect(Collectors.joining(", ")));
    source.append(") AS ");
    if (resultType == RoutineType.tableFunction)
    {
      source.append("TABLE \n");
    }
    source.append(macroDef);
    source.append(";");

    if (StringUtil.isNotBlank(proc.getComment()))
    {
      source.append("\n\nCOMMENT ON MACRO ");
      if (resultType == RoutineType.tableFunction)
      {
        source.append("TABLE ");
      }
      source.append(nameExpr);
      source.append(" IS '");
      source.append(SqlUtil.escapeQuotes(proc.getComment()));
      source.append("';\n");
    }
    return source;
  }
}
