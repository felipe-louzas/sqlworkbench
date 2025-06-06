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
import java.awt.Rectangle;
import java.awt.event.ActionEvent;

import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.WbTable;

import workbench.util.StringUtil;

/**
 * @author Thomas Kellerer
 */
public class ScrollToColumnAction
  extends WbAction
{
  private WbTable client;

  public ScrollToColumnAction(WbTable aClient)
  {
    super();
    this.client = aClient;
    this.initMenuDefinition("MnuTxtFindColumn");
    this.setIcon(null);
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    String lastValue = Settings.getInstance().getProperty("workbench.gui.findcolumn.lastvalue", null);
    String col = WbSwingUtilities.getUserInput(client, ResourceMgr.getPlainString("MnuTxtFindColumn"), lastValue);
    if (col != null)
    {
      Settings.getInstance().setProperty("workbench.gui.findcolumn.lastvalue", col);
      scrollToColumn(col.toLowerCase());
    }
  }

  protected void scrollToColumn(String toFind)
  {
    if (StringUtil.isBlank(toFind)) return;

    for (int idx = 0; idx < client.getModel().getColumnCount(); idx++)
    {
      String name = client.getModel().getColumnName(idx);
      if (name.toLowerCase().indexOf(toFind) > -1)
      {
        int row = client.getSelectedRow();
        if (row < 0)
        {
          row = client.getFirstVisibleRow();
        }
        final Rectangle rect = client.getCellRect(row, idx, true);
        EventQueue.invokeLater(() ->
        {
          client.scrollRectToVisible(rect);
          client.getTableHeader().repaint();
          client.repaint();
        });
      }
    }
  }

  @Override
  public boolean useInToolbar()
  {
    return false;
  }
}
