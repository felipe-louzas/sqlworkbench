/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2020 Thomas Kellerer.
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
package workbench.gui.actions;

import java.awt.Color;

import javax.swing.JPanel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.gui.WbSwingUtilities;
import workbench.gui.editor.ValuesListCreator;

/**
 *
 * @author Thomas Kellerer
 */
public class ValuesCreatorParameter
  extends JPanel
  implements DocumentListener
{
  private final String delimiterProp = "workbench.gui.values.creator.delimiter";
  private final String regexProp = "workbench.gui.values.creator.regex";
  private final String emptyStringProp = "workbench.gui.values.creator.emptystring.null";
  private final String trimSepProp = "workbench.gui.values.creator.trim.delimiter";

  private String input;

  public ValuesCreatorParameter(String text)
  {
    initComponents();
    restoreSettings();
    this.input = text;
    this.previewArea.setFont(Settings.getInstance().getEditorFont());
    this.previewArea.setText(input);
    Color bg = Settings.getInstance().getEditorBackgroundColor();
    if (bg == null)
    {
      bg = this.previewArea.getBackground();
    }
    this.previewArea.setEditable(false);
    // Setting the background must be done after turning off the editable flag,
    // otherwise the edit area will be shown "disabled" with a gray background
    this.previewArea.setBackground(bg);
    this.previewArea.setForeground(Settings.getInstance().getEditorTextColor());
    this.previewArea.setTabSize(Settings.getInstance().getEditorTabWidth());
    this.delimiter.getDocument().addDocumentListener(this);
    WbSwingUtilities.invokeLater(this::preview);
  }

  public String getDelimiter()
  {
    return delimiter.getText();
  }

  public boolean isRegex()
  {
    return isRegex.isSelected();
  }

  public boolean getEmptyStringIsNull()
  {
    return emptyString.isSelected();
  }

  public boolean getTrimDelimiter()
  {
    return trimDelimiter.isSelected();
  }

  public void setFocusToInput()
  {
    this.delimiter.requestFocus();
    this.delimiter.selectAll();
  }

  public void restoreSettings()
  {
    String delim = Settings.getInstance().getProperty(delimiterProp, null);
    delimiter.setText(delim);
    isRegex.setSelected(Settings.getInstance().getBoolProperty(regexProp, false));
    emptyString.setSelected(Settings.getInstance().getBoolProperty(emptyStringProp, false));
    trimDelimiter.setSelected(Settings.getInstance().getBoolProperty(trimSepProp, true));
  }

  public void saveSettings()
  {
    Settings.getInstance().setProperty(delimiterProp, getDelimiter());
    Settings.getInstance().setProperty(regexProp, isRegex());
    Settings.getInstance().setProperty(emptyStringProp, getEmptyStringIsNull());
    Settings.getInstance().setProperty(trimSepProp, getTrimDelimiter());
  }

  public void preview()
  {
    ValuesListCreator creator = new ValuesListCreator(input, getDelimiter(), isRegex());
    creator.setEmptyStringIsNull(getEmptyStringIsNull());
    creator.setTrimDelimiter(getTrimDelimiter());
    creator.setLineEnding("\n");
    previewArea.setText(creator.createValuesList());
  }

  @Override
  public void insertUpdate(DocumentEvent e)
  {
    preview();
  }

  @Override
  public void removeUpdate(DocumentEvent e)
  {
    preview();
  }

  @Override
  public void changedUpdate(DocumentEvent e)
  {
    preview();
  }


  @SuppressWarnings("unchecked")
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents()
  {
    java.awt.GridBagConstraints gridBagConstraints;

    jLabel1 = new javax.swing.JLabel();
    delimiter = new javax.swing.JTextField();
    isRegex = new javax.swing.JCheckBox();
    emptyString = new javax.swing.JCheckBox();
    trimDelimiter = new javax.swing.JCheckBox();
    jScrollPane1 = new javax.swing.JScrollPane();
    previewArea = new javax.swing.JTextArea();
    jSeparator1 = new javax.swing.JSeparator();
    previewButton = new javax.swing.JButton();

    setLayout(new java.awt.GridBagLayout());

    jLabel1.setText(ResourceMgr.getString("LblFieldDelimiter")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    add(jLabel1, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(0, 12, 0, 0);
    add(delimiter, gridBagConstraints);

    isRegex.setText(ResourceMgr.getString("LblDelimIsRegex")); // NOI18N
    isRegex.setBorder(null);
    isRegex.addActionListener(new java.awt.event.ActionListener()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        isRegexActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 0, 0);
    add(isRegex, gridBagConstraints);

    emptyString.setText(ResourceMgr.getString("LblEmptyStringIsNull")); // NOI18N
    emptyString.setBorder(null);
    emptyString.addActionListener(new java.awt.event.ActionListener()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        emptyStringActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 0, 0);
    add(emptyString, gridBagConstraints);

    trimDelimiter.setText(ResourceMgr.getString("LblTrimSeparator")); // NOI18N
    trimDelimiter.setBorder(null);
    trimDelimiter.addActionListener(new java.awt.event.ActionListener()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        trimDelimiterActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 0, 0);
    add(trimDelimiter, gridBagConstraints);

    previewArea.setColumns(60);
    previewArea.setRows(10);
    jScrollPane1.setViewportView(previewArea);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 0, 0);
    add(jScrollPane1, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(12, 0, 0, 0);
    add(jSeparator1, gridBagConstraints);

    previewButton.setText(ResourceMgr.getString("LblPreview")); // NOI18N
    previewButton.addActionListener(new java.awt.event.ActionListener()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        previewButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 0, 0);
    add(previewButton, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  private void previewButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_previewButtonActionPerformed
  {//GEN-HEADEREND:event_previewButtonActionPerformed
    preview();
  }//GEN-LAST:event_previewButtonActionPerformed

  private void isRegexActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_isRegexActionPerformed
  {//GEN-HEADEREND:event_isRegexActionPerformed
    preview();
  }//GEN-LAST:event_isRegexActionPerformed

  private void emptyStringActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_emptyStringActionPerformed
  {//GEN-HEADEREND:event_emptyStringActionPerformed
    preview();
  }//GEN-LAST:event_emptyStringActionPerformed

  private void trimDelimiterActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_trimDelimiterActionPerformed
  {//GEN-HEADEREND:event_trimDelimiterActionPerformed
    preview();
  }//GEN-LAST:event_trimDelimiterActionPerformed

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTextField delimiter;
  private javax.swing.JCheckBox emptyString;
  private javax.swing.JCheckBox isRegex;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JSeparator jSeparator1;
  private javax.swing.JTextArea previewArea;
  private javax.swing.JButton previewButton;
  private javax.swing.JCheckBox trimDelimiter;
  // End of variables declaration//GEN-END:variables
}
