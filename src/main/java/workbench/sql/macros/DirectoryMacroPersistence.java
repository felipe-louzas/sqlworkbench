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

import workbench.util.FileUtil;
import workbench.util.StringUtil;
import workbench.util.WbFile;
import workbench.util.WbProperties;

/**
 * A class to load macro definitions from a directory containing multiple SQL scripts.
 *
 * If the directory contains sub-directories, those will be added as groups, but only
 * one level deep.
 *
 * The files in the main directory are stored in a group that has the same name as the
 * source directory.
 *
 * @author Thomas Kellerer
 */
public class DirectoryMacroPersistence
{
  private static final String GROUP_INFO_FILE = "wb-macro-properties.wbinfo";

  private static final String PROP_TOOLTIP = "tooltip";
  private static final String PROP_SORT_ORDER = "sortorder";
  private static final String PROP_NAME = "name";
  private static final String PROP_EXPAND = "expandWhileTyping";
  private static final String PROP_APPEND = "appendResults";
  private static final String PROP_DB_TREE = "dbTreeMacro";
  private static final String PROP_INCLUDE_IN_MENU = "includeInMenu";
  private static final String PROP_INCLUDE_IN_POPUP = "includeInPopup";
  private final Comparator<File> fileSorter = (File f1, File f2) -> f1.getName().compareToIgnoreCase(f2.getName());

  public List<MacroGroup> loadMacros(File sourceDirectory)
  {
    List<MacroGroup> result = new ArrayList<>();
    WbFile baseDir = new WbFile(sourceDirectory);
    result.add(loadMacrosFromDirectory(baseDir));

    File[] dirs = sourceDirectory.listFiles((File pathname) -> pathname != null && pathname.isDirectory());
    Arrays.sort(dirs, fileSorter);
    for (File dir : dirs)
    {
      result.add(loadMacrosFromDirectory(new WbFile(dir)));
    }
    return result;
  }

  public void saveMacros(File baseDirectory, List<MacroGroup> groups)
  {
    if (!baseDirectory.isDirectory()) throw new IllegalArgumentException("The provided File " + baseDirectory + " is not a directory!");

    WbFile dir = new WbFile(baseDirectory);
    String baseDir = dir.getFileName();

    for (MacroGroup group : groups)
    {
      WbFile groupDir = dir;
      String groupName = group.getName();
      if (!baseDir.equalsIgnoreCase(groupName))
      {
        groupDir = new WbFile(baseDirectory, groupName);
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
          toDelete = new File(groupDir, def.getName() + ".sql");
        }
        if (toDelete.exists())
        {
          toDelete.delete();
        }
      }
    }
  }

  private void writeGroupInfo(File directory, MacroGroup group)
  {
    WbProperties props = new WbProperties(0);
    props.setProperty("group." + PROP_INCLUDE_IN_MENU, group.isVisibleInMenu());
    props.setProperty("group." + PROP_INCLUDE_IN_POPUP, group.isVisibleInPopup());
    props.setProperty("group." + PROP_TOOLTIP, group.getTooltip());
    props.setProperty("group." + PROP_NAME, group.getName());

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

  private void readGroupInfo(File directory, MacroGroup group)
  {
    if (directory == null) return;
    WbFile infoFile = new WbFile(directory, GROUP_INFO_FILE);
    if (!infoFile.exists()) return;

    WbProperties props = new WbProperties(0);
    try
    {
      props.loadTextFile(infoFile, "UTF-8");
      group.setTooltip(props.getProperty(PROP_TOOLTIP));
      group.setVisibleInMenu(props.getBoolProperty("group." + PROP_INCLUDE_IN_MENU, true));
      group.setVisibleInPopup(props.getBoolProperty("group." + PROP_INCLUDE_IN_POPUP, true));
      if (props.containsKey("group." + PROP_NAME))
      {
        group.setName(props.getProperty("group." + PROP_NAME));
      }
      if (props.containsKey("group." + PROP_SORT_ORDER))
      {
        group.setSortOrder(props.getIntProperty("group." + PROP_SORT_ORDER, 0));
      }

      for (MacroDefinition def : group.getAllMacros())
      {
        String key = makeKey(def);
        def.setVisibleInMenu(props.getBoolProperty(key + PROP_INCLUDE_IN_MENU, true));
        def.setVisibleInPopup(props.getBoolProperty(key + PROP_INCLUDE_IN_POPUP, true));
        def.setAppendResult(props.getBoolProperty(key + PROP_APPEND, false));
        def.setExpandWhileTyping(props.getBoolProperty(key + PROP_EXPAND, false));
        def.setDbTreeMacro(props.getBoolProperty(key + PROP_DB_TREE, false));
        String name = props.getProperty(key + PROP_NAME);
        if (name != null)
        {
          def.setName(name);
        }
        String toolTip = props.getProperty(key + PROP_TOOLTIP);
        if (toolTip != null)
        {
          def.setTooltip(toolTip);
        }
        if (props.containsKey(key + PROP_SORT_ORDER))
        {
          def.setSortOrder(props.getIntProperty(key + PROP_SORT_ORDER, 0));
        }
      }
    }
    catch (IOException io)
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
    catch (IOException io)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not write macro file: " + target.getFullpathForLogging(), io);
    }
  }

  private MacroGroup loadMacrosFromDirectory(WbFile source)
  {
    MacroGroup result = new MacroGroup();
    result.setName(source.getName());
    File[] files = source.listFiles((File f) -> f != null && f.isFile() && f.getName().toLowerCase().endsWith(".sql"));
    // In case not "macro info file" is available sort the macros by name.
    Arrays.sort(files, fileSorter);
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
      catch (IOException io)
      {
        LogMgr.logError(new CallerInfo(){}, "Could not read macrof file: " + wb.getFullpathForLogging(), io);
      }
    }
    readGroupInfo(source, result);
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
