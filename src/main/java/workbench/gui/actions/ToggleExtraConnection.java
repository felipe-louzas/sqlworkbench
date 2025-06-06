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

import workbench.resource.ResourceMgr;

import workbench.gui.MainWindow;

/**
 *
 * @author Thomas Kellerer
 */
public class ToggleExtraConnection
  extends CheckBoxAction
{
  private MainWindow window;

  public ToggleExtraConnection(MainWindow client)
  {
    super("MnuTxtUseExtraConn", null);
    this.setMenuItemName(ResourceMgr.MNU_TXT_FILE);
    this.setEnabled(false);
    this.window = client;
    checkState();
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    if (this.window == null) return;
    if (window.canUseSeparateConnection())
    {
      if (window.usesSeparateConnection())
      {
        this.window.disconnectCurrentPanel();
        this.setSwitchedOn(false);
      }
      else
      {
        this.window.createNewConnectionForCurrentPanel();
        this.setSwitchedOn(true);
      }
    }
  }

  public final void checkState()
  {
    if (this.window == null)
    {
      this.setEnabled(false);
      this.setSwitchedOn(false);
    }
    else
    {
      this.setEnabled(window.canUseSeparateConnection());
      this.setSwitchedOn(window.usesSeparateConnection());
    }
  }

  @Override
  public boolean useInToolbar()
  {
    return false;
  }
}
