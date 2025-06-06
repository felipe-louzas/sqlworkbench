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

import workbench.log.CallerInfo;

import workbench.db.ConnectionMgr;
import workbench.db.ConnectionProfile;
import workbench.db.DbDriver;
import workbench.db.WbConnection;

import workbench.log.LogMgr;

import workbench.util.ExceptionUtil;
import workbench.util.StringUtil;

/**
 * A Shutdown hook for Apache Derby.
 *
 * @author Thomas Kellerer
 */
public class DerbyShutdownHook
  implements DbShutdownHook
{

  @Override
  public void shutdown(WbConnection conn)
    throws SQLException
  {
    if (ConnectionMgr.getInstance().isActive(conn)) return;

    try
    {
      conn.rollback();
    }
    catch (Throwable th)
    {
      // ignore
    }

    conn.shutdown();

    if (!canShutdown(conn)) return;

    ConnectionProfile prof = conn.getProfile();

    String drvClass = prof.getDriverclass();
    String drvName = prof.getDriverName();

    String url = prof.getUrl();
    int pos = url.indexOf(';');
    if (pos < 0) pos = url.length();
    String command = url.substring(0, pos) + ";shutdown=true";

    try
    {
      DbDriver drv = ConnectionMgr.getInstance().findDriverByName(drvClass, drvName);
      LogMgr.logInfo(new CallerInfo(){}, "Local Derby connection detected. Shutting down engine...");
      drv.commandConnect(command);
    }
    catch (SQLException e)
    {
      // This exception is expected!
      // Cloudscape/Derby reports the shutdown success through an exception
      LogMgr.logInfo(new CallerInfo(){}, ExceptionUtil.getDisplay(e));
    }
    catch (Throwable th)
    {
      LogMgr.logError(new CallerInfo(){}, "Error when shutting down Cloudscape/Derby", th);
    }
  }

  private boolean canShutdown(WbConnection conn)
  {
    String cls = conn.getProfile().getDriverclass();

    // Never send a shutdown to a Derby server connection
    if (!cls.equals("org.apache.derby.jdbc.EmbeddedDriver")) return false;

    String url = conn.getUrl();
    int pos = StringUtil.indexOf(url, ':', 2);
    if (pos < 0) return true;

    String prefix = url.substring(pos + 1);

    // Do not shutdown Cloudscape server connections!
    if (url.startsWith(prefix + "net:")) return false;

    // Derby network URL starts with a //
    if (url.startsWith(prefix + "//")) return false;

    return true;
  }

}

