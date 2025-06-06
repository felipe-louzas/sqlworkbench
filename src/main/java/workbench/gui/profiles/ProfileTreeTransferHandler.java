/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0 (the "License")
 * that restricts the use for certain governments.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.sql-workbench.eu/manual/license.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.eu
 */
package workbench.gui.profiles;

import java.awt.Window;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.Transferable;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.gui.WbSwingUtilities;

/**
 *
 * @author Thomas Kellerer
 */
public class ProfileTreeTransferHandler
  extends TransferHandler
{
  private TreePath[] lastCutNodes = null;
  private ProfileTree myTree;

  public ProfileTreeTransferHandler(ProfileTree tree)
  {
    super();
    myTree = tree;
  }

  @Override
  public int getSourceActions(JComponent c)
  {
    return COPY_OR_MOVE;
  }

  @Override
  public Transferable createTransferable(JComponent c)
  {
    if (c instanceof ProfileTree)
    {
      TreePath[] paths = ((ProfileTree)c).getSelectionPaths();
      return new ProfileTreeTransferable(paths, (ProfileTree)c);
    }
    return null;
  }

  @Override
  public void exportDone(JComponent c, Transferable transferable, int action)
  {
    if (transferable == null) return;
    if (action != MOVE) return;

    if (!(c instanceof ProfileTree)) return;
    ProfileTree tree = (ProfileTree)c;

    try
    {
      TransferableProfileTreeNode profileNode = (TransferableProfileTreeNode)transferable.getTransferData(ProfileFlavor.FLAVOR);
      TreePath[] dropPath = profileNode.getPath();
      if (dropPath == null) return;

      for (TreePath treePath : dropPath)
      {
        DefaultMutableTreeNode node = (DefaultMutableTreeNode)treePath.getLastPathComponent();
        if (node.getParent() != null)
        {
          tree.getModel().removeNodeFromParent(node);
        }
      }
    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not process drop event", ex);
    }
  }

  private void removeCutNodes()
  {
    if (lastCutNodes == null) return;
    for (TreePath treePath : lastCutNodes)
    {
      TreeNode node = (TreeNode)treePath.getLastPathComponent();
      if (node instanceof ProfileNode)
      {
        myTree.getModel().deleteProfile((ProfileNode)node);
      }
      else if (node instanceof GroupNode)
      {
        myTree.getModel().deleteGroup((GroupNode)node);
      }
    }
  }

  @Override
  public boolean canImport(TransferSupport support)
  {
    if (!support.isDataFlavorSupported(ProfileFlavor.FLAVOR))
    {
      return false;
    }

    JTree.DropLocation location = (JTree.DropLocation)support.getDropLocation();
    TreePath path = location.getPath();
    if (path == null) return false;

    TreeNode node = (TreeNode)path.getLastPathComponent();
    if (!node.getAllowsChildren()) return false;

    Transferable transferable = support.getTransferable();
    return transferable != null;
  }

  @Override
  public boolean importData(TransferSupport support)
  {
    ProfileTree tree = (ProfileTree)support.getComponent();
    if (tree == null) return false;

    DefaultMutableTreeNode parentNode = null;
    if (support.isDrop())
    {
      JTree.DropLocation location = (JTree.DropLocation)support.getDropLocation();
      TreePath path = location.getPath();
      if (path == null) return false;

      parentNode = (DefaultMutableTreeNode)path.getLastPathComponent();
    }
    else
    {
      parentNode = tree.getSelectedGroupNode();
    }

    if (parentNode == null) return false;
    if (!(parentNode instanceof GroupNode)) return false;

    GroupNode targetGroup = (GroupNode)parentNode;

    int action = COPY;

    if (support.isDrop())
    {
      action = support.getDropAction();
    }

    try
    {
      Transferable transferable = support.getTransferable();
      if (transferable == null) return false;

      TransferableProfileTreeNode transferNode = (TransferableProfileTreeNode)transferable.getTransferData(ProfileFlavor.FLAVOR);
      List<DefaultMutableTreeNode> nodes = getTransferNodes(transferable);

      Window parent = SwingUtilities.getWindowAncestor(tree);
      ProfileTree sourceTree = (ProfileTree)WbSwingUtilities.findComponentByName(ProfileTree.class, transferNode.getSourceName(), parent);

      int targetAction = action;
      if (sourceTree != null && sourceTree != myTree)
      {
        // If this is a move between two trees, we need to use COPY
        // in order to not change the original group path of the dropped node
        // so that we can delete the profile from the source
        targetAction = COPY;
      }

      tree.handleDroppedNodes(nodes, targetGroup, targetAction);

      if (support.isDrop() && action == MOVE && sourceTree != null)
      {
        for (DefaultMutableTreeNode node : nodes)
        {
          if (node instanceof ProfileNode)
          {
            sourceTree.getModel().deleteProfile((ProfileNode)node);
          }
          else if (node instanceof GroupNode && sourceTree != myTree)
          {
            sourceTree.getModel().deleteGroup((GroupNode)node);
          }
        }
      }

      // for a Cut & Paste action we need to remove the stored nodes in the source tree
      if (!support.isDrop() && sourceTree != null)
      {
        ProfileTreeTransferHandler handler = (ProfileTreeTransferHandler)sourceTree.getTransferHandler();
        handler.removeCutNodes();
      }

      return true;
    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not process drop event", ex);
      return false;
    }
  }

  private List<DefaultMutableTreeNode> getTransferNodes(Transferable transferable)
  {
    List<DefaultMutableTreeNode> nodes = new ArrayList<>();
    TransferableProfileTreeNode transferNode = null;

    try
    {
      transferNode = (TransferableProfileTreeNode)transferable.getTransferData(ProfileFlavor.FLAVOR);
    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not obtain transferdata", ex);
      return nodes;
    }

    TreePath[] dropPath = transferNode.getPath();
    if (dropPath == null) return nodes;

    for (TreePath treePath : dropPath)
    {
      DefaultMutableTreeNode node = (DefaultMutableTreeNode)treePath.getLastPathComponent();
      nodes.add(node);
    }
    return nodes;
  }

  @Override
  public void exportToClipboard(JComponent comp, Clipboard clip, int action)
    throws IllegalStateException
  {
    // this changes a Cut & Paste action into a Copy & Paste action
    // so that the cut nodes are still visible in the tree until
    // pasted into their new location. The default MOVE action
    // removes the nodes immediately.
    if (action == MOVE)
    {
      lastCutNodes = ((ProfileTree)comp).getSelectionPaths();
    }
    else
    {
      lastCutNodes = null;
    }
    super.exportToClipboard(comp, clip, COPY);
  }

}
