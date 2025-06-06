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
package workbench.db.derby;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.JdbcUtils;
import workbench.db.SynonymReader;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.util.StringUtil;

/**
 * Retrieve synonyms and their definition from a Derby database.
 *
 * @author Thomas Kellerer
 */
public class DerbySynonymReader
  implements SynonymReader
{
  public DerbySynonymReader()
  {
  }

  @Override
  public List<TableIdentifier> getSynonymList(WbConnection con, String catalog, String schema, String namePattern)
    throws SQLException
  {
    List<TableIdentifier> result = new ArrayList<>();
    String sql =
      "SELECT s.schemaname, a.alias \n" +
      "FROM sys.sysaliases a \n" +
      "  JOIN sys.sysschemas s ON a.schemaid = s.schemaid \n" +
      "WHERE a.aliastype = 'S'\n" +
      "  AND s.schemaname = ? \n";

    if (StringUtil.isNotBlank(namePattern))
    {
      sql += " AND a.alias LIKE ?";
    }

    LogMgr.logMetadataSql(new CallerInfo(){}, "synonyms", sql);

    PreparedStatement stmt = null;
    ResultSet rs = null;
    try
    {
      stmt = con.getSqlConnection().prepareStatement(sql);
      stmt.setString(1, schema);
      if (StringUtil.isNotBlank(namePattern)) stmt.setString(2, namePattern);

      rs = stmt.executeQuery();
      while (rs.next())
      {
        String synSchema = rs.getString(1);
        String alias = rs.getString(2);
        if (!rs.wasNull())
        {
          TableIdentifier tbl = new TableIdentifier(null, synSchema, alias);
          tbl.setType(SYN_TYPE_NAME);
          tbl.setNeverAdjustCase(true);
          result.add(tbl);
        }
      }
    }
    catch (SQLException ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "synonyms", sql);
      throw ex;
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }

    return result;
  }

  @Override
  public TableIdentifier getSynonymTable(WbConnection con, String catalog, String owner, String synonym)
    throws SQLException
  {
    String sql =
      "select a.aliasinfo \n" +
      "from sys.sysaliases a" +
      "  join sys.sysschemas s on a.schemaid = s.schemaid \n" +
      "where a.alias = ?" +
      "  and s.schemaname = ?";

    LogMgr.logMetadataSql(new CallerInfo(){}, "synonym table", sql);

    PreparedStatement stmt = con.getSqlConnection().prepareStatement(sql);
    stmt.setString(1, synonym);
    stmt.setString(2, owner);
    ResultSet rs = stmt.executeQuery();
    String table = null;
    TableIdentifier result = null;
    try
    {
      if (rs.next())
      {
        table = rs.getString(1);
        if (table != null)
        {
          result = new TableIdentifier(table);
        }
      }
    }
    catch (SQLException ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "synonym table", sql);
      throw ex;
    }
    finally
    {
      JdbcUtils.closeAll(rs,stmt);
    }

    if (result != null)
    {
      String type = con.getMetadata().getObjectType(result);
      result.setType(type);
    }

    return result;
  }

  @Override
  public boolean supportsReplace(WbConnection con)
  {
    return false;
  }

}
