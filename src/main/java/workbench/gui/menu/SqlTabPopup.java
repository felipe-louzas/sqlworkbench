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
package workbench.gui.menu;

import java.util.Optional;

import javax.swing.JPopupMenu;

import workbench.interfaces.MainPanel;

import workbench.gui.MainWindow;
import workbench.gui.actions.AddTabAction;
import workbench.gui.actions.CloseOtherTabsAction;
import workbench.gui.actions.FileDiscardAction;
import workbench.gui.actions.InsertTabAction;
import workbench.gui.actions.LockPanelAction;
import workbench.gui.actions.MoveSqlTabLeft;
import workbench.gui.actions.MoveSqlTabRight;
import workbench.gui.actions.NewDbExplorerPanelAction;
import workbench.gui.actions.OpenFileAction;
import workbench.gui.actions.OpenFileDirAction;
import workbench.gui.actions.RemoveTabAction;
import workbench.gui.actions.RenameTabAction;
import workbench.gui.actions.RestoreClosedTabAction;
import workbench.gui.actions.ToggleExtraConnection;
import workbench.gui.actions.clipboard.CopyFileNameAction;
import workbench.gui.sql.EditorPanel;
import workbench.gui.sql.SqlPanel;

/**
 * @author  Thomas Kellerer
 */
public class SqlTabPopup
  extends JPopupMenu
{
  public SqlTabPopup(MainWindow mainWindow, int forIndex)
  {
    super();
    AddTabAction add = new AddTabAction(mainWindow);
    this.add(add);
    InsertTabAction insert = new InsertTabAction(mainWindow);
    this.add(insert);

    NewDbExplorerPanelAction newDbExp = new NewDbExplorerPanelAction(mainWindow, "MnuTxtAddExplorerPanel");
    newDbExp.removeIcon();
    add(newDbExp);

    addSeparator();
    int currentPanel = mainWindow.getCurrentPanelIndex();
    RemoveTabAction remove = new RemoveTabAction(mainWindow);
    if (currentPanel != forIndex)
    {
      remove.setAccelerator(null);
      remove.setAlternateAccelerator(null);
    }
    remove.setEnabled(mainWindow.canCloseTab(forIndex));
    this.add(remove);

    Optional<MainPanel> panel = mainWindow.getPanel(forIndex);

    CloseOtherTabsAction closeOthers = new CloseOtherTabsAction(mainWindow, forIndex);
    this.add(closeOthers);

    RestoreClosedTabAction restoreClosedTabAction = new RestoreClosedTabAction(mainWindow);
    this.add(restoreClosedTabAction.getMenuItem());

    if (mainWindow.canRenameTab(forIndex))
    {
      RenameTabAction rename = new RenameTabAction(mainWindow, forIndex);
      this.add(rename);
    }

    LockPanelAction lock = new LockPanelAction(panel);

    this.add(lock.getMenuItem());
    lock.setSwitchedOn(panel.map(MainPanel::isLocked).orElse(false));

    this.addSeparator();

    MoveSqlTabLeft moveLeft = new MoveSqlTabLeft(mainWindow);
    moveLeft.setEnabled(forIndex > 0);
    this.add(moveLeft);
    int lastIndex = mainWindow.getTabCount();
    MoveSqlTabRight moveRight = new MoveSqlTabRight(mainWindow);
    moveRight.setEnabled(forIndex < lastIndex);
    this.add(moveRight);

    if (mainWindow.canUseSeparateConnection())
    {
      this.addSeparator();
      ToggleExtraConnection toggle = new ToggleExtraConnection(mainWindow);
      this.add(toggle.getMenuItem());
    }

    this.addSeparator();

    MainPanel mpanel = panel.orElse(null);
    if (mpanel instanceof SqlPanel)
    {
      SqlPanel spanel = (SqlPanel)mpanel;

      EditorPanel editor = spanel.getEditor();

      this.add(editor.getFileSaveAction());
      this.add(editor.getFileSaveAsAction());
      this.add(new OpenFileAction(mainWindow, forIndex));

      if (editor.hasFileLoaded())
      {
        this.add(editor.getReloadAction());
        FileDiscardAction discard = new FileDiscardAction(spanel);
        discard.removeIcon();
        this.add(discard);
        this.addSeparator();
        this.add(new CopyFileNameAction(editor, true));
        this.add(new CopyFileNameAction(editor, false));
        this.add(new OpenFileDirAction(editor));
      }
    }
    else
    {
      this.add(new OpenFileAction(mainWindow, forIndex));
    }
  }

}
