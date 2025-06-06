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
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.gui.sql.EditorPanel;

/**
 * Action to toggle the highlighting of the selected text for the current editor.
 *
 * @see Settings#getHighlightCurrentSelection()
 *
 * @author Thomas Kellerer
 */
public class ToggleSelectionHighlightAction
  extends CheckBoxAction
  implements PropertyChangeListener
{
  private final EditorPanel editor;

  public ToggleSelectionHighlightAction(EditorPanel panel)
  {
    super("MnuTxtHiliteSel");
    this.editor = panel;
    this.setMenuItemName(ResourceMgr.MNU_TXT_EDIT);
    Settings.getInstance().addPropertyChangeListener(this, Settings.PROPERTY_EDITOR_OCCURANCE_HIGHLIGHT);
    checkSwitchedOn();
  }

  private void checkSwitchedOn()
  {
    if (editor.isGlobalSelectionHighlight())
    {
      setSwitchedOn(Settings.getInstance().getHighlightCurrentSelection());
    }
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    super.executeAction(e);
    editor.setHighlightSelection(this.isSwitchedOn());
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt)
  {
    checkSwitchedOn();
  }

  @Override
  public void dispose()
  {
    super.dispose();
    Settings.getInstance().removePropertyChangeListener(this);
  }

  @Override
  public boolean useInToolbar()
  {
    return false;
  }
}
