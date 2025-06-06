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
package workbench.db.oracle;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Collectors;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.ColumnIdentifier;
import workbench.db.DependencyNode;
import workbench.db.DropType;
import workbench.db.IndexDefinition;
import workbench.db.JdbcUtils;
import workbench.db.PkDefinition;
import workbench.db.TableGrantReader;
import workbench.db.TableIdentifier;
import workbench.db.TableSourceBuilder;
import workbench.db.WbConnection;
import workbench.db.sqltemplates.TemplateHandler;

import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class OracleTableSourceBuilder
  extends TableSourceBuilder
{
  public static final String REV_IDX_TYPE = "NORMAL/REV";
  public static final String INDEX_USAGE_PLACEHOLDER = "%pk_index_usage%";
  public static final String IOT_OPTIONS = "%IOT_DEFINITION%";
  private static final String A_DEFAULT = "DEFAULT";
  private String defaultTablespace;
  private String currentUser;

  public OracleTableSourceBuilder(WbConnection con)
  {
    super(con);
    if (OracleUtils.checkDefaultTablespace())
    {
      defaultTablespace = OracleUtils.getDefaultTablespace(con);
    }
    currentUser = con.getCurrentUser();
  }

  /**
   * Read additional options for the CREATE TABLE part.
   *
   * @param tbl        the table for which the options should be retrieved
   * @param columns    the table's columns
   */
  @Override
  public void readTableOptions(TableIdentifier tbl, List<ColumnIdentifier> columns)
  {
    if (tbl.getSourceOptions().isInitialized()) return;

    if (!Settings.getInstance().getBoolProperty("workbench.db.oracle.table_options.retrieve", true))
    {
      LogMgr.logInfo(new CallerInfo(){}, "Not retrieving table options for " + tbl.getTableExpression());
      tbl.getSourceOptions().setInitialized();
      return;
    }

    if (Settings.getInstance().getBoolProperty("workbench.db.oracle.retrieve_externaltables", true))
    {
      OracleExternalTableReader reader = new OracleExternalTableReader();
      CharSequence externalDef = reader.getDefinition(tbl, dbConnection);
      if (externalDef != null)
      {
        tbl.getSourceOptions().setTableOption(externalDef.toString());
        tbl.getSourceOptions().setInitialized();
        return;
      }
    }

    boolean supportsArchives = JdbcUtils.hasMinimumServerVersion(dbConnection, "11.2");
    boolean supportsCompression = JdbcUtils.hasMinimumServerVersion(dbConnection, "11.1");
    boolean supportsFlashCache = JdbcUtils.hasMinimumServerVersion(dbConnection, "11.1");

    PreparedStatement pstmt = null;
    ResultSet rs = null;

    boolean useUserTables = OracleUtils.useUserSpecificCatalogs(currentUser, tbl.getRawSchema());

    String archiveJoin = "";
    if (supportsArchives)
    {
      if (useUserTables)
      {
        archiveJoin = "  left join user_flashback_archive_tables fat on fat.table_name = atb.table_name \n";
      }
      else
      {
        archiveJoin = "  left join dba_flashback_archive_tables fat on fat.table_name = atb.table_name and fat.owner_name = atb.owner \n";
      }
    }

    String sql =
      "-- SQL Workbench/J \n" +
      "select " + OracleUtils.getCacheHint() + "atb.tablespace_name, \n" +
      "       atb.degree, \n" +
      "       atb.row_movement, \n" +
      "       atb.temporary, \n" +
      "       atb.degree, \n" +
      "       atb.cache, \n" +
      "       atb.buffer_pool, \n" +

      (supportsFlashCache ?
      "       atb.flash_cache, \n" +
      "       atb.cell_flash_cache, \n" :
      "       null as flash_cache, \n" +
      "       null as cell_flash_cache, \n") +

      "       atb.duration, \n" +
      "       atb.pct_free, \n" +
      "       atb.pct_used, \n" +
      "       atb.pct_increase, \n" +
      "       atb.logging, \n" +
      "       atb.iot_type, \n" +
      "       atb.partitioned, \n" +
      "       atb.read_only, \n" +
      (supportsArchives ?
      "       fat.flashback_archive_name, \n" :
      "       null as flashback_archive_name, \n") +
      (supportsCompression ?
      "       atb.compression, \n" +
      "       atb.compress_for \n" :
      "       null as compression,\n       null as compress_for \n") +
      "from all_tables atb \n" +
      archiveJoin +
      "where atb.table_name = ? ";

    if (useUserTables)
    {
      sql = sql.replace(" all_", " user_");
    }
    else
    {
      sql += "\n and atb.owner = ?";
    }

    String archive = null;
    boolean isPartitioned = false;
    String iotType = null;

    StringBuilder options = new StringBuilder(100);

    long start = System.currentTimeMillis();

    final CallerInfo ci = new CallerInfo(){};
    try
    {
      pstmt = OracleUtils.prepareQuery(dbConnection, sql);
      pstmt.setString(1, tbl.getRawTableName());
      if (!useUserTables)
      {
        pstmt.setString(2, tbl.getRawSchema());
      }

      LogMgr.logMetadataSql(ci, "table source options", sql, tbl.getTableName(), tbl.getSchema());

      rs = pstmt.executeQuery();
      if (rs.next())
      {

        String tempTable = rs.getString("temporary");
        boolean isTempTable = StringUtil.equalString("Y", tempTable);

        if (!isTempTable)
        {
          // you can't specify a tablespace for a temp table
          String tablespace = rs.getString("tablespace_name");
          tbl.setTablespace(tablespace);
        }

        isPartitioned = StringUtil.equalStringIgnoreCase("YES", StringUtil.trim(rs.getString("partitioned")));

        iotType = rs.getString("IOT_TYPE");

        if (StringUtil.isNotBlank(iotType))
        {
          tbl.getSourceOptions().addConfigSetting("organization", "index");
          options.append(IOT_OPTIONS);
        }

        String degree = StringUtil.trim(rs.getString("degree"));
        if (StringUtil.stringsAreNotEqual("1", degree))
        {
          if (options.length() > 0) options.append('\n');
          if (A_DEFAULT.equals(degree))
          {
            options.append("PARALLEL");
            tbl.getSourceOptions().addConfigSetting("parallel", "default");  // make this show in the XML schema report
          }
          else
          {
            options.append("PARALLEL " + degree);
            tbl.getSourceOptions().addConfigSetting("parallel", degree);
          }
        }

        String movement = StringUtil.trim(rs.getString("row_movement"));
        if (StringUtil.equalString("ENABLED", movement))
        {
          if (options.length() > 0) options.append('\n');
          options.append("ENABLE ROW MOVEMENT");
          tbl.getSourceOptions().addConfigSetting("row_movement", "enabled");
        }

        String readOnly = rs.getString("read_only");
        if ("YES".equals(readOnly))
        {
          String alter = "ALTER TABLE " + tbl.getTableExpression(dbConnection) + " READ ONLY;";
          String addSQL = tbl.getSourceOptions().getAdditionalSql();
          if (StringUtil.isBlank(addSQL))
          {
            addSQL = alter;
          }
          else
          {
            addSQL += "\n" + alter;
          }
          tbl.getSourceOptions().setAdditionalSql(addSQL);
        }

        String duration = rs.getString("duration");
        if (isTempTable)
        {
          tbl.getSourceOptions().setTypeModifier("GLOBAL TEMPORARY");
          if (options.length() > 0) options.append('\n');
          if (StringUtil.equalString("SYS$TRANSACTION", duration))
          {
            options.append("ON COMMIT DELETE ROWS");
            tbl.getSourceOptions().addConfigSetting("on_commit", "delete");
          }
          else if (StringUtil.equalString("SYS$SESSION", duration))
          {
            options.append("ON COMMIT PRESERVE ROWS");
            tbl.getSourceOptions().addConfigSetting("on_commit", "preserve");
          }
          tbl.setTablespace(null); // temporary tables can't have a tablespace
        }

        int free = rs.getInt("pct_free");
        if (!rs.wasNull() && free != 10 && StringUtil.isEmpty(iotType))
        {
          tbl.getSourceOptions().addConfigSetting("pct_free", Integer.toString(free));
          if (options.length() > 0) options.append('\n');
          options.append("PCTFREE ");
          options.append(free);
        }

        int used = rs.getInt("pct_used");
        if (!rs.wasNull() && used != 40 && StringUtil.isEmpty(iotType) && !isTempTable) // PCTUSED is not valid for IOTs
        {
          tbl.getSourceOptions().addConfigSetting("pct_used", Integer.toString(used));
          if (options.length() > 0) options.append('\n');
          options.append("PCTUSED ");
          options.append(used);
        }

        String bufferPool = StringUtil.coalesce(StringUtil.trim(rs.getString("buffer_pool")), "DEFAULT");
        String flashCache = StringUtil.coalesce(StringUtil.trim(rs.getString("flash_cache")), "DEFAULT");
        String cellFlashCache = StringUtil.coalesce(StringUtil.trim(rs.getString("cell_flash_cache")), "DEFAULT");
        String storage = null;

        if (StringUtil.stringsAreNotEqual("DEFAULT", bufferPool))
        {
          tbl.getSourceOptions().addConfigSetting("buffer_pool", bufferPool);
          storage = "STORAGE (BUFFER_POOL " + bufferPool;
        }

        if (StringUtil.stringsAreNotEqual("DEFAULT", flashCache))
        {
          tbl.getSourceOptions().addConfigSetting("flash_cache", flashCache);
          if (storage == null) storage = "STORAGE (";
          storage += " FLASH_CACHE " + flashCache;
        }

        if (StringUtil.stringsAreNotEqual("DEFAULT", cellFlashCache))
        {
          tbl.getSourceOptions().addConfigSetting("cell_flash_cache", cellFlashCache);
          if (storage == null) storage = "STORAGE (";
          storage += " CELL_FLASH_CACHE " + cellFlashCache;
        }

        if (storage != null)
        {
          if (options.length() > 0) options.append('\n');
          options.append(storage + ")");
        }

        String logging = rs.getString("logging");
        if (StringUtil.equalStringIgnoreCase("NO", logging) && !isTempTable)
        {
          tbl.getSourceOptions().addConfigSetting("logging", "nologging");
          if (options.length() > 0) options.append('\n');
          options.append("NOLOGGING");
        }

        String compression = rs.getString("compression");
        String compressType = rs.getString("compress_for");
        if (StringUtil.equalStringIgnoreCase("enabled", compression) && StringUtil.isNotBlank(compressType))
        {
          tbl.getSourceOptions().addConfigSetting("compression", compressType);
          if (options.length() > 0) options.append('\n');
          switch (compressType)
          {
            case "BASIC":
              options.append("ROW STORE COMPRESS BASIC");
              break;
            case "ADVANCED":
              options.append("ROW STORE COMPRESS ADVANCED");
              break;
          }
        }

        archive = rs.getString("flashback_archive_name");
        if (StringUtil.isNotEmpty(archive))
        {
          tbl.getSourceOptions().addConfigSetting("flashback_archive", archive);
        }
      }
    }
    catch (SQLException e)
    {
      LogMgr.logMetadataError(ci, e, "table source options", sql, tbl.getTableName(), tbl.getSchema());
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }

    long duration = System.currentTimeMillis() - start;
    LogMgr.logDebug(ci, "Retrieving table options for " + tbl.getTableName() + " took " + duration + "ms");

    String tablespace = tbl.getTablespace();
    if (OracleUtils.shouldAppendTablespace(tablespace, defaultTablespace, tbl.getRawSchema(), dbConnection.getCurrentUser()))
    {
      if (options.length() > 0)
      {
        options.append('\n');
      }
      options.append("TABLESPACE ");
      options.append(tablespace);
    }

    if (StringUtil.isNotEmpty(archive))
    {
      if (options.length() > 0) options.append('\n');
      options.append("FLASHBACK ARCHIVE ");
      options.append(dbConnection.getMetadata().quoteObjectname(archive));

      if (Settings.getInstance().getBoolProperty("workbench.db.oracle.retrieve_flashback", false))
      {
        retrieveFlashbackInfo(tbl);
      }
    }

    if (includePartitions && isPartitioned)
    {
      StringBuilder partition = getPartitionSql(tbl, "", true);
      if (partition != null && partition.length() > 0)
      {
        if (options.length() > 0 && options.charAt(options.length() - 1) != '\n')
        {
          options.append('\n');
        }
        options.append(partition);
      }
    }

    StringBuilder nested = getNestedTableSql(tbl, columns);
    if (nested != null && nested.length() > 0)
    {
      if (options.length() > 0) options.append('\n');
      options.append(nested);
    }

    StringBuilder lobOptions = retrieveLobOptions(tbl, columns);
    if (lobOptions != null)
    {
      options.insert(0, lobOptions + "\n");
    }

    tbl.getSourceOptions().setTableOption(options.toString());
    if (StringUtil.isNotEmpty(iotType))
    {
      readIOTDefinition(tbl, useUserTables);
    }

    String columnGroups = readColumnGroups(tbl);
    if (StringUtil.isNotEmpty(columnGroups))
    {
      tbl.getSourceOptions().appendAdditionalSql(columnGroups);
    }

    tbl.getSourceOptions().setInitialized();
  }

  private StringBuilder retrieveLobOptions(TableIdentifier tbl, List<ColumnIdentifier> columns)
  {
    if (!hasLobColumns(columns)) return null;

    final CallerInfo ci = new CallerInfo(){};

    if (!Settings.getInstance().getBoolProperty("workbench.db.oracle.lob_options.retrieve", true))
    {
      LogMgr.logWarning(ci,
        "Not retrieving table LOB options for " + tbl.getTableExpression() + " even though table has LOB columns. " +
        "To retrieve LOB options, set workbench.db.oracle.lob_options.retrieve to true");
      return null;
    }

    Statement stmt = null;
    ResultSet rs = null;

    String sql =
      "-- SQL Workbench/J \n" +
      "select coalesce(realcol.column_name, al.column_name) as column_name, \n" +
      "       realcol.data_type as declared_type,\n" +
      "       lobcol.data_type as internal_type,\n" +
      "       al.tablespace_name, \n" +
      "       al.chunk, \n" +
      "       al.retention, \n" +
      "       al.cache, \n" +
      "       al.logging, \n" +
      "       al.encrypt, \n" +
      "       al.compression, \n" +
      "       al.deduplication, \n" +
      "       al.in_row, \n" +
      "       al.securefile, \n" +
      "       al.retention_type, \n" +
      "       al.retention_value\n" +
      "from all_lobs al\n" +
      "  left join all_tab_cols lobcol \n" +
      "         on lobcol.column_name = al.column_name \n" +
      "        and lobcol.table_name = al.table_name\n" +
      "        and lobcol.owner = al.owner\n" +
      "  left join all_tab_cols realcol\n" +
      "         on realcol.table_name = lobcol.table_name\n" +
      "        and realcol.owner = lobcol.owner\n" +
      "        and realcol.column_id = lobcol.column_id \n" +
      "        and realcol.column_name <> lobcol.column_name \n" +
      "where al.table_name = '" +tbl.getRawTableName() + "' \n " +
      "  and al.owner = '" + tbl.getRawSchema() + "' ";

    LogMgr.logMetadataSql(ci, "LOB options", sql);

    StringBuilder result = new StringBuilder(100);
    long start = System.currentTimeMillis();

    try
    {
      boolean first = true;
      stmt = OracleUtils.createStatement(dbConnection);
      rs = stmt.executeQuery(sql);
      while (rs.next())
      {
        String column = rs.getString("column_name");
        String tbspace = rs.getString("tablespace_name");
        String declaredType = rs.getString("declared_type");
        String internalType = rs.getString("internal_type");
        long chunkSize = rs.getLong("chunk");
        long retention = rs.getLong("retention");
        long sfRetention = rs.getLong("retention_value");
        String retentionType = rs.getString("retention_type");
        if ("DEFAULT".equalsIgnoreCase(retentionType))
        {
          // Oracle stores the type "DEFAULT" in the system catalogs,
          // but DEFAULT cannot be used in a DDL statement, only AUTO
          retentionType = "AUTO";
        }
        if (rs.wasNull()) retention = -1;
        String cache = rs.getString("cache");
        String logging = rs.getString("logging");
        String encrypt = rs.getString("encrypt");
        String compress = rs.getString("compression");
        String securefile = rs.getString("securefile");
        String deduplication = rs.getString("deduplication");
        String inRow = rs.getString("in_row");

        StringBuilder colOptions = new StringBuilder(50);

        boolean isSecureFile = false;
        boolean isXML = "XMLTYPE".equals(declaredType);

        if ("YES".equals(securefile))
        {
          colOptions.append(" SECUREFILE ");
          isSecureFile = true;
        }
        else
        {
          colOptions.append(" BASICFILE ");
        }

        if (isXML)
        {
          switch (internalType)
          {
            case "BLOB":
              colOptions.append("BINARY XML ");
              break;
            case "CLOB":
              colOptions.append("CLOB ");
              break;
          }
        }

        colOptions.append("(");

        if (!StringUtil.equalStringIgnoreCase(tbspace, tbl.getTablespace()))
        {
          colOptions.append("TABLESPACE ");
          colOptions.append(tbspace);
          colOptions.append(' ');
        }

        if ("YES".equalsIgnoreCase(inRow))
        {
          colOptions.append("ENABLE STORAGE IN ROW");
        }
        else
        {
          colOptions.append("DISABLE STORAGE IN ROW");
        }

        if (chunkSize != 8192)
        {
          colOptions.append(" CHUNK ");
          colOptions.append(chunkSize);
        }

        if (isSecureFile)
        {
          colOptions.append(" RETENTION ");
          colOptions.append(retentionType);
          if ("MIN".equalsIgnoreCase(retentionType))
          {
            colOptions.append(' ');
            colOptions.append(sfRetention);
          }

          if ("NO".equals(compress))
          {
            colOptions.append(" NOCOMPRESS");
          }
          else
          {
            colOptions.append(" COMPRESS ");
            colOptions.append(compress);
          }

          if ("LOB".equals(deduplication))
          {
            colOptions.append(" DEDUPLICATE");
          }

          if ("YES".equals(encrypt))
          {
            colOptions.append(" ENCRYPT");
          }
        }

        switch (cache)
        {
          case "NO":
            colOptions.append(" NOCACHE");
            break;
          case "YES":
            colOptions.append(" CACHE");
            break;
          case "CACHEREADS":
            colOptions.append(" CACHE READS");
            break;
        }
        if ("NO".equals(logging))
        {
          colOptions.append(" NOLOGGING");
        }

        colOptions.append(')');
        if (!first) result.append("\n");
        String key;
        if (isXML)
        {
          key = "XMLTYPE " + column;
        }
        else
        {
          key = "LOB (" + column + ")";
        }
        result.append(key);
        result.append(" STORE AS");
        result.append(colOptions);
        tbl.getSourceOptions().addConfigSetting(key, colOptions.toString());
        first = false;
      }
    }
    catch (Exception ex)
    {
      LogMgr.logMetadataError(ci, ex, "LOB options", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }

    long duration = System.currentTimeMillis() - start;
    LogMgr.logDebug(ci, "Retrieving LOB options for " + tbl.getTableExpression() + " took " + duration + "ms");

    return result;
  }

  private boolean hasLobColumns(List<ColumnIdentifier> columns)
  {
    if (CollectionUtil.isEmpty(columns)) return false;
    for (ColumnIdentifier column : columns)
    {
      if (column.getDbmsType().endsWith("LOB")) return true;
      if (column.getDbmsType().equalsIgnoreCase("xmltype")) return true;
    }
    return false;
  }

  private void retrieveFlashbackInfo(TableIdentifier tbl)
  {
    PreparedStatement pstmt = null;
    ResultSet rs = null;


    // Using the table's tablespace for the flashback archive is not correct,
    // but there is no way to retrieve that information as far as I can tell
    // (not even SQL Developer displays the flashback archive information!)
    String sql =
      "-- SQL Workbench/J \n" +
      "select fa.flashback_archive_name,   \n" +
      "       fa.retention_in_days, \n" +
      "       tbl.tablespace_name \n" +
      "from dba_flashback_archive fa  \n" +  // this should be user_flashback_archive but that does not contain any information!
      "  join user_flashback_archive_tables fat  \n" +
      "    on fat.flashback_archive_name = fa.flashback_archive_name  \n" +
      "  join all_tables tbl  \n" +
      "    on tbl.owner = fat.owner_name  \n" +
      "   and tbl.table_name = fat.table_name \n" +
      "where fat.owner_name = ? \n" +
      "  and fat.table_name = ? ";

    final CallerInfo ci = new CallerInfo(){};
    try
    {
      pstmt = OracleUtils.prepareQuery(dbConnection, sql);
      pstmt.setString(1, tbl.getSchema());
      pstmt.setString(2, tbl.getTableName());
      LogMgr.logMetadataSql(ci, "flashback archive information", sql, tbl.getSchema(), tbl.getTableName());

      rs = pstmt.executeQuery();
      if (rs.next())
      {
        String archiveName = rs.getString(1);
        int days = rs.getInt(2);
        String tbSpace = rs.getString(3);
        String rentention = "RETENTION ";
        if (days < 30)
        {
          rentention += Integer.toString(days) + " DAY";
        }
        else if (days < 365)
        {
          rentention += Integer.toString(days / 30) + " MONTH";
        }
        else
        {
          rentention += Integer.toString(days / 365) + " YEAR";
        }
        String create =
          "\n-- definition of flasback archive \n" +
          "CREATE FLASHBACK ARCHIVE " + archiveName + "\n" +
          "  TABLESPACE " + tbSpace +"\n  " + rentention + ";\n";

        tbl.getSourceOptions().setAdditionalSql(create);
      }
    }
    catch (Exception ex)
    {
      LogMgr.logMetadataError(ci, ex, "flashback archive information", sql, tbl.getSchema(), tbl.getTableName());
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }
  }

  StringBuilder getPartitionSql(TableIdentifier table, String indent, boolean includeTablespace)
  {
    StringBuilder result = new StringBuilder(100);
    if (Settings.getInstance().getBoolProperty("workbench.db.oracle.retrieve_partitions", true))
    {
      try
      {
        OracleTablePartition reader = new OracleTablePartition(this.dbConnection);
        reader.retrieve(table, dbConnection);
        String sql = reader.getSourceForTableDefinition(indent, includeTablespace);
        if (sql != null)
        {
          result.append(sql);
        }
      }
      catch (SQLException sql)
      {
        LogMgr.logError(new CallerInfo(){}, "Error retrieving partitions for " + table.getFullyQualifiedName(dbConnection), sql);
      }
    }
    return result;
  }

  private StringBuilder getNestedTableSql(TableIdentifier tbl, List<ColumnIdentifier> columns)
  {
    StringBuilder options = new StringBuilder();

    // retrieving the nested table options requires another query to the Oracle
    // catalogs which is always horribly slow. In order to prevent this,
    // we first check if the table only contains "standard" data types.
    // as the number of tables containing nested tables is probably quite small
    // we prevent firing additional queries by checking if at least one column
    // might be a nested table
    boolean hasUserType = false;
    for (ColumnIdentifier col : columns)
    {
      String type = SqlUtil.getPlainTypeName(col.getDbmsType());
      if (!OracleUtils.STANDARD_TYPES.contains(type))
      {
        hasUserType = true;
        break;
      }
    }

    if (!hasUserType) return null;

    String sql =
      "-- SQL Workbench/J \n" +
      "SELECT 'NESTED TABLE '||parent_table_column||' STORE AS '||table_name \n" +
      "FROM all_nested_tables \n" +
      "WHERE parent_table_name = ? \n" +
      "  AND owner = ?";

    PreparedStatement pstmt = null;
    ResultSet rs = null;

    final CallerInfo ci = new CallerInfo(){};
    try
    {
      pstmt = OracleUtils.prepareQuery(dbConnection, sql);
      pstmt.setString(1, tbl.getTableName());
      pstmt.setString(2, tbl.getSchema());
      LogMgr.logMetadataSql(ci, "nested table", sql, tbl.getTableName(), tbl.getSchema());

      rs = pstmt.executeQuery();
      while (rs.next())
      {
        String option = rs.getString(1);
        if (options.length() > 0)
        {
          options.append('\n');
        }
        options.append(option);
      }
    }
    catch (SQLException e)
    {
      LogMgr.logMetadataError(ci, e, "nested table", sql, tbl.getTableName(), tbl.getSchema());
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }
    return options;
  }

  /**
   * Generate the SQL to create the primary key for the table.
   *
   * If the primary key is supported by an index that does not have the same name
   * as the primary key, it is assumed that the index is defined as an additional
   * option to the "ADD CONSTRAINT" statement
   *
   * @param table        the table for which the PK source should be created
   * @param def          the definition of the primary key of the table
   * @param forInlineUse if true, the SQL should be generated so it can be used inside a CREATE TABLE
   *                     otherwise an ALTER TABLE will be created
   * <p>
   * @return the SQL to re-create the primary key
   */
  @Override
  public CharSequence getPkSource(TableIdentifier table, PkDefinition def, boolean forInlineUse, boolean useFQN)
  {
    if (OracleUtils.getUseOracleDBMSMeta(OracleUtils.DbmsMetadataTypes.constraint))
    {
      try
      {
        String pk = DbmsMetadata.getDDL(dbConnection, "CONSTRAINT", def.getPkName(), table.getSchema());
        if (pk != null)
        {
          pk += "\n";
        }
        return pk;
      }
      catch (Exception ex)
      {
        // already logged, fall back to built-in retrieval
      }
    }

    OracleIndexReader reader = (OracleIndexReader)dbConnection.getMetadata().getIndexReader();
    String sql = super.getPkSource(table, def, forInlineUse, useFQN).toString();
    if (StringUtil.isEmpty(sql)) return sql;

    PkDefinition pk = def == null ? table.getPrimaryKey() : def;

    IndexDefinition pkIdx = pk.getPkIndexDefinition();

    // The name used by the index is not necessarily the same as the one used by the constraint.
    String pkIndexName = pk.getPkIndexName();

    if (pkIdx == null)
    {
      pkIdx = getIndexDefinition(table, pkIndexName);
      pk.setPkIndexDefinition(pkIdx);
    }

    String pkStatusVerb = "";
    if (pk.isDisabled())
    {
      pkStatusVerb = " DISABLE";
    }

    boolean pkIdxReverse = pkIdx != null && REV_IDX_TYPE.equals(pkIdx.getIndexType());

    if (pkIdx == null)
    {
      sql = sql.replace(" " + INDEX_USAGE_PLACEHOLDER, pkStatusVerb);
    }
    else if (pkIndexName.equals(pk.getPkName()) && !pkIdx.isPartitioned())
    {
      if (OracleUtils.shouldAppendTablespace(pkIdx.getTablespace(), defaultTablespace, pkIdx.getSchema(), dbConnection.getCurrentUser()))
      {
        String idx = "USING INDEX";
        if (pkIdxReverse)
        {
          idx += " REVERSE";
        }
        sql = sql.replace(INDEX_USAGE_PLACEHOLDER, "\n   " + idx + " TABLESPACE " + pkIdx.getTablespace());
      }
      else
      {
        sql = sql.replace(" " + INDEX_USAGE_PLACEHOLDER, "");
      }
    }
    else
    {
      String indexSql = reader.getIndexSource(table, pkIdx).toString();
      if (pkIdxReverse)
      {
        indexSql = indexSql.replace("\n    REVERSE", " REVERSE"); // cosmetic cleanup
      }
      StringBuilder using = new StringBuilder(indexSql.length() + 20);
      using.append("\n   USING INDEX (\n     ");
      using.append(SqlUtil.trimSemicolon(indexSql).trim().replace("\n", "\n  "));
      using.append("\n   )");
      using.append(pkStatusVerb);
      sql = sql.replace(INDEX_USAGE_PLACEHOLDER, using);
    }

    return sql;
  }

  private IndexDefinition getIndexDefinition(TableIdentifier table, String indexName)
  {
    OracleIndexReader reader = (OracleIndexReader)dbConnection.getMetadata().getIndexReader();
    IndexDefinition index;
    try
    {
      index = reader.getIndexDefinition(table, indexName, null);
    }
    catch (SQLException sql)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not retrieve index", sql);
      index = null;
    }
    return index;
  }

  private void readIOTDefinition(TableIdentifier tbl, boolean useUserTables)
  {

    String sql =
      "-- SQL Workbench/J \n" +
      "select " + OracleUtils.getCacheHint() + "coalesce(atb.tablespace_name, pt.def_tablespace_name) as tablespace_name, \n" +
      "       iot.tablespace_name as iot_overflow, \n" +
      "       iot.table_name as overflow_table, \n" +
      "       ac.index_name as pk_index_name, \n" +
      "       ai.compression as index_compression, \n" +
      "       ai.prefix_length, \n" +
      "       ai.tablespace_name as index_tablespace \n" +
      "from all_tables atb \n" +
      "  left join all_tables iot on atb.table_name = iot.iot_name " + (useUserTables ? "\n" : " and atb.owner = iot.owner \n")  +
      "  left join all_constraints ac on ac.table_name = atb.table_name and ac.constraint_type = 'P' " + (useUserTables ? "\n" : " and ac.owner = atb.owner \n") +
      "  left join all_indexes ai on ai.table_name = ac.table_name and ai.index_name = ac.index_name " + (useUserTables ? "\n" : " and ai.owner = coalesce(ac.index_owner, ac.owner) \n") +
      "  left join all_part_tables pt on pt.table_name = iot.table_name " + (useUserTables ? "\n" : " and pt.owner = iot.owner \n") +
      "where atb.table_name = ? ";

    if (useUserTables)
    {
      sql = sql.replace(" all_", " user_");
    }
    else
    {
      sql += "\n and atb.owner = ?";
    }

    LogMgr.logMetadataSql(new CallerInfo(){}, "IOT information", sql, tbl.getRawTableName(), tbl.getRawSchema());

    StringBuilder options = new StringBuilder(100);

    String included = getIOTIncludedColumn(tbl.getSchema(), tbl.getTableName(), tbl.getPrimaryKey().getPkIndexName());

    long start = System.currentTimeMillis();

    PreparedStatement pstmt = null;
    ResultSet rs = null;

    try
    {
      pstmt = OracleUtils.prepareQuery(dbConnection, sql);
      pstmt.setString(1, tbl.getRawTableName());
      if (!useUserTables)
      {
        pstmt.setString(2, tbl.getRawSchema());
      }

      rs = pstmt.executeQuery();
      if (!rs.next()) return;

      options.append("ORGANIZATION INDEX");
      String overflow = rs.getString("IOT_OVERFLOW");
      tbl.getSourceOptions().addConfigSetting("organization", "index");

      String compression = rs.getString("index_compression");
      if ("ENABLED".equalsIgnoreCase(compression))
      {
        String cols = rs.getString("prefix_length");
        if (StringUtil.isNotBlank(cols))
        {
          options.append("\nCOMPRESS ");
          options.append(cols);
        }
      }
      if (included != null)
      {
        options.append("\nINCLUDING ");
        options.append(included);
        if (StringUtil.isEmpty(overflow))
        {
          options.append(" OVERFLOW");
        }
        tbl.getSourceOptions().addConfigSetting("iot_included_cols", included);
      }

      String idxTbs = rs.getString("INDEX_TABLESPACE");
      if (StringUtil.isNotEmpty(idxTbs))
      {
        options.append("\nTABLESPACE ").append(idxTbs);
        tbl.getSourceOptions().addConfigSetting("index_tablespace", idxTbs);
        tbl.setTablespace(null);
      }

      if (StringUtil.isNotBlank(overflow))
      {
        options.append("\nOVERFLOW TABLESPACE ");
        options.append(overflow);
        tbl.getSourceOptions().addConfigSetting("overflow_tablespace", overflow);
      }
      tbl.setUseInlinePK(true); // you cannot define a IOT without a PK therefor the PK has to be inline!
    }
    catch (SQLException ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "IOT information", sql, tbl.getRawTableName(), tbl.getRawSchema());
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }
    String tableOptions = tbl.getSourceOptions().getTableOption();
    String newOptions = tableOptions.replace(IOT_OPTIONS, options.toString());
    tbl.getSourceOptions().setTableOption(newOptions);

    long duration = System.currentTimeMillis() - start;
    LogMgr.logDebug(new CallerInfo(){}, "Retrieving IOT information for " + tbl.getTableExpression() + " took " + duration + "ms");
  }

  private String getIOTIncludedColumn(String owner, String tableName, String pkIndexName)
  {
    PreparedStatement pstmt = null;
    ResultSet rs = null;
    String sql =
      "-- SQL Workbench/J \n" +
      "select column_name \n" +
      "from all_tab_columns \n" +
      "where column_name not in (select column_name \n" +
      "                          from all_ind_columns  \n" +
      "                          where index_name = ? \n" +
      "                            and index_owner = ?) \n" +
      "  and table_name = ? \n" +
      "  and owner = ? \n" +
      "order by column_id \n";

    final CallerInfo ci = new CallerInfo(){};
    long start = System.currentTimeMillis();
    String column = null;
    try
    {
      pstmt = OracleUtils.prepareQuery(dbConnection, sql);
      pstmt.setString(1, pkIndexName);
      pstmt.setString(2, owner);
      pstmt.setString(3, tableName);
      pstmt.setString(4, owner);

      LogMgr.logMetadataSql(ci, "IOT included columns", sql, pkIndexName, owner, tableName, owner);

      rs = pstmt.executeQuery();
      if (rs.next())
      {
        column = rs.getString(1);
      }
    }
    catch (Exception ex)
    {
      LogMgr.logMetadataError(ci, ex, "IOT included columns", sql, pkIndexName, owner, tableName, owner);
      column = null;
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }
    long duration = System.currentTimeMillis() - start;
    LogMgr.logDebug(ci, "Retrieving included columns for IOT " + owner + "." + tableName + " took " + duration + "ms");
    return column;
  }

  @Override
  protected String getAdditionalFkSql(TableIdentifier table, DependencyNode fk, String template)
  {
    if (fk.isValidated())
    {
      template = TemplateHandler.removePlaceholder(template, "%validate%", false);
    }
    else
    {
      template = TemplateHandler.replacePlaceholder(template, "%validate%", "NOVALIDATE", true);
    }

    if (fk.isEnabled())
    {
      template = TemplateHandler.removePlaceholder(template, "%enabled%", false);
    }
    else
    {
      template = TemplateHandler.replacePlaceholder(template, "%enabled%", "DISABLE", true);
    }
    return template;
  }

  @Override
  public String getNativeTableSource(TableIdentifier table, DropType dropType)
  {
    if (OracleUtils.getUseOracleDBMSMeta(OracleUtils.DbmsMetadataTypes.table))
    {
      try
      {
        boolean inlineFK = dbConnection.getDbSettings().createInlineFKConstraints();
        boolean inlinePK = dbConnection.getDbSettings().createInlinePKConstraints();
        String ddl = DbmsMetadata.getTableDDL(dbConnection, table.getTableName(), table.getSchema(), inlinePK, inlineFK) + "\n";
        if (!inlinePK && table.getPrimaryKeyName() != null)
        {
          String pk = DbmsMetadata.getDDL(dbConnection, "CONSTRAINT", table.getPrimaryKeyName(), table.getSchema());
          if (StringUtil.isNotEmpty(pk))
          {
            ddl += "\n\n" + pk + "\n";
          }
        }
        return ddl;
      }
      catch (SQLException ex)
      {
        // already logged, fall back to built-in retrieval
      }
    }
    return super.getNativeTableSource(table, dropType);
  }

  @Override
  public StringBuilder getFkSource(TableIdentifier table)
  {
    if (OracleUtils.getUseOracleDBMSMeta(OracleUtils.DbmsMetadataTypes.constraint))
    {
      String fk = DbmsMetadata.getDependentDDL(dbConnection, "REF_CONSTRAINT", table.getTableName(), table.getSchema());
      if (fk != null)
      {
        StringBuilder result = new StringBuilder(fk.length());
        result.append(fk);
        result.append('\n');
        return result;
      }
      return null;
    }

    return super.getFkSource(table);
  }

  @Override
  public StringBuilder getFkSource(TableIdentifier table, List<DependencyNode> fkList, boolean forInlineUse)
  {
    if (!forInlineUse && OracleUtils.getUseOracleDBMSMeta(OracleUtils.DbmsMetadataTypes.constraint))
    {
      return getFkSource(table);
    }
    return super.getFkSource(table, fkList, forInlineUse);
  }

  @Override
  public String getTableSource(TableIdentifier table, DropType dropType, boolean includeFk, boolean includeGrants)
    throws SQLException
  {
    if (OracleUtils.getUseOracleDBMSMeta(OracleUtils.DbmsMetadataTypes.table))
    {
      return getCompleteTableSource(table, dropType, includeFk, includeGrants);
    }
    return super.getTableSource(table, dropType, includeFk, includeGrants);
  }

  public String getCompleteTableSource(TableIdentifier table, DropType dropType, boolean includeFk, boolean includeGrants)
    throws SQLException
  {
    String result = "";
    if (dropType != DropType.none)
    {
      result = generateDrop(table, dropType).toString() + "\n\n";
    }

    boolean inlinePK = dbConnection.getDbSettings().createInlinePKConstraints();
    boolean inlineFK = dbConnection.getDbSettings().createInlineFKConstraints();

    if (!inlinePK && table.getPrimaryKey() == null)
    {
      PkDefinition pk = getIndexReader().getPrimaryKey(table);
      table.setPrimaryKey(pk);
    }

    result += DbmsMetadata.getTableDDL(dbConnection, table.getTableName(), table.getSchema(), dbConnection.getDbSettings().createInlinePKConstraints(), inlineFK);

    if (table.getPrimaryKeyName() != null)
    {
      String pk = DbmsMetadata.getDDL(dbConnection, "CONSTRAINT", table.getPrimaryKeyName(), table.getSchema());
      if (StringUtil.isNotEmpty(pk))
      {
        result += "\n\n" + pk;
      }
    }

    if (includeFk && !inlineFK)
    {
      CharSequence fk = getFkSource(table);
      if (StringUtil.isNotEmpty(fk))
      {
        result += "\n\n" + fk;
      }
    }

    CharSequence indexDDL = null;
    if (OracleUtils.getUseOracleDBMSMeta(OracleUtils.DbmsMetadataTypes.index))
    {
      indexDDL = DbmsMetadata.getDependentDDL(dbConnection, "INDEX", table.getTableName(), table.getSchema());
    }
    else
    {
      List<IndexDefinition> indexDef = getIndexReader().getTableIndexList(table, true);
      List<IndexDefinition> toCreate = indexDef.stream().filter(idx -> !idx.isPrimaryKeyIndex()).collect(Collectors.toList());
      indexDDL = getIndexReader().getIndexSource(table, toCreate);
    }

    if (StringUtil.isNotEmpty(indexDDL))
    {
      result += "\n\n" + indexDDL;
    }

    String columnGroups = readColumnGroups(table);
    if (StringUtil.isNotEmpty(columnGroups))
    {
      result += "\n\n" + columnGroups;
    }

    String comments = DbmsMetadata.getDependentDDL(dbConnection, "COMMENT", table.getTableName(), table.getSchema());
    if (StringUtil.isNotEmpty(comments))
    {
      result += "\n\n" + comments;
    }

    if (includeGrants)
    {
      TableGrantReader reader = new OracleTableGrantReader();
      StringBuilder grants = reader.getTableGrantSource(dbConnection, table);
      if (StringUtil.isNotEmpty(grants))
      {
        result += "\n\n" + grants;
      }
    }
    return result;
  }

  private String readColumnGroups(TableIdentifier table)
  {
    if (!JdbcUtils.hasMinimumServerVersion(dbConnection, "11.0"))
    {
      return "";
    }

    String sql =
      "select extension \n" +
      "from all_stat_extensions \n" +
      "where owner = ? \n" +
      "  and table_name = ?";

    if (OracleUtils.showSystemGeneratedExtendedStats() == false)
    {
      sql += "\n  and creator <> 'SYSTEM'";
    }

    StringBuilder result = new StringBuilder(500);

    final String userName;
    if (currentUser.equalsIgnoreCase(table.getRawSchema()))
    {
      userName = "NULL";
    }
    else
    {
      userName = "'" + table.getRawSchema() + "'";
    }

    final String tname = dbConnection.getMetadata().quoteObjectname(table.getTableName());
    final String cmd = "select dbms_stats.create_extended_stats(ownname => %s, tabname => '%s', extension => '%s') from dual;\n";

    LogMgr.logMetadataSql(new CallerInfo(){}, "extended stats", sql, table.getRawSchema(), table.getRawTableName());

    PreparedStatement pstmt = null;
    ResultSet rs = null;
    try
    {
      pstmt = OracleUtils.prepareQuery(dbConnection, sql);
      pstmt.setString(1, table.getRawSchema());
      pstmt.setString(2, table.getRawTableName());

      rs = pstmt.executeQuery();

      while (rs.next())
      {
        String expression = rs.getString(1);
        if (StringUtil.isNotEmpty(expression))
        {
          String stmt = String.format(cmd, userName, tname, SqlUtil.escapeQuotes(expression));
          result.append(stmt);
        }
      }
      if (result.length() > 0)
      {
        result.append('\n');
      }
    }
    catch (Exception ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "extended stats", sql, table.getRawSchema(), table.getRawTableName());
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }
    return result.toString();
  }
}
