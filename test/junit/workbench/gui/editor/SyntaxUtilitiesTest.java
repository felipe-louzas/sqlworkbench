/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2020, Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
 * that restricts the use for certain governments.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.sql-workbench.eu/manual/license.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.eu
 */

package workbench.gui.editor;

import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;

import javax.swing.JTextField;
import javax.swing.text.Segment;
import javax.swing.text.TabExpander;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class SyntaxUtilitiesTest
{

  @Test
  public void testRegionMatchStart()
  {
    String lineText = "this is a line with some text in it.";
    Segment line = new Segment(lineText.toCharArray(), 0, lineText.length());
    int pos = SyntaxUtilities.findMatch(line, "line", 0, true);
    assertEquals(10, pos);

    pos = SyntaxUtilities.findMatch(line, "xline", 0, true);
    assertEquals(-1, pos);

    pos = SyntaxUtilities.findMatch(line, "this", 0, true);
    assertEquals(0, pos);

    pos = SyntaxUtilities.findMatch(line, "it.", 0, true);
    assertEquals(33, pos);

    lineText = "Line 1 Text\nLine 2 foo\nLine 4 bar\n";
    line = new Segment(lineText.toCharArray(), 0, 11);

    pos = SyntaxUtilities.findMatch(line, "foo", 0, true);
    assertEquals(-1, pos);

    line.offset = 12;
    line.count = 10;
    pos = SyntaxUtilities.findMatch(line, "foo", 0, true);
    assertEquals(7, pos);
  }


  @Test
  public void testGetTabbedWidth()
    throws Exception {

    if (GraphicsEnvironment.isHeadless()) return;

    String text =         "123456\t";
    String textExpanded = "123456  ";
    testGetTabbedWidth(text, textExpanded, 2);

    text =         "12345678\t";
    textExpanded = "12345678  ";
    testGetTabbedWidth(text, textExpanded, 2);

    text =         "1234\t56\t";
    textExpanded = "1234    56  ";
    testGetTabbedWidth(text, textExpanded, 4);
  }

  public void testGetTabbedWidth(String text, String textExpanded, int tabSize)
    throws Exception
  {
    JTextField  p = new JTextField();
    Font f = new Font(Font.MONOSPACED, Font.PLAIN, 12);
    p.setFont(f);
    Graphics2D g = (Graphics2D)p.getGraphics();
    FontMetrics fm = p.getFontMetrics(f);
    final int tabChars = fm.charWidth(' ') * tabSize;
    TabExpander expander = new TabExpander()
    {
      @Override
      public float nextTabStop(float x, int tabOffset)
      {
        try
        {
          int ntabs = ((int)x) / tabChars;
          return (ntabs + 1) * tabChars;
        }
        catch (Throwable th)
        {
          th.printStackTrace();
          return 0;
        }
      }
    };

    Segment sTab = new Segment(text.toCharArray(), 0, text.length());
    double width = SyntaxUtilities.getTabbedTextWidth(sTab, g, fm, 0, expander, 0);
//    System.out.println("width tabs \"" + text.trim() + "\": " + width);

    Segment s2 = new Segment(textExpanded.toCharArray(), 0, textExpanded.length());
    double width2 = SyntaxUtilities.getTabbedTextWidth(s2, g, fm, 0, expander, 0);
//    System.out.println("width expanded \"" + textExpanded + "\": " + width2);
    assertEquals(width2, width, 0.1);
  }
}
