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
package workbench.gui.macros;

import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreePath;

/**
 *
 * @author Thomas Kellerer
 */
public class MacroTreeNode
  extends DefaultMutableTreeNode
{
  public MacroTreeNode(Object dataObject)
  {
    super(dataObject);
  }

  public MacroTreeNode(Object dataObject, boolean allowsChildren)
  {
    super(dataObject, allowsChildren);
  }

  public Object getDataObject()
  {
    return getUserObject();
  }

  public DragType getDropType(TreePath[] source)
  {
    if (source == null) return DragType.none;

    boolean sourceIsGroup = true;
    boolean sourceBelongsToUs = false;

    for (TreePath path : source)
    {
      MacroTreeNode node = (MacroTreeNode)path.getLastPathComponent();
      sourceIsGroup = sourceIsGroup && node.getAllowsChildren();
      if (!sourceBelongsToUs && getAllowsChildren() && isNodeChild(node))
      {
        sourceBelongsToUs = true;
      }
    }
    if (sourceBelongsToUs) return DragType.none;

    if (getAllowsChildren())
    {
      if (sourceIsGroup) return DragType.reorderItems;
      return DragType.moveItems;
    }
    else if (!sourceIsGroup)
    {
      return DragType.reorderItems;
    }
    else
    {
      return DragType.none;
    }
  }
}
