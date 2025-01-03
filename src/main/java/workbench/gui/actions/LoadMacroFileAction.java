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

import workbench.sql.macros.MacroFileSelector;
import workbench.sql.macros.MacroManager;

import workbench.util.WbFile;

/**
 * @author Thomas Kellerer
 */
public class LoadMacroFileAction
  extends WbAction
{
  private WbFile macroFile;
  private final int macroClientId;

  public LoadMacroFileAction(int clientId, WbFile file)
  {
    super();
    macroFile = file;
    macroClientId = clientId;
    this.setMenuText(macroFile.getFileName());
    this.setTooltip(macroFile.getFullPath());
    this.setIcon(null);
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    MacroFileSelector selector = new MacroFileSelector();
    if (selector.canLoadMacros(macroClientId))
    {
      MacroManager.getInstance().loadMacros(macroClientId, macroFile);
    }
  }

  @Override
  public boolean useInToolbar()
  {
    return false;
  }
}
