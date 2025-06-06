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
package workbench.gui.dbobjects;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;

import workbench.interfaces.DbData;
import workbench.interfaces.IndexChangeListener;
import workbench.interfaces.Resettable;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.DbExplorerSettings;
import workbench.resource.GuiSettings;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.ColumnDropper;
import workbench.db.ColumnIdentifier;
import workbench.db.DbMetadata;
import workbench.db.DbObject;
import workbench.db.TableColumnsDatastore;
import workbench.db.TableDefinition;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.gui.WbSwingUtilities;
import workbench.gui.actions.ColumnAlterAction;
import workbench.gui.actions.CreateDummySqlAction;
import workbench.gui.actions.CreateIndexAction;
import workbench.gui.actions.CreatePKAction;
import workbench.gui.actions.DeleteRowAction;
import workbench.gui.actions.DropDbObjectAction;
import workbench.gui.actions.DropPKAction;
import workbench.gui.actions.InsertRowAction;
import workbench.gui.actions.ReloadAction;
import workbench.gui.actions.WbAction;
import workbench.gui.components.DataStoreTableModel;
import workbench.gui.components.EmptyTableModel;
import workbench.gui.components.FlatButton;
import workbench.gui.components.QuickFilterPanel;
import workbench.gui.components.WbLabelField;
import workbench.gui.components.WbScrollPane;
import workbench.gui.components.WbTable;
import workbench.gui.components.WbTraversalPolicy;
import workbench.gui.renderer.RendererSetup;
import workbench.gui.renderer.SqlTypeRenderer;

import workbench.storage.DataStore;

import workbench.sql.wbcommands.ObjectInfo;

import workbench.util.ExceptionUtil;
import workbench.util.StringUtil;
import workbench.util.WbThread;

/**
 * A panel to display the table definition information (=column list) inside the DbExplorer.
 *
 * @see workbench.db.DbMetadata#getTableDefinition(TableIdentifier)
 *
 * @author Thomas Kellerer
 */
public class TableDefinitionPanel
  extends JPanel
  implements PropertyChangeListener, ListSelectionListener, Resettable, DbObjectList, IndexChangeListener
{
  public static final String INDEX_PROP = "index";
  public static final String DEFINITION_PROP = "tableDefinition";

  private final ReentrantLock connectionLock = new ReentrantLock();
  private final Object busyLock = new Object();

  private WbTable tableDefinition;
  private WbLabelField tableNameLabel;
  private QuickFilterPanel columnFilter;
  private CreateIndexAction createIndexAction;
  private CreatePKAction createPKAction;
  private DropPKAction dropPKAction;
  private ColumnAlterAction alterColumnsAction;
  private TableIdentifier currentTable;
  private InsertRowAction addColumn;
  private DeleteRowAction deleteColumn;

  private WbConnection dbConnection;
  private WbAction reloadAction;
  private DropDbObjectAction dropColumnsAction;
  private JPanel toolbar;
  private boolean busy;
  private FlatButton alterButton;
  private final ColumnChangeValidator validator = new ColumnChangeValidator();
  private boolean doRestore;
  private boolean initialized;

  public TableDefinitionPanel()
  {
    super();
  }

  private void initGui()
  {
    if (initialized) return;

    WbSwingUtilities.invoke(this::_initGui);
  }

  private void _initGui()
  {
    if (initialized) return;

    this.tableDefinition = new WbTable(true, false, false);
    this.tableDefinition.setAdjustToColumnLabel(false);
    this.tableDefinition.setSelectOnRightButtonClick(true);
    this.tableDefinition.getExportAction().setEnabled(true);
    this.tableDefinition.setRendererSetup(RendererSetup.getBaseSetup());
    this.tableDefinition.setSortIgnoreCase(Settings.getInstance().sortColumnListIgnoreCase());
    this.tableDefinition.setUseNaturalSort(Settings.getInstance().useNaturalSortForColumnList());

    updateReadOnlyState();
    Settings.getInstance().addPropertyChangeListener(this, DbExplorerSettings.PROP_ALLOW_ALTER_TABLE);

    this.reloadAction = new ReloadAction(this);
    this.reloadAction.setEnabled(false);
    this.reloadAction.addToInputMap(this.tableDefinition);

    toolbar = new JPanel(new GridBagLayout());

    alterColumnsAction = new ColumnAlterAction(tableDefinition);
    alterColumnsAction.setReloader(this);

    columnFilter  = new QuickFilterPanel(this.tableDefinition, true, "columnlist");
    // Setting the column list now, ensures that the dropdown will be displayed (=sized)
    // properly in the QuickFilterPanel, although it wouldn't be necessary
    // as the column list will be updated automatically when the model of the table changes
    columnFilter.setColumnList(TableColumnsDatastore.TABLE_DEFINITION_COLS);

    columnFilter.setFilterOnType(DbExplorerSettings.getFilterDuringTyping());
    columnFilter.setAlwaysUseContainsFilter(DbExplorerSettings.getUsePartialMatch());

    DbData db = new DbData()
    {
      @Override
      public int addRow()
      {
        return tableDefinition.addRow();
      }

      @Override
      public void deleteRow()
      {
        tableDefinition.deleteRow();
      }

      @Override
      public void deleteRowWithDependencies()
      {
      }

      @Override
      public boolean startEdit()
      {
        return true;
      }

      @Override
      public int duplicateRow()
      {
        return -1;
      }

      @Override
      public void endEdit()
      {
      }
    };
    addColumn = new InsertRowAction(db);
    addColumn.initMenuDefinition("MnuTxtAddCol");

    deleteColumn = new DeleteRowAction(db);
    deleteColumn.initMenuDefinition("MnuTxtDropColumn");

    columnFilter.addToToolbar(addColumn, true, true);
    columnFilter.addToToolbar(deleteColumn, 1);
    columnFilter.addToToolbar(reloadAction, 0);

    GridBagConstraints cc = new GridBagConstraints();

    cc.anchor = GridBagConstraints.LINE_START;
    cc.fill = GridBagConstraints.NONE;
    cc.gridx = 0;
    cc.weightx = 0.0;
    cc.weighty = 0.0;
    cc.ipadx = 0;
    cc.ipady = 0;
    cc.insets = new Insets(0, 0, 0, 5);
    toolbar.add(columnFilter, cc);

    JLabel l = new JLabel(ResourceMgr.getString("LblTable") + ":");
    cc.fill = GridBagConstraints.NONE;
    cc.gridx ++;
    cc.weightx = 0.0;
    cc.insets = new Insets(0, 5, 0, 0);
    toolbar.add(l, cc);

    tableNameLabel = new WbLabelField();
    tableNameLabel.useBoldFont();

    cc.fill = GridBagConstraints.HORIZONTAL;
    cc.gridx ++;
    cc.weightx = 1.0;
    cc.insets = WbSwingUtilities.getEmptyInsets();
    toolbar.add(tableNameLabel, cc);

    cc.fill = GridBagConstraints.HORIZONTAL;
    cc.gridx ++;
    cc.weightx = 0;
    cc.fill = GridBagConstraints.NONE;
    cc.anchor = GridBagConstraints.EAST;
    cc.insets = new Insets(0, 15, 0, 0);
    alterButton = new FlatButton(alterColumnsAction);

    alterButton.showMessageOnEnable("MsgApplyDDLHint");
    alterButton.setIcon(null);
    alterButton.setUseDefaultMargin(false);
    toolbar.add(alterButton, cc);

    WbScrollPane scroll = new WbScrollPane(this.tableDefinition, WbSwingUtilities.EMPTY_BORDER);
    this.setLayout(new BorderLayout());
    this.add(toolbar, BorderLayout.NORTH);
    this.add(scroll, BorderLayout.CENTER);

    createIndexAction = new CreateIndexAction(this, this);

    createPKAction = new CreatePKAction(this);
    dropPKAction = new DropPKAction(this);

    tableDefinition.addPopupAction(CreateDummySqlAction.createDummyInsertAction(this, tableDefinition.getSelectionModel()), true);
    tableDefinition.addPopupAction(CreateDummySqlAction.createDummyUpdateAction(this, tableDefinition.getSelectionModel()), false);
    tableDefinition.addPopupAction(CreateDummySqlAction.createDummySelectAction(this, tableDefinition.getSelectionModel()), false);

    tableDefinition.getSelectionModel().addListSelectionListener(this);
    tableDefinition.addPopupAction(this.createPKAction, true);
    tableDefinition.addPopupAction(this.dropPKAction, false);
    tableDefinition.addPopupAction(this.createIndexAction, false);

    WbTraversalPolicy policy = new WbTraversalPolicy();
    policy.addComponent(tableDefinition);
    policy.setDefaultComponent(tableDefinition);
    setFocusCycleRoot(false);
    setFocusTraversalPolicy(policy);

    if (DbExplorerSettings.showFocusInDbExplorer())
    {
      tableDefinition.showFocusBorder();
    }

    if (doRestore)
    {
      restoreSettings();
    }
    initialized = true;
  }

  protected void fireTableDefinitionChanged()
  {
    firePropertyChange(DEFINITION_PROP, null, this.currentTable.getTableName());
  }

  @Override
  public void indexChanged(TableIdentifier table, String indexName)
  {
    firePropertyChange(INDEX_PROP, null, indexName);
  }

  public boolean isBusy()
  {
    synchronized (this.busyLock)
    {
      return busy;
    }
  }

  private void setBusy(boolean flag)
  {
    synchronized (this.busyLock)
    {
      busy = flag;
    }
  }

  public void dispose()
  {
    if (tableDefinition != null) tableDefinition.dispose();
    if (columnFilter != null) columnFilter.dispose();

    WbAction.dispose(
      this.addColumn,this.deleteColumn,this.reloadAction,this.alterColumnsAction,this.createIndexAction,
      this.createPKAction,this.dropColumnsAction,this.dropPKAction
    );

    Settings.getInstance().removePropertyChangeListener(this);
    WbSwingUtilities.removeAllListeners(this);
  }

  /**
   * Retrieve the definition of the given table.
   */
  public void retrieve(TableIdentifier table)
    throws SQLException
  {
    this.currentTable = table;
    initGui();
    retrieveTableDefinition();
  }

  protected void retrieveTableDefinition()
    throws SQLException
  {
    if (this.isBusy())
    {
      LogMgr.logDebug(new CallerInfo(){}, "Panel is busy, not retrieving table definition for current table: " + currentTable);
      return;
    }

    if (currentTable == null)
    {
      LogMgr.logDebug(new CallerInfo(){}, "No current table set!");
      return;
    }

    if (!connectionLock.tryLock())
    {
      LogMgr.logWarning(new CallerInfo(){}, "Concurrent table definition retrieval in process", new Exception("Backtrace"));
      this.reset();
      return;
    }

    try
    {
      WbSwingUtilities.invoke(() ->
      {
        tableDefinition.reset();
        reloadAction.setEnabled(false);
        String msg = " " + ResourceMgr.getString("TxtRetrieveTableDef") + " " + currentTable.getTableName();
        tableNameLabel.setText(msg);
      });

      DbMetadata meta = this.dbConnection.getMetadata();
      DataStore def = null;
      if (dbConnection.getDbSettings().isSynonymType(currentTable.getType()) && !GuiSettings.showSynonymTargetInDbExplorer())
      {
        def = ObjectInfo.getPlainSynonymInfo(dbConnection, currentTable);
      }
      else
      {
        def = meta.getObjectDetails(currentTable);
      }

      final TableModel model = def == null ? EmptyTableModel.EMPTY_MODEL : new DataStoreTableModel(def) ;

      if (def instanceof TableColumnsDatastore)
      {
        DataStoreTableModel dsModel = (DataStoreTableModel)model;
        // Make sure some columns are not modified by the user
        // to avoid the impression that e.g. the column's position
        // can be changed by editing that column
        dsModel.setValidator(validator);

        int typeIndex = dsModel.findColumn(TableColumnsDatastore.JAVA_SQL_TYPE_COL_NAME);
        int posIndex = dsModel.findColumn(TableColumnsDatastore.COLPOSITION_COL_NAME);
        int pkIndex = dsModel.findColumn(TableColumnsDatastore.PKFLAG_COL_NAME);
        dsModel.setNonEditableColums(typeIndex, posIndex, pkIndex);

        if (meta.isTableType(currentTable.getType()) || meta.isViewType(currentTable.getType()))
        {
          List<ColumnIdentifier> cols = TableColumnsDatastore.createColumnIdentifiers(meta, def);
          TableDefinition tbl = new TableDefinition(currentTable, cols);
          dbConnection.getObjectCache().addTable(tbl);
        }

        if (DbExplorerSettings.sortColumnsByName())
        {
          int colNameIndex = dsModel.findColumn(TableColumnsDatastore.COLUMN_NAME_COL_NAME);
          dsModel.sortByColumn(colNameIndex, true, false);
        }
      }

      alterButton.setVisible(dbConnection.getDbSettings().columnCommentAllowed(currentTable.getType()));

      WbSwingUtilities.invoke(() ->
      {
        applyTableModel(model);
        tableDefinition.adjustColumns();
      });
      alterColumnsAction.setSourceTable(dbConnection, currentTable);
      alterColumnsAction.setEnabled(false);
      boolean canAddColumn = dbConnection.getDbSettings().getAddColumnSql() != null && DbExplorerSettings.allowAlterInDbExplorer();
      addColumn.setEnabled(canAddColumn && isTable());
    }
    catch (SQLException e)
    {
      tableNameLabel.setText(ExceptionUtil.getDisplay(e));
      throw e;
    }
    finally
    {
      connectionLock.unlock();
      reloadAction.setEnabled(true);
      setBusy(false);
    }
  }

  protected void applyTableModel(TableModel model)
  {
    tableDefinition.setPrintHeader(this.currentTable.getTableName());
    tableDefinition.setAutoCreateColumnsFromModel(true);
    tableDefinition.setModel(model, true);

    TableIdentifier displayTable = currentTable;
    if (model instanceof DataStoreTableModel)
    {
      DataStore ds = ((DataStoreTableModel)model).getDataStore();
      if (ds instanceof TableColumnsDatastore)
      {
        TableIdentifier realTbl = ((TableColumnsDatastore)ds).getSourceTable();
        if (realTbl != null)
        {
          displayTable = realTbl;
        }
      }
    }

    String displayName;
    if (DbExplorerSettings.getDbExplorerTableDetailFullyQualified())
    {
      displayName = displayTable.getFullyQualifiedName(dbConnection);
    }
    else
    {
      displayName = displayTable.getTableExpression(dbConnection);
    }

    if (model instanceof EmptyTableModel)
    {
      tableNameLabel.setText("");
    }
    else
    {
      tableNameLabel.setText(displayName);
    }

    TableColumnModel colmod = tableDefinition.getColumnModel();

    // Assign the correct renderer to display java.sql.Types values
    // should only appear for table definitions
    try
    {
      int typeIndex = colmod.getColumnIndex(TableColumnsDatastore.JAVA_SQL_TYPE_COL_NAME);
      TableColumn col = colmod.getColumn(typeIndex);
      col.setCellRenderer(new SqlTypeRenderer());
    }
    catch (IllegalArgumentException e)
    {
      // The IllegalArgumentException will be thrown by getColumnIndex()
      // rather than returning a -1 as other methods do.

      // If the Types column is not present, we can simply return as the
      // other columns will then not be there as well.
      return;
    }

    // hide the the columns "SCALE/SIZE", "PRECISION"
    // they don't need to be displayed as this is "included" in the
    // displayed (DBMS) data type already

    // Columns may not be removed from the underlying DataStore because
    // that is also used to retrieve the table source and DbMetadata
    // relies on all columns being present when that datastore is passed
    // to getTableSource()

    // So we need to remove those columns from the view
    String[] columns = new String[] { "SCALE/SIZE", "PRECISION" };
    for (String name : columns)
    {
      try
      {
        int index = colmod.getColumnIndex(name);
        TableColumn col = colmod.getColumn(index);
        colmod.removeColumn(col);
      }
      catch (IllegalArgumentException e)
      {
        // ignore, this is expected for some table types
      }
    }
  }

  @Override
  public void reset()
  {
    if (!initialized) return;

    currentTable = null;
    tableDefinition.reset();
    reloadAction.setEnabled(false);
  }

  private DropDbObjectAction getDropColumnAction()
  {
    if (this.dropColumnsAction == null)
    {
      dropColumnsAction = new DropDbObjectAction("MnuTxtDropColumn", this, tableDefinition.getSelectionModel(), this);
      dropColumnsAction.setDropper(new ColumnDropper());
    }
    return dropColumnsAction;
  }

  public void setConnection(WbConnection conn)
  {
    initGui();
    this.dbConnection = conn;
    this.createIndexAction.setConnection(dbConnection);
    this.reloadAction.setEnabled(dbConnection != null);

    validator.setConnection(dbConnection);

    if (dbConnection != null && dbConnection.getDbSettings().canDropType("column"))
    {
      DropDbObjectAction action = getDropColumnAction();
      action.setAvailable(true);
      this.tableDefinition.addPopupAction(action, false);
    }
    else if (this.dropColumnsAction != null)
    {
      dropColumnsAction.setAvailable(false);
    }

    addColumn.setEnabled(false);
  }

  /**
   * Implement the Reloadable interface for the reload action.
   * This method should not be called directly, use {@link #retrieve(workbench.db.TableIdentifier) }
   * instead.
   */
  @Override
  public void reload()
  {
    if (this.currentTable == null) return;
    if (this.dbConnection == null) return;

    initGui();

    WbThread t = new WbThread("TableDefinition Retrieve")
    {
      @Override
      public void run()
      {
        try
        {
          retrieveTableDefinition();
          fireTableDefinitionChanged();
        }
        catch (SQLException ex)
        {
          LogMgr.logError(new CallerInfo(){}, "Error loading table definition", ex);
        }
      }
    };
    t.start();
  }

  @Override
  public List<TableIdentifier> getSelectedTables()
  {
    return Collections.emptyList();
  }

  @Override
  public List<DbObject> getSelectedObjects()
  {
    if (this.tableDefinition.getSelectedRowCount() <= 0) return null;
    int[] rows = this.tableDefinition.getSelectedRows();

    List<DbObject> columns = new ArrayList<>(rows.length);

    for (int i=0; i < rows.length; i++)
    {
      String column = this.tableDefinition.getValueAsString(rows[i], TableColumnsDatastore.COLUMN_NAME_COL_NAME);

      // the column name can be empty if a new column has just been inserted in the definition display
      if (StringUtil.isNotBlank(column))
      {
        columns.add(new ColumnIdentifier(column));
      }
    }
    return columns;
  }

  @Override
  public Component getComponent()
  {
    return this;
  }

  @Override
  public WbConnection getConnection()
  {
    return this.dbConnection;
  }

  @Override
  public TableIdentifier getObjectTable()
  {
    return this.currentTable;
  }

  @Override
  public int getSelectionCount()
  {
    return tableDefinition.getSelectedRowCount();
  }

  @Override
  public TableDefinition getCurrentTableDefinition()
  {
    return null;
  }

  protected boolean isTable()
  {
    if (currentTable == null) return false;
    if (dbConnection == null) return false;

    String type = currentTable.getType();
    return dbConnection.getMetadata().isExtendedTableType(type);
  }

  protected boolean isMview()
  {
    if (currentTable == null) return false;
    if (dbConnection == null) return false;

    String type = currentTable.getType();
    return dbConnection.getDbSettings().isMview(type);
  }

  protected boolean hasIndex()
  {
    if (currentTable == null) return false;
    if (dbConnection == null) return false;
    if (isTable()) return true;
    if (isMview()) return true;
    String type = currentTable.getType();
    return dbConnection.getMetadata().isViewType(type) && dbConnection.getDbSettings().supportsIndexedViews();
  }

  protected boolean hasPkColumn()
  {
    if (!isTable()) return false;
    for (int row = 0; row < this.tableDefinition.getRowCount(); row++)
    {
      String flag = tableDefinition.getValueAsString(row, TableColumnsDatastore.PKFLAG_COL_NAME);
      boolean isPk = StringUtil.stringToBool(flag);
      if (isPk) return true;
    }
    return false;
  }

  /**
   * Invoked when the selection in the table list has changed
   */
  @Override
  public void valueChanged(ListSelectionEvent e)
  {
    if (!initialized) return;

    if (e.getValueIsAdjusting()) return;
    if (e.getSource() == this.tableDefinition.getSelectionModel())
    {
      boolean rowsSelected = (this.tableDefinition.getSelectedRowCount() > 0);

      boolean isTable = isTable();
      boolean isMview = isMview();
      boolean hasPk = hasPkColumn();
      createPKAction.setEnabled(rowsSelected && isTable && !hasPk);
      dropPKAction.setEnabled(isTable && hasPk);
      createIndexAction.setEnabled(rowsSelected && hasIndex());
      deleteColumn.setEnabled(rowsSelected && (isTable || isMview) && DbExplorerSettings.allowAlterInDbExplorer());
    }
  }

  public List<ColumnIdentifier> getColumns()
  {
    return TableColumnsDatastore.createColumnIdentifiers(this.dbConnection.getMetadata(), this.tableDefinition.getDataStore());
  }

  public int getRowCount()
  {
    if (tableDefinition == null) return 0;
    return this.tableDefinition.getRowCount();
  }

  public DataStore getDataStore()
  {
    if (tableDefinition == null) return null;
    return this.tableDefinition.getDataStore();
  }

  public void restoreSettings()
  {
    if (columnFilter != null)
    {
      this.columnFilter.restoreSettings();
      doRestore = false;
    }
    else
    {
      doRestore = true;
    }
  }

  public void saveSettings()
  {
    if (columnFilter != null)
    {
      this.columnFilter.saveSettings();
      doRestore = false;
    }
    else
    {
      doRestore = true;
    }
  }

  private void updateReadOnlyState()
  {
    tableDefinition.setReadOnly(!DbExplorerSettings.allowAlterInDbExplorer());
    tableDefinition.setAllowEditMode(true);
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt)
  {
    if (evt.getPropertyName().equals(DbExplorerSettings.PROP_ALLOW_ALTER_TABLE))
    {
      updateReadOnlyState();
    }
  }
}
