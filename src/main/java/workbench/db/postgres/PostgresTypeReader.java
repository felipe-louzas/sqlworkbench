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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.BaseObjectType;
import workbench.db.ColumnIdentifier;
import workbench.db.CommentSqlManager;
import workbench.db.DataTypeResolver;
import workbench.db.DbMetadata;
import workbench.db.DbObject;
import workbench.db.JdbcUtils;
import workbench.db.ObjectListDataStore;
import workbench.db.ObjectListEnhancer;
import workbench.db.ObjectListExtender;
import workbench.db.ObjectListLookup;
import workbench.db.TableColumnsDatastore;
import workbench.db.TableDefinition;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;
import workbench.db.sqltemplates.ColumnChanger;

import workbench.storage.DataStore;

import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * A class to read information about "structure" types in Postgres.
 *
 * This class will read information about types, created using e.g.:
 *
 * <tt>CREATE TYPE foo AS (id integer, some_data text);</tt>
 *
 * @author Thomas Kellerer
 */
public class PostgresTypeReader
  implements ObjectListExtender, ObjectListEnhancer
{
  private final PostgresRangeTypeReader rangeReader = new PostgresRangeTypeReader();

  @Override
  public void updateObjectList(WbConnection con, ObjectListDataStore result, String aCatalog, String aSchema, String objects, String[] requestedTypes)
  {
    String replacement = null;

    if (JdbcUtils.hasMinimumServerVersion(con, "9.3"))
    {
      // this assumes that the 9.3 server is used together with a 9.x driver!
      replacement = DbMetadata.MVIEW_NAME;
    }

    if (DbMetadata.typeIncluded(replacement, requestedTypes))
    {
      int count = result.getRowCount();
      for (int row=0; row < count; row++)
      {
        String type = result.getType(row);
        if (type == null)
        {
          result.setType(row, replacement);
        }
      }
    }
  }

  @Override
  public boolean extendObjectList(WbConnection con, ObjectListDataStore result, String catalog, String schemaPattern, String objectPattern, String[] requestedTypes)
  {
    boolean retrieveTypes = DbMetadata.typeIncluded("TYPE", requestedTypes);
    boolean retrieveRangeTypes = JdbcUtils.hasMinimumServerVersion(con, "9.2") && PostgresRangeTypeReader.retrieveRangeTypes();

    if (JdbcUtils.hasMiniumDriverVersion(con, "9.0") || requestedTypes == null)
    {
      // nothing to do, starting with driver version 9.0 the driver will correctly return the TYPE entries
      retrieveTypes = false;
    }

    if (retrieveTypes)
    {
      List<BaseObjectType> types = getTypes(con, schemaPattern, objectPattern);

      ObjectListLookup finder = new ObjectListLookup(result);
      finder.setCaseSensitive(false);

      for (BaseObjectType type : types)
      {
        // Just in case the JDBC driver did return TYPE objects
        String tschema = SqlUtil.removeObjectQuotes(type.getSchema());
        String tname = SqlUtil.removeObjectQuotes(type.getObjectName());
        int existing = finder.findObject(tschema, tname);
        if (existing > -1)
        {
          // remove the "generic" entry that was created while retrieving the "tables"
          result.deleteRow(existing);
        }
        result.addDbObject(type);
      }
    }

    if (retrieveRangeTypes)
    {
      List<PgRangeType> rangeTypes = rangeReader.getRangeTypes(con, schemaPattern, objectPattern);
      result.addObjects(rangeTypes);
    }

    return true;
  }

  public List<BaseObjectType> getTypes(WbConnection con, String schemaPattern, String objectPattern)
  {
    List<BaseObjectType> result = new ArrayList<>();

    StringBuilder select = new StringBuilder(100);

    String baseSelect =
      "-- SQL Workbench/J \n" +
      "SELECT null as table_cat, \n" +
       "        n.nspname as table_schem, \n" +
       "        t.typname as table_name, \n" +
       "        'TYPE' as table_type, \n" +
       "        pg_catalog.obj_description(t.oid, 'pg_type') as remarks \n" +
       "FROM pg_catalog.pg_type t \n" +
       "  JOIN pg_catalog.pg_namespace n ON n.oid = t.typnamespace \n" +
       "WHERE (t.typrelid = 0 OR (SELECT c.relkind = 'c' FROM pg_catalog.pg_class c WHERE c.oid = t.typrelid)) \n" +
       " AND NOT EXISTS (SELECT 1 FROM pg_catalog.pg_type el WHERE el.oid = t.typelem AND el.typarray = t.oid) \n" +
       " AND t.typtype NOT IN ('e', 'd', 'r', 'm') \n" +
       " AND n.nspname <> 'pg_catalog' \n" +
       " AND n.nspname <> 'information_schema' \n";

    if (!JdbcUtils.hasMinimumServerVersion(con, "8.3"))
    {
      baseSelect = baseSelect.replace("AND el.typarray = t.oid", "");
    }

    select.append(baseSelect);
    SqlUtil.appendAndCondition(select, "n.nspname", schemaPattern, con);
    SqlUtil.appendAndCondition(select, "t.typname", objectPattern, con);

    select.append("\n ORDER BY 2,3 ");

    LogMgr.logMetadataSql(new CallerInfo(){}, "types", select);

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
        String schema = rs.getString("TABLE_SCHEM");
        String name = rs.getString("TABLE_NAME");
        String remarks = rs.getString("REMARKS");
        BaseObjectType pgtype = new BaseObjectType(schema, name);
        pgtype.setComment(remarks);
        result.add(pgtype);
      }
      con.releaseSavepoint(sp);
    }
    catch (Exception e)
    {
      con.rollback(sp);
      LogMgr.logMetadataError(new CallerInfo(){}, e, "types", select);
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
    return false;
  }

  @Override
  public List<String> supportedTypes()
  {
    return CollectionUtil.arrayList("TYPE");
  }

  @Override
  public boolean handlesType(String type)
  {
    return "TYPE".equalsIgnoreCase(type);
  }

  @Override
  public DataStore getObjectDetails(WbConnection con, DbObject object)
  {
    try
    {
      List<ColumnIdentifier> columns = getColumns(con, object);
      TableDefinition tdef = new TableDefinition(createTableIdentifier(object), columns);
      DataStore result = new TableColumnsDatastore(tdef);
      return result;
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Cannot retrieve type columns", e);
      return null;
    }
  }

  @Override
  public BaseObjectType getObjectDefinition(WbConnection con, DbObject name)
  {
    try
    {
      // Currently the Postgres driver does not return the comments defined for a type
      // so it's necessary to retrieve the type definition again in order to get the correct remarks
      List<BaseObjectType> types = getTypes(con, name.getSchema(), name.getObjectName());
      if (types.size() == 1)
      {
        BaseObjectType result = types.get(0);
        result.setAttributes(getColumns(con, name));
        return result;
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Cannot retrieve type columns", e);
    }
    return null;
  }

  @Override
  public boolean hasColumns()
  {
    return true;
  }

  @Override
  public List<ColumnIdentifier> getColumns(WbConnection con, DbObject object)
  {
    if (object == null) return null;
    if (con == null) return null;

    // Starting with build 1204 the Postgres JDBC driver does not return column
    // information for types any longer
    // this is a copy of the statement and code used by the driver, adjusted for type columns only
    String sql =
      "SELECT a.attname as column_name, \n" +
      "       a.attnum as column_position,  \n" +
      "       dsc.description as remarks, \n" +
      "       a.atttypid, \n" +
      "       a.atttypmod, \n" +
      "       pg_catalog.format_type(a.atttypid, null) as data_type \n" +
      "FROM pg_catalog.pg_namespace n  \n" +
      "   JOIN pg_catalog.pg_class c ON c.relnamespace = n.oid \n" +
      "   JOIN pg_catalog.pg_attribute a ON a.attrelid = c.oid \n" +
      "   LEFT JOIN pg_catalog.pg_description dsc ON c.oid=dsc.objoid AND a.attnum = dsc.objsubid \n" +
      "   LEFT JOIN pg_catalog.pg_class dc ON dc.oid=dsc.classoid AND dc.relname='pg_class' \n" +
      "   LEFT JOIN pg_catalog.pg_namespace dn ON dc.relnamespace=dn.oid AND dn.nspname='pg_catalog' \n" +
      "WHERE a.attnum > 0 AND NOT a.attisdropped  \n" +
      "  AND c.relkind = 'c' \n" +
      "  AND n.nspname = ? \n " +
      "  AND c.relname = ? \n " +
      "ORDER BY column_position";

    PreparedStatement stmt = null;
    ResultSet rs = null;
    Savepoint sp = null;
    List<ColumnIdentifier> result = new ArrayList<>();

    PgTypeInfo typeInfo = new PgTypeInfo(con);

    LogMgr.logMetadataSql(new CallerInfo(){}, "type columns", sql, object.getSchema(), object.getObjectName());

    try
    {
      DataTypeResolver resolver = con.getMetadata().getDataTypeResolver();
      sp = con.setSavepoint();
      stmt = con.getSqlConnection().prepareStatement(sql);
      stmt.setString(1, object.getSchema());
      stmt.setString(2, object.getObjectName());

      rs = stmt.executeQuery();

      while (rs.next())
      {
        String colName = rs.getString(1);
        int pos = rs.getInt(2);
        String remarks = rs.getString(3);
        int typeOid = rs.getInt(4);
        int typeMod = rs.getInt(5);
        String pgType = rs.getString(6);

        ColumnIdentifier col = new ColumnIdentifier(colName);
        int jdbcType = typeInfo.getSQLType(typeOid);
        int decimalDigits = typeInfo.getScale(typeOid, typeMod);
        int columnSize = typeInfo.getPrecision(typeOid, typeMod);
        if (columnSize == 0) {
            columnSize = typeInfo.getDisplaySize(typeOid, typeMod);
        }
        col.setDataType(jdbcType);
        col.setDecimalDigits(decimalDigits);
        col.setColumnSize(columnSize);
        col.setComment(remarks);
        col.setDbmsType(resolver.getSqlTypeDisplay(pgType, jdbcType, columnSize, decimalDigits));
        col.setPosition(pos);
        result.add(col);
      }
    }
    catch (Exception ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "type columns", sql, object.getSchema(), object.getObjectName());
    }
    finally
    {
      con.releaseSavepoint(sp);
      JdbcUtils.closeAll(rs, stmt);
    }
    return result;
  }

  private TableIdentifier createTableIdentifier(DbObject object)
  {
    TableIdentifier tbl = new TableIdentifier(object.getCatalog(), object.getSchema(), object.getObjectName());
    tbl.setComment(object.getComment());
    tbl.setType(object.getObjectType());
    return tbl;
  }

  @Override
  public String getObjectSource(WbConnection con, DbObject object)
  {
    BaseObjectType type = getObjectDefinition(con, object);
    if (type == null) return null;
    StringBuilder sql = new StringBuilder(50 + type.getNumberOfAttributes() * 50);
    sql.append("CREATE TYPE ");
    sql.append(type.getObjectName());
    sql.append(" AS\n(\n");
    List<ColumnIdentifier> columns = type.getAttributes();
    int maxLen = ColumnIdentifier.getMaxNameLength(columns);
    for (int i=0; i < columns.size(); i++)
    {
      sql.append("  ");
      sql.append(StringUtil.padRight(columns.get(i).getColumnName(), maxLen + 2));
      sql.append(columns.get(i).getDbmsType());
      if (i < columns.size() - 1) sql.append(",\n");
    }
    sql.append("\n);\n");

    String comment = type.getComment();
    CommentSqlManager mgr = new CommentSqlManager(con.getDbSettings().getDbId());
    String template = mgr.getCommentSqlTemplate("type", null);
    if (StringUtil.isNotBlank(comment) && template != null)
    {
      template = template.replace(CommentSqlManager.COMMENT_OBJECT_NAME_PLACEHOLDER, type.getObjectExpression(con));
      template = template.replace(CommentSqlManager.COMMENT_PLACEHOLDER, comment);
      sql.append('\n');
      sql.append(template);
      sql.append(";\n");
    }

    ColumnChanger changer = new ColumnChanger(con);
    for (ColumnIdentifier col : columns)
    {
      String colComment = col.getComment();
      if (StringUtil.isNotBlank(colComment))
      {
        String commentSql = changer.getColumnCommentSql(object, col);
        sql.append(commentSql);
        sql.append(";\n");
      }
    }
    return sql.toString();
  }
}
