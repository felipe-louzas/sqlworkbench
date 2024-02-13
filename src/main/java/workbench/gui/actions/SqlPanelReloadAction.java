/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2024 Thomas Kellerer
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

import java.awt.event.ActionEvent;

import workbench.resource.ResourceMgr;

import workbench.gui.sql.DwPanel;
import workbench.gui.sql.SqlPanel;

import workbench.storage.DataStore;

/**
 *
 * @author Thomas Kellerer
 */
public class SqlPanelReloadAction
  extends WbAction
{
  private final SqlPanel client;
  private final int tabIndex;

  public SqlPanelReloadAction(SqlPanel panel, int index)
  {
    initMenuDefinition("TxtReloadResult");
    setMenuItemName(ResourceMgr.MNU_TXT_DATA);
    setIcon("refresh");
    client = panel;
    tabIndex = index;
    checkEnabled();
  }

  public void checkEnabled()
  {
    boolean enable = false;
    if (getSql() != null)
    {
      DwPanel dw = tabIndex == -1 ? client.getCurrentResult() : client.getResultAt(tabIndex);
      if (dw != null)
      {
        DataStore ds = dw.getDataStore();
        enable = (ds != null ? ds.getOriginalConnection() != null : false);
      }
    }
    setEnabled(enable);
  }

  protected String getSql()
  {
    return client.getSourceQuery(tabIndex);
  }

  @Override
  public void executeAction(ActionEvent evt)
  {
    client.reloadResult(tabIndex);
  }

}
