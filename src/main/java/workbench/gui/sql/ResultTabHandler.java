/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2024 Thomas Kellerer
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

import java.awt.Component;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.JPopupMenu;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeListener;

import workbench.resource.ResourceMgr;

import workbench.gui.actions.AutomaticReloadAction;
import workbench.gui.actions.CancelAutoReloadAction;
import workbench.gui.actions.CloseAllResultsAction;
import workbench.gui.actions.CloseEmptyResultsAction;
import workbench.gui.actions.CloseOtherResultsAction;
import workbench.gui.actions.CloseResultTabAction;
import workbench.gui.actions.DetachResultTabAction;
import workbench.gui.actions.LockResultTabAction;
import workbench.gui.actions.RenameTabAction;
import workbench.gui.actions.ShowSourceQueryAction;
import workbench.gui.actions.SqlPanelReloadAction;

import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class ResultTabHandler
  implements MouseListener, RenameableTab
{
  private final JTabbedPane resultTab;
  private final SqlPanel client;

  public ResultTabHandler(JTabbedPane tab, SqlPanel sqlPanel)
  {
    resultTab = tab;
    resultTab.addMouseListener(this);
    client = sqlPanel;
  }

  @Override
  public void mouseClicked(MouseEvent e)
  {
    if (e.getSource() != this.resultTab) return;

    Point p = e.getPoint();
    int index = resultTab.indexAtLocation(p.x, p.y);
    boolean isResultTab = (index != resultTab.getTabCount() - 1);

    if (!isResultTab) return;

    if (e.getButton() == MouseEvent.BUTTON3)
    {
      JPopupMenu menu = createPopup(index);
      menu.show(resultTab, e.getX(), e.getY());
    }
    else if (e.getButton() == MouseEvent.BUTTON1 && e.getClickCount() == 2)
    {
      ShowSourceQueryAction action = new ShowSourceQueryAction(client, index);
      action.showQuery();
    }
  }

  @Override
  public void mousePressed(MouseEvent e)
  {
  }

  @Override
  public void mouseReleased(MouseEvent e)
  {
  }

  @Override
  public void mouseEntered(MouseEvent e)
  {
  }

  @Override
  public void mouseExited(MouseEvent e)
  {
  }

  private JPopupMenu createPopup(int forIndex)
  {
    JPopupMenu menu = new JPopupMenu();
    menu.add(new RenameTabAction(this, forIndex));
    menu.add(new ShowSourceQueryAction(client, forIndex));
    menu.add(new SqlPanelReloadAction(client, forIndex));
    menu.addSeparator();
    menu.add(new CloseResultTabAction(client, forIndex));
    menu.add(new CloseOtherResultsAction(client, forIndex));
    menu.add(new CloseEmptyResultsAction(client));
    menu.add(new CloseAllResultsAction(client));
    menu.addSeparator();
    menu.add(new AutomaticReloadAction(client, forIndex));
    menu.add(new CancelAutoReloadAction(client, forIndex));
    menu.addSeparator();
    LockResultTabAction lock = new LockResultTabAction(client, forIndex);
    menu.add(lock.getMenuItem());
    menu.add(new DetachResultTabAction(client, forIndex));
    return menu;
  }

  @Override
  public Component getComponent()
  {
    return resultTab;
  }

  @Override
  public void setCurrentTabTitle(String newName)
  {
    int index = this.resultTab.getSelectedIndex();
    setTabTitle(index, newName);
  }

  @Override
  public void setTabTitle(int index, String newName)
  {
    if (StringUtil.isBlank(newName))
    {
      newName = ResourceMgr.getString("LblTabResult");
    }
    if (index == -1)
    {
      index = resultTab.getSelectedIndex();
    }
    resultTab.setTitleAt(index, newName);
  }


  @Override
  public String getCurrentTabTitle()
  {
    int index = this.resultTab.getSelectedIndex();
    return resultTab.getTitleAt(index);
  }

  @Override
  public String getTabTitle(int index)
  {
    return resultTab.getTitleAt(index);
  }

  @Override
  public boolean canRenameTab(int index)
  {
    return true;
  }

  @Override
  public void addTabChangeListener(ChangeListener l)
  {
    this.resultTab.addChangeListener(l);
  }
}
