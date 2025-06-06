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
package workbench.gui.tools;

import java.awt.EventQueue;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListModel;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import javax.swing.WindowConstants;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import workbench.WbManager;
import workbench.interfaces.ToolWindow;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.ConnectionMgr;
import workbench.db.ConnectionProfile;
import workbench.db.DbObject;
import workbench.db.ProcedureReader;
import workbench.db.TriggerReader;
import workbench.db.WbConnection;
import workbench.db.search.ObjectSourceSearcher;

import workbench.gui.MainWindow;
import workbench.gui.WbSwingUtilities;
import workbench.gui.components.DataStoreTableModel;
import workbench.gui.components.EditWindow;
import workbench.gui.components.FlatButton;
import workbench.gui.components.RunningJobIndicator;
import workbench.gui.components.TextComponentMouseListener;
import workbench.gui.components.WbScrollPane;
import workbench.gui.components.WbSplitPane;
import workbench.gui.components.WbStatusLabel;
import workbench.gui.components.WbTable;
import workbench.gui.dbobjects.DbObjectSourcePanel;
import workbench.gui.profiles.ProfileSelectionDialog;

import workbench.storage.DataStore;

import workbench.sql.wbcommands.CommandTester;
import workbench.sql.wbcommands.CommonArgs;
import workbench.sql.wbcommands.ObjectResultListDataStore;
import workbench.sql.wbcommands.WbGrepSource;

import workbench.util.CollectionUtil;
import workbench.util.ExceptionUtil;
import workbench.util.StringUtil;
import workbench.util.WbThread;

/**
 *
 * @author Thomas Kellerer
 */
public class ObjectSourceSearchPanel
  extends JPanel
  implements ListSelectionListener, WindowListener, ToolWindow
{
  private boolean standalone;
  private WbConnection connection;
  private ObjectSourceSearcher searcher;
  private JFrame window;

  private final WbTable results;
  private final DbObjectSourcePanel objectSource;
  private WbThread searchThread;
  private final int instanceId;
  private static int instanceCount;

  public ObjectSourceSearchPanel()
  {
    this.instanceId = ++instanceCount;
    initComponents();

    Border sb = new CompoundBorder(WbSwingUtilities.createLineBorder(this), new EmptyBorder(2, 5, 2, 5));
    statusbar.setBorder(sb);
    WbSwingUtilities.makeEqualWidth(closeButton, showScriptButton, startButton);
    checkButtons();

    results = new WbTable(true, false, false);
    WbScrollPane scroll = new WbScrollPane(results);
    results.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    results.getSelectionModel().addListSelectionListener(this);

    ((WbSplitPane)splitPane).setDividerBorder(WbSwingUtilities.EMPTY_BORDER);

    objectSource = new DbObjectSourcePanel(null, null);
    objectSource.setEditable(false);

    splitPane.setRightComponent(objectSource);
    splitPane.setLeftComponent(scroll);

    topPanel.setBorder(new EmptyBorder(5,5,5,5));
    clearSearch();

    TextComponentMouseListener.addListener(this.objectNames, this.searchValues, this.objectTypes, this.schemaNames);
  }

  private void checkCatalogDD()
  {
    boolean showCat = false;
    if (connection != null)
    {
      showCat = connection.getDbSettings().supportsCatalogs() && connection.getDbSettings().supportsSchemas();
      String catalogTerm = StringUtil.capitalize(connection.getMetadata().getCatalogTerm());
      catalogLabel.setText(catalogTerm);
      List<String> catalogs = connection.getMetadata().getCatalogs();
      ComboBoxModel model = new DefaultComboBoxModel(catalogs.toArray());
      catalogDD.setModel(model);
      String currentCatalog = connection.getCurrentCatalog();
      if (currentCatalog != null)
      {
        catalogDD.setSelectedItem(currentCatalog);
      }
    }
    catalogLabel.setVisible(showCat);
    catalogDD.setVisible(showCat);
  }

  private String getSelectedCatalog()
  {
    if (catalogDD.isVisible())
    {
      return (String)catalogDD.getSelectedItem();
    }
    return null;
  }

  protected void clearSearch()
  {
    objectSource.reset();
    setModel(new ObjectResultListDataStore(false));
  }

  protected void startSearch()
  {
    clearSearch();
    searcher = new ObjectSourceSearcher(connection);
    searcher.setRowMonitor(((WbStatusLabel)statusbar).getMonitor());
    startButton.setText(ResourceMgr.getString("LblCancelPlain"));

    List<String> schemas = StringUtil.stringToList(schemaNames.getText(), ",", true, true, false);
    List<String> names = StringUtil.stringToList(objectNames.getText(), ",", true, true, false);
    List<String> types = StringUtil.stringToList(objectTypes.getText(), ",", true, true, false);

    searcher.setSchemasToSearch(schemas);
    searcher.setNamesToSearch(names);
    searcher.setTypesToSearch(types);
    searcher.setCatalog(getSelectedCatalog());

    final List<String> values = StringUtil.stringToList(searchValues.getText(), ",", true, true, false);

    window.setTitle(RunningJobIndicator.TITLE_PREFIX + ResourceMgr.getString("TxtWindowTitleObjectSearcher"));

    searchThread = new WbThread("SourceSearch")
    {
      @Override
      public void run()
      {
        try
        {
          List<DbObject> result = searcher.searchObjects(values, matchAll.isSelected(), ignoreCase.isSelected(), regex.isSelected());
          showResult(result);
        }
        catch (Exception e)
        {
          LogMgr.logError(new CallerInfo(){}, "Error while searching", e);
        }
        finally
        {
          searchEnded();
        }
      }
    };
    selectConnection.setEnabled(false);
    searchThread.start();
  }

  protected void searchEnded()
  {
    searchThread = null;
    WbSwingUtilities.invoke(() ->
    {
      window.setTitle(ResourceMgr.getString("TxtWindowTitleObjectSearcher"));
      String msg = ResourceMgr.getFormattedString("MsgGrepSourceFinished", searcher.getNumberOfObjectsSearched(), results.getRowCount());
      statusbar.setText(msg);
      checkButtons();
    });
  }

  protected void checkButtons()
  {
    if (searcher != null && searcher.isRunning())
    {
      startButton.setText(ResourceMgr.getString("LblCancelPlain"));
      selectConnection.setEnabled(false);
    }
    else
    {
      startButton.setText(ResourceMgr.getString("LblStartSearch"));
      startButton.setEnabled(this.connection != null);
      selectConnection.setEnabled(true);
    }
    selectTypesButton.setEnabled(this.connection != null);
    selectSchemasButton.setEnabled(this.connection != null);
  }

  protected void showResult(List<DbObject> result)
    throws SQLException
  {
    try
    {
      ObjectResultListDataStore ds = new ObjectResultListDataStore(connection, result, false);
      setModel(ds);
    }
    finally
    {
      checkButtons();
    }
  }

  protected void setModel(final DataStore data)
  {
    EventQueue.invokeLater(() ->
    {
      DataStoreTableModel model = new DataStoreTableModel(data);
      results.setModel(model, true);
      results.adjustRowsAndColumns();
    });
  }

  protected void cancelSearch()
  {
    if (searcher != null)
    {
      searcher.cancelSearch();
    }
  }

  protected void selectConnection()
  {
    String profilekey = "workbench.objectsearcher.lastprofile";

    ConnectionProfile prof = null;
    try
    {
      WbSwingUtilities.showWaitCursor(this.window);
      ProfileSelectionDialog dialog = new ProfileSelectionDialog(this.window, true, profilekey);
      WbSwingUtilities.center(dialog, this.window);
      WbSwingUtilities.showDefaultCursor(this.window);
      dialog.setVisible(true);
      boolean cancelled = dialog.isCancelled();
      if (!cancelled)
      {
        prof = dialog.getSelectedProfile();
        if (prof != null)
        {
          Settings.getInstance().setProperty(profilekey, prof.getName());
        }
        else
        {
          LogMgr.logError(new CallerInfo(){}, "NULL Profile selected!", null);
        }
      }
      dialog.setVisible(false);
      dialog.dispose();
    }
    catch (Throwable th)
    {
      LogMgr.logError(new CallerInfo(){}, "Error during connect", th);
      prof = null;
    }
    if (prof != null)
    {
      disconnect();
      connect(prof);
    }
  }

  protected void connect(final ConnectionProfile profile)
  {
    clearSearch();
    statusbar.setText(ResourceMgr.getString("MsgConnecting"));
    //WbSwingUtilities.repaintNow(statusbar);
    selectConnection.setEnabled(false);
    WbSwingUtilities.showWaitCursor(window);

    WbThread t = new WbThread("Connection")
    {
      @Override
      public void run()
      {
        try
        {
          connection = ConnectionMgr.getInstance().getConnection(profile, "ObjectSearcher-" + instanceId);
        }
        catch (Exception e)
        {
          LogMgr.logError(new CallerInfo(){}, "Error during connect", e);
          String msg = ExceptionUtil.getDisplay(e);
          WbSwingUtilities.showFriendlyErrorMessage(ObjectSourceSearchPanel.this, ResourceMgr.getString("ErrConnectFailed"), msg);
          connection = null;
        }
        finally
        {
          connectEnded();
        }
      }
    };
    t.start();
  }

  public void connectEnded()
  {
    WbSwingUtilities.showDefaultCursor(window);
    objectSource.setDatabaseConnection(connection);
    EventQueue.invokeLater(() ->
    {
      statusbar.setText("");
      checkButtons();
      checkCatalogDD();
      updateWindowTitle();
    });
  }

  protected void updateWindowTitle()
  {
    String title = ResourceMgr.getString("TxtWindowTitleObjectSearcher");
    if (this.connection != null && connection.getProfile() != null)
    {
      String profileName = connection.getProfile().getName();
      title = title + " [" + profileName + "]";
    }

    if (searcher != null && searcher.isRunning())
    {
      title = RunningJobIndicator.TITLE_PREFIX + title;
    }
    window.setTitle(title);
  }

  public void saveSettings()
  {
    Settings s = Settings.getInstance();
    s.setProperty("workbench.objectsearcher.ignorecase", ignoreCase.isSelected());
    s.setProperty("workbench.objectsearcher.matchall", matchAll.isSelected());
    s.setProperty("workbench.objectsearcher.regex", regex.isSelected());
    s.storeWindowSize(window, "workbench.objectsearcher.window");
    s.setProperty("workbench.objectsearcher.searchvalues", searchValues.getText());
    s.setProperty("workbench.objectsearcher.schemas", schemaNames.getText());
    s.setProperty("workbench.objectsearcher.objectnames", objectNames.getText());
    s.setProperty("workbench.objectsearcher.objecttypes", objectTypes.getText());
    int location = splitPane.getDividerLocation();
    s.setProperty("workbench.objectsearcher.divider", location);
  }

  public void restoreSettings()
  {
    Settings s = Settings.getInstance();

    ignoreCase.setSelected(s.getBoolProperty("workbench.objectsearcher.ignorecase", true));
    matchAll.setSelected(s.getBoolProperty("workbench.objectsearcher.matchall", false));
    regex.setSelected(s.getBoolProperty("workbench.objectsearcher.regex", false));

    if (!s.restoreWindowSize(window, "workbench.objectsearcher.window"))
    {
      window.setSize(800,600);
    }

    searchValues.setText(s.getProperty("workbench.objectsearcher.searchvalues", ""));
    schemaNames.setText(s.getProperty("workbench.objectsearcher.schemas", ""));
    objectNames.setText(s.getProperty("workbench.objectsearcher.objectnames", ""));
    objectTypes.setText(s.getProperty("workbench.objectsearcher.objecttypes", ""));

    int location = s.getIntProperty("workbench.objectsearcher.divider", 200);
    splitPane.setDividerLocation(location);
  }

  protected void unregister()
  {
    WbManager.getInstance().unregisterToolWindow(this);
  }

  protected void done()
  {
    cancelSearch();
    saveSettings();

    if (standalone)
    {
      // Unregister will actually close the application
      // as this is the only (and thus last) window that is open
      // WbManager will also take care of disconnecting everything
      unregister();
    }
    else
    {
      Thread t = new WbThread("DataPumper disconnect thread")
      {
        @Override
        public void run()
        {
          disconnect();
          unregister();
        }
      };
      t.start();
    }
  }

  @Override
  public void closeWindow()
  {
    this.done();
    if (this.window != null)
    {
      this.window.removeWindowListener(this);
      this.window.dispose();
    }
  }

  @Override
  public void disconnect()
  {
    if (connection != null)
    {
      connection.disconnect();
      connection = null;
    }
  }

  @Override
  public void activate()
  {
  }

  @Override
  public WbConnection getConnection()
  {
    return connection;
  }

  @Override
  public JFrame getWindow()
  {
    return window;
  }

  public void showWindow()
  {
    standalone = true;
    showWindow(null);
  }

  public void showWindow(final MainWindow parent)
  {
    EventQueue.invokeLater(() ->
    {
      _showWindow(parent);
    });
  }

  public void _showWindow(MainWindow parent)
  {
    this.window  = new JFrame(ResourceMgr.getString("TxtWindowTitleObjectSearcher"))
    {
      @Override
      public void setVisible(boolean visible)
      {
        if (!visible) saveSettings();
        super.setVisible(visible);
      }
    };

    final ConnectionProfile profile = parent == null ? null : parent.getCurrentProfile();

    boolean showConnect = standalone || profile == null || Settings.getInstance().getBoolProperty("workbench.gui.objectsearcher.allowconnect", false);
    selectConnection.setVisible(showConnect);
    selectConnection.setEnabled(showConnect);

    window.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

    ResourceMgr.setWindowIcons(window, "searchsource");

    window.getContentPane().add(this);
    restoreSettings();
    window.addWindowListener(this);

    WbManager.getInstance().registerToolWindow(this);

    // Window size has already been restored in restoreSettings()
    if (!Settings.getInstance().restoreWindowPosition(this.window, "workbench.objectsearcher.window"))
    {
      WbSwingUtilities.center(this.window, parent);
    }

    JRootPane rootPane = window.getRootPane();
    rootPane.setDefaultButton(startButton);

    this.window.setVisible(true);

    if (Settings.getInstance().getAutoConnectObjectSearcher() && profile != null)
    {
      EventQueue.invokeLater(() ->
      {
        connect(profile);
      });
    }
  }

  private void selectSchemas()
  {
    if (this.connection == null) return;
    Collection<String> schemas = connection.getMetadata().getSchemas(null, getSelectedCatalog());
    List<String> selected = StringUtil.stringToList(schemaNames.getText(), ",");
    String result = selectFromList(schemas, selected);
    if (result != null)
    {
      schemaNames.setText(result);
    }
  }

  private void selectObjectTypes()
  {
    if (this.connection == null) return;
    Collection<String> types = connection.getMetadata().getObjectTypes();

    // These types can be hardcoded as they are exactly
    // what the searcher is checking as well.
    // getObjectTypes() returns a sorted set, so the new
    // values will automatically be sorted.
    types.add(ProcedureReader.TYPE_NAME_FUNC);
    types.add(ProcedureReader.TYPE_NAME_PROC);
    types.add(TriggerReader.TYPE_NAME);

    List<String> selected = StringUtil.stringToList(objectTypes.getText(), ",", true, true, false, false);

    String result = selectFromList(types, selected);
    if (result != null)
    {
      objectTypes.setText(result);
    }
  }

  private String selectFromList(Collection<String> elements, List<String> selected)
  {
    DefaultListModel<String> model = new DefaultListModel<>();
    model.addAll(elements);
    final JList<String> elementList = new JList(model);

    if (CollectionUtil.isNonEmpty(selected))
    {
      int firstSelected = -1;
      final int[] indexes = new int[selected.size()];
      for (int i=0; i < selected.size(); i++)
      {
        int index = model.indexOf(selected.get(i));
        if (index > -1)
        {
          indexes[i] = index;
          if (firstSelected == -1)
          {
            firstSelected = index;
          }
        }
      }

      final int toSelect = firstSelected;
      EventQueue.invokeLater(() ->
      {
        elementList.setSelectedIndices(indexes);
        elementList.ensureIndexIsVisible(toSelect);
      });
    }

    elementList.setVisibleRowCount(14);

    JScrollPane pane = new JScrollPane(elementList);
    if (WbSwingUtilities.getOKCancel("Select type", this, pane))
    {
      List<String> sel = elementList.getSelectedValuesList();
      if (sel != null)
      {
        return sel.stream().collect(Collectors.joining(","));
      }
    }
    return null;
  }

  @Override
  public void valueChanged(ListSelectionEvent e)
  {
    int row = results.getSelectedRow();
    if (row < 0) return;

    ObjectResultListDataStore ds = (ObjectResultListDataStore)results.getDataStore();
    final CharSequence source = ds.getSource(row, this.connection);
    final String name = ds.getName(row);
    final String type = ds.getType(row);

    EventQueue.invokeLater(() ->
    {
      objectSource.setText(source == null ? "" : source.toString(), name, type);
      objectSource.setCaretPosition(0, false);
      List<String> values = StringUtil.stringToList(searchValues.getText(), ",", true, true, false);
      if (values.size() == 1)
      {
        String text = values.get(0);
        int pos = objectSource.getReplacer().findFirst(text, ignoreCase.isSelected(), false, regex.isSelected());
        if (pos > -1)
        {
          objectSource.requestFocus();
        }
      }
    });
  }

  public void showWbCommand()
  {
    CommandTester t = new CommandTester();
    StringBuilder result = new StringBuilder(150);
    String indent = "\n             ";

    result.append(t.formatVerb(WbGrepSource.VERB) + " -" + WbGrepSource.PARAM_SEARCH_EXP + "=");
    result.append(StringUtil.quoteIfNeeded(searchValues.getText()));
    result.append(indent);
    result.append("-" + WbGrepSource.PARAM_IGNORE_CASE + "=" + Boolean.toString(ignoreCase.isSelected()));
    result.append(indent);
    result.append("-" + WbGrepSource.PARAM_MATCHALL + "=" + Boolean.toString(matchAll.isSelected()));
    result.append(indent);
    result.append("-" + WbGrepSource.PARAM_USE_REGEX + "=" + Boolean.toString(regex.isSelected()));

    if (StringUtil.isNotBlank(objectTypes.getText()))
    {
      result.append(indent);
      result.append("-" + CommonArgs.ARG_TYPES + "=" + StringUtil.quoteIfNeeded(objectTypes.getText()));
    }

    if (StringUtil.isNotBlank(objectNames.getText()))
    {
      result.append(indent);
      result.append("-" + CommonArgs.ARG_OBJECTS + "=" + StringUtil.quoteIfNeeded(objectNames.getText()));
    }

    if (StringUtil.isNotBlank(schemaNames.getText()))
    {
      result.append(indent);
      result.append("-" + CommonArgs.ARG_SCHEMAS + "=" + StringUtil.quoteIfNeeded(schemaNames.getText()));
    }

    result.append("\n;");

    EditWindow w = new EditWindow(this.window, ResourceMgr.getString("TxtWindowTitleGrepScript"), result.toString(), "workbench.objectsearcher.scriptwindow", true);
    w.setVisible(true);
    w.dispose();
  }

  @Override
  public void windowOpened(WindowEvent e)
  {
  }

  @Override
  public void windowClosing(WindowEvent e)
  {
    closeWindow();
  }

  @Override
  public void windowClosed(WindowEvent e)
  {
  }

  @Override
  public void windowIconified(WindowEvent e)
  {
  }

  @Override
  public void windowDeiconified(WindowEvent e)
  {
  }

  @Override
  public void windowActivated(WindowEvent e)
  {
  }

  @Override
  public void windowDeactivated(WindowEvent e)
  {
  }

  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  @SuppressWarnings("unchecked")
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents()
  {
    java.awt.GridBagConstraints gridBagConstraints;

    topPanel = new javax.swing.JPanel();
    catalogLabel = new javax.swing.JLabel();
    catalogDD = new javax.swing.JComboBox<>();
    schemaLabel = new javax.swing.JLabel();
    schemaNames = new javax.swing.JTextField();
    nameLabel = new javax.swing.JLabel();
    objectNames = new javax.swing.JTextField();
    valueLabel = new javax.swing.JLabel();
    searchValues = new javax.swing.JTextField();
    jPanel1 = new javax.swing.JPanel();
    matchAll = new javax.swing.JCheckBox();
    ignoreCase = new javax.swing.JCheckBox();
    regex = new javax.swing.JCheckBox();
    typeLabel = new javax.swing.JLabel();
    objectTypes = new javax.swing.JTextField();
    selectSchemasButton = new FlatButton();
    selectTypesButton = new FlatButton();
    selectConnection = new javax.swing.JButton();
    resultContainer = new javax.swing.JPanel();
    splitPane = new WbSplitPane();
    footerPanel = new javax.swing.JPanel();
    statusbar = new WbStatusLabel();
    buttonPanel = new javax.swing.JPanel();
    startButton = new javax.swing.JButton();
    showScriptButton = new javax.swing.JButton();
    closeButton = new javax.swing.JButton();

    setLayout(new java.awt.BorderLayout());

    topPanel.setLayout(new java.awt.GridBagLayout());

    catalogLabel.setText("Catalog");
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 7, 0);
    topPanel.add(catalogLabel, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.gridwidth = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(5, 4, 7, 0);
    topPanel.add(catalogDD, gridBagConstraints);

    schemaLabel.setText(ResourceMgr.getString("LblSchemas")); // NOI18N
    schemaLabel.setToolTipText(ResourceMgr.getString("d_LblSchemas")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    topPanel.add(schemaLabel, gridBagConstraints);

    schemaNames.setToolTipText(ResourceMgr.getString("d_LblSchemas")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 0);
    topPanel.add(schemaNames, gridBagConstraints);

    nameLabel.setText(ResourceMgr.getString("LblObjectNames")); // NOI18N
    nameLabel.setToolTipText(ResourceMgr.getString("d_LblObjectNames")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 13, 0, 0);
    topPanel.add(nameLabel, gridBagConstraints);

    objectNames.setToolTipText(ResourceMgr.getString("d_LblObjectNames")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 4;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 0.3;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 5);
    topPanel.add(objectNames, gridBagConstraints);

    valueLabel.setText(ResourceMgr.getString("LblSearchCriteria")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    topPanel.add(valueLabel, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 0.3;
    gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 0);
    topPanel.add(searchValues, gridBagConstraints);

    jPanel1.setLayout(new java.awt.GridBagLayout());

    matchAll.setText(ResourceMgr.getString("LblSearchMatchAll")); // NOI18N
    matchAll.setBorder(null);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 0);
    jPanel1.add(matchAll, gridBagConstraints);

    ignoreCase.setText(ResourceMgr.getString("LblSearchIgnoreCase")); // NOI18N
    ignoreCase.setBorder(null);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.weighty = 1.0;
    jPanel1.add(ignoreCase, gridBagConstraints);

    regex.setText(ResourceMgr.getString("LblSearchRegEx")); // NOI18N
    regex.setBorder(null);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 0);
    jPanel1.add(regex, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.gridwidth = 5;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(10, 8, 5, 0);
    topPanel.add(jPanel1, gridBagConstraints);

    typeLabel.setText(ResourceMgr.getString("LblTypes")); // NOI18N
    typeLabel.setToolTipText(ResourceMgr.getString("d_LblTypes")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 5;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 0);
    topPanel.add(typeLabel, gridBagConstraints);

    objectTypes.setToolTipText(ResourceMgr.getString("d_LblTypes")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 6;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 0.3;
    gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 0);
    topPanel.add(objectTypes, gridBagConstraints);

    selectSchemasButton.setText("...");
    selectSchemasButton.setToolTipText(ResourceMgr.getString("d_LblSchemaSelect")); // NOI18N
    selectSchemasButton.addActionListener(new java.awt.event.ActionListener()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        selectSchemasButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 8, 0, 0);
    topPanel.add(selectSchemasButton, gridBagConstraints);

    selectTypesButton.setText("...");
    selectTypesButton.setToolTipText(ResourceMgr.getString("d_LblTypeSelect")); // NOI18N
    selectTypesButton.addActionListener(new java.awt.event.ActionListener()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        selectTypesButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 7;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 8, 0, 5);
    topPanel.add(selectTypesButton, gridBagConstraints);

    selectConnection.setText(ResourceMgr.getString("LblSelectConnection")); // NOI18N
    selectConnection.addActionListener(new java.awt.event.ActionListener()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        selectConnectionActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 7;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(0, 9, 0, 5);
    topPanel.add(selectConnection, gridBagConstraints);

    add(topPanel, java.awt.BorderLayout.NORTH);

    resultContainer.setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 5, 5, 5));
    resultContainer.setLayout(new java.awt.BorderLayout(5, 5));

    splitPane.setDividerLocation(200);
    resultContainer.add(splitPane, java.awt.BorderLayout.CENTER);

    add(resultContainer, java.awt.BorderLayout.CENTER);

    footerPanel.setLayout(new java.awt.GridBagLayout());

    statusbar.setMaximumSize(new java.awt.Dimension(73, 32));
    statusbar.setPreferredSize(new java.awt.Dimension(100, 22));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 5);
    footerPanel.add(statusbar, gridBagConstraints);

    buttonPanel.setLayout(new java.awt.GridBagLayout());

    startButton.setText(ResourceMgr.getString("LblStartSearch")); // NOI18N
    startButton.addActionListener(new java.awt.event.ActionListener()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        startButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    buttonPanel.add(startButton, gridBagConstraints);

    showScriptButton.setText(ResourceMgr.getString("LblShowScript")); // NOI18N
    showScriptButton.addActionListener(new java.awt.event.ActionListener()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        showScriptButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 0);
    buttonPanel.add(showScriptButton, gridBagConstraints);

    closeButton.setText(ResourceMgr.getString("LblClose")); // NOI18N
    closeButton.addActionListener(new java.awt.event.ActionListener()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        closeButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 4;
    gridBagConstraints.gridy = 0;
    buttonPanel.add(closeButton, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(9, 5, 3, 5);
    footerPanel.add(buttonPanel, gridBagConstraints);

    add(footerPanel, java.awt.BorderLayout.SOUTH);
  }// </editor-fold>//GEN-END:initComponents

  private void selectConnectionActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_selectConnectionActionPerformed
  {//GEN-HEADEREND:event_selectConnectionActionPerformed
    selectConnection();
  }//GEN-LAST:event_selectConnectionActionPerformed

  private void closeButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_closeButtonActionPerformed
  {//GEN-HEADEREND:event_closeButtonActionPerformed
    closeWindow();
  }//GEN-LAST:event_closeButtonActionPerformed

  private void startButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_startButtonActionPerformed
  {//GEN-HEADEREND:event_startButtonActionPerformed
    if (searcher == null || !searcher.isRunning())
    {
      startSearch();
    }
    else
    {
      cancelSearch();
    }
  }//GEN-LAST:event_startButtonActionPerformed

  private void selectTypesButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_selectTypesButtonActionPerformed
  {//GEN-HEADEREND:event_selectTypesButtonActionPerformed
    selectObjectTypes();
  }//GEN-LAST:event_selectTypesButtonActionPerformed

  private void selectSchemasButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_selectSchemasButtonActionPerformed
  {//GEN-HEADEREND:event_selectSchemasButtonActionPerformed
    selectSchemas();
  }//GEN-LAST:event_selectSchemasButtonActionPerformed

  private void showScriptButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_showScriptButtonActionPerformed
  {//GEN-HEADEREND:event_showScriptButtonActionPerformed
    showWbCommand();
  }//GEN-LAST:event_showScriptButtonActionPerformed

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JPanel buttonPanel;
  private javax.swing.JComboBox<String> catalogDD;
  private javax.swing.JLabel catalogLabel;
  private javax.swing.JButton closeButton;
  private javax.swing.JPanel footerPanel;
  private javax.swing.JCheckBox ignoreCase;
  private javax.swing.JPanel jPanel1;
  private javax.swing.JCheckBox matchAll;
  private javax.swing.JLabel nameLabel;
  private javax.swing.JTextField objectNames;
  private javax.swing.JTextField objectTypes;
  private javax.swing.JCheckBox regex;
  private javax.swing.JPanel resultContainer;
  private javax.swing.JLabel schemaLabel;
  private javax.swing.JTextField schemaNames;
  private javax.swing.JTextField searchValues;
  private javax.swing.JButton selectConnection;
  private javax.swing.JButton selectSchemasButton;
  private javax.swing.JButton selectTypesButton;
  private javax.swing.JButton showScriptButton;
  private javax.swing.JSplitPane splitPane;
  private javax.swing.JButton startButton;
  private javax.swing.JLabel statusbar;
  private javax.swing.JPanel topPanel;
  private javax.swing.JLabel typeLabel;
  private javax.swing.JLabel valueLabel;
  // End of variables declaration//GEN-END:variables

}
