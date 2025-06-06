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
package workbench.gui.macros;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Insets;

import javax.swing.border.AbstractBorder;

/**
 *
 * @author Thomas Kellerer
 */
public class ReorderBorder
  extends AbstractBorder
{
  private final int thickness = 2;
  private Color color = Color.DARK_GRAY;

  public ReorderBorder()
  {
    super();
  }

  @Override
  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height)
  {
    Color oldColor = g.getColor();
    g.setColor(color);
    final int arrowLen = 4;
    for (int i=0; i < arrowLen; i++)
    {
      g.drawLine(x + i, y, x + i, y + (arrowLen-i));
    }
    g.fillRect(x, y, width, thickness);
    g.setColor(oldColor);
  }

  @Override
  public Insets getBorderInsets(Component c)
  {
    return new Insets(2, 2, 2, 2);
  }

  @Override
  public Insets getBorderInsets(Component c, Insets insets)
  {
    insets.left = insets.top = insets.right = insets.bottom = 2;
    return insets;
  }

}

