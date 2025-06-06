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
package workbench.gui.dbobjects;

import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.Border;

import workbench.interfaces.Interruptable;
import workbench.interfaces.InterruptableJob;
import workbench.resource.ResourceMgr;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.WbButton;

import workbench.storage.RowActionMonitor;

import workbench.util.NumberStringCache;
import workbench.util.StringUtil;

/**
 *
 * @author  Thomas Kellerer
 */
public class ProgressPanel
  extends JPanel
  implements RowActionMonitor
{
  private final Interruptable task;

  private JDialog parent;
  private int monitorType = RowActionMonitor.MONITOR_PLAIN;

  public ProgressPanel(Interruptable aWorker, boolean showFilename)
  {
    super();
    task = aWorker;
    initComponents();

    Border b = BorderFactory.createCompoundBorder(WbSwingUtilities.createLineBorder(this), BorderFactory.createEmptyBorder(1, 1, 1, 1));

    fileNameField.setBorder(b);
    infoPanel.setBorder(b);

    if (!showFilename)
    {
      remove(fileNameField);
    }
    setRowSize(20);
    WbSwingUtilities.calculatePreferredSize(rowInfo, 15);
    WbSwingUtilities.calculatePreferredSize(progressInfoText, 30);
  }

  public void setParentDialog(JDialog d)
  {
    parent = d;
  }

  public void setRowInfo(long row)
  {
    if (row < 0)
    {
      rowInfo.setText("");
    }
    else
    {
      rowInfo.setText(Long.toString(row));
    }
  }

  public void setInfoText(String text)
  {
    progressInfoText.setText(text);
  }

  public void setObject(String name)
  {
    boolean changed = StringUtil.stringsAreNotEqual(name, fileNameField.getText());
    if (changed)
    {
      fileNameField.setText(name);
      updateLayout();
    }
  }

  protected void updateLayout()
  {
    FontMetrics fm = this.getFontMetrics(fileNameField.getFont());
    int width = fm.stringWidth(fileNameField.getText()) + 25;
    int h = fm.getHeight() + 2;
    Dimension d = new Dimension(width, h < 22 ? 22 : h);
    this.fileNameField.setPreferredSize(d);
    this.fileNameField.setMinimumSize(d);

    if (parent != null)
    {
      parent.invalidate();
    }

    invalidate();

    if (parent != null)
    {
      parent.validate();
      parent.pack();
    }
  }

  public void setRowSize(int cols)
  {
    FontMetrics fm = this.getFontMetrics(this.getFont());
    int w = fm.charWidth(' ');
    int h = fm.getHeight() + 2;
    Dimension d = new Dimension(w * cols, h < 22 ? 22 : h);
    this.rowInfo.setPreferredSize(d);
    this.rowInfo.setMinimumSize(d);
    updateLayout();
  }

  @Override
  public void jobFinished()
  {
  }

  @Override
  public void setCurrentObject(final String object, final long number, final long totalObjects)
  {
    final String info = NumberStringCache.getNumberString(number) + "/"+  NumberStringCache.getNumberString(totalObjects);
    WbSwingUtilities.invoke(() ->
    {
      if (monitorType == RowActionMonitor.MONITOR_EXPORT)
      {
        setRowInfo(0);
        setInfoText(ResourceMgr.getString("MsgSpoolingRow"));
        setObject(object + " [" + info + "]");
      }
      else
      {
        setInfoText(object);
        if (number >= 0 && totalObjects > 0) rowInfo.setText(info);
      }
    });
  }

  @Override
  public void setCurrentRow(long currentRow, long totalRows)
  {
    if (currentRow > -1 && totalRows > -1)
    {
      this.rowInfo.setText(NumberStringCache.getNumberString(currentRow) + "/"+  NumberStringCache.getNumberString(totalRows));
    }
    if (currentRow > -1)
    {
      this.rowInfo.setText(NumberStringCache.getNumberString(currentRow));
    }
    else
    {
      this.rowInfo.setText("");
    }
  }

  @Override
  public void saveCurrentType(String type)
  {
  }

  @Override
  public void restoreType(String type)
  {
  }

  @Override
  public void setMonitorType(int aType)
  {
    monitorType = aType;
  }

  @Override
  public int getMonitorType()
  {
    return monitorType;
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

    infoPanel = new JPanel();
    progressInfoText = new JTextField();
    rowInfo = new JLabel();
    cancelButton = new WbButton();
    fileNameField = new JTextField();

    setMinimumSize(new Dimension(250, 10));
    setLayout(new GridBagLayout());

    infoPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEtchedBorder(), BorderFactory.createEmptyBorder(1, 1, 1, 1)));
    infoPanel.setLayout(new GridBagLayout());

    progressInfoText.setEditable(false);
    progressInfoText.setBorder(null);
    progressInfoText.setDisabledTextColor(progressInfoText.getForeground());
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.weightx = 1.0;
    infoPanel.add(progressInfoText, gridBagConstraints);

    rowInfo.setHorizontalAlignment(SwingConstants.RIGHT);
    rowInfo.setMinimumSize(new Dimension(40, 20));
    rowInfo.setPreferredSize(new Dimension(40, 0));
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.LINE_END;
    gridBagConstraints.weightx = 1.0;
    infoPanel.add(rowInfo, gridBagConstraints);

    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new Insets(5, 6, 0, 6);
    add(infoPanel, gridBagConstraints);

    cancelButton.setText(ResourceMgr.getString("LblCancel"));
    cancelButton.addActionListener(new ActionListener()
    {
      public void actionPerformed(ActionEvent evt)
      {
        cancelButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = GridBagConstraints.NORTH;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new Insets(12, 0, 10, 0);
    add(cancelButton, gridBagConstraints);

    fileNameField.setEditable(false);
    fileNameField.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createEtchedBorder(), BorderFactory.createEmptyBorder(1, 1, 1, 1)));
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new Insets(4, 6, 0, 6);
    add(fileNameField, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cancelButtonActionPerformed
  {//GEN-HEADEREND:event_cancelButtonActionPerformed
    if (this.task instanceof InterruptableJob)
    {
      String msg = ResourceMgr.getString("MsgCancelAllCurrent");
      String current = ResourceMgr.getString("LblCancelCurrentExport");
      String all = ResourceMgr.getString("LblCancelAllExports");
      int answer = WbSwingUtilities.getYesNo(parent, msg, new String[] { current, all });
      InterruptableJob job = (InterruptableJob)task;
      if (answer == JOptionPane.YES_OPTION)
      {
        job.cancelCurrent();
      }
      else
      {
        job.cancelExecution();
      }
    }
    else if (task != null)
    {
      task.cancelExecution();
    }
  }//GEN-LAST:event_cancelButtonActionPerformed


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private JButton cancelButton;
  private JTextField fileNameField;
  private JPanel infoPanel;
  private JTextField progressInfoText;
  private JLabel rowInfo;
  // End of variables declaration//GEN-END:variables

}
