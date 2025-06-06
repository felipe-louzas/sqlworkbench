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
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.FontMetrics;
import java.util.List;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;

import workbench.interfaces.Reloadable;
import workbench.resource.IconMgr;
import workbench.resource.ResourceMgr;

import workbench.db.DbObject;
import workbench.db.ObjectListDataStore;
import workbench.db.PackageDefinition;
import workbench.db.ProcedureDefinition;
import workbench.db.WbConnection;
import workbench.db.dependency.DependencyReader;
import workbench.db.dependency.DependencyReaderFactory;

import workbench.gui.WbSwingUtilities;
import workbench.gui.actions.ReloadAction;
import workbench.gui.components.DataStoreTableModel;
import workbench.gui.components.WbScrollPane;
import workbench.gui.components.WbSplitPane;
import workbench.gui.components.WbTable;
import workbench.gui.components.WbToolbar;

import workbench.util.WbThread;

/**
 *
 * @author Thomas Kellerer
 */
public class ObjectDependencyPanel
  extends JPanel
  implements Reloadable
{
  private DbObject currentObject;
  private ReloadAction reload;

  private WbConnection dbConnection;
  private DependencyReader reader;

  private JScrollPane usedScroll;
  private WbTable objectsUsed;
  private WbTable usedByObjects;

  private boolean isRetrieving;
  private WbSplitPane split;
  private JLabel usingLabel;
  private JLabel usedByLabel;

  public ObjectDependencyPanel()
  {
    super(new BorderLayout());
    objectsUsed = new WbTable(false, false, false);
    usedByObjects = new WbTable(false, false, false);

    split = new WbSplitPane(JSplitPane.VERTICAL_SPLIT);
    split.setDividerBorder(WbSwingUtilities.EMPTY_BORDER);
    JPanel usesPanel = new JPanel(new BorderLayout(0,2));
    usingLabel = createTitleLabel("TxtDepsUses");
    usesPanel.add(usingLabel, BorderLayout.PAGE_START);
    usedScroll = new WbScrollPane(objectsUsed);
    usesPanel.add(usedScroll, BorderLayout.CENTER);
    split.setTopComponent(usesPanel);

    JPanel usingPanel = new JPanel(new BorderLayout(0,2));
    usedByLabel = createTitleLabel("TxtDepsUsedBy");

    usingPanel.add(usedByLabel, BorderLayout.PAGE_START);
    JScrollPane scroll2 = new WbScrollPane(usedByObjects);
    usingPanel.add(scroll2, BorderLayout.CENTER);
    split.setBottomComponent(usingPanel);
    split.setDividerLocation(150);
    split.setDividerSize((int)(IconMgr.getInstance().getSizeForLabel() / 2));
    split.setDividerBorder(new EmptyBorder(0, 0, 0, 0));

    add(split, BorderLayout.CENTER);

    reload = new ReloadAction(this);
    reload.setEnabled(false);

    WbToolbar toolbar = new WbToolbar();
    toolbar.add(reload);
    add(toolbar, BorderLayout.PAGE_START);
  }

  private JLabel createTitleLabel(String key)
  {
    JLabel title = new JLabel(ResourceMgr.getString(key));
    title.setOpaque(true);
    title.setBackground(UIManager.getColor("TextArea.background"));
    title.setForeground(UIManager.getColor("TextArea.foreground"));
    title.setIconTextGap((int)(IconMgr.getInstance().getSizeForLabel() / 2));
    title.setHorizontalTextPosition(SwingConstants.LEADING);
    Font f = title.getFont();
    Font f2 = f.deriveFont(Font.BOLD);
    FontMetrics fm = title.getFontMetrics(f2);
    int fontHeight = fm.getHeight();

    int top = (int)(fontHeight / 3);
    int left = (int)(fontHeight / 5);
    title.setBorder(new CompoundBorder(WbSwingUtilities.DEFAULT_LINE_BORDER, new EmptyBorder(top, left, top, left)));
    title.setFont(f2);
    return title;
  }

  public void dispose()
  {
    if (objectsUsed != null) objectsUsed.dispose();
    if (usedByObjects != null) usedByObjects.dispose();
  }

  public void setCurrentObject(DbObject object)
  {
    currentObject = object;
    reset();
    checkPackage();
  }

  private void checkPackage()
  {
    if (currentObject instanceof ProcedureDefinition)
    {
      ProcedureDefinition proc = (ProcedureDefinition)currentObject;
      if (proc.isPackageProcedure())
      {
        currentObject = new PackageDefinition(proc.getSchema(), proc.getPackageName());
      }
    }
  }

  public void setConnection(WbConnection conn)
  {
    reset();
    dbConnection = conn;
    reader = DependencyReaderFactory.getReader(dbConnection);
    reload.setEnabled(true);
  }

  public void reset()
  {
    objectsUsed.reset();
    usedByObjects.reset();
  }

  public void cancel()
  {
  }

  @Override
  public void reload()
  {
    reset();

    if (!WbSwingUtilities.isConnectionIdle(this, dbConnection)) return;

    WbThread loader = new WbThread(this::doLoad, "DependencyLoader Thread");
    loader.start();
  }

  public void doLoad()
  {
    if (reader == null) return;
    if (isRetrieving) return;

    try
    {
      isRetrieving = true;
      reload.setEnabled(false);
      WbSwingUtilities.showWaitCursor(this);
      final List<DbObject> using = reader.getUsedObjects(dbConnection, currentObject);

      EventQueue.invokeLater(() ->
      {
        showResult(using, objectsUsed);
      });

      final List<DbObject> used = reader.getUsedBy(dbConnection, currentObject);

      EventQueue.invokeLater(() ->
      {
        showResult(used, usedByObjects);
        invalidate();
      });

      EventQueue.invokeLater(this::calculateSplit);
    }
    finally
    {
      WbSwingUtilities.showDefaultCursor(this);
      reload.setEnabled(true);
      isRetrieving = false;
    }
  }

  private void calculateSplit()
  {
    invalidate();
    int rows = Math.max(objectsUsed.getRowCount() + 2, 5);
    int height = (int)((objectsUsed.getRowHeight() * rows) * 1.10);
    int minHeight = (int)getHeight() / 5;
    split.setDividerLocation(Math.max(minHeight, height));
    doLayout();
  }

  private void showResult(List<DbObject> objects, WbTable display)
  {
    ObjectListDataStore ds = dbConnection.getMetadata().createObjectListDataStore();
    ds.addObjects(objects);
    ds.resetStatus();
    DataStoreTableModel model = new DataStoreTableModel(ds);
    display.setModel(model, true);
  }

}
