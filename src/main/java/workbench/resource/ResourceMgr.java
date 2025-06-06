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
package workbench.resource;

import java.awt.Image;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import javax.swing.ImageIcon;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.util.FileUtil;
import workbench.util.StringUtil;
import workbench.util.VersionNumber;

/**
 * A class to manage the ResourceBundle for the localization.
 *
 * @author Thomas Kellerer.kellerer
 */
public class ResourceMgr
{
  public static final String TXT_PRODUCT_NAME = "SQL Workbench/J";

  public static final String TXT_OK = "LblOK";
  public static final String TXT_CANCEL = "LblCancel";

  public static final String MNU_TXT_WORKSPACE = "MnuTxtWorkspace";
  public static final String MNU_TXT_FILE = "MnuTxtFile";
  public static final String MNU_TXT_MACRO = "MnuTxtMacro";
  public static final String MNU_TXT_SQL = "MnuTxtSQL";
  public static final String MNU_TXT_EDIT = "MnuTxtEdit";
  public static final String MNU_TXT_DATA = "MnuTxtData";
  public static final String MNU_TXT_COPY_SELECTED = "MnuTxtCopySelected";

  public static final String MNU_TXT_VIEW = "MnuTxtView";
  public static final String MNU_TXT_TOOLS = "MnuTxtTools";
  public static final String MNU_TXT_TAB_HISTORY = "MnuTxtTabHistory";
  public static final String MNU_TXT_HELP = "MnuTxtHelp";
  public static final String MNU_TXT_OPTIONS = "MnuTxtOptions";

  private static ResourceBundle resources;
  private static final String PROP_CHANGE_LOCALE = "workbench.gui.setdefaultlocale";

  private ResourceMgr()
  {
  }

  public static String getBuildInfo()
  {
    return getString("TxtBuild") + " " + getBuildNumber().toString() + " (" + getString("TxtBuildDate") + ")";
  }

  public static String getFullJavaInfo()
  {
    return "Java version=" + System.getProperty("java.version")  + ", java.home=" + System.getProperty("java.home") + ", vendor=" + System.getProperty("java.vendor") + ", name=" + System.getProperty("java.vm.name");
  }

  public static String getOSInfo()
  {
    return "Operating System=" + System.getProperty("os.name")  + ", version=" + System.getProperty("os.version") + ", platform=" + System.getProperty("os.arch");
  }

  public static String getJavaInfo()
  {
    String version = System.getProperty("java.version");
    String jdk = getString("TxtJavaVersion") + ": " + version;
    String bits = System.getProperty("sun.arch.data.model", null);
    if (bits != null)
    {
      jdk += " (" + bits + "bit)";
    }
    return jdk;
  }

  public static String replaceModifierText(String msg)
  {
    msg = StringUtil.replace(msg, "%shift%", KeyEvent.getModifiersExText(KeyEvent.SHIFT_DOWN_MASK));
    msg = StringUtil.replace(msg, "%control%", KeyEvent.getModifiersExText(PlatformShortcuts.getDefaultModifier()));
    return msg;
  }

  public static boolean isDevBuild()
  {
    String nr = getString("TxtBuildNumber");
    char c = nr.charAt(0);
    return (c == '[' || c == '@');
  }

  public static VersionNumber getBuildNumber()
  {
    String nr = getString("TxtBuildNumber");
    return new VersionNumber(nr);
  }

  public static void setWindowIcons(Window window, List<File> iconFiles)
  {
    if (iconFiles == null) return;
    try
    {
      List<Image> icons = new ArrayList<>(iconFiles.size());
      for (File f : iconFiles)
      {
        URL url = f.toURI().toURL();
        ImageIcon img = new ImageIcon(url);
        icons.add(img.getImage());
      }
      window.setIconImages(icons);
    }
    catch (Throwable ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not set icons!", ex);
      setWindowIcons(window, "workbench");
    }
  }

  public static void setWindowIcons(Window window, String baseName)
  {
    int[] sizes = new int[] {256,128,64,48,32,16};
    List<Image> icons = new ArrayList<>(sizes.length);
    for (int size : sizes)
    {
      ImageIcon image = IconMgr.getInstance().getPngIcon(baseName, size);
      if (image != null)
      {
        icons.add(image.getImage());
      }
    }
    if (icons.size() > 0)
    {
      window.setIconImages(icons);
    }
  }

  public static String getDefaultTabLabel()
  {
    return Settings.getInstance().getProperty("workbench.gui.tabs.defaultlabel", getString("LblTabStatement"));
  }

  public static String getFormattedString(String key, Object ... values)
  {
    return getFormattedString(null, key, values);
  }

  public static String getFormattedString(ResourcePath bundlePath, String key, Object ... values)
  {
    return MessageFormat.format(getString(bundlePath, key, false), values);
  }

  public static String getDynamicString(String baseKey, String option, String dbid)
  {
    return getDynamicString(null, baseKey, option, dbid);
  }

  public static String getDynamicString(ResourcePath bundlePath, String baseKey, String option, String dbid)
  {
    if (baseKey == null || option == null) return null;

    String key = null;
    if (dbid != null)
    {
      key = baseKey + "." + dbid + "." + option.toLowerCase();
      if (!getResources().containsKey(key))
      {
        key = baseKey + "." + option.toLowerCase();
      }
    }
    else
    {
      key = baseKey + "." + option.toLowerCase();
    }

    if (getResources(bundlePath).containsKey(key))
    {
      return getResources(bundlePath).getString(key);
    }
    return null;
  }

  public static String getString(String aKey)
  {
    return getString(null, aKey, false);
  }

  public static String getString(ResourcePath path, String aKey, boolean replaceModifiers)
  {
    try
    {
      String value = getResources(path).getString(aKey);
      if (replaceModifiers)
      {
        return replaceModifierText(value);
      }
      return value;
    }
    catch (MissingResourceException e)
    {
      LogMgr.logWarning(new CallerInfo(){}, "String with key=" + aKey + " not found in resource file!");
      return aKey;
    }
  }

  /**
   * Returns the resource string for the given key with
   * all occurances of &amp; removed.
   * @param aKey
   */
  public static String getPlainString(String aKey)
  {
    String value = getString(aKey).replace("&", "");
    return value;
  }

  /**
   * Returns the description (tooltip) for the specified resource key.
   * The description is retrieved from the resourcebundle by addind a d_ to the
   * passed key.
   *
   * @param aKey
   * @see #getString(java.lang.String)
   */
  public static String getDescription(String aKey)
  {
    return getDescription(aKey, false);
  }

  /**
   *    Returns the description associcate with the given key.
   *    This is used for Tooltips which are associated with a
   *    certain menu text etc.
   */
  public static String getDescription(String aKey, boolean replaceModifiers)
  {
    String value = getString("d_" + aKey);
    if (replaceModifiers)
    {
      value = replaceModifierText(value);
    }
    return value;
  }

  public static InputStream getDefaultSettings()
  {
    InputStream in = ResourceMgr.class.getResourceAsStream("default.properties");

    return in;
  }

  /**
   * For testing purposes
   */
  static ResourceBundle getResourceBundle(Locale l)
  {
    return getResourceBundle(l, null);
  }

  static ResourceBundle getResourceBundle(Locale l, String bundlePath)
  {
    if (bundlePath == null)
    {
      bundlePath = "language";
    }

    if (Settings.getInstance().isUTF8Language(l))
    {
      ResourceBundle.Control control = ResourceBundle.Control.getControl(ResourceBundle.Control.FORMAT_PROPERTIES);
      String bundleName = "/" + control.toBundleName(bundlePath + "/wbstrings", l) + ".properties";
      InputStream in = null;
      Reader r = null;

      try
      {
        in = ResourceMgr.class.getResourceAsStream(bundleName);
        r = new InputStreamReader(in, "UTF-8");
        WbResourceBundle bundle = new WbResourceBundle(r);

        ResourceBundle parent = ResourceBundle.getBundle("language/wbstrings", Locale.ENGLISH);
        bundle.setParent(parent);

        return bundle;
      }
      catch (Exception ex)
      {
        LogMgr.logError(new CallerInfo(){}, "Could not read resource bundle "+ bundleName + " using UTF-8", ex);
      }
      finally
      {
        FileUtil.closeQuietely(in);
      }
    }
    return ResourceBundle.getBundle(bundlePath + "/wbstrings", l);
  }

  public static ResourceBundle getResources()
  {
    return getResources(null);
  }

  public static ResourceBundle getResources(ResourcePath bundlePath)
  {
    if (resources == null && bundlePath == null)
    {
      Locale l = Settings.getInstance().getLanguage();
      resources = getResourceBundle(l);

      boolean setDefaultLocale = Settings.getInstance().getBoolProperty(PROP_CHANGE_LOCALE, true);
      if (setDefaultLocale)
      {
        LogMgr.logInfo(new CallerInfo(){}, "Setting default locale to: " + l.toString());
        Locale.setDefault(l);
      }
      else
      {
        Locale def = Locale.getDefault();
        LogMgr.logInfo(new CallerInfo(){}, "Default locale is : " + def.toString());
      }
    }
    else if (bundlePath != null)
    {
      Locale l = Settings.getInstance().getLanguage();
      return getResourceBundle(l, bundlePath.getPath());
    }
    return resources;
  }

}
