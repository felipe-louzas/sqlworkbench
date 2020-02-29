/*
 * SyntaxStyle.java - A simple text style class
 * Copyright (C) 1999 Slava Pestov
 *
 * You may use and modify this package for any purpose. Redistribution is
 * permitted, in both source and binary form, provided that this notice
 * remains intact in all source distributions of this package.
 */
package workbench.gui.editor;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;

import javax.swing.JComponent;

/**
 * A simple text style class. It can specify the color, italic flag,
 * and bold flag of a run of text.
 *
 * @author Slava Pestov
 * @author Thomas Kellerer
 */
public class SyntaxStyle
{
  /**
   * The property prefix for storing a color in the settings file.
   */
  public static final String PREFIX_COLOR = "workbench.editor.color.";

  /**
   * The property prefix for storing the italic flag in the settings file.
   * This needs to be combined with the actual style keyword, e.g.
   * <code>PREFIX_ITALIC + COMMENT1</code>
   */
  public static final String PREFIX_ITALIC = "workbench.editor.syntax.italic.";

  /**
   * The property prefix for storing the boldflag in the settings file.
   * This needs to be combined with the actual style keyword, e.g.
   * <code>PREFIX_BOLD + KEYWORD1</code>
   */
  public static final String PREFIX_BOLD = "workbench.editor.syntax.bold.";

  /**
   * The property suffix for storing the block comment style.
   * This needs to be combined with the color, italic or bold prefix.
   */
  public static final String COMMENT1 = "comment1";
  /**
   * The full property key for the block comment style.
   */
  public static final String PROP_COMMENT1 = PREFIX_COLOR + COMMENT1;

  /**
   * The property suffix for storing the style for block comments.
   */
  public static final String COMMENT2 = "comment2";
  /**
   * The full property key for the line comment style.
   */
  public static final String PROP_COMMENT2 = PREFIX_COLOR + COMMENT2;

  /**
   * The property suffix for the keyword1 style.
   */
  public static final String KEYWORD1 = "keyword1";
  /**
   * The full property key for the keyword1 style.
   */
  public static final String PROP_KEYWORD1 = PREFIX_COLOR + KEYWORD1;

  /**
   * The property suffix for the keyword2 style.
   */
  public static final String KEYWORD2 = "keyword2";
  /**
   * The full property key for the keyword2 style.
   */
  public static final String PROP_KEYWORD2 = PREFIX_COLOR + KEYWORD2;

  /**
   * The property suffix for the keyword3 style.
   */
  public static final String KEYWORD3 = "keyword3";
  /**
   * The full property key for the keyword3 style.
   */
  public static final String PROP_KEYWORD3 = PREFIX_COLOR + KEYWORD3;

  /**
   * The property suffix for the literal1 style.
   */
  public static final String LITERAL1 = "literal1";
  /**
   * The full property key for the literal1 style.
   */
  public static final String PROP_LITERAL1 = PREFIX_COLOR + LITERAL1;

  /**
   * The property suffix for the literal2 style.
   */
  public static final String LITERAL2 = "literal2";
  /**
   * The full property key for the literal2 style.
   */
  public static final String PROP_LITERAL2 = PREFIX_COLOR + LITERAL2;

  /**
   * The property suffix for the operator style.
   */
  public static final String OPERATOR = "operator";
  /**
   * The full property key for the operator style.
   */
  public static final String PROP_OPERATOR = PREFIX_COLOR + OPERATOR;

  /**
   * The property suffix for the datatype style.
   */
  public static final String DATATYPE = "datatype";
  /**
   * The full property key for the datatype style.
   */
  public static final String PROP_DATATYPE = PREFIX_COLOR + DATATYPE;

  public static final String INVALID = "invalid";


  // private members
  private Color color;
  private boolean italic;
  private boolean bold;
  private Font lastFont;
  private Font lastStyledFont;
  private FontMetrics fontMetrics;

  /**
   * Creates a new SyntaxStyle.
   * @param color The text color
   * @param italic True if the text should be italics
   * @param bold True if the text should be bold
   */
  public SyntaxStyle(Color color, boolean italic, boolean bold)
  {
    this.color = color;
    this.italic = italic;
    this.bold = bold;
  }

  /**
   * Clear cached font information.
   * This has to be called when the font of the TextAreaPainter is changed.
   */
  public void clearFontCache()
  {
    lastFont = null;
    lastStyledFont = null;
    fontMetrics = null;
  }

  /**
   * Returns the color specified in this style.
   */
  public Color getColor()
  {
    return color;
  }

  /**
   * Returns true if no font styles are enabled.
   */
  public boolean isPlain()
  {
    return !(bold || italic);
  }

  /**
   * Returns true if italics is enabled for this style.
   */
  public boolean isItalic()
  {
    return italic;
  }

  /**
   * Returns true if boldface is enabled for this style.
   */
  public boolean isBold()
  {
    return bold;
  }

  /**
   * Returns the specified font, but with the style's bold and
   * italic flags applied.
   */
  public Font getStyledFont(Font font)
  {
    if (font.equals(lastFont))
    {
      return lastStyledFont;
    }
    int fontBold = font.isBold() ? Font.BOLD : 0;
    int fontItalic = font.isItalic() ? Font.ITALIC : 0;

    lastFont = font;
    lastStyledFont = new Font(font.getFamily(), (bold ? Font.BOLD : fontBold) | (italic ? Font.ITALIC : fontItalic), font.getSize());
    return lastStyledFont;
  }

  /**
   * Returns the font metrics for the styled font.
   */
  public FontMetrics getFontMetrics(Font font, JComponent painter)
  {
    if (font.equals(lastFont) && fontMetrics != null)
    {
      return fontMetrics;
    }

    Font styled = getStyledFont(font);
    fontMetrics = painter.getFontMetrics(styled);
    return fontMetrics;
  }

  /**
   * Sets the foreground color and font of the specified graphics
   * context to that specified in this style.
   * @param gfx The graphics context
   * @param font The font to add the styles to
   */
  public void setGraphicsFlags(Graphics gfx, Font font)
  {
    Font _font = getStyledFont(font);
    gfx.setFont(_font);
    gfx.setColor(color);
  }

  /**
   * Returns a string representation of this object.
   */
  @Override
  public String toString()
  {
    return getClass().getName() + "[color=" + color + (italic ? ",italic" : "") + (bold ? ",bold" : "") + "]";
  }

}
