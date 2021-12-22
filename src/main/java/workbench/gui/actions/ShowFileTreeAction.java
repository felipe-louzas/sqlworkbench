package workbench.gui.actions;

import java.awt.event.ActionEvent;

import workbench.gui.MainWindow;
import workbench.gui.filetree.FileTreePanel;

public class ShowFileTreeAction
  extends WbAction
{
  private MainWindow mainWin;

  public ShowFileTreeAction(MainWindow mainWin)
  {
    super();
    this.mainWin = mainWin;
    initMenuDefinition("MnuTxtNewFileTreeWindow");
    setEnabled(true);
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    if (mainWin.isDbTreeVisible())
    {
      mainWin.closeDbTree();
    }

    if (mainWin.isFileTreeVisible())
    {
      FileTreePanel fileTree = mainWin.getFileTree();
      fileTree.requestFocusInWindow();
    }
    else
    {
      mainWin.showFileTree(true);
    }
  }
}
