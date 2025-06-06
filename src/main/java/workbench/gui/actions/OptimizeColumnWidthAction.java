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

import workbench.resource.GuiSettings;

import workbench.gui.components.ColumnWidthOptimizer;
import workbench.gui.components.WbTable;

import workbench.util.WbThread;

/**
 * @author Thomas Kellerer
 */
public class OptimizeColumnWidthAction
  extends WbAction
{
  protected WbTable client;
  protected ColumnWidthOptimizer optimizer;

  public OptimizeColumnWidthAction(WbTable aClient)
  {
    super();
    this.client = aClient;
    this.optimizer = new ColumnWidthOptimizer(client);
    this.setMenuTextByKey("MnuTxtOptimizeCol");
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    if (client == null) return;
    final boolean respectColName = ((e.getModifiers() & ActionEvent.SHIFT_MASK) == ActionEvent.SHIFT_MASK) || GuiSettings.getIncludeHeaderInOptimalWidth();
    final int column = client.getPopupColumnIndex();
    Thread t = new WbThread("OptimizeCol Thread")
    {
      @Override
      public void run()
      {
        optimizer.optimizeColWidth(column, respectColName);
      }
    };
    t.start();
  }

  @Override
  public boolean useInToolbar()
  {
    return false;
  }
}
