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
import java.util.Properties;

import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.gui.MainWindow;
import workbench.gui.actions.WbAction;
import workbench.gui.components.MapEditor;
import workbench.gui.components.ValidatingDialog;

/**
 * Action to close the current workspace.
 *
 * @see workbench.gui.MainWindow#closeWorkspace(boolean)
 * @author Thomas Kellerer
 */
public class EditWorkspaceVarsAction
  extends WbAction
{
  private final String CONFIG_PROP = "workbench.gui.edit.workspace.variables.dialog";
  private MainWindow client;

  public EditWorkspaceVarsAction(MainWindow aClient)
  {
    super();
    this.client = aClient;
    this.initMenuDefinition("MnuTxtEditWkspVars", null);
    this.setMenuItemName(ResourceMgr.MNU_TXT_WORKSPACE);
    this.setIcon(null);
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    if (client == null) return;

    Properties variables = client.getCurrentWorkspaceVariables();
    if (variables == null) return;

    MapEditor editor = new MapEditor(variables);
    editor.optimizeColumnWidths();

    ValidatingDialog dialog = ValidatingDialog.createDialog(client, editor, ResourceMgr.getPlainString("MnuTxtEditWkspVars"), null, 0, false);
    dialog.setVisible(true);

    Settings.getInstance().storeWindowSize(dialog, CONFIG_PROP);

    if (!dialog.isCancelled())
    {
      client.replaceWorkspaceVariables(editor.getProperties());
    }
  }
}
