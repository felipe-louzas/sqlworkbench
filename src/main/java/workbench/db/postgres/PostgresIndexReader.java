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
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.DbMetadata;
import workbench.db.IndexDefinition;
import workbench.db.JdbcIndexReader;
import workbench.db.JdbcUtils;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.util.CollectionUtil;
import workbench.util.ExceptionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * A extension to the JdbcIndexReader to construct the Postgres specific syntax
 * for indexes.
 *
 * This class does not actually construct the CREATE INDEX based on the information
 * available from the JDBC API, but retrieves the CREATE INDEX directly from the database
 * as Postgres stores the full command in the table <tt>pg_indexes</tt>.
 *
 * @author  Thomas Kellerer
 */
public class PostgresIndexReader
  extends JdbcIndexReader
{
  private static final String PROP_RETRIEVE_DETAILS = "workbench.db.postgres.index.retrieve.details";

  public PostgresIndexReader(DbMetadata meta)
  {
    super(meta);
  }

  /**
   * Return the SQL for several indexes for one table.
   *
   * @param table      the table for which to retrieve the indexes
   * @param indexList  the indexes to retrieve
   *
   * @return The SQL statement for all indexes
   */
  @Override
  public StringBuilder getIndexSource(TableIdentifier table, List<IndexDefinition> indexList)
  {
    if (CollectionUtil.isEmpty(indexList)) return null;

    WbConnection con = this.metaData.getWbConnection();
    Statement stmt = null;
    ResultSet rs = null;

    final CallerInfo ci = new CallerInfo(){};

    // The full CREATE INDEX Statement is stored in pg_indexes for each
    // index. So all we need to do, is retrieve the indexdef value from there for all passed indexes.
    // For performance reasons I'm not calling getIndexSource(IndexDefinition) in a loop
    int count = indexList.size();
    String schema = "'" + table.getRawSchema() + "'";

    StringBuilder sql = new StringBuilder(50 + count * 20);
    sql.append("-- SQL Workbench/J \n");
    String colStatsExpr = "null::int[] as column_stats";

    if (JdbcUtils.hasMinimumServerVersion(con, "11"))
    {
      colStatsExpr = "(select array_agg(a.attstattarget) " +
                     "from pg_attribute a " +
                     "where a.attrelid = pg_catalog.format('%I.%I', i.schemaname, i.indexname)::regclass) as column_stats";
    }

    String myPgIndexes;

    // this fixes a bug in Postgres 11.2 where the view pg_indexes does not include partitioned tables or partitioned indexes
    if (JdbcUtils.hasMinimumServerVersion(con, "11"))
    {
      myPgIndexes =
        "  SELECT c.relnamespace::regnamespace::text AS schemaname, \n" +
        "         c.relname AS tablename, \n" +
        "         i.relname AS indexname, \n" +
        "         t.spcname AS tablespace, \n" +
        "         pg_catalog.pg_get_indexdef(i.oid) AS indexdef \n" +
        "  FROM pg_catalog.pg_index x \n" +
        "     JOIN pg_catalog.pg_class c ON c.oid = x.indrelid \n" +
        "     JOIN pg_catalog.pg_class i ON i.oid = x.indexrelid \n" +
        "     LEFT JOIN pg_catalog.pg_tablespace t ON t.oid = i.reltablespace \n" +
        "  WHERE c.relkind in ('r','m','p') \n" +
        "    AND i.relkind in ('i', 'I') \n " +
        "    AND NOT i.relispartition \n"; // exclude "automatic" indexes on partitions
    }
    else
    {
      myPgIndexes = "select * from pg_indexes\n";
    }

    if (JdbcUtils.hasMinimumServerVersion(con, "8.0"))
    {
      sql.append(
        "with my_pg_indexes as (" +
            myPgIndexes +
        ")\n" +
        "SELECT i.indexdef, \n" +
        "       i.indexname, \n" +
        "       i.tablespace, \n" +
        "       pg_catalog.obj_description((quote_ident(i.schemaname)||'.'||quote_ident(i.indexname))::regclass, 'pg_class') as remarks, \n" +
        "       " + colStatsExpr + ", \n " +
        "       ts.default_tablespace \n" +
        "FROM my_pg_indexes i \n" +
        "  cross join (\n" +
        "    select ts.spcname as default_tablespace\n" +
        "    from pg_catalog.pg_database d\n" +
        "      join pg_catalog.pg_tablespace ts on ts.oid = d.dattablespace\n" +
        "    where d.datname = current_database()\n" +
        "  ) ts \n " +
        "WHERE (i.schemaname, i.indexname) IN (");
    }
    else
    {
      sql.append(
        "SELECT indexdef, indexname, null::text as tablespace, null::text as remarks, null::text as default_tablespace \n" +
        "FROM pg_catalog.pg_indexes \n" +
        "WHERE (schemaname, indexname) IN (");
    }

    boolean showNonStandardTablespace = con.getDbSettings().getBoolProperty("show.nonstandard.tablespace", true);

    String nl = Settings.getInstance().getInternalEditorLineEnding();

    StringBuilder source = new StringBuilder(count * 50);

    Savepoint sp = null;
    int indexCount = 0;
    try
    {
      for (IndexDefinition index : indexList)
      {
        String idxName = "'" + con.getMetadata().removeQuotes(index.getName()) + "'";

        if (index.isPrimaryKeyIndex()) continue;
        if (index.isAutoGenerated()) continue;

        if (index.isUniqueConstraint())
        {
          String constraint = getUniqueConstraint(table, index);
          source.append(constraint);
          source.append(nl);
        }
        else
        {
          if (indexCount > 0) sql.append(',');
          sql.append('(');
          sql.append(schema);
          sql.append(',');
          sql.append(idxName);
          sql.append(')');
          indexCount++;
        }
      }
      sql.append(')');

      if (indexCount > 0)
      {
        LogMgr.logMetadataSql(ci, "index definition", sql);

        sp = con.setSavepoint();
        stmt = con.createStatementForQuery();

        rs = stmt.executeQuery(sql.toString());
        while (rs.next())
        {
          source.append(addIfNotExists(rs.getString("indexdef")));

          String idxName = rs.getString("indexname");
          String tblSpace = rs.getString("tablespace");
          String defaultTablespace = rs.getString("default_tablespace");
          Integer[] colStats = JdbcUtils.getArray(rs, "column_stats", Integer[].class);

          if (showNonStandardTablespace && !"pg_default".equals(defaultTablespace) && StringUtil.isEmpty(tblSpace))
          {
            tblSpace = defaultTablespace;
          }

          if (StringUtil.isNotEmpty(tblSpace))
          {
            IndexDefinition idx = findIndexByName(indexList, idxName);
            idx.setTablespace(tblSpace);
            source.append(" TABLESPACE ");
            source.append(tblSpace);
          }
          source.append(';');
          source.append(nl);
          if (colStats != null)
          {
            source.append(nl);
            for (int i=0; i < colStats.length; i++)
            {
              if (colStats[i] > 0)
              {
                source.append("ALTER INDEX " + SqlUtil.quoteObjectname(idxName) + " ALTER COLUMN " + (i+1) + " SET STATISTICS " + colStats[i] + ";");
                source.append(nl);
              }
            }
          }

          String remarks = rs.getString("remarks");
          if (StringUtil.isNotBlank(remarks))
          {
            source.append("COMMENT ON INDEX " + SqlUtil.quoteObjectname(idxName) + " IS '" + SqlUtil.escapeQuotes(remarks) + "'");
          }
        }
        con.releaseSavepoint(sp);
      }
    }
    catch (Exception e)
    {
      con.rollback(sp);
      LogMgr.logMetadataError(ci, e, "index definition", sql);
      source = new StringBuilder(ExceptionUtil.getDisplay(e));
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }

    if (source.length() > 0) source.append(nl);

    return source;
  }

  private boolean useIfNotExists()
  {
    if (this.metaData == null) return false;
    return StringUtil.isNotEmpty(metaData.getDbSettings().getDDLIfNoExistsOption("INDEX"));
  }

  private String addIfNotExists(String indexDef)
  {
    if (StringUtil.isEmpty(indexDef)) return indexDef;
    if (useIfNotExists())
    {
      return indexDef.replace("CREATE INDEX", "CREATE INDEX IF NOT EXISTS");
    }
    return indexDef;
  }

  /**
   * Return the SQL to re-create any (non default) options for the index.
   *
   * The returned String has to be structured so that it can be appended
   * after the DBMS specific basic CREATE INDEX statement.
   *
   * @param table   the table for which ot retrieve the index options
   * @param index   the table's index for which to retrieve the options
   *
   * @return null if not options are applicable
   *         a SQL fragment to be appended at the end of the create index statement if an option is available.
   */
  @Override
  public String getIndexOptions(TableIdentifier table, IndexDefinition index)
  {
    if (index != null && StringUtil.isNotEmpty(index.getTablespace()))
    {
      return "\n   TABLESPACE " + index.getTablespace();
    }
    return null;
  }

  @Override
  public boolean supportsTableSpaces()
  {
    // only make this dependent on the property to actually retrieve the tablespace, because
    // we know that Postgres supports table spaces
    return Settings.getInstance().getBoolProperty(PROP_RETRIEVE_DETAILS, true);
  }

  /**
   * Enhance the retrieved indexes with additional information.
   *
   * Currently this reads comments, tablespace and index type details.
   *
   * Reading of the details can be turned off using the config setting {@link #PROP_RETRIEVE_DETAILS}
   *
   * @param tbl        the table for which the indexes were retrieved
   * @param indexDefs  the list of retrieved indexes
   *
   * @see IndexDefinition#setTablespace(java.lang.String)
   * @see PostgresIndexReader#PROP_RETRIEVE_DETAILS
   */
  @Override
  public void processIndexList(Collection<IndexDefinition> indexDefs)
  {
    if (!Settings.getInstance().getBoolProperty(PROP_RETRIEVE_DETAILS, true)) return;

    if (CollectionUtil.isEmpty(indexDefs)) return;

    WbConnection con = this.metaData.getWbConnection();
    if (!JdbcUtils.hasMinimumServerVersion(con, "8.0")) return;

    Statement stmt = null;
    ResultSet rs = null;

    int count = indexDefs.size();

    boolean isPg11 = JdbcUtils.hasMinimumServerVersion(con, "11");

    StringBuilder sql = new StringBuilder(50 + count * 20);
    sql.append(
      "SELECT i.relname AS indexname, \n" +
      "       coalesce(t.spcname, ts.default_tablespace) as tablespace, \n" +
      "       pg_catalog.obj_description(i.oid) as remarks, \n" +
      "       am.amname as index_type, \n" +
      "       pg_catalog.pg_get_expr(x.indpred, x.indrelid, true) as filter_expression \n" +
      "FROM pg_catalog.pg_index x \n" +
      "  JOIN pg_catalog.pg_class i ON i.oid = x.indexrelid \n" +
      "  JOIN pg_catalog.pg_class c ON c.oid = x.indrelid \n" +
      "  JOIN pg_catalog.pg_am am on am.oid = i.relam \n" +
      "  LEFT JOIN pg_catalog.pg_namespace n ON n.oid = c.relnamespace \n" +
      "  LEFT JOIN pg_catalog.pg_tablespace t ON t.oid = i.reltablespace \n" +
      "  cross join ( \n" +
      "    select nullif(ts.spcname, 'pg_default') as default_tablespace \n" +
      "    from pg_catalog.pg_database d \n" +
      "      join pg_tablespace ts on ts.oid = d.dattablespace \n" +
      "    where d.datname = current_database() \n" +
      "  ) ts \n" +
      "WHERE c.relkind in ('r', 'm', 'p')  \n" +
      "  AND i.relkind in ('i','I') \n" +
      (isPg11 ? "  and not i.relispartition \n" : "") +
      "and (n.nspname, i.relname) IN (");

    int indexCount = 0;
    for (IndexDefinition index : indexDefs)
    {
      String idxName = con.getMetadata().removeQuotes(index.getName());
      String schema = con.getMetadata().removeQuotes(index.getSchema());
      if (indexCount > 0) sql.append(',');
      sql.append("('");
      sql.append(schema);
      sql.append("','");
      sql.append(idxName);
      sql.append("')");
      indexCount++;
    }
    sql.append(')');

    LogMgr.logMetadataSql(new CallerInfo(){}, "index information", sql);

    Savepoint sp = null;

    try
    {
      sp = con.setSavepoint();
      stmt = con.createStatementForQuery();

      rs = stmt.executeQuery(sql.toString());
      while (rs.next())
      {
        String idxName = rs.getString(1);
        String tblSpace = rs.getString(2);
        String remarks = rs.getString(3);
        String type = rs.getString(4);
        String filter = rs.getString(5);
        IndexDefinition idx = findIndexByName(indexDefs, idxName);
        if (StringUtil.isNotEmpty(tblSpace))
        {
          idx.setTablespace(tblSpace);
        }
        idx.setComment(remarks);
        idx.setIndexType(type);
        idx.setFilterExpression(filter);
      }
      con.releaseSavepoint(sp);
    }
    catch (Exception e)
    {
      con.rollback(sp);
      LogMgr.logMetadataError(new CallerInfo(){}, e, "index information", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
  }

  @Override
  public CharSequence getIndexSource(TableIdentifier table, IndexDefinition indexDefinition)
  {
    if (indexDefinition == null) return null;
    if (table == null) return null;

    // This allows to use a statement configured through workbench.settings
    // see getNativeIndexSource()
    if (Settings.getInstance().getBoolProperty("workbench.db.postgresql.default.indexsource", false))
    {
      return super.getIndexSource(table, indexDefinition);
    }

    if (indexDefinition.isUniqueConstraint())
    {
      return getUniqueConstraint(table, indexDefinition);
    }

    return getIndexSource(table, Collections.singletonList(indexDefinition));
  }

  @Override
  public boolean supportsIndexComments()
  {
    return true;
  }

  @Override
  protected String quoteIndexColumn(String colName)
  {
    if (colName == null) return null;
    if (colName.startsWith("(")) return colName;
    return metaData.quoteObjectname(colName);
  }

}
