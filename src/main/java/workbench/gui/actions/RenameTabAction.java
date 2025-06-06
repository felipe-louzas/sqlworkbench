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

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import workbench.resource.ResourceMgr;

import workbench.gui.WbSwingUtilities;
import workbench.gui.sql.RenameableTab;

/**
 * @author Thomas Kellerer
 */
public class RenameTabAction
  extends WbAction
  implements ChangeListener
{
  private final RenameableTab client;
  private final int tabIndex;

  public RenameTabAction(RenameableTab aClient, int index)
  {
    super();
    this.client = aClient;
    this.tabIndex = index;
    this.initMenuDefinition("MnuTxtRenameTab");
    this.setMenuItemName(ResourceMgr.MNU_TXT_VIEW);
    client.addTabChangeListener(this);
    setEnabled(client.canRenameTab(tabIndex));
    this.setIcon(null);
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    String oldName = client.getTabTitle(tabIndex);
    String newName = WbSwingUtilities.getUserInput(client.getComponent(), ResourceMgr.getString("MsgEnterNewTabName"), oldName);
    if (newName != null)
    {
      client.setTabTitle(tabIndex, newName);
    }
  }

  @Override
  public void stateChanged(ChangeEvent e)
  {
    setEnabled(client.canRenameTab(tabIndex));
  }

  @Override
  public boolean useInToolbar()
  {
    return false;
  }
}
