/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2022, Thomas Kellerer
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
package workbench.gui.profiles;

import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.sql.Driver;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.DefaultListModel;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import workbench.interfaces.Validator;
import workbench.resource.IconMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.DbDriver;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.ClassFinderGUI;
import workbench.gui.components.FeedbackWindow;
import workbench.gui.components.FlatButton;
import workbench.gui.components.TextComponentMouseListener;
import workbench.gui.components.WbFileChooser;
import workbench.gui.components.WbStatusLabel;

import workbench.util.ClassFinder;
import workbench.util.ClasspathUtil;
import workbench.util.CollectionUtil;
import workbench.util.StringUtil;
import workbench.util.WbFile;
import workbench.util.download.MavenArtefact;
import workbench.util.download.MavenDownloader;

/**
 *
 * @author  Thomas Kellerer
 */
public class DriverEditorPanel
  extends JPanel
  implements DocumentListener, ActionListener
{
  private static final String LAST_DIR_PROP = "workbench.driver.download.last.dir";
  private DbDriver currentDriver;
  private Validator validator;
  private GridBagConstraints defaultErrorConstraints;
  private JLabel errorLabel;
  private final MavenDownloader mavenDownloader = new MavenDownloader();
  private FeedbackWindow downloadWindow;

  public DriverEditorPanel()
  {
    super();
    initComponents();
    statusLabel.setText("");
    statusLabel.setBorder(new EmptyBorder(0,0,0,0));
    defaultErrorConstraints = new GridBagConstraints();
    defaultErrorConstraints.gridx = 0;
    defaultErrorConstraints.gridy = 0;
    defaultErrorConstraints.gridwidth = GridBagConstraints.REMAINDER;
    defaultErrorConstraints.fill = GridBagConstraints.HORIZONTAL;
    defaultErrorConstraints.ipadx = 0;
    defaultErrorConstraints.ipady = 0;
    defaultErrorConstraints.anchor = java.awt.GridBagConstraints.WEST;
    defaultErrorConstraints.insets = new java.awt.Insets(15, 8, 0, 3);

    errorLabel = new JLabel(ResourceMgr.getString("ErrDrvNameNotUnique"));
    Border b = new CompoundBorder(new LineBorder(Color.RED.brighter(), 1), new EmptyBorder(3, 5, 3, 5));
    errorLabel.setBorder(b);
    errorLabel.setFont(errorLabel.getFont().deriveFont(Font.BOLD));
    errorLabel.setBackground(new Color(255, 255, 220));
    errorLabel.setOpaque(true);

    tfName.getDocument().addDocumentListener(this);
    classpathEditor.setLastDirProperty("workbench.drivers.lastlibdir");
    classpathEditor.addActionListener(this);
    WbSwingUtilities.setMinimumSize(tfName, 40);
    WbSwingUtilities.setMinimumSize(tfClassName, 50);
  }

  public void setValidator(Validator nameValidator)
  {
    this.validator = nameValidator;
  }

  @Override
  public void actionPerformed(ActionEvent e)
  {
    selectClass();
  }

  protected void selectClass()
  {
    ClassFinder finder = new ClassFinder(Driver.class);

    // Ignore deprecated drivers
    List<String> classes = Settings.getInstance().getListProperty("workbench.db.drivers.deprecated", false);
    finder.setExcludedClasses(classes);

    List<String> libs = classpathEditor.getRealJarPaths();
    detectDriverButton.setEnabled(CollectionUtil.isNonEmpty(libs));

    ClassFinderGUI gui = new ClassFinderGUI(finder, tfClassName, statusLabel);
    gui.setStatusBarKey("TxtSearchingDriver");
    gui.setWindowTitleKey("TxtSelectDriver");
    gui.setClassPath(libs.stream().map(fname -> new File(fname)).collect(Collectors.toList()));
    gui.startCheck();
  }

  public boolean validateName()
  {
    boolean valid = false;
    if (validator.isValid(tfName.getText()))
    {
      this.remove(errorLabel);
      valid = true;
    }
    else
    {
      this.add(errorLabel, defaultErrorConstraints);
    }
    this.doLayout();
    this.validate();
    return valid;
  }

  @Override
  public void insertUpdate(DocumentEvent e)
  {
    validateName();
  }

  @Override
  public void removeUpdate(DocumentEvent e)
  {
    validateName();
  }

  @Override
  public void changedUpdate(DocumentEvent e)
  {
    validateName();
  }

  public String getCurrentName()
  {
    return tfName.getText().trim();
  }

  public void setDriver(DbDriver driver)
  {
    this.currentDriver = driver;
    this.tfName.setText(driver.getName());
    this.tfClassName.setText(driver.getDriverClass());
    List<String> libraryList = driver.getLibraryList();
    DefaultListModel model = new DefaultListModel();
    for (String lib : libraryList)
    {
      model.addElement(new LibraryElement(lib));
    }
    classpathEditor.setLibraries(libraryList);
    classpathEditor.setFileSelectionEnabled(!getCurrentName().equals("sun.jdbc.odbc.JdbcOdbcDriver"));
    this.tfSampleUrl.setText(driver.getSampleUrl());
    this.detectDriverButton.setEnabled(classpathEditor.hasLibraries());
    checkDownload();
  }

  public void updateDriver()
  {
    this.currentDriver.setName(tfName.getText().trim());
    this.currentDriver.setDriverClass(tfClassName.getText().trim());
    this.currentDriver.setLibraryList(classpathEditor.getLibraries());
    this.currentDriver.setSampleUrl(tfSampleUrl.getText());
    checkDownload();
  }

  public DbDriver getDriver()
  {
    this.updateDriver();
    return this.currentDriver;
  }

  public void reset()
  {
    this.currentDriver = null;
    this.tfName.setText("");
    this.tfClassName.setText("");
    this.classpathEditor.reset();
    this.tfSampleUrl.setText("");
    checkDownload();
  }

  private void setDownloadEnabled(boolean flag)
  {
    //this.downloadButton.setVisible(flag);
    this.downloadButton.setEnabled(flag);
  }

  private void checkDownload()
  {
    if (this.currentDriver == null)
    {
      setDownloadEnabled(false);
      return;
    }

    MavenArtefact artefact = mavenDownloader.searchByClassName(currentDriver.getDriverClass());
    setDownloadEnabled(artefact != null);
  }

  private void downloadDriver()
  {
    JDialog dialog = (JDialog)SwingUtilities.getWindowAncestor(this);
    downloadWindow = new FeedbackWindow(dialog, ResourceMgr.getString("MsgSearchMaven"));

    WbSwingUtilities.center(downloadWindow, dialog);
    WbSwingUtilities.showWaitCursor(downloadWindow);
    downloadWindow.showAndStart(this::searchArtefact);
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

  private void searchArtefact()
  {
    final MavenArtefact artefact = mavenDownloader.searchByClassName(currentDriver.getDriverClass());
    String version = mavenDownloader.searchForLatestVersion(artefact.getGroupId(), artefact.getArtefactId());
    WbSwingUtilities.showDefaultCursor(downloadWindow);

    if (StringUtil.isBlank(version))
    {
      String error = mavenDownloader.getLastHttpMsg();
      String msg = ResourceMgr.getString("MsgMavenNoDriver");
      if (StringUtil.isNonBlank(error))
      {
        msg = "<html>" + msg + "<br>(" + error + ")</html>";
      }
      WbSwingUtilities.showErrorMessage(msg);
      return;
    }

    artefact.setVersion(version);
    if (downloadWindow != null)
    {
      downloadWindow.setVisible(false);
      downloadWindow.dispose();
      downloadWindow = null;
    }
    JDialog dialog = (JDialog)SwingUtilities.getWindowAncestor(this);
    String msg = ResourceMgr.getFormattedString("MsgDownloadDriver", artefact.buildFilename());
    boolean ok = WbSwingUtilities.getYesNo(dialog, msg);
    if (ok)
    {
      WbFileChooser fc = new WbFileChooser();
      fc.setCurrentDirectory(getDefaultDownloadDir());
      fc.setDialogTitle("Select download directory");
      fc.setDialogType(JFileChooser.SAVE_DIALOG);
      fc.setMultiSelectionEnabled(false);
      fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      int choice = fc.showOpenDialog(dialog);
      if (choice == JFileChooser.APPROVE_OPTION)
      {
        final File dir = fc.getSelectedFile();
        msg = ResourceMgr.getFormattedString("MsgDownloadingFile", artefact.buildFilename(), dir);
        ActionListener l = (ActionEvent e) ->
        {
          mavenDownloader.cancelDownload();
        };
        downloadWindow = new FeedbackWindow(dialog, msg, l, "LblCancelPlain", true);
        mavenDownloader.setProgressBar(downloadWindow.getProgressBar());
        WbSwingUtilities.center(downloadWindow, dialog);
        downloadWindow.showAndStart(() ->
        {
          downloadFile(artefact, dir);
        });
      }
    }
  }

  private void downloadFile(MavenArtefact artefact, File downloadDir)
  {
    long bytes = mavenDownloader.download(artefact, downloadDir);
    WbSwingUtilities.showDefaultCursor(this);
    if (downloadWindow != null)
    {
      downloadWindow.setVisible(false);
      downloadWindow.dispose();
      downloadWindow = null;
    }
    if (bytes > 0)
    {
      WbSwingUtilities.showMessage(SwingUtilities.getWindowAncestor(this), "Suchessfully downloaded " + artefact.buildFilename());
      WbFile file = new WbFile(downloadDir, artefact.buildFilename());
      List<String> liblist = CollectionUtil.arrayList(file.getFullPath());
      classpathEditor.setLibraries(liblist);
    }
    else
    {
      WbSwingUtilities.showMessage(SwingUtilities.getWindowAncestor(this), "Error: " + mavenDownloader.getLastHttpMsg());
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

    lblName = new javax.swing.JLabel();
    tfName = new javax.swing.JTextField();
    lblClassName = new javax.swing.JLabel();
    tfClassName = new javax.swing.JTextField();
    lblLibrary = new javax.swing.JLabel();
    lblSample = new javax.swing.JLabel();
    tfSampleUrl = new javax.swing.JTextField();
    statusLabel = new WbStatusLabel();
    detectDriverButton = new FlatButton();
    classpathEditor = new workbench.gui.components.ClasspathEditor();
    downloadButton = new javax.swing.JButton();

    setLayout(new java.awt.GridBagLayout());

    lblName.setText(ResourceMgr.getString("LblDriverName")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(11, 10, 0, 7);
    add(lblName, gridBagConstraints);

    tfName.setHorizontalAlignment(javax.swing.JTextField.LEFT);
    tfName.setMinimumSize(new java.awt.Dimension(50, 20));
    tfName.addMouseListener(new TextComponentMouseListener());
    tfName.addFocusListener(new java.awt.event.FocusAdapter()
    {
      public void focusLost(java.awt.event.FocusEvent evt)
      {
        DriverEditorPanel.this.focusLost(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(11, 3, 0, 3);
    add(tfName, gridBagConstraints);

    lblClassName.setText(ResourceMgr.getString("LblDriverClass")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(6, 10, 0, 7);
    add(lblClassName, gridBagConstraints);

    tfClassName.setColumns(10);
    tfClassName.setHorizontalAlignment(javax.swing.JTextField.LEFT);
    tfClassName.addMouseListener(new TextComponentMouseListener());
    tfClassName.addFocusListener(new java.awt.event.FocusAdapter()
    {
      public void focusLost(java.awt.event.FocusEvent evt)
      {
        DriverEditorPanel.this.focusLost(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(6, 3, 0, 3);
    add(tfClassName, gridBagConstraints);

    lblLibrary.setText(ResourceMgr.getString("LblDriverLibrary")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(6, 10, 0, 7);
    add(lblLibrary, gridBagConstraints);

    lblSample.setText(ResourceMgr.getString("LblSampleUrl")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(6, 10, 0, 7);
    add(lblSample, gridBagConstraints);

    tfSampleUrl.setColumns(10);
    tfSampleUrl.setHorizontalAlignment(javax.swing.JTextField.LEFT);
    tfSampleUrl.addMouseListener(new TextComponentMouseListener());
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(6, 3, 0, 5);
    add(tfSampleUrl, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.gridwidth = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(14, 10, 0, 5);
    add(statusLabel, gridBagConstraints);

    detectDriverButton.setIcon(IconMgr.getInstance().getLabelIcon("magnifier"));
    detectDriverButton.setToolTipText(ResourceMgr.getString("MsgDetectDriver")); // NOI18N
    detectDriverButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
    detectDriverButton.addActionListener(new java.awt.event.ActionListener()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        detectDriverButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.insets = new java.awt.Insets(6, 0, 0, 5);
    add(detectDriverButton, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(7, 3, 0, 3);
    add(classpathEditor, gridBagConstraints);

    downloadButton.setText("Download Driver");
    downloadButton.addActionListener(new java.awt.event.ActionListener()
    {
      public void actionPerformed(java.awt.event.ActionEvent evt)
      {
        downloadButtonActionPerformed(evt);
      }
    });
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(12, 10, 0, 0);
    add(downloadButton, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents

  private void focusLost(java.awt.event.FocusEvent evt)//GEN-FIRST:event_focusLost
  {//GEN-HEADEREND:event_focusLost
    if (validateName())
    {
      this.currentDriver.setName(tfName.getText().trim());
    }
  }//GEN-LAST:event_focusLost

  private void detectDriverButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_detectDriverButtonActionPerformed
  {//GEN-HEADEREND:event_detectDriverButtonActionPerformed
    selectClass();
  }//GEN-LAST:event_detectDriverButtonActionPerformed

  private void downloadButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_downloadButtonActionPerformed
  {//GEN-HEADEREND:event_downloadButtonActionPerformed
    downloadDriver();
  }//GEN-LAST:event_downloadButtonActionPerformed

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private workbench.gui.components.ClasspathEditor classpathEditor;
  private javax.swing.JButton detectDriverButton;
  private javax.swing.JButton downloadButton;
  private javax.swing.JLabel lblClassName;
  private javax.swing.JLabel lblLibrary;
  private javax.swing.JLabel lblName;
  private javax.swing.JLabel lblSample;
  private javax.swing.JLabel statusLabel;
  private javax.swing.JTextField tfClassName;
  private javax.swing.JTextField tfName;
  private javax.swing.JTextField tfSampleUrl;
  // End of variables declaration//GEN-END:variables
}
