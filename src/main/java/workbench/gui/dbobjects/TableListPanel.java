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
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import javax.swing.ActionMap;
import javax.swing.ComponentInputMap;
import javax.swing.InputMap;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;

import workbench.WbManager;
import workbench.interfaces.DbExecutionListener;
import workbench.interfaces.ListSelectionControl;
import workbench.interfaces.ObjectDropListener;
import workbench.interfaces.PropertyStorage;
import workbench.interfaces.Reloadable;
import workbench.interfaces.Resettable;
import workbench.interfaces.ShareableDisplay;
import workbench.interfaces.WbSelectionModel;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.DbExplorerSettings;
import workbench.resource.GuiSettings;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;
import workbench.workspace.WbWorkspace;

import workbench.db.ColumnIdentifier;
import workbench.db.DBID;
import workbench.db.DbMetadata;
import workbench.db.DbObject;
import workbench.db.DbObjectComparator;
import workbench.db.DbSettings;
import workbench.db.DropType;
import workbench.db.GenerationOptions;
import workbench.db.GenericObjectDropper;
import workbench.db.IndexDefinition;
import workbench.db.IndexReader;
import workbench.db.ObjectListDataStore;
import workbench.db.PartitionLister;
import workbench.db.SequenceReader;
import workbench.db.SynonymDDLHandler;
import workbench.db.TableColumnsDatastore;
import workbench.db.TableDefinition;
import workbench.db.TableIdentifier;
import workbench.db.TableSourceBuilder;
import workbench.db.TableSourceBuilderFactory;
import workbench.db.TriggerReader;
import workbench.db.TriggerReaderFactory;
import workbench.db.WbConnection;
import workbench.db.dependency.DependencyReader;
import workbench.db.dependency.DependencyReaderFactory;

import workbench.gui.MainWindow;
import workbench.gui.WbSwingUtilities;
import workbench.gui.actions.AlterObjectAction;
import workbench.gui.actions.CompileDbObjectAction;
import workbench.gui.actions.CreateDropScriptAction;
import workbench.gui.actions.CreateDummySqlAction;
import workbench.gui.actions.DeleteTablesAction;
import workbench.gui.actions.DropDbObjectAction;
import workbench.gui.actions.ReloadAction;
import workbench.gui.actions.SchemaReportAction;
import workbench.gui.actions.ScriptDbObjectAction;
import workbench.gui.actions.SpoolDataAction;
import workbench.gui.actions.ToggleTableSourceAction;
import workbench.gui.actions.WbAction;
import workbench.gui.components.DataStoreTableModel;
import workbench.gui.components.DividerBorder;
import workbench.gui.components.FlatButton;
import workbench.gui.components.MultiSelectComboBox;
import workbench.gui.components.QuickFilterPanel;
import workbench.gui.components.WbScrollPane;
import workbench.gui.components.WbSplitPane;
import workbench.gui.components.WbTabbedPane;
import workbench.gui.components.WbTable;
import workbench.gui.components.WbTraversalPolicy;
import workbench.gui.dbobjects.objecttree.ObjectFinder;
import workbench.gui.dbobjects.objecttree.RowCountDisplay;
import workbench.gui.dbobjects.objecttree.ShowRowCountAction;
import workbench.gui.filter.FilterDefinitionManager;
import workbench.gui.renderer.RendererSetup;
import workbench.gui.settings.PlacementChooser;

import workbench.storage.DataStore;
import workbench.storage.NamedSortDefinition;
import workbench.storage.SortDefinition;

import workbench.util.CollectionUtil;
import workbench.util.ExceptionUtil;
import workbench.util.LowMemoryException;
import workbench.util.StringUtil;
import workbench.util.WbProperties;
import workbench.util.WbThread;

import static workbench.storage.NamedSortDefinition.*;

/**
 * A panel that displays a list of tables, views and other database objects.
 * Essentially everything returned by DbMetadata.getObjects()
 *
 * @author Thomas Kellerer
 * @see workbench.db.DbMetadata#getObjects(java.lang.String, java.lang.String, java.lang.String[])
 */
public class TableListPanel
  extends JPanel
  implements ActionListener, ChangeListener, ListSelectionListener, MouseListener,
             ShareableDisplay, PropertyChangeListener, ObjectFinder,
             TableModelListener, DbObjectList, ListSelectionControl, TableLister,
             ObjectDropListener, RowCountDisplay
{
  public static final String PROP_DO_SAVE_SORT = "workbench.gui.dbexplorer.tablelist.sort";

  // <editor-fold defaultstate="collapsed" desc=" Variables ">
  private static final ColumnIdentifier ROW_COUNT_COLUMN = new ColumnIdentifier("ROWCOUNT", Types.INTEGER, 5);
  protected WbConnection dbConnection;
  protected JPanel listPanel;
  protected QuickFilterPanel findPanel;
  protected DbObjectTable tableList;
  protected TableDefinitionPanel tableDefinition;
  protected WbTable indexes;
  protected VerticaProjectionPanel projections;
  protected FkDisplayPanel importedKeys;
  protected FkDisplayPanel exportedKeys;
  protected ReloadAction reloadAction;

  protected TableDataPanel tableData;
  protected ObjectDependencyPanel dependencyPanel;
  protected TablePartitionsPanel partitionsPanel;

  private final TableIndexPanel indexPanel;
  private final TriggerDisplayPanel triggers;
  protected DbObjectSourcePanel tableSource;
  private final WbTabbedPane displayTab;
  private final WbSplitPane splitPane;

  private final MultiSelectComboBox tableTypes;
  private String currentSchema;
  private String currentCatalog;
  private final SpoolDataAction spoolData;

  private CompileDbObjectAction compileAction;
  private ShowRowCountAction rowCountAction;
  private final AlterObjectAction renameAction;

  private final MainWindow parentWindow;

  private TableIdentifier selectedTable;

  private JComboBox tableHistory;

  private boolean shiftDown;
  protected boolean shouldRetrieve;

  protected boolean shouldRetrieveTable;
  protected boolean shouldRetrieveTableSource;
  protected boolean shouldRetrieveTriggers;
  protected boolean shouldRetrieveIndexes;
  protected boolean shouldRetrieveProjections;
  protected boolean shouldRetrieveExportedKeys;
  protected boolean shouldRetrieveImportedKeys;
  protected boolean shouldRetrievePartitions;
  protected boolean shouldRetrieveTableData;

  protected boolean busy;
  protected boolean ignoreStateChanged;

  private EditorTabSelectMenu showDataMenu;

  private final ToggleTableSourceAction toggleTableSource;

  // holds a reference to other WbTables which
  // need to display the same table list
  // e.g. the table search panel
  private List<JTable> tableListClients;

  private NamedSortDefinition savedSort;

  protected JDialog infoWindow;
  private final JPanel statusPanel;
  private final FlatButton alterButton;
  private final SummaryLabel summaryStatusBarLabel;
  private String tableTypeToSelect;

  private final ReentrantLock connectionLock = new ReentrantLock();

  private final TableChangeValidator validator = new TableChangeValidator();
  private final IsolationLevelChanger levelChanger = new IsolationLevelChanger();
  private FilterDefinitionManager filterMgr;

  private final int maxTypeItems = 25;
  private int currentRetrievalPanel = -1;

  private DbObject objectToSelect;
  // </editor-fold>

  public TableListPanel(MainWindow aParent)
    throws Exception
  {
    super();
    this.parentWindow = aParent;
    this.setBorder(WbSwingUtilities.EMPTY_BORDER);

    int location = PlacementChooser.getDBExplorerTabLocation();
    displayTab = new WbTabbedPane(location);
    displayTab.setBorder(WbSwingUtilities.EMPTY_BORDER);
    displayTab.setName("displaytab");

    tableTypes = new MultiSelectComboBox();
    tableTypes.setCloseOnSelect(DbExplorerSettings.getDbExplorerMultiSelectTypesAutoClose());

    this.tableDefinition = new TableDefinitionPanel();
    this.tableDefinition.setName("tabledefinition");
    this.tableDefinition.addPropertyChangeListener(TableDefinitionPanel.INDEX_PROP, this);
    this.tableDefinition.addPropertyChangeListener(TableDefinitionPanel.DEFINITION_PROP, this);

    Reloadable indexReload = () ->
    {
      shouldRetrieveIndexes = true;
      startRetrieveCurrentPanel();
    };

    this.indexes = new WbTable();
    this.indexes.setRendererSetup(RendererSetup.getBaseSetup());
    this.indexes.setName("indexlist");
    this.indexes.setAdjustToColumnLabel(false);
    this.indexes.setSelectOnRightButtonClick(true);
    this.indexPanel = new TableIndexPanel(this.indexes, indexReload);

    Reloadable sourceReload = () ->
    {
      shouldRetrieveTable = true;
      shouldRetrieveIndexes = true;
      shouldRetrieveTableSource = true;
      startRetrieveCurrentPanel();
    };

    this.tableSource = new DbObjectSourcePanel(aParent, sourceReload);
    this.tableSource.setTableFinder(this);
    this.tableSource.allowReformat();
    if (DbExplorerSettings.allowSourceEditing())
    {
      tableSource.allowEditing(true);
    }

    this.tableData = new TableDataPanel();

    this.importedKeys = new FkDisplayPanel(this, true);
    this.exportedKeys = new FkDisplayPanel(this, false);

    this.triggers = new TriggerDisplayPanel();

    this.listPanel = new JPanel();
    this.tableList = new DbObjectTable(PROP_DO_SAVE_SORT);

    this.tableList.setName("dbtablelist");
    this.tableList.setSelectOnRightButtonClick(true);
    this.tableList.getSelectionModel().addListSelectionListener(this);
    this.tableList.getSelectionModel().setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
    this.tableList.setAdjustToColumnLabel(true);
    this.tableList.addTableModelListener(this);
    this.tableList.setSortIgnoreCase(Settings.getInstance().sortTableListIgnoreCase());
    this.tableList.setUseNaturalSort(Settings.getInstance().useNaturalSortForTableList());

    this.spoolData = new SpoolDataAction(this);
    this.tableList.addPopupAction(spoolData, true);

    renameAction = new AlterObjectAction(tableList);
    renameAction.setReloader(this);
    renameAction.addPropertyChangeListener(this);

    this.extendPopupMenu();
    if (DbExplorerSettings.enableExtendedObjectFilter())
    {
      filterMgr = new FilterDefinitionManager();
    }
    findPanel =  new QuickFilterPanel(this.tableList, false, filterMgr, "tablelist");

    Settings.getInstance().addPropertyChangeListener(this,
      DbExplorerSettings.PROP_INSTANT_FILTER,
      DbExplorerSettings.PROP_ASSUME_WILDCARDS,
      PlacementChooser.DBEXPLORER_LOCATION_PROPERTY,
      DbExplorerSettings.PROP_TABLE_HISTORY,
      DbExplorerSettings.PROP_USE_FILTER_RETRIEVE,
      DbExplorerSettings.PROP_ALLOW_SOURCE_EDITING
    );

    reloadAction = new ReloadAction(this);
    reloadAction.setUseLabelIconSize(true);
    reloadAction.getToolbarButton().setToolTipText(ResourceMgr.getString("TxtRefreshTableList"));
    reloadAction.addToInputMap(tableList);

    configureFindPanel();
    this.findPanel.addToToolbar(reloadAction, true, false);

    JPanel selectPanel = new JPanel();
    selectPanel.setLayout(new FlowLayout(FlowLayout.LEFT));

    JPanel topPanel = new JPanel();
    topPanel.setLayout(new GridBagLayout());
    GridBagConstraints constr = new GridBagConstraints();
    constr.gridx = 0;
    constr.gridy = 0;
    constr.gridwidth = 1;
    constr.fill = GridBagConstraints.HORIZONTAL;
    constr.anchor = GridBagConstraints.FIRST_LINE_START;
    constr.weightx = 0.6;

    topPanel.add(this.tableTypes, constr);

    constr.gridx++;
    constr.weightx = 1.0;
    topPanel.add((JPanel)this.findPanel, constr);

    this.listPanel.setLayout(new BorderLayout());
    this.listPanel.add(topPanel, BorderLayout.NORTH);

    this.statusPanel = new JPanel(new BorderLayout());
    this.statusPanel.setBorder(new EmptyBorder(2, 0, 1, 0));
    this.alterButton = new FlatButton(this.renameAction);
    alterButton.showMessageOnEnable("MsgApplyDDLHint");
    this.alterButton.setResourceKey("MnuTxtRunAlter");

    this.summaryStatusBarLabel = new SummaryLabel("");
    this.statusPanel.add(summaryStatusBarLabel, BorderLayout.CENTER);

    if (DbExplorerSettings.getDbExplorerShowTableHistory())
    {
      showTableHistory();
    }

    this.listPanel.add(statusPanel, BorderLayout.SOUTH);

    this.splitPane = new WbSplitPane(JSplitPane.HORIZONTAL_SPLIT);
    WbScrollPane scroll = new WbScrollPane(this.tableList, WbSwingUtilities.EMPTY_BORDER);

    this.listPanel.add(scroll, BorderLayout.CENTER);
    this.listPanel.setBorder(WbSwingUtilities.EMPTY_BORDER);
    this.splitPane.setLeftComponent(this.listPanel);
    this.splitPane.setRightComponent(displayTab);
    this.splitPane.setDividerBorder(DividerBorder.RIGHT_DIVIDER);
    this.splitPane.setOneTouchExpandable(true);
    this.splitPane.setContinuousLayout(true);

    this.setLayout(new BorderLayout());
    this.add(splitPane, BorderLayout.CENTER);

    WbTraversalPolicy pol = new WbTraversalPolicy();
    pol.setDefaultComponent((JPanel)findPanel);
    pol.addComponent((JPanel)findPanel);
    pol.addComponent(tableList);
    pol.addComponent(tableDefinition);
    this.setFocusTraversalPolicy(pol);
    this.setFocusCycleRoot(false);
    this.displayTab.addMouseListener(this);
    this.tableList.addMouseListener(this);

    initIndexDropper(indexReload);

    this.toggleTableSource = new ToggleTableSourceAction(this, "MnuTxtToggleDbExpSplit");
    this.splitPane.setOneTouchTooltip(toggleTableSource.getTooltipTextWithKeys());
    setupActionMap();

    if (DbExplorerSettings.showFocusInDbExplorer())
    {
      EventQueue.invokeLater(() ->
      {
        indexes.showFocusBorder();
        tableList.showFocusBorder();
      });
    }

    projections = new VerticaProjectionPanel();
    tableList.setRememberColumnOrder(DbExplorerSettings.getRememberMetaColumnOrder("tablelist"));
    tableList.setListSelectionControl(this);
    tableList.setReadOnly(!DbExplorerSettings.allowAlterInDbExplorer());
    tableList.setAllowEditMode(true);
    dependencyPanel = new ObjectDependencyPanel();
    showObjectDefinitionPanels(false);
  }

  private boolean getApplyFilterWhileTyping()
  {
    if (DbExplorerSettings.getUseFilterForRetrieve()) return false;
    return DbExplorerSettings.getFilterDuringTyping();
  }

  private void hideTableHistory()
  {
    if (tableHistory != null)
    {
      disposeTableHistory();
      statusPanel.remove(tableHistory);
      tableHistory = null;
      updateStatusPanel();
    }
  }

  private void resetTableHistory()
  {
    if (tableHistory != null)
    {
      TableHistoryModel model = (TableHistoryModel) tableHistory.getModel();
      model.removeAllElements();
    }
  }

  private void disposeTableHistory()
  {
    if (tableHistory != null)
    {
      TableHistoryModel model = (TableHistoryModel) tableHistory.getModel();
      model.removeAllElements();
      model.clearListeners();
      tableHistory.removeActionListener(this);
    }

  }

  private void showTableHistory()
  {
    if (tableHistory == null)
    {
      this.tableHistory = new JComboBox();
      this.tableHistory.addActionListener(this);
      this.tableHistory.setModel(new TableHistoryModel());
      this.statusPanel.add(tableHistory, BorderLayout.NORTH);
      updateStatusPanel();
    }
  }

  private void updateStatusPanel()
  {
    listPanel.invalidate();
    listPanel.validate();
  }

  private void initIndexDropper(Reloadable indexReload)
  {
    DbObjectList indexList = new DbObjectList()
    {
      @Override
      public void reload()
      {
        TableListPanel.this.reload();
      }

      @Override
      public Component getComponent()
      {
        return TableListPanel.this;
      }

      @Override
      public WbConnection getConnection()
      {
        return dbConnection;
      }

      @Override
      public TableIdentifier getObjectTable()
      {
        return TableListPanel.this.getObjectTable();
      }

      @Override
      public TableDefinition getCurrentTableDefinition()
      {
        return TableListPanel.this.getCurrentTableDefinition();
      }

      @Override
      public int getSelectionCount()
      {
        return indexes.getSelectedRowCount();
      }

      @Override
      public List<TableIdentifier> getSelectedTables()
      {
        return Collections.emptyList();
      }

      @Override
      public List<DbObject> getSelectedObjects()
      {
        int[] rows = indexes.getSelectedRows();
        if (rows == null) return null;
        TableIdentifier tbl = getObjectTable();
        if (tbl == null) return null;

        ArrayList<DbObject> objects = new ArrayList<>(rows.length);

        for (int i = 0; i < rows.length; i++)
        {
          String name = indexes.getValueAsString(rows[i], IndexReader.COLUMN_IDX_TABLE_INDEXLIST_INDEX_NAME);
          IndexDefinition index = new IndexDefinition(tbl, name);
          objects.add(index);
        }
        return objects;
      }
    };

    DropDbObjectAction dropAction = new DropDbObjectAction("MnuTxtDropIndex", indexList, indexes.getSelectionModel(), indexReload);
    this.indexes.addPopupAction(dropAction, true);
  }

  public boolean isModified()
  {
    if (this.tableData == null) return false;
    return tableData.isModified();
  }

  public void dispose()
  {
    reset();
    disposeTableHistory();
    tableDefinition.dispose();
    tableList.dispose();
    tableData.dispose();
    tableSource.dispose();
    findPanel.dispose();
    dependencyPanel.dispose();
    if (indexes != null) indexes.dispose();
    if (indexPanel != null) indexPanel.dispose();
    if (projections != null) projections.dispose();
    if (partitionsPanel != null) partitionsPanel.dispose();
    WbAction.dispose(compileAction,rowCountAction,reloadAction,renameAction,spoolData,toggleTableSource);
    Settings.getInstance().removePropertyChangeListener(this);
  }


  private void extendPopupMenu()
  {
    rowCountAction = new ShowRowCountAction(this, this, summaryStatusBarLabel);
    tableList.addPopupAction(rowCountAction, false);

    if (this.parentWindow != null)
    {
      this.showDataMenu = new EditorTabSelectMenu(ResourceMgr.getString("MnuTxtShowTableData"), "LblShowDataInNewTab", "LblShowDataInTab", parentWindow, null, true);
      this.showDataMenu.setObjectList(this);
      this.showDataMenu.setEnabled(false);
      this.tableList.addPopupMenu(this.showDataMenu, false);
    }

    this.tableList.addPopupAction(CreateDummySqlAction.createDummyInsertAction(this, tableList.getSelectionModel()), true);
    this.tableList.addPopupAction(CreateDummySqlAction.createDummyUpdateAction(this, tableList.getSelectionModel()), false);
    this.tableList.addPopupAction(CreateDummySqlAction.createDummySelectAction(this, tableList.getSelectionModel()), false);

    WbSelectionModel list = WbSelectionModel.Factory.createFacade(tableList.getSelectionModel());
    ScriptDbObjectAction createScript = new ScriptDbObjectAction(this, list);
    this.tableList.addPopupAction(createScript, false);

    SchemaReportAction action = new SchemaReportAction(this);
    tableList.addPopupMenu(action.getMenuItem(), false);

    compileAction = new CompileDbObjectAction(this, list);
    tableList.addPopupAction(compileAction, false);

    DropDbObjectAction dropAction = new DropDbObjectAction(this, list);
    dropAction.addDropListener(this);
    tableList.addPopupAction(dropAction, true);

    CreateDropScriptAction dropScript = new CreateDropScriptAction(this, list);
    this.tableList.addPopupAction(dropScript, false);

    tableList.addPopupAction(new DeleteTablesAction(this, list, this.tableData), false);
    tableList.addPopupAction(renameAction, true);
  }

  public void setDbExecutionListener(DbExecutionListener l)
  {
    tableData.addDbExecutionListener(l);
  }

  private void setDirty(boolean flag)
  {
    this.shouldRetrieve = flag;
  }

  private void setupActionMap()
  {
    InputMap im = new ComponentInputMap(this);
    ActionMap am = new ActionMap();
    this.setInputMap(WHEN_IN_FOCUSED_WINDOW, im);
    this.setActionMap(am);

    this.toggleTableSource.addToInputMap(im, am);
  }

  /**
   * Displays the tabs necessary for a TABLE like object
   */
  protected void showTablePanels()
  {
    // already showing the table definition?
    if (displayTab.indexOfComponent(exportedKeys) > -1) return;

    WbSwingUtilities.invoke(() ->
    {
      try
      {
        ignoreStateChanged = true;
        int index = displayTab.getSelectedIndex();
        displayTab.removeAll();
        addBaseObjectPanels();
        addDataPanel();
        if (DBID.Vertica.isDB(dbConnection))
        {
          displayTab.add(ResourceMgr.getString("TxtDbExplorerProjections"), projections);
        }
        else
        {
          addIndexPanel();
        }
        displayTab.add(ResourceMgr.getString("TxtDbExplorerFkColumns"), importedKeys);
        displayTab.add(ResourceMgr.getString("TxtDbExplorerReferencedColumns"), exportedKeys);
        addTriggerPanel();
        addDependencyPanelIfSupported();
        addPartitionsPanelIfSupported();
        restoreIndex(index);
      }
      finally
      {
        ignoreStateChanged = false;
      }
    });
  }

  /**
   * Displays the tabs necessary for a TABLE like object
   */
  protected void showOnlySourcePanel()
  {
    WbSwingUtilities.invoke(() ->
    {
      try
      {
        ignoreStateChanged = true;
        displayTab.removeAll();
        addSourcePanel();
        displayTab.setSelectedIndex(0);
      }
      finally
      {
        ignoreStateChanged = false;
      }
    });
  }

  private void restoreIndex(int index)
  {
    if (displayTab.getTabCount() == 0) return;

    if (index >= 0 && index < displayTab.getTabCount())
    {
      displayTab.setSelectedIndex(index);
    }
    else
    {
      displayTab.setSelectedIndex(0);
    }
  }

  /**
   * Displays the tabs common to all DB objects
   * (essentially object definition and source).
   *
   * @param includeDataPanel if true, the Data panel will also be displayed
   */
  private void showObjectDefinitionPanels(boolean includeDataPanel)
  {
    int count = displayTab.getTabCount();

    WbSwingUtilities.invoke(() ->
    {
      try
      {
        int index = displayTab.getSelectedIndex();
        ignoreStateChanged = true;
        displayTab.removeAll();
        currentRetrievalPanel = -1;

        addBaseObjectPanels();
        if (includeDataPanel) addDataPanel();
        showIndexesIfSupported();
        showTriggerIfSupported();

        addDependencyPanelIfSupported();
        addPartitionsPanelIfSupported();

        dependencyPanel.reset();
        exportedKeys.reset();
        indexes.reset();
        triggers.reset();
        importedKeys.reset();
        projections.reset();

        if (!includeDataPanel)
        {
          tableData.reset();
          shouldRetrieveTableData = false;
        }

        if (displayTab.getTabCount() != count)
        {
          displayTab.setSelectedIndex(0);
        }
        else
        {
          restoreIndex(index);
        }
      }
      finally
      {
        ignoreStateChanged = false;
      }
    });
  }

  private void addDependencyPanelIfSupported()
  {
    if (dbConnection == null) return;
    DependencyReader reader = DependencyReaderFactory.getReader(dbConnection);
    TableIdentifier tbl = getObjectTable();
    if (tbl == null) return;
    if (reader != null && (reader.supportsIsUsingDependency(tbl.getType()) || reader.supportsUsedByDependency(tbl.getObjectType())) )
    {
      displayTab.add(ResourceMgr.getString("TxtDeps"), dependencyPanel);
    }
  }

  private void addPartitionsPanelIfSupported()
  {
    if (dbConnection == null) return;
    PartitionLister lister = PartitionLister.Factory.createReader(dbConnection);
    if (lister != null && isTable())
    {
      TablePartitionsPanel panel = getPartitionsPanel();
      panel.reset();
      panel.setCurrentTable(selectedTable);
      displayTab.add(ResourceMgr.getString("TxtPartitions"), panel);
    }
  }

  private synchronized TablePartitionsPanel getPartitionsPanel()
  {
    if (this.partitionsPanel == null)
    {
      this.partitionsPanel = new TablePartitionsPanel();
      this.partitionsPanel.setConnection(dbConnection);
    }
    return this.partitionsPanel;
  }

  private boolean viewTriggersSupported()
  {
    TriggerReader reader = TriggerReaderFactory.createReader(dbConnection);

    if (reader == null) return false;
    return reader.supportsTriggersOnViews();
  }

  private void showTriggerIfSupported()
  {
    if (!viewTriggersSupported()) return;

    TableIdentifier tbl = getObjectTable();
    if (tbl == null) return;
    DbSettings dbs = dbConnection.getDbSettings();
    if (dbs.isViewType(tbl.getType()))
    {
      addTriggerPanel();
    }
  }

  private void showIndexesIfSupported()
  {
    TableIdentifier tbl = getObjectTable();
    if (tbl == null) return;
    DbSettings dbs = dbConnection.getDbSettings();
    if (dbs.isViewType(tbl.getType()) && dbs.supportsIndexedViews())
    {
      addIndexPanel();
    }
    if (dbs.isMview(tbl.getType()))
    {
      addIndexPanel();
    }
  }

  protected void addTriggerPanel()
  {
    displayTab.add(ResourceMgr.getString("TxtDbExplorerTriggers"), triggers);
  }

  protected void addIndexPanel()
  {
    displayTab.add(ResourceMgr.getString("TxtDbExplorerIndexes"), indexPanel);
  }

  protected void addBaseObjectPanels()
  {
    TableIdentifier tbl = getObjectTable();
    if (tbl == null) return;
    if (dbConnection.getMetadata().hasColumns(tbl))
    {
      displayTab.add(ResourceMgr.getString("TxtDbExplorerTableDefinition"), tableDefinition);
    }
    addSourcePanel();
  }

  protected void addSourcePanel()
  {
    displayTab.add(ResourceMgr.getString("TxtDbExplorerSource"), tableSource);
  }

  protected void addDataPanel()
  {
    displayTab.add(ResourceMgr.getString("TxtDbExplorerData"), tableData);
  }

  private boolean sourceExpanded = false;

  public void toggleExpandSource()
  {
    if (sourceExpanded)
    {
      int last = this.splitPane.getLastDividerLocation();
      this.splitPane.setDividerLocation(last);
    }
    else
    {
      int current = this.splitPane.getDividerLocation();
      this.splitPane.setLastDividerLocation(current);
      this.splitPane.setDividerLocation(0);
    }
    sourceExpanded = !sourceExpanded;
  }

  public void setInitialFocus()
  {
    findPanel.setFocusToEntryField();
  }

  public void disconnect()
  {
    try
    {
      tableData.storeColumnOrder();
      // resetChangedFlags() sets and clears the ignoreStateChanged flag as well!
      this.reset();
      this.ignoreStateChanged = true;
      this.dbConnection = null;
      this.tableTypes.removeActionListener(this);
      this.displayTab.removeChangeListener(this);
      this.tableData.setConnection(null);
      this.tableTypes.removeAllItems();
      this.tableDefinition.setConnection(null);
      if (partitionsPanel != null)
      {
        partitionsPanel.setConnection(null);
      }
    }
    finally
    {
      this.ignoreStateChanged = false;
    }
  }

  public void reset()
  {
    this.selectedTable = null;
    this.invalidateData();

    if (this.isBusy())
    {
      return;
    }

    tableList.saveColumnOrder();

    WbSwingUtilities.invoke(() ->
    {
      tableList.cancelEditing();
      if (displayTab.getTabCount() > 0)
      {
        try
        {
          ignoreStateChanged = true;
          displayTab.setSelectedIndex(0);
        }
        finally
        {
          ignoreStateChanged = false;
        }
      }
      tableDefinition.reset();
      importedKeys.reset();
      exportedKeys.reset();
      if (projections != null) projections.reset();
      indexes.reset();
      triggers.reset();
      tableSource.reset();
      tableData.reset();
      tableList.reset();
      if (partitionsPanel != null)
      {
        partitionsPanel.reset();
      }
      resetTableHistory();
    });
  }

  protected void resetCurrentPanel()
  {
    WbSwingUtilities.invoke(() ->
    {
      Resettable panel = (Resettable)displayTab.getSelectedComponent();
      if (panel != null)
      {
        panel.reset();
      }
    });
  }

  protected void invalidateData()
  {
    shouldRetrieveTable = true;
    shouldRetrieveTableData = true;
    if (this.tableData != null)
    {
      if (this.selectedTable != null)
      {
        this.tableData.setTable(this.selectedTable);
      }
      else
      {
        this.tableData.reset();
      }
    }
    if (this.partitionsPanel != null)
    {
      this.partitionsPanel.setCurrentTable(selectedTable);
    }
    shouldRetrieveTableSource = true;
    shouldRetrieveTriggers = true;
    shouldRetrieveIndexes = true;
    shouldRetrieveExportedKeys = true;
    shouldRetrieveImportedKeys = true;
    shouldRetrieveProjections = true;
    shouldRetrievePartitions = true;
  }

  private void setupObjectTypes()
  {
    List<String> types = new ArrayList<>(this.dbConnection.getMetadata().getObjectTypes());
    List<String> toSelect = new ArrayList<>();

    if (tableTypeToSelect != null)
    {
      if (tableTypeToSelect.equals("*"))
      {
        toSelect.addAll(types);
      }
      else
      {
        toSelect = StringUtil.stringToList(tableTypeToSelect.toUpperCase(), ",", true, true, false, false);
      }
    }

    // setItems() will clear all previous items
    tableTypes.setItems(types, toSelect);

    if (toSelect.isEmpty())
    {
      tableTypes.selectAll();
    }
  }

  private void initVertica()
  {
    if (dbConnection == null) return;

    if (DBID.Vertica.isDB(dbConnection))
    {
      projections.setConnection(dbConnection);
    }
    else
    {
      if (projections != null)
      {
        projections.reset();
        displayTab.remove(projections);
      }
    }
  }

  public void setConnection(WbConnection connection)
  {
    dbConnection = connection;

    tableTypes.removeActionListener(this);
    displayTab.removeChangeListener(this);

    importedKeys.setConnection(connection);
    exportedKeys.setConnection(connection);
    tableData.setConnection(connection);
    tableDefinition.setConnection(connection);
    triggers.setConnection(connection);
    tableSource.setDatabaseConnection(connection);
    dependencyPanel.setConnection(connection);
    if (partitionsPanel != null)
    {
      partitionsPanel.setConnection(connection);
    }

    renameAction.setConnection(dbConnection);
    validator.setConnection(dbConnection);

    reset();

    setupObjectTypes();
    tableTypes.setMaximumRowCount(Math.min(tableTypes.getItemCount() + 1, maxTypeItems));

    this.tableTypes.addActionListener(this);
    this.displayTab.addChangeListener(this);
    this.compileAction.setConnection(connection);
    initVertica();
  }

  public boolean isReallyVisible()
  {
    if (!this.isVisible()) return false;
    Window w = SwingUtilities.getWindowAncestor(this);
    if (w == null) return false;
    return (w.isVisible());
  }

  public void setCatalogAndSchema(String newCatalog, String newSchema, boolean retrieve)
    throws Exception
  {
    this.currentSchema = newSchema;
    this.currentCatalog = newCatalog;

    invalidateData();

    if (this.isBusy())
    {
      setDirty(retrieve);
      return;
    }

    reset();

    if (!retrieve) return;

    if (this.dbConnection == null) return;

    if (isReallyVisible() || isClientVisible())
    {
      retrieve();
      setFocusToTableList();
    }
    else
    {
      setDirty(true);
    }
  }

  @Override
  public void tableChanged(TableModelEvent e)
  {
    this.summaryStatusBarLabel.showObjectListInfo(tableList.getDataStoreTableModel());
  }

  protected void checkAlterButton()
  {
    WbSwingUtilities.invoke(() ->
    {
      if (renameAction.isEnabled() && !WbSwingUtilities.containsComponent(statusPanel, alterButton))
      {
        statusPanel.add(alterButton, BorderLayout.EAST);
        statusPanel.validate();
      }
      else
      {
        statusPanel.remove(alterButton);
      }
    });
  }

  protected void setFocusToTableList()
  {
    EventQueue.invokeLater(() ->
    {
      listPanel.requestFocus();
      tableList.requestFocus();
    });
  }

  private String[] getSelectedTypes()
  {
    if (tableTypes == null) return null;
    List<String> items = tableTypes.getSelectedItems();
    return items.toArray(String[]::new);
  }

  public void retrieve()
  {
    if (this.isBusy())
    {
      invalidateData();
      return;
    }

    final CallerInfo ci = new CallerInfo(){};

    if (dbConnection == null)
    {
      LogMgr.logDebug(ci, "Connection object not accessible", new Exception());
      WbSwingUtilities.showErrorMessageKey(this, "ErrConnectionGone");
      return;
    }

    if (dbConnection.getMetadata() == null)
    {
      LogMgr.logDebug(ci, "Database Metadata object not accessible", new Exception());
      WbSwingUtilities.showErrorMessageKey(this, "ErrConnectionMetaGone");
      return;
    }

    try
    {
      WbSwingUtilities.showWaitCursor(this);
      tableTypes.setEnabled(false);
      setFindPanelEnabled(false);
      reloadAction.setEnabled(false);
      summaryStatusBarLabel.setText(ResourceMgr.getString("MsgRetrieving"));
      NamedSortDefinition lastSort = tableList.getCurrentSort();

      reset();

      // do not call setBusy() before resetChangedFlags() because
      // resetChangedFlags will do nothing if the panel is busy
      setBusy(true);

      String[] types = getSelectedTypes();

      if (dbConnection.isShared() == false)
      {
        levelChanger.changeIsolationLevel(dbConnection);
      }
      ObjectListDataStore ds = null;
      if (DbExplorerSettings.getUseFilterForRetrieve())
      {
        String filter = findPanel.getText();
        filter = dbConnection.getMetadata().adjustObjectnameCase(filter);
        filter = dbConnection.getMetadata().removeQuotes(filter);
        ds = dbConnection.getMetadata().getObjects(currentCatalog, currentSchema, filter, types);
      }
      else
      {
        ds = dbConnection.getMetadata().getObjects(currentCatalog, currentSchema, types);
      }
      dbConnection.getObjectCache().addTableList(ds, currentSchema);
      tableList.setOriginalOrder(ds);
      final DataStoreTableModel model = new DataStoreTableModel(ds);
      model.setSortIgnoreCase(Settings.getInstance().sortTableListIgnoreCase());
      model.setUseNaturalSort(Settings.getInstance().useNaturalSortForTableList());

      // by applying the sort definition to the table model we ensure
      // that the sorting is retained when filtering the objects

      if (savedSort != null)
      {
        // sort definition stored in the workspace
        model.setSortDefinition(savedSort);
        savedSort = null;
      }
      else if (lastSort != null)
      {
        model.setSortDefinition(lastSort);
      }
      else
      {
        SortDefinition sort = SortDefinition.getTableListSort();
        model.setSortDefinition(sort);
      }

      // Make sure some columns are not modified by the user
      // to avoid the impression that e.g. a table's catalog can be changed
      // by editing this list
      model.setValidator(validator);

      final int remarksColumn = model.findColumn(ObjectListDataStore.RESULT_COL_REMARKS);

      WbSwingUtilities.invoke(() ->
      {
        tableList.setModel(model, true);
        tableList.getExportAction().setEnabled(true);
        if (remarksColumn > -1)
        {
          tableList.setMultiLine(remarksColumn);
        }
        tableList.adjustColumns();
        updateDisplayClients();
      });

      setDirty(false);
      retrieveFinished();
    }
    catch (OutOfMemoryError mem)
    {
      setBusy(false);
      reset();
      setDirty(true);
      WbManager.getInstance().showOutOfMemoryError();
    }
    catch (LowMemoryException mem)
    {
      setBusy(false);
      reset();
      setDirty(true);
      WbManager.getInstance().showLowMemoryError();
    }
    catch (NullPointerException npe)
    {
      // this can happen if the DbExplorer is closed while the retrieve is running
      LogMgr.logError(ci, "Error retrieving table list", npe);
    }
    catch (Throwable e)
    {
      if (e instanceof SQLException)
      {
        LogMgr.logError(ci, "Error retrieving table list", (SQLException)e);
      }
      else
      {
        LogMgr.logError(ci, "Error retrieving table list", e);
      }
      String msg = ExceptionUtil.getDisplay(e);
      invalidateData();
      setDirty(true);
      WbSwingUtilities.showErrorMessage(this, msg);
    }
    finally
    {
      WbSwingUtilities.showDefaultCursor(this);
      setBusy(false);
      tableTypes.setEnabled(true);
      levelChanger.restoreIsolationLevel(dbConnection);
      setFindPanelEnabled(true);
      reloadAction.setEnabled(true);
      endTransaction();
    }
  }

  private void setFindPanelEnabled(boolean flag)
  {
    findPanel.setEnabled(flag);
    if (flag && DbExplorerSettings.getUseFilterForRetrieve())
    {
      findPanel.setActionsEnabled(false);
    }
  }

  /**
   *  Starts the retrieval of the tables in a background thread
   */
  protected void startRetrieve(final boolean setFocus)
  {
    if (dbConnection == null)
    {
      LogMgr.logDebug(new CallerInfo(){}, "startRetrieve() called, but no connection available", new Exception());
      return;
    }

    Thread t = new WbThread("TableListPanel retrieve() thread")
    {
      @Override
      public void run()
      {
        retrieve();
        if (setFocus) setFocusToTableList();
      }
    };
    t.start();
  }

  public void panelSelected()
  {
    if (this.shouldRetrieve) startRetrieve(true);
  }

  @Override
  public void setVisible(boolean aFlag)
  {
    super.setVisible(aFlag);
    if (aFlag && this.shouldRetrieve)
      this.startRetrieve(false);
  }

  private String getWorkspacePrefix(int index)
  {
    return "dbexplorer" + index + ".tablelist.";
  }

  /**
   * Save settings to global settings file
   */
  public void saveSettings()
  {
    this.triggers.saveSettings();
    this.tableData.saveSettings();
    this.tableDefinition.saveSettings();
    String prefix = this.getClass().getName() + ".";
    storeSettings(Settings.getInstance(), prefix);
    findPanel.saveSettings(Settings.getInstance(), "workbench.quickfilter.");
  }

  /**
   *  Restore settings from global settings file.
   */
  public void restoreSettings()
  {
    String prefix = this.getClass().getName() + ".";
    readSettings(Settings.getInstance(), prefix);
    findPanel.restoreSettings(Settings.getInstance(), "workbench.quickfilter.");
    this.triggers.restoreSettings();
    this.tableData.restoreSettings();
    this.tableDefinition.restoreSettings();
    this.projections.restoreSettings();
  }


  /**
   * Save settings to a workspace
   *
   * @param w the Workspace into which the settings should be saved
   * @param index the index to be used in the Workspace
   */
  public void saveToWorkspace(WbWorkspace w, int index)
  {
    tableData.saveToWorkspace(w, index);
    projections.saveToWorkspace(w, index);
    WbProperties props = w.getSettings();
    String prefix = getWorkspacePrefix(index);
    storeSettings(props, prefix);
    this.findPanel.saveSettings(props, "workbench.quickfilter.");
  }

  /**
   *  Read settings from a workspace
   *
   * @param w the Workspace from which to read the settings
   * @param index the index inside the workspace
   */
  public void readFromWorkspace(WbWorkspace w, int index)
  {
    // first we read the global settings, then we'll let
    // the settings in the workspace override the global ones
    restoreSettings();
    tableData.readFromWorkspace(w, index);
    projections.readFromWorkspace(w, index);
    WbProperties props = w.getSettings();
    String prefix = getWorkspacePrefix(index);
    readSettings(props, prefix);
    findPanel.restoreSettings(props, "workbench.quickfilter.");
  }

  private void storeSettings(PropertyStorage props, String prefix)
  {
    try
    {
      String type;
      if (tableTypes != null && tableTypes.getModel().getSize() > 0)
      {
        type = StringUtil.arrayToString(getSelectedTypes());
      }
      else
      {
        // if tableTypes does not contain any items, this panel was never
        // displayed and we should use the value of the tableTypeToSelect
        // variable that was retrieved when the settings were read from
        // the workspace.
        type = tableTypeToSelect;
      }
      if (type != null) props.setProperty(prefix + "objecttype", type);

      props.setProperty(prefix + "divider", Integer.toString(this.splitPane.getDividerLocation()));
      props.setProperty(prefix + "exportedtreedivider", Integer.toString(exportedKeys.getDividerLocation()));
      props.setProperty(prefix + "importedtreedivider", Integer.toString(importedKeys.getDividerLocation()));
      props.setProperty(prefix + "exportedtree.retrieveall", Boolean.toString(exportedKeys.getRetrieveAll()));
      props.setProperty(prefix + "importedtree.retrieveall", Boolean.toString(importedKeys.getRetrieveAll()));

      if (Settings.getInstance().getBoolProperty(PROP_DO_SAVE_SORT, false))
      {
        NamedSortDefinition sortDef = tableList.getCurrentSort();
        if (sortDef == null)
        {
          sortDef = savedSort;
        }
        String sort = null;
        if (sortDef != null)
        {
          sort = sortDef.getDefinitionString();
        }
        props.setProperty(prefix + "tablelist.sort", sort);
      }

      List<String> objectListColumnOrder = tableList.saveColumnOrder();
      if (objectListColumnOrder != null)
      {
        props.setProperty(prefix + "columnorder", StringUtil.listToString(objectListColumnOrder, ','));
      }
      if (filterMgr != null)
      {
        filterMgr.saveSettings(props, prefix);
      }
    }
    catch (Throwable th)
    {
      LogMgr.logError(new CallerInfo(){}, "Error storing settings", th);
    }
  }

  private void readSettings(PropertyStorage props, String prefix)
  {
    Dimension d = Toolkit.getDefaultToolkit().getScreenSize();
    int maxWidth = (int)(d.getWidth() - 50);

    int loc = props.getIntProperty(prefix + "divider",-1);
    if (loc != -1)
    {
      if (loc == 0 || loc > maxWidth) loc = 200;
      this.splitPane.setDividerLocation(loc);
    }

    loc = props.getIntProperty(prefix + "exportedtreedivider",-1);
    if (loc != -1)
    {
      if (loc == 0 || loc > maxWidth) loc = 200;
      exportedKeys.setDividerLocation(loc);
    }

    loc = props.getIntProperty(prefix + "importedtreedivider",-1);
    if (loc != -1)
    {
      if (loc == 0 || loc > maxWidth) loc = 200;
      importedKeys.setDividerLocation(loc);
    }

    importedKeys.setRetrieveAll(props.getBoolProperty(prefix + "importedtree.retrieveall", true));
    exportedKeys.setRetrieveAll(props.getBoolProperty(prefix + "exportedtree.retrieveall", true));

    String defType = DbExplorerSettings.getDefaultExplorerObjectType();
    if (DbExplorerSettings.getStoreExplorerObjectType())
    {
      this.tableTypeToSelect = props.getProperty(prefix + "objecttype", defType);
    }
    else
    {
      this.tableTypeToSelect = defType;
    }
    String colString = props.getProperty(prefix + "columnorder", null);
    if (StringUtil.isNotEmpty(colString))
    {
      tableList.setNewColumnOrder(StringUtil.stringToList(colString, ","));
    }
    String sortString = props.getProperty(prefix + "tablelist.sort", null);
    if (StringUtil.isNotBlank(sortString))
    {
      savedSort = parseDefinitionString(sortString);
      if (savedSort != null)
      {
        savedSort.setUseNaturalSort(Settings.getInstance().useNaturalSortForTableList());
        savedSort.setIgnoreCase(Settings.getInstance().sortTableListIgnoreCase());
      }
    }
    if (filterMgr != null)
    {
      filterMgr.loadSettings(props, prefix);
    }
  }


  /**
   * Invoked when the selection in the table list has changed
   */
  @Override
  public void valueChanged(ListSelectionEvent e)
  {
    if (e.getValueIsAdjusting()) return;

    if (e.getSource() == this.tableList.getSelectionModel())
    {
      if (this.showDataMenu != null)
      {
        this.showDataMenu.setEnabled(this.tableList.getSelectedRowCount() == 1);
      }
      if (rowCountAction != null)
      {
        this.rowCountAction.setEnabled(this.tableList.getSelectedRowCount() > 0);
      }
      try
      {
        WbSwingUtilities.showWaitCursor(this);
        this.updateDisplay();
      }
      finally
      {
        WbSwingUtilities.showDefaultCursor(this);
      }
    }
  }

  @Override
  public boolean canChangeSelection()
  {
    if (this.isBusy()) return false;
    if (this.tableData == null) return true;

    if (GuiSettings.getConfirmDiscardResultSetChanges() && isModified())
    {
      if (!WbSwingUtilities.getProceedCancel(this, "MsgDiscardDataChanges"))
      {
        return false;
      }
      else
      {
        // for some reason the "valueIsAdjusting" flag is set to true if we wind up here
        // and I have no idea why.
        // But if the change of the selection is allowed, the valueIsAdjusting flag will
        // prevent updateDisplay() from applying the change.
        tableList.getSelectionModel().setValueIsAdjusting(false);
      }
    }
    return true;
  }

  private DbObject getSelectedUserObject()
  {
    int count = this.tableList.getSelectedRowCount();
    int row = this.tableList.getSelectedRow();
    if (count == 1 && row > -1)
    {
      return tableList.getUserObject(row, DbObject.class);
    }
    return null;
  }

  private void updateSelectedTable()
  {
    int count = this.tableList.getSelectedRowCount();
    int row = this.tableList.getSelectedRow();
    if (row < 0) return;

    if (count == 1 && row > -1)
    {
      this.selectedTable = createTableIdentifier(row);
    }
  }

  private boolean isScriptOnlyObject()
  {
    if (this.dbConnection == null) return false;
    DbSettings settings = dbConnection.getDbSettings();
    if (settings == null) return false;
    if (this.selectedTable == null) return false;

    String type = selectedTable.getType();
    return settings.getScriptOnlyObjects().contains(type);
  }

  public void updateDisplay()
  {
    int count = this.tableList.getSelectedRowCount();

    this.spoolData.setEnabled(count > 0);

    if (count > 1) return;

    int row = this.tableList.getSelectedRow();
    if (row < 0) return;

    updateSelectedTable();

    this.invalidateData();

    boolean isTable = isTable();
    boolean hasData = isTable || canContainData();
    boolean onlySource = isScriptOnlyObject();

    if (isTable)
    {
      showTablePanels();
    }
    else if (onlySource)
    {
      showOnlySourcePanel();
    }
    else
    {
      showObjectDefinitionPanels(hasData);
    }

    if (!onlySource)
    {
      tableData.reset();
      tableData.setTable(this.selectedTable);
      dependencyPanel.reset();
      dependencyPanel.setCurrentObject(selectedTable);
    }

    if (tableHistory != null)
    {
      TableHistoryModel model = (TableHistoryModel)tableHistory.getModel();
      try
      {
        ignoreStateChanged = true;
        model.addTable(this.selectedTable);
      }
      finally
      {
        ignoreStateChanged = false;
      }
    }

    this.setShowDataMenuStatus(hasData);

    this.startRetrieveCurrentPanel();
  }

  private void setShowDataMenuStatus(boolean flag)
  {
    if (this.showDataMenu != null) this.showDataMenu.setEnabled(flag);
  }

  private boolean isSynonym(TableIdentifier table)
  {
    if (table == null) return false;

    // dbConnection, metata or dbSettings can be null when the application is being closed
    DbMetadata meta = this.dbConnection != null ? this.dbConnection.getMetadata() : null;
    DbSettings dbs = this.dbConnection  != null ? this.dbConnection.getDbSettings() : null;
    if (meta == null || dbs == null) return false;

    return (meta.supportsSynonyms() && dbs.isSynonymType(table.getType()));
  }

  private boolean isTable()
  {
    if (this.selectedTable == null) return false;
    if (this.dbConnection == null) return false;

    // dbConnection, metata or dbSettings can be null when the application is being closed
    DbMetadata meta = this.dbConnection != null ? this.dbConnection.getMetadata() : null;
    DbSettings dbs = this.dbConnection  != null ? this.dbConnection.getDbSettings() : null;
    if (meta == null || dbs == null) return false;

    String type = selectedTable.getType();

    // isExtendedTableType() checks for regular tables and "extended tables"
    if (meta.isExtendedTableType(type)) return true;

    if (GuiSettings.showSynonymTargetInDbExplorer() && meta.supportsSynonyms() && dbs.isSynonymType(type))
    {
      TableIdentifier rt = getObjectTable();
      if (rt != null)
      {
        return meta.isTableType(rt.getType());
      }
    }
    LogMgr.logDebug(new CallerInfo(){}, "Object " + selectedTable.getTableExpression() + ", type=[" + selectedTable.getType() + "] is not considered a table");
    return false;
  }

  private boolean canContainData()
  {
    if (selectedTable == null) return false;
    String type = selectedTable.getType();

    // dbConnection, metata or dbSettings can be null when the application is being closed
    DbMetadata meta = this.dbConnection != null ? this.dbConnection.getMetadata() : null;
    DbSettings dbs = this.dbConnection  != null ? this.dbConnection.getDbSettings() : null;
    if (meta == null || dbs == null) return false;

    if (GuiSettings.showSynonymTargetInDbExplorer() && meta.supportsSynonyms() && dbs.isSynonymType(type))
    {
      TableIdentifier rt = getObjectTable();
      if (rt == null) return false;
      type = rt.getType();
    }

    boolean containsData = meta.objectTypeCanContainData(type) || meta.isExtendedTableType(type);
    if (!containsData)
    {
      LogMgr.logDebug(new CallerInfo(){}, "Object " + selectedTable.getTableExpression() + ", type=[" + selectedTable.getType() + "] is not considered to contain selectable data");
    }
    return containsData;
  }

  private String getDropForCurrentObject(DropType type)
  {
    DbObject dbo = getSelectedUserObject();

    GenericObjectDropper dropper = new GenericObjectDropper();
    dropper.setConnection(dbConnection);
    String drop = dropper.getDropForObject(dbo == null ? selectedTable : dbo, type == DropType.cascaded).toString();

    if (drop.length() > 0)
    {
      drop += "\n\n";
    }
    return drop;
  }

  protected void retrieveTableSource()
  {
    if (selectedTable == null) return;
    if (this.dbConnection == null) return;

    DbMetadata meta = this.dbConnection.getMetadata();
    DbSettings dbs = this.dbConnection.getDbSettings();

    // Can happen when closing the application
    if (meta == null) return;
    if (dbs == null) return;

    tableSource.setPlainText(ResourceMgr.getString("TxtRetrievingSourceCode"));
    tableSource.setEnabled(false);
    TableSourceBuilder builder = TableSourceBuilderFactory.getBuilder(this.dbConnection);

    try
    {
      setActivePanelIndex(tableSource);
      WbSwingUtilities.showWaitCursor(this);
      WbSwingUtilities.showWaitCursor(tableSource);

      String type = selectedTable.getType();

      CharSequence sql = null;

      DropType dropType = DbExplorerSettings.getDropTypeToGenerate(type);

      if (meta.isExtendedObject(selectedTable))
      {
        String drop = "";
        if (dropType != DropType.none)
        {
          drop = getDropForCurrentObject(dropType);
        }
        CharSequence source = null;

        DbObject dbo = getSelectedUserObject();
        if (dbo != null)
        {
          source = dbo.getSource(dbConnection);
        }

        if (StringUtil.isNotEmpty(source))
        {
          sql = drop + source;
        }
        else
        {
          sql = drop + meta.getObjectSource(selectedTable);
        }
      }
      else if (dbs.isViewType(type))
      {
        if (shouldRetrieveTable) retrieveTableDefinition();
        TableDefinition def = new TableDefinition(this.selectedTable, TableColumnsDatastore.createColumnIdentifiers(meta, tableDefinition.getDataStore()));
        sql = meta.getViewReader().getExtendedViewSource(def, dropType, false);
      }
      else if (dbs.isSynonymType(type))
      {
        SynonymDDLHandler synHandler = new SynonymDDLHandler();
        sql = synHandler.getSynonymSource(this.dbConnection, this.selectedTable, GuiSettings.showSynonymTargetInDbExplorer(), dropType);
      }
      else if (meta.isSequenceType(type))
      {
        SequenceReader sequenceReader = meta.getSequenceReader();
        String drop = "";
        if (dropType != DropType.none)
        {
          drop = getDropForCurrentObject(dropType);
        }
        GenerationOptions opt = new GenerationOptions(true, dbs.getGenerateTableGrants());
        CharSequence seqSql = sequenceReader.getSequenceSource(selectedTable.getCatalog(),
          this.selectedTable.getSchema(), this.selectedTable.getTableName(), opt);

        if (StringUtil.isEmpty(seqSql))
        {
          sql = ResourceMgr.getString("MsgSequenceSourceNotImplemented") + " " + meta.getProductName();
        }
        else
        {
          sql = seqSql.toString();
        }
        sql = drop + sql;
      }
      // isExtendedTableType() checks for regular tables and "extended tables"
      else if (meta.isExtendedTableType(type))
      {
        sql = builder.getTableSource(selectedTable, dropType, true, dbs.getGenerateTableGrants());
      }

      if (sql != null && dbConnection.generateCommitForDDL())
      {
        sql = sql.toString() + "\nCOMMIT;\n";
      }

      final String s = (sql == null ? "" : sql.toString());
      WbSwingUtilities.invokeLater(() ->
      {
        tableSource.setText(s, selectedTable.getTableName(), selectedTable.getObjectType());
        tableSource.setCaretPosition(0, false);
        if (DbExplorerSettings.getSelectSourcePanelAfterRetrieve())
        {
          tableSource.requestFocusInWindow();
        }
      });

      shouldRetrieveTableSource = false;
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error retrieving table source", e);
      final String msg = ExceptionUtil.getDisplay(e);
      EventQueue.invokeLater(() ->
      {
        tableSource.setPlainText(msg);
      });
    }
    finally
    {
      setActivePanelIndex(null);
      tableSource.setEnabled(true);
      WbSwingUtilities.showDefaultCursor(tableSource);
      WbSwingUtilities.showDefaultCursor(this);
    }

  }

  private void retrieveTableDefinition()
    throws SQLException
  {
    int currentIndex = currentRetrievalPanel;
    try
    {
      setActivePanelIndex(tableDefinition);
      if (selectedTable == null)
      {
        LogMgr.logDebug(new CallerInfo(){},"No current table available!", new Exception("TraceBack"));
        updateSelectedTable();
      }

      if (selectedTable == null)
      {
        LogMgr.logWarning(new CallerInfo(){},"No table selected!");
        return;
      }

      WbSwingUtilities.showWaitCursor(this);
      tableDefinition.retrieve(selectedTable);
      shouldRetrieveTable = false;
    }
    catch (SQLException e)
    {
      shouldRetrieveTable = true;
      throw e;
    }
    finally
    {
      currentRetrievalPanel = currentIndex;
      WbSwingUtilities.showDefaultCursor(this);
    }
  }

  protected Thread panelRetrieveThread;

  protected void startRetrieveCurrentPanel()
  {
    if (isBusy())
    {
      LogMgr.logWarning(new CallerInfo(){}, "Start retrieve called while connection was busy");
      return;
    }

    panelRetrieveThread = new WbThread("TableListPanel RetrievePanel")
    {
      @Override
      public void run()
      {
        try
        {
          retrieveCurrentPanel();
        }
        finally
        {
          panelRetrieveThread = null;
        }
      }
    };
    panelRetrieveThread.start();
  }

  private void setActivePanelIndex(JPanel panel)
  {
    if (panel == null)
    {
      currentRetrievalPanel = -1;
    }
    else
    {
      currentRetrievalPanel = displayTab.indexOfComponent(panel);
    }
  }

  protected void retrieveCurrentPanel()
  {
    if (this.dbConnection == null) return;

    if (this.isBusy() || this.dbConnection.isBusy())
    {
      this.invalidateData();
      this.resetCurrentPanel();
      return;
    }

    if (this.tableList.getSelectedRowCount() <= 0) return;
    int index = this.displayTab.getSelectedIndex();

    this.setBusy(true);

    if (!connectionLock.tryLock())
    {
      LogMgr.logWarning(new CallerInfo(){}, "Concurrent table list retrieval in process", new Exception("Backtrace"));
      this.invalidateData();
      return;
    }

    try
    {
      if (dbConnection.isShared() == false)
      {
        levelChanger.changeIsolationLevel(dbConnection);
      }

      retrieveSelectedPanel();
    }
    catch (Throwable ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Error retrieving panel " + index, ex);
    }
    finally
    {
      connectionLock.unlock();
      WbSwingUtilities.showDefaultCursor(this);
      this.setBusy(false);
      this.repaint();
      levelChanger.restoreIsolationLevel(dbConnection);
      endTransaction();
    }
  }

  private void retrieveSelectedPanel()
    throws SQLException
  {
    Component panel = displayTab.getSelectedComponent();
    if (panel == null) return;

    if (panel == this.tableDefinition && shouldRetrieveTable)
    {
      retrieveTableDefinition();
    }
    if (panel == tableSource && shouldRetrieveTableSource)
    {
      retrieveTableSource();
    }
    if (panel == tableData && shouldRetrieveTableData)
    {
      this.tableData.showData(!this.shiftDown);
      this.shouldRetrieveTableData = false;
    }
    if (panel == this.indexPanel && shouldRetrieveIndexes)
    {
      retrieveIndexes();
    }
    else if (panel == projections && shouldRetrieveProjections)
    {
      retrieveProjections();
    }
    else if (panel == importedKeys && shouldRetrieveImportedKeys)
    {
      retrieveImportedTables();
    }
    else if (panel == exportedKeys && shouldRetrieveExportedKeys)
    {
      retrieveExportedTables();
    }
    else if (panel == triggers && shouldRetrieveTriggers)
    {
      retrieveTriggers();
    }
    else if (panel == dependencyPanel && shouldRetrieveTriggers)
    {
      dependencyPanel.doLoad();
    }
    else if (panel == partitionsPanel && shouldRetrievePartitions)
    {
      partitionsPanel.doLoad();
    }
  }

  private void endTransaction()
  {
    ExplorerUtils.endTransaction(dbConnection);
  }

  private final Object busyLock = new Object();

  public boolean isBusy()
  {
    // this can happen if the DbExplorer is closed while the retrieve is running
    if (this.dbConnection == null) return false;

    synchronized (busyLock)
    {
      if (busy) return true;
      if (dbConnection != null && dbConnection.isBusy()) return true;
      return false;
    }
  }

  protected void setBusy(boolean aFlag)
  {
    synchronized (busyLock)
    {
      this.busy = aFlag;
      if (dbConnection != null)
      {
        this.dbConnection.setBusy(aFlag);
      }
    }
  }

  @Override
  public TableIdentifier getObjectTable()
  {
    if (this.selectedTable == null) return null;
    if (this.getSelectionCount() != 1) return null;
    if (!isSynonym(selectedTable)) return selectedTable;

    if (selectedTable.getRealTable() == null)
    {
      TableIdentifier realTable = dbConnection.getMetadata().resolveSynonym(selectedTable);
      selectedTable.setRealTable(realTable);
    }
    return selectedTable.getRealTable();
  }

  protected void retrieveTriggers()
  {
    WbThread th = new WbThread("Trigger Retrieval")
    {
      @Override
      public void run()
      {
        _retrieveTriggers();
      }
    };
    WbSwingUtilities.showWaitCursor(this);
    th.start();
  }

  private void _retrieveTriggers()
  {
    try
    {
      setActivePanelIndex(triggers);
      WbSwingUtilities.showWaitCursor(this);
      triggers.readTriggers(getObjectTable());
      this.shouldRetrieveTriggers = false;
    }
    catch (Throwable th)
    {
      this.shouldRetrieveTriggers = true;
      LogMgr.logError(new CallerInfo(){}, "Error retrieving triggers", th);
      WbSwingUtilities.showErrorMessage(this, ExceptionUtil.getDisplay(th));
    }
    finally
    {
      setActivePanelIndex(null);
      WbSwingUtilities.showDefaultCursor(this);
    }
  }

  protected void retrieveIndexes()
    throws SQLException
  {
    TableIdentifier tbl = getObjectTable();
    if (tbl == null) return;
    try
    {
      setActivePanelIndex(indexPanel);
      WbSwingUtilities.showWaitCursor(this);
      DbMetadata meta = this.dbConnection.getMetadata();
      DataStore ds = meta.getIndexReader().getTableIndexInformation(tbl);
      final DataStoreTableModel model = new DataStoreTableModel(ds);
      WbSwingUtilities.invoke(() ->
      {
        indexes.setModel(model, true);
        indexes.adjustRowsAndColumns();
      });
      this.shouldRetrieveIndexes = false;
    }
    catch (Throwable th)
    {
      this.shouldRetrieveIndexes = true;
      LogMgr.logError(new CallerInfo(){}, "Error retrieving indexes", th);
      WbSwingUtilities.showErrorMessage(this, ExceptionUtil.getDisplay(th));
    }
    finally
    {
      setActivePanelIndex(null);
      WbSwingUtilities.showDefaultCursor(this);
    }
  }

  protected void retrieveExportedTables()
    throws SQLException
  {
    try
    {
      setActivePanelIndex(exportedKeys);
      WbSwingUtilities.showWaitCursor(this);
      exportedKeys.retrieve(getObjectTable());
      this.shouldRetrieveExportedKeys = false;
    }
    catch (Throwable th)
    {
      this.shouldRetrieveExportedKeys = true;
      LogMgr.logError(new CallerInfo(){}, "Error retrieving table references", th);
      WbSwingUtilities.showErrorMessage(this, ExceptionUtil.getDisplay(th));
    }
    finally
    {
      WbSwingUtilities.showDefaultCursor(this);
      setActivePanelIndex(null);
    }
  }

  protected void retrieveImportedTables()
    throws SQLException
  {
    try
    {
      setActivePanelIndex(importedKeys);
      WbSwingUtilities.showWaitCursor(this);
      importedKeys.retrieve(getObjectTable());
      this.shouldRetrieveImportedKeys = false;
    }
    catch (Throwable th)
    {
      this.shouldRetrieveImportedKeys = true;
      LogMgr.logError(new CallerInfo(){}, "Error retrieving table references", th);
      WbSwingUtilities.showErrorMessage(this, ExceptionUtil.getDisplay(th));
    }
    finally
    {
      setActivePanelIndex(null);
      WbSwingUtilities.showDefaultCursor(this);
    }
  }

  protected void retrieveProjections()
    throws SQLException
  {
    try
    {
      setActivePanelIndex(projections);
      WbSwingUtilities.showWaitCursor(this);
      projections.retrieve(getObjectTable());
      this.shouldRetrieveProjections = false;
    }
    catch (Throwable th)
    {
      this.shouldRetrieveProjections = true;
      LogMgr.logError(new CallerInfo(){}, "Error retrieving projections", th);
      WbSwingUtilities.showErrorMessage(this, ExceptionUtil.getDisplay(th));
    }
    finally
    {
      setActivePanelIndex(null);
      WbSwingUtilities.showDefaultCursor(this);
    }
  }

  @Override
  public void reload()
  {
    if (!WbSwingUtilities.isConnectionIdle(this, dbConnection)) return;
    this.startRetrieve(false);
  }

  @Override
  public int getSelectionCount()
  {
    return tableList.getSelectedRowCount();
  }

  @Override
  public TableDefinition getCurrentTableDefinition()
  {
    if (selectedTable == null) return null;

    if (this.shouldRetrieveTable || this.tableDefinition.getRowCount() == 0)
    {
      try
      {
        this.retrieveTableDefinition();
      }
      catch (Exception e)
      {
        LogMgr.logError(new CallerInfo(){}, "Error retrieving table definition", e);
        String msg = ExceptionUtil.getDisplay(e);
        WbSwingUtilities.showErrorMessage(this, msg);
        return null;
      }
    }
    List<ColumnIdentifier> columns = tableDefinition.getColumns();
    return new TableDefinition(selectedTable, columns);
  }

  /**
   * Invoked when the type dropdown changes or one of the additional actions
   * is invoked that are put into the context menu of the table list
   *
   * @param e the Event that ocurred
   */
  @Override
  public void actionPerformed(ActionEvent e)
  {
    if (ignoreStateChanged) return;

    if (e.getSource() == this.tableTypes && !isBusy())
    {
      try
      {
        this.showObjectDefinitionPanels(false);
        this.startRetrieve(true);
      }
      catch (Exception ex)
      {
        LogMgr.logError(new CallerInfo(){}, "Error while retrieving", ex);
      }
    }
    else if (e.getSource() == this.tableHistory)
    {
      final TableIdentifier tbl = (TableIdentifier)this.tableHistory.getSelectedItem();
      if (tbl != null)
      {
        selectTable(tbl);
      }
    }
  }

  private boolean isClientVisible()
  {
    if (this.tableListClients == null) return false;
    for (JTable table : tableListClients)
    {
      if (table.isVisible()) return true;
    }
    return false;
  }

  protected void updateDisplayClients()
  {
    if (this.tableListClients == null) return;

    TableModel model = this.tableList.getModel();
    for (JTable table : tableListClients)
    {
      if (table != null && model != null)
      {
        table.setModel(model);
        if (table instanceof WbTable)
        {
          WbTable t = (WbTable)table;
          t.adjustRowsAndColumns();
        }
        table.repaint();
      }
    }
  }

  @Override
  public void objectsDropped(List<DbObject> objects)
  {
    if (CollectionUtil.isEmpty(objects)) return;

    tableList.resetFilter();
    DataStore ds = tableList.getDataStore();
    int count = ds.getRowCount();

    for (int row=count - 1; row >= 0; row--)
    {
      int viewRow = tableList.convertRowIndexToView(row);
      DbObject uo = getDbObject(viewRow);
      if (uo != null && objects.contains(uo))
      {
        ds.deleteRow(row);
      }
    }
    tableList.getDataStoreTableModel().fireTableDataChanged();
  }

  @Override
  public void addTableListDisplayClient(JTable aClient)
  {
    if (this.tableListClients == null) this.tableListClients = new ArrayList<>();
    if (!this.tableListClients.contains(aClient)) this.tableListClients.add(aClient);
    if (tableList != null && tableList.getRowCount() > 0)
    {
      updateDisplayClients();
    }
  }

  @Override
  public void removeTableListDisplayClient(JTable aClient)
  {
    if (this.tableListClients == null) return;
    this.tableListClients.remove(aClient);
  }

  /**
   * Return a TableIdentifier for the given row number in the table list.
   *
   * @param row the row from the tableList Table
   * @return a TableIdentifier for that row
   */
  private TableIdentifier createTableIdentifier(int row)
  {
    ObjectListDataStore ds = (ObjectListDataStore)this.tableList.getDataStore();
    return ds.getTableIdentifier(row);
  }

  private void retrieveFinished()
  {
    if (objectToSelect != null)
    {
      DbObject dbo = objectToSelect;
      objectToSelect = null;
      findAndSelect(dbo);
    }
  }

  @Override
  public void selectObject(DbObject object)
  {
    if (shouldRetrieve)
    {
      this.objectToSelect = object;
      this.startRetrieve(false);
      return;
    }
    findAndSelect(object);
  }

  private void findAndSelect(DbObject object)
  {
    if (object == null) return;
    for (int row=0; row < this.tableList.getRowCount(); row++)
    {
      TableIdentifier tbl = createTableIdentifier(row);
      if (DbObjectComparator.namesAreEqual(object, tbl, false))
      {
        final int toSelect = row;
        WbSwingUtilities.invokeLater(() ->
        {
          WbSwingUtilities.selectComponentTab(this);
          tableList.selectRow(toSelect);
        });
        return;
      }
    }
    String name = object.getFullyQualifiedName(dbConnection);
    WbSwingUtilities.showMessage(this, ResourceMgr.getFormattedString("ErrTableOrViewNotFound", name));
  }

  @Override
  public WbConnection getConnection()
  {
    return this.dbConnection;
  }

  @Override
  public Component getComponent()
  {
    return this;
  }

  private DbObject getDbObject(int row)
  {
    DbObject dbo = tableList.getUserObject(row, DbObject.class);
    if (dbo != null)
    {
      return dbo;
    }
    else
    {
      TableIdentifier table = createTableIdentifier(row);
      table.checkQuotesNeeded(dbConnection);
      return table;
    }
  }

  @Override
  public void rowCountStarting()
  {
    if (tableList.getColumnIndex(ROW_COUNT_COLUMN.getColumnName()) == -1)
    {
      int[] selection = tableList.getSelectedRows();
      WbSwingUtilities.invoke(() ->
      {
        tableList.addColumn(ROW_COUNT_COLUMN, 1);
        tableList.restoreSelection(selection);
      });
    }
  }

  @Override
  public void showRowCount(TableIdentifier table, long rowCount)
  {
    DataStoreTableModel data = tableList.getDataStoreTableModel();
    if (data == null) return;
    int row = findTable(table);
    if (row < 0) return;

    String name = ROW_COUNT_COLUMN.getColumnName();
    int column = data.findColumn(name);
    data.setValue(rowCount, row, column);
  }

  @Override
  public void rowCountDone(int tableCount)
  {
  }

  @Override
  public List<TableIdentifier> getSelectedTables()
  {
    List<DbObject> objects = getSelectedObjects();
    if (objects == null)
    {
      return Collections.emptyList();
    }
    return objects.stream().filter(dbo -> dbo instanceof TableIdentifier)
                           .map(TableIdentifier.class::cast)
                           .collect(Collectors.toList());
  }

  @Override
  public List<DbObject> getSelectedObjects()
  {
    int[] rows = this.tableList.getSelectedRows();
    int count = rows.length;
    if (count == 0) return null;

    List<DbObject> result = new ArrayList<>(count);
    for (int i=0; i < count; i++)
    {
      DbObject dbo = getDbObject(rows[i]);
      result.add(dbo);
    }
    return result;
  }

  @Override
  public void selectTable(TableIdentifier table)
  {
    // this can happen during "closing" of the DbExplorer
    if (table == null) return;
    if (tableList == null) return;

    // no need to apply the same table again.
    if (selectedTable != null && selectedTable.equals(table)) return;

    int row = findTable(table);

    if (row < 0 && tableList.getDataStore().isFiltered())
    {
      findPanel.resetFilter();
      row = findTable(table);
    }

    if (row > -1)
    {
      try
      {
        // if the tab is changed, this will trigger a reload of the current table definition
        // but as we are going to select a different one right away we don't need this.
        this.ignoreStateChanged = true;
        displayTab.setSelectedIndex(0);
      }
      finally
      {
        this.ignoreStateChanged = false;
      }
      tableList.scrollToRow(row);
      tableList.setRowSelectionInterval(row, row);
    }
  }

  private int findTable(TableIdentifier table)
  {
    for (int row = 0; row < this.tableList.getRowCount(); row++)
    {
      TableIdentifier tbl = createTableIdentifier(row);
      if (tbl.compareNames(table))
      {
        return row;
      }
    }
    return -1;
  }

  /**
   * Invoked when the displayed tab has changed (e.g. from source to data).
   */
  @Override
  public void stateChanged(ChangeEvent e)
  {
    if (this.ignoreStateChanged) return;

    if (e.getSource() == this.displayTab)
    {
      if (isBusy() && displayTab.getSelectedIndex() != this.currentRetrievalPanel)
      {
        WbSwingUtilities.showMessageKey(SwingUtilities.getWindowAncestor(this), "ErrConnectionBusy");
        return;
      }

      EventQueue.invokeLater(this::startRetrieveCurrentPanel);
    }
  }

  /**
   * If an index is created in the TableDefinitionPanel it
   * sends a PropertyChange event. This will invalidate
   * the currently retrieved index list
   */
  @Override
  public void propertyChange(PropertyChangeEvent evt)
  {
    Set<String> filterProps = CollectionUtil.caseInsensitiveSet(DbExplorerSettings.PROP_INSTANT_FILTER,
      DbExplorerSettings.PROP_USE_FILTER_RETRIEVE, DbExplorerSettings.PROP_ASSUME_WILDCARDS);

    if (evt.getSource() == renameAction)
    {
      checkAlterButton();
    }
    else if (DbExplorerSettings.PROP_TABLE_HISTORY.equals(evt.getPropertyName()))
    {
      WbSwingUtilities.invokeLater(() ->
      {
        if (DbExplorerSettings.getDbExplorerShowTableHistory())
        {
          showTableHistory();
        }
        else
        {
          hideTableHistory();
        }
      });
    }
    else if (DbExplorerSettings.PROP_ALLOW_SOURCE_EDITING.equals(evt.getPropertyName()))
    {
      tableSource.allowEditing(DbExplorerSettings.allowSourceEditing());
    }
    else if (TableDefinitionPanel.INDEX_PROP.equals(evt.getPropertyName()))
    {
      this.shouldRetrieveIndexes = true;
    }
    else if (TableDefinitionPanel.DEFINITION_PROP.equals(evt.getPropertyName()))
    {
      invalidateData();
      this.shouldRetrieveTable = false;
    }
    else if (filterProps.contains(evt.getPropertyName()))
    {
      configureFindPanel();
    }
    else if (PlacementChooser.DBEXPLORER_LOCATION_PROPERTY.equals(evt.getPropertyName()))
    {
      EventQueue.invokeLater(() ->
      {
        int location = PlacementChooser.getDBExplorerTabLocation();
        displayTab.setTabPlacement(location);
        displayTab.validate();
      });
    }
  }

  private void configureFindPanel()
  {
    findPanel.setFilterOnType(getApplyFilterWhileTyping());
    findPanel.setAlwaysUseContainsFilter(DbExplorerSettings.getUsePartialMatch());
    if (DbExplorerSettings.getUseFilterForRetrieve())
    {
      findPanel.setActionsEnabled(false);
      findPanel.setToolTipText(ResourceMgr.getString("TxtQuickFilterLikeHint"));
      findPanel.setReloadAction(reloadAction);
    }
    else
    {
      findPanel.setReloadAction(null);
      findPanel.setActionsEnabled(true);
      findPanel.setFilterTooltip();
    }
  }

  @Override
  public void mouseClicked(MouseEvent e)
  {
    this.shiftDown = ((e.getModifiersEx() & ActionEvent.SHIFT_MASK) == ActionEvent.SHIFT_MASK);
  }

  @Override
  public void mouseEntered(MouseEvent e)
  {
  }

  @Override
  public void mouseExited(MouseEvent e)
  {
  }

  @Override
  public void mousePressed(MouseEvent e)
  {
  }

  @Override
  public void mouseReleased(MouseEvent e)
  {
  }

}
