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

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.util.HashMap;

import javax.swing.Icon;
import javax.swing.UIManager;

import workbench.resource.Settings;

import workbench.gui.renderer.ColorUtils;

/**
 *
 * @author Thomas Kellerer
 */
public class SortArrowIcon
  implements Icon
{
  public enum Direction
  {
    UP,
    DOWN;
  }

  private final Direction direction;
  private final int width;
  private final int height;

  private static final HashMap<Integer, SortArrowIcon> sharedUpArrows = new HashMap<>(2);
  private static final HashMap<Integer, SortArrowIcon> sharedDownArrows = new HashMap<>(2);
  private final int blendValue;
  private Color flatLafColor;
  public static synchronized SortArrowIcon getIcon(Direction dir, int size)
  {
    HashMap<Integer, SortArrowIcon> cache = (dir == Direction.UP ? sharedUpArrows : sharedDownArrows);

    Integer key = Integer.valueOf(size);
    SortArrowIcon icon = cache.get(key);
    if (icon == null)
    {
      icon = new SortArrowIcon(dir, size);
      cache.put(key, icon);
    }
    return icon;
  }

  private SortArrowIcon(Direction dir, int size)
  {
    direction = dir;
    width = (int)(size * 1.1);
    height = size;
    blendValue = Settings.getInstance().getIntProperty("workbench.gui.sorticon.blend", 128);
    flatLafColor = UIManager.getDefaults().getColor("TableHeader.sortIconColor");
  }

  @Override
  public int getIconWidth()
  {
    return width;
  }

  @Override
  public int getIconHeight()
  {
    return height;
  }

  private Color getArrowColor(Color background)
  {
    if (flatLafColor != null) return flatLafColor;
    return ColorUtils.blend(background, Color.BLACK, blendValue);
  }

  @Override
  public void paintIcon(Component c, Graphics g, int x, int y)
  {
    Color bg = c.getBackground();
    Color fg = c.getForeground();

    Color arrowColor = getArrowColor(bg);
    int w = width;
    int h = height;
    int top = y + h;
    int bottom = y;

    int[] xPoints = new int[] {x, x + w/2, x + w}; // left, middle, right
    int[] yPoints;

    if (direction == Direction.UP)
    {
      yPoints = new int[] {top - 1, bottom - 1, top - 1};
    }
    else
    {
      yPoints = new int[] {bottom, top, bottom};
    }

    g.setColor(arrowColor);
    g.fillPolygon(xPoints, yPoints, 3);
    g.setColor(fg);
  }
}

