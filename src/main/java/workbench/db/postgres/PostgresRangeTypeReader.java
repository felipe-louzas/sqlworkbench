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
import java.util.ArrayList;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.ColumnIdentifier;
import workbench.db.CommentSqlManager;
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
 * An ObjectlistExtender to read Postgres' range types
 *
 * @author Thomas Kellerer
 */
public class PostgresRangeTypeReader
  implements ObjectListExtender
{

  public static boolean retrieveRangeTypes()
  {
    return Settings.getInstance().getBoolProperty("workbench.db.postgresql.rangetypes.retrieve", true);
  }

  @Override
  public boolean extendObjectList(WbConnection con, ObjectListDataStore result,
                                  String catalog, String schemaPattern, String objectPattern, String[] requestedTypes)
  {
    boolean doRetrieve = DbMetadata.typeIncluded(PgRangeType.RANGE_TYPE_NAME, requestedTypes)
      || DbMetadata.typeIncluded("TYPE", requestedTypes);

    if (!doRetrieve) return false;

    List<PgRangeType> ranges = getRangeTypes(con, schemaPattern, objectPattern);
    result.addObjects(ranges);
    return true;
  }

  public List<PgRangeType> getRangeTypes(WbConnection con, String schemaPattern, String objectPattern)
  {
    List<PgRangeType> result = new ArrayList<>();

    StringBuilder select = new StringBuilder(100);

    String mrangeType;
    if (JdbcUtils.hasMinimumServerVersion(con, "14"))
    {
      mrangeType = "       pg_catalog.format_type(rg.rngmultitypid, NULL) as multi_range_type \n";
    }
    else
    {
      mrangeType = "       null::text as multi_range_type";
    }

    String baseSelect =
      "-- SQL Workbench/J \n" +
      "select t.typname as type_name,  \n" +
      "       n.nspname as type_schema, \n" +
      "       pg_catalog.obj_description(t.oid, 'pg_type') as remarks, \n" +
      "       pg_catalog.format_type(rg.rngsubtype, NULL) as data_type, \n" +
      mrangeType +
      "FROM pg_catalog.pg_type t \n" +
      "   join pg_catalog.pg_namespace n on t.typnamespace = n.oid \n" +
      "   join pg_catalog.pg_range rg on t.oid = rg.rngtypid \n" +
      "WHERE n.nspname NOT IN ('pg_catalog', 'information_schema') \n" +
      "  AND t.typtype IN ('r') \n";

    select.append(baseSelect);
    SqlUtil.appendAndCondition(select, "n.nspname", schemaPattern, con);
    SqlUtil.appendAndCondition(select, "t.typname", objectPattern, con);

    select.append("\n ORDER BY 1,2 ");

    LogMgr.logMetadataSql(new CallerInfo(){}, "range types", select);

    Statement stmt = null;
    ResultSet rs = null;
    Savepoint sp = null;
    try
    {
      sp = con.setSavepoint();
      stmt = con.createStatementForQuery();
      rs = stmt.executeQuery(select.toString());
      while (rs.next())
      {
        String schema = rs.getString("type_schema");
        String name = rs.getString("type_name");
        String remarks = rs.getString("remarks");
        String dataType = rs.getString("data_type");
        String multiRangeType = rs.getString("multi_range_type");
        PgRangeType pgtype = new PgRangeType(schema, name);
        pgtype.setDataType(dataType);
        pgtype.setComment(remarks);
        pgtype.setMultirangeType(multiRangeType);
        result.add(pgtype);
      }
      con.releaseSavepoint(sp);
    }
    catch (Exception e)
    {
      con.rollback(sp);
      LogMgr.logMetadataError(new CallerInfo(){}, e, "range types", select);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return result;
  }


  @Override
  public boolean isDerivedType()
  {
    return true;
  }

  @Override
  public List<String> supportedTypes()
  {
    return CollectionUtil.arrayList(PgRangeType.RANGE_TYPE_NAME);
  }

  @Override
  public boolean handlesType(String type)
  {
    return PgRangeType.RANGE_TYPE_NAME.equalsIgnoreCase(type);
  }

  @Override
  public DataStore getObjectDetails(WbConnection con, DbObject object)
  {
    String[] cols = { "TYPE_NAME", "DATA_TYPE", "REMARKS" };
    int[] colTypes = { Types.VARCHAR, Types.VARCHAR, Types.VARCHAR };

    DataStore result = new DataStore(cols, colTypes );

    PgRangeType range = null;

    if (object instanceof PgRangeType)
    {
      range = (PgRangeType)object;
    }
    else
    {
      List<PgRangeType> types = getRangeTypes(con, object.getSchema(), object.getObjectName());
      if (types.size() == 1)
      {
        range = types.get(0);
      }
    }
    if (range != null)
    {
      int row = result.addRow();
      result.setValue(row, 0, range.getObjectName());
      result.setValue(row, 1, range.getDataType());
      result.setValue(row, 2, range.getComment());
    }
    return result;
  }

  @Override
  public PgRangeType getObjectDefinition(WbConnection con, DbObject name)
  {
    if (name == null) return null;

    if (name instanceof PgRangeType)
    {
      return (PgRangeType)name;
    }

    List<PgRangeType> types = getRangeTypes(con, name.getSchema(), name.getObjectName());
    if (types.size() == 1)
    {
      return types.get(0);
    }
    return null;
  }

  @Override
  public String getObjectSource(WbConnection con, DbObject object)
  {
    PgRangeType type = null;
    if (object instanceof PgRangeType)
    {
      type = (PgRangeType)object;
    }
    else
    {
      type = getObjectDefinition(con, object);
    }

    if (type == null) return null;
    StringBuilder sql = new StringBuilder(250);
    sql.append("CREATE TYPE ");
    sql.append(type.getObjectName());
    sql.append(" AS RANGE\n");
    sql.append("(\n");
    sql.append("  SUBTYPE = ");
    sql.append(type.getDataType());
    if (type.getMultirangeType() != null)
    {
      sql.append(",\n  MULTIRANGE_TYPE_NAME = ");
      sql.append(type.getMultirangeType());
    }
    sql.append("\n);");

    String comment = type.getComment();
    CommentSqlManager mgr = new CommentSqlManager(con.getDbSettings().getDbId());
    String template = mgr.getCommentSqlTemplate("type", null);
    if (StringUtil.isNotBlank(comment) && template != null)
    {
      template = template.replace(CommentSqlManager.COMMENT_OBJECT_NAME_PLACEHOLDER, type.getObjectExpression(con));
      template = template.replace(CommentSqlManager.COMMENT_PLACEHOLDER, comment);
      sql.append("\n\n");
      sql.append(template);
      sql.append(";\n");
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
