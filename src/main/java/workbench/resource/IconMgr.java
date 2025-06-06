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
package workbench.resource;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.File;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import javax.swing.ImageIcon;
import javax.swing.JComponent;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.gui.WbSwingUtilities;
import workbench.gui.lnf.LnFHelper;

import workbench.util.PlatformHelper;

/**
 *
 * @author Thomas Kellerer
 */
public class IconMgr
{
  public static final String IMG_SAVE = "save";

  public static final int SMALL_ICON = 16;
  public static final int MEDIUM_ICON = 24;
  public static final int LARGE_ICON = 32;

  private final RenderingHints scaleHints;

  private final Map<String, ImageIcon> iconCache = new HashMap<>();
  private final String filepath;

  private final int menuFontHeight;
  private final int labelFontHeight;
  private final int toolbarIconSize;
  private final boolean scaleMenuIcons;

  private static class LazyInstanceHolder
  {
    private static final IconMgr INSTANCE = new IconMgr();
  }

  public static IconMgr getInstance()
  {
    return LazyInstanceHolder.INSTANCE;
  }

  private IconMgr()
  {
    filepath = ResourcePath.ICONS.getPath() + "/";
    menuFontHeight = LnFHelper.getMenuFontHeight();
    labelFontHeight = LnFHelper.getLabelFontHeight();
    boolean isHiDPI = Toolkit.getDefaultToolkit().getScreenSize().width > 2000;
    scaleMenuIcons = (isHiDPI && PlatformHelper.isLinux()) || Settings.getInstance().getScaleMenuIcons();
    toolbarIconSize = retrieveToolbarIconSize();

    scaleHints = new RenderingHints(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    scaleHints.put(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);

    LogMgr.logInfo(new CallerInfo(){}, "Using sizes: toolbar: " + toolbarIconSize + ", menu: " + getSizeForMenuItem());
  }

  private int retrieveToolbarIconSize()
  {
    int size = Settings.getInstance().getToolbarIconSize();
    if (size < SMALL_ICON)
    {
      LogMgr.logError(new CallerInfo(){}, "Invalid icon size: " + size + " specified. Reverting to 16!", null);
      size = SMALL_ICON;
      Settings.getInstance().setToolbarIconSize(size);
    }

    if (scaleMenuIcons)
    {
      int menuSize = getSizeForMenuItem();
      if (menuSize > size) return menuSize;
    }
    return size;
  }

  public int getToolbarIconSize()
  {
    return toolbarIconSize;
  }

  /**
   * Return a GIF icon in a size suitable for the toolbar.
   *
   * @param basename the basename of the GIF icon
   * @return the icon
   * @see Settings#getToolbarIconSize()
   */
  public ImageIcon getGifIcon(String baseName)
  {
    return getIcon(baseName, toolbarIconSize, false);
  }

  /**
   * Get a picture based on the complete filename name.
   *
   * This method will not try to detect the correct image size for filename,
   * but takes it "as is".
   *
   * @param filename  the complete filename of the picture
   * @return
   */
  public ImageIcon getPicture(String filename)
  {
    return retrieveImage(filename);
  }

  /**
   * Return a PNG icon in the requested size.
   *
   * @param basename the basename of the GIF icon
   * @param size     the icon size in pixel (16,24,32)
   * @return the icon
   */
  public ImageIcon getPngIcon(String basename, int size)
  {
    return getIcon(basename, size, true);
  }

  /**
   * Return a PNG icon in a size suitable for the toolbar.
   *
   * @param basename the basename of the GIF icon
   * @return the icon
   * @see Settings#getToolbarIconSize()
   */
  public ImageIcon getToolbarIcon(String basename)
  {
    return getIcon(basename.toLowerCase(), toolbarIconSize, true);
  }

  /**
   * Calculate the icon size for a menu item in the UI
   *
   * @return the preferred icon size (16,24,32)
   * @see LnFHelper#getMenuFontHeight()
   * @see #getSizeForFont(int)
   */
  public int getSizeForMenuItem()
  {
    if (scaleMenuIcons)
    {
      return getSizeForFont(menuFontHeight);
    }
    return SMALL_ICON;
  }

  /**
   * Calculate the icon size for a label in the UI
   *
   * @return the preferred icon size (16,24,32)
   * @see LnFHelper#getLabelFontHeight()
   * @see #getSizeForFont(int)
   */
  public int getSizeForLabel()
  {
    if (scaleMenuIcons)
    {
      return getSizeForFont(labelFontHeight);
    }
    return SMALL_ICON;
  }

  /**
   * Calculate the corresponding image size for the given fontheight.
   *
   * @param fontHeight the height as returned by Font.getSize();
   * @return the approriate icon size for the font (16,24,32)
   */
  public int getSizeForFont(int fontHeight)
  {
    if (fontHeight < 24)
    {
      return SMALL_ICON;
    }
    else if (fontHeight < 32)
    {
      return MEDIUM_ICON;
    }
    return LARGE_ICON;
  }

  /**
   * Calculate the corresponding image size based on the font of the component.
   *
   * @param fontHeight the height as returned by Font.getSize();
   * @return the approriate icon size for the font (16,24,32)
   * @see WbSwingUtilities#getFontHeight(javax.swing.JComponent)
   */
  public int getSizeForComponentFont(JComponent comp)
  {
    int fontHeight = 16;
    if (scaleMenuIcons)
    {
      fontHeight = WbSwingUtilities.getFontHeight(comp);
    }
    return getSizeForFont(fontHeight);
  }

  /**
   * Return a PNG icon properly sized for a label in the UI.
   *
   * @param basename the basename of the GIF icon
   * @return
   */
  public ImageIcon getLabelIcon(String basename)
  {
    int imgSize = getSizeForLabel();
    return getIcon(basename.toLowerCase(), imgSize, true);
  }

  /**
   * Return an image icon used to show the "loading" image in the SQL Panel.
   *
   * These images are currently not adjusted to the correct size.
   * The loading images are also not cached to allow clearing their state using flush()
   *
   * @param baseName
   * @return the icon
   */
  public ImageIcon getLoadingImage(String baseName)
  {
    return retrieveImage(baseName + ".gif");
  }

  public ImageIcon getIcon(String baseName, int imageSize, boolean isPng)
  {
    String fname = makeFilename(baseName, imageSize, isPng);

    boolean useCache = Settings.getInstance().getCacheIcons();

    ImageIcon result = null;

    synchronized (this)
    {
      result = useCache ? iconCache.get(fname) : null;
      if (result == null)
      {
        result = retrieveImage(fname);
        if (result == null)
        {
          if (imageSize > SMALL_ICON)
          {
            LogMgr.logDebug(new CallerInfo(){}, "Icon " + fname + " not found. Scaling existing 16px image");
            ImageIcon small = retrieveImage(makeBaseFilename(baseName, isPng));
            result = scale(small, imageSize);
          }
          else
          {
            LogMgr.logWarning(new CallerInfo(){}, "Icon " + fname + " not found!");
          }
        }
        if (useCache) iconCache.put(fname, result);
      }
    }
    return result;
  }

  private String makeBaseFilename(String basename, boolean isPng)
  {
    return makeFilename(basename, SMALL_ICON, isPng);
  }

  private String makeFilename(String basename, int size, boolean isPng)
  {
    String sz = Integer.toString(size);
    if (isPng) basename = basename.toLowerCase();
    return basename + (isPng ? sz + ".png" : sz + ".gif");
  }

  public ImageIcon getExternalImage(String fname, int resizeTo)
  {
    File f = new File(fname);
    if (!f.isAbsolute())
    {
      f = new File(Settings.getInstance().getConfigDir(), fname);
    }

    if (!f.exists()) return null;

    ImageIcon result = null;

    fname = f.getAbsolutePath();

    if (Settings.getInstance().getCacheIcons())
    {
      result = iconCache.get(fname);
    }

    if (result == null)
    {
      result = new ImageIcon(fname);
    }

    if (resizeTo > 0 && (result.getIconWidth() > resizeTo || result.getIconHeight() > resizeTo))
    {
      result = scale(result, resizeTo);
    }
    return result;
  }

  private ImageIcon retrieveImage(String fname)
  {
    ImageIcon result = null;
    URL imageIconUrl = getClass().getClassLoader().getResource(filepath + fname);
    if (imageIconUrl != null)
    {
      result = new ImageIcon(imageIconUrl);
    }
    return result;
  }

  private ImageIcon scale(ImageIcon original, int imageSize)
  {
    if (original == null) return null;
    BufferedImage bi = new BufferedImage(imageSize, imageSize, BufferedImage.TRANSLUCENT);
    Graphics2D g2d = null;
    try
    {
      g2d = (Graphics2D) bi.createGraphics();
      g2d.addRenderingHints(scaleHints);
      g2d.drawImage(original.getImage(), 0, 0, imageSize, imageSize, null);
    }
    catch (Throwable th)
    {
      Image image = original.getImage();
      if (image == null) return original;
      return new ImageIcon(image.getScaledInstance(imageSize, imageSize, Image.SCALE_SMOOTH));
    }
    finally
    {
      if (g2d != null)
      {
        g2d.dispose();
      }
    }
    return new ImageIcon(bi);
  }

}
