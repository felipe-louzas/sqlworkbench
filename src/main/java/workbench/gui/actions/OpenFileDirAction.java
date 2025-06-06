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

import java.awt.Desktop;
import java.awt.event.ActionEvent;
import java.io.File;

import workbench.interfaces.TextFileContainer;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.gui.WbSwingUtilities;

/**
 * An action to open the directory of the currently loaded editor file.
 *
 * @see Desktop#open(java.io.File)
 *
 * @author Thomas Kellerer
 */
public class OpenFileDirAction
  extends WbAction
{
  private final TextFileContainer editor;

  public OpenFileDirAction(TextFileContainer textContainer)
  {
    this.editor = textContainer;
    this.initMenuDefinition("MnuTxtOpenFileDir");
    boolean hasFile = this.editor.getCurrentFile() != null;
    boolean desktopAvailable = false;
    if (hasFile)
    {
      desktopAvailable = Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN);
      if (!desktopAvailable)
      {
        LogMgr.logWarning(new CallerInfo(){}, "Desktop or Desktop.open() not supported!");
      }
    }
    this.setEnabled(hasFile && desktopAvailable);
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    if (editor == null) return;

    File file = editor.getCurrentFile();
    if (file == null) return;

    File dir = file.getParentFile();

    if (dir != null)
    {
      try
      {
        Desktop.getDesktop().open(dir);
      }
      catch (Exception io)
      {
        WbSwingUtilities.showErrorMessage(io.getLocalizedMessage());
      }
    }
  }

  @Override
  public boolean useInToolbar()
  {
    return false;
  }

}
