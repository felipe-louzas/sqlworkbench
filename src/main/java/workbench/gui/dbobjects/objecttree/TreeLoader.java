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

import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.DbExplorerSettings;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.CatalogChanger;
import workbench.db.CatalogIdentifier;
import workbench.db.ColumnIdentifier;
import workbench.db.ConstraintReader;
import workbench.db.DBID;
import workbench.db.DbMetadata;
import workbench.db.DbObject;
import workbench.db.DbSettings;
import workbench.db.DependencyNode;
import workbench.db.IndexColumn;
import workbench.db.IndexDefinition;
import workbench.db.JdbcUtils;
import workbench.db.PartitionLister;
import workbench.db.ProcedureDefinition;
import workbench.db.ProcedureReader;
import workbench.db.ReaderFactory;
import workbench.db.SchemaIdentifier;
import workbench.db.SubPartitionState;
import workbench.db.TableConstraint;
import workbench.db.TableDefinition;
import workbench.db.TableDependency;
import workbench.db.TableIdentifier;
import workbench.db.TablePartition;
import workbench.db.TriggerDefinition;
import workbench.db.TriggerReader;
import workbench.db.TriggerReaderFactory;
import workbench.db.WbConnection;
import workbench.db.dependency.DependencyReader;
import workbench.db.dependency.DependencyReaderFactory;

import workbench.gui.dbobjects.IsolationLevelChanger;
import workbench.gui.dbobjects.objecttree.vertica.ProjectionListNode;

import workbench.util.CollectionUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class TreeLoader
{
  /**
   * The node type for schema elements.
   */
  public static final String TYPE_ROOT = "database";

  /**
   * The node type for schema elements.
   */
  public static final String TYPE_SCHEMA = "schema";

  /**
   * The node type for catalog elements.
   */
  public static final String TYPE_CATALOG = "catalog";

  /**
   * The node type for global elements.
   */
  public static final String TYPE_GLOBAL = "global";

  /**
   * The node type for table like elements.
   */
  public static final String TYPE_TABLE = "table";

  /**
   * The node type for view elements.
   */
  public static final String TYPE_VIEW = "view";

  /**
   * The node type for the "columns" node in a table or a view.
   */
  public static final String TYPE_COLUMN_LIST = "column-list";

  /**
   * The node type for the "parameters" node for a procedure or function.
   */
  public static final String TYPE_PARAMETER_LIST = "parameter-list";

  /**
   * The node type for the "indexes" node in a table or a view.
   */
  public static final String TYPE_INDEX_LIST = "index-list";

  /**
   * The node type for the "Projections" node when connected to Vertica.
   */
  public static final String TYPE_PROJECTION_LIST = "projection-list";

  /**
   * The node type for the "Projections" node when connected to Vertica.
   */
  public static final String TYPE_PROJECTION_BUDDIES = "projection-buddies";

  /**
   * The node type for the "Projections" node when connected to Vertica.
   */
  public static final String TYPE_PROJECTION_COLUMNS = "projection-columns";

  /**
   * The node type for the "dependencies" node in a table or a view.
   */
  public static final String TYPE_DEPENDENCY_USED = "dep-used";

  /**
   * The node type for the "dependencies" node in a table or a view.
   */
  public static final String TYPE_DEPENDENCY_USING = "dep-using";

  /**
   * The node type for the "dependencies" node in a table or a view.
   */
  public static final String TYPE_PARTITIONS_NODE = "table-partitions-node";

  /**
   * The node type for the foreign key nodes in a table.
   * These are the "outgoing" foreign keys, i.e. columns from the "current" table
   * referencing other tables.
   */
  public static final String TYPE_FK_LIST = "referenced-fk-list";

  /**
   * The node type for the foreign key nodes in a table.
   * These are the "incoming" foreign keys, i.e. columns from the other tables
   * referencing the current table.
   */
  public static final String TYPE_REF_LIST = "referencing-fk-list";

  /**
   * The node type for the list of check constraints nodes in a table.
   */
  public static final String TYPE_CONSTRAINTS_LIST = "check-constraints-list";

  /**
   * The node type for a single (check) constraint.
   */
  public static final String TYPE_TABLE_CONSTRAINT = "table-constraint";

  /**
   * The node type identifying an index column.
   */
  public static final String TYPE_IDX_COL = "index-column";

  /**
   * The node type identifying nodes that group object types together.
   * Those nodes are typically "TABLE", "VIEW" and so on.
   */
  public static final String TYPE_DBO_TYPE_NODE = "dbobject-type";

  public static final String TYPE_FK_DEF = "fk-definition";

  public static final String TYPE_TRIGGERS_NODE = "table-trigger";

  public static final String TYPE_FUNCTION = "function";
  public static final String TYPE_PROCEDURES_NODE = "procedures";
  public static final String TYPE_PROC_PARAMETER = "parameter";
  public static final String TYPE_PACKAGE_NODE = "package";
  public static final String TYPE_PROJECTION_NODE = "projection";
  public static final String TYPE_PROJECTION_COL_NODE = "projection-column";
  public static final String TYPE_PROJECTION_BUDDY = "projection-buddy";

  private WbConnection connection;
  private final DbObjectTreeModel model;
  private final ObjectTreeNode root;
  private GlobalTreeNode globalNode;
  private Collection<String> availableTypes;
  private ProcedureTreeLoader procLoader;
  private DependencyReader dependencyLoader;
  private PartitionLister partitionLister;
  private final Set<String> typesToShow = CollectionUtil.caseInsensitiveSet();
  private final IsolationLevelChanger levelChanger = new IsolationLevelChanger();

  public TreeLoader()
  {
    root = new RootNode();
    model = new DbObjectTreeModel(root);
  }

  public void setConnection(WbConnection conn)
  {
    connection = conn;
    clear();
    if (connection != null)
    {
      availableTypes = connection.getMetadata().getObjectTypes();
      LogMgr.logDebug(new CallerInfo(){}, "Using object types: " + availableTypes);
      Set<String> globalTypes = connection.getDbSettings().getGlobalObjectTypes();
      if (!globalTypes.isEmpty())
      {
        LogMgr.logDebug(new CallerInfo(){}, "Using global object types: " + globalTypes);
      }
      availableTypes.removeAll(globalTypes);
    }
    availableTypes.add(ProcedureReader.TYPE_NAME_PROC);
    if (DbExplorerSettings.getShowTriggerPanel())
    {
      availableTypes.add(TriggerReader.TYPE_NAME);
    }
    procLoader = new ProcedureTreeLoader();
    dependencyLoader = DependencyReaderFactory.getReader(connection);
    partitionLister = PartitionLister.Factory.createReader(connection);
  }

  private void removeAllChildren(ObjectTreeNode node)
  {
    if (node == null) return;
    int count = node.getChildCount();
    for (int i=0; i < count; i++)
    {
      ObjectTreeNode child = node.getChildAt(i);
      removeAllChildren(child);
    }
    node.removeAllChildren();
  }

  public void setSelectedTypes(List<String> types)
  {
    LogMgr.logDebug(new CallerInfo(){}, "Changing selected object types to: " + types);
    typesToShow.clear();
    if (types != null && availableTypes != null)
    {
      types.retainAll(availableTypes);
    }

    if (CollectionUtil.isNonEmpty(types))
    {
      typesToShow.addAll(types);
    }
    else if (this.availableTypes != null)
    {
      typesToShow.addAll(availableTypes);
    }
    if (globalNode != null && DbTreeSettings.applyTypeFilterForGlobalObjects())
    {
      globalNode.setTypesToShow(typesToShow);
    }
    LogMgr.logDebug(new CallerInfo(){}, "Selected object types: " + typesToShow);
  }

  private String getRootName()
  {
    if (this.connection == null || connection.isClosed() || connection.getDbSettings() == null)
    {
      return ResourceMgr.getString("TxtDbExplorerTables");
    }

    if (connection.getDbSettings().supportsCatalogs())
    {
      return getDatabaseLabel();
    }

    if (connection.getDbSettings().supportsSchemas())
    {
      return ResourceMgr.getString("LblSchemas");
    }

    return ResourceMgr.getString("TxtDbExplorerTables");
  }

  private String getDatabaseLabel()
  {
    if (connection.getMetadata().getCatalogTerm().toLowerCase().equals("database"))
    {
      return ResourceMgr.getString("LblDatabases");
    }
    return ResourceMgr.getString("LblCatalogs");
  }

  private void setRootLabel(String newLabel)
  {
    String lbl = root.getName();
    if (StringUtil.stringsAreNotEqual(lbl, newLabel))
    {
      root.setNameAndType(newLabel, TYPE_ROOT);
      model.nodeChanged(root);
    }
  }

  public void clear()
  {
    removeAllChildren(root);
    root.setNameAndType(getRootName(), TYPE_ROOT);
    root.setChildrenLoaded(false);
    model.nodeStructureChanged(root);
  }

  public DbObjectTreeModel getModel()
  {
    return model;
  }

  public void load()
    throws SQLException
  {
    boolean loaded = false;

    final CallerInfo ci = new CallerInfo(){};
    if (connection == null)
    {
      LogMgr.logWarning(ci, "TreeLoader.load() called without connection!", new Exception("Backtrace"));
      return;
    }

    boolean wasBusy = connection.setBusy(true);
    if (wasBusy)
    {
      LogMgr.logError(ci, "TreeLoader.load() called even though connection is busy!", new Exception("Backtrace"));
    }

    Savepoint sp = null;

    try
    {
      if (connection.getDbSettings().useSavePointForDML() && !connection.getAutoCommit())
      {
        sp = connection.setSavepoint(ci);
      }

      if (CollectionUtil.isNonEmpty(connection.getDbSettings().getGlobalObjectTypes()))
      {
        if (DbTreeSettings.applyTypeFilterForGlobalObjects())
        {
          globalNode = new GlobalTreeNode(typesToShow);
        }
        else
        {
          Set<String> globalTypes = connection.getDbSettings().getGlobalObjectTypes();
          globalNode = new GlobalTreeNode(globalTypes);
        }
        globalNode.loadChildren(connection, this);
        root.add(globalNode);
      }

      if (connection.getDbSettings().supportsCatalogs())
      {
        loaded = loadCatalogs(root);
      }

      if (loaded)
      {
        setRootLabel(getDatabaseLabel());
      }
      else if (connection.getDbSettings().supportsSchemas())
      {
        setRootLabel(ResourceMgr.getString("LblSchemas"));
        loaded = loadSchemas(root);
      }

      if (!loaded)
      {
        addTypeNodes(root);
        root.setChildrenLoaded(true);
        model.nodeStructureChanged(root);
      }
      connection.releaseSavepoint(sp, ci);
      root.setChildrenLoaded(true);
    }
    catch (SQLException ex)
    {
      connection.rollback(sp, ci);
      throw ex;
    }
    finally
    {
      connection.setBusy(wasBusy);
      endTransaction(ci);
    }
  }

  public void endTransaction(CallerInfo context)
  {
    if (connection == null) return;

    if (connection.getDbSettings().selectStartsTransaction() && !connection.getAutoCommit())
    {
      if (DbTreeSettings.useTabConnection() || connection.isShared())
      {
        connection.endReadOnlyTransaction(context);
      }
      else
      {
        LogMgr.logDebug(new CallerInfo(){}, "Ending DbTree transaction using rollback on connection: " + connection.getId());
        connection.rollbackSilently(context);
      }
    }
  }

  private boolean loadSchemas(ObjectTreeNode parentNode)
    throws SQLException
  {
    boolean isCatalogChild = parentNode.getType().equals(TYPE_CATALOG);
    CatalogChanger catalogChanger = null;
    boolean catalogChanged = false;
    boolean supportsCatalogParameter = connection.getMetadata().supportsCatalogForGetSchemas();

    String catalogToRetrieve = null;
    String currentCatalog = null;

    if (isCatalogChild)
    {
      catalogChanger = new CatalogChanger();
      catalogChanger.setFireEvents(false);
      currentCatalog = connection.getMetadata().getCurrentCatalog();
      catalogToRetrieve = parentNode.getName();
    }

    final CallerInfo ci = new CallerInfo(){};
    LogMgr.logDebug(ci, "Loading schemas for node type: " + parentNode.getType() + ", name: " + parentNode.getNodeName());

    try
    {
      levelChanger.changeIsolationLevel(connection);

      if (isCatalogChild && connection.getDbSettings().changeCatalogToRetrieveSchemas() && !supportsCatalogParameter)
      {
        LogMgr.logDebug(ci, "Setting current catalog to: " + catalogToRetrieve);
        catalogChanger.setCurrentCatalog(connection, catalogToRetrieve);
        catalogChanged = true;
      }

      List<String> schemas = connection.getMetadata().getSchemas(connection.getSchemaFilter(), catalogToRetrieve);
      LogMgr.logDebug(ci, "Loaded " + schemas.size() + " schemas. Currently selected types: " + typesToShow);

      if (CollectionUtil.isEmpty(schemas)) return false;

      for (String schema : schemas)
      {
        ObjectTreeNode node = new ObjectTreeNode(schema, TYPE_SCHEMA);
        node.setAllowsChildren(true);
        SchemaIdentifier id = new SchemaIdentifier(schema);
        if (isCatalogChild)
        {
          id.setCatalog(catalogToRetrieve);
        }
        node.setUserObject(id);
        parentNode.add(node);
        addTypeNodes(node);
      }
    }
    finally
    {
      levelChanger.restoreIsolationLevel(connection);
      if (catalogChanged)
      {
        LogMgr.logDebug(ci, "Resetting current catalog to: " + currentCatalog);
        catalogChanger.setCurrentCatalog(connection, currentCatalog);
      }
    }
    parentNode.setChildrenLoaded(true);
    model.nodeStructureChanged(parentNode);
    return true;
  }

  private boolean loadCatalogs(ObjectTreeNode parentNode)
    throws SQLException
  {
    if (parentNode.getChildCount() > 0)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Loading catalogs to an already populated parent node: " + parentNode, new Exception("Backtrack"));
    }
    boolean supportsCatalogObjects = connection.getMetadata().supportsCatalogLevelObjects();

    try
    {
      levelChanger.changeIsolationLevel(connection);
      List<String> catalogs = connection.getMetadata().getCatalogInformation(connection.getCatalogFilter());
      for (String cat : catalogs)
      {
        ObjectTreeNode catalogNode = new ObjectTreeNode(cat, TYPE_CATALOG);
        catalogNode.setAllowsChildren(true);
        CatalogIdentifier id = new CatalogIdentifier(cat);
        id.setTypeName(connection.getMetadata().getCatalogTerm());
        catalogNode.setUserObject(id);
        parentNode.add(catalogNode);
        if (supportsCatalogObjects)
        {
          Set<String> types = connection.getDbSettings().getCatalogLevelTypes();
          if (!types.isEmpty())
          {
            CatalogObjectTypesNode catalogObjectTypes = new CatalogObjectTypesNode(types);
            catalogNode.add(catalogObjectTypes);
            catalogObjectTypes.loadChildren(connection, this);
          }
        }
        if (!connection.getDbSettings().supportsSchemas())
        {
          addTypeNodes(catalogNode);
        }
      }
      model.nodeStructureChanged(parentNode);
      return catalogs.size() > 0;
    }
    finally
    {
      levelChanger.restoreIsolationLevel(connection);
    }
  }

  private boolean shouldShowType(String type)
  {
    if (type == null) return false;
    return (typesToShow.isEmpty() || typesToShow.contains("*") || typesToShow.contains(type));
  }

  private void addTypeNodes(ObjectTreeNode parentNode)
  {
    if (parentNode == null) return;

    for (String type : availableTypes)
    {
      if (type.equalsIgnoreCase(TriggerReader.TYPE_NAME) || type.equalsIgnoreCase(ProcedureReader.TYPE_NAME_PROC)) continue;
      if (shouldShowType(type))
      {
        ObjectTreeNode node = new ObjectTreeNode(type, TYPE_DBO_TYPE_NODE);
        node.setAllowsChildren(true);
        parentNode.add(node);
      }
    }

    if (shouldShowType(ProcedureReader.TYPE_NAME_PROC))
    {
      String label = ResourceMgr.getString("TxtDbExplorerProcs");
      if (DBID.Postgres.isDB(connection) && !JdbcUtils.hasMinimumServerVersion(connection, "11.0"))
      {
        label = ResourceMgr.getString("TxtDbExplorerFuncs");
      }
      ObjectTreeNode node = new ObjectTreeNode(label, TYPE_PROCEDURES_NODE);
      node.setAllowsChildren(true);
      node.setChildrenLoaded(false);
      parentNode.add(node);
    }

    // always add triggers at the end
    if (shouldShowType(TriggerReader.TYPE_NAME))
    {
      ObjectTreeNode node = new ObjectTreeNode(ResourceMgr.getString("TxtDbExplorerTriggers"), TYPE_TRIGGERS_NODE);
      node.setAllowsChildren(true);
      parentNode.add(node);
    }

    parentNode.setChildrenLoaded(true);
  }

  private void loadTypesForSchema(ObjectTreeNode schemaNode)
  {
    if (schemaNode == null) return;
    if (!schemaNode.isSchemaNode()) return;

    int count = schemaNode.getChildCount();
    for (int i=0; i < count; i++)
    {
      ObjectTreeNode child = schemaNode.getChildAt(i);
      try
      {
        child.removeAllChildren();
        loadObjectsForTypeNode(child);
      }
      catch (SQLException ex)
      {
        LogMgr.logError(new CallerInfo(){}, "Could not load schema nodes for " + child.displayString(), ex);
      }
    }
  }

  public void reloadSchema(ObjectTreeNode schemaNode)
    throws SQLException
  {
    if (schemaNode == null) return;
    if (!schemaNode.isSchemaNode()) return;

    try
    {
      schemaNode.removeAllChildren();
      addTypeNodes(schemaNode);
      loadNodeObjects(schemaNode);
    }
    finally
    {
      endTransaction(new CallerInfo(){});
    }
  }

  public void loadNodeObjects(ObjectTreeNode node)
    throws SQLException
  {
    if (node == null) return;

    int count = node.getChildCount();
    for (int i=0; i < count; i++)
    {
      ObjectTreeNode child = node.getChildAt(i);
      if (!child.isLoaded())
      {
        loadObjectsForTypeNode(child);
      }
    }
    node.setChildrenLoaded(true);
    model.nodeStructureChanged(node);
    model.nodeChanged(node);
  }

  public void reloadTableNode(ObjectTreeNode node)
    throws SQLException
  {
    DbObject dbo = node.getDbObject();
    if (! (dbo instanceof TableIdentifier) ) return;

    node.removeAllChildren();
    addColumnsNode(node);
    addTableSubNodes(node);

    int count = node.getChildCount();

    for (int i=0; i < count; i++)
    {
      ObjectTreeNode child = node.getChildAt(i);
      loadChildren(child);
    }
    node.setChildrenLoaded(true);
    model.nodeStructureChanged(node);
  }

  public void reloadNode(ObjectTreeNode node)
    throws SQLException
  {
    if (node == null) return;

    node.removeAllChildren();
    loadChildren(node);
    node.setChildrenLoaded(true);
    model.nodeStructureChanged(node);
  }

  private TableIdentifier getParentInfo(ObjectTreeNode node)
  {
    ObjectTreeNode parent = node.getParent();
    if (parent == null)
    {
      return new TableIdentifier(null, null, "$wb-dummy$");
    }
    String schema = null;
    String catalog = null;

    if (parent.getDbObject() != null)
    {
      // this is typically a CatalogIdentifier or SchemaIdentifier
      DbObject dbo = parent.getDbObject();
      schema = dbo.getSchema();
      catalog = dbo.getCatalog();
    }
    else if (parent.isCatalogNode())
    {
      catalog = parent.getName();
    }
    else if (parent.isSchemaNode())
    {
      schema = parent.getName();
    }

    if (connection.getDbSettings().supportsCatalogs() && connection.getDbSettings().supportsSchemas() && catalog == null)
    {
      // if schemas and catalogs are supported, the current node must be a schema
      // and the parent of that must be a catalog
      ObjectTreeNode catNode = parent.getParent();
      if (catNode != null && catNode.getType().equalsIgnoreCase(TYPE_CATALOG))
      {
        catalog = catNode.getName();
      }
    }
    return new TableIdentifier(catalog, schema, "$wb-dummy$");
  }

  public void loadObjectsForTypeNode(ObjectTreeNode typeNode)
    throws SQLException
  {
    if (typeNode == null) return;
    if (typeNode.getType().equalsIgnoreCase(TYPE_TRIGGERS_NODE))
    {
      loadTriggers(typeNode);
      return;
    }

    if (typeNode.getType().equalsIgnoreCase(TYPE_PROCEDURES_NODE))
    {
      loadProcedures(typeNode);
      return;
    }

    TableIdentifier info = getParentInfo(typeNode);
    String schema = info.getRawSchema();
    String catalog = info.getRawCatalog();

    boolean loaded = true;
    List<DbObject> objects = connection.getMetadata().getDbObjectList(null, catalog, schema, new String[] { typeNode.getName() });
    DbObjectSorter.sort(objects, Settings.getInstance().useNaturalSortForTableList(), false);

    for (DbObject dbo : objects)
    {
      ObjectTreeNode node = new ObjectTreeNode(dbo);
      node.setAllowsChildren(false);
      if (hasColumns(dbo))
      {
        node.setAllowsChildren(true);
        addColumnsNode(node);
      }
      else if (dbo instanceof ProcedureDefinition)
      {
        ProcedureTreeLoader.addParameterNode(node);
        node.setChildrenLoaded(false);
      }

      if (isTableOrView(dbo) && dbo instanceof TableIdentifier)
      {
        TableIdentifier tbl = (TableIdentifier)dbo;
        addTableSubNodes(node);
        connection.getObjectCache().addTable(new TableDefinition(tbl));
      }
      else if (hasIndexes(node))
      {
        node.setAllowsChildren(true);
        addIndexNode(node);
        addDependencyNodes(node);
      }
      else
      {
        addDependencyNodes(node);
      }

      if (connection.getMetadata().isViewType(typeNode.getName())
          && dbo instanceof TableIdentifier
          && !dbo.getObjectType().equals(DbMetadata.MVIEW_NAME))
      {
        TableIdentifier tbl = (TableIdentifier)dbo;
        addViewTriggerNode(node);
        connection.getObjectCache().addTable(new TableDefinition(tbl));
      }

      typeNode.add(node);
      node.setChildrenLoaded(loaded);
    }
    typeNode.setChildrenLoaded(true);
    model.nodeStructureChanged(typeNode);
    model.nodeChanged(typeNode);
  }

  private boolean supportsUsedByDependencies(ObjectTreeNode node)
  {
    if (dependencyLoader == null) return false;
    if (node == null) return false;
    if (node.getDbObject() == null) return false;
    return dependencyLoader.supportsUsedByDependency(node.getDbObject().getObjectType());
  }

  private boolean supportsIsUsingDependencies(ObjectTreeNode node)
  {
    if (dependencyLoader == null) return false;
    if (node == null) return false;
    if (node.getDbObject() == null) return false;
    return dependencyLoader.supportsIsUsingDependency(node.getDbObject().getObjectType());
  }

  private boolean hasIndexes(ObjectTreeNode node)
  {
    if (node == null) return false;
    DbObject dbo = node.getDbObject();
    if (dbo == null) return false;
    DbSettings dbs = connection.getDbSettings();
    if (dbs.isViewType(dbo.getObjectType()) && dbs.supportsIndexedViews())
    {
      return true;
    }
    return dbs.isMview(dbo.getObjectType());
  }

  private void addViewTriggerNode(ObjectTreeNode node)
  {
    TriggerReader reader = TriggerReaderFactory.createReader(connection);
    if (reader == null) return;
    if (reader.supportsTriggersOnViews())
    {
      ObjectTreeNode trg = new ObjectTreeNode(ResourceMgr.getString("TxtDbExplorerTriggers"), TYPE_TRIGGERS_NODE);
      trg.setAllowsChildren(true);
      node.add(trg);
    }
  }

  private void addColumnsNode(ObjectTreeNode node)
  {
    ObjectTreeNode cols = new ObjectTreeNode(ResourceMgr.getString("TxtDbExplorerTableDefinition"), TYPE_COLUMN_LIST);
    cols.setAllowsChildren(true);
    node.add(cols);
  }

  public boolean addPartitionsNode(ObjectTreeNode node)
  {
    if (node == null) return false;
    if (this.partitionLister == null) return false;
    node.setAllowsChildren(true);
    ObjectTreeNode partNode = new ObjectTreeNode("Partitions", TYPE_PARTITIONS_NODE);
    node.add(partNode);
    return true;
  }

  public boolean addDependencyNodes(ObjectTreeNode node)
  {
    if (node == null) return false;
    boolean supported = false;

    if (supportsIsUsingDependencies(node))
    {
      node.setAllowsChildren(true);
      ObjectTreeNode usingNode = new ObjectTreeNode(ResourceMgr.getString("TxtDepsUses"), TYPE_DEPENDENCY_USING);
      usingNode.setAllowsChildren(true);
      node.add(usingNode);
      supported = true;
    }

    if (supportsUsedByDependencies(node))
    {
      node.setAllowsChildren(true);
      ObjectTreeNode usedNode = new ObjectTreeNode(ResourceMgr.getString("TxtDepsUsedBy"), TYPE_DEPENDENCY_USED);
      usedNode.setAllowsChildren(true);
      node.add(usedNode);
      supported = true;
    }
    return supported;
  }

  private void addIndexNode(ObjectTreeNode node)
  {
    ObjectTreeNode idx = null;
    if (DBID.Vertica.isDB(connection))
    {
      idx = new ProjectionListNode();
    }
    else
    {
      idx = new ObjectTreeNode(ResourceMgr.getString("TxtDbExplorerIndexes"), TYPE_INDEX_LIST);
      idx.setAllowsChildren(true);
    }
    node.add(idx);
  }

  private void addTableSubNodes(ObjectTreeNode node)
  {
    addIndexNode(node);

    ConstraintReader reader = ReaderFactory.getConstraintReader(connection.getMetadata());
    if (reader != ConstraintReader.NULL_READER)
    {
      ObjectTreeNode constraints = new ObjectTreeNode(ResourceMgr.getString("TxtDbExplorerTableConstraints"), TYPE_CONSTRAINTS_LIST);
      constraints.setAllowsChildren(true);
      node.add(constraints);
    }

    ObjectTreeNode fk = new ObjectTreeNode(ResourceMgr.getString("TxtDbExplorerFkColumns"), TYPE_FK_LIST);
    fk.setAllowsChildren(true);
    node.add(fk);

    ObjectTreeNode ref = new ObjectTreeNode(ResourceMgr.getString("TxtDbExplorerReferencedColumns"), TYPE_REF_LIST);
    ref.setAllowsChildren(true);
    node.add(ref);

    addPartitionsNode(node);
    addDependencyNodes(node);

    ObjectTreeNode trg = new ObjectTreeNode(ResourceMgr.getString("TxtDbExplorerTriggers"), TYPE_TRIGGERS_NODE);
    trg.setAllowsChildren(true);
    node.add(trg);
  }

  public void loadProcedures(ObjectTreeNode procNode)
    throws SQLException
  {
    procLoader.loadProcedures(procNode, model, connection, this);
  }

  public boolean isDependencyNode(ObjectTreeNode node)
  {
    if (node == null) return false;
    return (node.getType().equals(TYPE_DEPENDENCY_USED) || node.getType().equals(TYPE_DEPENDENCY_USING));
  }

  public void loadProcedureParameters(ObjectTreeNode parameterListNode)
  {
    if (parameterListNode == null) return;
    ObjectTreeNode procNode = parameterListNode.getParent();
    ProcedureDefinition proc = (ProcedureDefinition)procNode.getDbObject();
    if (proc == null) return;

    List<ColumnIdentifier> parameters = proc.getParameters(connection);

    for (ColumnIdentifier col : parameters)
    {
      String mode = col.getArgumentMode();
      ObjectTreeNode p = null;
      if ("RETURN".equals(mode))
      {
        if (proc.isTableFunction())
        {
          p = new ObjectTreeNode("RETURNS TABLE", TYPE_PROC_PARAMETER);
        }
        else
        {
          p = new ObjectTreeNode("RETURNS " + col.getDbmsType(), TYPE_PROC_PARAMETER);
        }
      }
      else
      {
        p = new ObjectTreeNode(col);
        p.setNameAndType(col.getColumnName(), TYPE_PROC_PARAMETER);
      }
      p.setAllowsChildren(false);
      p.setChildrenLoaded(true);
      parameterListNode.add(p);
    }
    parameterListNode.setChildrenLoaded(true);
    model.nodeStructureChanged(parameterListNode);
  }

  public void loadTriggers(ObjectTreeNode trgNode)
    throws SQLException
  {
    if (trgNode == null) return;

    TableIdentifier info = getParentInfo(trgNode);
    TriggerReader reader = TriggerReaderFactory.createReader(connection);
    List<TriggerDefinition> triggers = reader.getTriggerList(info.getRawCatalog(), info.getRawSchema(), null);
    DbObjectSorter.sort(triggers, Settings.getInstance().useNaturalSortForTableList());

    for (TriggerDefinition trg : triggers)
    {
      ObjectTreeNode node = new ObjectTreeNode(trg);
      boolean supportsDeps = supportsIsUsingDependencies(node) || supportsUsedByDependencies(node);
      if (trg.getRelatedTable() != null)
      {
        node.setAllowsChildren(true);
        TableIdentifier tbl = trg.getRelatedTable();
        ObjectTreeNode tableNode = new ObjectTreeNode(tbl);
        node.add(tableNode);
        tableNode.setAllowsChildren(true);
        addColumnsNode(tableNode);
        addTableSubNodes(tableNode);
      }
      if (supportsDeps)
      {
        node.setAllowsChildren(true);
        addDependencyNodes(node);
      }
      node.setChildrenLoaded(true);
      trgNode.add(node);
    }
    model.nodeStructureChanged(trgNode);
    trgNode.setChildrenLoaded(true);
  }

  public void loadTableTriggers(DbObject dbo, ObjectTreeNode trgNode)
    throws SQLException
  {
    if (trgNode == null) return;
    if (dbo == null)
    {
      trgNode.setAllowsChildren(false);
      return;
    }

    TriggerReader reader = TriggerReaderFactory.createReader(connection);

    TableIdentifier tbl = (TableIdentifier)dbo;
    List<TriggerDefinition> triggers = reader.getTriggerList(tbl.getRawCatalog(), tbl.getRawSchema(), tbl.getRawTableName());
    DbObjectSorter.sort(triggers, Settings.getInstance().useNaturalSortForTableList());

    for (TriggerDefinition trg : triggers)
    {
      ObjectTreeNode node = new ObjectTreeNode(trg);
      node.setAllowsChildren(false);
      node.setChildrenLoaded(true);
      trgNode.add(node);
    }
    model.nodeStructureChanged(trgNode);
    trgNode.setChildrenLoaded(true);
  }

  public void loadTableIndexes(DbObject dbo, ObjectTreeNode indexNode)
    throws SQLException
  {
    if (indexNode == null) return;
    if (dbo == null)
    {
      indexNode.setAllowsChildren(false);
      return;
    }

    DbMetadata meta = connection.getMetadata();

    TableIdentifier tbl = (TableIdentifier)dbo;
    List<IndexDefinition> indexes = meta.getIndexReader().getTableIndexList(tbl, false);
    DbObjectSorter.sort(indexes, Settings.getInstance().useNaturalSortForTableList());

    boolean removeColumnQuotes = DbTreeSettings.removeColumnQuotesForDisplay();
    for (IndexDefinition idx : indexes)
    {
      ObjectTreeNode node = new ObjectTreeNode(idx);
      node.setAllowsChildren(true);
      node.setChildrenLoaded(true);
      for (IndexColumn col : idx.getColumns())
      {
         ObjectTreeNode idxCol = new ObjectTreeNode(col.getExpression(removeColumnQuotes), TYPE_IDX_COL);
         idxCol.setAllowsChildren(false);
         idxCol.setChildrenLoaded(true);
         node.add(idxCol);
      }
      indexNode.add(node);
    }
    model.nodeStructureChanged(indexNode);
    indexNode.setChildrenLoaded(true);
  }

  private void loadDependencies(DbObject dbo, ObjectTreeNode depNode)
  {
    if (depNode == null) return;
    if (dbo == null || dependencyLoader == null)
    {
      depNode.setAllowsChildren(false);
      return;
    }

    List<DbObject> objects = null;
    if (depNode.getType().equals(TYPE_DEPENDENCY_USED))
    {
      objects = dependencyLoader.getUsedBy(connection, dbo);
    }
    else
    {
      objects = dependencyLoader.getUsedObjects(connection, dbo);
    }

    DbObjectSorter.sort(objects, Settings.getInstance().useNaturalSortForTableList(), true);

    for (DbObject obj : objects)
    {
      ObjectTreeNode node = new ObjectTreeNode(obj);
      node.setAllowsChildren(node.canHaveChildren());
      addDependencyNodes(node);
      depNode.add(node);
    }
    model.nodeStructureChanged(depNode);
    depNode.setChildrenLoaded(true);
  }

  private void loadTableConstraints(DbObject dbo, ObjectTreeNode constraintNode)
  {
    if (dbo instanceof TableIdentifier)
    {
      ConstraintReader reader = ReaderFactory.getConstraintReader(connection.getMetadata());
      TableIdentifier baseTable = (TableIdentifier)dbo;
      List<TableConstraint> constraints = reader.getTableConstraints(connection, new TableDefinition(baseTable, CollectionUtil.arrayList()));
      for (TableConstraint constraint : constraints)
      {
        ObjectTreeNode node = new ObjectTreeNode(constraint);
        node.setNodeType(TYPE_TABLE_CONSTRAINT);
        node.setObjectSource(constraint.getSql());
        node.setTooltip(StringUtil.getMaxSubstring(constraint.getExpression(), 80));
        node.setAllowsChildren(false);
        constraintNode.add(node);
      }
      model.nodeStructureChanged(constraintNode);
    }
  }
  private void loadTableColumns(DbObject dbo, ObjectTreeNode columnsNode)
    throws SQLException
  {
    if (columnsNode == null) return;
    if (dbo == null)
    {
      columnsNode.setAllowsChildren(false);
      return;
    }

    List<ColumnIdentifier> columns = connection.getMetadata().getObjectColumns(dbo);
    if (columns == null)
    {
      return;
    }

    if (dbo instanceof TableIdentifier)
    {
      TableIdentifier tbl = (TableIdentifier)dbo;
      connection.getObjectCache().addTable(new TableDefinition(tbl, columns));
    }

    if (DbTreeSettings.sortColumnsByName())
    {
      DbObjectSorter.sort(columns, Settings.getInstance().useNaturalSortForColumnList(), Settings.getInstance().sortColumnListIgnoreCase());
    }

    for (ColumnIdentifier col : columns)
    {
      ObjectTreeNode node = new ObjectTreeNode(col);
      node.setAllowsChildren(false);
      columnsNode.add(node);
    }

    model.nodeStructureChanged(columnsNode);
    columnsNode.setChildrenLoaded(true);
  }

  private TableIdentifier findTableParent(ObjectTreeNode node)
  {
    if (node == null) return null;
    ObjectTreeNode parent = node.getParent();
    while (parent != null)
    {
      if (parent.getUserObject() instanceof TableIdentifier)
      {
        return (TableIdentifier)parent.getUserObject();
      }
      parent = parent.getParent();
    }
    return null;
  }

  private void loadSubPartitions(ObjectTreeNode partitionNode)
  {
    if (this.partitionLister == null) return;
    TablePartition partition = (TablePartition)partitionNode.getDbObject();

    TableIdentifier baseTable = findTableParent(partitionNode);
    List<? extends TablePartition> subPartitions = partitionLister.getSubPartitions(baseTable, partition);
    partitionNode.setChildrenLoaded(true);
    if (CollectionUtil.isEmpty(subPartitions)) return;

    for (TablePartition obj : subPartitions)
    {
      ObjectTreeNode node = new ObjectTreeNode(obj);
      node.setAllowsChildren(false);
      partitionNode.add(node);
    }
    model.nodeStructureChanged(partitionNode);
  }

  private void loadTablePartitions(DbObject table, ObjectTreeNode partNode)
  {
    if (this.partitionLister == null) return;
    List<? extends TablePartition> partitions = partitionLister.getPartitions((TableIdentifier)table);
    partNode.setChildrenLoaded(true);
    if (CollectionUtil.isEmpty(partitions)) return;

    for (TablePartition part : partitions)
    {
      ObjectTreeNode node = new ObjectTreeNode(part);
      boolean hasSubPartitions = part.getSubPartitionState() != SubPartitionState.none;
      node.setAllowsChildren(hasSubPartitions);
      if (hasSubPartitions)
      {
        node.setIconKey("partitions");
      }
      partNode.add(node);
    }
    model.nodeStructureChanged(partNode);
  }

  private void loadForeignKeys(DbObject dbo, ObjectTreeNode fkNode, boolean showIncoming)
    throws SQLException
  {
    if (fkNode == null) return;
    if (dbo == null)
    {
      fkNode.setAllowsChildren(false);
      return;
    }

    DbMetadata meta = connection.getMetadata();
    if (!meta.isTableType(dbo.getObjectType())) return;

    TableIdentifier tbl = (TableIdentifier)dbo;
    TableDependency deps = new TableDependency(connection, tbl);

    List<DependencyNode> fklist = null;

    if (showIncoming)
    {
      fklist = deps.getIncomingForeignKeys();
      connection.getObjectCache().addReferencingTables(tbl, fklist);
    }
    else
    {
      fklist = deps.getOutgoingForeignKeys();
      connection.getObjectCache().addReferencedTables(tbl, fklist);
    }

    fklist.sort((DependencyNode o1, DependencyNode o2) -> o1.getTable().compareTo(o2.getTable()));

    for (DependencyNode fk : fklist)
    {
      TableIdentifier table = fk.getTable();
      ObjectTreeNode tblNode = new ObjectTreeNode(table);
      tblNode.setAllowsChildren(true);
      tblNode.setChildrenLoaded(true);
      fkNode.add(tblNode);

      String colDisplay = "<html><b>" + fk.getFkName() + "</b>: ";

      String fkTableDisplay = DependencyNode.getDisplayTableExpression(fk.getTable(), connection);
      String tblDisplay = DependencyNode.getDisplayTableExpression(tbl, connection);
      if (showIncoming)
      {
        colDisplay += fkTableDisplay + "(" + fk.getSourceColumnsList() + ") REFERENCES  " +
        tblDisplay + "(" + fk.getTargetColumnsList();
      }
      else
      {
        colDisplay += tblDisplay + "(" + fk.getTargetColumnsList() + ") REFERENCES  " +
        fkTableDisplay + "(" + fk.getSourceColumnsList();

      }
      colDisplay += ")</html>";

      ObjectTreeNode fkEntry = new ObjectTreeNode(fk.getFkName(), TYPE_FK_DEF);
      fkEntry.setDisplay(colDisplay);
      fkEntry.setAllowsChildren(false);
      fkEntry.setChildrenLoaded(true);
      fkEntry.setTooltip(fk.getComment());
      tblNode.add(fkEntry);
      addColumnsNode(tblNode);
      addTableSubNodes(tblNode);
    }
    model.nodeStructureChanged(fkNode);
    fkNode.setChildrenLoaded(true);
  }

  private boolean isTableOrView(DbObject dbo)
  {
    if (dbo == null) return false;
    DbMetadata meta = connection.getMetadata();
    return meta.isExtendedTableType(dbo.getObjectType()) || meta.isViewType(dbo.getObjectType());
  }

  private boolean hasColumns(DbObject dbo)
  {
    if (dbo == null) return false;
    DbMetadata meta = connection.getMetadata();
    return meta.hasColumns(dbo);
  }

  public WbConnection getConnection()
  {
    return connection;
  }

  public void loadChildren(ObjectTreeNode node)
    throws SQLException
  {
    if (node == null) return;
    if (this.connection == null) return;

    Savepoint sp = null;

    final CallerInfo ci = new CallerInfo(){};
    if (connection.getDbSettings().useSavePointForDML())
    {
      sp = connection.setSavepoint(ci);
    }
    try
    {
      levelChanger.changeIsolationLevel(connection);
      this.connection.setBusy(true);

      if (node.loadChildren(connection, this))
      {
        model.nodeStructureChanged(node);
        connection.releaseSavepoint(sp, ci);
        return;
      }

      String type = node.getType();

      if (node.isCatalogNode())
      {
        if (connection.getDbSettings().supportsSchemas())
        {
          loadSchemas(node);
        }
        else if (!node.childrenAreLoaded())
        {
          addTypeNodes(node);
          loadNodeObjects(node);
        }
      }
      else if (node.isSchemaNode())
      {
        loadTypesForSchema(node);
      }
      else if (TYPE_CONSTRAINTS_LIST.equals(type))
      {
        ObjectTreeNode parent = node.getParent();
        DbObject dbo = parent.getDbObject();
        loadTableConstraints(dbo, node);
      }
      else if (TYPE_DBO_TYPE_NODE.equals(type))
      {
        loadObjectsForTypeNode(node);
      }
      else if (TYPE_COLUMN_LIST.equals(type))
      {
        ObjectTreeNode parent = node.getParent();
        DbObject dbo = parent.getDbObject();
        loadTableColumns(dbo, node);
      }
      else if (TYPE_INDEX_LIST.equals(type))
      {
        ObjectTreeNode parent = node.getParent();
        DbObject dbo = parent.getDbObject();
        loadTableIndexes(dbo, node);
      }
      else if (PartitionLister.PARTITION_TYPE_NAME.equals(type))
      {
        loadSubPartitions(node);
      }
      else if (TYPE_PARTITIONS_NODE.equals(type))
      {
        ObjectTreeNode parent = node.getParent();
        DbObject dbo = parent.getDbObject();
        loadTablePartitions(dbo, node);
      }
      else if (TYPE_FK_LIST.equals(type))
      {
        ObjectTreeNode parent = node.getParent();
        DbObject dbo = parent.getDbObject();
        loadForeignKeys(dbo, node, false);
      }
      else if (TYPE_REF_LIST.equals(type))
      {
        ObjectTreeNode parent = node.getParent();
        DbObject dbo = parent.getDbObject();
        loadForeignKeys(dbo, node, true);
      }
      else if (TYPE_TRIGGERS_NODE.equals(type))
      {
        ObjectTreeNode parent = node.getParent();
        DbObject dbo = parent.getDbObject();
        if (dbo instanceof TableIdentifier)
        {
          loadTableTriggers(dbo, node);
        }
        else
        {
          loadTriggers(node);
        }
      }
      else if (TYPE_DEPENDENCY_USED.equals(type) || TYPE_DEPENDENCY_USING.equals(type))
      {
        ObjectTreeNode parent = node.getParent();
        DbObject dbo = parent.getDbObject();
        loadDependencies(dbo, node);
      }
      else if (TYPE_PROCEDURES_NODE.equals(type))
      {
        loadProcedures(node);
      }
      else if (TYPE_PARAMETER_LIST.equals(type))
      {
        loadProcedureParameters(node);
      }
      else if (connection.getMetadata().isExtendedTableType(type) || connection.getMetadata().isViewType(type))
      {
        reloadTableNode(node);
      }
      connection.releaseSavepoint(sp, ci);
    }
    catch (SQLException ex)
    {
      connection.rollback(sp, ci);
      throw ex;
    }
    finally
    {
      connection.setBusy(false);
      levelChanger.restoreIsolationLevel(connection);
      endTransaction(ci);
    }
  }
}
