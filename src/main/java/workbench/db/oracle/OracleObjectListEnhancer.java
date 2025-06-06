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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.DbMetadata;
import workbench.db.JdbcUtils;
import workbench.db.ObjectListDataStore;
import workbench.db.ObjectListEnhancer;
import workbench.db.WbConnection;

import workbench.util.StringUtil;

/**
 * A class to "cleanup" the reported table type for MATERIALZED VIEWS.
 *
 * The JDBC driver returns MVIEWS with the type "TABLE" which is not useful when displaying
 * objects in the DbExplorer.
 *
 * This class processes the retrieved objects and updates the object type accordingly
 *
 * @author Thomas Kellerer
 */
public class OracleObjectListEnhancer
  implements ObjectListEnhancer
{
  private boolean canRetrieveSnapshots = true;

  @Override
  public void updateObjectList(WbConnection con, ObjectListDataStore result,
                               String catalogPattern, String schema, String objectNamePattern, String[] types)
  {
    if (con == null) return;

    boolean checkSnapshots = Settings.getInstance().getBoolProperty("workbench.db.oracle.detectsnapshots", true) && DbMetadata.typeIncluded("TABLE", types);
    if (!checkSnapshots) return;

    Map<String, String> snapshots = getSnapshots(con, schema);
    for (int row=0; row < result.getRowCount(); row++)
    {
      String owner = result.getSchema(row);
      String name =  result.getObjectName(row);
      String fqName = owner + "." + name;
      if (snapshots.containsKey(fqName))
      {
        result.setType(row, DbMetadata.MVIEW_NAME);
        result.setRemarks(row, snapshots.get(fqName));
      }
    }
  }

  /**
   * Returns a Set with Strings identifying available Snapshots (materialized views).
   *
   * The names will be returned as owner.tablename
   * In case the retrieve throws an error, this method will return
   * an empty set in subsequent calls.
   */
  public Map<String, String> getSnapshots(WbConnection connection, String schema)
  {
    if (!canRetrieveSnapshots || connection == null)
    {
      return Collections.emptyMap();
    }

    String defaultPrefix = Settings.getInstance().getProperty("workbench.db.oracle.default.mv.comment", "snapshot table for snapshot");

    if (StringUtil.isBlank(defaultPrefix))
    {
      defaultPrefix = null;
    }
    Map<String, String> result = new HashMap<>();

    String sql;

    if (OracleUtils.getRemarksReporting(connection))
    {
      sql =
        "-- SQL Workbench/J \n" +
        "SELECT mv.owner, mv.mview_name, \n" +
        "       c.comments\n" +
        "FROM all_mviews mv\n" +
        "  left join all_mview_comments c on c.owner = mv.owner and c.mview_name = mv.mview_name \n";
    }
    else
    {
      sql =
        "-- SQL Workbench/J \n" +
        "SELECT mv.owner, mv.mview_name, \n " +
        "      null as comments  \n" +
        "FROM all_mviews mv \n";
    }

    if (schema != null)
    {
      sql += " WHERE mv.owner = ?";
    }

    PreparedStatement stmt = null;
    ResultSet rs = null;

    LogMgr.logMetadataSql(new CallerInfo(){}, "snapshots", sql, schema);
    try
    {
      stmt = OracleUtils.prepareQuery(connection, sql);
      if (schema != null)
      {
        stmt.setString(1, schema);
      }
      rs = stmt.executeQuery();
      while (rs.next())
      {
        String owner = rs.getString(1);
        String name = rs.getString(2);
        String comment = rs.getString(3);
        if (defaultPrefix != null && comment != null && comment.startsWith(defaultPrefix) && comment.endsWith(name))
        {
          comment = null;
        }
        result.put(owner + "." + name, comment);
      }
    }
    catch (SQLException e)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, e, "snapshots", sql, schema);
      // When we get an exception, most probably we cannot access the ALL_MVIEWS view.
      // To avoid further (unnecessary) calls, we are disabling the support
      // for snapshots
      this.canRetrieveSnapshots = false;
      result = Collections.emptyMap();
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return result;
  }

}
