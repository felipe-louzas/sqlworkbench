/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
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

package workbench.gui.components;

import java.awt.Dialog;
import java.awt.FileDialog;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import workbench.resource.GuiSettings;
import workbench.resource.IconMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.gui.profiles.LibraryElement;

import workbench.util.FileUtil;
import workbench.util.WbFile;

/**
 *
 * @author Thomas Kellerer
 */
public class ClasspathEditor
  extends JPanel
  implements ListSelectionListener, ActionListener
{
  private String lastDirProperty;
  private String lastDir = null;
  private final List<ActionListener> listener = new ArrayList<>();
  private boolean onlyDirectories = false;
  private String dialogTitleKey = "TxtWindowTitleSelectDrv";

  public ClasspathEditor()
  {
    initComponents();
    libList.getSelectionModel().addListSelectionListener(this);
    btnUp.addActionListener(this);
    btnDown.addActionListener(this);
    btnRemove.addActionListener(this);
    btnAdd.addActionListener(this);
  }

  public void setDialogTitleKey(String resourceKey)
  {
    this.dialogTitleKey = resourceKey;
  }

  public void setAddDirIcon(String iconKey)
  {
    btnAdd.setIcon(IconMgr.getInstance().getLabelIcon(iconKey));
  }

  public void setRemoveDirIcon(String iconKey)
  {
    btnRemove.setIcon(IconMgr.getInstance().getLabelIcon(iconKey));
  }

  public void setAllowOnlyDirectories(boolean flag)
  {
    this.onlyDirectories = flag;
    if (flag)
    {
      this.btnAdd.setToolTipText(null);
    }
  }

  public void restoreSettings()
  {
    if (this.lastDirProperty != null)
    {
      this.lastDir = Settings.getInstance().getProperty(lastDirProperty, null);
    }
  }

  public void addActionListener(ActionListener l)
  {
    this.listener.add(l);
  }

  public void removeActionListener(ActionListener l)
  {
    this.listener.remove(l);
  }

  public void saveSettings()
  {
    if (this.lastDirProperty != null)
    {
      Settings.getInstance().setProperty(lastDirProperty, lastDir);
    }
  }

  public String getLastDirProperty()
  {
    return this.lastDirProperty;
  }

  public void setLastDirProperty(String propName)
  {
    this.lastDirProperty = propName;
    restoreSettings();
  }

  public boolean hasLibraries()
  {
    return libList.getModel().getSize() > 0;
  }

  public void setLibraries(List<String> libraries)
  {
    DefaultListModel model = new DefaultListModel();
    if (libraries != null)
    {
      for (String lib : libraries)
      {
        if (lib != null)
        {
          model.addElement(new LibraryElement(lib));
        }
      }
    }
    libList.setModel(model);
    libList.getSelectionModel().clearSelection();
    checkButtons();
  }

  public List<String> getRealJarPaths()
  {
    int size = libList.getModel().getSize();
    List<String> result = new ArrayList<>(size);
    for (int i=0; i < size; i++)
    {
      LibraryElement lib = (LibraryElement)libList.getModel().getElementAt(i);
      result.add(lib.getRealPath());
    }
    return result;
  }

  public List<String> getLibraries()
  {
    int size = libList.getModel().getSize();
    List<String> result = new ArrayList<>(size);
    for (int i=0; i < size; i++)
    {
      LibraryElement lib = (LibraryElement)libList.getModel().getElementAt(i);
      result.add(lib.getPath());
    }
    return result;
  }

  @Override
  public void actionPerformed(ActionEvent e)
  {
    int[] indexes = libList.getSelectedIndices();

    int selectedIndex = -1;
    if (indexes.length == 1)
    {
      selectedIndex = indexes[0];
    }

    DefaultListModel model = (DefaultListModel)libList.getModel();
    int count = model.getSize();

    if (e.getSource() == btnRemove && indexes.length > 0)
    {
      removeSelected();
    }
    else if (e.getSource() == btnUp && selectedIndex > 0)
    {
      swap(selectedIndex, selectedIndex - 1);
    }
    else if (e.getSource() == btnDown && selectedIndex > -1 && selectedIndex < count - 1)
    {
      swap(selectedIndex, selectedIndex + 1);
    }
    else if (e.getSource() == btnAdd)
    {
      if (GuiSettings.useAWTFileDialog())
      {
        // This seems to be necessary on some MacOS installations
        // that won't show any files in the file open dialog
        // from a Java application due to some weird security restrictions
        selectFileAWT();
      }
      else
      {
        selectFile();
      }
    }
  }

  private void removeSelected()
  {
    int[] indexes = libList.getSelectedIndices();
    if (indexes.length == 0) return;

    Arrays.sort(indexes);
    DefaultListModel model = (DefaultListModel)libList.getModel();
    for (int i=indexes.length - 1; i >= 0; i --)
    {
      model.remove(indexes[i]);
    }
  }

  private void swap(int firstIndex, int secondIndex)
  {
    DefaultListModel model = (DefaultListModel)libList.getModel();
    Object first = model.get(firstIndex);
    Object second = model.get(secondIndex);
    model.set(firstIndex, second);
    model.set(secondIndex, first);
    libList.setSelectedIndex(secondIndex);
  }

  private void removeSingleInvalidEntry()
  {
    if (this.onlyDirectories) return;

    DefaultListModel model = (DefaultListModel)libList.getModel();

    if (model.getSize() == 1)
    {
      LibraryElement lib = (LibraryElement)model.getElementAt(0);
      String realPath = lib.getRealPath();
      File f = new File(realPath);
      if (!f.exists())
      {
        model.remove(0);
      }
    }
  }

  private void selectFileAWT()
  {
    FileDialog dialog = new FileDialog((Dialog)SwingUtilities.getWindowAncestor(this), ResourceMgr.getString(dialogTitleKey));
    dialog.setMode(FileDialog.LOAD);
    if (FileUtil.fileExists(lastDir))
    {
      dialog.setDirectory(lastDir);
    }

    FilenameFilter jarFilter = (File file, String fname) ->
    {
      if (fname == null) return false;
      return fname.toLowerCase().endsWith("jar") || fname.toLowerCase().endsWith("zip");
    };

    FilenameFilter dirFilter = (File file, String fname) ->
    {
      if (file == null && fname == null) return false;
      if (fname == null) return file.isDirectory();
      File f = new File(file, fname);
      return f.isDirectory();
    };

    if (onlyDirectories)
    {
      dialog.setFilenameFilter(dirFilter);
    }
    else
    {
      dialog.setFilenameFilter(jarFilter);
    }
    dialog.setMultipleMode(true);
    dialog.setVisible(true);
    File[] files = dialog.getFiles();
    if (files != null)
    {
      setSelectedFiles(files);
    }
  }

  private void selectFile()
  {
    JFileChooser jf = new WbFileChooser();
    jf.setMultiSelectionEnabled(true);

    if (this.onlyDirectories)
    {
      jf.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    }
    else
    {
      jf.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
      jf.setFileFilter(ExtensionFileFilter.getJarFileFilter());
    }

    if (this.lastDir != null)
    {
      jf.setCurrentDirectory(new File(this.lastDir));
    }
    jf.setDialogTitle(ResourceMgr.getString(dialogTitleKey));
    int answer = jf.showOpenDialog(SwingUtilities.getWindowAncestor(this));
    if (answer == JFileChooser.APPROVE_OPTION)
    {
      File[] selectedFiles = jf.getSelectedFiles();
      setSelectedFiles(selectedFiles);
    }
  }

  private void setSelectedFiles(File[] selectedFiles)
  {
    DefaultListModel model = (DefaultListModel)libList.getModel();
    removeSelected();
    removeSingleInvalidEntry();
    for (File f : selectedFiles)
    {
      model.addElement(new LibraryElement(new WbFile(f)));
    }
    lastDir = selectedFiles[0].getParent();
    saveSettings();
    fireLiblistChanged();
  }

  private void fireLiblistChanged()
  {
    ActionEvent evt = new ActionEvent(this, 1, "modified");
    for (ActionListener l : listener)
    {
      l.actionPerformed(evt);
    }
  }

  @Override
  public void valueChanged(ListSelectionEvent e)
  {
    checkButtons();
  }

  public void setFileSelectionEnabled(boolean flag)
  {
    this.btnAdd.setEnabled(flag);
    this.btnRemove.setEnabled(flag);
    this.btnUp.setEnabled(flag);
    this.btnDown.setEnabled(flag);
    this.libList.setEnabled(flag);
  }

  private void checkButtons()
  {
    int selectedIndex = libList.getSelectedIndex();
    int count = libList.getModel().getSize();
    btnRemove.setEnabled(selectedIndex > -1);
    btnUp.setEnabled(selectedIndex > 0);
    btnDown.setEnabled(selectedIndex > -1 && selectedIndex < count - 1);
  }

  public void reset()
  {
    this.libList.setModel(new DefaultListModel());
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

    jScrollPane1 = new WbScrollPane();
    libList = new javax.swing.JList();
    btnAdd = new javax.swing.JButton();
    btnRemove = new javax.swing.JButton();
    btnUp = new javax.swing.JButton();
    btnDown = new javax.swing.JButton();

    setLayout(new java.awt.GridBagLayout());

    libList.setVerifyInputWhenFocusTarget(false);
    libList.setVisibleRowCount(4);
    jScrollPane1.setViewportView(libList);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridheight = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    add(jScrollPane1, gridBagConstraints);

    btnAdd.setIcon(IconMgr.getInstance().getLabelIcon("Open"));
    btnAdd.setToolTipText(ResourceMgr.getString("d_LblDriverLibrary")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(0, 8, 0, 0);
    add(btnAdd, gridBagConstraints);

    btnRemove.setIcon(IconMgr.getInstance().getLabelIcon("delete"));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(4, 8, 0, 0);
    add(btnRemove, gridBagConstraints);

    btnUp.setIcon(IconMgr.getInstance().getLabelIcon("Up"));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(16, 8, 0, 0);
    add(btnUp, gridBagConstraints);

    btnDown.setIcon(IconMgr.getInstance().getLabelIcon("Down"));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(4, 8, 0, 0);
    add(btnDown, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JButton btnAdd;
  private javax.swing.JButton btnDown;
  private javax.swing.JButton btnRemove;
  private javax.swing.JButton btnUp;
  private javax.swing.JScrollPane jScrollPane1;
  private javax.swing.JList libList;
  // End of variables declaration//GEN-END:variables
}
