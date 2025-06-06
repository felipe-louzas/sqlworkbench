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

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class CsvLineParserTest
{

  @Test
  public void testLeadingEmptyQuote()
  {
    String line = "\"\";3;\"test.dat\";\"KA007\";1;13;";
    CsvLineParser parser = new CsvLineParser(";",'"');
    parser.setQuoteEscaping(QuoteEscapeType.duplicate);
    parser.setLine(line);
    parser.setReturnEmptyStrings(true);

    List<String> items = parser.getAllElements();
    assertEquals("", items.get(0));
    assertEquals("3", items.get(1));
    assertEquals("test.dat", items.get(2));
    assertEquals("KA007", items.get(3));
  }

  @Test
  public void testEscapedBackslash()
  {
    String input = "\"1\";\"foo \\\"bar\\\\\";\"2\"";
    CsvLineParser parser = new CsvLineParser(";",'"');
    parser.setReturnEmptyStrings(true);
    parser.setTrimValues(true);
    parser.setUnquotedEmptyStringIsNull(false);
    parser.setQuoteEscaping(QuoteEscapeType.escape);
    parser.setLine(input);
    List<String> items = parser.getAllElements();
    String expected = "foo \"bar\\";
//    System.out.println(expected);
//    System.out.println("items: " + items);
    assertEquals(3, items.size());
    assertEquals(expected, items.get(1));
  }

  @Test
  public void testLeadingDelimiter()
  {
    String input = "\t\tval2\tval3\n";

    CsvLineParser parser = new CsvLineParser("\t",'"');
    parser.setReturnEmptyStrings(true);
    parser.setTrimValues(true);
    parser.setUnquotedEmptyStringIsNull(false);
    parser.setLine(input);
    List<String> elements = parser.getAllElements();
    assertEquals(4, elements.size());
    assertEquals("", elements.get(0));
    assertEquals("", elements.get(1));
    assertEquals("val2", elements.get(2));
    assertEquals("val3", elements.get(3));
  }

  @Test
  public void testMultiCharDelimiter()
    throws Exception
  {
    CsvLineParser parser = new CsvLineParser("\t\t",'\'');
    parser.setReturnEmptyStrings(true);
    parser.setLine("1234\t\tde_DE\t\t8888888888");
    List<String> elements = parser.getAllElements();

    assertEquals(3, elements.size());
    assertEquals("1234", elements.get(0));
    assertEquals("de_DE", elements.get(1));
    assertEquals("8888888888", elements.get(2));

    parser.setLine("1234\t\tde_DE\t\t8888888888\t\t");
    elements = parser.getAllElements();
    assertEquals(4, elements.size());
    assertEquals("1234", elements.get(0));
    assertEquals("de_DE", elements.get(1));
    assertEquals("8888888888", elements.get(2));
    assertEquals("", elements.get(3));

    parser.setLine("'12\t34'\t\tde_DE\t\t8888888888");
    elements = parser.getAllElements();
    assertEquals(3, elements.size());
    assertEquals("12\t34", elements.get(0));
    assertEquals("de_DE", elements.get(1));
    assertEquals("8888888888", elements.get(2));
  }

  @Test
  public void testEscapedEscapes()
  {
    CsvLineParser parser = new CsvLineParser(';','\'');
    parser.setQuoteEscaping(QuoteEscapeType.escape);
    parser.setReturnEmptyStrings(true);
    String line = "'\\\\\\'ku\"la'";
//    System.out.println(line);
    parser.setLine(line);

    String result = null;
    if (parser.hasNext())
    {
      result = parser.getNext();
    }
    String ex = "\\'ku\"la";
//    System.out.println("expected: " + ex + "  result: " + result);
    assertEquals(ex, result);

    parser.setLine("'\\\\ku\"la'");
    result = null;
    if (parser.hasNext())
    {
      result = parser.getNext();
    }
    ex = "\\ku\"la";
//    System.out.println("expected: " + ex + "  result: " + result);
    assertEquals(ex, result);

    parser.setLine("'\\'ku\"la'");
    result = null;
    if (parser.hasNext())
    {
      result = parser.getNext();
    }
    ex = "'ku\"la";
    assertEquals(ex, result);
  }

  @Test
  public void testEscapedQuotes()
  {
    CsvLineParser parser = new CsvLineParser('\t','"');
    parser.setLine("one\twith\\\"quotes\t\"three\tvalues\\\"\"\t\tdef");
    parser.setQuoteEscaping(QuoteEscapeType.escape);
    List<String> result = parser.getAllElements();
    assertEquals("Not enough values", 5, result.size());
    String v = result.get(1);
    assertEquals("Wrong second value", "with\"quotes", v);
    v = result.get(2);
    assertEquals("Wrong third value", "three\tvalues\"", v);
    assertNull(result.get(3));
  }

  @Test
  public void testDuplicatedQuotes()
  {
    CsvLineParser parser = new CsvLineParser('\t','"');
    parser.setLine("one\twith\"\"quotes\t\"three\tvalue\"\"s\"\t");
    parser.setQuoteEscaping(QuoteEscapeType.duplicate);
    List<String> result = parser.getAllElements();
    assertEquals("Not enough values", 4, result.size());
    String v = result.get(1);
    assertEquals("Wrong second value", "with\"quotes", v);
    v = result.get(2);
    assertEquals("Wrong third value", "three\tvalue\"s", v);

    assertNull(result.get(3));

    parser = new CsvLineParser(';', '\'');
    String line = "'2';'ab''c'';d''ef'";
    parser.setQuoteEscaping(QuoteEscapeType.duplicate);
    parser.setLine(line);
    result = parser.getAllElements();
    assertEquals(2, result.size());
    assertEquals("2", result.get(0));
    assertEquals("ab'c';d'ef", result.get(1));

    parser = new CsvLineParser(',', '\'');
    parser.setQuoteEscaping(QuoteEscapeType.duplicate);
    parser.setLine("'arthur''s house','ford'");
    parser.setTrimValues(true);
    result = parser.getAllElements();
//    assertEquals(2, result.size());
    assertEquals("arthur's house", result.get(0));
    assertEquals("ford", result.get(1));

    parser = new CsvLineParser(',','"');
    parser.setQuoteEscaping(QuoteEscapeType.duplicate);
    parser.setReturnEmptyStrings(true);
    parser.setLine("one,\"hello \"\"\",three");
    result = parser.getAllElements();
    assertEquals(3, result.size());
    assertEquals("one", result.get(0));
    assertEquals("hello \"", result.get(1));
    assertEquals("three", result.get(2));

    parser.setLine("one,\"\",three");
    result = parser.getAllElements();
    assertEquals(3, result.size());
    assertEquals("one", result.get(0));
    assertEquals("", result.get(1));
    assertEquals("three", result.get(2));

    parser.setLine("one,\"\"\"\",three");
    result = parser.getAllElements();
    assertEquals(3, result.size());
    assertEquals("one", result.get(0));
    assertEquals("\"", result.get(1));
    assertEquals("three", result.get(2));
  }

  @Test
  public void testGetEmptyValues()
  {
    // Check for empty elements at the end
    CsvLineParser parser = new CsvLineParser('\t');
    parser.setLine("one\t");
    parser.setReturnEmptyStrings(true);
    List<String> result = parser.getAllElements();
    assertEquals("Not enough values", 2, result.size());

    assertNotNull("Null string returned", result.get(1));

    parser.setLine("one\t");
    parser.setReturnEmptyStrings(false);
    result = parser.getAllElements();
    assertEquals("Not enough values", 2, result.size());
    assertNull("Empty string returned", result.get(1));

    // Check for empty element at the beginning
    parser.setReturnEmptyStrings(true);
    parser.setLine("\ttwo\tthree");
    if (parser.hasNext())
    {
      String value = parser.getNext();
      assertEquals("First value not an empty string", "", value);
    }
    else
    {
      fail("No value returned");
    }

    parser.setReturnEmptyStrings(false);
    parser.setLine("\ttwo\tthree");
    if (parser.hasNext())
    {
      String value = parser.getNext();
      assertNull("First value not null", value);
    }
    else
    {
      fail("No value returned");
    }

    parser = new CsvLineParser('\t', '\'');
    parser.setUnquotedEmptyStringIsNull(true);
    parser.setQuoteEscaping(QuoteEscapeType.escape);
    parser.setLine("''\t\tvalue");
    result = parser.getAllElements();
    assertEquals(3, result.size());
    assertNotNull(result.get(0));
    assertEquals("", result.get(0));
    assertNull(result.get(1));
    assertEquals("value", result.get(2));

    parser = new CsvLineParser(';', '\'');
    parser.setUnquotedEmptyStringIsNull(true);
    parser.setQuoteEscaping(QuoteEscapeType.none);
    parser.setLine("1;''");
    result = parser.getAllElements();
    assertEquals(2, result.size());
    assertNotNull(result.get(0));
    assertNotNull(result.get(1));
    assertEquals("1", result.get(0));
    assertEquals("", result.get(1));
  }

  @Test
  public void testParser()
  {
    String line = "one\ttwo\tthree\tfour\tfive";
    CsvLineParser parser = new CsvLineParser('\t');
    parser.setLine(line);
    List<String> elements = parser.getAllElements();
    assertEquals("Wrong number of elements", 5, elements.size());
    assertEquals("Wrong first value", "one", elements.get(0));
    assertEquals("Wrong second value", "two", elements.get(1));

    // check for embedded quotes without a quote defined!
    parser.setLine("one\ttwo\"values\tthree");
    parser.getNext(); // skip the first
    String value = parser.getNext();
    assertEquals("Invalid second element", "two\"values", value);

    parser = new CsvLineParser('\t', '"');
    parser.setTrimValues(false);
    parser.setReturnEmptyStrings(false);
    parser.setLine("one\t\"quoted\tdelimiter\"\t  three  ");
    List<String> l = parser.getAllElements();

    assertEquals("Not enough values", 3, l.size());
    assertEquals("Wrong quoted value", "quoted\tdelimiter", l.get(1));
    assertEquals("Value was trimmed", "  three  ", l.get(2));

    parser.setTrimValues(true);
    parser.setLine("one\t   two   ");
    l = parser.getAllElements();

    assertEquals("Not enough values", 2, l.size());
    assertEquals("Value was not trimmed", "two", l.get(1));

    // Test a different delimiter
    parser = new CsvLineParser(';', '"');
    parser.setLine("one;two;\"one;element\"");
    elements = parser.getAllElements();
    assertEquals("Wrong number of elements", 3, elements.size());
    assertEquals("Wrong first value", "one", elements.get(0));
    assertEquals("Wrong second value", "two", elements.get(1));
    assertEquals("Wrong third value", "one;element", elements.get(2));
  }

}
