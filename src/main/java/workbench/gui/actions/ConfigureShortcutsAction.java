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

import javax.swing.JFrame;

import workbench.WbManager;

import workbench.gui.settings.ShortcutEditor;

/**
 * Action to open the shortcut manager window.
 *
 * @see workbench.gui.settings.ShortcutEditor
 * @author Thomas Kellerer
 */
public class ConfigureShortcutsAction
  extends WbAction
{
  public ConfigureShortcutsAction()
  {
    super();
    initMenuDefinition("MnuTxtConfigureShortcuts");
    removeIcon();
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    final JFrame main = WbManager.getInstance().getCurrentWindow();
    ShortcutEditor editor = new ShortcutEditor(main);
    editor.showWindow();
  }
}
