/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2022, Thomas Kellerer
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

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

/**
 *
 * @author  Thomas Kellerer
 */
public class SpreadSheetOptionsPanel
  extends JPanel
  implements SpreadSheetOptions
{
  private String exportType;

  public SpreadSheetOptionsPanel(String type)
  {
    super();
    exportType = type;
    initComponents();
  }

  public void saveSettings(String settingsType)
  {
    Settings s = Settings.getInstance();
    s.setProperty("workbench." + settingsType + "." + exportType + ".pagetitle", this.getPageTitle());
    s.setProperty("workbench." + settingsType + "." + exportType + ".header", getExportHeaders());
    s.setProperty("workbench." + settingsType + "." + exportType + ".fixedheader", getCreateFixedHeaders());
    s.setProperty("workbench." + settingsType + "." + exportType + ".autofilter", getCreateAutoFilter());
    s.setProperty("workbench." + settingsType + "." + exportType + ".infosheet", getCreateInfoSheet());
    s.setProperty("workbench." + settingsType + "." + exportType + ".optimizecols", getOptimizeColumns());
  }

  public void restoreSettings(String settingsType)
  {
    Settings s = Settings.getInstance();
    this.setPageTitle(s.getProperty("workbench." + settingsType + "." + exportType + ".pagetitle", ""));
    boolean headerDefault = s.getBoolProperty("workbench." + settingsType + "." + exportType + ".default.header", false);
    boolean header = s.getBoolProperty("workbench." + settingsType + "." + exportType + ".header", headerDefault);
    this.setExportHeaders(header);
    if (createAutoFilter.isEnabled())
    {
      setCreateAutoFilter(s.getBoolProperty("workbench." + settingsType + "." + exportType + ".autofilter", true));
    }
    setCreateInfoSheet(s.getBoolProperty("workbench." + settingsType + "." + exportType + ".infosheet", false));
    setCreateFixedHeaders(s.getBoolProperty("workbench." + settingsType + "." + exportType + ".fixedheader", true));
    setOptimizeColumns(s.getBoolProperty("workbench." + settingsType + "." + exportType + ".optimizecols", true));
    checkHeaderSettings();
  }

  @Override
  public boolean getIncludeComments()
  {
    return includeComments.isSelected();
  }

  @Override
  public boolean getOptimizeColumns()
  {
    return cbxOptimizeCols.isSelected();
  }

  @Override
  public void setOptimizeColumns(boolean flag)
  {
    cbxOptimizeCols.setSelected(flag);
  }

  @Override
  public boolean getCreateInfoSheet()
  {
    return createInfosheet.isSelected();
  }

  @Override
  public void setCreateInfoSheet(boolean flag)
  {
    createInfosheet.setSelected(flag);
  }

  @Override
  public boolean getCreateFixedHeaders()
  {
    return freezeHeaders.isSelected();
  }

  @Override
  public void setCreateFixedHeaders(boolean flag)
  {
    freezeHeaders.setSelected(flag);
  }

  @Override
  public boolean getCreateAutoFilter()
  {
    return createAutoFilter.isSelected();
  }

  @Override
  public void setCreateAutoFilter(boolean flag)
  {
    createAutoFilter.setSelected(flag);
  }

  @Override
  public boolean getExportHeaders()
  {
    return exportHeaders.isSelected();
  }

  @Override
  public void setExportHeaders(boolean flag)
  {
    exportHeaders.setSelected(flag);
  }

  @Override
  public String getPageTitle()
  {
    return pageTitle.getText();
  }

  @Override
  public void setPageTitle(String title)
  {
    pageTitle.setText(title);
  }

  private void checkHeaderSettings()
  {
    freezeHeaders.setEnabled(exportHeaders.isSelected());
    includeComments.setEnabled(exportHeaders.isSelected());
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

    pageTitleLabel = new JLabel();
    pageTitle = new JTextField();
    exportHeaders = new JCheckBox();
    createInfosheet = new JCheckBox();
    freezeHeaders = new JCheckBox();
    createAutoFilter = new JCheckBox();
    cbxOptimizeCols = new JCheckBox();
    includeComments = new JCheckBox();

    FormListener formListener = new FormListener();

    setLayout(new GridBagLayout());

    pageTitleLabel.setText(ResourceMgr.getString("LblSheetName")); // NOI18N
    pageTitleLabel.setHorizontalTextPosition(SwingConstants.LEADING);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new Insets(10, 6, 3, 6);
    add(pageTitleLabel, gridBagConstraints);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new Insets(0, 6, 0, 6);
    add(pageTitle, gridBagConstraints);

    exportHeaders.setText(ResourceMgr.getString("LblExportIncludeHeaders")); // NOI18N
    exportHeaders.setBorder(null);
    exportHeaders.addActionListener(formListener);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new Insets(7, 6, 0, 6);
    add(exportHeaders, gridBagConstraints);

    createInfosheet.setText(ResourceMgr.getString("LblExportInfoSheet")); // NOI18N
    createInfosheet.setToolTipText(ResourceMgr.getString("d_LblExportInfoSheet")); // NOI18N
    createInfosheet.setBorder(null);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(6, 6, 0, 0);
    add(createInfosheet, gridBagConstraints);

    freezeHeaders.setText(ResourceMgr.getString("LblExportFreezeHeader")); // NOI18N
    freezeHeaders.setToolTipText(ResourceMgr.getString("d_LblExportFreezeHeader")); // NOI18N
    freezeHeaders.setBorder(null);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(6, 6, 0, 0);
    add(freezeHeaders, gridBagConstraints);

    createAutoFilter.setText(ResourceMgr.getString("LblExportAutoFilter")); // NOI18N
    createAutoFilter.setToolTipText(ResourceMgr.getString("d_LblExportAutoFilter")); // NOI18N
    createAutoFilter.setBorder(null);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(6, 6, 0, 0);
    add(createAutoFilter, gridBagConstraints);

    cbxOptimizeCols.setText(ResourceMgr.getPlainString("MnuTxtOptimizeCol")); // NOI18N
    cbxOptimizeCols.setBorder(null);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new Insets(6, 6, 0, 0);
    add(cbxOptimizeCols, gridBagConstraints);

    includeComments.setText(ResourceMgr.getString("LblExportIncludeComments")); // NOI18N
    includeComments.setBorder(null);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(6, 6, 0, 0);
    add(includeComments, gridBagConstraints);
  }

  // Code for dispatching events from components to event handlers.

  private class FormListener implements ActionListener
  {
    FormListener() {}
    public void actionPerformed(ActionEvent evt)
    {
      if (evt.getSource() == exportHeaders)
      {
        SpreadSheetOptionsPanel.this.exportHeadersActionPerformed(evt);
      }
    }
  }// </editor-fold>//GEN-END:initComponents

  private void exportHeadersActionPerformed(ActionEvent evt)//GEN-FIRST:event_exportHeadersActionPerformed
  {//GEN-HEADEREND:event_exportHeadersActionPerformed
    checkHeaderSettings();
  }//GEN-LAST:event_exportHeadersActionPerformed


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private JCheckBox cbxOptimizeCols;
  private JCheckBox createAutoFilter;
  private JCheckBox createInfosheet;
  private JCheckBox exportHeaders;
  private JCheckBox freezeHeaders;
  private JCheckBox includeComments;
  private JTextField pageTitle;
  private JLabel pageTitleLabel;
  // End of variables declaration//GEN-END:variables

}
