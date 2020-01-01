/*
 * WorkspaceOptions.java
 *
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2019, Thomas Kellerer
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

import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import workbench.interfaces.Restoreable;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

/**
 *
 * @author  Thomas Kellerer
 */
public class WorkspaceOptions
  extends JPanel
  implements Restoreable
{
  public WorkspaceOptions()
  {
    super();
    initComponents();
    String[] types = new String[] {
      ResourceMgr.getString("LblFileWksplink"),
      ResourceMgr.getString("LblFileWkspcontent"),
      ResourceMgr.getString("LblFileWkspnone")
    };
    fileHandling.setModel(new DefaultComboBoxModel(types));
  }

  @Override
  public void restoreSettings()
  {
    autoSaveWorkspace.setSelected(Settings.getInstance().getAutoSaveWorkspace());
    ExternalFileHandling handling = Settings.getInstance().getFilesInWorkspaceHandling();
    switch (handling)
    {
      case link:
        fileHandling.setSelectedIndex(0);
        break;
      case content:
        fileHandling.setSelectedIndex(1);
        break;
      case none:
        fileHandling.setSelectedIndex(2);
        break;
      default:
        fileHandling.setSelectedIndex(0);
    }
  }

  @Override
  public void saveSettings()
  {
    Settings set = Settings.getInstance();

    // General settings
    set.setAutoSaveWorkspace(autoSaveWorkspace.isSelected());
    int index = fileHandling.getSelectedIndex();
    switch (index)
    {
      case 0:
        set.setFilesInWorkspaceHandling(ExternalFileHandling.link);
        break;
      case 1:
        set.setFilesInWorkspaceHandling(ExternalFileHandling.content);
        break;
      case 2:
        set.setFilesInWorkspaceHandling(ExternalFileHandling.none);
        break;
      default:
        set.setFilesInWorkspaceHandling(ExternalFileHandling.link);
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
    GridBagConstraints gridBagConstraints;

    autoSaveWorkspace = new JCheckBox();
    jPanel1 = new JPanel();
    jLabel3 = new JLabel();
    fileHandling = new JComboBox();

    setLayout(new GridBagLayout());

    autoSaveWorkspace.setText(ResourceMgr.getString("LblAutoSaveWksp")); // NOI18N
    autoSaveWorkspace.setToolTipText(ResourceMgr.getString("d_LblAutoSaveWksp")); // NOI18N
    autoSaveWorkspace.setBorder(null);
    autoSaveWorkspace.setHorizontalAlignment(SwingConstants.LEFT);
    autoSaveWorkspace.setHorizontalTextPosition(SwingConstants.RIGHT);
    autoSaveWorkspace.setIconTextGap(5);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 0, 3, 0);
    add(autoSaveWorkspace, gridBagConstraints);

    jPanel1.setLayout(new GridBagLayout());

    jLabel3.setText(ResourceMgr.getString("LblRememberFileWksp")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new Insets(4, 3, 0, 0);
    jPanel1.add(jLabel3, gridBagConstraints);

    fileHandling.setModel(new DefaultComboBoxModel(new String[] { "Item 1", "Item 2", "Item 3", "Item 4" }));
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new Insets(0, 8, 0, 0);
    jPanel1.add(fileHandling, gridBagConstraints);

    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new Insets(3, 0, 0, 0);
    add(jPanel1, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private JCheckBox autoSaveWorkspace;
  private JComboBox fileHandling;
  private JLabel jLabel3;
  private JPanel jPanel1;
  // End of variables declaration//GEN-END:variables

}
