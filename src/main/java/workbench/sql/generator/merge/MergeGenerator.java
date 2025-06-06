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
package workbench.sql.generator.merge;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import workbench.resource.Settings;

import workbench.db.ColumnIdentifier;
import workbench.db.DBID;
import workbench.db.JdbcUtils;
import workbench.db.QuoteHandler;
import workbench.db.WbConnection;

import workbench.storage.ResultInfo;
import workbench.storage.RowData;
import workbench.storage.RowDataContainer;

import workbench.util.CaseInsensitiveComparator;
import workbench.util.CollectionUtil;

/**
 *
 * @author Thomas Kellerer
 */
public interface MergeGenerator
{
  final String PG_CTE_TYPE = "postgres-cte";

  /**
   * Generate a single MERGE statment (or something equivalent depending on the DBMS)
   * based on the passed data.
   *
   * Depending on the capabilities of the DBMS, the result might be one
   * statement for each row or one statement for all rows.
   *
   * @param data       the data source
   *
   * @return one or more SQL statements to merge the data into an existing table.
   *         might be null (e.g. if no update table is present)
   */
  String generateMerge(RowDataContainer data);

  /**
   * Generate the start of a MERGE statement.
   * <br/>
   * The complete MERGE statement needs to be assembled using generateMergeStart(), addRow() and generateMergeEnd().
   *
   * @param data the data source
   * @return  the start of a MERGE statement
   */
  String generateMergeStart(RowDataContainer data);

  /**
   * Generate the SQL for a single row in a MERGE statement.
   *
   * <br/>
   * The complete MERGE statement needs to be assembled using generateMergeStart(), addRow() and generateMergeEnd().
   *
   * @param info      the metadata of the result
   * @param row       the data
   * @param rowIndex  the rowIndex in the result
   * @return  the SQL for a single row inside a MERGE statement.
   */
  String addRow(ResultInfo info, RowData row, long rowIndex);

  /**
   * Generate the end of a MERGE statement.
   * <br/>
   * The complete MERGE statement needs to be assembled using generateMergeStart(), addRow() and generateMergeEnd().
   *
   * @param data the data source
   * @return  the end of a MERGE statement
   */
  String generateMergeEnd(RowDataContainer data);

  /**
   * Define the column to include in the generated MERGE.
   *
   * If this is not set, all columns of the result are included.
   *
   * @param columns
   */
  void setColumns(List<ColumnIdentifier> columns);

  void setQuoteHandler(QuoteHandler handler);

  /**
   * The factory to create MergeGenerator instances depending on the DBMS.
   */
  class Factory
  {
    private static final Map<String, String> DBID_TO_TYPE_MAP = new TreeMap<>(CaseInsensitiveComparator.INSTANCE);
    static
    {
      DBID_TO_TYPE_MAP.put("postgresql", "postgres");
      DBID_TO_TYPE_MAP.put("h2database", "h2");
      DBID_TO_TYPE_MAP.put("microsoft_sql_server", "sqlserver");
      DBID_TO_TYPE_MAP.put("db2i", "db2");
      DBID_TO_TYPE_MAP.put("db2h", "db2");
      DBID_TO_TYPE_MAP.put("hsql_database_engine", "hsqldb");
    }

    /**
     * Create a MergeGenerator for the DBMS identified by the connection.
     *
     * @param conn the connection identifying the DBMS
     * @return the generator, never null (defaults to AnsiSQLMergeGenerator)
     */
    public static MergeGenerator createGenerator(WbConnection conn)
    {
      if (conn == null) return new AnsiSQLMergeGenerator();
      if (DBID.Postgres.isDB(conn) && JdbcUtils.hasMinimumServerVersion(conn, "9.5"))
      {
        return new Postgres95MergeGenerator();
      }
      MergeGenerator generator = createGenerator(conn.getDbId());
      generator.setQuoteHandler(conn.getMetadata());
      return generator;
    }

    /**
     * Create a MergeGenerator for the specify DBMS.
     *
     * @param type the database identifier or the "generator type"
     * @return the generator, never null (defaults to AnsiSQLMergeGenerator)
     */
    public static MergeGenerator createGenerator(String type)
    {
      if (type == null) return null;

      type = type.toLowerCase();

      if (DBID.Oracle.isDB(type))
      {
        return new OracleMergeGenerator();
      }

      if (DBID.Postgres.isDB(type) || type.equals("postgres"))
      {
        return new Postgres95MergeGenerator();
      }

      if (type.equals("postgres-cte"))
      {
        return new PostgresWriteableCTEGenerator();
      }

      if (DBID.MySQL.isDB(type) || DBID.MariaDB.isDB(type))
      {
        return new MySQLMergeGenerator();
      }

      if (DBID.SQL_Server.isDB(type) || "sqlserver".equals(type))
      {
        return new SqlServerMergeGenerator(type);
      }

      if (type.startsWith("db2"))
      {
        return new Db2MergeGenerator();
      }

      if (type.startsWith("h2"))
      {
        return new H2MergeGenerator();
      }

      if (DBID.Firebird.isDB(type))
      {
        if (Settings.getInstance().getBoolProperty("workbench.db.firebird.mergegenerator.use.ansi", true))
        {
          return new Firebird21MergeGenerator();
        }
        return new Firebird20MergeGenerator();
      }

      return new AnsiSQLMergeGenerator();
    }

    public static List<String> getSupportedTypes()
    {
      return CollectionUtil.arrayList("ansi", "db2", DBID.Firebird.getId(),
        DBID.H2.getId(), "hsqldb", DBID.MariaDB.getId(), DBID.MySQL.getId(), DBID.Oracle.getId(), "postgres", PG_CTE_TYPE, "sqlserver");
    }

    public static String getTypeForDBID(String dbid)
    {
      if (dbid == null) return "ansi";
      String type = DBID_TO_TYPE_MAP.get(dbid);
      if (type == null) return "ansi";
      return type;
    }
  }
}
