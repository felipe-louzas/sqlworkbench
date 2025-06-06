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
package workbench.gui.renderer;

import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.io.StringReader;

import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.table.TableCellRenderer;

import workbench.resource.GuiSettings;
import workbench.resource.Settings;

import workbench.gui.WbSwingUtilities;

import workbench.util.StringUtil;

/**
 * A renderer to display multi-line character data.
 * <br/>
 * The renderer uses a JTextArea internally which is a lot slower than the own
 * drawing of the text implemented in ToolTipRender. But ToolTipRenderer
 * cannot cope with line breaks
 *
 * @author Thomas Kellerer
 */
public class TextAreaRenderer
  extends ToolTipRenderer
  implements TableCellRenderer, WbRenderer
{
  private final JTextArea textDisplay;
  protected boolean useStringReader;
  private final boolean clipToFirstLine = GuiSettings.getMultilineRendererClipToFirstLine();
  private final int maxColumnWidth = GuiSettings.getMaxColumnWidth();

  public TextAreaRenderer()
  {
    super();
    clipLimit = GuiSettings.getClipLongMultiRendererValues();
    useOwnPaint = false;
    textDisplay = new JTextArea()
    {
      @Override
      public Insets getInsets()
      {
        return regularInsets;
      }

      @Override
      public Insets getMargin()
      {
        return WbSwingUtilities.getEmptyInsets();
      }
    };

    boolean wrap = GuiSettings.getWrapMultilineRenderer();
    useStringReader = GuiSettings.getUseReaderForMultilineRenderer();

    textDisplay.setWrapStyleWord(wrap);
    textDisplay.setLineWrap(wrap);
    textDisplay.setAutoscrolls(false);
    textDisplay.setTabSize(Settings.getInstance().getEditorTabWidth());
    textDisplay.setBorder(WbSwingUtilities.EMPTY_BORDER);
    textDisplay.setDoubleBuffered(true);
    textDisplay.setOpaque(true);
  }

  @Override
  public int getHorizontalAlignment()
  {
    return SwingConstants.LEFT;
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value,  boolean isSelected, boolean hasFocus, int row, int col)
  {
    initDisplay(table, value, isSelected, hasFocus, row, col);

    this.textDisplay.setFont(table.getFont());

    if (hasFocus)
    {
      this.textDisplay.setBorder(focusedBorder);
    }
    else
    {
      this.textDisplay.setBorder(WbSwingUtilities.EMPTY_BORDER);
    }

    String originalValue = null;
    if (value instanceof String && (clipToFirstLine || clipLimit > 0))
    {
      Font f = table.getFont();
      String clipped = (String)value;
      originalValue = clipped;
      if (this.clipToFirstLine)
      {
        clipped = clipToFirstLine(clipped, f);
      }

      if (this.clipLimit > 0)
      {
        clipped = StringUtil.getMaxSubstring(clipped, clipLimit, null);
      }
      value = clipped;
    }

    prepareDisplay(value, originalValue);

    this.textDisplay.setBackground(getBackgroundColor());
    this.textDisplay.setForeground(getForegroundColor());

    return textDisplay;
  }

  @Override
  public void prepareDisplay(Object value)
  {
    prepareDisplay(value, null);
  }

  protected void prepareDisplay(Object value, String tip)
  {
    this.isNull = (value == null);
    if (this.isNull)
    {
      if (rendererSetup.nullString == null)
      {
        this.displayValue = StringUtil.EMPTY_STRING;
      }
      else
      {
        this.displayValue = rendererSetup.nullString;
      }
      setTooltip(null);
    }
    else
    {
      try
      {
        // A direct cast is faster that calling toString()
        this.displayValue = (String)value;
      }
      catch (Exception cce)
      {
        this.displayValue = value.toString();
      }
      setTooltip(tip == null ? displayValue : tip);
    }
    textDisplay.setToolTipText(tooltip);

    if (useStringReader)
    {
      try
      {
        StringReader reader = new StringReader(this.displayValue);
        this.textDisplay.read(reader, null);
      }
      catch (Throwable th)
      {
        // cannot happen
      }
    }
    else
    {
      textDisplay.setText(displayValue);
    }
  }

  protected String clipToFirstLine(String value, Font f)
  {
    if (f == null) return value;
    FontMetrics fm = getFontMetrics(f);
    if (fm == null) return value;

    int maxSearch = (maxColumnWidth / fm.charWidth('M')) + 50;
    int pos = findNewLine(value, maxSearch);
    if (pos > 0)
    {
      return value.substring(0, pos);
    }
    return value;
  }

  protected int findNewLine(String value, int maxLength)
  {
    if (value == null) return -1;
    int end = Math.min(maxLength, value.length());
    for (int pos=0; pos < end; pos ++)
    {
      char ch = value.charAt(pos);
      if (ch == '\n' || ch == '\r') return pos;
    }
    return -1;
  }

}
