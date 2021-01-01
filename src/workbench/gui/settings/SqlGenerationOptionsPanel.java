/*
 * SqlGenerationOptionsPanel.java
 *
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2021, Thomas Kellerer
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

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import workbench.interfaces.Restoreable;
import workbench.resource.GeneratedIdentifierCase;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

/**
 *
 * @author  Thomas Kellerer
 */
public class SqlGenerationOptionsPanel
  extends JPanel
  implements Restoreable
{

  public SqlGenerationOptionsPanel()
  {
    super();
    initComponents();

    List<String> types = Settings.getInstance().getLiteralTypeList();
    ComboBoxModel model1 = new DefaultComboBoxModel(types.toArray());
    literalTypes.setModel(model1);
    ComboBoxModel model2 = new DefaultComboBoxModel(types.toArray());
    exportLiteralTypes.setModel(model2);
    ComboBoxModel model3 = new DefaultComboBoxModel(types.toArray());
    diffLiteralsType.setModel(model3);
    tableNameCase.setModel(new DefaultComboBoxModel(GeneratedIdentifierCase.values()));
  }

  @Override
  public void restoreSettings()
  {
    GeneratedIdentifierCase genCase = Settings.getInstance().getGeneratedSqlTableCase();
    this.tableNameCase.setSelectedItem(genCase);

    this.literalTypes.setSelectedItem(Settings.getInstance().getDefaultCopyDateLiteralType());
    this.exportLiteralTypes.setSelectedItem(Settings.getInstance().getDefaultExportDateLiteralType());
    this.diffLiteralsType.setSelectedItem(Settings.getInstance().getDefaultDiffDateLiteralType());
    this.includeEmptyComments.setSelected(Settings.getInstance().getIncludeEmptyComments());
    ignoreIdentity.setSelected(Settings.getInstance().getGenerateInsertIgnoreIdentity());
  }

  @Override
  public void saveSettings()
  {
    Settings set = Settings.getInstance();
    set.setDoFormatUpdates(formatUpdates.isSelected());
    set.setDoFormatInserts(formatInserts.isSelected());
    set.setDoFormatDeletes(formatDeletes.isSelected());
    set.setIncludeOwnerInSqlExport(includeOwner.isSelected());
    set.setGeneratedSqlTableCase((GeneratedIdentifierCase)tableNameCase.getSelectedItem());
    set.setDefaultCopyDateLiteralType((String)literalTypes.getSelectedItem());
    set.setDefaultExportDateLiteralType((String)exportLiteralTypes.getSelectedItem());
    set.setDefaultDiffDateLiteralType((String)diffLiteralsType.getSelectedItem());
    set.setIncludeEmptyComments(includeEmptyComments.isSelected());
    set.setGenerateInsertIgnoreIdentity(ignoreIdentity.isSelected());
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

    formatUpdates = new JCheckBox();
    formatInserts = new JCheckBox();
    formatDeletes = new JCheckBox();
    ignoreIdentity = new JCheckBox();
    tableNameCaseLabel = new JLabel();
    tableNameCase = new JComboBox();
    jPanel1 = new JPanel();
    includeEmptyComments = new JCheckBox();
    includeOwner = new JCheckBox();
    jPanel2 = new JPanel();
    copyLiteralLabel = new JLabel();
    exportLiteralLabel = new JLabel();
    literalTypes = new JComboBox();
    exportLiteralTypes = new JComboBox();
    diffLiteralsLabel = new JLabel();
    diffLiteralsType = new JComboBox();
    jPanel3 = new JPanel();
    jPanel4 = new JPanel();

    setLayout(new GridBagLayout());

    formatUpdates.setSelected(Settings.getInstance().getDoFormatUpdates());
    formatUpdates.setText(ResourceMgr.getString("LblFmtUpd")); // NOI18N
    formatUpdates.setToolTipText(ResourceMgr.getString("d_LblFmtUpd")); // NOI18N
    formatUpdates.setBorder(null);
    formatUpdates.setHorizontalAlignment(SwingConstants.LEFT);
    formatUpdates.setHorizontalTextPosition(SwingConstants.RIGHT);
    formatUpdates.setIconTextGap(5);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new Insets(0, 0, 0, 11);
    add(formatUpdates, gridBagConstraints);

    formatInserts.setSelected(Settings.getInstance().getDoFormatInserts());
    formatInserts.setText(ResourceMgr.getString("LblFmtIns")); // NOI18N
    formatInserts.setToolTipText(ResourceMgr.getString("d_LblFmtIns")); // NOI18N
    formatInserts.setBorder(null);
    formatInserts.setHorizontalAlignment(SwingConstants.LEFT);
    formatInserts.setHorizontalTextPosition(SwingConstants.RIGHT);
    formatInserts.setIconTextGap(5);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.gridwidth = 3;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new Insets(10, 0, 0, 11);
    add(formatInserts, gridBagConstraints);

    formatDeletes.setSelected(Settings.getInstance().getDoFormatDeletes());
    formatDeletes.setText(ResourceMgr.getString("LblFmtDel")); // NOI18N
    formatDeletes.setToolTipText(ResourceMgr.getString("d_LblFmtDel")); // NOI18N
    formatDeletes.setBorder(null);
    formatDeletes.setHorizontalAlignment(SwingConstants.LEFT);
    formatDeletes.setHorizontalTextPosition(SwingConstants.RIGHT);
    formatDeletes.setIconTextGap(5);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.gridwidth = 3;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new Insets(0, 12, 0, 11);
    add(formatDeletes, gridBagConstraints);

    ignoreIdentity.setText(ResourceMgr.getString("LblInsIgnoreId")); // NOI18N
    ignoreIdentity.setToolTipText(ResourceMgr.getString("d_LblInsIgnoreId")); // NOI18N
    ignoreIdentity.setBorder(null);
    ignoreIdentity.setHorizontalAlignment(SwingConstants.LEFT);
    ignoreIdentity.setHorizontalTextPosition(SwingConstants.RIGHT);
    ignoreIdentity.setIconTextGap(5);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.gridwidth = 4;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new Insets(10, 0, 0, 11);
    add(ignoreIdentity, gridBagConstraints);

    tableNameCaseLabel.setLabelFor(tableNameCase);
    tableNameCaseLabel.setText(ResourceMgr.getString("LblGenTableNameCase")); // NOI18N
    tableNameCaseLabel.setToolTipText(ResourceMgr.getString("d_LblGenTableNameCase")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(11, 0, 0, 0);
    add(tableNameCaseLabel, gridBagConstraints);

    tableNameCase.setModel(new DefaultComboBoxModel(new String[] { "As is", "Lowercase", "Uppercase" }));
    tableNameCase.setToolTipText(ResourceMgr.getDescription("LblGenTableNameCase"));
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.gridwidth = 3;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(6, 10, 0, 15);
    add(tableNameCase, gridBagConstraints);

    jPanel1.setLayout(new GridBagLayout());

    includeEmptyComments.setSelected(Settings.getInstance().getIncludeOwnerInSqlExport());
    includeEmptyComments.setText(ResourceMgr.getString("LblGenInclEmptyComments")); // NOI18N
    includeEmptyComments.setToolTipText(ResourceMgr.getString("d_LblGenInclEmptyComments")); // NOI18N
    includeEmptyComments.setBorder(null);
    includeEmptyComments.setHorizontalAlignment(SwingConstants.LEFT);
    includeEmptyComments.setHorizontalTextPosition(SwingConstants.RIGHT);
    includeEmptyComments.setIconTextGap(5);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new Insets(0, 5, 0, 0);
    jPanel1.add(includeEmptyComments, gridBagConstraints);

    includeOwner.setSelected(Settings.getInstance().getIncludeOwnerInSqlExport());
    includeOwner.setText(ResourceMgr.getString("LblGenInclOwn")); // NOI18N
    includeOwner.setToolTipText(ResourceMgr.getString("d_LblGenInclOwn")); // NOI18N
    includeOwner.setBorder(null);
    includeOwner.setHorizontalAlignment(SwingConstants.LEFT);
    includeOwner.setHorizontalTextPosition(SwingConstants.RIGHT);
    includeOwner.setIconTextGap(5);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new Insets(0, 0, 0, 11);
    jPanel1.add(includeOwner, gridBagConstraints);

    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.gridwidth = 4;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new Insets(10, 0, 0, 0);
    add(jPanel1, gridBagConstraints);

    jPanel2.setBorder(BorderFactory.createTitledBorder(ResourceMgr.getString("LblDefDateLiterals"))); // NOI18N
    jPanel2.setLayout(new GridBagLayout());

    copyLiteralLabel.setText(ResourceMgr.getString("LblDefCopyLiteralType")); // NOI18N
    copyLiteralLabel.setToolTipText(ResourceMgr.getString("d_LblDefCopyLiteralType")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 8, 0, 0);
    jPanel2.add(copyLiteralLabel, gridBagConstraints);

    exportLiteralLabel.setLabelFor(exportLiteralTypes);
    exportLiteralLabel.setText(ResourceMgr.getString("LblDefExportLiteralType")); // NOI18N
    exportLiteralLabel.setToolTipText(ResourceMgr.getString("d_LblDefExportLiteralType")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(9, 8, 3, 0);
    jPanel2.add(exportLiteralLabel, gridBagConstraints);

    literalTypes.setToolTipText(ResourceMgr.getDescription("LblDefCopyLiteralType"));
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 8, 0, 0);
    jPanel2.add(literalTypes, gridBagConstraints);

    exportLiteralTypes.setToolTipText(ResourceMgr.getDescription("LblDefExportLiteralType"));
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(6, 8, 3, 0);
    jPanel2.add(exportLiteralTypes, gridBagConstraints);

    diffLiteralsLabel.setLabelFor(diffLiteralsType);
    diffLiteralsLabel.setText(ResourceMgr.getString("LblDefDiffLiteralType")); // NOI18N
    diffLiteralsLabel.setToolTipText(ResourceMgr.getString("d_LblDefDiffLiteralType")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 12, 0, 0);
    jPanel2.add(diffLiteralsLabel, gridBagConstraints);

    diffLiteralsType.setToolTipText(ResourceMgr.getDescription("LblDefExportLiteralType"));
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 8, 0, 0);
    jPanel2.add(diffLiteralsType, gridBagConstraints);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    jPanel2.add(jPanel3, gridBagConstraints);

    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.gridwidth = 4;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(11, 0, 3, 14);
    add(jPanel2, gridBagConstraints);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    add(jPanel4, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private JLabel copyLiteralLabel;
  private JLabel diffLiteralsLabel;
  private JComboBox diffLiteralsType;
  private JLabel exportLiteralLabel;
  private JComboBox exportLiteralTypes;
  private JCheckBox formatDeletes;
  private JCheckBox formatInserts;
  private JCheckBox formatUpdates;
  private JCheckBox ignoreIdentity;
  private JCheckBox includeEmptyComments;
  private JCheckBox includeOwner;
  private JPanel jPanel1;
  private JPanel jPanel2;
  private JPanel jPanel3;
  private JPanel jPanel4;
  private JComboBox literalTypes;
  private JComboBox tableNameCase;
  private JLabel tableNameCaseLabel;
  // End of variables declaration//GEN-END:variables

}
