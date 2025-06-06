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

import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import workbench.interfaces.Resettable;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.TableIdentifier;
import workbench.db.TriggerDefinition;
import workbench.db.TriggerReader;
import workbench.db.TriggerReaderFactory;
import workbench.db.WbConnection;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.DataStoreTableModel;
import workbench.gui.components.WbScrollPane;
import workbench.gui.components.WbSplitPane;
import workbench.gui.components.WbTable;
import workbench.gui.renderer.RendererSetup;
import workbench.gui.sql.EditorPanel;

import workbench.storage.DataStore;

/**
 *
 * @author Thomas Kellerer
 */
public class TriggerDisplayPanel
  extends JPanel
  implements ListSelectionListener, Resettable
{
  private TriggerReader reader;
  private final WbTable triggers;
  private final EditorPanel source;
  private final WbSplitPane splitPane;

  public TriggerDisplayPanel()
  {
    super(new BorderLayout());
    triggers = new WbTable();
    triggers.setRendererSetup(RendererSetup.getBaseSetup());
    source = EditorPanel.createSqlEditor();
    source.setEditable(false);
    //source.setBorder(WbSwingUtilities.EMPTY_BORDER);
    setBorder(WbSwingUtilities.EMPTY_BORDER);

    JPanel list = new JPanel(new BorderLayout());
    list.add(new WbScrollPane(this.triggers), BorderLayout.CENTER);

    splitPane = new WbSplitPane(JSplitPane.VERTICAL_SPLIT, list, this.source);
    add(splitPane, BorderLayout.CENTER);
    triggers.getSelectionModel().addListSelectionListener(this);
    triggers.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
  }

  public void saveSettings()
  {
    Settings.getInstance().setProperty(this.getClass().getName() + ".divider", this.splitPane.getDividerLocation());
  }

  public void restoreSettings()
  {
    int loc = Settings.getInstance().getIntProperty(this.getClass().getName() + ".divider", 200);
    this.splitPane.setDividerLocation(loc);
  }

  public void setConnection(WbConnection aConnection)
  {
    this.reader = TriggerReaderFactory.createReader(aConnection);
    this.source.setDatabaseConnection(aConnection);
    this.reset();
  }

  @Override
  public void reset()
  {
    this.triggers.reset();
    this.source.reset();
  }

  public void readTriggers(final TableIdentifier table)
  {
    source.setText("");
    try
    {
      if (table == null) return;
      DataStore trg = reader.getTableTriggers(table);
      final DataStoreTableModel rs = new DataStoreTableModel(trg);
      WbSwingUtilities.invoke(() ->
      {
        triggers.setModel(rs, true);
        triggers.adjustRowsAndColumns();
        if (triggers.getRowCount() > 0)
        {
          triggers.getSelectionModel().setSelectionInterval(0, 0);
        }
      });
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error retrieving triggers", e);
      this.reset();
    }
  }

  @Override
  public void valueChanged(ListSelectionEvent e)
  {
    if (e.getValueIsAdjusting()) return;
    int row = this.triggers.getSelectedRow();
    if (row < 0) return;
    TriggerDefinition def = this.triggers.getUserObject(row, TriggerDefinition.class);
    if (def == null)
    {
      LogMgr.logError(new CallerInfo(){}, "No TriggerDefinition stored in DataStore!", null);
      return;
    }

    try
    {
      String sql = reader.getTriggerSource(def, true);
      this.source.setText(sql);
      this.source.setCaretPosition(0);
    }
    catch (Exception ex)
    {
      ex.printStackTrace();
      this.source.setText("");
    }
  }

}

