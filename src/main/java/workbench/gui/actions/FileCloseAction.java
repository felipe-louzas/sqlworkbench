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

import workbench.WbManager;

import workbench.gui.MainWindow;

/**
 * Close the current main window.
 *
 * If the last window is closed, the application is terminated.
 *
 * @see WbManager#closeMainWindow(workbench.gui.MainWindow)
 * @see FileExitAction
 *
 * @author Thomas Kellerer
 */
public class FileCloseAction
  extends WbAction
{
  private MainWindow window;

  public FileCloseAction(MainWindow toClose)
  {
    super();
    window = toClose;
    this.initMenuDefinition("MnuTxtFileCloseWin");
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    WbManager.getInstance().closeMainWindow(window);
  }
}
