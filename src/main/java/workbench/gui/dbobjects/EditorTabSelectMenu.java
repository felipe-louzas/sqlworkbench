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

import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JMenuItem;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import workbench.interfaces.FilenameChangeListener;
import workbench.interfaces.ResultReceiver;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.DependencyNode;
import workbench.db.TableDefinition;
import workbench.db.TableIdentifier;
import workbench.db.TableSelectBuilder;
import workbench.db.WbConnection;

import workbench.gui.MainWindow;
import workbench.gui.actions.WbAction;
import workbench.gui.actions.clipboard.CreateSnippetAction;
import workbench.gui.components.WbMenu;
import workbench.gui.components.WbMenuItem;
import workbench.gui.sql.PanelContentSender;
import workbench.gui.sql.PasteType;

import workbench.util.CollectionUtil;
import workbench.util.NumberStringCache;

/**
 * @author Thomas Kellerer
 */
public class EditorTabSelectMenu
  extends WbMenu
  implements FilenameChangeListener, ChangeListener, ActionListener
{
  public static final String CMD_CLIPBOARD = "clipboard";
  public static final String SEND_TO_RECEIVER = "receiver";
  public static final String PANEL_CMD_PREFIX = "panel_";

  private MainWindow parentWindow;
  private ActionListener target;
  private String regularTooltip;
  private String newTabTooltip;
  private DependencyNode node;
  private boolean withClipboard;
  private DbObjectList objectList;
  private PasteType pasteType = PasteType.overwrite;
  private boolean useColumnListForTableSelect = true;
  private ResultReceiver receiver;

  public EditorTabSelectMenu(String label, String tooltipKeyNewTab, String tooltipKeyTab, MainWindow parent, ResultReceiver receiver)
  {
    this(label, tooltipKeyNewTab, tooltipKeyTab, parent, receiver, false);
  }

  public EditorTabSelectMenu(String label, String tooltipKeyNewTab, String tooltipKeyTab, MainWindow parent, boolean includeClipboard)
  {
    this(label, tooltipKeyNewTab, tooltipKeyTab, parent, null, includeClipboard);
  }

  public EditorTabSelectMenu(String label, String tooltipKeyNewTab, String tooltipKeyTab, MainWindow parent, ResultReceiver receiver, boolean includeClipboard)
  {
    super(label);
    parentWindow = parent;
    this.receiver = receiver;
    newTabTooltip = ResourceMgr.getDescription(tooltipKeyNewTab, true);
    regularTooltip = ResourceMgr.getDescription(tooltipKeyTab, true);
    withClipboard = includeClipboard;
    if (parentWindow != null)
    {
      parentWindow.addFilenameChangeListener(this);
      parentWindow.addTabChangeListener(this);
    }
  }

  public void setUseColumnListForTableData(boolean flag)
  {
    this.useColumnListForTableSelect = flag;
  }

  public void setPasteType(PasteType type)
  {
    pasteType = type;
  }

  public void setActionListener(ActionListener l)
  {
    this.target = l;
    updateMenu();
  }

  public void setObjectList(DbObjectList list)
  {
    this.objectList = list;
    updateMenu();
  }

  public void setDependencyNode(DependencyNode dep)
  {
    this.node = dep;
  }

  public DependencyNode getDependencyNode()
  {
    return node;
  }

  protected final synchronized void updateMenu()
  {
    if (parentWindow == null) return;

    List<String> panels = this.parentWindow.getPanelLabels();
    if (CollectionUtil.isEmpty(panels)) return;

    int count = this.getItemCount();
    // Make sure none of the items has an ActionListener attached
    for (int i=0; i < count; i++)
    {
      JMenuItem item = this.getItem(i);
      if (item != null && target != null)
      {
        item.removeActionListener(target);
      }
    }

    this.removeAll();

    int current = this.parentWindow.getCurrentPanelIndex();

    Font boldFont = this.getFont();
    if (boldFont != null) boldFont = boldFont.deriveFont(Font.BOLD);

    if (receiver != null)
    {
      JMenuItem send = new WbMenuItem(ResourceMgr.getString("LblHere"));
      send.setActionCommand(SEND_TO_RECEIVER);
      send.addActionListener(target == null ? this : target);
      send.setFont(boldFont);
      this.add(send);
      addSeparator();
    }

    JMenuItem show = new WbMenuItem(ResourceMgr.getString("LblShowDataInNewTab"));
    show.setActionCommand(PANEL_CMD_PREFIX + "-1");
    show.setToolTipText(newTabTooltip);
    show.addActionListener(target == null ? this : target);
    this.add(show);

    if (withClipboard)
    {
      JMenuItem clipboard = new WbMenuItem(ResourceMgr.getString("MnuTxtStmtClip"));
      clipboard.setToolTipText(ResourceMgr.getDescription("MnuTxtStmtClip", true));
      clipboard.setActionCommand(CMD_CLIPBOARD);
      clipboard.addActionListener(target == null ? this : target);
      this.add(clipboard);
    }

    if (receiver == null) addSeparator();

    for (int i=0; i < panels.size(); i++)
    {
      if (panels.get(i) == null) continue;

      String menuText = panels.get(i);
      if (i < 9)
      {
        menuText += " &" + NumberStringCache.getNumberString(i+1);
      }
      else
      {
        menuText += NumberStringCache.getNumberString(i+1);
      }
      JMenuItem item = new WbMenuItem(menuText);

      item.setActionCommand(EditorTabSelectMenu.PANEL_CMD_PREFIX + NumberStringCache.getNumberString(i));
      if (i == current && boldFont != null)
      {
        item.setFont(boldFont);
      }

      // The tooltip is the same for all items
      item.setToolTipText(regularTooltip);
      item.addActionListener(target == null ? this : target);
      this.add(item);
    }

    if (objectList != null)
    {
      List<TableIdentifier> tables = DbObjectList.Util.getSelectedTableObjects(objectList);
      setEnabled(tables.size() > 0);
    }
  }

  /**
   * This is a callback from the MainWindow if a tab has been
   * renamed. As we are showing the tab names in the "Show table data"
   * popup menu, we need to update the popup menu
   */
  @Override
  public void fileNameChanged(Object sender, String newFilename)
  {
    try
    {
      updateMenu();
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error when updating the popup menu", e);
    }
  }

  @Override
  public void stateChanged(ChangeEvent e)
  {
    // Updating the menu needs to be done "later" because
    // the ChangeEvent is also triggered when a tab has been
    // removed (thus implicitely changing the index)
    // but the changeEvent occurs before the actual
    // panel is removed from the control.
    EventQueue.invokeLater(this::updateMenu);
  }

  @Override
  public void dispose()
  {
    int count = this.getItemCount();
    // Make sure none of the items has an ActionListener attached
    for (int i = 0; i < count; i++)
    {
      JMenuItem item = this.getItem(i);
      if (item != null && target != null)
      {
        item.removeActionListener(target);
      }
      if (item instanceof WbMenuItem)
      {
        ((WbMenuItem)item).dispose();
      }
    }
    super.dispose();
  }

  private void showTableData(final int panelIndex, final PasteType type)
  {
    TableIdentifier tbl = objectList.getObjectTable();

    final PanelContentSender sender = new PanelContentSender(this.parentWindow, tbl.getTableName());

    try
    {
      final String sql = buildSqlForTable();

      if (sql == null) return;

      EventQueue.invokeLater(() ->
      {
        sender.sendContent(sql, panelIndex, type, false);
      });
    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not build SELECT statement", ex);
    }
  }

  private String buildSqlForTable()
  {
    WbConnection conn = objectList.getConnection();

    TableIdentifier tbl = objectList.getObjectTable();
    if (tbl == null) return null;

    List<ColumnIdentifier> columns = new ArrayList<>();
    if (useColumnListForTableSelect)
    {
      TableDefinition selectedTable = objectList.getCurrentTableDefinition();
      columns = selectedTable.getColumns();
    }

    TableSelectBuilder builder = new TableSelectBuilder(conn, TableSelectBuilder.TABLEDATA_TEMPLATE_NAME);
    final String sql = builder.getSelectForTableData(tbl, columns, true);
    return sql;
  }

  @Override
  public void actionPerformed(ActionEvent e)
  {
    String command = e.getActionCommand();
    if (command.startsWith(EditorTabSelectMenu.PANEL_CMD_PREFIX) && this.parentWindow != null)
    {
      try
      {
        int panelIndex = Integer.parseInt(command.substring(EditorTabSelectMenu.PANEL_CMD_PREFIX.length()));
        PasteType type = WbAction.isCtrlPressed(e) ? PasteType.append : pasteType;
        showTableData(panelIndex, type);
      }
      catch (Exception ex)
      {
        LogMgr.logError(new CallerInfo(){}, "Error when accessing editor tab", ex);
      }
    }
    else if (CMD_CLIPBOARD.equals(command))
    {
      boolean ctrlPressed = WbAction.isCtrlPressed(e);
      String sql = buildSqlForTable();
      if (sql == null) return;

      if (ctrlPressed)
      {
        sql = CreateSnippetAction.makeJavaString(sql, true);
      }
      StringSelection sel = new StringSelection(sql);
      Clipboard clp = Toolkit.getDefaultToolkit().getSystemClipboard();
      clp.setContents(sel, sel);
    }
  }
}
