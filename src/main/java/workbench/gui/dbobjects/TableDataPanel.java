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
import java.awt.Cursor;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import workbench.WbManager;
import workbench.interfaces.DbExecutionListener;
import workbench.interfaces.DbExecutionNotifier;
import workbench.interfaces.Interruptable;
import workbench.interfaces.JobErrorHandler;
import workbench.interfaces.PropertyStorage;
import workbench.interfaces.Reloadable;
import workbench.interfaces.Resettable;
import workbench.interfaces.ResultReceiver;
import workbench.interfaces.TableDeleteListener;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.DbExplorerSettings;
import workbench.resource.GuiSettings;
import workbench.resource.IconMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;
import workbench.workspace.WbWorkspace;

import workbench.db.ColumnIdentifier;
import workbench.db.DbSettings;
import workbench.db.JdbcUtils;
import workbench.db.TableDefinition;
import workbench.db.TableIdentifier;
import workbench.db.TableSelectBuilder;
import workbench.db.WbConnection;

import workbench.gui.MainWindow;
import workbench.gui.PanelReloader;
import workbench.gui.WbSwingUtilities;
import workbench.gui.actions.FilterPickerAction;
import workbench.gui.actions.ReloadAction;
import workbench.gui.actions.SelectionFilterAction;
import workbench.gui.actions.StopAction;
import workbench.gui.actions.WbAction;
import workbench.gui.components.ColumnOrderMgr;
import workbench.gui.components.DividerBorder;
import workbench.gui.components.TabCloser;
import workbench.gui.components.WbButton;
import workbench.gui.components.WbLabelField;
import workbench.gui.components.WbSplitPane;
import workbench.gui.components.WbTabbedPane;
import workbench.gui.components.WbTable;
import workbench.gui.components.WbToolbar;
import workbench.gui.components.WbTraversalPolicy;
import workbench.gui.sql.AutomaticRefreshMgr;
import workbench.gui.sql.DwPanel;

import workbench.storage.DataStore;
import workbench.storage.NamedSortDefinition;
import workbench.storage.ResultColumnMetaData;

import workbench.sql.EndReadOnlyTrans;

import workbench.util.CollectionUtil;
import workbench.util.ExceptionUtil;
import workbench.util.FilteredProperties;
import workbench.util.LowMemoryException;
import workbench.util.StringUtil;
import workbench.util.WbThread;

/**
 *
 * @author  Thomas Kellerer
 */
public class TableDataPanel
  extends JPanel
  implements ActionListener, Reloadable, Interruptable,
             TableDeleteListener, Resettable, DbExecutionNotifier, PanelReloader,
             ResultReceiver, TabCloser
{
  private WbConnection dbConnection;
  protected DwPanel dataDisplay;

  private ReloadAction reloadAction;

  private JButton config;
  private WbLabelField tableNameLabel;
  private JLabel rowCountLabel;
  private JButton rowCountButton;
  private JCheckBox autoRetrieve;
  private JPanel topPanel;

  private int warningThreshold = -1;
  private boolean retrieveRunning;
  private boolean updateRunning;
  private boolean autoloadRowCount = true;
  private TableIdentifier table;
  private TableDefinition tableDefinition;
  protected StopAction cancelRetrieve;
  private List<DbExecutionListener> execListener;
  private Savepoint currentSavepoint;
  private Statement rowCountRetrieveStmt;
  private NamedSortDefinition lastSort;
  private WbToolbar toolbar;

  private boolean initialized;
  private boolean useDataStoreSource;
  private boolean showRefreshButton;
  private WbButton enableRefreshButton;
  private FilteredProperties workspaceSettings;

  private final AutomaticRefreshMgr refreshMgr = new AutomaticRefreshMgr();
  private List<JButton> additionalButtons;
  private WbTabbedPane tabPane;
  private WbSplitPane splitPane;
  private DwPanel currentRetrievalPanel;

  public TableDataPanel()
  {
    super();
    setName("tabledata");
  }

  private void initGui()
  {
    if (initialized) return;
    WbSwingUtilities.invoke(this::_initGui);
  }

  private void _initGui()
  {
    if (initialized) return;

    this.setBorder(WbSwingUtilities.EMPTY_BORDER);
    this.setLayout(new BorderLayout());

    this.dataDisplay = new DwPanel()
    {
      @Override
      public synchronized int saveChanges(WbConnection aConnection, JobErrorHandler errorHandler)
        throws SQLException
      {
        int result = -1;
        try
        {
          dbUpdateStart();
          result = super.saveChanges(aConnection, errorHandler);
        }
        finally
        {
          dbUpdateEnd();
        }
        return result;
      }
    };
    dataDisplay.showCreateDeleteScript();
    dataDisplay.setBorder(WbSwingUtilities.EMPTY_BORDER);
    dataDisplay.setShowLoadProcess(true);
    dataDisplay.setDefaultStatusMessage("");

    createToolbar();

    topPanel = new JPanel();
    topPanel.setLayout(new GridBagLayout());

    GridBagConstraints gc = new GridBagConstraints();
    gc.gridx = 0;
    gc.gridy = 0;
    gc.anchor = GridBagConstraints.LINE_START;

    topPanel.add(toolbar, gc);

    int buttonWidth = IconMgr.getInstance().getToolbarIconSize();

    gc.gridx ++;
    gc.insets = new Insets(0, buttonWidth, 0, 0);
    JLabel l = new JLabel(ResourceMgr.getString("LblTable") + ":");
    topPanel.add(l, gc);

    Font std = l.getFont();
    Font bold = std.deriveFont(Font.BOLD);
    tableNameLabel = new WbLabelField();
    tableNameLabel.setFont(bold);

    gc.gridx ++;
    gc.insets = new Insets(0, 1, 0, 0);
    topPanel.add(tableNameLabel, gc);

    gc.gridx ++;
    gc.insets = new Insets(0, (int)(buttonWidth/2), 0, 0);
    topPanel.add(new JLabel(ResourceMgr.getString("LblTableDataRowCount")), gc);
    rowCountLabel = new JLabel();
    rowCountLabel.setFont(bold);
    rowCountLabel.setHorizontalTextPosition(SwingConstants.LEFT);
    gc.gridx ++;
    gc.insets = new Insets(0, (int)(buttonWidth/4), 0, 0);
    topPanel.add(rowCountLabel, gc);

    rowCountButton = new WbButton(IconMgr.getInstance().getToolbarIcon("refresh"));
    rowCountButton.setMargin(new Insets(1,1,1,1));
    rowCountButton.setRolloverEnabled(true);
    rowCountButton.setBackground(topPanel.getBackground());
    rowCountButton.addActionListener(this);
    rowCountButton.setToolTipText(ResourceMgr.getDescription("LblTableDataRowCountButton"));
    rowCountButton.setFocusable(false);
    gc.gridx ++;
    gc.insets = new Insets(0, (int)(buttonWidth/4), 0, 0);
    topPanel.add(rowCountButton, gc);

    autoRetrieve = new JCheckBox(ResourceMgr.getString("LblAutoLoad"));
    autoRetrieve.setToolTipText(ResourceMgr.getDescription("LblAutoLoadTableData"));
    autoRetrieve.setHorizontalTextPosition(SwingConstants.LEFT);
    gc.gridx ++;
    gc.weightx = 1.0;
    gc.insets = new Insets(0, buttonWidth, 0, 0);
    topPanel.add(autoRetrieve, gc);

    config = new JButton(ResourceMgr.getString("LblConfigureWarningThreshold"));
    config.setToolTipText(ResourceMgr.getDescription("LblConfigureWarningThreshold"));
    config.addActionListener(this);
    gc.gridx ++;
    gc.weightx = 0.0;
    gc.anchor = GridBagConstraints.LINE_END;
    topPanel.add(this.config, gc);
    topPanel.setBorder(DividerBorder.BOTTOM_DIVIDER);

    this.add(topPanel, BorderLayout.NORTH);
    this.add(dataDisplay, BorderLayout.CENTER);

    WbTraversalPolicy policy = new WbTraversalPolicy();
    policy.addComponent(dataDisplay);
    policy.setDefaultComponent(dataDisplay);
    setFocusCycleRoot(false);
    setFocusTraversalPolicy(policy);
    dataDisplay.getTable().setColumnOrderSavingEnabled(true);

    if (DbExplorerSettings.showFocusInDbExplorer())
    {
      dataDisplay.getTable().showFocusBorder();
    }

    restoreSettings();

    if (workspaceSettings != null)
    {
      readSettings(workspaceSettings.getFilterPrefix(), workspaceSettings);
      workspaceSettings = null;
    }

    dataDisplay.setConnection(dbConnection);

    initialized = true;
  }

  public void addButtons(JButton... buttons)
  {
    if (buttons == null)
    {
      this.additionalButtons = null;
    }
    else
    {
      this.additionalButtons = new ArrayList<>(buttons.length);
      for (JButton button : buttons)
      {
        if (button != null)
        {
          this.additionalButtons.add(button);
        }
      }
    }
  }

  public void showRefreshButton(boolean show)
  {
    showRefreshButton = show;
  }

  public void refreshAutomatically(int interval)
  {
    if (interval <= 0) return;
    refreshMgr.addRefresh(this, dataDisplay, interval);
    checkRefreshButton();
  }

  private void createToolbar()
  {
    toolbar = new WbToolbar();
    reloadAction = new ReloadAction(this);
    reloadAction.setTooltip(ResourceMgr.getDescription("TxtLoadTableData", true));
    addActionToToolbar(reloadAction);
    toolbar.addSeparator();
    cancelRetrieve = new StopAction(this);
    cancelRetrieve.setEnabled(false);
    addActionToToolbar(this.cancelRetrieve);
    toolbar.addSeparator();
    addActionToToolbar(this.dataDisplay.getUpdateDatabaseAction());
    addActionToToolbar(this.dataDisplay.getSelectKeysAction());
    toolbar.addSeparator();
    addActionToToolbar(this.dataDisplay.getInsertRowAction());
    addActionToToolbar(this.dataDisplay.getCopyRowAction());
    addActionToToolbar(this.dataDisplay.getDeleteRowAction());
    toolbar.addSeparator();
    SelectionFilterAction a = new SelectionFilterAction();
    a.setClient(this.dataDisplay.getTable());
    addActionToToolbar(a);
    toolbar.addSeparator();
    addActionToToolbar(this.dataDisplay.getTable().getFilterAction());
    FilterPickerAction p = new FilterPickerAction(dataDisplay.getTable());
    addActionToToolbar(p);
    toolbar.addSeparator();
    addActionToToolbar(this.dataDisplay.getTable().getResetFilterAction());
  }

  private void addActionToToolbar(WbAction action)
  {
    toolbar.add(action);
    action.addToInputMap(this.dataDisplay.getTable());
  }

  @Override
  public void addNotify()
  {
    super.addNotify();
    initTableNavigation();
  }

  public DwPanel getPanel()
  {
    return dataDisplay;
  }

  /**
   * Return the displayed table data.
   * Intended for testing purposes
   */
  public WbTable getData()
  {
    return this.dataDisplay.getTable();
  }

  public boolean isModified()
  {
    if (this.dataDisplay == null) return false;
    return this.dataDisplay.isModified();
  }

  private void initTableNavigation()
  {
    if (this.dataDisplay == null) return;
    try
    {
      MainWindow w = (MainWindow)SwingUtilities.getWindowAncestor(this);
      this.dataDisplay.initTableNavigation(w, this);
    }
    catch (Exception e)
    {
      // ignore, will only happen if the DbExplorer was started
      // as a standalone application
    }
  }

  private ImageIcon getLoadingIndicator()
  {
    return IconMgr.getInstance().getLabelIcon("wait");
  }

  public void dispose()
  {
    if (!initialized) return;

    reset();
    if (dataDisplay != null) dataDisplay.dispose();
    WbAction.dispose(reloadAction, cancelRetrieve);
    WbSwingUtilities.removeAllListeners(this);
    CollectionUtil.clear(execListener);
  }

  public void detachConnection()
  {
    this.dbConnection = null;
    if (this.dataDisplay != null)
    {
      this.dataDisplay.detachConnection();
      this.dataDisplay.disableUpdateActions();
    }
    this.reloadAction.setEnabled(false);
    this.cancelRetrieve.setEnabled(false);
    CollectionUtil.clear(execListener);
  }

  public void disconnect()
  {
    this.dbConnection = null;
    this.reset();
  }

  private void storeSort()
  {
    if (DbExplorerSettings.getRememberSortInDbExplorer())
    {
      this.lastSort = dataDisplay.getCurrentSort();
    }
    else
    {
      this.lastSort = null;
    }
  }

  @Override
  public void reset()
  {
    if (!initialized) return;

    if (this.isRetrieving()) return;

    // storeSort() must be called before calling
    // clearContent() as the sort definition is maintained
    // in the TableModel and that is deleted when the data is cleared
    storeSort();

    storeColumnOrder();

    refreshMgr.clear();

    WbSwingUtilities.invoke(() ->
    {
      removeAdditionalData();
      if (dataDisplay != null) dataDisplay.clearContent();
      if (rowCountLabel != null) rowCountLabel.setText(ResourceMgr.getString("LblNotAvailable"));
      clearLoadingImage();
      reloadAction.setEnabled(true);
    });
  }

  public void setConnection(WbConnection aConnection)
  {
    this.dbConnection = aConnection;

    if (this.initialized)
    {
      try
      {
        this.dataDisplay.setConnection(aConnection);
      }
      catch (Throwable th)
      {
        LogMgr.logError(new CallerInfo(){}, "Error when setting connection", th);
      }
    }
  }

  private boolean rowCountCancel = false;

  private void startRetrieveRowCount()
  {
    if (this.dbConnection == null) return;

    Thread t = null;
    if (rowCountRetrieveStmt != null)
    {
      t = new WbThread("RowCount cancel")
      {
        @Override
        public void run()
        {
          cancelRowCountRetrieve();
        }
      };
    }
    else
    {
      t = new WbThread("RowCount Retrieve")
      {
        @Override
        public void run()
        {
          showRowCount();
        }
      };
    }
    t.start();
  }

  private void setSavepoint()
  {
    if (dbConnection == null) return;

    DbSettings dbs = dbConnection.getDbSettings();

    if (dbs == null) return;

    if (!ExplorerUtils.isOwnTransaction(dbConnection)
        && dbs.useSavePointForDML()
        && dbs.getAutoCloseReadOnlyTransactions() == EndReadOnlyTrans.never)
    {
      try
      {
        this.currentSavepoint = this.dbConnection.setSavepoint();
      }
      catch (SQLException e)
      {
        this.currentSavepoint = null;
      }
      catch (Exception e)
      {
        LogMgr.logDebug(new CallerInfo(){}, "Error setting savepoint", e);
      }
    }
  }

  public long showRowCount()
  {
    if (rowCountLabel == null) return -1;
    if (this.dbConnection == null) return -1;
    if (this.isRetrieving()) return -1;
    initGui();

    WbSwingUtilities.invoke(() ->
    {
      rowCountLabel.setText("");
      rowCountLabel.setIcon(getLoadingIndicator());
      reloadAction.setEnabled(false);
      dataDisplay.setStatusMessage(ResourceMgr.getFormattedString("MsgCalculatingTableRows", table.getTableName()));
    });

    String sql = this.buildSqlForRowCount();
    if (sql == null) return -1;

    long rowCount = 0;
    ResultSet rs = null;

    boolean error = false;

    try
    {
      setSavepoint();
      retrieveStart();
      rowCountButton.setToolTipText(ResourceMgr.getDescription("LblTableDataRowCountCancel"));
      rowCountRetrieveStmt = this.dbConnection.createStatementForQuery();

      LogMgr.logDebug(new CallerInfo(){}, "Retrieving row count using:\n" + sql);

      rs = rowCountRetrieveStmt.executeQuery(sql);
      if (rs.next())
      {
        rowCount = rs.getLong(1);
      }
      this.rowCountLabel.setText(Long.toString(rowCount));
      this.rowCountLabel.setToolTipText(null);
    }
    catch (final Exception e)
    {
      WbSwingUtilities.showDefaultCursor(this);
      rowCount = -1;
      error = true;
      final String msg = ExceptionUtil.getDisplay(e);
      LogMgr.logError(new CallerInfo(){}, "Error retrieving rowcount for " + this.table.getTableExpression(dbConnection) + " using\n " + sql, e);
      if (rowCountCancel)
      {
        WbSwingUtilities.setLabel(rowCountLabel, ResourceMgr.getString("LblNotAvailable"), null);
      }
      else
      {
        WbSwingUtilities.setLabel(rowCountLabel, ResourceMgr.getString("TxtError"), msg);
      }
      String title = ResourceMgr.getString("TxtErrorRowCount");
      WbSwingUtilities.showFriendlyErrorMessage(SwingUtilities.getWindowAncestor(this), title, msg);
    }
    finally
    {
      JdbcUtils.closeAll(rs, rowCountRetrieveStmt);
      this.rowCountCancel = false;

      WbSwingUtilities.invoke(() ->
      {
        dataDisplay.setStatusMessage("");
        clearLoadingImage();
        reloadAction.setEnabled(true);
        rowCountButton.setToolTipText(ResourceMgr.getDescription("LblTableDataRowCountButton"));
      });

      if (error)
      {
        rollbackIfNeeded();
      }
      else
      {
        commitRetrieveIfNeeded();
      }
      retrieveEnd();
      rowCountRetrieveStmt = null;
    }
    return rowCount;
  }

  protected void cancelRowCountRetrieve()
  {
    if (this.rowCountRetrieveStmt != null)
    {
      try
      {
        this.dataDisplay.setStatusMessage(ResourceMgr.getString("MsgCancelRowCount"));
        this.rowCountCancel = true;
        this.rowCountRetrieveStmt.cancel();
      }
      catch (Throwable th)
      {
        LogMgr.logError(new CallerInfo(){}, "Error when cancelling row count retrieve", th);
      }
    }
  }

  public void storeColumnOrder()
  {
    if (!DbExplorerSettings.getRememberColumnOrder()) return;
    saveColumnOrder();
  }

  public void saveColumnOrder()
  {
    if (dataDisplay == null) return;
    WbTable tbl = dataDisplay.getTable();
    if (tbl == null) return;

    if (tbl.isColumnOrderChanged())
    {
      ColumnOrderMgr.getInstance().storeColumnOrder(tbl);
    }
  }

  /**
   * Define the table for which the data should be displayed.
   */
  public void setTable(TableIdentifier aTable)
  {
    initGui();

    if (!this.isRetrieving()) reset();

    this.table = aTable;
    this.tableDefinition = null;
    this.lastSort = null;

    WbSwingUtilities.invoke(() ->
    {
      dataDisplay.getTable().clearLastFilter(true);
      dataDisplay.getTable().resetFilter();
      if (DbExplorerSettings.getDbExplorerTableDetailFullyQualified())
      {
        tableNameLabel.setText(table.getFullyQualifiedName(dbConnection));
      }
      else
      {
        tableNameLabel.setText(table.getTableExpression(dbConnection));
      }
    });
  }

  private String buildSqlForRowCount()
  {
    if (this.table == null) return null;
    TableSelectBuilder builder = new TableSelectBuilder(dbConnection, TableSelectBuilder.ROWCOUNT_TEMPLATE_NAME, TableSelectBuilder.TABLEDATA_TEMPLATE_NAME);
    String sql = builder.getSelectForCount(table);
    return sql;
  }

  private String buildSqlForTable(TableDefinition tableDef, boolean respectMaxrows)
  {
    TableIdentifier tbl;
    List<ColumnIdentifier> columns;
    if (tableDef != null)
    {
      tbl = tableDef.getTable();
      columns = tableDef.getColumns();
    }
    else
    {
      tbl = this.table;
      columns = Collections.emptyList();
    }
    TableSelectBuilder builder = new TableSelectBuilder(this.dbConnection, TableSelectBuilder.TABLEDATA_TEMPLATE_NAME);
    String sort = null;
    if (DbExplorerSettings.getApplySQLSortInDbExplorer() && lastSort != null)
    {
      sort = lastSort.getSqlExpression(dbConnection.getMetadata());
    }
    int maxRows = respectMaxrows ? dataDisplay.getMaxRows() : 0;
    String sql = builder.getSelectForColumns(tbl, columns, sort, maxRows);
    return sql;
  }

  private void clearLoadingImage()
  {
    if (rowCountLabel != null) this.rowCountLabel.setIcon(null);
  }

  @Override
  public boolean confirmCancel()
  {
    return true;
  }

  /**
   *  Directly cancel the retrieval (in the same thread)
   */
  public void cancelRetrieve()
  {
    if (currentRetrievalPanel != null)
    {
      currentRetrievalPanel.cancelExecution();
    }
  }

  /**
   *  Implementation of the Interruptable interface.
   *  This will kick off a Thread that cancels the retrieval.
   */
  @Override
  public void cancelExecution()
  {
    if (!initialized) return;
    if (currentRetrievalPanel == null) return;

    Thread t = new WbThread("Cancel thread")
    {
      @Override
      public void run()
      {
        try
        {
          currentRetrievalPanel.cancelExecution();
        }
        finally
        {
          cancelRetrieve.setEnabled(false);
          WbSwingUtilities.showDefaultCursor(dataDisplay);
        }
      }
    };
    t.start();
  }

  protected void retrieveStart()
  {
    fireDbExecStart();
    this.retrieveRunning = true;
  }

  private void retrieveEnd()
  {
    this.retrieveRunning = false;
    if (currentRetrievalPanel == null)
    {
      this.dataDisplay.updateStatusBar();
    }
    else
    {
      this.currentRetrievalPanel.updateStatusBar();
    }
    fireDbExecEnd();
  }

  protected void dbUpdateStart()
  {
    this.reloadAction.setEnabled(false);
    fireDbExecStart();
    this.updateRunning = true;
  }

  protected void dbUpdateEnd()
  {
    try
    {
      this.reloadAction.setEnabled(true);
    }
    finally
    {
      this.updateRunning = false;
      fireDbExecEnd();
    }
  }

  public boolean isRetrieving()
  {
    return this.retrieveRunning || this.updateRunning;
  }

  private void rollbackIfNeeded()
  {
    if (dbConnection.getDbSettings().getAutoCloseReadOnlyTransactions() != EndReadOnlyTrans.never) return;

    if (ExplorerUtils.isOwnTransaction(dbConnection))
    {
      this.dbConnection.rollbackSilently(new CallerInfo(){});
    }
    else if (this.currentSavepoint != null)
    {
      this.dbConnection.rollback(this.currentSavepoint);
      this.currentSavepoint = null;
    }
  }

  private void commitRetrieveIfNeeded()
  {
    if (dbConnection.getDbSettings().getAutoCloseReadOnlyTransactions() != EndReadOnlyTrans.never) return;

    if (ExplorerUtils.isOwnTransaction(dbConnection))
    {
      if (this.dbConnection.selectStartsTransaction())
      {
        try { this.dbConnection.commit(); } catch (Throwable th) {}
      }
    }
    else if (this.currentSavepoint != null)
    {
      this.dbConnection.releaseSavepoint(this.currentSavepoint);
      this.currentSavepoint = null;
    }
  }

  private void retrieveTableDefinition()
  {
    if (this.table == null)
    {
      tableDefinition = null;
      return;
    }

    List<ColumnIdentifier> columns = dbConnection.getObjectCache().getColumns(table);
    tableDefinition = new TableDefinition(table, columns);
  }

  protected void doRetrieve(boolean respectMaxRows)
  {
    if (this.dbConnection == null) return;
    if (this.isRetrieving()) return;

    String sql = null;
    if (!useDataStoreSource)
    {
      retrieveTableDefinition();
      sql = this.buildSqlForTable(tableDefinition, respectMaxRows);
      if (sql == null)
      {
        return;
      }
    }
    doRetrieve(dataDisplay, sql, respectMaxRows);
  }

  protected void doRetrieve(DwPanel dataPanel, String sql, boolean respectMaxRows)
  {
    this.retrieveStart();

    this.cancelRetrieve.setEnabled(true);
    this.reloadAction.setEnabled(false);
    boolean error = false;

    try
    {
      WbSwingUtilities.showWaitCursor(this);
      WbSwingUtilities.invoke(() ->
      {
        dataPanel.setStatusMessage(ResourceMgr.getString("LblLoadingProgress"));
      });

      setSavepoint();
      this.currentRetrievalPanel = dataPanel;
      if (useDataStoreSource && dataPanel == dataDisplay)
      {
        dataPanel.runCurrentSql(respectMaxRows);
      }
      else
      {
        LogMgr.logDebug(new CallerInfo(){}, "Retrieving table data using:\n" + sql);

        error = !dataPanel.runQuery(sql, respectMaxRows);
        if (!error && GuiSettings.getRetrieveQueryComments())
        {
          ResultColumnMetaData meta = new ResultColumnMetaData(dbConnection);
          meta.updateCommentsFromDefinition(dataPanel.getDataStore(), tableDefinition);
        }

        // By directly setting the update table, we avoid
        // another round-trip to the database to check the table from the
        // passed SQL statement.
        if (!error && dataPanel == dataDisplay)
        {
          WbSwingUtilities.invoke(() ->
          {
            dataPanel.defineUpdateTable(tableDefinition);
            dataPanel.getSelectKeysAction().setEnabled(true);
            String header = ResourceMgr.getString("TxtTableDataPrintHeader") + " " + table;
            dataPanel.setPrintHeader(header);
            dataPanel.showLastExecutionDuration();

            if (lastSort != null)
            {
              dataPanel.setSortDefinition(lastSort);
            }

            ColumnOrderMgr.getInstance().restoreColumnOrder(dataPanel.getTable());
            dataPanel.checkLimitReachedDisplay();
            dataPanel.showGeneratingSQLAsTooltip();
          });
        }
      }
    }
    catch (LowMemoryException mem)
    {
      WbSwingUtilities.showDefaultCursor(this);
      error = true;
      WbManager.getInstance().showLowMemoryError();
    }
    catch (Throwable e)
    {
      WbSwingUtilities.showDefaultCursor(this);
      error = true;

      if (e instanceof OutOfMemoryError)
      {
        try { dataPanel.getTable().reset(); } catch (Throwable th) {}
        WbManager.getInstance().showOutOfMemoryError();
      }
      else
      {
        String msg = ExceptionUtil.getDisplay(e);
        LogMgr.logError(new CallerInfo(){}, "Error retrieving table data", e);
        WbSwingUtilities.showFriendlyErrorMessage(this, msg);
      }
    }
    finally
    {
      WbSwingUtilities.showDefaultCursor(this);

      WbSwingUtilities.invoke(() ->
      {
        dataPanel.clearStatusMessage();
        cancelRetrieve.setEnabled(false);
        reloadAction.setEnabled(true);
      });

      this.retrieveEnd();
      if (error)
      {
        rollbackIfNeeded();
      }
      else
      {
        commitRetrieveIfNeeded();
      }
      this.currentRetrievalPanel = null;
    }

    if (!error && DbExplorerSettings.getSelectDataPanelAfterRetrieve() && dataPanel == dataDisplay)
    {
      WbSwingUtilities.requestFocus(dataDisplay.getTable());
    }
  }

  @Override
  public void setCursor(Cursor newCursor)
  {
    super.setCursor(newCursor);
    if (dataDisplay != null)
    {
      this.dataDisplay.setCursor(newCursor);
    }
  }

  @Override
  public void startReloadPanel(DwPanel panel)
  {
    if (panel != this.dataDisplay) return;
    reload();
  }

  /**
   * Start a new thread to retrieve the table data.
   * @param respectMaxRows
   */
  public void retrieve(final boolean respectMaxRows)
  {
    if (this.dbConnection == null) return;
    if (this.isRetrieving()) return;

    initGui();
    Thread t = new WbThread("TableDataPanel retrieve thread")
    {
      @Override
      public void run()
      {
        doRetrieve(respectMaxRows);
      }
    };
    t.start();
  }

  private String getWorkspacePrefix(int index)
  {
    return "dbexplorer" + index + ".tabledata.";
  }

  /**
   * Save the settings to a Workspace
   */
  public void saveToWorkspace(WbWorkspace wb, int index)
  {
    String prefix = getWorkspacePrefix(index);
    saveSettings(prefix, wb.getSettings());
    storeColumnOrder();
  }

  /**
   * Restore the settings from a Workspace
   */
  public void readFromWorkspace(WbWorkspace wb, int index)
  {
    this.restoreSettings(); // load "global" settings first;
    String prefix = getWorkspacePrefix(index);
    if (!initialized)
    {
      workspaceSettings = new FilteredProperties(wb.getSettings(), prefix);
    }
    else
    {
      readSettings(prefix, wb.getSettings());
    }
  }

  /**
   *  Store global settings for this DbExplorer
   */
  public void saveSettings()
  {
    String prefix = TableDataPanel.class.getName();
    saveSettings(prefix, Settings.getInstance());
  }

  private void saveSettings(String prefix, PropertyStorage props)
  {
    if (initialized)
    {
      props.setProperty(prefix + "maxrows", this.dataDisplay.getMaxRows());
      props.setProperty(prefix + "timeout", this.dataDisplay.getQueryTimeout());
      props.setProperty(prefix + "autoretrieve", this.autoRetrieve.isSelected());
      props.setProperty(prefix + "autoloadrowcount", this.autoloadRowCount);
      props.setProperty(prefix + "warningthreshold", this.warningThreshold);
    }
    else if (workspaceSettings != null)
    {
      workspaceSettings.copyTo(props, prefix);
    }
  }
  /**
   *  Restore global settings for this DbExplorer
   */
  public void restoreSettings()
  {
    String prefix = TableDataPanel.class.getName() + ".";
    readSettings(prefix, Settings.getInstance());
  }

  private void readSettings(String prefix, PropertyStorage props)
  {
    int max = props.getIntProperty(prefix + "maxrows", 500);
    if (max != -1)
    {
      if (dataDisplay != null)
      {
        this.dataDisplay.setMaxRows(max);
      }
    }
    boolean auto = props.getBoolProperty(prefix + "autoretrieve", true);
    if (autoRetrieve != null)
    {
      this.autoRetrieve.setSelected(auto);
    }
    this.autoloadRowCount = props.getBoolProperty(prefix + "autoloadrowcount", true);
    this.warningThreshold = props.getIntProperty(prefix + "warningthreshold", 1500);
    int timeout = props.getIntProperty(prefix + "timeout", 0);
    if (dataDisplay != null)
    {
      this.dataDisplay.setQueryTimeout(timeout);
    }

  }

  public void showData()
  {
    initGui();
    showData(true);
  }

  public void removeTableDisplay()
  {
    this.topPanel.removeAll();
    GridBagConstraints gc = new GridBagConstraints();
    gc.gridx = 0;
    gc.gridy = 0;
    gc.weightx = 1.0;
    gc.anchor = GridBagConstraints.LINE_START;
    this.topPanel.add(toolbar, gc);

    if (showRefreshButton)
    {
      enableRefreshButton = new WbButton();
      enableRefreshButton.addActionListener(this);
      checkRefreshButton();
      gc.gridx = 1;
      gc.gridy = 0;
      gc.weightx = 0.0;
      gc.anchor = GridBagConstraints.LINE_END;
      topPanel.add(enableRefreshButton, gc);
    }

    if (this.additionalButtons != null)
    {
      int width = (int)IconMgr.getInstance().getToolbarIconSize()/2;
      gc.weightx = 0.0;
      gc.insets = new Insets(0,width,0,0);
      gc.anchor = GridBagConstraints.LINE_END;
      for (int i=0; i < additionalButtons.size(); i++)
      {
        gc.gridx ++;
        topPanel.add(additionalButtons.get(i), gc);
      }
    }

    config.removeActionListener(this);
    rowCountButton.removeActionListener(this);
    this.autoloadRowCount = false;
    this.rowCountButton = null;
    this.rowCountLabel = null;
    this.config = null;
    this.tableNameLabel = null;
  }

  private void handleRefreshButton()
  {
    if (refreshMgr.isRegistered(dataDisplay))
    {
      // a refresh is enabled, turn it off
      refreshMgr.removeRefresh(dataDisplay);
    }
    else
    {
      String lastValue = Settings.getInstance().getProperty("workbench.gui.result.refresh.last_interval", null);
      String interval = WbSwingUtilities.getUserInput(this, ResourceMgr.getString("LblRefreshIntv"), lastValue);
      if (StringUtil.isBlank(interval)) return;
      Settings.getInstance().setProperty("workbench.gui.result.refresh.last_interval", interval);

      refreshMgr.addRefresh(this, dataDisplay, interval);
    }
    checkRefreshButton();
    WbSwingUtilities.requestFocus(dataDisplay.getTable());
  }

  private void checkRefreshButton()
  {
    if (refreshMgr.isRegistered(dataDisplay))
    {
      enableRefreshButton.setResourceKey("MnuTxtRemoveRefresh");
      enableRefreshButton.setIcon(IconMgr.getInstance().getLabelIcon("auto_refresh"));
    }
    else
    {
      enableRefreshButton.setResourceKey("MnuTxtReloadAutomatic");
      enableRefreshButton.setIcon(null);
    }
  }

  public void displayData(DataStore result, long lastExecutionTime)
  {
    initGui();
    removeTableDisplay();
    useDataStoreSource = true;
    try
    {
      setConnection(result.getOriginalConnection());
      dataDisplay.showData(result, result.getGeneratingSql(), lastExecutionTime);
      WbSwingUtilities.requestFocus(dataDisplay.getTable());
    }
    catch (SQLException sql)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not display data", sql);
    }
  }

  public void showData(boolean includeData)
  {
    if (this.isRetrieving()) return;

    initGui();
    this.reset();
    long rows = -1;
    if (this.autoloadRowCount)
    {
      rows = this.showRowCount();
      // -1 means an error occurred. No need to continue in that case.
      if (rows == -1) return;
    }

    if (this.autoRetrieve.isSelected() && includeData)
    {
      int max = this.dataDisplay.getMaxRows();
      if ( this.warningThreshold > 0
           && rows > this.warningThreshold
           && max == 0)
      {
        String msg = ResourceMgr.getString("MsgDataDisplayWarningThreshold");
        msg = msg.replace("%rows%", Long.toString(rows));
        int choice = JOptionPane.showConfirmDialog(this, msg, ResourceMgr.TXT_PRODUCT_NAME, JOptionPane.YES_NO_OPTION);
        if (choice != JOptionPane.YES_OPTION) return;
      }
      this.doRetrieve(true);
    }
  }

  @Override
  public void reload()
  {
    if (GuiSettings.getConfirmDiscardResultSetChanges() && isModified())
    {
      if (!WbSwingUtilities.getProceedCancel(this, "MsgDiscardDataChanges")) return;
    }
    initGui();
    long rows = -1;
    boolean ctrlPressed = this.reloadAction.ctrlPressed();

    storeSort();
    storeColumnOrder();

    if (this.autoloadRowCount)
    {
      rows = this.showRowCount();
      // An error occurred --> no need to continue
      if (rows == -1) return;
    }
    this.removeAdditionalData();
    this.retrieve(!ctrlPressed);
  }

  public Window getParentWindow()
  {
    return SwingUtilities.getWindowAncestor(this);
  }

  @Override
  public void actionPerformed(ActionEvent e)
  {
    if (e.getSource() == this.config)
    {
      TableDataSettings p = new TableDataSettings();
      p.setThresholdValue(this.warningThreshold);
      p.setAutoloadData(this.autoRetrieve.isSelected());
      p.setAutoloadRowCount(this.autoloadRowCount);
      Window parent = SwingUtilities.getWindowAncestor(this);
      int choice = JOptionPane.showConfirmDialog(parent, p, ResourceMgr.getString("LblConfigureWarningThresholdTitle"), JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
      if (choice == JOptionPane.OK_OPTION)
      {
        this.warningThreshold = p.getThresholdValue();
        this.autoRetrieve.setSelected(p.getAutoloadData());
        this.autoloadRowCount = p.getAutoloadRowCount();
      }
    }
    else if (e.getSource() == this.rowCountButton)
    {
      this.startRetrieveRowCount();
    }
    else if (e.getSource() == enableRefreshButton)
    {
      handleRefreshButton();
    }
  }

  @Override
  public void tableDataDeleted(List<TableIdentifier> tables)
  {
    if (tables == null) return;
    if (this.table == null) return;
    if (tables.contains(this.table))
    {
      this.reset();
    }
  }

  @Override
  public synchronized void addDbExecutionListener(DbExecutionListener l)
  {
    if (l == null) return;
    if (this.execListener == null)
    {
      this.execListener = Collections.synchronizedList(new ArrayList<>());
    }
    this.execListener.add(l);
  }

  @Override
  public synchronized void removeDbExecutionListener(DbExecutionListener l)
  {
    if (this.execListener == null) return;
    this.execListener.remove(l);
  }

  protected synchronized void fireDbExecStart()
  {
    if (this.dbConnection == null) return;
    this.dbConnection.executionStart(this.dbConnection, this);
    if (this.execListener == null) return;
    for (DbExecutionListener l : execListener)
    {
      if (l != null) l.executionStart(this.dbConnection, this);
    }
  }

  protected synchronized void fireDbExecEnd()
  {
    if (this.dbConnection == null) return;
    this.dbConnection.executionEnd(this.dbConnection, this);
    if (this.execListener == null) return;
    for (DbExecutionListener l : execListener)
    {
      if (l != null) l.executionEnd(this.dbConnection, this);
    }
  }

  @Override
  public void showResult(String sql, String comment, ShowType how)
  {
    if (tabPane == null)
    {
      addTabPane();
    }
    DwPanel newPanel = new DwPanel();
    newPanel.setMaxRows(dataDisplay.getMaxRows());
    newPanel.setConnection(this.dbConnection);
    this.tabPane.add(newPanel, comment);
    this.tabPane.showCloseButton(this);
    newPanel.setReadOnly(true);
    Thread t = new WbThread("TableDataPanel retrieve thread")
    {
      @Override
      public void run()
      {
        doRetrieve(newPanel, sql, true);
      }
    };
    t.start();
  }

  @Override
  public String getTitle()
  {
    return tableNameLabel.getText();
  }

  private void removeAdditionalData()
  {
    if (this.tabPane != null)
    {
      this.tabPane.removeAll();
    }

    if (this.splitPane != null)
    {
      splitPane.removeAll();
      this.remove(splitPane);
      this.add(dataDisplay, BorderLayout.CENTER);
    }
    this.tabPane = null;
    this.splitPane = null;
    this.validate();
  }

  private void addTabPane()
  {
    this.remove(dataDisplay);
    this.tabPane = new WbTabbedPane(JTabbedPane.TOP);
    this.splitPane = new WbSplitPane(JSplitPane.VERTICAL_SPLIT);
    this.splitPane.setLeftComponent(dataDisplay);
    this.splitPane.setRightComponent(tabPane);
    this.add(splitPane, BorderLayout.CENTER);
    this.validate();
    this.splitPane.setDividerLocation(0.65);
  }

  @Override
  public boolean canCloseTab(int index)
  {
    return true;
  }

  @Override
  public void tabCloseButtonClicked(int index)
  {
    if (this.tabPane == null) return;
    try
    {
      DwPanel data = (DwPanel)tabPane.getComponentAt(index);
      data.clearContent();
      tabPane.remove(index);
      if (tabPane.getTabCount() == 0)
      {
       removeAdditionalData();
      }
    }
    catch (Throwable th)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not remove tab index: " + index, th);
    }
  }

}
