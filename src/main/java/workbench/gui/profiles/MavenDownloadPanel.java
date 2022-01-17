/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2022 Thomas Kellerer.
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
package workbench.gui.profiles;

import java.awt.EventQueue;
import java.io.File;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import workbench.interfaces.ValidatingComponent;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.ValidatingDialog;

import workbench.util.ClasspathUtil;
import workbench.util.WbFile;
import workbench.util.WbThread;
import workbench.util.download.MavenArtefact;
import workbench.util.download.MavenDownloader;

/**
 *
 * @author Thomas Kellerer
 */
public class MavenDownloadPanel
  extends JPanel
  implements ListSelectionListener, ValidatingComponent
{
  private static final String LAST_DIR_PROP = "workbench.driver.download.last.dir";
  private final MavenArtefact artefact;
  private MavenDownloader downloader = new MavenDownloader();
  private File downloadedFile;
  private ValidatingDialog dialog;

  public MavenDownloadPanel(String className)
  {
    initComponents();
    artefact = downloader.searchByClassName(className);
    downloader.setProgressBar(downloadProgress);
    downloadDir.setSelectDirectoryOnly(true);
    downloadDir.setAllowMultiple(false);
    File dir = getDefaultDownloadDir();
    if (dir != null)
    {
      downloadDir.setFilename(dir.getAbsolutePath());
    }
    versionList.addListSelectionListener(this);
  }

  public File getDownloadedFile()
  {
    return this.downloadedFile;
  }

  public void startRetrieveVersions()
  {
    WbThread th = new WbThread("Search Maven versions")
    {
      @Override
      public void run()
      {
        retrieveVersions();
      }
    };
    th.start();
  }

  public void retrieveVersions()
  {
    WbSwingUtilities.showWaitCursorOnWindow(this);
    try
    {
      List<MavenArtefact> versions = downloader.getAvailableVersions(this.artefact.getGroupId(), this.artefact.getArtefactId());
      final DefaultListModel<String> model = new DefaultListModel<>();
      for (MavenArtefact version : versions)
      {
        model.addElement(version.getVersion());
      }
      EventQueue.invokeLater(() -> {
        versionList.setModel(model);
        if (versions.size() > 0)
        {
          versionList.setSelectedIndex(0);
        }
      });
    }
    catch (Throwable th)
    {
    }
    finally
    {
      WbSwingUtilities.showDefaultCursorOnWindow(this);
    }
  }

  private File getDefaultDownloadDir()
  {
    String libDir = Settings.getInstance().getProperty(Settings.PROP_LIBDIR, null);
    String dir = Settings.getInstance().getProperty(LAST_DIR_PROP, libDir);
    if (dir != null)
    {
      File d = new File(dir);
      if (d.exists()) return d;
    }
    ClasspathUtil cp = new ClasspathUtil();
    return cp.getExtDir();
  }

  @Override
  public void valueChanged(ListSelectionEvent e)
  {
    boolean canDownload = false;
    File f = downloadDir.getSelectedFile();
    canDownload = f != null && f.exists();
    downloadSelected.setEnabled(versionList.getSelectedValue() != null && canDownload);
  }

  public void startDownload()
  {
    String version = versionList.getSelectedValue();
    if (version == null) return;
    cancelButton.setEnabled(true);
    WbSwingUtilities.showWaitCursorOnWindow(this);
    WbThread th = new WbThread("Download Driver")
    {
      @Override
      public void run()
      {
        downloadFile();
      }
    };
    th.start();
  }

  private void downloadFile()
  {
    long bytes = -1;
    File dir = this.downloadDir.getSelectedFile();
    try
    {
      String version = versionList.getSelectedValue();
      if (version == null) return;
      this.artefact.setVersion(version);
      bytes = downloader.download(this.artefact, dir);
    }
    catch (Exception ex)
    {
      if (!downloader.isCancelled())
      {
        WbSwingUtilities.showErrorMessage(SwingUtilities.getWindowAncestor(this), ex.getMessage());
      }
      this.downloadedFile = null;
      bytes = -1;
    }
    finally
    {
      cancelButton.setEnabled(false);
      WbSwingUtilities.showDefaultCursorOnWindow(this);
    }

    if (bytes > 0 && !downloader.isCancelled())
    {
      this.downloadedFile = new WbFile(dir, artefact.buildFilename());
      if (dialog != null)
      {
        dialog.setButtonEnabled(0, true);
      }
    }
    else
    {
      WbSwingUtilities.showMessage(SwingUtilities.getWindowAncestor(this), "Error: " + downloader.getLastHttpMsg());
    }
  }

  @Override
  public boolean validateInput()
  {
    return this.downloadedFile != null;
  }

  @Override
  public void componentDisplayed()
  {
    startRetrieveVersions();
  }

  @Override
  public void componentWillBeClosed()
  {
    File dir = downloadDir.getSelectedFile();
    if (dir != null)
    {
      Settings.getInstance().setProperty(LAST_DIR_PROP, dir.getAbsolutePath());
    }
  }

  public boolean showDialog(JDialog parent)
  {
    dialog = new ValidatingDialog(parent, ResourceMgr.getString("LblDownloadDriver"), this, true);
    dialog.setDefaultButton(0);
    dialog.setButtonEnabled(0, false);
    dialog.pack();
    WbSwingUtilities.center(dialog, parent);
    dialog.setVisible(true);
    boolean ok = !dialog.isCancelled();
    dialog.dispose();
    dialog = null;
    return ok;
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

    downloadDir = new workbench.gui.components.WbFilePicker();
    jLabel1 = new javax.swing.JLabel();
    jScrollPane1 = new javax.swing.JScrollPane();
    versionList = new javax.swing.JList<>();
    downloadProgress = new javax.swing.JProgressBar();
    jPanel1 = new javax.swing.JPanel();
    downloadSelected = new javax.swing.JButton();
    cancelButton = new javax.swing.JButton();
    jLabel2 = new javax.swing.JLabel();

    setLayout(new java.awt.GridBagLayout());
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(1, 0, 0, 0);
    add(downloadDir, gridBagConstraints);

    jLabel1.setText(ResourceMgr.getString("LblDownloadDir")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.ipadx = 6;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(1, 0, 0, 0);
    add(jLabel1, gridBagConstraints);

    versionList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
    versionList.setVisibleRowCount(10);
    jScrollPane1.setViewportView(versionList);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    add(jScrollPane1, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(2, 0, 6, 0);
    add(downloadProgress, gridBagConstraints);

    jPanel1.setLayout(new java.awt.BorderLayout());

    downloadSelected.setText(ResourceMgr.getString("LblDownloadSel")); // NOI18N
    downloadSelected.setEnabled(false);
    downloadSelected.addActionListener(new java.awt.event.ActionListener()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        downloadSelectedActionPerformed(evt);
      }
    });
    jPanel1.add(downloadSelected, java.awt.BorderLayout.WEST);

    cancelButton.setText(ResourceMgr.getString("LblCancelPlain")); // NOI18N
    cancelButton.setEnabled(false);
    cancelButton.addActionListener(new java.awt.event.ActionListener()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        cancelButtonActionPerformed(evt);
      }
    });
    jPanel1.add(cancelButton, java.awt.BorderLayout.EAST);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(12, 0, 8, 0);
    add(jPanel1, gridBagConstraints);

    jLabel2.setText(ResourceMgr.getString("LblDriverVersions")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 6, 0);
    add(jLabel2, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  private void downloadSelectedActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_downloadSelectedActionPerformed
  {//GEN-HEADEREND:event_downloadSelectedActionPerformed
    startDownload();
  }//GEN-LAST:event_downloadSelectedActionPerformed

  private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_cancelButtonActionPerformed
  {//GEN-HEADEREND:event_cancelButtonActionPerformed
    if (downloader != null)
    {
      downloader.cancelDownload();
    }
  }//GEN-LAST:event_cancelButtonActionPerformed

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JButton cancelButton;
  private workbench.gui.components.WbFilePicker downloadDir;
  private javax.swing.JProgressBar downloadProgress;
  private javax.swing.JButton downloadSelected;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel2;
  private javax.swing.JPanel jPanel1;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JList<String> versionList;
  // End of variables declaration//GEN-END:variables
}
