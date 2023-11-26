/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer
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

import workbench.resource.GuiSettings;
import workbench.resource.ResourceMgr;

import workbench.gui.components.WbTraversalPolicy;

/**
 *
 * @author Thomas Kellerer
 */
public class WindowTitleOptionsPanel
  extends javax.swing.JPanel
  implements workbench.interfaces.Restoreable
{

  public WindowTitleOptionsPanel()
  {
    super();
    initComponents();
    // It is important to add these in the correct order
    // which is defined by the numeric values from Settings.SHOW_NO_FILENAME
    // SHOW_FILENAME and SHOW_FULL_PATH
    this.showFilename.addItem(ResourceMgr.getString("TxtShowNone"));
    this.showFilename.addItem(ResourceMgr.getString("TxtShowName"));
    this.showFilename.addItem(ResourceMgr.getString("TxtShowPath"));

    WbTraversalPolicy policy = new WbTraversalPolicy();
    policy.addComponent(showUrl);
    policy.addComponent(includeUser);
    policy.addComponent(productAtEnd);
    policy.addComponent(showWorkspace);
    policy.addComponent(encloseWksp.getSelector());
    policy.addComponent(showProfileGroup);
    policy.addComponent(encloseProfile.getSelector());
    policy.addComponent(showFilename);
    policy.setDefaultComponent(showUrl);

    encloseProfile.setLabelKey("LblEncloseGroupChar");
    encloseWksp.setLabelKey("LblEncloseWkspChar");
    this.setFocusTraversalPolicy(policy);
    this.setFocusCycleRoot(false);
    this.restoreSettings();
  }

  @Override
  public final void restoreSettings()
  {
    int type = GuiSettings.getShowFilenameInWindowTitle();
    if (type >= GuiSettings.SHOW_NO_FILENAME && type <= GuiSettings.SHOW_FULL_PATH)
    {
      this.showFilename.setSelectedIndex(type);
    }
    this.showProfileGroup.setSelected(GuiSettings.getShowProfileGroupInWindowTitle());
    this.showWorkspace.setSelected(GuiSettings.getShowWorkspaceInWindowTitle());
    this.productAtEnd.setSelected(GuiSettings.getShowProductNameAtEnd());
    this.showUrl.setSelected(GuiSettings.getShowURLinWindowTitle());
    this.includeUser.setSelected(GuiSettings.getIncludeUserInTitleURL());
    this.includeUser.setEnabled(showUrl.isSelected());
    encloseProfile.setSelectedItem(GuiSettings.getTitleGroupBracket());
    encloseWksp.setSelectedItem(GuiSettings.getTitleWorkspaceBracket());
    encloseWksp.setEnabled(this.showWorkspace.isSelected());
  }

  @Override
  public void saveSettings()
  {
    GuiSettings.setShowFilenameInWindowTitle(this.showFilename.getSelectedIndex());
    GuiSettings.setShowProfileGroupInWindowTitle(showProfileGroup.isSelected());
    GuiSettings.setShowWorkspaceInWindowTitle(showWorkspace.isSelected());
    GuiSettings.setShowProductNameAtEnd(productAtEnd.isSelected());
    GuiSettings.setShowURLinWindowTitle(showUrl.isSelected());
    GuiSettings.setIncludeUserInTitleURL(includeUser.isSelected());
    GuiSettings.setTitleGroupBracket(encloseProfile.getSelectedBrackets());
    GuiSettings.setTitleWkspBracket(encloseWksp.getSelectedBrackets());
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

    productAtEnd = new javax.swing.JCheckBox();
    showProfileGroup = new javax.swing.JCheckBox();
    showWorkspace = new javax.swing.JCheckBox();
    windowTitleLabel = new javax.swing.JLabel();
    showFilename = new javax.swing.JComboBox();
    jPanel1 = new javax.swing.JPanel();
    showUrl = new javax.swing.JCheckBox();
    includeUser = new javax.swing.JCheckBox();
    encloseWksp = new workbench.gui.settings.BracketSelector();
    encloseProfile = new workbench.gui.settings.BracketSelector();

    setLayout(new java.awt.GridBagLayout());

    productAtEnd.setText(ResourceMgr.getString("LblShowProductAtEnd")); // NOI18N
    productAtEnd.setToolTipText(ResourceMgr.getString("d_LblShowProductAtEnd")); // NOI18N
    productAtEnd.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.gridwidth = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(4, 0, 0, 0);
    add(productAtEnd, gridBagConstraints);

    showProfileGroup.setText(ResourceMgr.getString("LblShowProfileGroup")); // NOI18N
    showProfileGroup.setToolTipText(ResourceMgr.getString("d_LblShowProfileGroup")); // NOI18N
    showProfileGroup.setBorder(null);
    showProfileGroup.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
    showProfileGroup.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
    showProfileGroup.setIconTextGap(5);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.gridwidth = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(showProfileGroup, gridBagConstraints);

    showWorkspace.setText(ResourceMgr.getString("LblShowWorkspace")); // NOI18N
    showWorkspace.setToolTipText(ResourceMgr.getString("d_LblShowWorkspace")); // NOI18N
    showWorkspace.setBorder(null);
    showWorkspace.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
    showWorkspace.setHorizontalTextPosition(javax.swing.SwingConstants.RIGHT);
    showWorkspace.setIconTextGap(5);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.gridwidth = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(3, 0, 2, 0);
    add(showWorkspace, gridBagConstraints);

    windowTitleLabel.setLabelFor(showFilename);
    windowTitleLabel.setText(ResourceMgr.getString("LblShowEditorInfo")); // NOI18N
    windowTitleLabel.setToolTipText(ResourceMgr.getString("d_LblShowEditorInfo")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
    add(windowTitleLabel, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.gridwidth = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(8, 4, 0, 11);
    add(showFilename, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    add(jPanel1, gridBagConstraints);

    showUrl.setText(ResourceMgr.getString("LblUrlInTitle")); // NOI18N
    showUrl.setToolTipText(ResourceMgr.getString("d_LblUrlInTitle")); // NOI18N
    showUrl.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
    showUrl.addActionListener(new java.awt.event.ActionListener()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        showUrlActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.gridwidth = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(1, 0, 2, 11);
    add(showUrl, gridBagConstraints);

    includeUser.setText(ResourceMgr.getString("LblUrlWithUser")); // NOI18N
    includeUser.setToolTipText(ResourceMgr.getString("d_LblUrlWithUser")); // NOI18N
    includeUser.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 0));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.gridwidth = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(1, 20, 5, 11);
    add(includeUser, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(3, 7, 2, 0);
    add(encloseWksp, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(0, 7, 0, 0);
    add(encloseProfile, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  private void showUrlActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_showUrlActionPerformed
  {//GEN-HEADEREND:event_showUrlActionPerformed
    includeUser.setEnabled(showUrl.isSelected());
  }//GEN-LAST:event_showUrlActionPerformed

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private workbench.gui.settings.BracketSelector encloseProfile;
  private workbench.gui.settings.BracketSelector encloseWksp;
  private javax.swing.JCheckBox includeUser;
  private javax.swing.JPanel jPanel1;
  private javax.swing.JCheckBox productAtEnd;
  private javax.swing.JComboBox showFilename;
  private javax.swing.JCheckBox showProfileGroup;
  private javax.swing.JCheckBox showUrl;
  private javax.swing.JCheckBox showWorkspace;
  private javax.swing.JLabel windowTitleLabel;
  // End of variables declaration//GEN-END:variables
}
