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
package workbench.util;

import java.awt.Component;
import java.awt.HeadlessException;
import java.awt.Window;
import java.io.File;
import java.nio.file.Path;

import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileFilter;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.EncodingDropDown;
import workbench.gui.components.ExtensionFileFilter;
import workbench.gui.components.WbFileChooser;

/**
 *
 * @author Thomas Kellerer
 */
public class FileDialogUtil
{
  private static final int FILE_TYPE_UNKNOWN = -1;
  private static final int FILE_TYPE_TXT = 0;
  private static final int FILE_TYPE_SQL = 1;
  private static final int FILE_TYPE_XML = 2;
  private static final int FILE_TYPE_HTML = 3;
  private static final int FILE_TYPE_SQL_UPDATE = 4;

  private int lastFileType = FILE_TYPE_UNKNOWN;
  public static final String CONFIG_DIR_KEY = "%ConfigDir%";
  public static final String MACRO_DIR_KEY = "%MacroDir%";
  public static final String WKSP_DIR_KEY = "%WorkspaceDir%";
  public static final String PROGRAM_DIR_KEY = "%ProgramDir%";
  private String encoding = null;

  public int getLastSelectedFileType()
  {
    return this.lastFileType;
  }

  public String getEncoding()
  {
    return this.encoding;
  }

  public String getXmlReportFilename(Component caller)
  {
    String lastDir = Settings.getInstance().getProperty("workbench.xmlreport.lastdir", null);
    JFileChooser fc = new WbFileChooser(lastDir);
    fc.addChoosableFileFilter(ExtensionFileFilter.getXmlFileFilter());
    fc.setFileFilter(ExtensionFileFilter.getXmlFileFilter());

    if (this.encoding == null)
    {
      this.encoding = Settings.getInstance().getDefaultDataEncoding();
    }

    EncodingDropDown encodingPanel = new EncodingDropDown(this.encoding);
    encodingPanel.setBorder(new EmptyBorder(0,5,0,0));
    fc.setAccessory(encodingPanel);

    String filename = null;

    Window parent = SwingUtilities.getWindowAncestor(caller);

    int answer = fc.showSaveDialog(parent);
    if (answer == JFileChooser.APPROVE_OPTION)
    {
      this.encoding = encodingPanel.getEncoding();

      File fl = fc.getSelectedFile();
      if (StringUtil.isEmpty(encoding))
      {
        encoding = FileUtil.detectFileEncoding(fl);
      }
      FileFilter ff = fc.getFileFilter();
      if (ff instanceof ExtensionFileFilter)
      {
        ExtensionFileFilter eff = (ExtensionFileFilter)ff;
        filename = fl.getAbsolutePath();

        String ext = ExtensionFileFilter.getExtension(fl);
        if (StringUtil.isEmpty(ext))
        {
          if (!filename.endsWith(".")) filename += ".";
          filename += eff.getDefaultExtension();
        }
        this.lastFileType = this.getFileFilterType(ff);
      }
      else
      {
        filename = fl.getAbsolutePath();
      }

      lastDir = fc.getCurrentDirectory().getAbsolutePath();
      Settings.getInstance().setProperty("workbench.xmlreport.lastdir", lastDir);
    }

    return filename;
  }

  private int getFileFilterType(FileFilter ff)
  {
    if (ff == ExtensionFileFilter.getSqlFileFilter())
    {
      return FILE_TYPE_SQL;
    }
    else if (ff == ExtensionFileFilter.getSqlUpdateFileFilter())
    {
      return FILE_TYPE_SQL_UPDATE;
    }
    else if (ff == ExtensionFileFilter.getXmlFileFilter())
    {
      return FILE_TYPE_XML;
    }
    else if (ff == ExtensionFileFilter.getTextFileFilter())
    {
      return FILE_TYPE_TXT;
    }
    else if (ff == ExtensionFileFilter.getHtmlFileFilter())
    {
      return FILE_TYPE_HTML;
    }
    return FILE_TYPE_UNKNOWN;
  }

  public static String getBlobFile(Component caller)
  {
    return getBlobFile(caller, true);
  }

  public static String getBlobFile(Component caller, boolean showSaveDialog)
  {
    try
    {
      Window parent = SwingUtilities.getWindowAncestor(caller);
      String lastDir = Settings.getInstance().getLastBlobDir();
      JFileChooser fc = new WbFileChooser(lastDir);
      int answer;
      if (showSaveDialog)
      {
        answer = fc.showSaveDialog(parent);
      }
      else
      {
        answer = fc.showOpenDialog(parent);
      }
      String filename = null;
      if (answer == JFileChooser.APPROVE_OPTION)
      {
        File fl = fc.getSelectedFile();
        filename = fl.getAbsolutePath();
        Settings.getInstance().setLastBlobDir(fl.getParentFile().getAbsolutePath());
      }
      return filename;
    }
    catch (HeadlessException e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error selecting file", e);
      WbSwingUtilities.showErrorMessage(ExceptionUtil.getDisplay(e));
      return null;
    }
  }

  public static String removeConfigDir(String filePath)
  {
    WbFile f = new WbFile(filePath);
    return removeConfigDir(f);
  }

  public static String removeConfigDir(WbFile selectedFile)
  {
    return removeBaseDir(selectedFile, Settings.getInstance().getConfigDir());
  }

  public static String removeWorkspaceDir(String selectedDir)
  {
    if (StringUtil.isBlank(selectedDir)) return selectedDir;
    return removeBaseDir(new WbFile(selectedDir), Settings.getInstance().getWorkspaceDir());
  }

  public static String removeMacroDir(String selectedDir)
  {
    if (StringUtil.isBlank(selectedDir)) return selectedDir;
    return removeBaseDir(new WbFile(selectedDir), Settings.getInstance().getMacroBaseDirectory());
  }

  public static String removeMacroDir(File selectedDir)
  {
    if (selectedDir == null) return "";
    return removeBaseDir(new WbFile(selectedDir), Settings.getInstance().getMacroBaseDirectory());
  }

  public static String removeBaseDir(WbFile selectedDir, File baseDir)
  {
    if (selectedDir == null || baseDir == null) return "";

    Path sp = selectedDir.toPath();
    Path bp = baseDir.toPath();

    if (sp.startsWith(bp))
    {
      return sp.subpath(bp.getNameCount(), sp.getNameCount()).toString();
    }
    return selectedDir.getFullPath();
  }

  public static String getPathWithConfigDirPlaceholder(WbFile file)
  {
    return getPathWithPlaceholder(file, Settings.getInstance().getConfigDir(), CONFIG_DIR_KEY);
  }

  public static String getPathWithMacroPlaceholder(WbFile file)
  {
    return getPathWithPlaceholder(file, Settings.getInstance().getMacroBaseDirectory(), MACRO_DIR_KEY);
  }

  public static String getPathWithPlaceholder(WbFile file, File toReplace, String placeHolder)
  {
    if (file == null) return "";
    if (toReplace == null || StringUtil.isBlank(placeHolder) || !Settings.getInstance().getReplaceDirVariables())
    {
      return file.getAbsolutePath();
    }

    Path pf = file.toPath();
    Path rp = toReplace.toPath();
    if (pf.startsWith(rp))
    {
      return placeHolder + "/" + pf.subpath(rp.getNameCount(), pf.getNameCount()).toString();
    }
    return file.getFullPath();
  }

  public static String replaceWorkspaceDir(String pathname)
  {
    if (pathname == null) return null;
    WbFile dir = new WbFile(Settings.getInstance().getWorkspaceDir());
    return StringUtil.replace(pathname, WKSP_DIR_KEY, dir.getFullPath());
  }

  public static String replaceConfigDir(String aPathname)
  {
    if (aPathname == null) return null;
    WbFile dir = new WbFile(Settings.getInstance().getConfigDir());
    return StringUtil.replace(aPathname, CONFIG_DIR_KEY, dir.getFullPath());
  }

  public static String replaceMacroDir(String aPathname)
  {
    if (aPathname == null) return null;
    WbFile dir = new WbFile(Settings.getInstance().getMacroBaseDirectory());
    return StringUtil.replace(aPathname, MACRO_DIR_KEY, dir.getFullPath());
  }

  public static String makeWorkspacePath(String aPathname)
  {
    if (aPathname == null) return null;

    String fullPath = replaceWorkspaceDir(aPathname);
    fullPath = replaceConfigDir(fullPath);
    fullPath = replaceProgramDir(fullPath);
    return fullPath;
  }

  public static String replaceProgramDir(String aPathname)
  {
    if (aPathname == null) return null;
    if (!aPathname.contains(PROGRAM_DIR_KEY)) return aPathname;
    ClasspathUtil cp = new ClasspathUtil();
    WbFile dir = new WbFile(cp.getJarPath());
    return StringUtil.replace(aPathname, PROGRAM_DIR_KEY, dir.getFullPath());
  }

  public static void selectPkMapFileIfNecessary(Component parent)
  {
    File file = Settings.getInstance().getPKMappingFile();
    if (file != null)
    {
      if (file.exists()) return;
    }
    boolean doSelectFile = WbSwingUtilities.getYesNo(parent, ResourceMgr.getString("MsgSelectPkMapFile"));
    if (!doSelectFile) return;
    file = selectPkMapFile(parent);
    if (file != null)
    {
      Settings.getInstance().setPKMappingFile(file);
    }
  }

  public static File selectPkMapFile(Component parent)
  {
    File mappingFile = Settings.getInstance().getPKMappingFile();
    File dir;

    if (mappingFile == null || mappingFile.getParentFile() == null)
    {
      dir = Settings.getInstance().getConfigDir();
    }
    else
    {
      dir = mappingFile.getParentFile();
    }

    try
    {
      JFileChooser dialog = new WbFileChooser(dir);
      dialog.setApproveButtonText(ResourceMgr.getString("LblOK"));
      if (mappingFile != null)
      {
        dialog.setSelectedFile(mappingFile);
      }
      int choice = dialog.showSaveDialog(parent);
      if (choice == JFileChooser.APPROVE_OPTION)
      {
        return dialog.getSelectedFile();
      }
      return null;
    }
    catch (HeadlessException e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error selecting file", e);
      WbSwingUtilities.showErrorMessage(ExceptionUtil.getDisplay(e));
      return null;
    }
  }

  public static WbFile selectPngFile(JComponent parent, String lastDirProp)
  {
    String last = Settings.getInstance().getProperty(lastDirProp, null);
    File lastDir;

    if (StringUtil.isNotBlank(last))
    {
      lastDir = new File(last);
    }
    else
    {
      lastDir = Settings.getInstance().getConfigDir();
    }

    JFileChooser fc = new WbFileChooser(lastDir);
    ExtensionFileFilter ff = new ExtensionFileFilter(ResourceMgr.getString("TxtFileFilterIcons"), CollectionUtil.arrayList("png"), true);
    fc.setMultiSelectionEnabled(false);
    fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
    fc.removeChoosableFileFilter(fc.getAcceptAllFileFilter());
    fc.addChoosableFileFilter(ff);
    fc.setFileFilter(ff);

    int answer = fc.showOpenDialog(SwingUtilities.getWindowAncestor(parent));

    if (answer == JFileChooser.APPROVE_OPTION)
    {
      WbFile f = new WbFile(fc.getSelectedFile());
      if (!ImageUtil.isPng(f))
      {
        String msg = ResourceMgr.getFormattedString("ErrInvalidIcon", f.getName());
        WbSwingUtilities.showErrorMessage(parent, msg);
      }
      WbFile dir = new WbFile(fc.getCurrentDirectory());
      Settings.getInstance().setProperty(lastDirProp, dir.getFullPath());
      return f;
    }
    return null;
  }
}
