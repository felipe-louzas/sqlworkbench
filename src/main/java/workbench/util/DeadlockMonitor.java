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
package workbench.util;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

/**
 *
 * @author Thomas Kellerer
 */
public class DeadlockMonitor
  extends WbThread
{
  private final ThreadDumper monitor;
  private final int sleepTime;
  private final int minLogDuration;
  private boolean keepRunning = true;

  public DeadlockMonitor()
  {
    super("WbDeadlockMonitor");
    monitor = new ThreadDumper();
    sleepTime = Settings.getInstance().getIntProperty("workbench.gui.debug.deadlockmonitor.sleeptime", 5000);
    minLogDuration = Settings.getInstance().getIntProperty("workbench.gui.debug.deadlockmonitor.logduration", 50);
  }

  @Override
  public void run()
  {
    while (keepRunning)
    {
      long start = System.currentTimeMillis();
      String dump = monitor.getDeadlockDump();
      long duration = System.currentTimeMillis() - start;

      if (duration > minLogDuration)
      {
        LogMgr.logInfo(new CallerInfo(){}, "Checking for deadlocks took: " + duration + "ms");
      }

      if (dump != null)
      {
        LogMgr.logError(new CallerInfo(){}, "Deadlock detected:\n" + dump, null);
      }

      if (keepRunning)
      {
        try
        {
          Thread.sleep(sleepTime);
        }
        catch (InterruptedException ir)
        {
          break;
        }
      }
    }
  }

  public void cancel()
  {
    keepRunning = false;
    interrupt();
  }

}
