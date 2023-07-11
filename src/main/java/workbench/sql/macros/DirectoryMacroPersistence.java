/*
 * This file is part of SQL Workbench/J, http://www.sql-workbench.net
 *
 * Copyright 2002-2013, Thomas Kellerer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at.
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.net
 *
 */
package workbench.sql.macros;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.util.CollectionUtil;
import workbench.util.FileUtil;
import workbench.util.StringUtil;
import workbench.util.WbFile;
import workbench.util.WbProperties;

/**
 * A class to load macro definitions from a directory containing multiple SQL scripts.
 *
 * Each sub-directory of the defined base directory is assumed to be a macro group.
 * File in the base directory are not loaded.
 *
 * The files in the main directory are stored in a group that has the same name as the
 * source directory.
 *
 * Additional properties of the MacroGroup and MacroDefinition are stored and loaded from
 * a properties file ({@link #GROUP_INFO_FILE}) in the group's directory.
 *
 * If the file is not present, the default values for attributes of the group or macro definitions are used.
 *
 * @author Thomas Kellerer
 */
public class DirectoryMacroPersistence
  implements MacroPersistence
{
  private static final String GROUP_INFO_FILE = "wb-macro-group.properties";
  private static final String GROUP_PREFIX = "group.info.";

  private static final String PROP_TOOLTIP = "tooltip";
  private static final String PROP_SORT_ORDER = "sortorder";
  private static final String PROP_NAME = "name";
  private static final String PROP_EXPAND = "expandWhileTyping";
  private static final String PROP_APPEND = "appendResults";
  private static final String PROP_DB_TREE = "dbTreeMacro";
  private static final String PROP_INCLUDE_IN_MENU = "includeInMenu";
  private static final String PROP_INCLUDE_IN_POPUP = "includeInPopup";

  private final Comparator<File> fileSorter = (File f1, File f2) -> f1.getName().compareToIgnoreCase(f2.getName());

  @Override
  public List<MacroGroup> loadMacros(File sourceDirectory)
  {
    List<MacroGroup> result = new ArrayList<>();
    if (sourceDirectory == null || !sourceDirectory.exists()) return result;

    if (!sourceDirectory.isDirectory())
    {
      throw new IllegalArgumentException("The provided file " + sourceDirectory + " is not a directory!");
    }

    File[] dirs = sourceDirectory.listFiles((File f) -> f != null && f.isDirectory());
    Arrays.sort(dirs, fileSorter);
    int groupIndex = 0;

    for (File dir : dirs)
    {
      result.add(loadMacrosFromDirectory(new WbFile(dir), groupIndex++));
    }
    return result;
  }

  @Override
  public void saveMacros(WbFile baseDirectory, List<MacroGroup> groups, boolean isModified)
  {
    if (baseDirectory == null || CollectionUtil.isEmpty(groups)) return;

    if (baseDirectory.exists() && !baseDirectory.isDirectory())
    {
      throw new IllegalArgumentException("The provided file " + baseDirectory + " is not a directory!");
    }
    if (!baseDirectory.exists())
    {
      baseDirectory.mkdirs();
    }

    for (MacroGroup group : groups)
    {
      String groupName = group.getName();
      WbFile groupDir = new WbFile(baseDirectory, StringUtil.makeFilename(groupName, false));
      if (group.getTotalSize() == 0)
      {
        File infoFile = new File(groupDir, GROUP_INFO_FILE);
        if (infoFile.exists())
        {
          infoFile.delete();
        }
      }
      else
      {
        saveGroup(groupDir, group);
      }
    }
  }

  private void saveGroup(File groupDir, MacroGroup group)
  {
    if (!groupDir.exists())
    {
      groupDir.mkdirs();
    }

    for (MacroDefinition macro : group.getAllMacros())
    {
      saveMacro(groupDir, macro);
    }
    writeGroupInfo(groupDir, group);
    List<MacroDefinition> deleted = group.getDeletedMacros();
    for (MacroDefinition def : deleted)
    {
      File toDelete = def.getOriginalSourceFile();
      if (toDelete == null)
      {
        toDelete = new File(groupDir, getFilename(def) + ".sql");
      }
      if (toDelete.exists())
      {
        toDelete.delete();
      }
    }
  }

  private void writeGroupInfo(File directory, MacroGroup group)
  {
    WbProperties props = new WbProperties(2);
    props.setProperty(GROUP_PREFIX + PROP_INCLUDE_IN_MENU, group.isVisibleInMenu());
    props.setProperty(GROUP_PREFIX + PROP_INCLUDE_IN_POPUP, group.isVisibleInPopup());
    props.setProperty(GROUP_PREFIX + PROP_TOOLTIP, group.getTooltip());
    props.setProperty(GROUP_PREFIX + PROP_NAME, group.getName());

    for (MacroDefinition def : group.getAllMacros())
    {
      String key = makeKey(def);
      props.setProperty(key + PROP_INCLUDE_IN_MENU, def.isVisibleInMenu());
      props.setProperty(key + PROP_INCLUDE_IN_POPUP, def.isVisibleInPopup());
      props.setProperty(key + PROP_APPEND, def.isAppendResult());
      props.setProperty(key + PROP_EXPAND, def.getExpandWhileTyping());
      props.setProperty(key + PROP_DB_TREE, def.isDbTreeMacro());
      props.setProperty(key + PROP_NAME, def.getName());
      props.setProperty(key + PROP_TOOLTIP, def.getTooltip());
      props.setProperty(key + PROP_SORT_ORDER, def.getSortOrder());
    }

    WbFile info = new WbFile(directory, GROUP_INFO_FILE);
    try
    {
      props.saveToFile(info);
    }
    catch (IOException io)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not write macro info file: " + info.getFullpathForLogging(), io);
    }
  }

  private void applyGroupInfo(File directory, MacroGroup group)
  {
    if (directory == null || group == null) return;

    WbFile infoFile = new WbFile(directory, GROUP_INFO_FILE);
    if (!infoFile.exists())
    {
      // Sort the macros by (file) name if no attributes are available
      group.sortByName();
      return;
    }

    WbProperties props = new WbProperties(0);
    try
    {
      props.loadTextFile(infoFile, "UTF-8");
      group.setTooltip(props.getProperty(PROP_TOOLTIP));
      group.setVisibleInMenu(props.getBoolProperty(GROUP_PREFIX + PROP_INCLUDE_IN_MENU, group.isVisibleInMenu()));
      group.setVisibleInPopup(props.getBoolProperty(GROUP_PREFIX + PROP_INCLUDE_IN_POPUP, group.isVisibleInPopup()));
      group.setName(props.getProperty(GROUP_PREFIX + PROP_NAME, group.getName()));
      group.setSortOrder(props.getIntProperty(GROUP_PREFIX + PROP_SORT_ORDER, group.getSortOrder()));

      for (MacroDefinition def : group.getAllMacros())
      {
        String key = makeKey(def);
        def.setVisibleInMenu(props.getBoolProperty(key + PROP_INCLUDE_IN_MENU, def.isVisibleInMenu()));
        def.setVisibleInPopup(props.getBoolProperty(key + PROP_INCLUDE_IN_POPUP, def.isVisibleInPopup()));
        def.setAppendResult(props.getBoolProperty(key + PROP_APPEND, def.isAppendResult()));
        def.setExpandWhileTyping(props.getBoolProperty(key + PROP_EXPAND, def.getExpandWhileTyping()));
        def.setDbTreeMacro(props.getBoolProperty(key + PROP_DB_TREE, def.isDbTreeMacro()));
        def.setName(props.getProperty(key + PROP_NAME, def.getName()));
        def.setTooltip(props.getProperty(key + PROP_TOOLTIP, def.getTooltip()));
        def.setSortOrder(props.getIntProperty(key + PROP_SORT_ORDER, def.getSortOrder()));
      }
    }
    catch (Exception io)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not read info file for macro group " + group.getName() + " in directory: " + WbFile.getPathForLogging(directory), io);
    }

  }
  private void saveMacro(File baseDirectory, MacroDefinition macro)
  {
    if (baseDirectory == null || macro == null) return;
    File original = macro.getOriginalSourceFile();
    WbFile target;
    if (original != null)
    {
      target = new WbFile(baseDirectory, original.getName());
    }
    else
    {
      target = new WbFile(baseDirectory, StringUtil.makeFilename(macro.getName(), false) + ".sql");
    }

    try
    {
      FileUtil.writeString(target, macro.getText());
    }
    catch (Exception io)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not write macro file: " + target.getFullpathForLogging(), io);
    }
  }

  private MacroGroup loadMacrosFromDirectory(WbFile source, int groupIndex)
  {
    MacroGroup result = new MacroGroup();
    result.setName(source.getName());
    // will be overwritten if a group info file exists
    result.setSortOrder(groupIndex);
    File[] files = source.listFiles((File f) -> f != null && f.isFile() && f.getName().toLowerCase().endsWith(".sql"));
    for (File f : files)
    {
      WbFile wb = new WbFile(f);
      try
      {
        String content = FileUtil.readFile(f, "UTF-8");
        MacroDefinition def = new MacroDefinition(wb.getFileName(), content);
        def.setOriginalSourceFile(wb);
        result.addMacro(def);
      }
      catch (Exception io)
      {
        LogMgr.logError(new CallerInfo(){}, "Could not read macro file: " + wb.getFullpathForLogging(), io);
      }
    }
    applyGroupInfo(source, result);
    return result;
  }

  private String getFilename(MacroDefinition macro)
  {
    if (macro.getOriginalSourceFile() != null)
    {
      return macro.getOriginalSourceFile().getName();
    }
    return StringUtil.makeFilename(macro.getName(), false);
  }

  private String makeKey(MacroDefinition def)
  {
    String cleanName = getFilename(def).replace(' ', '_').replace('=', '-');
    return "macro." + cleanName + ".";
  }

}
