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
package workbench.gui.actions.workspace;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.KeyStroke;

import workbench.resource.PlatformShortcuts;
import workbench.resource.ResourceMgr;

import workbench.gui.MainWindow;
import workbench.gui.actions.WbAction;

/**
 * @author Thomas Kellerer
 */
public class LoadWorkspaceAction
  extends WbAction
{
  private MainWindow client;

  public LoadWorkspaceAction(MainWindow aClient)
  {
    super();
    this.client = aClient;
    this.initMenuDefinition("MnuTxtLoadWorkspace", KeyStroke.getKeyStroke(KeyEvent.VK_O, PlatformShortcuts.getDefaultModifier() | InputEvent.SHIFT_DOWN_MASK));
    this.setMenuItemName(ResourceMgr.MNU_TXT_WORKSPACE);
    this.setIcon(null);
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    this.client.loadWorkspace();
  }

}
