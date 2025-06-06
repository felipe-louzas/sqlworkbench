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

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.util.List;

import javax.swing.SwingUtilities;

import workbench.interfaces.Exporter;
import workbench.interfaces.TextSelectionListener;
import workbench.resource.ResourceMgr;

import workbench.db.DbObject;

import workbench.gui.WbSwingUtilities;
import workbench.gui.dbobjects.DbObjectList;
import workbench.gui.dbobjects.TableExporter;
import workbench.gui.sql.EditorPanel;

import workbench.util.CollectionUtil;

/**
 * @author Thomas Kellerer
 */
public class SpoolDataAction
  extends WbAction
  implements TextSelectionListener
{
  private EditorPanel editor;
  private boolean canExport = false;
  private DbObjectList objects;
  private Exporter dataExporter;

  public SpoolDataAction(DbObjectList list)
  {
    this(list, "MnuTxtSpoolData");
  }

  public SpoolDataAction(DbObjectList list, String msgKey)
  {
    super();
    this.objects = list;
    this.initMenuDefinition(msgKey);
    this.setIcon("spool-data");
    this.setMenuItemName(ResourceMgr.MNU_TXT_SQL);
    setEnabled(DbObjectList.Util.getSelectedTableObjects(list).size() > 0);
  }

  public SpoolDataAction(Exporter exporter, String msgKey)
  {
    super();
    dataExporter = exporter;
    this.initMenuDefinition(msgKey);
    this.setIcon("spool-data");
    this.setMenuItemName(ResourceMgr.MNU_TXT_SQL);
    setEnabled(false);
  }

  public void canExport(boolean flag)
  {
    this.canExport = flag;
    checkEnabled();
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    if (objects != null)
    {
      exportData();
    }
    else if (dataExporter != null)
    {
      dataExporter.exportData();
    }
  }

  private void exportData()
  {
    if (!WbSwingUtilities.isConnectionIdle(objects.getComponent(), objects.getConnection())) return;

    List<? extends DbObject> tables = objects.getSelectedObjects();
    if (CollectionUtil.isEmpty(tables)) return;

    final TableExporter exporter = new TableExporter(objects.getConnection());
    final Frame f = (Frame)SwingUtilities.getWindowAncestor(objects.getComponent());

    if (exporter.selectTables(tables, f))
    {
      EventQueue.invokeLater(() ->
      {
        exporter.startExport(f);
      });
    }
  }

  @Override
  public void dispose()
  {
    super.dispose();
    if (editor != null)
    {
      editor.removeSelectionListener(this);
    }
  }

  public void setEditor(EditorPanel ed)
  {
    this.editor = ed;
    this.editor.addSelectionListener(this);
    checkEnabled();
  }

  private void checkEnabled()
  {
    if (this.editor != null)
    {
      this.setEnabled(canExport && editor.isTextSelected());
    }
    else
    {
      this.setEnabled(false);
    }
  }

  @Override
  public void selectionChanged(int newStart, int newEnd)
  {
    checkEnabled();
  }
}
