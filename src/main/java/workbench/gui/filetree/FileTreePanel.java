/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2021, Thomas Kellerer, Matthias Melzner
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
package workbench.gui.filetree;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.File;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.tree.TreePath;

import workbench.interfaces.Reloadable;
import workbench.resource.IconMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.ConnectionProfile;

import workbench.gui.MainWindow;
import workbench.gui.WbSwingUtilities;
import workbench.gui.actions.ReloadAction;
import workbench.gui.actions.WbAction;
import workbench.gui.components.CloseIcon;
import workbench.gui.components.WbFileChooser;
import workbench.gui.components.WbPopupMenu;
import workbench.gui.components.WbSplitPane;
import workbench.gui.components.WbToolbar;
import workbench.gui.components.WbToolbarButton;
import workbench.gui.dbobjects.DbObjectSourcePanel;
import workbench.gui.dbobjects.objecttree.DbTreeSettings;
import workbench.gui.dbobjects.objecttree.TreePosition;
import workbench.gui.sql.SqlPanel;

import workbench.util.FileUtil;
import workbench.util.WbProperties;
import workbench.util.WbThread;

/**
 *
 * @author Matthias Melzner
 */
public class FileTreePanel
  extends JPanel
  implements Reloadable, ActionListener, MouseListener
{
  public static final String PROP_DIVIDER = "filetree.divider.location";
  public static final String PROP_ROOT_DIR = "filetree.rootdir";

  private FileTree tree;
  private JPanel toolPanel;
  public ReloadAction reload;
  private WbToolbarButton closeButton;
  private List<TreePath> expandedNodes;
  public DbObjectSourcePanel source;
  private MainWindow window;
  private JTextField filterText;
  private JButton selectDirectoryButton;
  private String workspaceDefaultDir;

  public FileTreePanel(MainWindow window)
  {
    super(new BorderLayout());
    this.window = window;
    tree = new FileTree();

    ConnectionProfile profile = window.getCurrentProfile();
    if (profile != null)
    {
      this.workspaceDefaultDir = profile.getDefaultDirectory();
    }
    tree.addMouseListener(this);
    JScrollPane scroll = new JScrollPane(tree);
    scroll.setBorder(WbSwingUtilities.EMPTY_BORDER);
    createToolbar();

    add(toolPanel, BorderLayout.PAGE_START);
    add(scroll, BorderLayout.CENTER);
  }

  private void createToolbar()
  {
    toolPanel = new JPanel(new GridBagLayout());
    GridBagConstraints gc = new GridBagConstraints();

    gc.gridx = 0;
    gc.gridy = 0;
    gc.weightx = 0.0;
    gc.fill = GridBagConstraints.NONE;
    gc.anchor = GridBagConstraints.LINE_START;
    gc.insets = new Insets(0, 0, 0, IconMgr.getInstance().getSizeForLabel() / 3);
    JLabel label = new JLabel(ResourceMgr.getString("LblSearchShortcut"));
    toolPanel.add(label, gc);

    gc.gridx ++;
    gc.weightx = 1.0;
    gc.fill = GridBagConstraints.HORIZONTAL;
    filterText = new JTextField();
    filterText.addActionListener((ActionEvent e) -> {search();});
    toolPanel.add(filterText, gc);

    WbToolbar bar = new WbToolbar();

    selectDirectoryButton = new WbToolbarButton(IconMgr.getInstance().getLabelIcon("open"));
    selectDirectoryButton.addActionListener(this);
    reload = new ReloadAction(this);
    reload.setUseLabelIconSize(true);

    bar.add(selectDirectoryButton);
    bar.add(reload);
    bar.addSeparator();

    CloseIcon icon = new CloseIcon(IconMgr.getInstance().getToolbarIconSize());
    icon.setUseLargeSize(true);
    closeButton = new WbToolbarButton(icon);
    closeButton.setActionCommand("close-panel");
    closeButton.addActionListener(this);
    closeButton.setRolloverEnabled(true);

    Dimension bs = selectDirectoryButton.getPreferredSize();
    int iconWidth = icon.getIconWidth()/2;
    int iconHeight = icon.getIconHeight()/2;
    int wmargin = (int)(bs.width/2) - iconWidth - 2;
    int hmargin = (int)(bs.height/2) - iconHeight - 2;
    closeButton.setMargin(new Insets(hmargin, wmargin, hmargin, wmargin));
    bar.add(closeButton);

    gc.gridx ++;
    gc.weightx = 0.0;
    gc.fill = GridBagConstraints.NONE;
    gc.insets = new Insets(0, 0, 0, 0);
    gc.anchor = GridBagConstraints.LINE_END;
    toolPanel.add(bar, gc);

  }

  public void loadInBackground()
  {
    WbThread t = new WbThread("FileTree loader")
    {
      @Override
      public void run()
      {
        tree.reload();
      }
    };
    t.start();
  }

  @Override
  public void reload()
  {
    filterText.setText("");
    resetExpanded();
    loadInBackground();
  }

  public void search()
  {
    tree.loadFiltered(filterText.getText());
  }

  private void closePanel()
  {
    Window frame = SwingUtilities.getWindowAncestor(this);
    if (frame instanceof MainWindow)
    {
      final MainWindow mainWin = (MainWindow) frame;
      EventQueue.invokeLater(mainWin::closeFileTree);
    }
  }

  private void selectNewRoot()
  {
    JFileChooser jf = new WbFileChooser();
    jf.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    jf.setMultiSelectionEnabled(false);
    File dir = tree.getLoader().getRootDir();
    if (dir == null)
    {
      dir = new File(".").getAbsoluteFile();
    }
    jf.setCurrentDirectory(dir);
    int answer = jf.showOpenDialog(SwingUtilities.getWindowAncestor(this));
    if (answer == JFileChooser.APPROVE_OPTION)
    {
      File newDir = jf.getSelectedFile();
      tree.getLoader().setRootDir(newDir);
      loadInBackground();
    }
  }
  @Override
  public void actionPerformed(ActionEvent evt)
  {
    if (evt.getSource() == selectDirectoryButton)
    {
      selectNewRoot();
    }

    if (evt.getSource() == closeButton)
    {
      closePanel();
    }
  }

  private void loadFile(File toSelect, boolean newTab)
  {
    if (toSelect == null) return;
    if (toSelect.isDirectory())
    {
      return;
    }

    String encodingToUse = FileUtil.detectFileEncoding(toSelect);
    if (encodingToUse == null)
    {
      encodingToUse = Settings.getInstance().getDefaultFileEncoding();
    }

    SqlPanel panel = null;
    if (newTab)
    {
      panel = (SqlPanel)window.addTab();
    }
    else
    {
      panel = window.getCurrentSqlPanel();
    }
    panel.readFile(toSelect, encodingToUse);
  }

  private void resetExpanded()
  {
    if (expandedNodes != null)
    {
      expandedNodes.clear();
      expandedNodes = null;
    }
  }

  private int getDividerLocation()
  {
    WbSplitPane split = (WbSplitPane)getParent();
    if (split == null)
    {
      return -1;
    }
    return split.getDividerLocation();
  }

  public void saveSettings(WbProperties props)
  {
    props.setProperty(PROP_DIVIDER + "." + getCurrentPosition().name(), getDividerLocation());
    File currentDir = tree.getRootDir();
    if (currentDir != null)
    {
      props.setProperty(PROP_ROOT_DIR, currentDir.getAbsolutePath());
    }
  }

  private TreePosition getCurrentPosition()
  {
    WbSplitPane split = (WbSplitPane)getParent();
    if (split == null) return DbTreeSettings.getDbTreePosition();
    if (split.getLeftComponent() == this) return TreePosition.left;
    return TreePosition.right;
  }

  public void restoreSettings(WbProperties props)
  {
    int location = props.getIntProperty(PROP_DIVIDER + "." + getCurrentPosition().name(), -1);
    if (location > -1)
    {
      WbSplitPane split = (WbSplitPane)getParent();
      if (split != null)
      {
        split.setDividerLocation(location);
      }
    }
    File dir = null;
    String path = props.getProperty(PROP_ROOT_DIR, workspaceDefaultDir);
    if (path == null)
    {
      dir = FileTreeSettings.getDefaultDirectory();
    }
    else
    {
      dir = new File(path);
    }
    tree.setRootDir(dir);
  }

  @Override
  public void mouseClicked(MouseEvent e)
  {
    if (SwingUtilities.isLeftMouseButton(e) && e.getClickCount() == 2)
    {
      TreePath p = tree.getClosestPathForLocation(e.getX(), e.getY());
      if (p == null) return;

      FileNode node = (FileNode)p.getLastPathComponent();
      File f = node.getFile();

      if (!f.isDirectory())
      {
        if ((e.getModifiersEx() & InputEvent.CTRL_DOWN_MASK) == InputEvent.CTRL_DOWN_MASK)
        {
          this.loadFile(f, true);
        }
        else
        {
          this.loadFile(f, false);
        }
      }
    }
    else if (SwingUtilities.isRightMouseButton(e) && e.getClickCount() == 1)
    {
      int x = e.getX();
      int y = e.getY();
      TreePath p = tree.getClosestPathForLocation(x, y);
      if (p == null) return;
      tree.setSelectionPath(p);

      JPopupMenu popup = createContextMenu();
      if (popup != null)
      {
        popup.show(tree, x, y);
      }
    }
  }

  private JPopupMenu createContextMenu()
  {
    JPopupMenu menu = new WbPopupMenu();
    menu.addPopupMenuListener(new PopupMenuListener()
    {
      @Override
      public void popupMenuWillBecomeVisible(PopupMenuEvent e)
      {
      }

      @Override
      public void popupMenuWillBecomeInvisible(PopupMenuEvent e)
      {
        menu.removeAll();
      }

      @Override
      public void popupMenuCanceled(PopupMenuEvent e)
      {
      }
    });

    WbAction openInSameTab = new WbAction()
    {
      @Override
      public void executeAction(ActionEvent e)
      {
        loadFile(tree.getSelectedFile(), false);
      }
    };
    openInSameTab.initMenuDefinition("MnuTxtOpenInSameTab");
    menu.add(openInSameTab);
    WbAction openInNewTab = new WbAction()
    {
      @Override
      public void executeAction(ActionEvent e)
      {
        loadFile(tree.getSelectedFile(), true);
      }
    };
    openInNewTab.initMenuDefinition("MnuTxtOpenInNewTab");
    menu.add(openInNewTab);
    return menu;
  }

  @Override
  public void mousePressed(MouseEvent e)
  {
  }

  @Override
  public void mouseReleased(MouseEvent e)
  {
  }

  @Override
  public void mouseEntered(MouseEvent e)
  {
  }

  @Override
  public void mouseExited(MouseEvent e)
  {
  }
}
