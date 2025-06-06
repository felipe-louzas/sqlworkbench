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

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.ValidatingDialog;
import workbench.gui.components.WbTabbedPane;
import workbench.gui.sql.DwPanel;
import workbench.gui.sql.EditorPanel;
import workbench.gui.sql.SqlPanel;

import workbench.util.DurationFormatter;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class ShowSourceQueryAction
  extends WbAction
{
  private final SqlPanel panel;
  private final int tabIndex;

  public ShowSourceQueryAction(SqlPanel handler, int index)
  {
    panel = handler;
    isConfigurable = false;
    tabIndex = index;
    initMenuDefinition("MnuTxtShowQuery");
  }

  @Override
  public boolean isEnabled()
  {
    return (panel != null && panel.getSourceQuery(tabIndex) != null);
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    showQuery();
  }

  public void showQuery()
  {
    DwPanel result = panel.getResultAt(tabIndex);
    if (result == null) return;

    EditorPanel editor = EditorPanel.createSqlEditor();
    editor.setBorder(WbSwingUtilities.EMPTY_BORDER);
    WbTabbedPane tab = new WbTabbedPane();

    String sql = panel.getSourceQuery(tabIndex);
    String title = panel.getResultTitle(tabIndex);

    JPanel display = new JPanel(new BorderLayout(0, 5));
    display.setBorder(WbSwingUtilities.createLineBorder(display));

    editor.setText(sql);
    editor.setCaretPosition(0);
    editor.setEditable(false);
    Frame f = WbSwingUtilities.getParentFrame(panel);

    String loadedAt = StringUtil.ISO_TIMESTAMP_FORMATTER.format(panel.getLoadedAt(tabIndex));

    DurationFormatter formatter = new DurationFormatter();
    long millis = result.getLastExecutionTime();
    String duration = formatter.formatDuration(millis);

    String msg = ResourceMgr.getFormattedString("TxtLastExec", loadedAt) + " (" + duration + ")";
    JLabel lbl = new JLabel(msg);
    lbl.setBorder(new EmptyBorder(3, 2, 2, 0));

    display.add(editor, BorderLayout.CENTER);
    display.add(lbl, BorderLayout.NORTH);

    ResultSetInfoPanel resultInfo = new ResultSetInfoPanel(result);

    tab.addTab("SQL", display);
    tab.addTab(ResourceMgr.getString("LblResultMeta"), resultInfo);

    ValidatingDialog d = new ValidatingDialog(f, title, tab, false);
    if (!Settings.getInstance().restoreWindowSize(d, "workbench.resultquery.display"))
    {
      d.setSize(500, 350);
    }
    WbSwingUtilities.center(d, f);
    WbSwingUtilities.repaintLater(editor);
    d.setVisible(true);
    Settings.getInstance().storeWindowSize(d, "workbench.resultquery.display");
  }

  @Override
  public boolean useInToolbar()
  {
    return false;
  }
}
