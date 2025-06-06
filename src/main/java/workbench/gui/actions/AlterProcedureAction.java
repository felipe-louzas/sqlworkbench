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
import workbench.db.WbConnection;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.WbTable;
import workbench.gui.dbobjects.ProcedureListPanel;
import workbench.gui.dbobjects.RunScriptPanel;

import workbench.storage.DataStore;

import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class AlterProcedureAction
  extends WbAction
  implements TableModelListener
{
  private WbTable procList;
  private WbConnection dbConnection;
  private Reloadable client;

  public AlterProcedureAction(WbTable tables)
  {
    procList = tables;
    initMenuDefinition("MnuTxtAlterObjects");
    procList.addTableModelListener(this);
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
    DataStore ds = (procList != null ? procList.getDataStore() : null);
    boolean modified = (ds != null ? ds.isModified() : false);
    setEnabled(modified && canAlterChangedTypes());
  }

  private boolean canAlterChangedTypes()
  {
    if (dbConnection == null) return false;
    DbSettings db = dbConnection.getDbSettings();
    if (db == null) return false;

    DataStore ds = (procList != null ? procList.getDataStore() : null);
    if (ds == null) return false;

    Map<DbObject, DbObject> changed = new HashMap<>();
    for (int row = 0; row < ds.getRowCount(); row++)
    {
      if (ds.isRowModified(row))
      {
        DbObject oldProc = ds.getUserObject(row, DbObject.class);
        DbObject newProc = ProcedureListPanel.buildDefinitionFromDataStore(dbConnection, ds, row, true);
        changed.put(oldProc, newProc);
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
      WbSwingUtilities.showErrorMessageKey(procList, "MsgNoAlterAvailable");
    }

    RunScriptPanel panel = new RunScriptPanel(dbConnection, alterScript);
    panel.openWindow(procList, ResourceMgr.getString("TxtAlterTable"));

    if (panel.wasRun() && client != null)
    {
      EventQueue.invokeLater(client::reload);
    }
  }

  private String getScript()
  {
    DataStore ds = procList.getDataStore();
    DbObjectChanger renamer = new DbObjectChanger(dbConnection);

    Map<DbObject, DbObject> changed = new HashMap<>();

    for (int row = 0; row < ds.getRowCount(); row++)
    {
      if (ds.isRowModified(row))
      {
        DbObject oldObject = ProcedureListPanel.buildDefinitionFromDataStore(dbConnection, ds, row, false);
        DbObject newObject = ProcedureListPanel.buildDefinitionFromDataStore(dbConnection, ds, row, true);
        changed.put(oldObject, newObject);
      }
    }

    return renamer.getAlterScript(changed);
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
