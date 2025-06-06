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
package workbench.gui.actions.clipboard;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;

import workbench.resource.Settings;

import workbench.gui.actions.WbAction;
import workbench.gui.components.WbTable;

/**
 * Action to copy the names of all columns into the clipboard
 *
 * @author Andreas Krist
 */
public class CopyAllColumnNamesAction
  extends WbAction
{
  private static final long serialVersionUID = 5433513843703540824L;
  private WbTable client;

  public CopyAllColumnNamesAction(WbTable aClient)
  {
    super();
    this.client = aClient;
    isConfigurable = false;
    initMenuDefinition("MnuTxtCopyAllColNames");
    removeIcon();
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    int columnCount = client.getColumnCount();
    if (columnCount > 0)
    {
      boolean spaceAfterComma = Settings.getInstance().getFormatterAddSpaceAfterComma();

      StringBuilder columnNames = new StringBuilder();

      for (int i = 0; i < columnCount; i++)
      {
        String columnName = client.getColumnName(i);
        columnNames.append(columnName);
        if (i + 1 != columnCount)
        {
          columnNames.append(",");
          if (spaceAfterComma)
          {
            columnNames.append(" ");
          }
        }
      }

      Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
      clipboard.setContents(new StringSelection(columnNames.toString()), null);
    }
  }

  @Override
  public boolean useInToolbar()
  {
    return false;
  }
}
