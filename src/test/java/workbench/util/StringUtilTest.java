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
package workbench.util;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import workbench.db.TableIdentifier;

import org.junit.Test;

import static org.junit.Assert.*;


/**
 *
 * @author Thomas Kellerer
 */
public class StringUtilTest
{

  @Test
  public void testListToString()
  {
    List<String> items = List.of("One", "Two", "Three");
    assertEquals("One,Two,Three", StringUtil.listToString(items, ','));
    assertEquals("'One','Two','Three'", StringUtil.listToString(items, ",", true, '\''));
    List<TableIdentifier> tables = new ArrayList<>();
    tables.add(new TableIdentifier("public", "person"));
    tables.add(new TableIdentifier("public", "address"));
    String s = StringUtil.listToString(tables, ",", false);
    assertEquals("public.person,public.address", s);
  }

  @Test
  public void testTrimAfterLineFeed()
  {
    String line = "   this is a test    ";
    assertEquals(line, StringUtil.trimAfterLineFeed(line));

    line = "this is a test  \r\n    ";
    assertEquals("this is a test  \r\n", StringUtil.trimAfterLineFeed(line));

    line = "this is a test  \r\n    \t";
    assertEquals("this is a test  \r\n", StringUtil.trimAfterLineFeed(line));

    line = "this is a test  \r\n";
    assertEquals("this is a test  \r\n", StringUtil.trimAfterLineFeed(line));

    line = "this is a test  \r\nbla\r\n";
    assertEquals("this is a test  \r\nbla\r\n", StringUtil.trimAfterLineFeed(line));

    line = "this is a test  \r\nbla\r\n    \t  ";
    assertEquals("this is a test  \r\nbla\r\n", StringUtil.trimAfterLineFeed(line));
  }

  @Test
  public void testIncrementCounter()
  {
    String result = StringUtil.incrementCounter("SQLExport", 4);
    assertEquals("SQLExport (4)", result);
    result = StringUtil.incrementCounter(result, 1);
    assertEquals("SQLExport (5)", result);
  }

  @Test
  public void testRemoveLeading()
  {
    assertEquals("bla", StringUtil.removeLeading("..bla", '.'));
    assertEquals("", StringUtil.removeLeading("..", '.'));
    assertEquals("bla.", StringUtil.removeLeading("bla.", '.'));
    assertEquals("bla", StringUtil.removeLeading("bla", '.'));
  }

  @Test
  public void testIsAllBlank()
  {
    assertTrue(StringUtil.isAllBlank(null));
    assertTrue(StringUtil.isAllBlank(null, " "));
    assertTrue(StringUtil.isAllBlank("", "  "));
    assertFalse(StringUtil.isAllBlank("", "bla"));
    assertFalse(StringUtil.isAllBlank(null, "bla"));
  }

  @Test
  public void testIsAnyBlank()
  {
    assertTrue(StringUtil.isAnyBlank(null));
    assertTrue(StringUtil.isAnyBlank(null, " "));
    assertTrue(StringUtil.isAnyBlank("42", "  "));
    assertFalse(StringUtil.isAnyBlank("foo", "bar"));
  }

  @Test
  public void removeTrailing()
  {
    assertEquals("bla", StringUtil.removeTrailing("bla.", '.'));
    assertEquals("foo.bar", StringUtil.removeTrailing("foo.bar.", '.'));
  }

  @Test
  public void testUnescape()
  {
    assertEquals("\t", StringUtil.unescape("\\t"));
    assertEquals("'", StringUtil.unescape("&#39;"));
    assertEquals("\007", StringUtil.unescape("\\u0007"));
    assertEquals("\"", StringUtil.unescape("\\u0022"));
  }

  @Test
  public void testConcatWS()
  {
    assertEquals("foo", StringUtil.concatWithSeparator(".", null, "foo"));
    assertEquals("foo.bar", StringUtil.concatWithSeparator(".", "foo", "bar"));
    assertEquals("foo.bar", StringUtil.concatWithSeparator(".", "foo", "bar", ""));
    assertEquals("foo.bar", StringUtil.concatWithSeparator(".", "foo", "", "bar"));
  }

  @Test
  public void testGetLineEnd()
  {
    String text = "first line\nsecond line";
    int end = StringUtil.getLineEnd(text, 2);
    assertEquals(text.indexOf("second") - 1, end);
  }

  @Test
  public void testFindNextLineStart()
  {
    String text = "first line\nsecond line";
    int end = StringUtil.findNextLineStart(text, 2);
    assertEquals(text.indexOf("second"), end);

    text = "first line\r\nsecond line";
    end = StringUtil.findNextLineStart(text, 2);
    assertEquals(text.indexOf("second"), end);

    text = "first line\r\n\n\nsecond line";
    end = StringUtil.findNextLineStart(text, 2);
    assertEquals(text.indexOf("second"), end);
  }

  @Test
  public void testGetFirstWord()
  {
    assertEquals("foo", StringUtil.getFirstWord("foo bar"));
    assertEquals("foobar", StringUtil.getFirstWord("foobar"));
    assertEquals("foobar", StringUtil.getFirstWord(" foobar"));
    assertEquals("foo", StringUtil.getFirstWord(" foo bar"));
  }

  @Test
  public void testFindFirstWhitespace()
  {
    String text = "This is a long comment with multiple words";
    int pos = StringUtil.findFirstWhiteSpace(text, (char)0, 10);
    assertEquals("This is a long", text.substring(0,pos));
  }

  @Test
  public void testRemove()
  {
    StringBuilder b = new StringBuilder("12345");
    StringUtil.removeFromEnd(b, 2);
    assertEquals("123", b.toString());

    b = new StringBuilder("12");
    StringUtil.removeFromEnd(b, 2);
    assertTrue(b.length() == 0);

    b = new StringBuilder("123");
    StringUtil.removeFromEnd(b, 5);
    assertTrue(b.length() == 0);
  }

  @Test
  public void testWildcardRegex()
  {
    String pattern = StringUtil.wildcardToRegex("*.sql", false);
    assertEquals("^.*\\.sql$", pattern);

    pattern = StringUtil.wildcardToRegex("*.sql*", false);
    assertEquals("^.*\\.sql.*$", pattern);

    pattern = StringUtil.wildcardToRegex("foo%20*.txt", false);
    assertEquals("^foo%20.*\\.txt$", pattern);

    pattern = StringUtil.wildcardToRegex("foo%", true);
    assertEquals("^foo.*$", pattern);
  }

  @Test
  public void testFormatString()
  {
    assertEquals("0001", StringUtil.formatInt(1, 4).toString());
    assertEquals("0123", StringUtil.formatInt(123, 4).toString());
    assertEquals("123", StringUtil.formatInt(123, 3).toString());
    assertEquals("123", StringUtil.formatInt(123, 1).toString());
    assertEquals("-01", StringUtil.formatInt(-1, 2).toString());
  }

  @Test
  public void testHexString()
  {
    String hex = StringUtil.hexString(4, 2);
    assertEquals("04", hex);

    hex = StringUtil.hexString(4, 3);
    assertEquals("004", hex);

    hex = StringUtil.hexString(16, 2);
    assertEquals("10", hex);

    hex = StringUtil.hexString(256, 2);
    assertEquals("100", hex);

    hex = StringUtil.hexString(256, 4);
    assertEquals("0100", hex);
  }

  @Test
  public void testStartsWith()
  {
    String input = "this is a test";
    assertTrue(StringUtil.lineStartsWith(input, 0, "this"));
    assertFalse(StringUtil.lineStartsWith(input, 0, "thisx"));
    assertTrue(StringUtil.lineStartsWith(input, 10, "test"));
    assertTrue(StringUtil.lineStartsWith(input, 9, "test"));
    assertFalse(StringUtil.lineStartsWith(input, 13, "test"));
  }

  @Test
  public void testRemoveQuotes()
  {
    String name = "test";
    String trimmed = StringUtil.removeQuotes(name, "\"");
    assertEquals(name, trimmed);

    name = "\"test";
    trimmed = StringUtil.removeQuotes(name, "\"");
    assertEquals(name, trimmed);

    name = "\"test\"";
    trimmed = StringUtil.removeQuotes(name, "\"");
    assertEquals("test", trimmed);

    name = "\"test'";
    trimmed = StringUtil.removeQuotes(name, "\"");
    assertEquals(name, trimmed);

    name = "'test'";
    trimmed = StringUtil.removeQuotes(name, "\"");
    assertEquals(name, trimmed);

    name = "'test'";
    trimmed = StringUtil.removeQuotes(name, "'");
    assertEquals("test", trimmed);

    name = "`test`";
    trimmed = StringUtil.removeQuotes(name, "`");
    assertEquals("test", trimmed);
  }

  @Test
  public void testLongestLine()
  {
    String s = "this\na test for\nseveral lines";
    String line = StringUtil.getLongestLine(s, 10);
    assertEquals("several lines", line);

    s = "this\na test for\nseveral lines\nand another long line that is even longer\na short end";
    line = StringUtil.getLongestLine(s, 3);
    assertEquals("several lines", line);
    line = StringUtil.getLongestLine(s, 10);
    assertEquals("and another long line that is even longer", line);

    s = "this\na test for\nseveral lines\na long line at the end of the string";
    line = StringUtil.getLongestLine(s, 10);
    assertEquals("a long line at the end of the string", line);

    s = "this\na test for\nseveral lines\na long line at the end of the string\n";
    line= StringUtil.getLongestLine(s, 10);
    assertEquals("a long line at the end of the string", line);

    s = "this\r\na test for\r\nseveral lines\r\na long line at the end of the string\r\n";
    line = StringUtil.getLongestLine(s, 10);
    assertEquals("a long line at the end of the string", line);

    s = "no line feeds";
    line = StringUtil.getLongestLine(s, 10);
    assertEquals(s, line);

    s = "long line at the start\nshort";
    line = StringUtil.getLongestLine(s, 10);
    assertEquals("long line at the start", line);
  }

  @Test
  public void testFindWordLeftOfCursor()
  {
    String input = "  ab   test   more    text";
    String word = StringUtil.findWordLeftOfCursor(input, 1);
    assertNull(word);
    word = StringUtil.findWordLeftOfCursor(input, 5);
    assertNotNull(word);
    assertEquals("ab", word);
  }

  @Test
  public void testGetWordLefOfCursor()
  {
    String input = "ab test\nmore text";
    String word = StringUtil.getWordLeftOfCursor(input, 2, " \t");
    assertNotNull(word);
    assertEquals("ab", word);
  }

  @Test
  public void testLineStartsWith()
  {
    String s = "some stuff     -- this is a comment";
    boolean isComment = StringUtil.lineStartsWith(s, 0, "--");
    assertFalse(isComment);

    s = "some stuff     -- this is a comment";
    isComment = StringUtil.lineStartsWith(s, 10, "--");
    assertTrue(isComment);

    s = "-- comment'\nselect 'b' from dual;";
    isComment = StringUtil.lineStartsWith(s, 0, "--");
    assertTrue(isComment);

    isComment = StringUtil.lineStartsWith(s, 12, "--");
    assertFalse(isComment);

    isComment = StringUtil.lineStartsWith("\t# non-standard comment", 0, "#");
    assertTrue(isComment);

    isComment = StringUtil.lineStartsWith("update foo set bar = 24     \t # non-standard comment\n", 25, "#");
    assertTrue(isComment);

    isComment = StringUtil.lineStartsWith("x \n-- comment", 0, "--");
    assertFalse(isComment);

    isComment = StringUtil.lineStartsWith("1 \n-- comment", 2, "--");
    assertFalse(isComment);

  }

  @Test
  public void testFindFirstNonWhitespace()
  {
    String s = "   Hello, world";
    int pos = StringUtil.findFirstNonWhitespace(s);
    assertEquals(3, pos);

    s = "some stuff     -- this is a comment";
    pos = StringUtil.findFirstNonWhitespace(s, 10, true);
    assertEquals(15, pos);

    pos = StringUtil.findFirstNonWhitespace(s, 12, true);
    assertEquals(15, pos);

    String empty = "   ";
    pos = StringUtil.findFirstNonWhitespace(empty);
    assertEquals(-1, pos);

    pos = StringUtil.findFirstNonWhitespace("\nx");
    assertEquals(1, pos);
  }

  @Test
  public void testGetFirsNonWhitespace()
  {
    String value = "   this is a test";
    char c = StringUtil.getFirstNonWhitespace(value);
    assertEquals('t', c);

    value = "   ";
    c = StringUtil.getFirstNonWhitespace(value);
    assertEquals(0, c);

    value = "";
    c = StringUtil.getFirstNonWhitespace(value);
    assertEquals(0, c);

    value = null;
    c = StringUtil.getFirstNonWhitespace(value);
    assertEquals(0, c);
  }

  @Test
  public void testGetStartingWhitespace()
  {
    String s = "   Hello, world";
    String p = StringUtil.getStartingWhiteSpace(s);
    assertEquals("   ", p);

    s = "Hello, world";
    p = StringUtil.getStartingWhiteSpace(s);
    assertNull(p);

    s = "\t\nHello, world";
    p = StringUtil.getStartingWhiteSpace(s);
    assertEquals("\t\n", p);
  }

  @Test
  public void testMakeFilename()
  {
    String fname = StringUtil.makeFilename("TABLE_NAME");
    assertEquals("table_name", fname);

    fname = StringUtil.makeFilename("TABLE_\\NAME");
    assertEquals("table_name", fname);

    fname = StringUtil.makeFilename("TABLE_<>NAME");
    assertEquals("table_name", fname);

    fname = StringUtil.makeFilename("TABLE_<>NAME", false);
    assertEquals("TABLE_NAME", fname);

    fname = StringUtil.makeFilename("ProductDetails", false);
    assertEquals("ProductDetails", fname);

    fname = StringUtil.makeFilename("Produc:tDetails", false);
    assertEquals("ProductDetails", fname);
  }

  @Test
  public void testEndsWith()
  {
    String s = "this is a test";
    assertTrue(StringUtil.endsWith(s, "test"));
    assertFalse(StringUtil.endsWith(s, "testing"));

    assertFalse(StringUtil.endsWith("bla", "blabla"));
    assertTrue(StringUtil.endsWith("bla", "bla"));

    assertFalse(StringUtil.endsWith("est", "test"));
    assertFalse(StringUtil.endsWith("no est", "test"));
  }

  @Test
  public void testIndexOf()
  {
    String s = ".this. is a test";
    int pos = StringUtil.indexOf(s, '.');
    assertEquals(0, pos);

    s = "this. is. a. test.";
    pos = StringUtil.indexOf(s, '.');
    assertEquals(4, pos);

    s = "this. is. a test";
    pos = StringUtil.indexOf(s, '.', 2);
    assertEquals(8, pos);
  }

  @Test
  public void testLastIndexOf()
  {
    String s = "this is a test.";
    int pos = StringUtil.lastIndexOf(s, '.');
    assertEquals(s.length() - 1, pos);

    s = "this. is. a. test.";
    pos = StringUtil.lastIndexOf(s, '.');
    assertEquals(s.length() - 1, pos);

    s = "this is a test";
    pos = StringUtil.lastIndexOf(s, '.');
    assertEquals(-1, pos);

    StringBuilder b = new StringBuilder("this is a test.");
    pos = StringUtil.lastIndexOf(b, '.');
    assertEquals(b.length() - 1, pos);

    b = new StringBuilder("this. is a test");
    pos = StringUtil.lastIndexOf(b, '.');
    assertEquals(4, pos);
  }

  @Test
  public void testDecodeUnicode()
  {
    String value = "Incorrect \\ string";
    String decoded = StringUtil.decodeUnicode(value);
    assertEquals(value, decoded);

    value = "Test \\u00E4\\u00E5";
    decoded = StringUtil.decodeUnicode(value);
    assertEquals("Test \u00E4\u00E5", decoded);

    value = "Wrong \\uxyz encoded";
    decoded = StringUtil.decodeUnicode(value);
    assertEquals(value, decoded);

    value = "Wrong \\u04";
    decoded = StringUtil.decodeUnicode(value);
    assertEquals("Wrong string not decoded", value, decoded);

    value = "test \\u";
    decoded = StringUtil.decodeUnicode(value);
    assertEquals("Wrong string not decoded", value, decoded);

    value = "test \\u wrong";
    decoded = StringUtil.decodeUnicode(value);
    assertEquals("Wrong string not decoded", value, decoded);

    decoded = StringUtil.decodeUnicode("\\r\\ntest");
    assertEquals("Single char not replaced correctly", "\r\ntest", decoded);

    decoded = StringUtil.decodeUnicode("Hello \\t World");
    assertEquals("Single char not replaced correctly", "Hello \t World", decoded);

    decoded = StringUtil.decodeUnicode("test\\t");
    assertEquals("Single char not replaced correctly", "test\t", decoded);

    decoded = StringUtil.decodeUnicode("test\\x");
    assertEquals("Single char not replaced correctly", "test\\x", decoded);

    decoded = StringUtil.decodeUnicode("test\\");
    assertEquals("Single char not replaced correctly", "test\\", decoded);

    decoded = StringUtil.decodeUnicode("test\\\\");
    assertEquals("test\\", decoded);

    value = "abc\\\\def";
    decoded = StringUtil.decodeUnicode(value);
    assertEquals("abc\\def", decoded);

    value = "abc\\\\n";
    decoded = StringUtil.decodeUnicode(value);
    assertEquals("abc\\n", decoded);
  }

  @Test
  public void testReplace()
  {
    String s = StringUtil.replace(null, "gaga", "gogo");
    assertNull(s);

    s = StringUtil.replace("gaga", null, "gogo");
    assertEquals("gaga", s);

    s = StringUtil.replace("gaga", "gogo", null);
    assertEquals("gaga", s);

    String value = "something";
    String result = StringUtil.replace(value, "some", "other");
    assertEquals("otherthing", result);

    value = "ababababaab";
    result = StringUtil.replace(value, "a", "x");
    assertEquals("xbxbxbxbxxb", result);

    value = "foobear";
    result = StringUtil.replace(value, "bear", "bar");
    assertEquals("foobar", result);

    value = "some text";
    result = StringUtil.replace(value, "xyz", "zxy");
    assertEquals(value, result);

    value = "some text";
    result = StringUtil.replace(value, "text", "other text");
    assertEquals("some other text", result);

    value = "some text";
    result = StringUtil.replace(value, "some", "no");
    assertEquals("no text", result);
  }

  @Test
  public void testEncodeUnicode()
  {
    String value = "\u00E4";
    String enc = StringUtil.escapeText(value, CharacterRange.RANGE_7BIT, "");
    assertEquals("Umlaut not replaced", "\\u00E4", enc);

    value = "\n";
    enc = StringUtil.escapeText(value, CharacterRange.RANGE_7BIT, "");
    assertEquals("NL not replaced" , "\\n", enc);

    value = "\u0016";
    enc = StringUtil.escapeText(value, CharacterRange.RANGE_CONTROL, "", CharacterEscapeType.pgHex);
    assertEquals("\\x16", enc);

    enc = StringUtil.escapeText("a\nnewline and a tab\t", CharacterRange.RANGE_CONTROL, "", CharacterEscapeType.pgHex);
    assertEquals("a\\nnewline and a tab\\t", enc);

    enc = StringUtil.escapeText("a windows newline\r\n", CharacterRange.RANGE_CONTROL, "", CharacterEscapeType.pgHex);
    assertEquals("a windows newline\\r\\n", enc);

  }

  @Test
  public void testMakePlainLF()
  {
    String line = "line1\r\nline2";
    String newline = StringUtil.makePlainLinefeed(line);
    assertEquals("line1\nline2", newline);

    line = "line1\nline2";
    newline = StringUtil.makePlainLinefeed(line);
    assertEquals("Wrong replacement", "line1\nline2", newline);

    line = "line1\rline2";
    newline = StringUtil.makePlainLinefeed(line);
    assertEquals("line1\nline2", newline);

    line = "line1\n\rline2";
    newline = StringUtil.makePlainLinefeed(line);
    assertEquals("line1\nline2", newline);
  }

  @Test
  public void testTrimStringBuilder()
  {
    StringBuilder s = new StringBuilder();
    s.append("hello   ");
    StringUtil.trimTrailingWhitespace(s);
    assertEquals("hello", s.toString());

    s = new StringBuilder();
    s.append("hello\n");
    StringUtil.trimTrailingWhitespace(s);
    assertEquals("hello", s.toString());

    s = new StringBuilder();
    s.append("hello\nnewline  ");
    StringUtil.trimTrailingWhitespace(s);
    assertEquals("hello\nnewline", s.toString());

    s = new StringBuilder();
    s.append(" hello");
    StringUtil.trimTrailingWhitespace(s);
    assertEquals(" hello", s.toString());

    s = new StringBuilder();
    StringUtil.trimTrailingWhitespace(s);
    assertEquals("", s.toString());

    s = new StringBuilder(" ");
    StringUtil.trimTrailingWhitespace(s);
    assertEquals("", s.toString());

    s = new StringBuilder("         ");
    StringUtil.trimTrailingWhitespace(s);
    assertEquals("", s.toString());
  }

  @Test
  public void testRtrim()
  {
    String s = "bla";
    assertEquals(s, StringUtil.rtrim(s));

    s = " \tbla";
    assertEquals(s, StringUtil.rtrim(s));

    s = "bla \t\n";
    assertEquals("bla", StringUtil.rtrim(s));

    s = "  bla \t\n";
    assertEquals("  bla", StringUtil.rtrim(s));

    s = "bla \t\nbla";
    assertEquals(s, StringUtil.rtrim(s));

    s = " \n\r\t";
    assertEquals("", StringUtil.rtrim(s));

    s = "";
    assertEquals(s, StringUtil.rtrim(s));

    s = " ";
    assertEquals("", StringUtil.rtrim(s));

    s = null;
    assertNull(StringUtil.rtrim(s));
  }

  @Test
  public void testEqualString()
  {
    String one = "bla";
    String two = null;

    assertEquals(false, StringUtil.equalString(one, two));
    assertEquals(false, StringUtil.equalString(two, one));

    assertEquals(false, StringUtil.equalStringIgnoreCase(one, two));
    assertEquals(false, StringUtil.equalStringIgnoreCase(two, one));

    one = "bla";
    two = "bla";

    assertEquals(true, StringUtil.equalString(one, two));
    assertEquals(true, StringUtil.equalStringIgnoreCase(two, one));

    one = "bla";
    two = "BLA";

    assertEquals(false, StringUtil.equalString(one, two));
    assertEquals(true, StringUtil.equalStringIgnoreCase(two, one));

    one = "bla";
    two = "blub";

    assertEquals(false, StringUtil.equalString(one, two));
    assertEquals(false, StringUtil.equalStringIgnoreCase(two, one));
  }

  @Test
  public void testCaseCheck()
  {
    assertEquals(false, StringUtil.isUpperCase("This is a test"));
    assertEquals(true, StringUtil.isMixedCase("This is a test"));
    assertEquals(false, StringUtil.isLowerCase("This is a test"));

    assertEquals(true, StringUtil.isLowerCase("this is a test 12345 #+*-.,;:!\"$%&/()=?"));
    assertEquals(true, StringUtil.isUpperCase("THIS IS A TEST 12345 #+*-.,;:!\"$%&/()=?"));
    assertEquals(true, StringUtil.isUpperCase("1234567890"));
    assertEquals(true, StringUtil.isLowerCase("1234567890"));
  }

  @Test
  public void testGetRealLineLenght()
  {
    int len = StringUtil.getRealLineLength("bla\r");
    assertEquals(3,len);

    len = StringUtil.getRealLineLength("bla\r\n");
    assertEquals(3,len);

    len = StringUtil.getRealLineLength("bla\r\n\n");
    assertEquals(3,len);

    len = StringUtil.getRealLineLength("bla \r\n\n\r");
    assertEquals(4,len);

    len = StringUtil.getRealLineLength("bla");
    assertEquals(3,len);

    len = StringUtil.getRealLineLength("\r\n");
    assertEquals(0,len);

    len = StringUtil.getRealLineLength("\n");
    assertEquals(0,len);
  }

  @Test
  public void testIsWhitespace()
  {
    String s = "bla";
    assertEquals(false, StringUtil.isWhitespace(s));

    s = "   bla   ";
    assertEquals(false, StringUtil.isWhitespace(s));

    s = " \n \n";
    assertEquals(true, StringUtil.isWhitespace(s));

    s = " \t\r\n   ;";
    assertEquals(false, StringUtil.isWhitespace(s));

    s = "";
    assertEquals(false, StringUtil.isWhitespace(s));
  }

  @Test
  public void testTrimBuffer()
  {
    StringBuilder b = new StringBuilder("bla");
    StringUtil.trimTrailingWhitespace(b);
    assertEquals("Buffer was changed", "bla", b.toString());

    b = new StringBuilder("bla bla ");
    StringUtil.trimTrailingWhitespace(b);
    assertEquals("Whitespace not removed", "bla bla", b.toString());

    b = new StringBuilder("bla bla \t");
    StringUtil.trimTrailingWhitespace(b);
    assertEquals("Whitespace not removed", "bla bla", b.toString());

    b = new StringBuilder("bla bla \t\n\r  \t");
    StringUtil.trimTrailingWhitespace(b);
    assertEquals("Whitespace not removed", "bla bla", b.toString());
  }

  @Test
  public void testToArray()
  {
    List<String> elements = new LinkedList<>();
    elements.add("one");
    elements.add("two");
    elements.add("three");

    String[] result = StringUtil.toArray(elements, false);
    assertEquals(result.length, 3);
    assertEquals(result[1], "two");

    result = StringUtil.toArray(elements, true);
    assertEquals(result.length, 3);
    assertEquals(result[1], "TWO");
  }

  @Test
  public void testGetDoubleValue()
  {
    double value = StringUtil.getDoubleValue("123.45", -1);
    assertEquals(123.45, value, 0.01);

    value = StringUtil.getDoubleValue(" 123.45 ", -1);
    assertEquals(123.45, value, 0.01);

    value = StringUtil.getDoubleValue("bla", -66);
    assertEquals(-66, value, 0.01);
  }

  @Test
  public void testGetIntValue()
  {
    int iValue = StringUtil.getIntValue(" 123 ", -1);
    assertEquals(123, iValue);

    iValue = StringUtil.getIntValue("42", -1);
    assertEquals(42, iValue);

    iValue = StringUtil.getIntValue("bla", -24);
    assertEquals(-24, iValue);
  }

  @Test
  public void testStringToList()
  {
    String list = "1,2,3";
    List l = StringUtil.stringToList(list, ",", true, true, true);
    assertEquals("Wrong number of elements returned", 3, l.size());

    list = "1,2,,3";
    l = StringUtil.stringToList(list, ",", true, true, true);
    assertEquals("Empty element not removed", 3, l.size());

    list = "1,2, ,3";
    l = StringUtil.stringToList(list, ",", false);
    assertEquals("Empty element removed", 4, l.size());

    list = "1,2,,3";
    l = StringUtil.stringToList(list, ",", false);
    assertEquals("Null element not removed", 3, l.size());

    list = " 1 ,2,3";
    l = StringUtil.stringToList(list, ",", true);
    assertEquals("Null element not removed", 3, l.size());
    assertEquals(" 1 ", l.get(0));

    l = StringUtil.stringToList(list, ",", true, true);
    assertEquals("Element not trimmed", "1", l.get(0));

    list = "1,\"2,5\",3";
    l = StringUtil.stringToList(list, ",", true, true, true);
    assertEquals("Quoted string not recognized","2,5", l.get(1));

    list = "library.jar";
    l = StringUtil.stringToList(list, ";", true, true, false);
    assertEquals("Single element list not correct", 1, l.size());
    assertEquals("Single element list not correct", "library.jar", l.get(0));

  }

  @Test
  public void testHasOpenQuotes()
  {
    String value = "this line does not have quotes";
    assertFalse(StringUtil.hasOpenQuotes(value, '\'', QuoteEscapeType.none));

    value = "this line 'does' have quotes";
    assertFalse(StringUtil.hasOpenQuotes(value, '\'', QuoteEscapeType.none));

    value = "this line leaves a 'quote open";
    assertTrue(StringUtil.hasOpenQuotes(value, '\'', QuoteEscapeType.none));

    value = "this 12\\\"monitor ";
    assertFalse(StringUtil.hasOpenQuotes(value, '"', QuoteEscapeType.escape));

    value = "this \"monitor ";
    assertTrue(StringUtil.hasOpenQuotes(value, '"', QuoteEscapeType.escape));

    value = "'foo';'Peter\\'s house';'some data'";
    assertFalse(StringUtil.hasOpenQuotes(value, '\'', QuoteEscapeType.escape));

    value = "'foo';'Peter''s house';'some data'";
    assertFalse(StringUtil.hasOpenQuotes(value, '\'', QuoteEscapeType.duplicate));

    value = "'foo';'Peter's house';'some data'";
    assertTrue(StringUtil.hasOpenQuotes(value, '\'', QuoteEscapeType.duplicate));
  }

  @Test
  public void testIsNumber()
  {
    boolean isNumber = StringUtil.isNumber("1");
    assertEquals(true, isNumber);

    isNumber = StringUtil.isNumber("1.234");
    assertEquals(true, isNumber);

    isNumber = StringUtil.isNumber("1.xxx");
    assertEquals(false, isNumber);

    isNumber = StringUtil.isNumber("bla");
    assertEquals(false, isNumber);

    assertFalse(StringUtil.isNumber(""));

    assertTrue(StringUtil.isNumber("  1 "));
  }

  @Test
  public void testMaxString()
  {
    String s = StringUtil.getMaxSubstring("Dent", 4, null);
    assertEquals("Truncated", "Dent", s);

    s = StringUtil.getMaxSubstring("Dent1", 4, null);
    assertEquals("Truncated", "Dent", s);

    s = StringUtil.getMaxSubstring("Den", 4, null);
    assertEquals("Truncated", "Den", s);

    s = StringUtil.getMaxSubstring("Beeblebrox", 5, null);
    assertEquals("Truncated", "Beebl", s);

    s = StringUtil.getMaxSubstring("Beeblebrox", 5, "...");
    assertEquals("Truncated", "Beebl...", s);

  }

  @Test
  public void testTrimQuotes()
  {
    String s = StringUtil.trimQuotes(" \"bla\" ");
    assertEquals("bla", s);
    s = StringUtil.trimQuotes(" \"bla ");
    assertEquals(" \"bla ", s);
    s = StringUtil.trimQuotes(" 'bla' ");
    assertEquals("bla", s);
  }

  @Test
  public void testPadRight()
  {
    String result = StringUtil.padRight("someStuff", 20);
    assertEquals(20, result.length());
    assertTrue(result.startsWith("someStuff"));
  }

  @Test
  public void testFormatNumber()
  {
    String result = StringUtil.formatNumber(10, 10, true);
    assertEquals(10, result.length());
    assertEquals("10        ", result);

    result = StringUtil.formatNumber(10, 10, false);
    assertEquals(10, result.length());
    assertEquals("        10", result);

    result = StringUtil.formatNumber(100000, 5, false);
    assertEquals(6, result.length());
    assertEquals("100000", result);
  }

  @Test
  public void testContainsWords()
  {
    String input = "So long and thanks for all the fish";
    List<String> values = CollectionUtil.arrayList("thanks", "phish");

    boolean found = StringUtil.containsWords(input, values, false, true);
    assertTrue(found);

    found = StringUtil.containsWords(input, values, true, true);
    assertFalse(found);

    values = CollectionUtil.arrayList("thanks", "fish");
    found = StringUtil.containsWords(input, values, true, false);
    assertTrue(found);

    found = StringUtil.containsWords(input, values, false, true);
    assertTrue(found);

    values = CollectionUtil.arrayList("thanks", "FISH");
    found = StringUtil.containsWords(input, values, true, false);
    assertFalse(found);

    found = StringUtil.containsWords(input, values, true, true);
    assertTrue(found);

    values = CollectionUtil.arrayList("nothere", "also_not_there");
    found = StringUtil.containsWords(input, values, true, false);
    assertFalse(found);

    values = CollectionUtil.arrayList("nothere", "also_not_there");
    found = StringUtil.containsWords(input, values, false, false);
    assertFalse(found);

    values = CollectionUtil.arrayList("a[ndl]{2}");
    found = StringUtil.containsWords(input, values, false, false, true);
    assertTrue(found);

    input = "Special $com";
    values = CollectionUtil.arrayList("$com");
    found = StringUtil.containsWords(input, values, false, false, false);
    assertTrue(found);

    found = StringUtil.containsWords(
      "Life, Universe\nand everything",
      CollectionUtil.arrayList("^and"), false, false, true
    );
    assertTrue(found);
  }

  @Test
  public void testArray()
  {
    int[] data = new int[] {1,2,3,4,5};
    String list = StringUtil.arrayToString(data);
    assertEquals("1,2,3,4,5", list);
    int[] data2 = StringUtil.stringToArray(list);
    assertArrayEquals(data, data2);
  }

  @Test
  public void testFindOccurance()
  {
    String value = "jdbc:oracle:thin:@localhost:1521:orcl";
    int pos = StringUtil.findOccurance(value, ':', 3);
    assertEquals(value.indexOf('@') -1, pos);

    value = "foobar";
    pos = StringUtil.findOccurance(value, ':', 3);
    assertEquals(-1, pos);

    value = "jdbc:oracle:thin:@localhost:1521:orcl";
    pos = StringUtil.findOccurance(value, ':', 8);
    assertEquals(-1, pos);

    value = "jdbc:oracle:thin:@localhost:1521:orcl";
    pos = StringUtil.findOccurance(value, ':', 1);
    assertEquals(4, pos);
  }

  @Test
  public void testCoalesce()
  {
    String one = null;
    String two = "two";
    String three = "three";

    String result = StringUtil.coalesce(one, two, three);
    assertNotNull(result);
    assertEquals(two, result);

    one = "one";
    result = StringUtil.coalesce(one, two, three);
    assertEquals(one, result);

    one = null;
    two = null;
    result = StringUtil.coalesce(one, two, three);
    assertEquals(three, result);

    result = StringUtil.coalesce("foo");
    assertEquals("foo", result);
  }

  @Test
  public void testReplaceProperties()
  {
    String input = "Username ${user.name}";
    String replaced = StringUtil.replaceProperties(input);
    assertEquals("Username " + System.getProperty("user.name"), replaced);

    input = "Username {user.name}";
    replaced = StringUtil.replaceProperties(input);
    assertEquals(input, replaced);

    input = "Username $USER";
    replaced = StringUtil.replaceProperties(input);
    assertEquals(input, replaced);
  }
}
