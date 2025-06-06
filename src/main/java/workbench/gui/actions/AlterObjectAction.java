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
import java.awt.event.ActionEvent;
import java.util.HashMap;
import java.util.Map;

import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;

import workbench.interfaces.Reloadable;
import workbench.resource.ResourceMgr;

import workbench.db.DbObject;
import workbench.db.DbObjectChanger;
import workbench.db.DbSettings;
import workbench.db.ObjectListDataStore;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.WbTable;
import workbench.gui.dbobjects.RunScriptPanel;

import workbench.storage.DataStore;

import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class AlterObjectAction
  extends WbAction
  implements TableModelListener
{
  private final WbTable tableList;
  private WbConnection dbConnection;
  private Reloadable client;

  public AlterObjectAction(WbTable tables)
  {
    tableList = tables;
    initMenuDefinition("MnuTxtAlterObjects");
    tableList.addTableModelListener(this);
    checkEnabled();
  }

  public void setReloader(Reloadable reload)
  {
    client = reload;
  }

  public void setConnection(WbConnection con)
  {
    dbConnection = con;
    checkEnabled();
  }

  private void checkEnabled()
  {
    DataStore ds = (tableList != null ? tableList.getDataStore() : null);
    boolean modified = (ds != null ? ds.isModified() : false);
    setEnabled(modified && canAlterChangedTypes());
  }

  private boolean canAlterChangedTypes()
  {
    if (dbConnection == null) return false;
    DbSettings db = dbConnection.getDbSettings();
    if (db == null) return false;

    DataStore ds = (tableList != null ? tableList.getDataStore() : null);
    if (ds == null) return false;

    Map<DbObject, DbObject> changed = new HashMap<>();
    for (int row = 0; row < ds.getRowCount(); row++)
    {
      if (ds.isRowModified(row))
      {
        TableIdentifier newTable = getCurrentDefinition(row);
        TableIdentifier oldTable = getOldDefintion(row);
        changed.put(oldTable, newTable);
      }
    }
    DbObjectChanger changer = new DbObjectChanger(dbConnection);
    String sql = changer.getAlterScript(changed);
    return StringUtil.isNotEmpty(sql);
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    String alterScript = getScript();
    if (alterScript == null)
    {
      WbSwingUtilities.showErrorMessageKey(tableList, "MsgNoAlterAvailable");
    }

    RunScriptPanel panel = new RunScriptPanel(dbConnection, alterScript);
    panel.openWindow(tableList, ResourceMgr.getString("TxtAlterTable"));

    if (panel.wasRun() && client != null)
    {
      EventQueue.invokeLater(client::reload);
    }
  }

  private String getScript()
  {
    DataStore ds = tableList.getDataStore();
    DbObjectChanger renamer = new DbObjectChanger(dbConnection);

    Map<DbObject, DbObject> changed = new HashMap<>();

    for (int row = 0; row < ds.getRowCount(); row++)
    {
      DbObject oldObject = getOldDefintion(row);
      DbObject newObject = getCurrentDefinition(row);
      changed.put(oldObject, newObject);
    }

    return renamer.getAlterScript(changed);
  }

  private TableIdentifier getCurrentDefinition(int row)
  {
    if (tableList == null) return null;
    DataStore ds = tableList.getDataStore();

    String name = ds.getValueAsString(row, ObjectListDataStore.RESULT_COL_OBJECT_NAME);
    String schema = ds.getValueAsString(row, ObjectListDataStore.RESULT_COL_SCHEMA);
    String catalog = ds.getValueAsString(row, ObjectListDataStore.RESULT_COL_CATALOG);
    String type = ds.getValueAsString(row, ObjectListDataStore.RESULT_COL_TYPE);
    String comment = ds.getValueAsString(row, ObjectListDataStore.RESULT_COL_REMARKS);
    TableIdentifier tbl = new TableIdentifier(catalog, schema, name);
    tbl.setType(type);
    tbl.setNeverAdjustCase(true);
    tbl.setComment(comment);
    return tbl;
  }

  private TableIdentifier getOldDefintion(int row)
  {
    if (tableList == null) return null;
    DataStore ds = tableList.getDataStore();

    String name = (String)ds.getOriginalValue(row, ObjectListDataStore.RESULT_COL_OBJECT_NAME);
    String schema = (String)ds.getOriginalValue(row, ObjectListDataStore.RESULT_COL_SCHEMA);
    String catalog = (String)ds.getOriginalValue(row, ObjectListDataStore.RESULT_COL_CATALOG);
    String type = (String)ds.getOriginalValue(row, ObjectListDataStore.RESULT_COL_TYPE);
    String comment = (String)ds.getOriginalValue(row, ObjectListDataStore.RESULT_COL_REMARKS);
    TableIdentifier tbl = new TableIdentifier(catalog, schema, name);
    tbl.setType(type);
    tbl.setNeverAdjustCase(true);
    tbl.setComment(comment);
    return tbl;
  }

  @Override
  public void tableChanged(TableModelEvent e)
  {
    if (e.getType() == TableModelEvent.UPDATE)
    {
      checkEnabled();
    }
  }

  @Override
  public boolean useInToolbar()
  {
    return false;
  }

}
