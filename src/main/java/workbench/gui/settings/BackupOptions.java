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
package workbench.gui.settings;

import java.awt.Dimension;
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

import workbench.interfaces.Restoreable;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.gui.components.WbFilePicker;

import workbench.util.StringUtil;

/**
 *
 * @author  Thomas Kellerer
 */
public class BackupOptions
  extends JPanel
  implements Restoreable, ActionListener
{
  public BackupOptions()
  {
    super();
    initComponents();
  }

  @Override
  public void restoreSettings()
  {
    createWkspBackup.setSelected(Settings.getInstance().getCreateWorkspaceBackup());
    backupSettingsFile.setSelected(Settings.getInstance().getCreateSettingsBackup());
    backupDrivers.setSelected(Settings.getInstance().getCreateDriverBackup());
    backupProfiles.setSelected(Settings.getInstance().getCreateProfileBackup());
    backupMacros.setSelected(Settings.getInstance().getCreateMacroBackup());

    backupCount.setEnabled(createWkspBackup.isSelected());
    backupCount.setText(Integer.toString(Settings.getInstance().getMaxBackupFiles()));
    backupDirPicker.setFilename(Settings.getInstance().getBackupDirName());
  }

  @Override
  public void saveSettings()
  {
    Settings set = Settings.getInstance();

    int value = StringUtil.getIntValue(backupCount.getText(), -1);
    if (value > -1)
    {
      set.setMaxWorkspaceBackup(value);
    }

    set.setBackupDir(backupDirPicker.getFilename());
    set.setCreateSettingsBackup(backupSettingsFile.isSelected());
    set.setCreateWorkspaceBackup(createWkspBackup.isSelected());
    set.setCreateDriverBackup(backupDrivers.isSelected());
    set.setCreateProfileBackup(backupProfiles.isSelected());
    set.setCreateMacroBackup(backupMacros.isSelected());
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

    createWkspBackup = new JCheckBox();
    backupSettingsFile = new JCheckBox();
    backupMacros = new JCheckBox();
    backupProfiles = new JCheckBox();
    backupDrivers = new JCheckBox();
    jPanel1 = new JPanel();
    backupDirPicker = new WbFilePicker();
    backupCount = new JTextField();
    jLabel2 = new JLabel();
    jLabel1 = new JLabel();

    setLayout(new GridBagLayout());

    createWkspBackup.setText(ResourceMgr.getString("LblBckWksp")); // NOI18N
    createWkspBackup.setToolTipText(ResourceMgr.getString("d_LblBckWksp")); // NOI18N
    createWkspBackup.setBorder(null);
    createWkspBackup.addActionListener(this);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    add(createWkspBackup, gridBagConstraints);

    backupSettingsFile.setText(ResourceMgr.getString("LblBckSettings")); // NOI18N
    backupSettingsFile.setBorder(null);
    backupSettingsFile.setHorizontalAlignment(SwingConstants.LEFT);
    backupSettingsFile.setHorizontalTextPosition(SwingConstants.RIGHT);
    backupSettingsFile.setIconTextGap(5);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(10, 0, 0, 0);
    add(backupSettingsFile, gridBagConstraints);

    backupMacros.setText(ResourceMgr.getString("LblBckMacros")); // NOI18N
    backupMacros.setBorder(null);
    backupMacros.setHorizontalAlignment(SwingConstants.LEFT);
    backupMacros.setHorizontalTextPosition(SwingConstants.RIGHT);
    backupMacros.setIconTextGap(5);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(10, 0, 0, 0);
    add(backupMacros, gridBagConstraints);

    backupProfiles.setText(ResourceMgr.getString("LblBckProfiles")); // NOI18N
    backupProfiles.setBorder(null);
    backupProfiles.setHorizontalAlignment(SwingConstants.LEFT);
    backupProfiles.setHorizontalTextPosition(SwingConstants.RIGHT);
    backupProfiles.setIconTextGap(5);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(10, 0, 0, 0);
    add(backupProfiles, gridBagConstraints);

    backupDrivers.setText(ResourceMgr.getString("LblBckDrivers")); // NOI18N
    backupDrivers.setBorder(null);
    backupDrivers.setHorizontalAlignment(SwingConstants.LEFT);
    backupDrivers.setHorizontalTextPosition(SwingConstants.RIGHT);
    backupDrivers.setIconTextGap(5);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(10, 0, 0, 0);
    add(backupDrivers, gridBagConstraints);

    jPanel1.setLayout(new GridBagLayout());

    backupDirPicker.setToolTipText(ResourceMgr.getString("d_LblBckDir")); // NOI18N
    backupDirPicker.setSelectDirectoryOnly(true);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new Insets(6, 13, 5, 11);
    jPanel1.add(backupDirPicker, gridBagConstraints);

    backupCount.setColumns(3);
    backupCount.setHorizontalAlignment(JTextField.TRAILING);
    backupCount.setToolTipText(ResourceMgr.getString("d_LblMaxWkspBck")); // NOI18N
    backupCount.setMinimumSize(new Dimension(30, 20));
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new Insets(0, 13, 1, 0);
    jPanel1.add(backupCount, gridBagConstraints);

    jLabel2.setText(ResourceMgr.getString("LblBckDir")); // NOI18N
    jLabel2.setToolTipText(ResourceMgr.getString("d_LblBckDir")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(6, 0, 4, 5);
    jPanel1.add(jLabel2, gridBagConstraints);

    jLabel1.setLabelFor(backupCount);
    jLabel1.setText(ResourceMgr.getString("LblMaxWkspBck")); // NOI18N
    jLabel1.setToolTipText(ResourceMgr.getString("d_LblMaxWkspBck")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 0, 1, 7);
    jPanel1.add(jLabel1, gridBagConstraints);

    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new Insets(10, 0, 0, 0);
    add(jPanel1, gridBagConstraints);
  }

  // Code for dispatching events from components to event handlers.

  public void actionPerformed(ActionEvent evt)
  {
    if (evt.getSource() == createWkspBackup)
    {
      BackupOptions.this.createWkspBackupActionPerformed(evt);
    }
  }// </editor-fold>//GEN-END:initComponents

  private void createWkspBackupActionPerformed(ActionEvent evt)//GEN-FIRST:event_createWkspBackupActionPerformed
  {//GEN-HEADEREND:event_createWkspBackupActionPerformed
    // TODO add your handling code here:
  }//GEN-LAST:event_createWkspBackupActionPerformed

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private JTextField backupCount;
  private WbFilePicker backupDirPicker;
  private JCheckBox backupDrivers;
  private JCheckBox backupMacros;
  private JCheckBox backupProfiles;
  private JCheckBox backupSettingsFile;
  private JCheckBox createWkspBackup;
  private JLabel jLabel1;
  private JLabel jLabel2;
  private JPanel jPanel1;
  // End of variables declaration//GEN-END:variables

}
