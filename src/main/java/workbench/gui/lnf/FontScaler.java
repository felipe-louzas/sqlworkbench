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
package workbench.gui.lnf;

import java.awt.Font;
import java.awt.Toolkit;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

/**
 * A class to scale fonts according to the DPI settings of the Desktop.
 *
 * The reference (i.e. a scale of 1.0) is assumed to be 96 DPI.
 *
 * If the user configured a customized scale factor
 *
 * @author Thomas Kellerer
 * @see Settings#getScaleFactor()
 */
public class FontScaler
{
  private final boolean scaleFont;
  private final int dpi;
  private final int defaultDPI;
  private final float scaleFactor;
  private final int defaultFontSize;
  
  public FontScaler()
  {
    defaultFontSize = Settings.getInstance().getDefaultFontSize();
    dpi = Toolkit.getDefaultToolkit().getScreenResolution();
    defaultDPI = Settings.getInstance().getIntProperty("workbench.gui.desktop.defaultdpi", 96);

    float factor = Settings.getInstance().getScaleFactor();
    if (factor > 0)
    {
      // manually configured scale factor
      scaleFont = true;
      scaleFactor = factor;
    }
    else
    {
      if (defaultFontSize > -1)
      {
        scaleFont = true;
        scaleFactor = 0;
      }
      else if (dpi == defaultDPI)
      {
        scaleFont = false;
        scaleFactor = 1.0f;
      }
      else
      {
        scaleFont = true;
        scaleFactor = ((float)dpi / (float)defaultDPI);
      }
    }
  }

  public boolean isHiDPI()
  {
    return dpi >= 120;
  }

  public boolean doScaleFonts()
  {
    return scaleFont;
  }

  public int getCurrentDPI()
  {
    return dpi;
  }
  
  public void logSettings()
  {
    LogMgr.logInfo(new CallerInfo(){}, "Current DPI: "  + dpi + ", Default DPI: " + defaultDPI + ", isHiDPI: " + isHiDPI());
  }

  public float getScaleFactor()
  {
    return scaleFactor;
  }

  public Font scaleFont(Font baseFont)
  {
    if (!scaleFont) return baseFont;
    if (baseFont == null) return null;
    
    float newSize;
    if (defaultFontSize > -1) 
    {
      newSize = defaultFontSize;
    }
    else
    {
      newSize  = baseFont.getSize2D() * scaleFactor;
    }
    Font scaled = baseFont.deriveFont(newSize);
    return scaled;
  }

}
