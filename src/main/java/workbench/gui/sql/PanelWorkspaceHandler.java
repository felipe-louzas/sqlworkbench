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
package workbench.gui.sql;


import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;
import workbench.workspace.WbWorkspace;

import workbench.gui.settings.ExternalFileHandling;

import workbench.util.WbFile;
import workbench.util.WbProperties;

/**
 *
 * @author Thomas Kellerer
 */
public class PanelWorkspaceHandler
{
  private final SqlPanel client;

  public PanelWorkspaceHandler(SqlPanel panel)
  {
    client = panel;
  }

  public void readFromWorkspace(WbWorkspace w, int index)
  {
    if (client.hasFileLoaded())
    {
      client.closeFile(true, false);
    }
    client.reset();

    try
    {
      w.readEditorHistory(index, client.sqlHistory);
    }
    catch (Exception e)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not read editor history for index=" + index);
      client.clearSqlHistory();
    }

    try
    {
      w.readSQLExecutionHistory(index, client.historyStatements);
    }
    catch (Exception e)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not read SQL execution history for index=" + index);
      client.historyStatements.clear();
    }

    client.setTabName(w.getTabTitle(index));

    WbProperties props = w.getSettings();

    int loc = props.getIntProperty("tab" + (index) + ".divider.location", 200);
    client.setDividerLocation(loc);
    loc = props.getIntProperty("tab" + (index) + ".divider.lastlocation", 0);
    client.contentPanel.setLastDividerLocation(loc);

    int v = w.getMaxRows(index);
    client.statusBar.setMaxRows(v);
    v = w.getQueryTimeout(index);
    client.statusBar.setQueryTimeout(v);

    client.invalidate();
    client.doLayout();

    String filename = w.getExternalFileName(index);
    boolean fileLoaded = false;
    if (filename != null)
    {
      String encoding = w.getExternalFileEncoding(index);
      WbFile f = new WbFile(filename);
      if (f.exists())
      {
        fileLoaded = client.readFile(f, encoding, false);
      }
      else
      {
        LogMgr.logWarning(new CallerInfo(){},
          "File \"" + f.getFullpathForLogging() + "\" referenced in workspace \"" + w.getFilename() + "\" does not exist!");
      }
    }

    if (fileLoaded)
    {
      int cursorPos = w.getExternalFileCursorPos(index);
      if (cursorPos > -1 && cursorPos < client.editor.getText().length())
      {
        client.editor.setCaretPosition(cursorPos);
      }
    }
    else
    {
      try
      {
        client.sqlHistory.showCurrent();
      }
      catch (Exception e)
      {
        LogMgr.logError(new CallerInfo(){}, "Error when showing current history entry", e);
      }
    }

    boolean appendResults = props.getBoolProperty( WbWorkspace.TAB_PROP_PREFIX + index + ".append.results", false);
    boolean locked = props.getBoolProperty( WbWorkspace.TAB_PROP_PREFIX + index + ".locked", false);
    client.setLocked(locked);
    client.setAppendResults(appendResults);
    client.updateAppendAction();
    client.editor.clearUndoBuffer();
    client.editor.resetModified();
    client.editor.invalidate();
  }

  public void saveToWorkspace(WbWorkspace w, int index)
  {
    if (!client.hasFileLoaded() ||
        client.hasFileLoaded() && Settings.getInstance().getFilesInWorkspaceHandling() != ExternalFileHandling.none)
    {
      // make sure the current content is stored in the SqlHistory object
      client.storeStatementInHistory();

      w.addEditorHistory(index, client.getEditorHistory());
      if (Settings.getInstance().getSaveSQLExcecutionHistory())
      {
        w.addExecutionHistory(index, client.historyStatements);
      }
    }

    WbProperties props = w.getSettings();
    String propStart = WbWorkspace.TAB_PROP_PREFIX + index;

    int location = client.contentPanel.getDividerLocation();
    int last = client.contentPanel.getLastDividerLocation();
    props.setProperty(propStart + ".divider.location", Integer.toString(location));
    props.setProperty(propStart + ".divider.lastlocation", Integer.toString(last));
    props.setProperty(propStart + ".append.results", Boolean.toString(client.getAppendResults()));
    props.setProperty(propStart + ".locked", Boolean.toString(client.isLocked()));
    props.setProperty(propStart + ".type", PanelType.sqlPanel.toString());

    w.setMaxRows(index, client.statusBar.getMaxRows());
    w.setQueryTimeout(index, client.statusBar.getQueryTimeout());

    if (client.hasFileLoaded() && Settings.getInstance().getFilesInWorkspaceHandling() == ExternalFileHandling.link)
    {
      w.setExternalFileName(index, client.getCurrentFileName());
      w.setExternalFileCursorPos(index, client.editor.getCaretPosition());
      w.setExternalFileEncoding(index, client.editor.getCurrentFileEncoding());
    }

    String title = client.getTabName();
    if (title == null)
    {
      title = ResourceMgr.getDefaultTabLabel();
    }
    w.setTabTitle(index, title);
  }

}
