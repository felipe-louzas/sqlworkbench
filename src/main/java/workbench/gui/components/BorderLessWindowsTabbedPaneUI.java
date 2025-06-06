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
import java.awt.Graphics;
import java.awt.Insets;

import javax.swing.JTabbedPane;
import javax.swing.UIManager;

import workbench.gui.WbSwingUtilities;

import com.sun.java.swing.plaf.windows.WindowsTabbedPaneUI;

import static javax.swing.SwingConstants.*;

/**
 *
 * @author Thomas Kellerer
 */
public class BorderLessWindowsTabbedPaneUI
  extends WindowsTabbedPaneUI
  implements TabHighlightSupport
{
  private TabHighlighter highlighter;
  private Color selColor;

  @Override
  public void setTabHighlighter(TabHighlighter highlight)
  {
    this.highlighter = highlight;
  }

  @Override
  protected void installDefaults()
  {
    super.installDefaults();
    selColor = UIManager.getColor("TabbedPane.selected");
  }

  @Override
  protected Insets getContentBorderInsets(int tabPlacement)
  {
    switch (tabPlacement)
    {
      case JTabbedPane.TOP:
        return new Insets(1, 0, 0, 0);
      case JTabbedPane.BOTTOM:
        return new Insets(0, 0, 0, 1);
      case JTabbedPane.LEFT:
        return new Insets(0, 0, 0, 1);
      case JTabbedPane.RIGHT:
        return new Insets(0, 1, 0, 0);
      default:
        return WbSwingUtilities.getEmptyInsets();
    }
  }

  @Override
  protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex, int x, int y, int w, int h, boolean isSelected)
  {
    super.paintTabBorder(g, tabPlacement, tabIndex, x, y, w, h, isSelected);
    if (highlighter != null)
    {
      highlighter.paintTabBorder(g, tabPlacement, tabIndex, x, y, w, h, isSelected);
    }
  }

  @Override
  protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex)
  {
    int width = tabPane.getWidth();
    int height = tabPane.getHeight();
    Insets insets = tabPane.getInsets();

    int x = insets.left;
    int y = insets.top;
    int w = width - insets.right - insets.left;
    int h = height - insets.top - insets.bottom;

    switch (tabPlacement)
    {
      case LEFT:
        x += calculateTabAreaWidth(tabPlacement, runCount, maxTabWidth);
        w -= (x - insets.left);
        break;
      case RIGHT:
        w -= calculateTabAreaWidth(tabPlacement, runCount, maxTabWidth);
        break;
      case BOTTOM:
        h -= calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight);
        break;
      case TOP:
      default:
        y += calculateTabAreaHeight(tabPlacement, runCount, maxTabHeight);
        h -= (y - insets.top);
    }
    // Fill region behind content area
    if (selColor == null)
    {
      g.setColor(tabPane.getBackground());
    }
    else
    {
      g.setColor(Color.RED);
    }
    g.fillRect(x, y, w, h);

    switch (tabPlacement)
    {
      case JTabbedPane.TOP:
        paintContentBorderTopEdge(g, tabPlacement, selectedIndex, x, y, w, h);
        break;
      case JTabbedPane.BOTTOM:
        paintContentBorderBottomEdge(g, tabPlacement, selectedIndex, x, y, w, h);
        break;
      case JTabbedPane.LEFT:
        paintContentBorderLeftEdge(g, tabPlacement, selectedIndex, x, y, w, h);
        break;
      case JTabbedPane.RIGHT:
        paintContentBorderRightEdge(g, tabPlacement, selectedIndex, x, y, w, h);
        break;
    }
  }
}
