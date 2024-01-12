/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2024 Thomas Kellerer
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

import java.awt.Color;

import javax.swing.JPanel;

import workbench.interfaces.Restoreable;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.TextFieldWidthAdjuster;
import workbench.gui.components.WbColorPicker;

import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class HighlightSettingsPanel
  extends JPanel
  implements Restoreable
{

  public HighlightSettingsPanel()
  {
    initComponents();
    jPanel1.setBorder(WbSwingUtilities.createLineBorder(this));
    jPanel2.setBorder(WbSwingUtilities.createLineBorder(this));
    TextFieldWidthAdjuster adjuster = new TextFieldWidthAdjuster();
    adjuster.adjustField(selMinLength);
  }

  @Override
  public void restoreSettings()
  {
    Settings sett = Settings.getInstance();
    Color hilite = sett.getEditorBracketHighlightColor();
    enableHilite.setSelected(sett.isBracketHighlightEnabled());
    bracketHilite.setSelectedColor(hilite);
    matchLeft.setSelected(sett.getBracketHighlightLeft());
    matchRight.setSelected(!sett.getBracketHighlightLeft());
    hiliteRec.setSelected(sett.getBracketHighlightRectangle());
    hiliteBoth.setSelected(sett.getBracketHighlightBoth());
    hiliteMatching.setSelected(!sett.getBracketHighlightBoth());
    enableSelHilite.setSelected(sett.getHighlightCurrentSelection());
    noWhitespace.setSelected(sett.getSelectionHighlightNoWhitespace());
    selMinLength.setText(Integer.toString(sett.getMinLengthForSelectionHighlight()));
    selHiliteColor.setSelectedColor(sett.geSelectionHighlightColor());
    ignoreCase.setSelected(sett.getSelectionHighlightIgnoreCase());
    minDistance.setText(Integer.toString(sett.getMinDistanceForBracketHighlight()));
  }

  @Override
  public void saveSettings()
  {
    Settings sett = Settings.getInstance();
    sett.setColor(Settings.PROPERTY_EDITOR_BRACKET_HILITE_COLOR, bracketHilite.getSelectedColor());
    sett.setBracketHighlight(enableHilite.isSelected());
    sett.setBracketHighlightLeft(matchLeft.isSelected());
    sett.setBracketHighlightRectangle(hiliteRec.isSelected());
    sett.setBracketHighlightBoth(hiliteBoth.isSelected());
    sett.setSelectionHighlightColor(selHiliteColor.getSelectedColor());
    sett.setSelectionHighlightNoWhitespace(noWhitespace.isSelected());
    sett.setHighlightCurrentSelection(enableSelHilite.isSelected());
    sett.setSelectionHighlightIgnoreCase(ignoreCase.isSelected());
    int minLength = StringUtil.getIntValue(selMinLength.getText(), Integer.MIN_VALUE);
    if (minLength != Integer.MIN_VALUE)
    {
      sett.setMinLengthForSelectionHighlight(minLength);
    }
    int minDist = StringUtil.getIntValue(minDistance.getText(), Integer.MIN_VALUE);
    if (minDist != Integer.MIN_VALUE)
    {
      sett.setMinDistanceForBracketHighlight(minDist);
    }
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

    matchType = new javax.swing.ButtonGroup();
    hiliteType = new javax.swing.ButtonGroup();
    enableHilite = new javax.swing.JCheckBox();
    jPanel1 = new javax.swing.JPanel();
    matchLeft = new javax.swing.JRadioButton();
    matchRight = new javax.swing.JRadioButton();
    jPanel2 = new javax.swing.JPanel();
    hiliteBoth = new javax.swing.JRadioButton();
    hiliteMatching = new javax.swing.JRadioButton();
    jPanel3 = new javax.swing.JPanel();
    bracketHiliteLabel = new javax.swing.JLabel();
    hiliteRec = new javax.swing.JCheckBox();
    bracketHilite = new WbColorPicker(true);
    jSeparator1 = new javax.swing.JSeparator();
    jPanel4 = new javax.swing.JPanel();
    enableSelHilite = new javax.swing.JCheckBox();
    jLabel1 = new javax.swing.JLabel();
    selMinLength = new javax.swing.JTextField();
    selHiliteColor = new WbColorPicker(true);
    jLabel2 = new javax.swing.JLabel();
    ignoreCase = new javax.swing.JCheckBox();
    noWhitespace = new javax.swing.JCheckBox();
    jPanel5 = new javax.swing.JPanel();
    jLabel3 = new javax.swing.JLabel();
    minDistance = new javax.swing.JTextField();

    setLayout(new java.awt.GridBagLayout());

    enableHilite.setText(ResourceMgr.getString("LblBracketHilite")); // NOI18N
    enableHilite.setToolTipText(ResourceMgr.getString("d_LblBracketHilite")); // NOI18N
    enableHilite.setBorder(null);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 0);
    add(enableHilite, gridBagConstraints);

    jPanel1.setBorder(javax.swing.BorderFactory.createEtchedBorder());
    jPanel1.setLayout(new java.awt.GridBagLayout());

    matchType.add(matchLeft);
    matchLeft.setText(ResourceMgr.getString("LblBracketLeftOfCursor")); // NOI18N
    matchLeft.setToolTipText(ResourceMgr.getString("d_LblBracketLeftOfCursor")); // NOI18N
    matchLeft.setBorder(null);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
    jPanel1.add(matchLeft, gridBagConstraints);

    matchType.add(matchRight);
    matchRight.setText(ResourceMgr.getString("LblBracketRightOfCursor")); // NOI18N
    matchRight.setToolTipText(ResourceMgr.getString("d_LblBracketRightOfCursor")); // NOI18N
    matchRight.setBorder(null);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
    jPanel1.add(matchRight, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 5, 0);
    add(jPanel1, gridBagConstraints);

    jPanel2.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(153, 153, 153)));
    jPanel2.setLayout(new java.awt.GridBagLayout());

    hiliteType.add(hiliteBoth);
    hiliteBoth.setText(ResourceMgr.getString("LblBracketHiliteBoth")); // NOI18N
    hiliteBoth.setToolTipText(ResourceMgr.getString("d_LblBracketHiliteBoth")); // NOI18N
    hiliteBoth.setBorder(null);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
    jPanel2.add(hiliteBoth, gridBagConstraints);

    hiliteType.add(hiliteMatching);
    hiliteMatching.setText(ResourceMgr.getString("LblBracketHiliteMatching")); // NOI18N
    hiliteMatching.setToolTipText(ResourceMgr.getString("d_LblBracketHiliteMatching")); // NOI18N
    hiliteMatching.setBorder(null);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
    jPanel2.add(hiliteMatching, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(6, 10, 5, 0);
    add(jPanel2, gridBagConstraints);

    jPanel3.setLayout(new java.awt.GridBagLayout());

    bracketHiliteLabel.setText(ResourceMgr.getString("LblBracketHiliteColor")); // NOI18N
    bracketHiliteLabel.setToolTipText(ResourceMgr.getString("d_LblBracketHiliteColor")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(4, 1, 0, 0);
    jPanel3.add(bracketHiliteLabel, gridBagConstraints);

    hiliteRec.setText(ResourceMgr.getString("LblBracketHiliteRec")); // NOI18N
    hiliteRec.setToolTipText(ResourceMgr.getString("d_LblBracketHiliteRec")); // NOI18N
    hiliteRec.setBorder(null);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 2, 0, 0);
    jPanel3.add(hiliteRec, gridBagConstraints);

    bracketHilite.setToolTipText(ResourceMgr.getString("d_LblBracketHiliteColor")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 0, 0);
    jPanel3.add(bracketHilite, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(4, 0, 0, 0);
    add(jPanel3, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(11, 0, 0, 0);
    add(jSeparator1, gridBagConstraints);

    jPanel4.setLayout(new java.awt.GridBagLayout());

    enableSelHilite.setText(ResourceMgr.getString("LblHiliteSel")); // NOI18N
    enableSelHilite.setBorder(null);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
    jPanel4.add(enableSelHilite, gridBagConstraints);

    jLabel1.setText(ResourceMgr.getString("LblHiliteSelMinLen")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 0, 0);
    jPanel4.add(jLabel1, gridBagConstraints);

    selMinLength.setColumns(5);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(4, 7, 0, 0);
    jPanel4.add(selMinLength, gridBagConstraints);

    selHiliteColor.setToolTipText(ResourceMgr.getString("d_LblBracketHiliteColor")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 0, 0);
    jPanel4.add(selHiliteColor, gridBagConstraints);

    jLabel2.setText(ResourceMgr.getString("LblHiliteSelColor")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(6, 9, 0, 0);
    jPanel4.add(jLabel2, gridBagConstraints);

    ignoreCase.setText(ResourceMgr.getString("LblSearchIgnoreCase")); // NOI18N
    ignoreCase.setBorder(null);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
    jPanel4.add(ignoreCase, gridBagConstraints);

    noWhitespace.setText(ResourceMgr.getString("LblHiliteNoSpc")); // NOI18N
    noWhitespace.setBorder(null);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
    jPanel4.add(noWhitespace, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 0, 0);
    add(jPanel4, gridBagConstraints);

    jPanel5.setLayout(new java.awt.GridBagLayout());

    jLabel3.setText(ResourceMgr.getString("LblMinDist")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    jPanel5.add(jLabel3, gridBagConstraints);

    minDistance.setColumns(3);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(0, 11, 0, 0);
    jPanel5.add(minDistance, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.weightx = 1.0;
    add(jPanel5, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private workbench.gui.components.WbColorPicker bracketHilite;
  private javax.swing.JLabel bracketHiliteLabel;
  private javax.swing.JCheckBox enableHilite;
  private javax.swing.JCheckBox enableSelHilite;
  private javax.swing.JRadioButton hiliteBoth;
  private javax.swing.JRadioButton hiliteMatching;
  private javax.swing.JCheckBox hiliteRec;
  private javax.swing.ButtonGroup hiliteType;
  private javax.swing.JCheckBox ignoreCase;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel2;
  private javax.swing.JLabel jLabel3;
  private javax.swing.JPanel jPanel1;
  private javax.swing.JPanel jPanel2;
  private javax.swing.JPanel jPanel3;
  private javax.swing.JPanel jPanel4;
  private javax.swing.JPanel jPanel5;
  private javax.swing.JSeparator jSeparator1;
  private javax.swing.JRadioButton matchLeft;
  private javax.swing.JRadioButton matchRight;
  private javax.swing.ButtonGroup matchType;
  private javax.swing.JTextField minDistance;
  private javax.swing.JCheckBox noWhitespace;
  private workbench.gui.components.WbColorPicker selHiliteColor;
  private javax.swing.JTextField selMinLength;
  // End of variables declaration//GEN-END:variables

}
