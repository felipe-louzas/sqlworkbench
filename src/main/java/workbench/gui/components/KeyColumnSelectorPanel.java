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
package workbench.gui.components;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.JCheckBox;

import javax.swing.JLabel;

import workbench.db.ColumnIdentifier;
import workbench.db.TableIdentifier;
import workbench.resource.ResourceMgr;
import workbench.storage.ResultInfo;

/**
 *
 * @author  Thomas Kellerer
 */
public class KeyColumnSelectorPanel
  extends ColumnSelectorPanel
{
  private ColumnIdentifier[] columns;
  private String tableName;
  private JCheckBox saveCheckBox;

  public KeyColumnSelectorPanel(ResultInfo info)
  {
    super(info.getColumns());
    TableIdentifier table = info.getUpdateTable();
    this.tableName = (table == null ? "" : table.getTableName());
    this.setSelectionLabel(ResourceMgr.getString("LblHeaderKeyColumnPKFlag"));
    configureInfoPanel();
    this.doLayout();
    ColumnIdentifier[] originalCols = info.getColumns();
    this.columns = new ColumnIdentifier[originalCols.length];
    for (int i=0; i < this.columns.length; i++)
    {
      this.columns[i] = originalCols[i].createCopy();
      this.setColumnSelected(i, originalCols[i].isPkColumn());
    }
    if (info.isUserDefinedPK())
    {
      this.saveCheckBox.setSelected(true);
    }
  }

  @Override
  protected void configureInfoPanel()
  {
    if (this.tableName == null)
    {
      return;
    }

    String msg = ResourceMgr.getString("MsgSelectKeyColumns").replace("%tablename%", tableName);
    JLabel infoLabel = new JLabel(msg);
    this.infoPanel.setLayout(new GridBagLayout());
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = 0;
    c.fill = GridBagConstraints.BOTH;
    c.anchor = GridBagConstraints.NORTHWEST;
    c.weightx = 1.0;

    this.infoPanel.add(infoLabel, c);

    this.saveCheckBox = new JCheckBox(ResourceMgr.getString("LblRememberPKMapping"));
    this.saveCheckBox.setToolTipText(ResourceMgr.getDescription("LblRememberPKMapping"));
    c.gridx = 0;
    c.gridy = 1;
    c.fill = GridBagConstraints.NONE;
    c.anchor = GridBagConstraints.NORTHWEST;
    c.weighty = 1.0;
    c.weightx = 0.0;
    c.insets = new Insets(5, 0, 5, 0);
    this.infoPanel.add(saveCheckBox, c);
  }

  public boolean getSaveToGlobalPKMap()
  {
    return this.saveCheckBox.isSelected();
  }

  public ColumnIdentifier[] getColumns()
  {
    for (int i = 0; i < this.columns.length; i++)
    {
      columns[i].setIsPkColumn(this.isColumnSelected(i));
    }
    return this.columns;
  }
}

