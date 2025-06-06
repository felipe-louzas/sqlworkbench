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
package workbench.db.mssql;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.DbMetadata;
import workbench.db.DbObjectFinder;
import workbench.db.JdbcUtils;
import workbench.db.SynonymReader;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * Retrieve synonyms and their definition from SQL Server
 *
 * @author Thomas Kellerer
 */
public class SqlServerSynonymReader
  implements SynonymReader
{
  private DbMetadata meta;

  private final String baseSql =
    "SELECT db_name(), \n" +
    "       sc.name as schema_name, \n" +
    "       syn.name as synonym_name, \n" +
    "       syn.base_object_name \n" +
    "FROM sys.synonyms syn with (nolock) \n" +
    "  JOIN sys.schemas sc with (nolock) ON syn.schema_id = sc.schema_id \n";

  public SqlServerSynonymReader(DbMetadata dbMeta)
  {
    this.meta = dbMeta;
  }

  public static boolean supportsSynonyms(WbConnection con)
  {
    return SqlServerUtil.isSqlServer2005(con);
  }

  @Override
  public List<TableIdentifier> getSynonymList(WbConnection con, String catalog, String schemaPattern, String namePattern)
    throws SQLException
  {
    List<TableIdentifier> result = new ArrayList<>();
    StringBuilder sql = new StringBuilder(baseSql.length() + 50);
    sql.append(baseSql);
    int schemaIndex = -1;
    int nameIndex = -1;

    boolean whereAdded = false;

    if (StringUtil.isNotBlank(schemaPattern))
    {
      sql.append("WHERE sc.name = ?");
      whereAdded = true;
      schemaIndex = 1;
    }

    if (StringUtil.isNotBlank(namePattern))
    {
      if (whereAdded)
      {
        sql.append(" AND ");
      }
      else
      {
        sql.append("WHERE ");
      }
      if (namePattern.indexOf('%') > -1)
      {
        sql.append(" syn.name LIKE ? ");
        namePattern = SqlUtil.escapeUnderscore(namePattern, con);
        SqlUtil.appendEscapeClause(sql, con, namePattern);
      }
      else
      {
        sql.append(" syn.name = ? ");
      }

      if (schemaIndex == 1) nameIndex = 2;
      else nameIndex = 1;
    }

    LogMgr.logMetadataSql(new CallerInfo(){}, "synonym list", sql, schemaPattern, namePattern);

    PreparedStatement stmt = null;
    ResultSet rs = null;
    try
    {
      stmt = con.getSqlConnection().prepareStatement(sql.toString());
      if (schemaIndex != -1) stmt.setString(schemaIndex, schemaPattern);
      if (nameIndex != -1) stmt.setString(nameIndex, namePattern);

      rs = stmt.executeQuery();

      while (rs.next())
      {
        String synCat = rs.getString(1);
        String synSchema = rs.getString(2);
        String synName = rs.getString(3);

        if (synName != null)
        {
          TableIdentifier tbl = new TableIdentifier(synCat, synSchema, synName);
          tbl.setType(SYN_TYPE_NAME);
          tbl.setNeverAdjustCase(true);
          result.add(tbl);
        }
      }
    }
    catch (SQLException ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "synonym list", sql, schemaPattern, namePattern);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }

    return result;
  }

  @Override
  public TableIdentifier getSynonymTable(WbConnection con, String catalog, String schema, String synonymName)
    throws SQLException
  {
    String sql = baseSql +
      "WHERE syn.name = ? \n" +
      "  AND sc.name = ?";

    LogMgr.logMetadataSql(new CallerInfo(){}, "synonym table", sql);

    PreparedStatement stmt = con.getSqlConnection().prepareStatement(sql);

    stmt.setString(1, synonymName);
    stmt.setString(2, schema);

    ResultSet rs = stmt.executeQuery();
    TableIdentifier result = null;

    try
    {
      if (rs.next())
      {
        String targetCat = rs.getString(1);
        String targetSchema = rs.getString(2);
        String targetTable = rs.getString(4);

        if (targetTable != null)
        {
          result = new TableIdentifier(targetCat, targetSchema, meta.removeQuotes(targetTable));
          result.setNeverAdjustCase(false);
        }
      }
    }
    catch (SQLException ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "synonym table", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    if (result == null) return null;

    result.setSchema(schema);
    DbObjectFinder finder = new DbObjectFinder(meta);
    TableIdentifier tbl = finder.findObject(result);

    return tbl;
  }

}
