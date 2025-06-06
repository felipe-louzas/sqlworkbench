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
package workbench.gui.actions;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.util.List;

import javax.swing.SwingUtilities;
import javax.swing.UIDefaults;
import javax.swing.UIManager;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import workbench.resource.GuiSettings;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.ConnectionProfile;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.ValidatingDialog;
import workbench.gui.components.WbTable;
import workbench.gui.sql.RecordFormPanel;

import workbench.util.Alias;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * Display a modal dialog showing a single row in a form, rather then a table.
 *
 * @see workbench.interfaces.DbData
 * @see workbench.gui.sql.DwPanel
 *
 * @author Thomas Kellerer
 */
public class DisplayDataFormAction
  extends WbAction
  implements TableModelListener
{
  private WbTable client;

  public DisplayDataFormAction(WbTable aClient)
  {
    super();
    this.setEnabled(false);
    this.initMenuDefinition("MnuTxtShowRecord");
    this.removeIcon();
    this.setMenuItemName(ResourceMgr.MNU_TXT_DATA);
    setTable(aClient);
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    if (client.getRowCount() == 0) return;
    int row = client.getEditingRow();
    if (row < 0) row = client.getSelectedRow();
    if (row < 0) row = 0;

    int col = client.getEditingColumn();
    if (client.isStatusColumnVisible()) col--;
    if (col < 0) col = 0;

    RecordFormPanel panel = new RecordFormPanel(client, row, col);

    Frame window = (Frame)SwingUtilities.getWindowAncestor(client);

    String name = client.getDataStore().getResultName();
    if (StringUtil.isNotBlank(name))
    {
      name = "(" + name + ")";
    }
    String windowTitle = ResourceMgr.getFormattedString("TxtWindowTitleForm", StringUtil.coalesce(name, ""));
    ValidatingDialog dialog = new ValidatingDialog(window, windowTitle, panel);
    Dimension d = dialog.getPreferredSize();
    Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();

    int maxWidth = (int)(screen.width * 0.6);
    int maxHeight = (int)(screen.height * 0.6);

    Dimension maxSize = new Dimension(maxWidth, maxHeight);

    panel.setMaximumSize(maxSize);
    dialog.setMaximumSize(maxSize);

    boolean doLimit = false;

    if (d.height > maxSize.height)
    {
      doLimit = true;

      // make the form wider, so that the vertical scrollbar does not
      // force a horizontal scrollbar to appear because the vertical space is now smaller
      UIDefaults def = UIManager.getDefaults();
      int scrollwidth = def.getInt("ScrollBar.width");
      if (scrollwidth <= 0) scrollwidth = 32; // this should leave enough room...
      d.width += scrollwidth + 2;
    }

    boolean sizeRestored = false;
    if (GuiSettings.getStoreFormRecordDialogSize())
    {
      sizeRestored = Settings.getInstance().restoreWindowSize(dialog, getSizeKey());
    }

    if (!sizeRestored)
    {
      if (d.width > maxSize.width)
      {
        doLimit = true;
      }

      if (doLimit)
      {
        dialog.setPreferredSize(maxSize);
      }
      dialog.pack();
    }

    try
    {
      WbSwingUtilities.center(dialog, window);
      dialog.setVisible(true);
      if (GuiSettings.getStoreFormRecordDialogSize())
      {
        Settings.getInstance().storeWindowSize(dialog, getSizeKey());
      }
    }
    finally
    {
      dialog.dispose();
    }
  }

  private String getSizeKey()
  {
    String key = "workbench.dataform.dialog";
    if (client != null && client.getDataStore() != null)
    {
      WbConnection conn = client.getDataStore().getOriginalConnection();
      if (conn != null)
      {
        key += "." + ConnectionProfile.makeFilename(conn.getUrl(), null);
      }
      TableIdentifier tbl = client.getDataStore().getUpdateTable();
      if (tbl != null)
      {
        key += "." + tbl.getFullyQualifiedName(conn);
      }
      else
      {
        List<Alias> tables = SqlUtil.getTables(client.getDataStore().getGeneratingSql(), false, conn);
        if (tables.size() > 0)
        {
          key += "." + tables.get(0).getObjectName();
        }
      }
    }
    return key;
  }

  public void setTable(WbTable table)
  {
    if (client != null && client != table)
    {
      client.removeTableModelListener(this);
    }
    this.client = table;
    setEnabled(client != null && client.getRowCount() > 0);
    if (client != null)
    {
      client.addTableModelListener(this);
    }
  }

  @Override
  public void tableChanged(TableModelEvent e)
  {
    setEnabled(client != null && client.getRowCount() > 0);
  }

}
