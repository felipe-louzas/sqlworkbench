/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.swing.tree.DefaultMutableTreeNode;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.CatalogIdentifier;
import workbench.db.ColumnIdentifier;
import workbench.db.DbObject;
import workbench.db.IndexDefinition;
import workbench.db.ProcedureDefinition;
import workbench.db.SchemaIdentifier;
import workbench.db.TableIdentifier;
import workbench.db.TriggerDefinition;
import workbench.db.TriggerLevel;
import workbench.db.WbConnection;

import workbench.storage.filter.ColumnExpression;

import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;
import workbench.util.WbNumberFormatter;

/**
 *
 * @author Thomas Kellerer
 */
public class ObjectTreeNode
  extends DefaultMutableTreeNode
  implements Serializable
{
  private final Set<String> typesWithChildren = CollectionUtil.caseInsensitiveSet(
    "database", "catalog", "schema", "table", "view", "materialized view", "type", "package", "enum", "index", "procedure");
  private String nodeType;
  private String nodeName;
  private boolean isLoaded;
  private Long rowCount;
  private int originalIndex;
  private final List<ObjectTreeNode> filteredNodes = new ArrayList<>();
  private DbObject originalObject;
  private String display;
  private String tooltip;
  private String iconKey;
  private String iconKeyOpen;
  private String objectSource;

  public ObjectTreeNode(DbObject dbo)
  {
    super(dbo);
    nodeType = dbo.getObjectType();
    nodeName = dbo.getObjectName();
    allowsChildren = false;
  }

  public ObjectTreeNode(String name, String type)
  {
    super();
    nodeType = type;
    nodeName = name;
  }

  public void setNodeType(String type)
  {
    this.nodeType = type;
  }

  public void setNameAndType(String name, String type)
  {
    nodeType = type;
    nodeName = name;
  }

  public void setIconKey(String iconKey)
  {
    this.iconKey = iconKey;
  }

  public void setIconKeyOpen(String iconKeyOpen)
  {
    this.iconKeyOpen = iconKeyOpen;
  }

  public String getIconKey()
  {
    return iconKey;
  }

  public String getIconKeyOpen()
  {
    return iconKeyOpen;
  }

  public String getNodeName()
  {
    return nodeName;
  }

  public void setDisplay(String text)
  {
    this.display = text;
  }

  public void setChildrenLoaded(boolean flag)
  {
    isLoaded = flag;
  }

  public String getObjectSource()
  {
    return objectSource;
  }

  public void setObjectSource(String source)
  {
    this.objectSource = source;
  }

  public boolean isFKTable()
  {
    if (getDbObject() == null) return false;
    ObjectTreeNode pNode = getParent();
    if (pNode == null) return false;
    return TreeLoader.TYPE_FK_LIST.equalsIgnoreCase(pNode.getType()) || TreeLoader.TYPE_REF_LIST.equals(pNode.getType());
  }

  public boolean isNamespace()
  {
    return isSchemaNode() || isCatalogNode();
  }

  public boolean isSchemaNode()
  {
    if (getDbObject() instanceof SchemaIdentifier) return true;
    return getType().equalsIgnoreCase(TreeLoader.TYPE_SCHEMA);
  }

  public boolean isCatalogNode()
  {
    if (getDbObject() instanceof CatalogIdentifier) return true;
    return getType().equalsIgnoreCase(TreeLoader.TYPE_CATALOG);
  }

  public boolean childrenAreLoaded()
  {
    if (!this.isLoaded) return false;
    int count = getChildCount();
    for (int i=0; i < count; i ++)
    {
      ObjectTreeNode child = getChildAt(i);
      if (!child.isLoaded) return false;
    }
    return true;
  }

  public boolean isLoaded()
  {
    return isLoaded;
  }

  public void setRowCount(Long count)
  {
    rowCount = count;
  }

  @Override
  public void removeAllChildren()
  {
    super.removeAllChildren();
    isLoaded = false;
    filteredNodes.clear();
  }

  public boolean canHaveChildren()
  {
    if (getAllowsChildren()) return true;
    return getType() != null && typesWithChildren.contains(getType());
  }

  public ObjectTreeNode getChildByType(String nodeType)
  {
    for (int i=0; i < getChildCount(); i++)
    {
      ObjectTreeNode child = getChildAt(i);
      if (child != null && nodeType.equals(child.getType())) return child;
    }
    return null;
  }

  @Override
  public ObjectTreeNode getChildAt(int index)
  {
    return (ObjectTreeNode)super.getChildAt(index);
  }

  @Override
  public ObjectTreeNode getParent()
  {
    return (ObjectTreeNode)super.getParent();
  }

  @Override
  public boolean isLeaf()
  {
    return !allowsChildren;
  }

  public DbObject getDbObject()
  {
    return (DbObject)getUserObject();
  }

  public String getType()
  {
    if (getDbObject() == null)
    {
      return nodeType;
    }
    if (getDbObject() instanceof CatalogIdentifier)
    {
      return TreeLoader.TYPE_CATALOG;
    }
    return getDbObject().getObjectType();
  }

  public String getName()
  {
    DbObject db = getDbObject();
    if (db == null)
    {
      return nodeName;
    }
    return db.getObjectName();
  }

  public boolean isChanged()
  {
    return originalObject != null;
  }

  public void resetChanged()
  {
    originalObject = null;
  }

  private DbObject copyUserObject()
  {
    DbObject dbo = getDbObject();
    if (dbo == null) return null;
    if (dbo instanceof TableIdentifier)
    {
      return ((TableIdentifier)dbo).createCopy();
    }
    if (dbo instanceof ColumnIdentifier)
    {
      return ((ColumnIdentifier)dbo).createCopy();
    }
    if (dbo instanceof SchemaIdentifier)
    {
      SchemaIdentifier copy = new SchemaIdentifier(dbo.getSchema());
      copy.setCatalog(dbo.getCatalog());
      return copy;
    }
    if (dbo instanceof CatalogIdentifier)
    {
      CatalogIdentifier copy = new CatalogIdentifier(dbo.getCatalog());
      return copy;
    }
    if (dbo instanceof IndexDefinition)
    {
      return ((IndexDefinition)dbo).createCopy();
    }
    return null;
  }

  @Override
  public void setUserObject(Object newName)
  {
    if (newName instanceof String)
    {
      // this happens when a node is edited manually in the tree
      originalObject = copyUserObject();
      DbObject dbo = getDbObject();
      String name = (String)newName;
      dbo.setName(name);
    }
    else
    {
      super.setUserObject(newName);
    }
  }

  @Override
  public String toString()
  {
    if (display != null)
    {
      return display;
    }

    DbObject dbo = getDbObject();

    if (dbo == null)
    {
      return nodeName;
    }

    if (dbo instanceof ColumnIdentifier)
    {
      ColumnIdentifier col = (ColumnIdentifier)dbo;
      String name = col.getColumnName();
      String type = col.getDbmsType();
      if (DbTreeSettings.removeColumnQuotesForDisplay())
      {
        name = SqlUtil.removeObjectQuotes(name);
      }

      if (StringUtil.isBlank(type))
      {
        return name;
      }

      if (!col.isNullable())
      {
        name = "<b>" + name + "</b>";
        type = type + " NOT NULL";
      }
      return "<html>" + name + " - <tt>" + type + "</tt></html>";
    }
    if (dbo instanceof IndexDefinition)
    {
      IndexDefinition idx = (IndexDefinition)dbo;
      if (idx.isPrimaryKeyIndex())
      {
        return idx.getName() + " (PK)";
      }
      if (idx.isUnique())
      {
        return idx.getName() + " (UNIQUE)";
      }
    }
    if (dbo instanceof ProcedureDefinition)
    {
      ProcedureDefinition proc = (ProcedureDefinition)dbo;
      return proc.getDisplayName();
    }
    if (dbo instanceof TableIdentifier && rowCount != null)
    {
      return dbo.getObjectName() + " (" + getRowCountAsString() + ")";
    }
    return dbo.getObjectName();
  }

  private String getRowCountAsString()
  {
    String formatString = DbTreeSettings.getRowCountFormatString();
    try
    {
      if (formatString != null)
      {
        String groupSymbol = DbTreeSettings.getRowCountGroupSymbol();
        String decimalSymbol = Settings.getInstance().getDecimalSymbol();

        WbNumberFormatter formatter = new WbNumberFormatter(formatString, decimalSymbol.charAt(0), groupSymbol.charAt(originalIndex));
        return formatter.format(rowCount);
      }
      else if (DbTreeSettings.useIntegerFormatterForRowCount())
      {
        WbNumberFormatter formatter = Settings.getInstance().createDefaultIntegerFormatter();
        return formatter.format(rowCount);
      }
    }
    catch (Throwable th)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not format row count", th);
    }
    return rowCount.toString();
  }

  public void setTooltip(String tip)
  {
    this.tooltip = StringUtil.trimToNull(tip);
  }

  public String getTooltip()
  {
    if (tooltip != null)
    {
      return tooltip;
    }

    DbObject dbo = getDbObject();
    if (dbo == null)
    {
      return null;
    }

    if (dbo instanceof TriggerDefinition)
    {
      TriggerDefinition trg = (TriggerDefinition)dbo;
      String tip = null;
      if (trg.getTriggerType() != null && trg.getTriggerEvent() != null)
      {
        tip = trg.getTriggerType() + " " + trg.getTriggerEvent();
        if (trg.getRelatedTable() != null)
        {
          tip += " ON " + trg.getRelatedTable().getTableName();
        }
        TriggerLevel level = trg.getLevel();
        if (level == TriggerLevel.row)
        {
          tip += " FOR EACH ROW";
        }
        else if (level == TriggerLevel.statement)
        {
          tip += " FOR EACH STATEMENT";
        }
      }
      else
      {
        tip = trg.getObjectType();
      }
      return tip;
    }
    else if (dbo instanceof ColumnIdentifier)
    {
      if (TreeLoader.TYPE_PROC_PARAMETER.equals(nodeType))
      {
        return ((ColumnIdentifier)dbo).getArgumentMode();
      }
      return getColumnTooltip((ColumnIdentifier)dbo);
    }
    String remarks = dbo.getComment();
    if (StringUtil.isNotBlank(remarks))
    {
      return remarks;
    }
    return dbo.getObjectType();
  }

  private String getColumnTooltip(ColumnIdentifier col)
  {
    String defaultValue = null;
    if (StringUtil.isNotBlank(col.getDefaultValue()))
    {
      defaultValue = "DEFAULT " + col.getDefaultValue();
    }
    String comment = col.getComment();
    String tip = null;

    if (StringUtil.isNotEmpty(comment))
    {
      tip = "<html>" + (defaultValue == null ? "" : defaultValue + "<br>") + comment + "</html>";
    }
    else
    {
      tip = defaultValue;
    }
    return tip;
  }

  public boolean isFiltered()
  {
    return CollectionUtil.isNonEmpty(filteredNodes);
  }

  public boolean applyFilter(ColumnExpression searchTerm)
  {
    resetFilter();

    if (searchTerm == null) return false;

    int count = getChildCount();
    if (count == 0) return false;

    for (int i = 0; i < count; i++)
    {
      ObjectTreeNode child = getChildAt(i);

      if (child.isCatalogNode() || child.isSchemaNode()) continue;

      DbObject dbo = child.getDbObject();
      if (dbo != null)
      {
        String name = dbo.getObjectName();
        boolean matches = searchTerm.evaluate(name);
        if (!matches)
        {
          child.originalIndex = i;
          filteredNodes.add(child);
        }
      }
    }

    for (ObjectTreeNode node : filteredNodes)
    {
      remove(node);
    }
    return filteredNodes.size() > 0;
  }

  public void resetFilter()
  {
    if (filteredNodes.isEmpty()) return;
    for (ObjectTreeNode node : filteredNodes)
    {
      if (node.originalIndex > getChildCount())
      {
        add(node);
      }
      else
      {
        insert(node, node.originalIndex);
      }
    }
    filteredNodes.clear();
  }

  public String displayString()
  {
    return nodeType + ": " + getName();
  }

  public String getLocationInfo()
  {
    if (this.isSchemaNode())
    {
      return getDbObject().toString();
    }

    if (this.isCatalogNode())
    {
      return getName();
    }

    String result = "";
    if (getDbObject() != null && !isFkTable())
    {
      result = SqlUtil.removeObjectQuotes(getDbObject().getObjectName());
    }
    ObjectTreeNode parentNode = getParent();
    while (parentNode != null)
    {
      if (parentNode.getDbObject() != null)
      {
        String name = SqlUtil.removeObjectQuotes(parentNode.getDbObject().getObjectName());
        if (result.isEmpty())
        {
          result = name;
        }
        else
        {
          result = name + "." + result;
        }
      }
      parentNode = parentNode.getParent();
    }
    return result;
  }

  private boolean isFkTable()
  {
    if (this.getParent() == null) return false;
    String type = getParent().getType();
    return (TreeLoader.TYPE_FK_DEF.equals(type) || TreeLoader.TYPE_FK_LIST.equals(type) || TreeLoader.TYPE_REF_LIST.equals(type));
  }

  public boolean loadChildren(WbConnection connection, TreeLoader loader)
  {
    return false;
  }

}
