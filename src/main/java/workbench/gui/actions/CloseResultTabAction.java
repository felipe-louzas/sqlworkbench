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
package workbench.gui.actions;

import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;

import javax.swing.KeyStroke;

import workbench.resource.PlatformShortcuts;
import workbench.resource.ResourceMgr;

import workbench.gui.sql.SqlPanel;

/**
 * An action to close the currently selected result tab of a SqlPanel.
 *
 * @author Thomas Kellerer
 */
public class CloseResultTabAction
  extends WbAction
{
  private final SqlPanel panel;
  private final int tabIndex;
  public CloseResultTabAction(SqlPanel sqlPanel)
  {
    this(sqlPanel, -1);
  }
  public CloseResultTabAction(SqlPanel sqlPanel, int index)
  {
    super();
    panel = sqlPanel;
    this.initMenuDefinition("MnuTxtCloseResultTab", KeyStroke.getKeyStroke(KeyEvent.VK_K, PlatformShortcuts.getDefaultModifier() | InputEvent.SHIFT_DOWN_MASK));
    this.setMenuItemName(ResourceMgr.MNU_TXT_DATA);
    this.setIcon(null);
    this.setEnabled(panel.getCurrentResult() != null);
    this.tabIndex = index;
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    if (tabIndex == -1)
    {
      panel.closeCurrentResult();
    }
    else
    {
      panel.closeResult(tabIndex);
    }
  }

}
