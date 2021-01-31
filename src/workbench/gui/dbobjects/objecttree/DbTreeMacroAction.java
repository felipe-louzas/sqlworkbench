/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2021 Thomas Kellerer.
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
package workbench.gui.dbobjects.objecttree;

import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.TableIdentifier;

import workbench.gui.actions.WbAction;
import workbench.gui.macros.QueryMacroParser;

import workbench.sql.macros.MacroDefinition;

import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
class DbTreeMacroAction
  extends WbAction
{
  private final MacroDefinition macro;
  private final DbTreePanel tree;

  public DbTreeMacroAction(DbTreePanel panel, MacroDefinition def)
  {
    super();
    this.macro = def;
    this.tree = panel;
    String title = def.getName();
    setMenuText(title);
    String desc = macro.getDisplayTooltip();
    if (desc == null)
    {
      desc = ResourceMgr.getDescription("MnuTxtRunMacro", true);
      desc = StringUtil.replace(desc, "%macro%", "'" + macro.getName() + "'");
    }
    setTooltip(desc);
    setIcon(null);
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    if (this.tree == null || this.macro == null) return;
    List<ObjectTreeNode> nodes = tree.getSelectedNodes();
    if (nodes.isEmpty()) return;

    TableIdentifier table = null;
    List<ColumnIdentifier> columns = new ArrayList<>();
    Optional<ObjectTreeNode> obj = nodes.stream().filter(n -> n.getDbObject() instanceof TableIdentifier).findFirst();
    if (obj.isPresent())
    {
      table = (TableIdentifier)obj.get().getDbObject();
    }

    for (ObjectTreeNode node : nodes)
    {
      if (node.getDbObject() instanceof ColumnIdentifier)
      {
        columns.add((ColumnIdentifier)node.getDbObject());
      }
    }

    if (columns.size() == nodes.size())
    {
      // only columns selected, get the table from the first column's parent
      // the direct parent of the column node is the "Columns" folder.
      // The parent of that must be the actual table node
      ObjectTreeNode folder = nodes.get(0).getParent();
      if (folder != null)
      {
        ObjectTreeNode parent = folder.getParent();
        if (parent.getDbObject() instanceof TableIdentifier)
        {
          table = (TableIdentifier)parent.getDbObject();
        }
      }
    }
    QueryMacroParser parser = new QueryMacroParser(macro);
    parser.setTable(table);
    parser.setColumn(columns);
    String sql = parser.getSQL(tree.getConnection());
    LogMgr.logDebug(new CallerInfo(){}, "Running DbTree macro: " + sql);
    tree.getMacroClient().executeMacroSql(sql, false, macro.isAppendResult());
  }

  @Override
  public boolean useInToolbar()
  {
    return false;
  }

}
