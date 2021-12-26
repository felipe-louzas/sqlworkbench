package workbench.gui.filetree;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import workbench.resource.Settings;

import workbench.gui.dbobjects.objecttree.TreePosition;

import workbench.util.StringUtil;

public class FileTreeSettings
{
  public static final String SETTINGS_PREFIX = "workbench.gui.filetree.";
  public static final String EXCLUDED_FILES_PROPERTY = SETTINGS_PREFIX + ".exclude.files";
  public static final String EXCLUDED_EXT_PROPERTY = SETTINGS_PREFIX + ".exclude.extensions";

  public static void setDefaultDirectory(String dir)
  {
    Settings.getInstance().setProperty(SETTINGS_PREFIX + ".default.dir", dir);
  }

  public static String getDefaultDirectory()
  {
    return Settings.getInstance().getProperty(SETTINGS_PREFIX + ".default.dir", null);
  }

  public static File getDirectoryToUse()
  {
    String dir = getDefaultDirectory();
    if (dir == null)
    {
      dir = Settings.getInstance().getLastSqlDir();
    }
    if (dir == null)
    {
      dir = ".";
    }
    return new File(dir);
  }

  public static String getExcludedFiles()
  {
    return Settings.getInstance().getProperty(EXCLUDED_FILES_PROPERTY, null);
  }

  public static void setExcludedFiles(String list)
  {
    Settings.getInstance().setProperty(EXCLUDED_FILES_PROPERTY, StringUtil.trimToNull(list));
  }

  public static String getExcludedExtensions()
  {
    return Settings.getInstance().getProperty(EXCLUDED_EXT_PROPERTY, null);
  }

  public static void setExcludedExtensions(String list)
  {
    Settings.getInstance().setProperty(EXCLUDED_EXT_PROPERTY, StringUtil.trimToNull(list));
  }

  public static List<String> getFilesToExclude()
  {
    List<String> names = Settings.getInstance().getListProperty(EXCLUDED_FILES_PROPERTY, false, null);
    return names.stream().
            map(s -> StringUtil.trimToNull(s)).
            filter(s -> s != null).
            collect(Collectors.toList());
  }

  public static List<String> getExtensionsToExclude()
  {
    List<String> extensions = Settings.getInstance().getListProperty(EXCLUDED_EXT_PROPERTY, false, null);
    return extensions.
           stream().
           map(s -> StringUtil.trimToNull(s)).
           map(s -> StringUtil.removeLeading(s, '.')).
           filter(s -> s != null).
           collect(Collectors.toList());
  }

  public static boolean getUseSystemIcons()
  {
    // Using system icons in the file tree is much slower than fixed ones.
    return Settings.getInstance().getBoolProperty(SETTINGS_PREFIX + ".use.system.icons", false);
  }

  public static void setTreePosition(TreePosition position)
  {
    if (position == null) return;
    Settings.getInstance().setProperty(SETTINGS_PREFIX + "position", position.name());
  }

  public static TreePosition getTreePosition()
  {
    String pos = Settings.getInstance().getProperty(SETTINGS_PREFIX + "position", TreePosition.left.name());
    try
    {
      return TreePosition.valueOf(pos);
    }
    catch (Throwable th)
    {
      return TreePosition.left;
    }
  }
}
