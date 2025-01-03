/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0 (the "License")
 * that restricts the use for certain governments.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.sql-workbench.eu/manual/license.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.eu
 */
package workbench.gui.dialogs.export;

import javax.swing.JPanel;

import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

/**
 *
 * @author Thomas Kellerer
 */
public class FormattedTextOptionsPanel
  extends JPanel
  implements FormattedTextOptions
{

  public FormattedTextOptionsPanel()
  {
    initComponents();
  }

  @Override
  public boolean createMarkdownCodeBlock()
  {
    return createCodeBlock.isSelected();
  }

  @Override
  public boolean useGitHubMarkdown()
  {
    return useMD.isSelected();
  }

  @Override
  public boolean includeHeaders()
  {
    return includeHeaders.isSelected();
  }

  public void saveSettings(String type)
  {
    Settings s = Settings.getInstance();
    s.setProperty("workbench." + type + ".consoletext.markdown", this.useGitHubMarkdown());
    s.setProperty("workbench." + type + ".consoletext.header", this.includeHeaders());
    s.setProperty("workbench." + type + ".consoletext.codeindent", this.createMarkdownCodeBlock());
  }

  public void restoreSettings(String type)
  {
    Settings s = Settings.getInstance();
    this.useMD.setSelected(s.getBoolProperty("workbench." + type + ".consoletext.markdown"));
    this.includeHeaders.setSelected(s.getBoolProperty("workbench." + type + ".consoletext.header"));
    this.createCodeBlock.setSelected(s.getBoolProperty("workbench." + type + ".consoletext.codeindent"));
  }

  private void checkSettings()
  {
    this.createCodeBlock.setEnabled(this.useMD.isSelected());
  }

  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  @SuppressWarnings("unchecked")
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents()
  {
    java.awt.GridBagConstraints gridBagConstraints;

    includeHeaders = new javax.swing.JCheckBox();
    useMD = new javax.swing.JCheckBox();
    createCodeBlock = new javax.swing.JCheckBox();

    setLayout(new java.awt.GridBagLayout());

    includeHeaders.setText(ResourceMgr.getString("LblExportIncludeHeaders")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.weightx = 1.0;
    add(includeHeaders, gridBagConstraints);

    useMD.setText(ResourceMgr.getString("LblExportUseMarkdown")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.weightx = 1.0;
    add(useMD, gridBagConstraints);

    createCodeBlock.setText(ResourceMgr.getString("LblExportUseCodeBlock")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    add(createCodeBlock, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JCheckBox createCodeBlock;
  private javax.swing.JCheckBox includeHeaders;
  private javax.swing.JCheckBox useMD;
  // End of variables declaration//GEN-END:variables
}
