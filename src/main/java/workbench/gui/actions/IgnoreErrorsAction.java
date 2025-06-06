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

import javax.swing.JToggleButton;
import javax.swing.JToolBar;

import workbench.resource.Settings;

import workbench.gui.components.WbToolbarButton;

/**
 * Toggle the "ignore errors" settings
 *
 * @author Thomas Kellerer
 */
public class IgnoreErrorsAction
  extends CheckBoxAction
{
  private JToggleButton toggleButton;

  public IgnoreErrorsAction()
  {
    super("MnuTxtIgnoreErrors", null);
    super.setSwitchedOn(Settings.getInstance().getIgnoreErrors());
    setIcon("ignore_error");
  }

  public JToggleButton createButton()
  {
    this.toggleButton = new JToggleButton(this);
    this.toggleButton.setText(null);
    this.toggleButton.setMargin(WbToolbarButton.SMALL_MARGIN);
    this.toggleButton.setIcon(getToolbarIcon());
    this.toggleButton.setSelected(isSwitchedOn());
    return this.toggleButton;
  }

  @Override
  public void addToToolbar(JToolBar aToolbar)
  {
    if (this.toggleButton == null) this.createButton();
    aToolbar.add(this.toggleButton);
  }

  @Override
  public void setSwitchedOn(boolean aFlag)
  {
    super.setSwitchedOn(aFlag);
    if (this.toggleButton != null)
    {
      this.toggleButton.setSelected(isSwitchedOn());
    }
    Settings.getInstance().setIgnoreErrors(isSwitchedOn());
  }

}
