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

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeModel;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.gui.WbSwingUtilities;

import workbench.util.StringUtil;
import workbench.util.WbFile;

/**
 *
 * @author Matthias Melzner
 */
public class FileTreeLoader
{
  private final FileNode root = new FileNode(true);
  private File rootDir;
  private final DefaultTreeModel model;
  private final Set<String> excludedFiles = new TreeSet<>(String::compareToIgnoreCase);
  private final Set<String> excludedExtensions = new TreeSet<>(String::compareToIgnoreCase);

  public FileTreeLoader()
  {
    this(FileTreeSettings.getDefaultDirectory());
  }

  public FileTreeLoader(File dir)
  {
    setRootDir(dir);
    model = new DefaultTreeModel(root);
    excludedFiles.addAll(FileTreeSettings.getFilesToExclude());
    excludedExtensions.addAll(FileTreeSettings.getExtensionsToExclude());
  }

  public void load()
  {
    createChildren(root, rootDir);
    WbSwingUtilities.invoke(() -> {model.nodeStructureChanged(root);});
  }

  public void createChildren(DefaultMutableTreeNode parent, File folder)
  {
    List<File> files = Arrays.asList(folder.listFiles());
    files.sort(getComparator());

    for (File fileEntry : files)
    {
      if (excludeFile(fileEntry)) continue;
      FileNode node = new FileNode(fileEntry);
      parent.add(node);
      node.setAllowsChildren(fileEntry.isDirectory());
      if (fileEntry.isDirectory())
      {
        createChildren(node, fileEntry);
      }
    }
  }

  public void loadFiltered(String text)
  {
    this.clear();
    this.createChildrenFiltered(root, rootDir, text);
    model.nodeStructureChanged(root);
  }

  private void createChildrenFiltered(DefaultMutableTreeNode parent, File folder, String text)
  {
    List<File> files = Arrays.asList(folder.listFiles());
    files.sort(getComparator());

    for (File fileEntry : files)
    {
      if (excludeFile(fileEntry)) continue;
      FileNode node = new FileNode(fileEntry);

      if (fileEntry.isDirectory())
      {
        node.setAllowsChildren(true);
        if (fileEntry.getName().toLowerCase().contains(text.toLowerCase()))
        {
          parent.add(node);
          createChildren(node, fileEntry);
        }

        createChildrenFiltered(node, fileEntry, text);
        if (node.getChildCount() > 0)
        {
          parent.add(node);
        }
      }
      else
      {
        node.setAllowsChildren(false);
        if (fileEntry.getName().toLowerCase().contains(text.toLowerCase()))
        {
          parent.add(node);
        }
      }
    }
  }

  private boolean excludeFile(File f)
  {
    if (f == null) return true;
    if (excludedFiles.contains(f.getName())) return true;
    WbFile wb = new WbFile(f);
    if (f.isDirectory()) return false;

    String fileExt = wb.getExtension();
    if (StringUtil.isBlank(fileExt)) return false;
    
    if (excludedExtensions.contains(fileExt)) return true;
    return false;
  }

  public void setRootDir(File newRoot)
  {
    if (newRoot == null || !newRoot.exists()) return;
    try
    {
      this.rootDir = newRoot.getCanonicalFile();
    }
    catch (Throwable th)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not get canonical file from " + newRoot, th);
      this.rootDir = newRoot.getAbsoluteFile();
    }
    root.setUserObject(rootDir);
  }

  private Comparator<File> getComparator()
  {
    return (File f1, File f2) ->
    {
      if (f1.isDirectory() && !f2.isDirectory()) return -1;
      if (!f1.isDirectory() && f2.isDirectory()) return 1;
      return f1.getName().toLowerCase().compareTo(f2.getName().toLowerCase());
    };
  }

  public File getRootDir()
  {
    return this.rootDir;
  }

  public TreeModel getModel()
  {
    return this.model;
  }

  public void clear()
  {
    root.removeAllChildren();
    model.nodeStructureChanged(root);
  }
}
