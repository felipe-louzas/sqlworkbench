/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2022, Thomas Kellerer, Matthias Melzner
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

import java.io.File;

import javax.swing.JTree;
import javax.swing.ToolTipManager;
import javax.swing.tree.TreePath;
import javax.swing.tree.TreeSelectionModel;

import workbench.gui.WbSwingUtilities;

import workbench.util.StringUtil;

/**
 *
 * @author Matthias Melzner
 */
public class FileTree
  extends JTree
{
  private final FileTreeLoader loader = new FileTreeLoader();
  private final FileTreeDragSource dragSource;

  public FileTree()
  {
    super();
    setModel(loader.getModel());
    setBorder(WbSwingUtilities.EMPTY_BORDER);
    setShowsRootHandles(true);
    setAutoscrolls(true);
    setScrollsOnExpand(true);
    setEditable(false);
    setRowHeight(0);
    setCellRenderer(new FileNodeRenderer());
    setExpandsSelectedPaths(true);
    getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
    ToolTipManager.sharedInstance().registerComponent(this);
    dragSource = new FileTreeDragSource(this);
  }

  public void setRootDir(File root)
  {
    this.loader.setRootDir(root);
  }

  public File getRootDir()
  {
    return loader.getRootDir();
  }

  public FileTreeLoader getLoader()
  {
    return this.loader;
  }

  public void load()
  {
    try
    {
      WbSwingUtilities.showWaitCursorOnWindow(this);
      this.loader.load();
      WbSwingUtilities.invokeLater(() -> {expandRow(0);});
    }
    finally
    {
      WbSwingUtilities.showDefaultCursorOnWindow(this);
    }
  }

  public void reload()
  {
    clear();
    load();
  }

  public void clear()
  {
    if (loader != null)
    {
      loader.clear();
      setModel(loader.getModel());
    }
  }

  public void expandNodes()
  {
    for (int i = 0; i < this.getRowCount(); i++)
    {
      this.expandRow(i);
    }
  }

  public File getSelectedFile()
  {
    TreePath path = getSelectionPath();
    if (path == null || path.getPathCount() == 0) return null;

    FileNode node = (FileNode)path.getLastPathComponent();
    return node.getFile();
  }

  public void loadFiltered(String text)
  {
    if (StringUtil.isBlank(text))
    {
      reload();
      return;
    }

    try
    {
      WbSwingUtilities.showWaitCursorOnWindow(this);
      clear();
      loader.loadFiltered(text);
      WbSwingUtilities.invokeLater(this::expandNodes);
    }
    finally
    {
      WbSwingUtilities.showDefaultCursorOnWindow(this);
    }
  }
}
