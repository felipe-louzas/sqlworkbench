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
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.Action;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.KeyStroke;

import workbench.resource.PlatformShortcuts;
import workbench.resource.ResourceMgr;

import workbench.gui.sql.SqlPanel;

/**
 * Discard the file currently loaded in the SQL Editor.
 *
 * @author Thomas Kellerer
 */
public class FileDiscardAction
  extends WbAction
{
  private final SqlPanel client;

  public FileDiscardAction(SqlPanel aClient)
  {
    super();
    this.client = aClient;
    String desc = ResourceMgr.getDescription("MnuTxtFileDiscard", true);
    this.putValue(Action.SHORT_DESCRIPTION, desc);
    this.initMenuDefinition(ResourceMgr.getString("MnuTxtFileDiscard"), desc, KeyStroke.getKeyStroke(KeyEvent.VK_F4, PlatformShortcuts.getDefaultModifier()));
    this.setMenuItemName(ResourceMgr.MNU_TXT_FILE);
    this.setEnabled(aClient.hasFileLoaded());
  }

  @Override
  public void addToInputMap(InputMap im, ActionMap am)
  {
    super.addToInputMap(im, am);
    im.put(KeyStroke.getKeyStroke(KeyEvent.VK_F4, PlatformShortcuts.getDefaultModifier() | InputEvent.SHIFT_DOWN_MASK), this.getActionName());
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    this.client.closeFile(!isShiftPressed(e));
  }
}
