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
package workbench.gui.components;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.Serializable;

import javax.swing.JButton;
import javax.swing.JColorChooser;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import workbench.resource.IconMgr;
import workbench.resource.ResourceMgr;

import workbench.gui.WbSwingUtilities;


/**
 *
 * @author Thomas Kellerer
 */
public class WbColorPicker
  extends JPanel
  implements Serializable
{
  private ActionListener actionListener;

  public WbColorPicker()
  {
    this(false);
  }

  public WbColorPicker(boolean showReset)
  {
    super();
    initComponents();
    this.resetButton.setEnabled(showReset);
    this.resetButton.setVisible(showReset);
    int iconSize = IconMgr.getInstance().getSizeForLabel();
    int buttonSize = (int)(iconSize * 1.5);

    if (showReset)
    {
      resetButton.setIcon(IconMgr.getInstance().getLabelIcon("delete"));
    }
    this.defaultLabel.setVisible(false);
    WbSwingUtilities.adjustButtonWidth(selectColor, buttonSize, buttonSize);
    resetButton.setPreferredSize(selectColor.getPreferredSize());
    resetButton.setSize(selectColor.getSize());

    Dimension d = new Dimension((int)(buttonSize * 0.9), (int)(buttonSize * 0.9));
    setButtonSize(sampleColor, d);
    sampleColor.setOpaque(true);
    sampleColor.setBorder(WbSwingUtilities.createLineBorder(this));
  }

  private void setButtonSize(JComponent button, Dimension d)
  {
    button.setPreferredSize(d);
    button.setMaximumSize(d);
    button.setMinimumSize(d);
  }

  /**
   * Define the displayed label when no color is selected by supplying
   * the resource key.
   *
   * @param key
   */
  public void setDefaultLabelKey(String key)
  {
    defaultLabel.setText(ResourceMgr.getString(key));
  }

  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents()
  {

    infoPanel = new JPanel();
    sampleColor = new JLabel();
    resetButton = new FlatButton();
    selectColor = new FlatButton();
    defaultLabel = new JLabel();

    setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));

    infoPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 0));

    sampleColor.setMinimumSize(new Dimension(16, 16));
    sampleColor.setPreferredSize(new Dimension(16, 16));
    infoPanel.add(sampleColor);

    resetButton.setToolTipText(ResourceMgr.getDescription("LblResetColor"));
    resetButton.setMaximumSize(null);
    resetButton.setMinimumSize(null);
    resetButton.setPreferredSize(null);
    resetButton.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent evt)
      {
        resetButtonActionPerformed(evt);
      }
    });
    infoPanel.add(resetButton);

    selectColor.setText("...");
    selectColor.setMargin(new Insets(0, 0, 0, 0));
    selectColor.setMaximumSize(null);
    selectColor.setMinimumSize(null);
    selectColor.setPreferredSize(null);
    selectColor.addMouseListener(new MouseAdapter()
    {
      public void mouseClicked(MouseEvent evt)
      {
        selectColorMouseClicked(evt);
      }
    });
    infoPanel.add(selectColor);

    defaultLabel.setText(ResourceMgr.getString("LblNone")); // NOI18N
    infoPanel.add(defaultLabel);

    add(infoPanel);
  }// </editor-fold>//GEN-END:initComponents

  private void selectColorMouseClicked(java.awt.event.MouseEvent evt)//GEN-FIRST:event_selectColorMouseClicked
  {//GEN-HEADEREND:event_selectColorMouseClicked
    Color newColor = JColorChooser.showDialog(SwingUtilities.getWindowAncestor(this),
      ResourceMgr.getString("TxtSelectColor"), this.getSelectedColor());
    if (newColor != null)
    {
      this.setSelectedColor(newColor);
      fireColorChanged();
    }
  }//GEN-LAST:event_selectColorMouseClicked

  private void resetButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetButtonActionPerformed
    resetColor();
    fireColorChanged();
  }//GEN-LAST:event_resetButtonActionPerformed

  private void fireColorChanged()
  {
    if (this.actionListener != null)
    {
      this.actionListener.actionPerformed(new ActionEvent(this, 1,"color-changed"));
    }
  }

  public void setActionListener(ActionListener l)
  {
    this.actionListener = l;
  }

  private void resetColor()
  {
    resetButton.setEnabled(false);
    sampleColor.setBackground(this.getBackground());
    defaultLabel.setVisible(true);
  }

  public void setSelectedColor(Color c)
  {
    if (c == null)
    {
      resetColor();
    }
    else
    {
      resetButton.setEnabled(true);
      defaultLabel.setVisible(false);
      sampleColor.setBackground(c);
    }
  }

  public Color getSelectedColor()
  {
    if (this.defaultLabel.isVisible())
    {
      return null;
    }
    else
    {
      return sampleColor.getBackground();
    }
  }
  // Variables declaration - do not modify//GEN-BEGIN:variables
  private JLabel defaultLabel;
  private JPanel infoPanel;
  private JButton resetButton;
  private JLabel sampleColor;
  private JButton selectColor;
  // End of variables declaration//GEN-END:variables
}
