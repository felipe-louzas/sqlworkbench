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
package workbench.gui.dialogs.dataimport;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;

import javax.swing.JPanel;

import workbench.interfaces.ValidatingComponent;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.importer.ImportOptions;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.HistoryTextField;

import workbench.util.StringUtil;

/**
 *
 * @author  Thomas Kellerer
 */
public class GeneralImportOptionsPanel
  extends JPanel
  implements ImportOptions, ValidatingComponent
{
  public GeneralImportOptionsPanel()
  {
    super();
    initComponents();
  }

  public void setEncodingVisible(boolean flag)
  {
    this.encodingPanel.setEnabled(false);
    this.encodingPanel.setVisible(false);
  }

  public void setModeSelectorEnabled(boolean flag)
  {
    this.modeComboBox.setEnabled(flag);
    this.modeComboBox.setSelectedIndex(0);
    this.modeComboBox.setVisible(flag);
  }

  public void saveSettings()
  {
    saveSettings("general");
  }

  public void saveSettings(String key)
  {
    HistoryTextField dateFmt = (HistoryTextField)dateFormat;
    HistoryTextField tsFmt = (HistoryTextField)timestampFormat;

    dateFmt.setSettingsProperty("dateformat");
    tsFmt.setSettingsProperty("timestampformat");
    Settings s = Settings.getInstance();
    if (StringUtil.isEmpty(key))
    {
      key = "general";
    }
    //s.setProperty(, this.getDateFormat());
    dateFmt.saveSettings(s, "workbench.import." + key + ".");
    tsFmt.saveSettings(s, "workbench.import." + key + ".");

    s.setProperty("workbench.import." + key + ".encoding", this.getEncoding());
    s.setProperty("workbench.import." + key + ".mode", this.getMode());

    // remove obsolete property
    s.removeProperty("workbench.import." + key + ".dateformat");
    s.removeProperty("workbench.import." + key + ".timestampformat");
  }

  public void restoreSettings()
  {
    restoreSettings("general");
  }

  public void restoreSettings(String key)
  {
    Settings s = Settings.getInstance();
    HistoryTextField dateFmt = (HistoryTextField)dateFormat;
    HistoryTextField tsFmt = (HistoryTextField)timestampFormat;
    dateFmt.setSettingsProperty("dateformat");
    dateFmt.restoreSettings(s, "workbench.import." + key + ".");

    tsFmt.setSettingsProperty("timestampformat");
    tsFmt.restoreSettings(s, "workbench.import." + key + ".");

    if (StringUtil.isEmpty(dateFmt.getText()))
    {
      // old property before changing to a history textfield
      this.setDateFormat(s.getProperty("workbench.import." + key + ".dateformat", s.getDefaultDateFormat()));
    }

    if (StringUtil.isEmpty(tsFmt.getText()))
    {
      // old property before changing to a history textfield
      this.setTimestampFormat(s.getProperty("workbench.import." + key + ".timestampformat", s.getDefaultTimestampFormat()));
    }
    this.setEncoding(s.getProperty("workbench.export." + key + ".encoding", s.getDefaultDataEncoding()));
    this.setMode(s.getProperty("workbench.import." + key + ".mode", "insert"));
  }

  public void fromOptions(ImportOptions options)
  {
    setDateFormat(options.getDateFormat());
    setTimestampFormat(options.getTimestampFormat());
    setEncoding(options.getEncoding());
  }

  public void removeModeSelection()
  {
    GridBagLayout layout = (GridBagLayout)getLayout();
    GridBagConstraints gc = layout.getConstraints(this.modeComboBox);
    this.remove(modeComboBox);
    this.remove(modeLabel);
    layout.setConstraints(timestampFormat, gc);
  }

  @Override
  public String getMode()
  {
    return (String)this.modeComboBox.getSelectedItem();
  }

  @Override
  public void setMode(String mode)
  {
    this.modeComboBox.setSelectedItem(mode);
  }

  @Override
  public String getDateFormat()
  {
    return ((HistoryTextField)this.dateFormat).getText();
  }

  @Override
  public String getEncoding()
  {
    return encodingPanel.getEncoding();
  }

  @Override
  public String getTimestampFormat()
  {
    return ((HistoryTextField)this.timestampFormat).getText();
  }

  @Override
  public void setDateFormat(String format)
  {
    ((HistoryTextField)dateFormat).setText(format);
  }

  @Override
  public void setEncoding(String enc)
  {
    encodingPanel.setEncoding(enc);
  }

  @Override
  public void setTimestampFormat(String format)
  {
    ((HistoryTextField)timestampFormat).setText(format);
  }

  @Override
  public boolean validateInput()
  {
    String format = getDateFormat();

    if (StringUtil.isNotBlank(format))
    {
      String err = StringUtil.isDatePatternValid(format);
      if (err != null)
      {
        String msg = ResourceMgr.getFormattedString("ErrInvalidInput", dateFormatLabel.getText(), err);
        WbSwingUtilities.showErrorMessage(this, ResourceMgr.getString("TxtError"), msg);
        return false;
      }
    }

    format = getTimestampFormat();
    if (StringUtil.isNotBlank(format))
    {
      String err = StringUtil.isDatePatternValid(format);
      if (err != null)
      {
        String msg = ResourceMgr.getFormattedString("ErrInvalidInput", timestampFormatLabel.getText(), err);
        WbSwingUtilities.showErrorMessage(this, ResourceMgr.getString("TxtError"), msg);
        return false;
      }
    }

    return true;
  }

  @Override
  public void componentDisplayed()
  {

  }

  @Override
  public void componentWillBeClosed()
  {
    // nothing to do
  }


  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents()
  {
    java.awt.GridBagConstraints gridBagConstraints;

    encodingPanel = new workbench.gui.components.EncodingDropDown();
    dateFormatLabel = new javax.swing.JLabel();
    timestampFormatLabel = new javax.swing.JLabel();
    jPanel1 = new javax.swing.JPanel();
    modeLabel = new javax.swing.JLabel();
    modeComboBox = new javax.swing.JComboBox();
    dateFormat = new HistoryTextField();
    timestampFormat = new HistoryTextField();

    setLayout(new java.awt.GridBagLayout());
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 4);
    add(encodingPanel, gridBagConstraints);

    dateFormatLabel.setText(ResourceMgr.getString("LblDateFormat")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(4, 4, 0, 4);
    add(dateFormatLabel, gridBagConstraints);

    timestampFormatLabel.setText(ResourceMgr.getString("LblTimestampFormat")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(4, 4, 0, 4);
    add(timestampFormatLabel, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    add(jPanel1, gridBagConstraints);

    modeLabel.setText(ResourceMgr.getString("LblImportMode")); // NOI18N
    modeLabel.setToolTipText(ResourceMgr.getString("d_LblImportMode")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(4, 4, 0, 4);
    add(modeLabel, gridBagConstraints);

    modeComboBox.setModel(new javax.swing.DefaultComboBoxModel(new String[] { "insert", "update", "insert,update", "update,insert" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 4);
    add(modeComboBox, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 4);
    add(dateFormat, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 4);
    add(timestampFormat, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JComboBox dateFormat;
  private javax.swing.JLabel dateFormatLabel;
  private workbench.gui.components.EncodingDropDown encodingPanel;
  private javax.swing.JPanel jPanel1;
  private javax.swing.JComboBox modeComboBox;
  private javax.swing.JLabel modeLabel;
  private javax.swing.JComboBox timestampFormat;
  private javax.swing.JLabel timestampFormatLabel;
  // End of variables declaration//GEN-END:variables

}
