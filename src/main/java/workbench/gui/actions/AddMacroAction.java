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

import java.awt.Toolkit;
import java.awt.event.ActionEvent;

import javax.swing.SwingUtilities;

import workbench.interfaces.TextSelectionListener;
import workbench.resource.ResourceMgr;

import workbench.gui.components.ValidatingDialog;
import workbench.gui.macros.AddMacroPanel;
import workbench.gui.sql.EditorPanel;

import workbench.sql.macros.MacroDefinition;
import workbench.sql.macros.MacroGroup;
import workbench.sql.macros.MacroManager;

import workbench.util.StringUtil;

/**
 * Action to add a new macro.
 *
 * @see workbench.sql.macros.MacroManager
 * @author Thomas Kellerer
 */
public class AddMacroAction
  extends WbAction
  implements TextSelectionListener
{
  private EditorPanel client;
  private final int macroClientId;

  public AddMacroAction(int macroId)
  {
    super();
    this.macroClientId = macroId;
    this.setIcon(null);
    this.setMenuItemName(ResourceMgr.MNU_TXT_MACRO);
    this.initMenuDefinition("MnuTxtAddMacro");
    setEnabled(false);
  }

  public void setClient(EditorPanel panel)
  {
    if (this.client != null)
    {
      this.client.removeSelectionListener(this);
    }
    this.client = panel;
    if (this.client == null)
    {
      this.setEnabled(false);
    }
    else
    {
      this.client.addSelectionListener(this);
      this.setEnabled(client.isTextSelected());
    }
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    String text = client.getSelectedText();
    if (StringUtil.isBlank(text))
    {
      Toolkit.getDefaultToolkit().beep();
      return;
    }

    AddMacroPanel panel = new AddMacroPanel(this.macroClientId);

    ValidatingDialog dialog = ValidatingDialog.createDialog(
      SwingUtilities.getWindowAncestor(client),
      panel,
      ResourceMgr.getString("TxtGetMacroNameWindowTitle"), null, 0, true);

    dialog.addWindowListener(panel);
    dialog.setVisible(true);

    if (!dialog.isCancelled())
    {
      MacroGroup group = panel.getSelectedGroup();
      String name = panel.getMacroName();
      if (StringUtil.isNotBlank(name) && group != null)
      {
        MacroManager.getInstance().getMacros(macroClientId).addMacro(group, new MacroDefinition(name, text));
      }
    }
  }

  @Override
  public void selectionChanged(int newStart, int newEnd)
  {
    boolean selected = (newStart > -1 && newEnd > newStart);
    this.setEnabled(selected);
  }
}
