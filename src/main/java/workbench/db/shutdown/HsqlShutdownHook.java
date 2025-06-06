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
package workbench.db.shutdown;

import java.sql.SQLException;
import java.sql.Statement;

import workbench.log.CallerInfo;

import workbench.db.ConnectionMgr;
import workbench.db.WbConnection;

import workbench.log.LogMgr;

import workbench.db.JdbcUtils;

/**
 * A Shutdown hook for H2 Database.
 *
 * @author Thomas Kellerer
 */
public class HsqlShutdownHook
  implements DbShutdownHook
{

  private boolean canShutdown(WbConnection con)
  {
    String url = con.getUrl();
    if (url == null) return true;

    // this is a HSQL server connection. Do not shut down this!
    if (url.startsWith("jdbc:hsqldb:hsql:")) return false;

    return true;
  }

  @Override
  public void shutdown(WbConnection con)
    throws SQLException
  {
    if (con == null) return;

    boolean otherActive = ConnectionMgr.getInstance().isActive(con);
    if (otherActive) return;

    if (canShutdown(con))
    {
      Statement stmt = null;
      try
      {
        stmt = con.createStatement();
        LogMgr.logInfo(new CallerInfo(){}, "Local HSQL connection detected. Sending SHUTDOWN to the engine before disconnecting");
        stmt.executeUpdate("SHUTDOWN");
      }
      finally
      {
        JdbcUtils.closeStatement(stmt);
      }
    }
    con.shutdown();
  }
}
