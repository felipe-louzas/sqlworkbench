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

import java.awt.event.ActionEvent;

import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.gui.WbSwingUtilities;
import workbench.gui.sql.AutomaticRefreshMgr;
import workbench.gui.sql.DwPanel;
import workbench.gui.sql.SqlPanel;

import workbench.storage.DataStore;

/**
 *
 * @author Thomas Kellerer
 */
public class AutomaticReloadAction
  extends WbAction
{
  private final SqlPanel client;
  private final int tabIndex;

  public AutomaticReloadAction(SqlPanel panel, int resultIndex)
  {
    initMenuDefinition("MnuTxtReloadAutomatic");
    this.tabIndex = resultIndex;
    this.client = panel;
    checkEnabled();
  }

  public void checkEnabled()
  {
    boolean canRefresh = false;
    DwPanel dw = client.getResultAt(tabIndex);
    if (dw != null)
    {
      DataStore ds = dw.getDataStore();
      canRefresh = (ds != null ? ds.getOriginalConnection() != null : false);
    }
    setEnabled(canRefresh);
  }

  @Override
  public void executeAction(ActionEvent evt)
  {
    DwPanel dw = client.getResultAt(tabIndex);
    String lastValue = Settings.getInstance().getProperty("workbench.gui.result.refresh.last_interval", null);
    String interval = WbSwingUtilities.getUserInput(client, ResourceMgr.getString("LblRefreshIntv"), lastValue);
    if (interval == null) return;
    Settings.getInstance().setProperty("workbench.gui.result.refresh.last_interval", interval);
    int milliSeconds = AutomaticRefreshMgr.parseInterval(interval);
    if (dw != null)
    {
      client.getRefreshMgr().addRefresh(client, dw, milliSeconds);
      client.checkAutoRefreshIndicator(dw);
    }
  }

  @Override
  public boolean useInToolbar()
  {
    return false;
  }
}
