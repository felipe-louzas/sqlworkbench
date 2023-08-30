/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer
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

package workbench.sql.macros;

import java.awt.Component;
import java.io.File;

import javax.swing.JFileChooser;

import workbench.WbManager;
import workbench.resource.DirectorySaveStrategy;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.gui.WbSwingUtilities;
import workbench.gui.YesNoCancel;
import workbench.gui.components.ExtensionFileFilter;
import workbench.gui.components.WbFileChooser;

import workbench.util.FileDialogUtil;
import workbench.util.WbFile;

/**
 *
 * @author Thomas Kellerer
 */
public class MacroFileSelector
{
  public static final String LAST_DIR_PROPERTY = "workbench.macros.lastdir";

  public MacroFileSelector()
  {
  }

  public boolean canLoadMacros(int clientId)
  {
    if (!MacroManager.getInstance().getMacros(clientId).isModified()) return true;

    YesNoCancel result = WbSwingUtilities.getYesNoCancel(WbManager.getInstance().getCurrentWindow(), ResourceMgr.getString("MsgConfirmUnsavedMacros"));
    switch (result)
    {
      case cancel:
        return false;
      case yes:
        MacroManager.getInstance().save();
    }
    return true;
  }

  public WbFile selectStorageForLoad(Component parent, int clientId)
  {
    if (!canLoadMacros(clientId)) return null;
    return selectStorageFile(parent, false, MacroManager.getInstance().getMacros(clientId).getCurrentFile());
  }

  public WbFile selectStorageForSave(Component parent, int clientId)
  {
    return selectStorageFile(parent, true, MacroManager.getInstance().getMacros(clientId).getCurrentFile());
  }

  private WbFile selectStorageFile(Component parent, boolean forSave, File currentFile)
  {
    String lastDir = Settings.getInstance().getProperty(LAST_DIR_PROPERTY, Settings.getInstance().getMacroBaseDirectory().getAbsolutePath());

    JFileChooser fc = new WbFileChooser(lastDir);
    fc.setFileSelectionMode(JFileChooser.FILES_AND_DIRECTORIES);
    fc.addChoosableFileFilter(ExtensionFileFilter.getXmlFileFilter());
    fc.setFileFilter(ExtensionFileFilter.getXmlFileFilter());
    fc.setDialogTitle(ResourceMgr.getString("MsgSelectMacroFile"));

    int answer = JFileChooser.CANCEL_OPTION;

    File selectedFile = null;
    boolean done = false;
    while (!done)
    {
      if (forSave)
      {
        if (currentFile != null)
        {
          fc.setSelectedFile(currentFile);
        }
        answer = fc.showSaveDialog(parent);
      }
      else
      {
        answer = fc.showOpenDialog(parent);
      }

      if (answer == JFileChooser.APPROVE_OPTION)
      {
        selectedFile = fc.getSelectedFile();

        if (!isValidSelection(selectedFile))
        {
          YesNoCancel choice = confirmDirectorySelection(parent, selectedFile);
          if (choice == YesNoCancel.cancel)
          {
            selectedFile = null;
            done = true;
            break;
          }

          if (choice == YesNoCancel.no)
          {
            done = false;
            continue;
          }
        }

        if (forSave)
        {
          selectedFile = checkFileExtension(selectedFile);
        }
        done = true;
        lastDir = fc.getCurrentDirectory().getAbsolutePath();
        Settings.getInstance().setProperty(LAST_DIR_PROPERTY, lastDir);
      }
    }

    if (selectedFile == null)
    {
      return null;
    }

    String pathToUse = FileDialogUtil.removeMacroDir(selectedFile.getAbsolutePath());
    return new WbFile(pathToUse);
  }

  private File checkFileExtension(File selectedFile)
  {
    WbFile wb = new WbFile(selectedFile);
    String ext = wb.getExtension();
    if (!wb.isDirectory() && !ext.equalsIgnoreCase("xml"))
    {
      String fullname = wb.getFullPath() + ".xml";
      selectedFile = new File(fullname);
    }
    return selectedFile;
  }

  private YesNoCancel confirmDirectorySelection(Component parent, File selected)
  {
    String msg = ResourceMgr.getFormattedString("MsgMacroDirNotEmpty", selected.getAbsolutePath());
    return WbSwingUtilities.getYesNoCancel(parent, msg);
  }

  private boolean isValidSelection(File selected)
  {
    if (selected == null) return false;
    if (selected.isFile() || !selected.exists())
    {
      return true;
    }

    // Only allow directories that are empty or are already used for macros
    File props = new File(selected, DirectoryMacroPersistence.GROUP_INFO_FILE);
    if (props.exists())
    {
      return true;
    }

    DirectorySaveStrategy saveStrategy = Settings.getInstance().getDirectoryBaseMacroStorageSaveStrategy();
    if (saveStrategy == DirectorySaveStrategy.Merge)
    {
      return true;
    }
    
    File[] files = selected.listFiles();
    return (files == null || files.length == 0);
  }

}
