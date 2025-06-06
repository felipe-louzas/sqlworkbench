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

import workbench.resource.ResourceMgr;

import workbench.gui.MainWindow;
import workbench.gui.actions.WbAction;

/**
 * Action to assign the currently loaded workspace to the current connection profile
 *
 * @see workbench.gui.MainWindow#assignWorkspace()
 * @see workbench.db.ConnectionProfile
 * @see workbench.util.WbWorkspace
 *
 * @author Thomas Kellerer
 */
public class AssignWorkspaceAction
  extends WbAction
{
  private MainWindow client;

  public AssignWorkspaceAction(MainWindow aClient)
  {
    super();
    this.client = aClient;
    this.initMenuDefinition("MnuTxtAssignWorkspace", null);
    this.setMenuItemName(ResourceMgr.MNU_TXT_WORKSPACE);
    this.setIcon(null);
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    this.client.assignWorkspace();
  }

}
