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
package workbench.gui.editor;

import java.util.List;

import workbench.WbTestCase;
import workbench.interfaces.TextContainer;

import org.junit.Assume;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class SearchAndReplaceTest
  extends WbTestCase
{

  public SearchAndReplaceTest()
  {
    super("SearchAndReplaceTest");
  }

  @Test
  public void testCreateSearchPattern()
  {
    String input = "thetext";
    String expression = SearchAndReplace.getSearchExpression(input, false, false, false);
    assertEquals("Wrong expression", "(" + input + ")", expression);

    expression = SearchAndReplace.getSearchExpression(input, false, true, false);
    assertEquals("Wrong expression", "\\b(" + input + ")\\b", expression);

    expression = SearchAndReplace.getSearchExpression(input, true, true, false);
    assertEquals("Wrong expression", "(?i)\\b(" + input + ")\\b", expression);

    expression = SearchAndReplace.getSearchExpression(input, true, true, true);
    assertEquals("Wrong expression", "(?i)\\b" + input + "\\b", expression);

    expression = SearchAndReplace.getSearchExpression(input, true, false, true);
    assertEquals("Wrong expression", "(?i)" + input, expression);

    expression = SearchAndReplace.getSearchExpression(input, false, false, true);
    assertEquals("Wrong expression", input, expression);
  }

  @Test
  public void testReplace()
  {
    Assume.assumeTrue(!java.awt.GraphicsEnvironment.isHeadless());
    DummyContainer container = new DummyContainer();
    container.setText("go\ngo\ngo\n");
    SearchAndReplace replace = new SearchAndReplace(null, container);
    int count = replace.replaceAll("go$", ";", false, true, false, true);
    assertEquals(";\n;\n;\n", container.getText());
    assertEquals(3, count);

    container.setText("foo go\nfoo go\nfoo go\n");
    count = replace.replaceAll("go$", ";", false, true, false, true);
    assertEquals("foo ;\nfoo ;\nfoo ;\n", container.getText());
    assertEquals(3, count);
  }

  @Test
  public void testFind()
  {
    Assume.assumeTrue(!java.awt.GraphicsEnvironment.isHeadless());
    DummyContainer editor = new DummyContainer();
    editor.setText("foobar\nfoobar\nbar\n");
    SearchAndReplace replace = new SearchAndReplace(null, editor);
    editor.setCaretPosition(0);
    int index = replace.findFirst("bar$", true, false, true);
    editor.setCaretPosition(index);
    assertEquals(3, index);
    index = replace.findNext();
    assertEquals(10, index);
  }

  @Test
  public void testFindAll()
  {
    Assume.assumeTrue(!java.awt.GraphicsEnvironment.isHeadless());
    DummyContainer editor = new DummyContainer();
    editor.setText("foobar1\nfoobar2\nbarman\nbar word\n");
    SearchAndReplace replace = new SearchAndReplace(null, editor);
    List<SearchResult> findAll = replace.findAll("foo", true, false, false, 0);
    assertEquals(2, findAll.size());
    assertEquals("foobar1", findAll.get(0).getLineText());
    assertEquals("foobar2", findAll.get(1).getLineText());
    assertEquals(0, findAll.get(0).getLineNumber());
    assertEquals(1, findAll.get(1).getLineNumber());

    List<SearchResult> result = replace.findAll("bar", true, true, false, 0);
    assertEquals(1, result.size());
    assertEquals("bar word", result.get(0).getLineText());
    assertEquals(3, result.get(0).getLineNumber());

    result = replace.findAll("bar", true, true, false, 1);
    assertEquals(1, result.size());
    assertEquals("barman\nbar word", result.get(0).getLineText());
    assertEquals(3, result.get(0).getLineNumber());

    result = replace.findAll("foobar2", true, true, false, 1);
    assertEquals(1, result.size());
    assertEquals("foobar1\nfoobar2\nbarman", result.get(0).getLineText());
    assertEquals(1, result.get(0).getLineNumber());
  }

  // <editor-fold desc="Editor Mock" defaultstate="collapsed">
  private static class DummyContainer
    implements TextContainer
  {
    private String text;
    private int caretPosition;

    @Override
    public String getText()
    {
      return text;
    }

    @Override
    public String getSelectedText()
    {
      return text;
    }

    @Override
    public void setSelectedText(String aText)
    {
    }

    @Override
    public void setText(String aText)
    {
      this.text = aText;
    }

    @Override
    public void setCaretPosition(int pos)
    {
      caretPosition = pos;
    }

    @Override
    public int getCaretPosition()
    {
      return caretPosition;
    }

    @Override
    public int getSelectionStart()
    {
      return 0;
    }

    @Override
    public int getSelectionEnd()
    {
      return 0;
    }

    @Override
    public void select(int start, int end)
    {
    }

    @Override
    public void setEditable(boolean flag)
    {
    }

    @Override
    public boolean isEditable()
    {
      return true;
    }

    @Override
    public boolean isTextSelected()
    {
      return false;
    }

    @Override
    public int getLineOfOffset(int offset)
    {
      int line = 0;
      for (int i = 0; i < text.length(); i++)
      {
        if (offset == i) return line;
        if (text.charAt(i) == '\n') line++;
      }
      return -1;
    }

    @Override
    public int getStartInLine(int offset)
    {
      return -1;
    }

    @Override
    public String getLineText(int line)
    {
      String[] lines = text.split("\\n");
      return lines[line];
    }

    @Override
    public int getLineCount()
    {
      String[] lines = text.split("\\n");
      return lines.length;
    }

    @Override
    public String getWordAtCursor(String wordChars)
    {
      return null;
    }
  }
  // </editor-fold>
}
