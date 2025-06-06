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
package workbench.gui.dialogs.export;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;

import workbench.resource.IconMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.gui.components.DividerBorder;
import workbench.gui.components.EncodingDropDown;
import workbench.gui.components.FlatButton;

import workbench.util.StringUtil;

/**
 *
 * @author  Thomas Kellerer
 */
public class GeneralExportOptionsPanel
  extends JPanel
  implements ExportOptions
{
  private final GridBagConstraints encodingPanelConstraints;
  private final GridBagConstraints selectedRowsConstraints;

  public GeneralExportOptionsPanel()
  {
    super();
    initComponents();
    GridBagLayout gbl = (GridBagLayout)this.getLayout();
    this.encodingPanelConstraints = gbl.getConstraints(encodingPanel);
    this.selectedRowsConstraints = gbl.getConstraints(selectedRows);
    Border b = new CompoundBorder(DividerBorder.BOTTOM_DIVIDER, new EmptyBorder(0, 0, 4, 0));
    Border b2 = new CompoundBorder(new EmptyBorder(0,0,4,0), b);
    setBorder(b2);
  }

  public void saveSettings(String type)
  {
    Settings s = Settings.getInstance();
    s.setProperty("workbench." + type + ".general.dateformat", this.getDateFormat());
    s.setProperty("workbench." + type + ".general.timestampformat", this.getTimestampFormat());
    s.setProperty("workbench." + type + ".general.encoding", this.getEncoding());
    s.setProperty("workbench." + type + ".nullstring", this.getNullString());
  }

  public void restoreSettings(String type)
  {
    Settings s = Settings.getInstance();
    this.setDateFormat(s.getProperty("workbench." + type + ".general.dateformat", ""));
    this.setTimestampFormat(s.getProperty("workbench." + type + ".general.timestampformat", ""));
    this.setEncoding(s.getProperty("workbench." + type + ".general.encoding", s.getDefaultDataEncoding()));
    this.setNullString(s.getProperty("workbench." + type + ".nullstring", null));
  }

  public void setSelectedRowCount(int count)
  {
    this.selectedRows.setEnabled(count > 0);
    if (count <= 0)
    {
      this.selectedRows.setSelected(false);
    }
  }

  @Override
  public boolean selectedRowsOnly()
  {
    return selectedRows.isSelected();
  }

  public void showSelectedRowsCbx()
  {
    if (isComponentVisible(selectedRows)) return;
    this.add(selectedRows, selectedRowsConstraints);
  }

  public void hideSelectedRowsCbx()
  {
    if (!isComponentVisible(encodingPanel)) return;
    this.remove(selectedRows);
  }

  public void showEncodingsPanel()
  {
    if (isComponentVisible(encodingPanel)) return;
    this.add(encodingPanel, encodingPanelConstraints);
  }

  public void hideEncodingPanel()
  {
    if (!isComponentVisible(encodingPanel)) return;
    this.remove(encodingPanel);
  }

  private boolean isComponentVisible(Component toCheck)
  {
    for (Component component : getComponents())
    {
      if (component == toCheck) return true;
    }
    return false;
  }

  @Override
  public void setTimestampTZFormat(String format)
  {
    timestampTZFormat.setText(StringUtil.trim(format));
  }

  @Override
  public String getTimestampTZFormat()
  {
    return timestampTZFormat.getText().trim();
  }

  @Override
  public void setNullString(String value)
  {
    nullString.setText(value);
  }

  @Override
  public String getNullString()
  {
    if (StringUtil.isBlank(nullString.getText()))
    {
      return null;
    }
    return nullString.getText().trim();
  }

  @Override
  public String getDateFormat()
  {
    return this.dateFormat.getText();
  }

  @Override
  public String getEncoding()
  {
    return encodingPanel.getEncoding();
  }

  @Override
  public String getTimestampFormat()
  {
    return this.timestampFormat.getText();
  }

  @Override
  public void setDateFormat(String format)
  {
    dateFormat.setText(format);
  }

  @Override
  public void setEncoding(String enc)
  {
    encodingPanel.setEncoding(enc);
  }

  @Override
  public void setTimestampFormat(String format)
  {
    timestampFormat.setText(format);
  }

  public void showRetrieveColumnsLabel()
  {
    selectColumnsButton.setText(ResourceMgr.getString("LblRetrieveColumns"));
  }

  public void showSelectColumnsLabel()
  {
    selectColumnsButton.setText(ResourceMgr.getString("LblSelectColumns"));
  }

  public void allowSelectColumns(boolean flag)
  {
    this.selectColumnsButton.setEnabled(flag);
  }

  public void setSelectedColumnsInfo(String tooltip)
  {
    if (tooltip != null)
    {
      this.selectColumnsButton.setIcon(IconMgr.getInstance().getLabelIcon("tick"));
      this.selectColumnsButton.setToolTipText(tooltip);
    }
    else
    {
      this.selectColumnsButton.setIcon(null);
      this.selectColumnsButton.setToolTipText(null);
    }
  }

  public Object addColumnSelectListener(ActionListener l)
  {
    this.selectColumnsButton.addActionListener(l);
    return this.selectColumnsButton;
  }

  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents()
  {
    GridBagConstraints gridBagConstraints;

    encodingPanel = new EncodingDropDown();
    dateFormatLabel = new JLabel();
    dateFormat = new JTextField();
    timestampFormatLabel = new JLabel();
    timestampFormat = new JTextField();
    selectColumnsButton = new FlatButton();
    nullStringLabel = new JLabel();
    nullString = new JTextField();
    selectedRows = new JCheckBox();
    timestampTZFormatLabel = new JLabel();
    timestampTZFormat = new JTextField();

    setLayout(new GridBagLayout());
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new Insets(0, 0, 6, 4);
    add(encodingPanel, gridBagConstraints);

    dateFormatLabel.setText(ResourceMgr.getString("LblDateFormat")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new Insets(8, 0, 7, 0);
    add(dateFormatLabel, gridBagConstraints);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new Insets(4, 4, 7, 4);
    add(dateFormat, gridBagConstraints);

    timestampFormatLabel.setText(ResourceMgr.getString("LblTimestampFormat")); // NOI18N
    timestampFormatLabel.setToolTipText(ResourceMgr.getString("d_LblTimestampFormat")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new Insets(4, 0, 7, 0);
    add(timestampFormatLabel, gridBagConstraints);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new Insets(0, 4, 7, 4);
    add(timestampFormat, gridBagConstraints);

    selectColumnsButton.setText(ResourceMgr.getString("LblSelectColumns")); // NOI18N
    selectColumnsButton.setToolTipText(ResourceMgr.getString("d_LblSelectColumns")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new Insets(6, 0, 7, 4);
    add(selectColumnsButton, gridBagConstraints);

    nullStringLabel.setText(ResourceMgr.getString("LblNullString")); // NOI18N
    nullStringLabel.setToolTipText(ResourceMgr.getString("d_LblNullString")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new Insets(4, 0, 7, 0);
    add(nullStringLabel, gridBagConstraints);

    nullString.setToolTipText(ResourceMgr.getString("d_LblNullString")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new Insets(0, 4, 7, 4);
    add(nullString, gridBagConstraints);

    selectedRows.setText(ResourceMgr.getString("LblSelectedRowsOnly")); // NOI18N
    selectedRows.setHorizontalTextPosition(SwingConstants.LEADING);
    selectedRows.setMargin(new Insets(2, 0, 2, 2));
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = GridBagConstraints.FIRST_LINE_START;
    add(selectedRows, gridBagConstraints);

    timestampTZFormatLabel.setText(ResourceMgr.getString("LblTimestampTZFormat")); // NOI18N
    timestampTZFormatLabel.setToolTipText(ResourceMgr.getString("d_LblTimestampTZFormat")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new Insets(4, 0, 7, 0);
    add(timestampTZFormatLabel, gridBagConstraints);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new Insets(0, 4, 7, 4);
    add(timestampTZFormat, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private JTextField dateFormat;
  private JLabel dateFormatLabel;
  private EncodingDropDown encodingPanel;
  private JTextField nullString;
  private JLabel nullStringLabel;
  private JButton selectColumnsButton;
  private JCheckBox selectedRows;
  private JTextField timestampFormat;
  private JLabel timestampFormatLabel;
  private JTextField timestampTZFormat;
  private JLabel timestampTZFormatLabel;
  // End of variables declaration//GEN-END:variables

}
