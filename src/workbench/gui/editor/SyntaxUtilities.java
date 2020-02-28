package workbench.gui.editor;

/*
 * SyntaxUtilities.java - Utility functions used by syntax colorizing
 * Copyright (C) 1999 Slava Pestov
 *
 * You may use and modify this package for any purpose. Redistribution is
 * permitted, in both source and binary form, provided that this notice
 * remains intact in all source distributions of this package.
 */
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;

import javax.swing.text.Segment;
import javax.swing.text.TabExpander;
import javax.swing.text.Utilities;

import workbench.log.LogMgr;
import workbench.resource.Settings;

/**
 * Class with several utility functions used by jEdit's syntax colorizing
 * subsystem.
 *
 * @author Slava Pestov
 */
public class SyntaxUtilities
{

  public static int findMatch(Segment line, String needle, int startAt, boolean ignoreCase)
  {
    char[] haystack = line.array;
    int needleLen = needle.length();

    int searchPos = 0;
    int textLength = line.offset + line.count;
    if (textLength > haystack.length)
    {
      // for some reason it happens that the textLength is calculated too big
      LogMgr.logDebug("SyntaxUtilities.findMatch()", "textLength=" + textLength + ", line.offset=" + line.offset + ", line.count=" + line.count + ", haystack.length=" + haystack.length + ", needle=" + needle + " (length=" + needle.length() + ")");
      textLength = haystack.length;
    }

    for (int textPos = line.offset + startAt; textPos < textLength; textPos++)
    {
      char c1 = haystack[textPos];
      char c2 = needle.charAt(searchPos);

      if (ignoreCase)
      {
        c1 = Character.toUpperCase(c1);
        c2 = Character.toUpperCase(c2);
      }

      if (c1 == c2)
      {
        searchPos++;
        if (searchPos == needleLen)
        {
          return (textPos + 1) - needleLen - line.offset;
        }
      }
      else
      {
        textPos -= searchPos;
        searchPos = 0;
      }
    }
    return -1;
  }

  /**
   * Checks if a subregion of a <code>Segment</code> is equal to a
   * character array.
   *
   * @param ignoreCase True if case should be ignored, false otherwise
   * @param text       The segment
   * @param offset     The offset into the segment
   * @param match      The character array to match
   */
  public static boolean regionMatches(boolean ignoreCase, Segment text, int offset, char[] match)
  {
    int length = offset + match.length;
    char[] textArray = text.array;
    if (length > text.offset + text.count)
    {
      return false;
    }
    for (int i = offset, j = 0; i < length; i++, j++)
    {
      char c1 = textArray[i];
      char c2 = match[j];
      if (ignoreCase)
      {
        c1 = Character.toUpperCase(c1);
        c2 = Character.toUpperCase(c2);
      }
      if (c1 != c2)
      {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns the default style table. This can be passed to the
   * <code>setStyles()</code> method of <code>SyntaxDocument</code>
   * to use the default syntax styles.
   */
  public static SyntaxStyle[] getDefaultSyntaxStyles()
  {
    SyntaxStyle[] styles = new SyntaxStyle[Token.ID_COUNT];

    styles[Token.COMMENT1] = getStyle("comment1", Color.GRAY, true, false);
    styles[Token.COMMENT2] = getStyle("comment2", Color.GRAY, true, false);
    styles[Token.KEYWORD1] = getStyle("keyword1", Color.BLUE, false, false);
    styles[Token.KEYWORD2] = getStyle("keyword2", Color.MAGENTA, false, false);
    styles[Token.KEYWORD3] = getStyle("keyword3", new Color(0x009600), false, false);
    styles[Token.LITERAL1] = getStyle("literal1", new Color(0x650099), false, false);
    styles[Token.LITERAL2] = getStyle("literal2", new Color(0x650099), false, false);
    styles[Token.DATATYPE] = getStyle("datatype", new Color(0x990033), false, false);
    styles[Token.OPERATOR] = getStyle("operator", Color.BLACK, false, false);
    styles[Token.INVALID] = getStyle("invalid", Color.RED, false, true);

    return styles;
  }

  private static SyntaxStyle getStyle(String suffix, Color defaultColor, boolean defaultItalic, boolean defaultBold)
  {
    Color color = Settings.getInstance().getColor("workbench.editor.color." + suffix, defaultColor);
    boolean italic = Settings.getInstance().getBoolProperty("workbench.editor.syntax.italic." + suffix, defaultItalic);
    boolean bold = Settings.getInstance().getBoolProperty("workbench.editor.syntax.bold." + suffix, defaultBold);
    return new SyntaxStyle(color, italic, bold);
  }

  /**
   * Paints the specified line onto the graphics context. Note that this
   * method munges the offset and count values of the segment.
   *
   * @param line     The line segment
   * @param tokens   The token list for the line
   * @param styles   The syntax style list
   * @param expander The tab expander used to determine tab stops. May
   *                 be null
   * @param gfx      The graphics context
   * @param x        The x co-ordinate
   * @param y        The y co-ordinate
   * @param addwidth Additional spacing to be added to the line width
   *
   * @return The x co-ordinate, plus the width of the painted string
   */
  public static int paintSyntaxLine(Segment line, Token tokens, SyntaxStyle[] styles, TabExpander expander, Graphics gfx, int x, int y, int addwidth)
  {
    if (tokens == null) return x;

    Font defaultFont = gfx.getFont();
    Color defaultColor = gfx.getColor();

    while (true)
    {
      if (tokens == null)
      {
        gfx.setColor(defaultColor);
        gfx.setFont(defaultFont);
        break;
      }

      if (tokens.id == Token.NULL)
      {
        gfx.setColor(defaultColor);
        gfx.setFont(defaultFont);
      }
      else
      {
        styles[tokens.id].setGraphicsFlags(gfx, defaultFont);
      }
      line.count = tokens.length;
      x = Utilities.drawTabbedText(line, x, y, gfx, expander, addwidth);
      line.offset += tokens.length;

      tokens = tokens.next;
    }

    return x;
  }

  public static float getTabbedTextWidth(Segment s, Graphics2D gfx, FontMetrics metrics, float x, TabExpander expander, int startOffset)
  {
    float nextX = x;
    final char[] txt = s.array;
    String txtStr = new String(txt);
    int txtOffset = s.offset;
    int n = s.offset + s.count;
    int charCount = 0;

    for (int i = txtOffset; i < n; i++)
    {
      if (txt[i] == '\t')
      {
        nextX = expander.nextTabStop(nextX, startOffset + i - txtOffset);
        charCount = 0;
      }
      else if (txt[i] == '\n')
      {
        nextX += metrics.getStringBounds(txtStr, i - charCount, i, gfx).getWidth();
        charCount = 0;
      }
      else
      {
        charCount++;
      }
    }
    nextX += metrics.getStringBounds(txtStr, n - charCount, n, gfx).getWidth();
    return nextX - x;
  }

}
