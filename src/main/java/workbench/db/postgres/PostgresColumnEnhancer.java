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
import java.util.ArrayList;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.ColumnDefinitionEnhancer;
import workbench.db.ColumnIdentifier;
import workbench.db.GeneratedColumnType;
import workbench.db.JdbcUtils;
import workbench.db.TableDefinition;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * A column enhancer to update column definitions for PostgreSQL.
 *
 * The following additional information is updated:
 * <ul>
 * <li>The column collation if the the version is &gt;= 9.1</li>
 * <li>Storage definition</li>
 * <li>The array dimensions so that arrays are displayed correctly.</li>
 * <li>Inheritance information</li>
 * <li>Converts serial columns back to "serial"</li>
 * </ul>
 *
 * @author Thomas Kellerer
 */
public class PostgresColumnEnhancer
  implements ColumnDefinitionEnhancer
{
  public static final int STORAGE_PLAIN = 1;
  public static final int STORAGE_MAIN = 2;
  public static final int STORAGE_EXTERNAL = 3;
  public static final int STORAGE_EXTENDED = 4;

  public static final String PROP_SHOW_REAL_SERIAL_DEF = "workbench.db.postgresql.serial.show";

  @Override
  public void updateColumnDefinition(TableDefinition table, WbConnection conn)
  {
    if (JdbcUtils.hasMinimumServerVersion(conn, "8.0"))
    {
      readColumnInfo(table, conn);
    }

    updateSerials(table);
  }

  public static String getStorageOption(int storage)
  {
    switch (storage)
    {
      case STORAGE_EXTENDED:
        return "EXTENDED";
      case STORAGE_EXTERNAL:
        return "EXTERNAL";
      case STORAGE_MAIN:
        return "MAIN";
      case STORAGE_PLAIN:
        return "PLAIN";
    }
    return null;
  }

  private void updateSerials(TableDefinition table)
  {
    for (ColumnIdentifier col : table.getColumns())
    {
      String dbmsType = col.getDbmsType();
      String defaultValue = col.getDefaultValue();
      if (dbmsType.endsWith("serial") && defaultValue != null)
      {
        // The nextval() call is returned with a fully qualified name if the sequence is not in the current schema.
        // to avoid calling WbConnection.getCurrentSchema() for each default value
        // I'm just checking for a . in the default value which would indicate a fully qualified sequence
        String expectedDefault = "nextval('" ;
        if (defaultValue.indexOf('.') > -1)
        {
          expectedDefault += table.getTable().getRawSchema()+ ".";
        }
        expectedDefault += table.getTable().getRawTableName() + "_" + col.getColumnName() + "_seq'::regclass)";

        if (Settings.getInstance().getBoolProperty(PROP_SHOW_REAL_SERIAL_DEF, true) && (defaultValue.equals(expectedDefault)))
        {
          col.setDefaultValue(null);
        }
        else
        {
          if (dbmsType.equals("serial"))
          {
            col.setDbmsType("integer");
          }
          if (dbmsType.equals("bigserial"))
          {
            col.setDbmsType("bigint");
          }
        }
      }
    }
  }

  /**
   * Read additional information about the table's columns that are not provided by the JDBC API.
   *
   * The following additional information is retrieved from the database:
   * <ul>
   * <li>Collation</li>
   * <li>Inheritance information</li>
   * <li>Storage information</li>
   * <li>Original array dimensions</li>
   * </ul>
   *
   * All the above is retrieved from <tt>pg_attribute</tt> and <tt>pg_collation</tt>.
   *
   * @param table - the table for which the information should be retrieved
   * @param conn - the connection to use
   */
  private void readColumnInfo(TableDefinition table, WbConnection conn)
  {
    PreparedStatement stmt = null;
    ResultSet rs = null;

    boolean is91 = JdbcUtils.hasMinimumServerVersion(conn, "9.1");
    boolean is10 = JdbcUtils.hasMinimumServerVersion(conn, "10");
    boolean is12 = JdbcUtils.hasMinimumServerVersion(conn, "12");
    boolean is14 = JdbcUtils.hasMinimumServerVersion(conn, "14");

    String collateColumn = is91 ? "col.collcollate" : "null as collcollate";
    String identityAtt = (is10 ? "nullif(att.attidentity, '') " : "null") + " as attidentity";
    String generatedExpr = (is12 ? "pg_get_expr(d.adbin, d.adrelid)" : "null") + " as generated_expr";
    String generationType = (is12 ? "att.attgenerated" : "null") + " as attgenerated";
    String compression = (is14 ? "att.attcompression" : "null") + " as attcompression";
    String sql =
      "select att.attname, \n" +
      "       " + collateColumn + ", \n" +
      "       case \n" +
      "          when att.attlen = -1 then att.attstorage \n" +
      "          else null \n" +
      "       end as attstorage, \n" +
      "       att.atttypmod, \n" +
      "       att.attinhcount, \n" +
      "       att.attndims, \n" +
      "       att.atthasdef, \n" +
      "       pg_catalog.format_type(att.atttypid, att.atttypmod) as display_type, \n" +
      "       " + identityAtt + ", \n" +
      "       " + generatedExpr + ", \n" +
      "       " + generationType + ", \n" +
      "       " + compression + " \n" +
      "from pg_catalog.pg_attribute att  \n" +
      "  join pg_catalog.pg_class tbl on tbl.oid = att.attrelid   \n" +
      "  join pg_catalog.pg_namespace ns on tbl.relnamespace = ns.oid   \n" +
      (is91 ?
      "  left join pg_catalog.pg_collation col on att.attcollation = col.oid \n" : "" ) +
      (is12 ?
      "  left join pg_catalog.pg_attrdef d on d.adrelid = att.attrelid  and d.adnum = att.attnum \n" : "") +
      "where tbl.relname = ? \n" +
      "  and ns.nspname = ? \n" +
      "  and not att.attisdropped \n" +
      "  and att.attname not in ('tableoid', 'cmax', 'xmax', 'cmin', 'xmin', 'ctid')";

    String tname = table.getTable().getRawTableName();
    String tschema = table.getTable().getRawSchema();

    LogMgr.logMetadataSql(new CallerInfo(){}, "column information", sql, tname, tschema);

    List<ColumnIdentifier> identityColumns = new ArrayList<>();
    Savepoint sp = null;
    try
    {
      sp = conn.setSavepoint();
      stmt = conn.getSqlConnection().prepareStatement(sql);
      stmt.setString(1, tname);
      stmt.setString(2, tschema);

      rs = stmt.executeQuery();
      while (rs.next())
      {
        String colname = rs.getString("attname");
        String collation = rs.getString("collcollate");
        String storage = rs.getString("attstorage");
        int ancestorCount = rs.getInt("attinhcount");
        int typMod = rs.getInt("atttypmod");
        boolean hasDefault = rs.getBoolean("atthasdef");

        int arrayDims = rs.getInt("attndims");
        if (rs.wasNull()) arrayDims = -1;
        String formattedType = rs.getString("display_type");
        String identity = rs.getString("attidentity");

        ColumnIdentifier col = table.findColumn(colname);
        if (col == null) continue; // should not happen

        if (StringUtil.isNotEmpty(collation))
        {
          col.setCollation(collation);
          col.setCollationExpression(" COLLATE \"" + collation + "\"");
        }

        if (arrayDims > 0)
        {
          updateArrayType(col, formattedType, arrayDims);
        }

        if (identity != null)
        {
          if ("d".equals(identity))
          {
            col.setGeneratedExpression("GENERATED BY DEFAULT AS IDENTITY", GeneratedColumnType.identity);
          }
          else if ("a".equals(identity))
          {
            col.setGeneratedExpression("GENERATED ALWAYS AS IDENTITY", GeneratedColumnType.identity);
          }
          identityColumns.add(col);
        }
        else if ((formattedType.startsWith("time") || formattedType.startsWith("interval")) && typMod >= 0)
        {
          String type = col.getDbmsType() + "(" + typMod + ")";
          col.setDbmsType(type);
        }
        else if (formattedType.contains(".") || formattedType.contains("\""))
        {
          // make sure quoted or fully qualified identifiers are preserved
          col.setDbmsType(formattedType);
        }
        else
        {
          // columns that use a default value where a sequence is part of the expression
          // e.g. concat('bla', nextval('some_sequence') are also marked as autoIncrement by the driver
          // I find that wrong, so I reset that here
          if (col.isAutoincrement() && !SqlUtil.isNumberType(col.getDataType()) && hasDefault)
          {
            col.setGeneratedColumnType(GeneratedColumnType.none);
          }
        }

        col.setInherited(ancestorCount > 0);

        if (storage != null && !storage.isEmpty())
        {
          switch (storage.charAt(0))
          {
            case 'p':
              col.setPgStorage(STORAGE_PLAIN);
              break;
            case 'm':
              col.setPgStorage(STORAGE_MAIN);
              break;
            case 'e':
              col.setPgStorage(STORAGE_EXTERNAL);
              break;
            case 'x':
              col.setPgStorage(STORAGE_EXTENDED);
              break;
          }
        }

        String genType = rs.getString("attgenerated");
        if ("s".equals(genType))
        {
          String expr = rs.getString("generated_expr");
          // currently the JDBC driver returns the generated expression as the default value
          col.setDefaultValue(null);
          col.setGeneratedExpression("GENERATED ALWAYS AS " + expr + " STORED", GeneratedColumnType.computed);
        }

        String compressType = rs.getString("attcompression");
        if ("l".equals(compressType))
        {
          col.setSQLOption("COMPRESSION lz4");
        }
      }
      conn.releaseSavepoint(sp);
    }
    catch (Exception ex)
    {
      conn.rollback(sp);
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "column information", sql, tname, tschema);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }

    if (identityColumns.size() > 0)
    {
      adjustIdentitySequenceOptions(table.getTable(), identityColumns, conn);
    }
  }

  private void adjustIdentitySequenceOptions(TableIdentifier table, List<ColumnIdentifier> cols, WbConnection conn)
  {
    String sql =
      "SELECT col.attname, s.seqstart, s.seqincrement, s.seqmax, s.seqmin, s.seqcache, s.seqcycle \n" +
      "FROM pg_catalog.pg_sequence s \n" +
      "  JOIN pg_catalog.pg_class seq on s.seqrelid = seq.oid \n" +
      "  JOIN pg_catalog.pg_namespace sn ON sn.oid = seq.relnamespace  \n" +
      "  JOIN pg_catalog.pg_depend d ON d.objid = seq.oid AND deptype in ('a', 'i') \n" +
      "  JOIN pg_catalog.pg_class tab ON d.objid = seq.oid AND d.refobjid = tab.oid    \n" +
      "  JOIN pg_catalog.pg_namespace ts on ts.oid = tab.relnamespace \n" +
      "  JOIN pg_catalog.pg_attribute col ON (d.refobjid, d.refobjsubid) = (col.attrelid, col.attnum)  \n" +
      "where ts.nspname = ? \n" +
      "  and tab.relname = ? \n" +
      "  and col.attname in (";

    boolean first = true;
    for (ColumnIdentifier col : cols)
    {
      if (first) first = false;
      else sql += ",";
      sql += "'" + SqlUtil.escapeQuotes(col.getColumnName()) + "'";
    }
    sql += ")";

    LogMgr.logMetadataSql(new CallerInfo(){}, "identity sequence information", sql, table.getRawSchema(), table.getRawTableName());

    PreparedStatement pstmt = null;
    ResultSet rs = null;
    Savepoint sp = null;
    try
    {
      sp = conn.setSavepoint();

      pstmt = conn.getSqlConnection().prepareStatement(sql);
      pstmt.setString(1, table.getRawSchema());
      pstmt.setString(2, table.getRawTableName());
      rs = pstmt.executeQuery();
      while (rs.next())
      {
        String option = "";
        String column = rs.getString(1);
        long start = rs.getLong(2);
        long inc = rs.getLong(3);
        long max = rs.getLong(4);
        long min = rs.getLong(5);
        long cache = rs.getLong(6);
        boolean cycle = rs.getBoolean(7);

        if (start != 1)
        {
          option += "START WITH " + start;
        }
        if (inc != 1)
        {
          option += " INCREMENT BY " + inc;
        }
        if (max != Integer.MAX_VALUE && max != Long.MAX_VALUE)
        {
          option += " MAXVALUE " + max;
        }
        if (min != 1)
        {
          option += " MINVALUE " + min;
        }
        if (cache != 1)
        {
          option += " CACHE " + cache;
        }
        if (cycle)
        {
          option += " CYCLE";
        }
        if (option.length() > 0)
        {
          ColumnIdentifier id = ColumnIdentifier.findColumnInList(cols, column);
          String expression = id.getGenerationExpression();
          expression = expression + " (" + option.trim() + ")";
          id.setGeneratedExpression(expression, id.getGeneratedColumnType());
        }
      }
      conn.releaseSavepoint(sp);
    }
    catch (Exception ex)
    {
      conn.rollback(sp);
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "identity sequence information", sql, table.getRawSchema(), table.getRawTableName());
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }
  }

  private void updateArrayType(ColumnIdentifier column, String formattedType, int numDims)
  {
    if (numDims <= 0) return;

    String type = StringUtil.replace(formattedType, "character varying", "varchar");
    for (int i = 0; i < numDims - 1; i++)
    {
      type += "[]";
    }
    column.setDbmsType(type);
  }

}
