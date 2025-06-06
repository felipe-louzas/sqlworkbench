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
import java.awt.event.KeyEvent;

import javax.swing.KeyStroke;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.PlatformShortcuts;
import workbench.resource.ResourceMgr;

import workbench.db.WbConnection;

import workbench.gui.sql.SqlPanel;

import workbench.storage.DataStore;

import workbench.sql.StatementRunnerResult;
import workbench.sql.wbcommands.ObjectInfo;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;
import workbench.util.WbThread;

/**
 *
 * @author Thomas Kellerer
 */
public class ShowObjectInfoAction
  extends WbAction
{
  private final SqlPanel display;

  public ShowObjectInfoAction(SqlPanel panel)
  {
    display = panel;
    setIcon("object-info");
    setMenuItemName(ResourceMgr.MNU_TXT_SQL);
    initMenuDefinition("MnuTxtShowObjectDef", KeyStroke.getKeyStroke(KeyEvent.VK_I, PlatformShortcuts.getDefaultModifier()));
    checkEnabled();
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    if (display.isConnectionBusy()) return;
    final boolean includeDependencies;
    if (invokedByMouse(e))
    {
      includeDependencies = isCtrlPressed(e);
    }
    else
    {
      includeDependencies = false;
    }
    WbThread t = new WbThread("ObjectInfoThread")
    {
      @Override
      public void run()
      {
        showInfo(includeDependencies);
      }
    };
    t.start();
  }

  protected void showInfo(boolean includeDependencies)
  {
    if (display.isConnectionBusy()) return;
    WbConnection conn = display.getConnection();
    if (conn == null) return;

    try
    {
      display.setBusy(true);
      display.fireDbExecStart();
      setEnabled(false);

      ObjectInfo info = new ObjectInfo();

      boolean deps = conn.getDbSettings().objectInfoWithDependencies();
      String text = SqlUtil.getIdentifierAtCursor(display.getEditor(), conn);

      if (StringUtil.isNotBlank(text))
      {
        display.setStatusMessage(ResourceMgr.getString("TxtRetrieveTableDef") + " " + text);
        StatementRunnerResult result = info.getObjectInfo(conn, text, includeDependencies || deps, true);

        if (result != null)
        {
          int count = display.getResultTabCount();

          // if the display is "kept busy" the current "data" will not be recognized
          // when switching panels
          display.setBusy(false);

          // Retrieving the messages will reset the hasMessages() flag...
          boolean hasMessages = result.hasMessages();

          if (hasMessages)
          {
            display.appendToLog("\n");
            display.appendToLog(result.getMessages());
          }

          if (result.hasDataStores())
          {
            for (DataStore data : result.getDataStores())
            {
              data.resetStatus();
            }
            display.addResult(result);
            display.setSelectedResultTab(count - 1);
          }
          else if (hasMessages)
          {
            display.showLogPanel();
          }
        }
      }
    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Error retrieving object info", ex);
    }
    finally
    {
      display.fireDbExecEnd();
      display.clearStatusMessage();

      // just in case...
      if (display.isBusy())
      {
        display.setBusy(false);
      }

      checkEnabled();
    }
  }

  public final void checkEnabled()
  {
    setEnabled(display != null && display.isConnected());
  }

}
