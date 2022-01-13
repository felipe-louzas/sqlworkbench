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
package workbench.gui.lnf;

import java.awt.Color;
import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilenameFilter;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Set;

import javax.swing.LookAndFeel;
import javax.swing.UIDefaults;
import javax.swing.UIManager;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.GuiSettings;
import workbench.resource.Settings;

import workbench.util.ClasspathUtil;
import workbench.util.CollectionUtil;
import workbench.util.PlatformHelper;
import workbench.util.StringUtil;
import workbench.util.WbFile;

/**
 * Initialize some GUI elements during startup.
 *
 * @author Thomas Kellerer
 */
public class LnFHelper
{
  public static final String MENU_FONT_KEY = "MenuItem.font";
  public static final String LABEL_FONT_KEY = "Label.font";
  public static final String TREE_FONT_KEY = "Tree.font";

  private LnFManager lnfManager = new LnFManager();

  // Font properties that are automatically scaled by Java
  private final Set<String> noScale = CollectionUtil.treeSet(
    "Menu.font",
    "MenuBar.font",
    "MenuItem.font",
    "PopupMenu.font",
    "CheckBoxMenuItem.font");

  private final Set<String> fontProperties = CollectionUtil.treeSet(
    "Button.font",
    "CheckBox.font",
    "CheckBoxMenuItem.font",
    "ColorChooser.font",
    "ComboBox.font",
    "EditorPane.font",
    "FileChooser.font",
    LABEL_FONT_KEY,
    "List.font",
    "Menu.font",
    "MenuBar.font",
    MENU_FONT_KEY,
    "OptionPane.font",
    "Panel.font",
    "PasswordField.font",
    "PopupMenu.font",
    "ProgressBar.font",
    "RadioButton.font",
    "RadioButtonMenuItem.font",
    "ScrollPane.font",
    "Slider.font",
    "Spinner.font",
    "TabbedPane.font",
    "TextArea.font",
    "TextField.font",
    "TextPane.font",
    "TitledBorder.font",
    "ToggleButton.font",
    "ToolBar.font",
    "ToolTip.font",
    TREE_FONT_KEY,
    "ViewPort.font");

  private static boolean isWebLaf;
  private static boolean isFlatLaf;
  private static boolean isJGoodies;
  private static boolean isWindowsLnF;

  public static boolean isJGoodies()
  {
    return isJGoodies;
  }

  public static boolean isWebLaf()
  {
    return isWebLaf;
  }

  public static boolean isFlatLaf()
  {
    return isFlatLaf;
  }

  public static boolean isWindowsLookAndFeel()
  {
    return isWindowsLnF;
  }

  public static int getMenuFontHeight()
  {
    return getFontHeight(MENU_FONT_KEY);
  }

  public static int getLabelFontHeight()
  {
    return getFontHeight(LABEL_FONT_KEY);
  }

  private static int getFontHeight(String key)
  {
    UIDefaults def = UIManager.getDefaults();
    double factor = Toolkit.getDefaultToolkit().getScreenResolution() / 72.0;
    Font font = def.getFont(key);
    if (font == null) return 18;
    return (int)Math.ceil((double)font.getSize() * factor);
  }

  public void initUI()
  {
    initializeLookAndFeel();

    Settings settings = Settings.getInstance();
    UIDefaults def = UIManager.getDefaults();

    Font stdFont = settings.getStandardFont();
    if (stdFont != null)
    {
      for (String property : fontProperties)
      {
        def.put(property, stdFont);
      }
    }
    else if (isWindowsLookAndFeel())
    {
      // The default Windows look and feel does not scale the fonts properly
      scaleDefaultFonts();
    }

    Font dataFont = settings.getDataFont();
    if (dataFont != null)
    {
      def.put("Table.font", dataFont);
      def.put("TableHeader.font", dataFont);
    }

    if (settings.getBoolProperty("workbench.gui.adjustgridcolor", true))
    {
      Color c = settings.getColor("workbench.table.gridcolor", new Color(215,215,215));
      def.put("Table.gridColor", c);
    }

    def.put("Button.showMnemonics", Boolean.valueOf(GuiSettings.getShowMnemonics()));
    UIManager.put("Synthetica.extendedFileChooser.rememberLastDirectory", false);
  }

  private void setDefaultFonts()
  {
    // for some reason the default menu font is properly scaled
    // on HiDPI displays. So we are adjusting all other fonts
    // to the size of the menu font.
    String prop = Settings.getInstance().getReferenceFontName();
    Font referenceFont = UIManager.getFont(prop);
    if (referenceFont == null)
    {
      referenceFont = UIManager.getFont("Menu.font");
      prop = "Menu.font";
    }

    UIDefaults def = UIManager.getDefaults();
    for (String property : fontProperties)
    {
      if (prop.equals(property)) continue;

      Font base = def.getFont(property);
      if (base != null)
      {
        Font scaled = base.deriveFont(base.getStyle(), referenceFont.getSize());
        def.put(property, scaled);
      }
    }
  }

  private boolean isSystemScaled()
  {
    try
    {
      GraphicsConfiguration config = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
      return config.getDefaultTransform().getScaleX() > 1.0;
    }
    catch (Throwable th)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not get graphics configuration", th);
    }
    return false;
  }

  private void scaleDefaultFonts()
  {
    FontScaler scaler = new FontScaler();
    scaler.logSettings();

    if (!Settings.getInstance().getScaleFonts() || !scaler.doScaleFonts())
    {
      if (Settings.getInstance().getUseReferenceFont() && (scaler.isHiDPI() || isSystemScaled()))
      {
        // It seems that Java scales the Menu font properly, but not the rest.
        // so we use the menu font as the default font for everything
        setDefaultFonts();
      }
      return;
    }

    LogMgr.logInfo(new CallerInfo(){}, "Scaling default fonts by: " + scaler.getScaleFactor());

    UIDefaults def = UIManager.getDefaults();

    // when the user configures a scale factor, don't check the menu fonts
    boolean checkJavaFonts = Settings.getInstance().getScaleFactor() < 0;

    for (String property : fontProperties)
    {
      if (checkJavaFonts && noScale.contains(property)) continue;
      Font base = def.getFont(property);
      if (base != null)
      {
        Font scaled = scaler.scaleFont(base);
        def.put(property, scaled);
      }
    }
  }

  private String getDefaultLookAndFeel()
  {
    if (PlatformHelper.isWindows())
    {
      return UIManager.getSystemLookAndFeelClassName();
    }
    if (PlatformHelper.isLinux() && lnfManager.isFlatLafLibPresent())
    {
      return LnFManager.FLATLAF_LIGHT_CLASS;
    }
    return UIManager.getSystemLookAndFeelClassName();
  }

  protected void initializeLookAndFeel()
  {
    String className = GuiSettings.getLookAndFeelClass();
    File flatLafTheme = null;

    try
    {
      if (StringUtil.isEmptyString(className))
      {
        className = getDefaultLookAndFeel();
      }
      LnFDefinition def = lnfManager.findLookAndFeel(className);

      if (def == null)
      {
        LogMgr.logError(new CallerInfo(){}, "Specified Look & Feel " + className + " not available!", null);
        setSystemLnF();
      }
      else
      {
        // Fix for bug: https://bugs.java.com/bugdatabase/view_bug.do?bug_id=8179014
        // under Windows 10 with the "Creators Update"
        if (className.contains(".plaf.windows.") && Settings.getInstance().getBoolProperty("workbench.gui.fix.filechooser.bug", false))
        {
          UIManager.put("FileChooser.useSystemExtensionHiding", false);
        }
        UIManager.put("FileChooser.useSystemIcons", Boolean.TRUE);

        // I hate the bold menu font in the Metal LnF
        UIManager.put("swing.boldMetal", Boolean.FALSE);

        // Remove Synthetica's own window decorations
        UIManager.put("Synthetica.window.decoration", Boolean.FALSE);

        // Remove the extra icons for read only text fields and
        // the "search bar" in the main menu for the Substance Look & Feel
        System.setProperty("substancelaf.noExtraElements", "");

        if (className.startsWith("org.jb2011.lnf.beautyeye"))
        {
          UIManager.put("RootPane.setupButtonVisible", false);
        }

        LnFLoader loader = new LnFLoader(def);

        // Enable configuration of FlatLaf options
        if (className.startsWith("com.formdev.flatlaf"))
        {
          isFlatLaf = true;
          flatLafTheme = getFlatLafTheme();
          if (flatLafTheme == null)
          {
            configureFlatLaf(loader);
          }
        }
        else if (className.contains("plaf.windows"))
        {
          isWindowsLnF = true;
        }
        else if (className.startsWith("com.jgoodies.looks.plastic"))
        {
          isJGoodies = true;
        }

        LookAndFeel lnf = null;
        if (flatLafTheme != null)
        {
          lnf = loadFlatLafTheme(loader, flatLafTheme);
        }

        if (lnf == null)
        {
          lnf = loader.getLookAndFeel();
        }

        UIManager.setLookAndFeel(lnf);

        if (className.startsWith("com.alee.laf"))
        {
          isWebLaf = true;
          initializeWebLaf();
        }
        PlatformHelper.installGtkPopupBugWorkaround();
      }
    }
    catch (Throwable e)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not set look and feel to [" + className + "]. Look and feel will be ignored", e);
      setSystemLnF();
    }
  }

  private File getFlatLafTheme()
  {
    ClasspathUtil cp = new ClasspathUtil();
    WbFile dir = cp.getExtDir();
    if (dir == null || !dir.exists()) return null;
    FilenameFilter themeFilter = (File dir1, String name) -> name != null && name.toLowerCase().endsWith(".theme.json");

    File[] themes = dir.listFiles(themeFilter);
    if (themes != null && themes.length > 0)
    {
      if (themes.length > 1)
      {
        LogMgr.logWarning(new CallerInfo(){}, "Found multiple FlatLaf themes in: \"" + dir + "\". Using: " + themes[0].getName());
      }
      return themes[0];
    }
    return null;
  }

  private LookAndFeel loadFlatLafTheme(LnFLoader loader, File themeFile)
  {
    if (!themeFile.exists()) return null;
    try (InputStream in = new FileInputStream(themeFile))
    {
      Class theme = loader.loadClass("com.formdev.flatlaf.IntelliJTheme", false);
      Method createLaf = theme.getMethod("createLaf", InputStream.class);
      LookAndFeel lnf = (LookAndFeel)createLaf.invoke(theme, in);
      LogMgr.logInfo(new CallerInfo(){}, "Loaded FlatLaf theme from " + themeFile);
      return lnf;
    }
    catch (Throwable th)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not load FlatLaf theme " + themeFile.getAbsolutePath());
      return null;
    }
  }

  private void configureFlatLaf(LnFLoader loader)
  {
    try
    {
      Class flatLaf = loader.loadClass("com.formdev.flatlaf.FlatLaf", false);
      Method registerPackage = flatLaf.getMethod("registerCustomDefaultsSource", String.class, ClassLoader.class);
      registerPackage.invoke(null, "workbench.resource", getClass().getClassLoader());

      Method registerDir = flatLaf.getMethod("registerCustomDefaultsSource", File.class);
      ClasspathUtil util = new ClasspathUtil();
      File extDir = util.getExtDir();
      File jarDir = util.getJarDir();
      registerDir.invoke(null, extDir);
      registerDir.invoke(null, jarDir);
    }
    catch (Throwable th)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not initialize FlatLaf");
    }
  }

  private void initializeWebLaf()
  {
    try
    {
      LookAndFeel lookAndFeel = UIManager.getLookAndFeel();
      Method init = lookAndFeel.getClass().getMethod("initializeManagers");
      init.invoke(null, (Object[])null);

      UIManager.getDefaults().put("ToolBarUI", "com.alee.laf.toolbar.WebToolBarUI");
      UIManager.getDefaults().put("TabbedPaneUI", "com.alee.laf.toolbar.WebTabbedPaneUI");
      UIManager.getDefaults().put("SplitPaneUI", "com.alee.laf.splitpane.WebSplitPaneUI");
    }
    catch (Throwable th)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not initialize WebLaf", th);
    }
  }

  private void setSystemLnF()
  {
    try
    {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    }
    catch (Exception ex)
    {
      // should not happen
    }
  }
}
