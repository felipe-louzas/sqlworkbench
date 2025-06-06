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

import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import workbench.interfaces.ObjectDropper;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;

import workbench.db.DbObject;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;
import workbench.db.importer.TableDependencySorter;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.DividerBorder;
import workbench.gui.components.EditWindow;
import workbench.gui.components.NoSelectionModel;
import workbench.gui.components.WbButton;
import workbench.gui.components.WbScrollPane;
import workbench.gui.components.WbStatusLabel;

import workbench.storage.RowActionMonitor;

import workbench.util.ExceptionUtil;
import workbench.util.WbThread;

/**
 *
 * @author  Thomas Kellerer
 */
public class ObjectDropperUI
  extends JPanel
  implements RowActionMonitor, WindowListener
{
  private JDialog dialog;
  private boolean cancelled;
  private boolean running;
  private boolean success;
  private final ObjectDropper dropper;
  private Thread checkThread;
  private Thread dropThread;

  public ObjectDropperUI(ObjectDropper drop)
  {
    super();
    dropper = drop;
    initComponents();
    statusLabel.setBorder(DividerBorder.TOP_BOTTOM_DIVIDER);
    if (!dropper.supportsFKSorting())
    {
      checkFKButton.setEnabled(false);
      addMissingTables.setEnabled(false);
      addMissingTables.setSelected(false);
      optionPanel.remove(checkPanel);
    }
  }

  private boolean isConnectionBusy()
  {
    if (dropper == null) return false;
    WbConnection con = dropper.getConnection();
    if (con == null) return false;
    return con.isBusy();
  }

  private void setConnectionBusy(boolean flag)
  {
    if (dropper == null) return;
    WbConnection con = dropper.getConnection();
    if (con == null) return;
    con.setBusy(flag);
  }

  protected void doDrop()
  {
    if (this.running || isConnectionBusy())
    {
      return;
    }

    success = true;
    try
    {
      setConnectionBusy(true);
      this.running = true;
      this.cancelled = false;
      this.dropper.dropObjects();
    }
    catch (Throwable ex)
    {
      final String msg = ex.getMessage();
      success = false;
      WbSwingUtilities.showErrorMessage(dialog, msg);
      LogMgr.logError(new CallerInfo(){}, "Error when dropping objects", ex);
    }
    finally
    {
      this.running = false;
      this.dropThread = null;
      setConnectionBusy(false);
    }

    EventQueue.invokeLater(() ->
    {
      if (cancelled)
      {
        dropButton.setEnabled(true);
      }
      else
      {
        dialog.setVisible(false);
        dialog.dispose();
        dialog = null;
      }
    });
  }

  public boolean success()
  {
    return success;
  }

  public boolean dialogWasCancelled()
  {
    return this.cancelled;
  }

  protected void initDisplay()
  {
    List<? extends DbObject> objects = this.dropper.getObjects();
    int numNames = objects.size();

    String[] display = new String[numNames];
    for (int i = 0; i < numNames; i++)
    {
      display[i] = objects.get(i).getObjectType() + " " + objects.get(i).getObjectNameForDrop(dropper.getConnection());
    }
    this.objectList.setListData(display);

    if (!dropper.supportsCascade())
    {
      this.optionPanel.remove(this.checkBoxCascadeConstraints);
      this.checkBoxCascadeConstraints.setSelected(false);
    }
  }

  protected void showScript()
  {
    this.dropper.setCascade(checkBoxCascadeConstraints.isSelected());
    CharSequence script = dropper.getScript();
    final EditWindow w = new EditWindow(this.dialog, ResourceMgr.getString("TxtWindowTitleGeneratedScript"), script.toString(), "workbench.objectdropper.scriptwindow", true);
    w.setVisible(true);
    w.dispose();
  }

  public void showDialog(Frame aParent)
  {
    initDisplay();

    this.dialog = new JDialog(aParent, ResourceMgr.getString("TxtDropObjectsTitle"), true);
    try
    {
      this.dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
      this.dialog.addWindowListener(this);
      this.dialog.getContentPane().add(this);
      this.dialog.pack();

      int height = dialog.getHeight();
      if (height > aParent.getHeight() * 0.8)
      {
        height = (int)(aParent.getHeight() * 0.7);
      }
      int width = dialog.getWidth();
      if (width < 200)
      {
        width = 200;
      }
      else if (this.dialog.getWidth() > aParent.getWidth() * 0.9)
      {
        width = (int)(aParent.getWidth() * 0.7);
      }
      if (width != dialog.getWidth() || height != dialog.getHeight())
      {
          this.dialog.setSize(width, height);
      }
      WbSwingUtilities.center(this.dialog, aParent);
      this.cancelled = true;
      this.dialog.setVisible(true);
    }
    finally
    {
      if (this.dialog != null)
      {
        this.dialog.dispose();
        this.dialog = null;
      }
    }
  }

  @Override
  public void windowOpened(WindowEvent e)
  {
  }

  @Override
  public void windowClosing(WindowEvent e)
  {
    cancel();
    if (dialog != null)
    {
      dialog.setVisible(false);
      dialog.dispose();
      dialog = null;
    }
  }

  @Override
  public void windowClosed(WindowEvent e)
  {
  }

  @Override
  public void windowIconified(WindowEvent e)
  {
  }

  @Override
  public void windowDeiconified(WindowEvent e)
  {
  }

  @Override
  public void windowActivated(WindowEvent e)
  {
  }

  @Override
  public void windowDeactivated(WindowEvent e)
  {
  }

  private void cancel()
  {
    if (!this.running) return;

    this.cancelled = true;
    try
    {
      statusLabel.setText(ResourceMgr.getString("MsgCancelling"));
      dropper.cancel();
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error when cancelling drop", e);
    }
    finally
    {
      statusLabel.setText("");
    }

    if (dropThread != null)
    {
      try
      {
        dropThread.interrupt();
        dropThread.join(1500);
      }
      catch (Exception e)
      {
        // ignore
      }
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

    mainPanel = new javax.swing.JPanel();
    jScrollPane1 = new WbScrollPane();
    objectList = new javax.swing.JList();
    optionPanel = new javax.swing.JPanel();
    checkPanel = new javax.swing.JPanel();
    checkFKButton = new javax.swing.JButton();
    addMissingTables = new javax.swing.JCheckBox();
    checkBoxCascadeConstraints = new javax.swing.JCheckBox();
    buttonPanel = new javax.swing.JPanel();
    showScriptButton = new javax.swing.JButton();
    dropButton = new WbButton();
    cancelButton = new WbButton();
    statusLabel = new WbStatusLabel();

    setLayout(new java.awt.GridBagLayout());

    mainPanel.setLayout(new java.awt.BorderLayout(0, 2));

    objectList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
    objectList.setSelectionModel(new NoSelectionModel());
    jScrollPane1.setViewportView(objectList);

    mainPanel.add(jScrollPane1, java.awt.BorderLayout.CENTER);

    optionPanel.setLayout(new java.awt.GridBagLayout());

    checkPanel.setLayout(new java.awt.GridBagLayout());

    checkFKButton.setText(ResourceMgr.getString("LblCheckFKDeps")); // NOI18N
    checkFKButton.setToolTipText(ResourceMgr.getDescription("LblCheckFKDeps"));
    checkFKButton.addActionListener(new java.awt.event.ActionListener()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        checkFKButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(0, 0, 6, 0);
    checkPanel.add(checkFKButton, gridBagConstraints);

    addMissingTables.setSelected(true);
    addMissingTables.setText(ResourceMgr.getString("LblIncFkTables")); // NOI18N
    addMissingTables.setToolTipText(ResourceMgr.getDescription("LblIncFkTables"));
    addMissingTables.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 5, 0, 0));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(0, 0, 6, 0);
    checkPanel.add(addMissingTables, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(2, 2, 6, 0);
    optionPanel.add(checkPanel, gridBagConstraints);

    checkBoxCascadeConstraints.setText(ResourceMgr.getString("LblCascadeConstraints")); // NOI18N
    checkBoxCascadeConstraints.setToolTipText(ResourceMgr.getDescription("LblCascadeConstraints"));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 1.0;
    optionPanel.add(checkBoxCascadeConstraints, gridBagConstraints);

    mainPanel.add(optionPanel, java.awt.BorderLayout.SOUTH);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    add(mainPanel, gridBagConstraints);

    buttonPanel.setLayout(new java.awt.GridBagLayout());

    showScriptButton.setText(ResourceMgr.getString("LblShowScript")); // NOI18N
    showScriptButton.addActionListener(new java.awt.event.ActionListener()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        showScriptButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 3, 0);
    buttonPanel.add(showScriptButton, gridBagConstraints);

    dropButton.setText(ResourceMgr.getString("LblDrop")); // NOI18N
    dropButton.addActionListener(new java.awt.event.ActionListener()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        dropButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(0, 24, 3, 6);
    buttonPanel.add(dropButton, gridBagConstraints);

    cancelButton.setText(ResourceMgr.getString("LblCancel")); // NOI18N
    cancelButton.addActionListener(new java.awt.event.ActionListener()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        cancelButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHEAST;
    gridBagConstraints.insets = new java.awt.Insets(0, 0, 3, 5);
    buttonPanel.add(cancelButton, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.SOUTHWEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 0, 0);
    add(buttonPanel, gridBagConstraints);

    statusLabel.setMinimumSize(new java.awt.Dimension(150, 24));
    statusLabel.setPreferredSize(new java.awt.Dimension(150, 24));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    add(statusLabel, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cancelButtonActionPerformed
  {//GEN-HEADEREND:event_cancelButtonActionPerformed
    this.cancelled = true;
    if (this.running)
    {
      cancel();
    }
    else
    {
      this.dialog.setVisible(false);
      this.dialog.dispose();
      this.dialog = null;
    }
  }//GEN-LAST:event_cancelButtonActionPerformed

  private void dropButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_dropButtonActionPerformed
  {//GEN-HEADEREND:event_dropButtonActionPerformed
    if (this.running)
    {
      return;
    }
    this.dropButton.setEnabled(false);
    this.dropper.setCascade(checkBoxCascadeConstraints.isSelected());
    this.dropper.setRowActionMonitor(this);

    dropThread = new WbThread("DropThread")
    {

      @Override
      public void run()
      {
        doDrop();
      }
    };
    dropThread.start();
  }//GEN-LAST:event_dropButtonActionPerformed

private void showScriptButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_showScriptButtonActionPerformed
  showScript();
}//GEN-LAST:event_showScriptButtonActionPerformed

  private void fkCheckFinished(final List<DbObject> tables)
  {
    this.checkThread = null;
    EventQueue.invokeLater(() ->
    {
      statusLabel.setText("");
      dropper.setObjects(tables);
      initDisplay();
      dropButton.setEnabled(true);
      showScriptButton.setEnabled(true);
      cancelButton.setEnabled(true);
      WbSwingUtilities.showDefaultCursor(dialog);
    });
  }

private void checkFKButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_checkFKButtonActionPerformed

  final WbConnection conn = dropper.getConnection();
  if (conn == null || conn.isBusy())
  {
    return;
  }

  this.dropButton.setEnabled(false);
  this.cancelButton.setEnabled(false);
  showScriptButton.setEnabled(false);
  this.statusLabel.setText(ResourceMgr.getString("MsgFkDeps"));

  WbSwingUtilities.showWaitCursor(dialog);

  this.checkThread = new WbThread("FKCheck")
  {
    @Override
    public void run()
    {
      List<DbObject> sorted = new ArrayList<>();
      Set<String> typesWithFKS = conn.getDbSettings().getTypesSupportingFKS();
      try
      {
        conn.setBusy(true);
        TableDependencySorter sorter = new TableDependencySorter(conn);

        // The tableDependencySorter will only accept TableIdentifier objects
        // not DbObjects, so I need to create a new list.
        // The list should not contain only TableIdentifiers anyway, otherwise
        // the ObjectDropper wouldn't (or shouldn't) support FK checking
        List<TableIdentifier> tables = new ArrayList<>();
        List<DbObject> otherObjects = new ArrayList<>();
        for (DbObject dbo : dropper.getObjects())
        {
          // the TableDependencySorter will remove non-table objects while processing the list
          // in order to not lose the user selection, we need to keep those objects separately
          if (dbo instanceof TableIdentifier && typesWithFKS.contains(dbo.getObjectType()))
          {
            tables.add((TableIdentifier) dbo);
          }
          else
          {
            otherObjects.add(dbo);
          }
        }
        List<TableIdentifier> sortedTables = sorter.sortForDelete(tables, addMissingTables.isSelected());
        sorted.addAll(otherObjects);
        sorted.addAll(sortedTables);
      }
      catch (Exception e)
      {
        LogMgr.logError(new CallerInfo(){}, "Error checking FK dependencies", e);
        WbSwingUtilities.showErrorMessage(ExceptionUtil.getDisplay(e));
        sorted = null;
      }
      finally
      {
        conn.setBusy(false);
        fkCheckFinished(sorted);
      }
    }
  };
  checkThread.start();
}//GEN-LAST:event_checkFKButtonActionPerformed
  // Variables declaration - do not modify//GEN-BEGIN:variables
  protected javax.swing.JCheckBox addMissingTables;
  protected javax.swing.JPanel buttonPanel;
  protected javax.swing.JButton cancelButton;
  protected javax.swing.JCheckBox checkBoxCascadeConstraints;
  protected javax.swing.JButton checkFKButton;
  protected javax.swing.JPanel checkPanel;
  protected javax.swing.JButton dropButton;
  protected javax.swing.JScrollPane jScrollPane1;
  protected javax.swing.JPanel mainPanel;
  protected javax.swing.JList objectList;
  protected javax.swing.JPanel optionPanel;
  protected javax.swing.JButton showScriptButton;
  protected javax.swing.JLabel statusLabel;
  // End of variables declaration//GEN-END:variables

  @Override
  public void setMonitorType(int aType)
  {
  }

  @Override
  public int getMonitorType()
  {
    return RowActionMonitor.MONITOR_PLAIN;
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
  public void setCurrentObject(String object, long number, long totalObjects)
  {
    String lbl = ResourceMgr.getFormattedString("LblDropping", object);
    statusLabel.setText(lbl);
  }

  @Override
  public void setCurrentRow(long currentRow, long totalRows)
  {
  }

  @Override
  public void jobFinished()
  {
  }

}
