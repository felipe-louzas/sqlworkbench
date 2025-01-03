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
package workbench.db;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.util.DurationUtil;
import workbench.util.ExceptionUtil;
import workbench.util.SqlUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class KeepAliveDaemon
  implements Runnable
{
  private final long idleTime;
  private boolean stopDaemon;
  private final WbConnection dbConnection;
  private final String sqlScript;
  private final ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
  private ScheduledFuture schedule;

  public KeepAliveDaemon(long idle, WbConnection con, String sql)
  {
    this.idleTime = idle;
    this.dbConnection = con;
    this.sqlScript = SqlUtil.trimSemicolon(sql);
  }

  public void startDaemon()
  {
    if (schedule != null && !schedule.isDone())
    {
      LogMgr.logWarning(new CallerInfo(){}, "startThread() called on already running daemon", new Exception("Backtrace"));
      return;
    }

    LogMgr.logInfo(new CallerInfo(){}, "Initializing keep alive every " + DurationUtil.getTimeDisplay(idleTime) + " with sql: " + this.sqlScript);
    scheduleTask();
    stopDaemon = false;
  }

  public void shutdown()
  {
    try
    {
      this.stopDaemon = true;
      this.schedule.cancel(true);
    }
    catch (Exception e)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Error when stopping thread", e);
    }
  }

  @Override
  public void run()
  {
    if (this.stopDaemon) return;

    runSqlScript();

    if (!this.stopDaemon)
    {
      scheduleTask();
    }
  }

  public void setLastDbAction(long millis)
  {
    // Restart the schedule
    if (schedule != null)
    {
      schedule.cancel(false);
    }
    scheduleTask();
  }

  private void scheduleTask()
  {
    schedule = executor.schedule(this, idleTime, TimeUnit.MILLISECONDS);
  }

  private void runSqlScript()
  {
    if (this.dbConnection == null) return;
    if (this.dbConnection.isBusy()) return;

    Statement stmt = null;
    try
    {
      stmt = this.dbConnection.createStatement();
      LogMgr.logInfo(new CallerInfo(){}, "Running keep alive for [" + dbConnection.getId() + "]: " + this.sqlScript);
      stmt.execute(sqlScript);
    }
    catch (SQLException sql)
    {
      LogMgr.logError(new CallerInfo(){}, Thread.currentThread().getName() + ": SQL Error when running keep alive script: " + ExceptionUtil.getDisplay(sql), null);
    }
    catch (Throwable e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error when running keep alive script", e);
    }
    finally
    {
      JdbcUtils.closeStatement(stmt);
    }
  }

}
