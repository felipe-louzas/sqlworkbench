/*
 * TableIndexPanel.java
 *
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

import javax.swing.JPanel;

import workbench.interfaces.Reloadable;
import workbench.interfaces.Resettable;

import workbench.gui.actions.ReloadAction;
import workbench.gui.actions.WbAction;
import workbench.gui.components.WbScrollPane;
import workbench.gui.components.WbTable;
import workbench.gui.components.WbToolbar;

/**
 * @author Thomas Kellerer
 */
public class TableIndexPanel
  extends JPanel
  implements Resettable
{
  private ReloadAction reloadIndex;
  private WbTable indexList;
  private WbToolbar toolbar;

  public TableIndexPanel(WbTable indexTable, Reloadable reloader)
  {
    super();
    this.setLayout(new BorderLayout());
    indexList = indexTable;
    WbScrollPane p = new WbScrollPane(indexTable);
    this.add(p, BorderLayout.CENTER);
    if (reloader != null)
    {
      reloadIndex = new ReloadAction(reloader);
      reloadIndex.setEnabled(true);
      toolbar = new WbToolbar();
      toolbar.add(reloadIndex);
      this.add(toolbar, BorderLayout.NORTH);
    }
  }

  @Override
  public void reset()
  {
    if (indexList != null)
    {
      indexList.reset();
    }
  }

  public void dispose()
  {
    WbAction.dispose(reloadIndex);
    if (toolbar != null)
    {
      toolbar.removeAll();
    }
  }

}
