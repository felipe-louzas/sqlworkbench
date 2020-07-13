/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2020, Thomas Kellerer
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
package workbench.gui;

import java.io.File;

import workbench.resource.GuiSettings;
import workbench.resource.ResourceMgr;

import workbench.db.ConnectionProfile;
import workbench.db.WbConnection;

import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class WindowTitleBuilder
{
  private static final int NAME_AT_END = 1;
  private static final int NAME_AT_START = 2;

  private boolean showProfileGroup = GuiSettings.getShowProfileGroupInWindowTitle();
  private boolean showURL = GuiSettings.getShowURLinWindowTitle();
  private boolean includeUser = GuiSettings.getIncludeUserInTitleURL();
  private int productNamePosition = NAME_AT_START;
  private boolean showWorkspace = GuiSettings.getShowWorkspaceInWindowTitle();
  private boolean showNotConnected = true;

  public WindowTitleBuilder()
  {
    setShowProductNameAtEnd(GuiSettings.getShowProductNameAtEnd());
  }

  public void setShowProfileGroup(boolean flag)
  {
    this.showProfileGroup = flag;
  }

  public void setShowURL(boolean flag)
  {
    this.showURL = flag;
  }

  public void setIncludeUser(boolean flag)
  {
    this.includeUser = flag;
  }

  public void setShowProductNameAtEnd(boolean flag)
  {
    if (flag)
    {
      productNamePosition = NAME_AT_END;
    }
    else
    {
      productNamePosition = NAME_AT_START;
    }
  }

  public void setShowWorkspace(boolean flag)
  {
    this.showWorkspace = flag;
  }

  public void setShowNotConnected(boolean flag)
  {
    this.showNotConnected = flag;
  }

  public String getWindowTitle(WbConnection connection)
  {
    return getWindowTitle(connection, null, null);
  }

  public String getWindowTitle(WbConnection connection, String workspaceFile, String editorFile)
  {
    return getWindowTitle(connection, workspaceFile, editorFile, ResourceMgr.TXT_PRODUCT_NAME);
  }

  public String getWindowTitle(WbConnection connection, String workspaceFile, String editorFile, String appName)
  {
    StringBuilder title = new StringBuilder(50);

    ConnectionProfile profile = connection != null ? connection.getProfile() : null;
    String user = connection != null ? connection.getDisplayUser() : null;

    if (appName != null && productNamePosition == NAME_AT_START)
    {
      title.append(appName);
      title.append(' ');
    }

    if (profile != null)
    {
      boolean showUser = includeUser || profile.getPromptForUsername();

      if (showURL)
      {
        if (showProfileGroup)
        {
          appendProfileName(title, profile);
        }
        String url = makeCleanUrl(profile.getActiveUrl());
        if (showUser && user != null)
        {
          title.append(user);
          if (url.charAt(0) != '@')
          {
            title.append('@');
          }
        }
        title.append(url);
      }
      else
      {
        if (profile.getPromptForUsername())
        {
          // always display the username if prompted
          title.append(user);
          title.append(" - ");
        }
        if (showProfileGroup)
        {
          appendProfileName(title, profile);
        }
        title.append(profile.getName());
      }
    }
    else if (showNotConnected)
    {
      if (title.length() > 0) title.append("- ");
      title.append(ResourceMgr.getString("TxtNotConnected"));
    }

    if (StringUtil.isNonBlank(workspaceFile) && showWorkspace)
    {
      File f = new File(workspaceFile);
      String baseName = f.getName();
      title.append(" - ");
      title.append(baseName);
      title.append(" ");
    }

    int showFilename = GuiSettings.getShowFilenameInWindowTitle();
    if (StringUtil.isNonBlank(editorFile) && showFilename != GuiSettings.SHOW_NO_FILENAME)
    {
      title.append(" - ");
      if (showFilename == GuiSettings.SHOW_FULL_PATH)
      {
        title.append(editorFile);
      }
      else
      {
        File f = new File(editorFile);
        title.append(f.getName());
      }
    }

    if (StringUtil.isNonBlank(appName) && productNamePosition == NAME_AT_END)
    {
      if (title.length() > 0) title.append(" - ");
      title.append(appName);
    }

    return title.toString();
  }

  private void appendProfileName(StringBuilder title, ConnectionProfile profile)
  {
    String enclose = GuiSettings.getTitleGroupBracket();
    String sep = GuiSettings.getTitleGroupSeparator();

    char open = getOpeningBracket(enclose);
    char close = getClosingBracket(enclose);

    if (open != 0 && close != 0)
    {
      title.append(open);
    }
    title.append(profile.getGroup());
    if (open != 0 && close != 0)
    {
      title.append(close);
    }
    if (sep != null) title.append(sep);
  }

  private char getOpeningBracket(String settingsValue)
  {
    if (StringUtil.isEmptyString(settingsValue)) return 0;
    return settingsValue.charAt(0);
  }

  private char getClosingBracket(String settingsValue)
  {
    if (StringUtil.isEmptyString(settingsValue)) return 0;
    char open = getOpeningBracket(settingsValue);
    if (open == '{') return '}';
    if (open == '[') return ']';
    if (open == '(') return ')';
    if (open == '<') return '>';
    return 0;
  }

  public String makeCleanUrl(String url)
  {
    if (StringUtil.isEmptyString(url)) return url;
    // remove the jdbc: prefix as it's not useful
    url = url.replace("jdbc:", "");

    // remove URL parameters
    if (GuiSettings.getCleanupURLParametersInWindowTitle())
    {
      int pos = url.indexOf('&');
      if (pos > 0)
      {
        url = url.substring(0, pos);
      }
      pos = url.indexOf(';');
      if (pos > 0)
      {
        url = url.substring(0, pos);
      }
      pos = url.indexOf('?');
      if (pos > 0)
      {
        url = url.substring(0, pos);
      }
    }
    else
    {
      // in any case remove the parameter for integratedSecurity as
      // that will be reflected in the username
      url = url.replaceFirst("(?i)(integratedSecurity=true);*", "");
    }

    if (GuiSettings.getRemoveJDBCProductInWindowTitle())
    {
      if (url.contains("oracle:"))
      {
        // special handling for Oracle
        url = url.replace("oracle:thin:", "");
        url = url.replace("oracle:oci:", "");
      }
      else if (url.contains("jtds:sqlserver:"))
      {
        url = url.replace("jtds:sqlserver:", "");
      }
      else
      {
        int pos = url.indexOf(':');
        if (pos > 0)
        {
          url = url.substring(pos + 1);
        }
      }
    }
    return url;
  }

}
