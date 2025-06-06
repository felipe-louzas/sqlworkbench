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
package workbench.gui.actions.clipboard;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.KeyStroke;

import workbench.interfaces.ClipboardSupport;
import workbench.resource.PlatformShortcuts;
import workbench.resource.ResourceMgr;

import workbench.gui.actions.WbAction;

import workbench.util.MacOSHelper;

/**
 * Action to copy the contents of an entry field into the clipboard
 *
 * @author Thomas Kellerer
 */
public class CopyAction
  extends WbAction
{
  private ClipboardSupport client;

  public CopyAction(ClipboardSupport aClient)
  {
    super();
    this.client = aClient;
    KeyStroke alternateKey = null;
    if (!MacOSHelper.isMacOS())
    {
      alternateKey = KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, KeyEvent.CTRL_DOWN_MASK);
    }
    initMenuDefinition("MnuTxtCopy", PlatformShortcuts.getDefaultCopyShortcut(), alternateKey);
    this.setIcon("copy");
    this.setMenuItemName(ResourceMgr.MNU_TXT_EDIT);
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    this.client.copy();
  }
}
