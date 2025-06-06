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
package workbench.gui.components;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

import javax.swing.Icon;
import javax.swing.UIManager;

import workbench.resource.IconMgr;

/**
 *
 * @author Thomas Kellerer
 */
public class CloseIcon
  implements Icon
{
  private static final float SMALL_SIZE_FACTOR = 0.3f;
  private static final float LARGE_SIZE_FACTOR = 0.15f;
  private static final Color DISABLED_FG_COLOR = UIManager.getDefaults().getColor("Button.disabledForeground");

  private final BasicStroke stroke;
  private final Color foregroundColor;
  private final Color backgroundColor;
  private int offset;
  private final int size;

  public CloseIcon()
  {
    this((int)(IconMgr.getInstance().getToolbarIconSize() * 0.95));
  }

  public CloseIcon(int iconSize)
  {
    this(iconSize, null, null);
  }

  public CloseIcon(int iconSize, Color foreground, Color background)
  {
    // only use even sizes
    size = (iconSize % 2 == 0 ? iconSize : iconSize + 1);
    foregroundColor = foreground;
    backgroundColor = background;
    stroke = new BasicStroke(size / 12f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER);
    calculateSize(false);
  }

  public void setUseLargeSize(boolean flag)
  {
    calculateSize(flag);
  }

  private void calculateSize(boolean useLargeSize)
  {
    int pct;
    if (useLargeSize)
    {
      pct = (int)Math.ceil(size * LARGE_SIZE_FACTOR);
    }
    else
    {
      pct = (int)Math.ceil(size * SMALL_SIZE_FACTOR);
    }
    if (pct % 2 != 0)
    {
      pct ++;
    }
    offset = pct / 2;
  }

  @Override
  public int getIconWidth()
  {
    return size;
  }

  @Override
  public int getIconHeight()
  {
    return size;
  }

  private Color getForeground(Component c)
  {
    if (c == null) return Color.BLACK;
    if (c.isEnabled())
    {
      return foregroundColor == null ? c.getForeground() : foregroundColor;
    }
    return DISABLED_FG_COLOR == null ? c.getForeground() : DISABLED_FG_COLOR;
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y)
  {
    Graphics2D g2 = (Graphics2D)g;

    int p1 = offset;
    int p2 = size - offset;

    g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

    if (backgroundColor != null)
    {
      g2.setColor(backgroundColor);
      g2.fillRect(x, y, x + size, y + size);
    }

    g2.setColor(getForeground(c));
    g2.setStroke(stroke);
    g2.drawLine(p1 + x, p1 + y, p2 + x, p2 + y);
    g2.drawLine(p2 + x, p1 + y, p1+ x, p2 + y);
  }
}

