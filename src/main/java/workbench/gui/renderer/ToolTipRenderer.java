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

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.util.List;
import java.util.Map;

import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.LineBorder;
import javax.swing.table.TableCellRenderer;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.GuiSettings;
import workbench.resource.Settings;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.DataStoreTableModel;
import workbench.gui.components.RowHighlighter;
import workbench.gui.components.WbTable;

import workbench.util.StringUtil;

/**
 * A renderer that automatically displays the value as a tooltip.
 * It also handles the highlighting of null values during display
 * and non-null columns in editing mode.
 * <br/>
 * It can also highlight values based on a ColumnExpression that is
 * provided by WbTable.
 * <br/>
 * For performance reasons the displayValue is drawn directly using the graphics
 * object.
 *
 * @author Thomas Kellerer
 */
public class ToolTipRenderer
  extends JComponent
  implements TableCellRenderer, WbRenderer, RequiredFieldHighlighter
{
  protected String displayValue = StringUtil.EMPTY_STRING;
  protected Object currentValue;
  protected String tooltip;
  protected boolean useOwnPaint = true;
  protected Color selectedForeground;
  protected Color selectedBackground;
  protected Color unselectedForeground;
  protected Color unselectedBackground;
  protected Color highlightBackground;
  protected Color filterHighlightColor = GuiSettings.getExpressionHighlightColor();

  protected RendererSetup rendererSetup;

  protected int maxTooltipSize = Settings.getInstance().getIntProperty("workbench.gui.renderer.maxtooltipsize", 1000);

  private static final int DEFAULT_BLEND = 0;
  protected int selectionBlendFactor = DEFAULT_BLEND;
  protected int alternateBlendFactor = DEFAULT_BLEND;

  protected int editingRow = -1;
  private boolean isEditing;
  private boolean[] highlightCols;
  private int currentColumn = -1;
  private int currentRow = -1;
  private String currentColumnName;

  private final Rectangle paintIconR = new Rectangle();
  private final Rectangle paintTextR = new Rectangle();
  private final Rectangle paintViewR = new Rectangle();

  protected Border focusedBorder;
  protected final Insets focusedInsets;
  protected final Insets regularInsets;
  protected boolean useInsetsFromLnF = false;

  protected boolean isSelected;
  protected boolean hasFocus;
  protected boolean isNull;

  protected RowHighlighter filter;

  private final int valign = SwingConstants.TOP;
  private int halign = SwingConstants.LEFT;

  private boolean isAlternatingRow;
  private boolean isModifiedColumn;

  protected boolean showTooltip = true;
  private final Map renderingHints;
  protected int clipLimit;

  public ToolTipRenderer()
  {
    super();
    setDoubleBuffered(true);
    setOpaque(true);
    regularInsets = getDefaultInsets();
    clipLimit = GuiSettings.getClipLongRendererValues();

    focusedBorder = WbSwingUtilities.getFocusedCellBorder();
    if (focusedBorder == null)
    {
      focusedBorder = new LineBorder(Color.YELLOW);
    }
    int thick = getFocusBorderThickness();
    useInsetsFromLnF = UIManager.getDefaults().getInsets("Table.cellMargins") != null;
    // if the regular inserts were changed, reflect this with the focused insets
    focusedInsets = new Insets(thick + regularInsets.top - 1, thick + regularInsets.left - 1, thick + regularInsets.bottom - 1, thick + regularInsets.right - 1);

    Toolkit tk = Toolkit.getDefaultToolkit();
    renderingHints = (Map) tk.getDesktopProperty("awt.font.desktophints");
    showTooltip = Settings.getInstance().getBoolProperty("workbench.gui.renderer.showtooltip", true);
    selectionBlendFactor = retrieveBlendFactor("selection");
    alternateBlendFactor = retrieveBlendFactor("alternate");
  }

  private void adjustInsets(Font f)
  {
    if (useInsetsFromLnF || f == null) return;
    FontMetrics fm = getFontMetrics(f);
    if (fm == null) return;

    int top = fm.getLeading();
    if (top != regularInsets.top)
    {
      regularInsets.set(top, regularInsets.left, regularInsets.bottom, regularInsets.right);
      int thick = getFocusBorderThickness();
      focusedInsets.set(thick + top - 1, thick + regularInsets.left - 1, thick + regularInsets.bottom - 1, thick + regularInsets.right - 1);
    }
  }

  private int getFocusBorderThickness()
  {
    if (focusedBorder instanceof LineBorder)
    {
      return ((LineBorder)focusedBorder).getThickness();
    }
    return 1;
  }

  private int retrieveBlendFactor(String type)
  {
    int value = Settings.getInstance().getIntProperty("workbench.gui.renderer.blend." + type, DEFAULT_BLEND);
    if (value < 0 || value > 256)
    {
      value = DEFAULT_BLEND;
    }
    return value;
  }

  public static Insets getDefaultInsets()
  {
    // This is used by FlatLaf
    Insets result = UIManager.getDefaults().getInsets("Table.cellMargins");
    if (result != null)
    {
      return WbSwingUtilities.cloneInsets(result);
    }

    String prop = Settings.getInstance().getProperty("workbench.gui.renderer.insets", null);
    List<String> values = prop != null ? StringUtil.stringToList(prop, ",", true, true, false) : null;

    if (values != null && values.size() == 4)
    {
      try
      {
        int top = Integer.parseInt(values.get(0));
        int left = Integer.parseInt(values.get(1));
        int bottom = Integer.parseInt(values.get(2));
        int right = Integer.parseInt(values.get(3));
        result = new Insets(top,left,bottom,right);
      }
      catch (Exception e)
      {
        LogMgr.logError(new CallerInfo(){}, "Error reading default insets from settings: " + prop, e);
        result = null;
      }
    }
    if (result == null)
    {
      result = new Insets(0,0,0,0);
    }
    return result;
  }

  @Override
  public void setEditingRow(int row)
  {
    this.editingRow = row;
  }

  @Override
  public void setHighlightColumns(boolean[] cols)
  {
    this.highlightCols = cols;
  }

  public void setHorizontalAlignment(int align)
  {
    this.halign = align;
  }

  @Override
  public int getHorizontalAlignment()
  {
    return this.halign;
  }

  @Override
  public void setHighlightBackground(Color c)
  {
    this.highlightBackground = c;
  }

  private boolean doModificationHighlight(WbTable table, int row, int col)
  {
    if (this.rendererSetup.modifiedColor == null) return false;
    if (table == null) return false;
    DataStoreTableModel model = table.getDataStoreTableModel();
    if (model == null) return false;

    return model.isColumnModified(row, col);
  }

  protected void initDisplay(JTable table, Object value,  boolean selected, boolean focus, int row, int col)
  {
    this.isNull = (value == null);
    this.hasFocus = focus;
    this.isEditing = (row == this.editingRow) && (this.highlightBackground != null);
    this.currentColumn = col;
    this.currentColumnName = table.getColumnName(col);
    this.currentRow = row;
    this.isSelected = selected;
    this.currentValue = value;

    adjustInsets(table.getFont());

    try
    {
      WbTable wbtable = (WbTable)table;
      this.rendererSetup = wbtable.getRendererSetup();
      this.filter = wbtable.getHighlightExpression();
      this.isModifiedColumn = doModificationHighlight(wbtable, row, col);
    }
    catch (ClassCastException cce)
    {
      // ignore, should not happen
    }

    this.isAlternatingRow = rendererSetup.useAlternatingColors && ((row % 2) == 1);

    if (selectedForeground == null)
    {
      selectedForeground = table.getSelectionForeground();
      selectedBackground = table.getSelectionBackground();
    }

    if (unselectedForeground == null)
    {
      unselectedForeground = table.getForeground();
      unselectedBackground = table.getBackground();
    }
  }

  @Override
  public Component getTableCellRendererComponent(JTable table, Object value,  boolean selected, boolean focus, int row, int col)
  {
    initDisplay(table, value, selected, focus, row, col);

    Font f = table.getFont();

    if (value != null)
    {
      setFont(f);
      String originalValue = null;
      if (this.clipLimit > 0 && value instanceof String)
      {
        originalValue = (String)value;
        displayValue = StringUtil.getMaxSubstring((String)value, clipLimit, null);
        setTooltip(originalValue);
      }
      else
      {
        prepareDisplay(value);
      }
    }
    else
    {
      if (rendererSetup.nullFontStyle > 0 && rendererSetup.nullString != null)
      {
        f = f.deriveFont(rendererSetup.nullFontStyle);
      }
      setFont(f);
      displayValue = rendererSetup.nullString;
      setTooltip(null);
    }

    return this;
  }

  @Override
  public Dimension getPreferredSize()
  {
    Dimension d = super.getPreferredSize();
    FontMetrics fm = getFontMetrics(getFont());

    d.setSize(d.getWidth(), fm.getHeight());
    return d;
  }

  protected Color getForegroundColor()
  {
    if (isSelected)
    {
      return selectedForeground;
    }
    return unselectedForeground;
  }

  private boolean isHighlightColumn(int col)
  {
    if (this.highlightCols == null) return false;
    if (col < 0 || col >= this.highlightCols.length) return false;
    return this.highlightCols[col];
  }

  protected Color getBackgroundColor()
  {
    Color c = getColumnHighlightColor(currentRow);

    if (isSelected)
    {
      return ColorUtils.blend(selectedBackground, c, selectionBlendFactor);
    }
    else if (isAlternatingRow)
    {
      return ColorUtils.blend(rendererSetup.alternateBackground, c, alternateBlendFactor);
    }
    if (c != null) return c;

    return unselectedBackground;
  }

  protected Color getColumnHighlightColor(int row)
  {
    if (isEditing)
    {
      if (isHighlightColumn(currentColumn))
      {
        return highlightBackground;
      }
      else
      {
        return unselectedBackground;
      }
    }

    if (shouldHighlight(row, currentValue))
    {
      return filterHighlightColor;
    }
    if (isModifiedColumn)
    {
      return rendererSetup.modifiedColor; // might be null which is OK
    }
    else if (displayValue == null || isNull)
    {
      return rendererSetup.nullColor; // might be null which is OK
    }
    // null means "default" color
    return null;
  }

  @Override
  public void paint(Graphics g)
  {
    if (!useOwnPaint)
    {
      super.paint(g);
      return;
    }

    int w = this.getWidth();
    int h = this.getHeight();

    Font f = getFont();
    FontMetrics fm = g.getFontMetrics(f);
    final Insets insets = hasFocus ? focusedInsets : regularInsets;

    paintViewR.x = insets.left;
    paintViewR.y = insets.top;
    paintViewR.width = w - (insets.left + insets.right);
    paintViewR.height = h - (insets.top + insets.bottom);

    paintIconR.x = paintIconR.y = paintIconR.width = paintIconR.height = 0;
    paintTextR.x = paintTextR.y = paintTextR.width = paintTextR.height = 0;

    String clippedText;
    if (displayValue == null)
    {
      clippedText = StringUtil.EMPTY_STRING;
    }
    else
    {
      clippedText = SwingUtilities.layoutCompoundLabel(this, fm,
        this.displayValue, (Icon)null, this.valign, this.halign,
        SwingConstants.TOP, SwingConstants.LEFT, paintViewR, paintIconR, paintTextR, 0);
    }

    if (renderingHints != null)
    {
      Graphics2D g2d = (Graphics2D)g;
      g2d.addRenderingHints(renderingHints);
    }

    g.setFont(f);
    g.setColor(getBackgroundColor());
    g.fillRect(0, 0, w, h);
    g.setColor(getForegroundColor());
    g.drawString(clippedText, paintTextR.x, paintTextR.y + fm.getAscent());

    if (hasFocus)
    {
      focusedBorder.paintBorder(this, g, 0, 0, w, h);
    }
  }

  @Override
  public void prepareDisplay(Object value)
  {
    if (value == null)
    {
      displayValue = null;
    }
    else
    {
      displayValue = value.toString();
    }
    setTooltip(displayValue);
  }

  protected boolean shouldHighlight(int row, Object value)
  {
    if (this.filter == null)
    {
      return false;
    }
    return filter.hightlightColumn(row, currentColumnName, currentValue);
  }

  @Override
  public String getToolTipText()
  {
    return this.tooltip;
  }

  protected void setTooltip(String tip)
  {
    if (showTooltip && tip != null && tip.length() > 0)
    {
      tooltip = StringUtil.getMaxSubstring(tip, maxTooltipSize, " ...");
    }
    else
    {
      tooltip = null;
    }
  }

  @Override
  public String getDisplayValue()
  {
    return displayValue;
  }

}
