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
package workbench.gui.settings;

import java.awt.EventQueue;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JPanel;

import workbench.interfaces.Restoreable;
import workbench.resource.ResourceMgr;

import workbench.gui.components.StringPropertyEditor;
import workbench.gui.components.TextComponentMouseListener;
import workbench.gui.dbobjects.objecttree.ComponentPosition;
import workbench.gui.filetree.FileOpenMode;
import workbench.gui.filetree.FileTreeSettings;

/**
 *
 * @author  Thomas Kellerer
 */
public class FileTreeOptionsPanel
  extends JPanel
  implements Restoreable
{
  public FileTreeOptionsPanel()
  {
    super();
    initComponents();
    String[] locations = new String[] {
      ResourceMgr.getString("TxtTabLeft"),
      ResourceMgr.getString("TxtTabRight"),
    };
    treePosition.setModel(new DefaultComboBoxModel(locations));

    defaultDirs.setAllowOnlyDirectories(true);
    defaultDirs.setAddDirIcon("folder_add");
    defaultDirs.setRemoveDirIcon("folder_remove");
    defaultDirs.setDialogTitleKey("MnuTxtAddFolder");

    String[] openTypes = new String[] {
      ResourceMgr.getString("TxtFileTreeSameTab"),
      ResourceMgr.getString("TxtFileTreeNewTab")};
    clickOption.setModel(new DefaultComboBoxModel<>(openTypes));

    invalidate();
    EventQueue.invokeLater(this::validate);
  }

  @Override
  public void restoreSettings()
  {
    this.excludedExtensions.setText(FileTreeSettings.getExcludedExtensions());
    this.excludedFiles.setText(FileTreeSettings.getExcludedFiles());
    this.defaultDirs.setLibraries(FileTreeSettings.getDefaultDirectoryNames());
    ComponentPosition position = FileTreeSettings.getComponentPosition();
    switch (position)
    {
      case left:
        treePosition.setSelectedIndex(0);
        break;
      case right:
        treePosition.setSelectedIndex(1);
        break;
    }
    FileOpenMode openOption = FileTreeSettings.getClickOption();
    switch (openOption)
    {
      case sameTab:
        clickOption.setSelectedIndex(0);
        break;
      case newTab:
        clickOption.setSelectedIndex(1);
        break;
    }
  }

  @Override
  public void saveSettings()
  {
    FileTreeSettings.setExcludedExtensions(excludedExtensions.getText());
    FileTreeSettings.setExcludedFiles(excludedFiles.getText());
    FileTreeSettings.setDefaultDirectoryNames(defaultDirs.getLibraries());
    int selected = treePosition.getSelectedIndex();
    switch (selected)
    {
      case 0:
        FileTreeSettings.setComponentPosition(ComponentPosition.left);
        break;
      case 1:
        FileTreeSettings.setComponentPosition(ComponentPosition.right);
        break;
    }
    int click = clickOption.getSelectedIndex();
    switch (click)
    {
      case 0:
        FileTreeSettings.setClickOption(FileOpenMode.sameTab);
        break;
      case 1:
        FileTreeSettings.setClickOption(FileOpenMode.newTab);
        break;
    }
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

    namesLabel = new javax.swing.JLabel();
    excludedFiles = new StringPropertyEditor();
    excludedExtensions = new StringPropertyEditor();
    extensionsLabel = new javax.swing.JLabel();
    jLabel1 = new javax.swing.JLabel();
    treePosition = new javax.swing.JComboBox<>();
    jLabel2 = new javax.swing.JLabel();
    jLabel3 = new javax.swing.JLabel();
    clickOption = new javax.swing.JComboBox<>();
    dummy = new javax.swing.JPanel();
    defaultDirs = new workbench.gui.components.ClasspathEditor();

    setLayout(new java.awt.GridBagLayout());

    namesLabel.setLabelFor(excludedFiles);
    namesLabel.setText(ResourceMgr.getString("LblFileTreeExclFiles")); // NOI18N
    namesLabel.setToolTipText(ResourceMgr.getString("d_LblFileTreeExclFiles")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(namesLabel, gridBagConstraints);

    excludedFiles.setHorizontalAlignment(javax.swing.JTextField.LEFT);
    excludedFiles.setToolTipText(ResourceMgr.getString("d_LblFileTreeExclFiles")); // NOI18N
    excludedFiles.setName("name"); // NOI18N
    excludedFiles.addMouseListener(new TextComponentMouseListener());
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(1, 8, 0, 3);
    add(excludedFiles, gridBagConstraints);

    excludedExtensions.setHorizontalAlignment(javax.swing.JTextField.LEFT);
    excludedExtensions.setToolTipText(ResourceMgr.getString("d_LblFileTreeExclExts")); // NOI18N
    excludedExtensions.setName("excludedExtensions"); // NOI18N
    excludedFiles.addMouseListener(new TextComponentMouseListener());
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(6, 8, 6, 3);
    add(excludedExtensions, gridBagConstraints);

    extensionsLabel.setLabelFor(extensionsLabel);
    extensionsLabel.setText(ResourceMgr.getString("LblFileTreeExclExts")); // NOI18N
    extensionsLabel.setToolTipText(ResourceMgr.getString("d_LblFileTreeExclExts")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    add(extensionsLabel, gridBagConstraints);

    jLabel1.setText(ResourceMgr.getString("LblTreePosition")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(jLabel1, gridBagConstraints);

    treePosition.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Left", "Right" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(6, 8, 6, 0);
    add(treePosition, gridBagConstraints);

    jLabel2.setText(ResourceMgr.getString("LblFileTreeDefDirs")); // NOI18N
    jLabel2.setToolTipText(ResourceMgr.getString("d_LblFileTreeDefDirs")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    add(jLabel2, gridBagConstraints);

    jLabel3.setText(ResourceMgr.getString("LblFileTreeClickOption")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(jLabel3, gridBagConstraints);

    clickOption.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(3, 8, 3, 0);
    add(clickOption, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    add(dummy, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(0, 8, 0, 3);
    add(defaultDirs, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  // Variables declaration - do not modify//GEN-BEGIN:variables
  public javax.swing.JComboBox<String> clickOption;
  public workbench.gui.components.ClasspathEditor defaultDirs;
  public javax.swing.JPanel dummy;
  public javax.swing.JTextField excludedExtensions;
  public javax.swing.JTextField excludedFiles;
  public javax.swing.JLabel extensionsLabel;
  public javax.swing.JLabel jLabel1;
  public javax.swing.JLabel jLabel2;
  public javax.swing.JLabel jLabel3;
  public javax.swing.JLabel namesLabel;
  public javax.swing.JComboBox<String> treePosition;
  // End of variables declaration//GEN-END:variables


}
