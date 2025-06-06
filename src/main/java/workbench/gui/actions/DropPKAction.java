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

import workbench.resource.ResourceMgr;

import workbench.db.DbObjectChanger;
import workbench.db.TableIdentifier;

import workbench.gui.dbobjects.DbObjectList;
import workbench.gui.dbobjects.RunScriptPanel;

import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class DropPKAction
  extends WbAction
{
  private final DbObjectList columns;

  public DropPKAction(DbObjectList cols)
  {
    super();
    this.columns = cols;
    this.initMenuDefinition("MnuTxtDropPK");
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    TableIdentifier table = columns.getObjectTable();

    DbObjectChanger changer = new DbObjectChanger(columns.getConnection());
    String sql = changer.getDropPKScript(table);
    if (StringUtil.isBlank(sql)) return;

    RunScriptPanel panel = new RunScriptPanel(columns.getConnection(), sql);

    panel.openWindow(columns.getComponent(), ResourceMgr.getString("TxtDropPK"));

    if (panel.wasRun() && columns != null)
    {
      EventQueue.invokeLater(columns::reload);
    }
  }

  @Override
  public boolean useInToolbar()
  {
    return false;
  }
}
