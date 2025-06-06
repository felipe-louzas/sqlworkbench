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

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.GenerationOptions;
import workbench.db.JdbcUtils;
import workbench.db.SequenceDefinition;
import workbench.db.SequenceReader;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.storage.DataStore;

import workbench.util.StringUtil;

/**
 * @author  Thomas Kellerer
 */
public class PostgresSequenceReader
  implements SequenceReader
{
  private final WbConnection dbConnection;
  public static final String PROP_ACL = "acl";
  private static final String NAME_PLACEHOLDER = "%sequence_name%";
  public static final String PROP_UNLOGGED = "unlogged";

  private final String baseSql =
      "SELECT seq_info.*, \n" +
      "       null::text as data_type, \n" +
      "       pg_catalog.obj_description(seq.oid, 'pg_class') as remarks, \n" +
      "       pg_catalog.quote_ident(tab.relname)||'.'||quote_ident(col.attname) as owned_by, \n" +
      "       seq.relname as sequence_name, \n" +
      "       sn.nspname as sequence_schema, \n" +
      "       array_to_string(seq.relacl, ',') as acl, \n" +
      "       'p' as relpersistence \n" +
      "FROM pg_catalog.pg_class seq \n" +
      "  JOIN pg_catalog.pg_namespace sn ON sn.oid = seq.relnamespace \n" +
      "  CROSS JOIN (SELECT min_value, max_value, last_value, increment_by, cache_value, is_cycled FROM " + NAME_PLACEHOLDER + ") seq_info \n" +
      "  LEFT JOIN pg_catalog.pg_depend d ON d.objid = seq.oid AND deptype = 'a' \n" +
      "  LEFT JOIN pg_catalog.pg_class tab ON d.objid = seq.oid AND d.refobjid = tab.oid   \n" +
      "  LEFT JOIN pg_catalog.pg_attribute col ON (d.refobjid, d.refobjsubid) = (col.attrelid, col.attnum) \n" +
      "WHERE seq.relkind = 'S'";

  private final String baseSqlV10 =
    "select s.min_value,\n" +
    "       s.max_value,\n" +
    "       s.last_value,\n" +
    "       s.increment_by,\n" +
    "       s.cache_size as cache_value,\n" +
    "       s.cycle as is_cycled,\n" +
    "       pg_catalog.format_type(s.data_type, NULL) as data_type,\n" +
    "       pg_catalog.obj_description(to_regclass(format('%I.%I', s.schemaname, s.sequencename)), 'pg_class') as remarks,\n" +
    "       pg_catalog.quote_ident(tab.relname)||'.'||quote_ident(col.attname) as owned_by,\n" +
    "       s.sequencename as sequence_name, \n" +
    "       s.schemaname as sequence_schema, \n" +
    "       array_to_string(cl.relacl, ',') as acl, \n" +
    "       cl.relpersistence \n" +
    "FROM pg_catalog.pg_sequences s \n" +
    "  JOIN pg_class cl on cl.relname = s.sequencename and cl.relnamespace = s.schemaname::text::regnamespace " +
    "  LEFT JOIN pg_catalog.pg_depend d ON d.objid = pg_catalog.to_regclass(format('%I.%I', s.schemaname, s.sequencename)) AND deptype in ('a', 'i') \n" +
    "  LEFT JOIN pg_catalog.pg_class tab ON d.objid = pg_catalog.to_regclass(format('%I.%I', s.schemaname, s.sequencename)) AND d.refobjid = tab.oid   \n" +
    "  LEFT JOIN pg_catalog.pg_attribute col ON (d.refobjid, d.refobjsubid) = (col.attrelid, col.attnum)";

  public PostgresSequenceReader(WbConnection conn)
  {
    this.dbConnection = conn;
  }

  /**
   *  Return the source SQL for a PostgreSQL sequence definition.
   *
   *  @return The SQL to recreate the given sequence
   */
  @Override
  public CharSequence getSequenceSource(String catalog, String schema, String aSequence, GenerationOptions opt)
  {
    SequenceDefinition def = getSequenceDefinition(catalog, schema, aSequence);
    if (def == null) return "";
    return getSequenceSource(def, opt);
  }

  @Override
  public CharSequence getSequenceSource(SequenceDefinition def, GenerationOptions options)
  {
    if (def == null) return null;

    StringBuilder buf = new StringBuilder(250);

    try
    {
      String name = def.getSequenceName();
      Long max = (Long) def.getSequenceProperty(PROP_MAX_VALUE);
      Long min = (Long) def.getSequenceProperty(PROP_MIN_VALUE);
      Long inc = (Long) def.getSequenceProperty(PROP_INCREMENT);
      Long cache = (Long) def.getSequenceProperty(PROP_CACHE_SIZE);
      Boolean cycle = (Boolean) def.getSequenceProperty(PROP_CYCLE);
      if (cycle == null) cycle = Boolean.FALSE;
      String dataType = (String)def.getSequenceProperty(PROP_DATA_TYPE);

      Boolean unlogged = (Boolean)def.getSequenceProperty(PROP_UNLOGGED);
      buf.append("CREATE ");
      if (unlogged != null && unlogged)
      {
        buf.append("UNLOGGED ");
      }
      buf.append("SEQUENCE ");
      if (dbConnection == null || JdbcUtils.hasMinimumServerVersion(dbConnection, "9.5"))
      {
        buf.append("IF NOT EXISTS ");
      }
      buf.append(name);
      if (StringUtil.isNotBlank(dataType) && StringUtil.stringsAreNotEqual("bigint", dataType))
      {
        buf.append("\n       AS ");
        buf.append(dataType);
      }
      buf.append("\n       INCREMENT BY ");
      buf.append(inc);
      buf.append("\n       MINVALUE ");
      buf.append(min);
      long maxMarker = 9223372036854775807L;
      if (max != maxMarker)
      {
        buf.append("\n       MAXVALUE ");
        buf.append(max.toString());
      }
      buf.append("\n       CACHE ");
      buf.append(cache);
      buf.append("\n       ");
      if (!cycle.booleanValue())
      {
        buf.append("NO ");
      }
      buf.append("CYCLE");
      String col = def.getRelatedColumn();
      TableIdentifier tbl = def.getRelatedTable();
      if (tbl != null && StringUtil.isNotBlank(col))
      {
        String owningColumn = tbl.getTableName() + "." + col;

        if (options != null && options.getIncludeDependencies())
        {
          buf.append("\n       OWNED BY ");
          buf.append(owningColumn);
        }
        def.setPostCreationSQL("ALTER SEQUENCE " + def.getObjectExpression(dbConnection) + " OWNED BY " + owningColumn + ";");
      }

      buf.append(";\n");

      if (StringUtil.isNotBlank(def.getComment()))
      {
        buf.append('\n');
        buf.append("COMMENT ON SEQUENCE ").append(def.getSequenceName()).append(" IS '").append(def.getComment().replace("'", "''")).append("';");
      }

      if (options != null && options.getIncludeGrants())
      {
        PostgresSequenceGrantReader reader = new PostgresSequenceGrantReader();
        String grants = reader.getSequenceGrants(dbConnection, def);
        if (StringUtil.isNotBlank(grants))
        {
          buf.append('\n');
          buf.append(grants);
        }
      }

    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error reading sequence definition", e);
    }
    return buf;
  }

  /**
   * Retrieve the list of full SequenceDefinitions from the database.
   */
  @Override
  public List<SequenceDefinition> getSequences(String catalog, String schema, String namePattern)
  {
    List<SequenceDefinition> result = new ArrayList<>();

    ResultSet rs = null;
    Savepoint sp = null;
    if (namePattern == null) namePattern = "%";

    try
    {
      sp = this.dbConnection.setSavepoint();
      DatabaseMetaData meta = this.dbConnection.getSqlConnection().getMetaData();
      rs = meta.getTables(null, schema, namePattern, new String[] { "SEQUENCE"} );
      while (rs.next())
      {
        String seqName = rs.getString("TABLE_NAME");
        String seqSchema = rs.getString("TABLE_SCHEM");
        result.add(getSequenceDefinition(null, seqSchema, seqName));
      }
      this.dbConnection.releaseSavepoint(sp);
    }
    catch (SQLException e)
    {
      this.dbConnection.rollback(sp);
      LogMgr.logError(new CallerInfo(){}, "Error retrieving sequences", e);
    }
    finally
    {
      JdbcUtils.closeResult(rs);
    }
    return result;
  }

  private SequenceDefinition createDefinition(String name, String schema, DataStore ds)
  {
    SequenceDefinition def = new SequenceDefinition(schema, name);
    def.setSequenceProperty(PROP_INCREMENT, ds.getValue(0, "increment_by"));
    def.setSequenceProperty(PROP_MAX_VALUE, ds.getValue(0, "max_value"));
    def.setSequenceProperty(PROP_MIN_VALUE, ds.getValue(0, "min_value"));
    def.setSequenceProperty(PROP_CACHE_SIZE, ds.getValue(0, "cache_value"));
    def.setSequenceProperty(PROP_CYCLE, ds.getValue(0, "is_cycled"));
    def.setSequenceProperty(PROP_LAST_VALUE, ds.getValue(0, "last_value"));
    def.setSequenceProperty(PROP_DATA_TYPE, ds.getValue(0, "data_type"));
    def.setSequenceProperty(PROP_ACL, ds.getValueAsString(0, "acl"));

    String persistence = ds.getValueAsString(0, "relpersistence");
    Boolean unlogged = "u".equals(persistence);
    def.setSequenceProperty(PROP_UNLOGGED, unlogged);

    String ownedBy = ds.getValueAsString(0, "owned_by");
    if (StringUtil.isNotEmpty(ownedBy))
    {
      List<String> elements = StringUtil.stringToList(ownedBy, ".", true, true, false, false);
      TableIdentifier tbl = new TableIdentifier(schema, elements.get(0));
      def.setRelatedTable(tbl, elements.get(1));
    }
    String comment = ds.getValueAsString(0, "remarks");
    def.setComment(comment);
    return def;
  }

  @Override
  public SequenceDefinition getSequenceDefinition(String catalog, String schema, String sequence)
  {
    DataStore ds = getRawSequenceDefinition(null, schema, sequence);
    if (ds == null) return null;
    SequenceDefinition result = createDefinition(sequence, schema, ds);
    return result;
  }

  @Override
  public DataStore getRawSequenceDefinition(String catalog, String schema, String sequence)
  {
    if (sequence == null) return null;

    String fullname = (schema == null ? sequence : schema + "." + sequence);

    DataStore result = null;
    PreparedStatement stmt = null;
    ResultSet rs = null;
    Savepoint sp = null;

    boolean is10 = JdbcUtils.hasMinimumServerVersion(dbConnection, "10.0");

    String seqInfoSql = is10 ? baseSqlV10 : baseSql;

    String sql =
      "-- SQL Workbench/J \n" +
      "select min_value, max_value, last_value, increment_by, cache_value, is_cycled, data_type, remarks, owned_by, acl, relpersistence \n" +
      "from ( \n" + seqInfoSql.replace(NAME_PLACEHOLDER, fullname) + "\n) t \n" +
      "where sequence_name = ? ";

    if (schema != null)
    {
      sql += "\n  and sequence_schema = ? ";
    }

    try
    {
      LogMgr.logMetadataSql(new CallerInfo(){}, "sequence details", sql, sequence, schema);

      sp = this.dbConnection.setSavepoint();
      stmt = this.dbConnection.getSqlConnection().prepareStatement(sql);
      stmt.setString(1, sequence);
      if (schema != null)
      {
        stmt.setString(2, schema);
      }
      rs = stmt.executeQuery();
      result = new DataStore(rs, true);
      this.dbConnection.releaseSavepoint(sp);
    }
    catch (SQLException e)
    {
      this.dbConnection.rollback(sp);
      // sqlstate = 42P01 is "undefined table" which can happen if this method was called
      // for a sequence that doesn't exist. There is no need to log this
      LogMgr.logMetadataError(new CallerInfo(){}, e, "sequence details", sql, sequence, schema);
      return null;
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }

    if (result.getRowCount() > 1)
    {
      // this can happen if a sequence is owned by more than one column
      // so collect all the "owned_by" values and then delete all but the
      // first row
      int colIndex = result.getColumnIndex("owned_by");
      String cols = "";
      for (int i=0; i < result.getRowCount(); i++)
      {
        String owned = result.getValueAsString(i, colIndex);
        if (StringUtil.isNotEmpty(owned))
        {
          if (cols.length() > 0) cols += ", ";
          cols += owned;
        }
      }
      for (int i=result.getRowCount() - 1; i > 0; i--)
      {
        result.deleteRow(i);
      }
      result.setValue(0, colIndex, cols);
    }

    result.resetStatus();

    return result;
  }

  @Override
  public String getSequenceTypeName()
  {
    return SequenceReader.DEFAULT_TYPE_NAME;
  }

}
