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
package workbench.gui.actions;

import java.awt.event.ActionEvent;
import java.text.MessageFormat;

import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.gui.WbSwingUtilities;

import workbench.util.BrowserLauncher;
import workbench.util.ExceptionUtil;
import workbench.util.StringUtil;
import workbench.util.VersionNumber;

/**
 * @author Thomas Kellerer
 */
public class ShowDbmsManualAction
  extends WbAction
{
  private String onlineManualUrl;

  public ShowDbmsManualAction()
  {
    super();
    initMenuDefinition("MnuTxtDbmsHelp");
    setIcon("dbms-manual");
    setEnabled(false);
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    if (StringUtil.isNotBlank(onlineManualUrl))
    {
      try
      {
        BrowserLauncher.openURL(onlineManualUrl);
      }
      catch (Exception ex)
      {
        WbSwingUtilities.showErrorMessage(ExceptionUtil.getDisplay(ex));
      }
    }
  }

  public void clearDbms()
  {
    setDbms(null, -1, -1);
  }

  public void setDbms(String dbid, VersionNumber version)
  {
    if (version == null)
    {
      setDbms(dbid, -1, -1);
    }
    else
    {
      setDbms(dbid, version.getMajorVersion(), version.getMinorVersion());
    }
  }

  private void setDbms(String dbid, int majorVersion, int minorVersion)
  {
    if (StringUtil.isNotBlank(dbid))
    {
      String url = null;
      if (majorVersion > 0 && minorVersion > 0)
      {
        url = Settings.getInstance().getProperty("workbench.db." + dbid + "." + Integer.toString(majorVersion) + "." + Integer.toString(minorVersion) + ".manual", null);
      }

      if (url == null && majorVersion > 0)
      {
        url = Settings.getInstance().getProperty("workbench.db." + dbid + "." + Integer.toString(majorVersion) + ".manual", null);
      }
      if (url == null)
      {
        url = Settings.getInstance().getProperty("workbench.db." + dbid + ".manual", null);
      }
      if (url != null)
      {
        onlineManualUrl = MessageFormat.format(url, majorVersion, minorVersion);
      }
      else
      {
        onlineManualUrl = null;
      }
    }
    else
    {
      onlineManualUrl = null;
    }
    setEnabled(StringUtil.isNotBlank(onlineManualUrl));
    if (onlineManualUrl != null)
    {
      setTooltip("<html>" + ResourceMgr.getDescription("MnuTxtDbmsHelp") + "<br>(" + onlineManualUrl + ")</html>");
    }
    else
    {
      setTooltip(null);
    }
  }
}
