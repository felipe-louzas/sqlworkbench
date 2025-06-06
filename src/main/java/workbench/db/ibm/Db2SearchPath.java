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
package workbench.db.ibm;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.DbSearchPath;
import workbench.db.JdbcUtils;
import workbench.db.WbConnection;

import workbench.util.StringUtil;

/**
 * An implementation of the DbSearchPath interface for DB2 on iSeries.
 * @author Thomas Kellerer
 */
public class Db2SearchPath
  implements DbSearchPath
{
  /**
   * Returns the current search path defined in the session (or the user).
   * <br/>
   * @param con the connection for which the search path should be retrieved
   * @return the list of schemas (libraries) in the search path.
   */
  @Override
  public List<String> getSearchPath(WbConnection con, String defaultSchema)
  {
    if (con == null) return Collections.emptyList();

    if (defaultSchema != null)
    {
      return Collections.singletonList(con.getMetadata().adjustSchemaNameCase(defaultSchema));
    }

    List<String> result = new ArrayList<>();

    ResultSet rs = null;
    Statement stmt = null;
    String sql = getSQL(con);
    LogMgr.logDebug(new CallerInfo(){}, "Query to retrieve search path: " + sql);

    try
    {
      stmt = con.createStatementForQuery();

      rs = stmt.executeQuery(sql);
      while (rs.next())
      {
        String row = rs.getString(1);
        if (StringUtil.isNotBlank(row))
        {
          result.add(row.trim());
        }
      }
    }
    catch (SQLException ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not read search path", ex);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }

    List<String> searchPath = parseResult(result);

    LogMgr.logDebug(new CallerInfo(){}, "Using path: " + searchPath.toString());
    return searchPath;
  }

  private String getSQL(WbConnection con)
  {
    String sql = Settings.getInstance().getProperty("workbench.db." + con.getDbId() + ".searchpath.sql", null);
    if (sql == null)
    {
      StringBuilder result = new StringBuilder(50);
      result.append("select schema_name from QSYS2");
      result.append(con.getMetadata().getSchemaSeparator());
      result.append("LIBRARY_LIST_INFO order by ordinal_position");
      sql = result.toString();
    }
    return sql;
  }

  List<String> parseResult(List<String> entries)
  {
    List<String> searchPath = new ArrayList<>(entries.size());
    for (String line : entries)
    {
      if (line.charAt(0) != '*')
      {
        searchPath.addAll(StringUtil.stringToList(line, ",", true, true, false, false));
      }
    }
    return searchPath;
  }

  @Override
  public boolean isRealSearchPath()
  {
    return true;
  }

}
