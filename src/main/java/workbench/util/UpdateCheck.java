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

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.gui.WbSwingUtilities;

/**
 * @author Thomas Kellerer
 */
public class UpdateCheck
  implements ActionListener
{
  private static final String TYPE_WB_VERSION = "version_check";
  private static final String TYPE_JAVA_VERSION = "java_check";
  public static final boolean DEBUG = Boolean.getBoolean("workbench.debug.versioncheck");

  private WbVersionReader versionReader;

  public void startUpdateCheck()
  {
    if (DEBUG)
    {
      startRead();
      return;
    }

    int interval = Settings.getInstance().getUpdateCheckInterval();
    LocalDate lastCheck = Settings.getInstance().getLastUpdateCheck();

    if (needCheck(interval, LocalDate.now(), lastCheck))
    {
      startRead();
    }
    else
    {
      checkJavaVersion();
    }
  }

  private void checkJavaVersion()
  {
    if (!Settings.getInstance().checkJavaVersion()) return;
    if (ResourceMgr.getBuildNumber().getMajorVersion() == 999) return; // don't check if started from IDE

    VersionNumber minVersion = new VersionNumber(17,0);
    VersionNumber currentVersion = VersionNumber.getJavaVersion();
    if (!currentVersion.isNewerOrEqual(minVersion))
    {
      NotifierEvent event = new NotifierEvent("alert", ResourceMgr.getString("MsgOldJava"), this);
      event.setTooltip(ResourceMgr.getString("MsgOldJavaDetail"));
      event.setType(TYPE_JAVA_VERSION);
      EventNotifier.getInstance().displayNotification(event);
    }
  }

  /**
   * This is public so that the method is accessible for Unit-Testing
   */
  boolean needCheck(int interval, LocalDate today, LocalDate lastCheck)
  {
    if (interval < 1) return false;
    if (lastCheck == null) return true;

    LocalDate next = lastCheck.plus(interval, ChronoUnit.DAYS);

    return next.isBefore(today) || next.isEqual(today);
  }

  public void startRead()
  {
    LogMgr.logDebug(new CallerInfo(){}, "Checking versions...");
    versionReader = new WbVersionReader("automatic", this);
    versionReader.startCheckThread();
  }

  private void showNotification()
  {
    final CallerInfo ci = new CallerInfo(){};
    try
    {
      LogMgr.logDebug(ci, "Current stable version: " + versionReader.getStableBuildNumber());
      LogMgr.logDebug(ci, "Current development version: " + versionReader.getDevBuildNumber());

      UpdateVersion update = this.versionReader.getAvailableUpdate();
      NotifierEvent event = null;
      if (DEBUG || update == UpdateVersion.stable)
      {
        LogMgr.logInfo(ci, "New stable version available");
        event = new NotifierEvent("updates", ResourceMgr.getString("LblVersionNewStableAvailable"), this);
      }
      else if (update == UpdateVersion.devBuild)
      {
        LogMgr.logInfo(ci, "New dev build available");
        event = new NotifierEvent("updates", ResourceMgr.getString("LblVersionNewDevAvailable"), this);
      }
      else
      {
        LogMgr.logInfo(ci, "No updates found");
      }

      if (this.versionReader.success())
      {
        try
        {
          Settings.getInstance().setLastUpdateCheck();
        }
        catch (Exception e)
        {
          LogMgr.logError(ci, "Error when updating last update date", e);
        }
      }

      if (event != null)
      {
        event.setType(TYPE_WB_VERSION);
        EventNotifier.getInstance().displayNotification(event);
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(ci, "Could not check for updates", e);
    }
  }

  @Override
  public void actionPerformed(ActionEvent e)
  {
    if (e.getSource() == this.versionReader)
    {
      showNotification();
      return;
    }

    String command = e.getActionCommand();

    try
    {
      if (TYPE_WB_VERSION.equals(command))
      {
        BrowserLauncher.openURL("https://www.sql-workbench.eu");
      }
      else if (TYPE_JAVA_VERSION.equals(command))
      {
        BrowserLauncher.openURL("https://adoptopenjdk.net");
      }
      EventNotifier.getInstance().removeNotification();
    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not open browser", ex);
      WbSwingUtilities.showMessage(null, "Could not open browser (" + ExceptionUtil.getDisplay(ex) + ")");
    }
  }
}
