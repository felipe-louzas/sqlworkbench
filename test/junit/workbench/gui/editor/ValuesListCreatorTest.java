/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2020 Thomas Kellerer.
 *
 * Licensed under a modified Apache License, Version 2.0 (the "License")
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

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class ValuesListCreatorTest
{

  @Test
  public void testCreateSimpleList()
  {
    String input = "1,2,3.14,10.20.30\n4,5,6.28,20.30.40";
    ValuesListCreator creator = new ValuesListCreator(input);
    String result = creator.createValuesList();
    String expected =
       "(1, 2, 3.14, '10.20.30'),\n" +
       "(4, 5, 6.28, '20.30.40')";
    assertEquals(expected, result.trim());
  }

  @Test
  public void testStrings()
  {
    String input = "1,Arthur,Dent\n2,Tricia,McMillan";
    ValuesListCreator creator = new ValuesListCreator(input);
    String result = creator.createValuesList();
    String expected =
       "(1, 'Arthur', 'Dent'),\n" +
       "(2, 'Tricia', 'McMillan')";
    assertEquals(expected, result.trim());
  }

  @Test
  public void testQuotedItems()
  {
    String input = "1,'Arthur','Dent'\n2,Tricia,McMillan";
    ValuesListCreator creator = new ValuesListCreator(input);
    String result = creator.createValuesList();
    String expected =
       "(1, 'Arthur', 'Dent'),\n" +
       "(2, 'Tricia', 'McMillan')";
    assertEquals(expected, result.trim());
  }

  @Test
  public void testInvalidList()
  {
    String input = "1,Arthur,Dent, 42\n2,Tricia,McMillan\nfoo";
    ValuesListCreator creator = new ValuesListCreator(input);
    String result = creator.createValuesList();
    String expected =
      "(1, 'Arthur', 'Dent', 42),\n" +
      "(2, 'Tricia', 'McMillan'),\n" +
      "('foo')";
    assertEquals(expected, result.trim());
  }

  @Test
  public void testDates()
  {
    String input = "1,2020-07-28,18:20\n2,2020-07-14,20:45";
    ValuesListCreator creator = new ValuesListCreator(input);
    String result = creator.createValuesList();
    String expected =
       "(1, '2020-07-28', '18:20'),\n" +
       "(2, '2020-07-14', '20:45')";
    assertEquals(expected, result.trim());
  }

  @Test
  public void testAlternateDelimiter()
  {
    String input =
      "|1  | 2020-07-22 18:19:20 | 3.14  | Arthur | Dent |\n" +
      "| 3 | 2020-06-22 14:12:25 | 6.42  | Tricia | McMillan | \n";
    ValuesListCreator creator = new ValuesListCreator(input, "|");
    String result = creator.createValuesList();
    String expected =
       "(1, '2020-07-22 18:19:20', 3.14, 'Arthur', 'Dent'),\n" +
       "(3, '2020-06-22 14:12:25', 6.42, 'Tricia', 'McMillan')";
    assertEquals(expected, result.trim());
  }

  @Test
  public void testNoTrim()
  {
    String input =
      "|1|Arthur|Dent|\n" +
      "|3|Marvin|  |\n";
    ValuesListCreator creator = new ValuesListCreator(input, "|");
    creator.setTrimItems(false);
    String result = creator.createValuesList();
    String expected =
       "(1, 'Arthur', 'Dent'),\n" +
       "(3, 'Marvin', '  ')";
    assertEquals(expected, result.trim());
  }

}
