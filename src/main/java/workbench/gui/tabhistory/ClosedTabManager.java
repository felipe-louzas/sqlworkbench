/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0 (the "License")
 * that restricts the use for certain governments.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.sql-workbench.eu/manual/license.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.eu
 */
package workbench.gui.tabhistory;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.List;

import javax.swing.JMenu;
import javax.swing.JMenuItem;

import workbench.interfaces.MainPanel;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.GuiSettings;

import workbench.gui.MainWindow;
import workbench.gui.sql.EditorHistoryEntry;
import workbench.gui.sql.SqlPanel;

import workbench.util.FixedSizeList;
import workbench.util.NumberStringCache;

/**
 *
 * @author Thomas Kellerer
 */
public class ClosedTabManager
  implements ActionListener
{
  private final FixedSizeList<ClosedTabInfo> recentTabs;
  private final MainWindow client;

  public ClosedTabManager(MainWindow window)
  {
    recentTabs = new FixedSizeList(GuiSettings.getTabHistorySize());
    recentTabs.setAllowDuplicates(true);
    client = window;
  }

  public void addToTabHistory(MainPanel panel, int index)
  {
    if (panel instanceof SqlPanel)
    {
      SqlPanel sql = (SqlPanel)panel;
      List<EditorHistoryEntry> entries = sql.getEditorHistory().getEntries();
      String title = panel.getTabTitle();
      ClosedTabInfo info = new ClosedTabInfo(title, entries, index);
      info.setExternalFile(sql.getEditor().getCurrentFile(), sql.getEditor().getCurrentFileEncoding());
      recentTabs.add(info);
      LogMgr.logDebug(new CallerInfo(){}, "Recent tab added: " + info.toString());
    }
  }

  public void updateMenu(JMenu historyMenu)
  {
    if (historyMenu == null) return;
    historyMenu.removeAll();

    for (ClosedTabInfo info : recentTabs)
    {
      String name = info.getTabName();
      if (GuiSettings.getShowTabIndex())
      {
        name += " " + NumberStringCache.getNumberString(info.getTabIndex() + 1);
      }
      JMenuItem item = new JMenuItem(name);
      item.putClientProperty("tab-info", info);
      item.addActionListener(this);
      historyMenu.add(item);
    }
    historyMenu.setEnabled(recentTabs.size() > 0);
  }

  @Override
  public void actionPerformed(ActionEvent e)
  {
    if (client == null) return;

    Object source = e.getSource();
    if ( !(source instanceof JMenuItem)) return;

    JMenuItem item = (JMenuItem)source;
    ClosedTabInfo tabInfo = (ClosedTabInfo)item.getClientProperty("tab-info");
    if (tabInfo == null) return;

    SqlPanel newTab = client.restoreTab(tabInfo.getTabIndex());
    if (newTab != null)
    {
      recentTabs.remove(tabInfo);
      newTab.getEditorHistory().replaceHistory(tabInfo.getHistory());
      newTab.setTabName(tabInfo.getTabName());
      client.updateTabHistoryMenu();
      if (tabInfo.getExternalFile() != null)
      {
        if (tabInfo.getExternalFile().exists())
        {
          newTab.getEditor().readFile(tabInfo.getExternalFile(), tabInfo.getFileEncoding());
        }
        else
        {
          LogMgr.logWarning(new CallerInfo(){},
            "The tab \"" + tabInfo.getTabName() + "\" referenced the no longer existing file: " + tabInfo.getExternalFile().getAbsolutePath());
        }
      }
      tabInfo.clear();
    }
  }

  public void clear()
  {
    for (ClosedTabInfo info : recentTabs)
    {
      info.clear();
    }
    recentTabs.clear();
  }

  public void reset(JMenu historyMenu)
  {
    clear();
    if (historyMenu != null)
    {
      historyMenu.removeAll();
      historyMenu.setEnabled(false);
    }
  }
}
