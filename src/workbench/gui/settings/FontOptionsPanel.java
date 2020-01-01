/*
 * FontOptionsPanel.java
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

import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import workbench.interfaces.Restoreable;
import workbench.resource.GuiSettings;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.gui.components.WbFontPicker;

import workbench.util.PlatformHelper;

/**
 *
 * @author Thomas Kellerer
 */
public class FontOptionsPanel
  extends JPanel
  implements Restoreable
{

  public FontOptionsPanel()
  {
    initComponents();
    standardFont.setAllowFontReset(true);
    editorFont.setListMonospacedOnly(true);
    editorFont.setAllowFontReset(true);
    dataFont.setAllowFontReset(true);
    msgLogFont.setAllowFontReset(true);
    if (!PlatformHelper.isWindows())
    {
      scaleFonts.setVisible(false);
      scaleFonts.setEnabled(false);
    }
  }

  @Override
  public void restoreSettings()
  {
    editorFont.setSelectedFont(Settings.getInstance().getEditorFont(false));
    dataFont.setSelectedFont(Settings.getInstance().getDataFont());
    msgLogFont.setSelectedFont(Settings.getInstance().getMsgLogFont());
    standardFont.setSelectedFont(Settings.getInstance().getStandardFont());
    wheelZoom.setSelected(GuiSettings.getZoomFontWithMouseWheel());
    if (scaleFonts.isVisible())
    {
      scaleFonts.setSelected(Settings.getInstance().getScaleFonts());
    }
  }

  @Override
  public void saveSettings()
  {
    Settings.getInstance().setEditorFont(editorFont.getSelectedFont());
    Settings.getInstance().setDataFont(dataFont.getSelectedFont());
    Settings.getInstance().setStandardFont(standardFont.getSelectedFont());
    Settings.getInstance().setMsgLogFont(msgLogFont.getSelectedFont());
    if (scaleFonts.isVisible())
    {
      Settings.getInstance().setScaleFonts(scaleFonts.isSelected());
    }
    GuiSettings.setZoomFontWithMouseWheel(wheelZoom.isSelected());
  }

  /** This method is called from within the constructor to  initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  @SuppressWarnings("unchecked")
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents()
  {
    GridBagConstraints gridBagConstraints;

    dataFontLabel = new JLabel();
    dataFont = new WbFontPicker();
    standardFont = new WbFontPicker();
    standardFontLabel = new JLabel();
    msgFontLabel = new JLabel();
    msgLogFont = new WbFontPicker();
    editorFont = new WbFontPicker();
    editorFontLabel = new JLabel();
    scaleFonts = new JCheckBox();
    jPanel1 = new JPanel();
    wheelZoom = new JCheckBox();

    setLayout(new GridBagLayout());

    dataFontLabel.setText(ResourceMgr.getString("LblDataFont")); // NOI18N
    dataFontLabel.setToolTipText(ResourceMgr.getString("d_LblDataFont")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 1, 5, 0);
    add(dataFontLabel, gridBagConstraints);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new Insets(0, 8, 5, 15);
    add(dataFont, gridBagConstraints);

    standardFont.setFont(standardFont.getFont());
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new Insets(0, 8, 5, 15);
    add(standardFont, gridBagConstraints);

    standardFontLabel.setText(ResourceMgr.getString("LblStandardFont")); // NOI18N
    standardFontLabel.setToolTipText(ResourceMgr.getString("d_LblStandardFont")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 1, 5, 0);
    add(standardFontLabel, gridBagConstraints);

    msgFontLabel.setText(ResourceMgr.getString("LblMsgLogFont")); // NOI18N
    msgFontLabel.setToolTipText(ResourceMgr.getString("d_LblMsgLogFont")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 1, 5, 0);
    add(msgFontLabel, gridBagConstraints);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new Insets(0, 8, 5, 15);
    add(msgLogFont, gridBagConstraints);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new Insets(3, 8, 5, 15);
    add(editorFont, gridBagConstraints);

    editorFontLabel.setText(ResourceMgr.getString("LblEditorFont")); // NOI18N
    editorFontLabel.setToolTipText(ResourceMgr.getString("d_LblEditorFont")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(3, 1, 5, 0);
    add(editorFontLabel, gridBagConstraints);

    scaleFonts.setText(ResourceMgr.getString("LblScaleFont")); // NOI18N
    scaleFonts.setToolTipText(ResourceMgr.getString("d_LblScaleFont")); // NOI18N
    scaleFonts.setBorder(null);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(7, 1, 5, 0);
    add(scaleFonts, gridBagConstraints);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.weighty = 1.0;
    add(jPanel1, gridBagConstraints);

    wheelZoom.setText(ResourceMgr.getString("LblEnableWheelZoom")); // NOI18N
    wheelZoom.setBorder(null);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(7, 1, 5, 0);
    add(wheelZoom, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private WbFontPicker dataFont;
  private JLabel dataFontLabel;
  private WbFontPicker editorFont;
  private JLabel editorFontLabel;
  private JPanel jPanel1;
  private JLabel msgFontLabel;
  private WbFontPicker msgLogFont;
  private JCheckBox scaleFonts;
  private WbFontPicker standardFont;
  private JLabel standardFontLabel;
  private JCheckBox wheelZoom;
  // End of variables declaration//GEN-END:variables
}
