/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer
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
import workbench.util.WbFile;
import workbench.util.WorkspaceSelector;

/**
 *
 * @author Thomas Kellerer
 */
public class WindowTitleBuilder
{
  public static final String PARM_APP_NAME = "{app}";
  public static final String PARM_CONN = "{conn}";
  public static final String PARM_WKSP = "{wksp}";
  public static final String PARM_FNAME = "{fname}";
  public static final String DELIM = " - ";

  private boolean showProfileGroup = GuiSettings.getShowProfileGroupInWindowTitle();
  private boolean showURL = GuiSettings.getShowURLinWindowTitle();
  private boolean includeUser = GuiSettings.getIncludeUserInTitleURL();
  private boolean showWorkspace = GuiSettings.getShowWorkspaceInWindowTitle();
  private boolean showNotConnected = true;
  private boolean showAppNameAtEnd = GuiSettings.getShowProductNameAtEnd();
  private boolean cleanupURL = GuiSettings.getCleanupURLParametersInWindowTitle();
  private boolean removeJDBCProduct = GuiSettings.getRemoveJDBCProductInWindowTitle();
  private String titleTemplate;
  private String encloseWksp = GuiSettings.getTitleWorkspaceBracket();
  private String encloseGroup = GuiSettings.getTitleGroupBracket();

  public WindowTitleBuilder()
  {
  }

  public void setRemoveJDBCProductFromURL(boolean flag)
  {
    removeJDBCProduct = flag;
  }
  public void setCleanupURL(boolean flag)
  {
    this.cleanupURL = flag;
  }

  public void setShowAppNameAtEnd(boolean flag)
  {
    this.showAppNameAtEnd = flag;
  }

  public void setEncloseWksp(String encloseWksp)
  {
    this.encloseWksp = encloseWksp;
  }

  public void setEncloseGroup(String encloseGroup)
  {
    this.encloseGroup = encloseGroup;
  }

  public final void setTitleTemplate(String template)
  {
    this.titleTemplate = StringUtil.trimToNull(template);
    if (StringUtil.isNotBlank(titleTemplate))
    {
      showWorkspace = titleTemplate.contains(PARM_WKSP);
    }
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
    String title = getTemplate();

    ConnectionProfile profile = connection != null ? connection.getProfile() : null;
    String user = connection != null ? connection.getDisplayUser() : null;

    title = replace(title, PARM_APP_NAME, appName);

    String connInfo = "";
    if (profile != null)
    {
      if (showURL)
      {
        boolean showUser = includeUser || profile.getPromptForUsername();
        String url = makeCleanUrl(profile.getActiveUrl());
        if (showUser && user != null)
        {
          connInfo += user;
          if (url.charAt(0) != '@')
          {
            connInfo += "@";
          }
        }
        connInfo += url;
      }
      else
      {
        if (profile.getPromptForUsername())
        {
          // always display the username if prompted
          connInfo = user + DELIM;
        }
        connInfo += getProfileName(profile);
      }
    }
    else if (showNotConnected)
    {
      connInfo = ResourceMgr.getString("TxtNotConnected");
    }
    connInfo = enclose(connInfo, encloseGroup);

    String wksp = null;
    if (StringUtil.isNotBlank(workspaceFile) && showWorkspace)
    {
      if (GuiSettings.shortenWorkspaceNameInWindowTitle())
      {
        wksp = WorkspaceSelector.shortenFilename(workspaceFile);
      }
      else
      {
        WbFile f = new WbFile(workspaceFile);
        wksp = f.getFullPath();
      }
      wksp = enclose(wksp, encloseWksp);
    }

    String fname = null;
    int showFilename = GuiSettings.getShowFilenameInWindowTitle();
    if (StringUtil.isNotBlank(editorFile) && showFilename != GuiSettings.SHOW_NO_FILENAME)
    {
      if (showFilename == GuiSettings.SHOW_FULL_PATH)
      {
        fname = editorFile;
      }
      else
      {
        File f = new File(editorFile);
        fname = f.getName();
      }
    }

    title = replace(title, PARM_CONN, connInfo);
    title = replace(title, PARM_FNAME, fname);
    title = replace(title, PARM_WKSP, wksp);
    return title;
  }

  private String replace(String title, String param, String value)
  {
    if (StringUtil.isEmpty(value))
    {
      title = title.replace(DELIM + param, "");
      title = title.replace(param + DELIM, "");
      title = title.replaceFirst("\\s{0,1}" + StringUtil.quoteRegexMeta(param), "");
      return title;
    }
    return title.replace(param, value);
  }

  private String getProfileName(ConnectionProfile profile)
  {
    String name = "";
    if(profile == null) return name;

    if (showProfileGroup)
    {
      name += profile.getGroupPathString() + "/";
    }
    name += profile.getName();
    return name;
  }

  private String enclose(String value, String enclose)
  {
    if (value == null || StringUtil.isBlank(enclose)) return value;
    char open = getOpeningBracket(enclose);
    char close = getClosingBracket(enclose);
    if (open != 0 && close != 0)
    {
      return open + value + close;
    }
    return value;
  }

  private char getOpeningBracket(String settingsValue)
  {
    if (StringUtil.isEmpty(settingsValue)) return 0;
    return settingsValue.charAt(0);
  }

  private char getClosingBracket(String settingsValue)
  {
    if (StringUtil.isEmpty(settingsValue)) return 0;
    char open = getOpeningBracket(settingsValue);
    if (open == '{') return '}';
    if (open == '[') return ']';
    if (open == '(') return ')';
    if (open == '<') return '>';
    return 0;
  }

  public String makeCleanUrl(String url)
  {
    if (StringUtil.isEmpty(url)) return url;
    // remove the jdbc: prefix as it's not useful
    url = url.replace("jdbc:", "");

    // remove URL parameters
    if (cleanupURL)
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

    if (removeJDBCProduct)
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

  private String getTemplate()
  {
    if (StringUtil.isNotBlank(titleTemplate)) return titleTemplate;

    String template = GuiSettings.getTitleTemplate();
    if (template != null) return template;

    if (showAppNameAtEnd)
    {
      return PARM_CONN + DELIM + PARM_WKSP + DELIM + PARM_FNAME + DELIM + PARM_APP_NAME;
    }
    return PARM_APP_NAME + DELIM + PARM_CONN + DELIM + PARM_WKSP + DELIM + PARM_FNAME;
  }

}
