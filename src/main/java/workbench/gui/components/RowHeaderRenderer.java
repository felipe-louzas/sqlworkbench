/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2024 Thomas Kellerer
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

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.geom.Rectangle2D;

import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumn;

import workbench.resource.GuiSettings;

import workbench.gui.renderer.ToolTipRenderer;

import workbench.util.NumberStringCache;

/**
 * A TableRowHeader to show row numbers in a JTable
 *
 * @author Thomas Kellerer
 */
public class RowHeaderRenderer
  extends JLabel
  implements TableCellRenderer
{
  private final JTable table;
  private final TableRowHeader rowHeader;
  private int colWidth = -1;
  private final int rightMargin;
  private final Insets insets = ToolTipRenderer.getDefaultInsets();
  protected boolean useInsetsFromLnF = false;

  public RowHeaderRenderer(TableRowHeader rowHeader, JTable client)
  {
    super();
    this.table = client;
    this.rowHeader = rowHeader;
    useInsetsFromLnF = UIManager.getDefaults().getInsets("Table.cellMargins") != null;

    JTableHeader header = table.getTableHeader();
    setFont(header.getFont());
    setOpaque(true);
    setHorizontalAlignment(SwingConstants.RIGHT);
    setVerticalAlignment(SwingConstants.CENTER);
    setVerticalTextPosition(SwingConstants.BOTTOM);
    setForeground(header.getForeground());
    setBackground(header.getBackground());

    rightMargin = GuiSettings.getRowNumberMargin();
    calculateWidth();
  }

  @Override
  public Insets getInsets(Insets ins)
  {
    return this.insets;
  }

  @Override
  public Insets getInsets()
  {
    return insets;
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column)
  {
    adjustInsets(table.getFont());
    this.setText((String)value);
    return this;
  }

  @Override
  public void setFont(Font f)
  {
    Font oldFont = getFont();
    super.setFont(f);
    boolean fontChanged = (oldFont == null && f != null) || (oldFont != null && f != null && !f.equals(oldFont));
    if (table != null && fontChanged)
    {
      calculateWidth();
    }
  }

  public final void calculateWidth()
  {
    FontMetrics fm = getFontMetrics(getFont());
    int width = 12;
    try
    {
      if (fm != null)
      {
        Rectangle2D r = fm.getStringBounds("0", getGraphics());
        width = r.getBounds().width;
      }
    }
    catch (Exception e)
    {
      width = 12;
    }
    String max = NumberStringCache.getNumberString(table.getRowCount());
    colWidth = (max.length() * width) + width + rightMargin + 1;

    try
    {
      TableColumn col = rowHeader.getColumnModel().getColumn(0);
      col.setPreferredWidth(colWidth);
      col.setMaxWidth(colWidth);

      col = rowHeader.getTableHeader().getColumnModel().getColumn(0);
      col.setPreferredWidth(colWidth);
      col.setMaxWidth(colWidth);

      Dimension psize = rowHeader.getPreferredSize();
      Dimension size = new Dimension(colWidth, psize.height);

      rowHeader.setMaximumSize(size);
      rowHeader.setSize(size);
      rowHeader.setPreferredScrollableViewportSize(size);
    }
    catch (Exception e)
    {
      e.printStackTrace();
    }
  }

  private void adjustInsets(Font f)
  {
    if (useInsetsFromLnF || f == null) return;
    FontMetrics fm = getFontMetrics(f);
    if (fm == null) return;

    int top = fm.getLeading() + 1;
    if (top != insets.top)
    {
      insets.set(top, insets.left, insets.bottom, insets.right);
    }
  }

}
