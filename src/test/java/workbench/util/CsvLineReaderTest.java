/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
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
package workbench.util;

import java.io.BufferedReader;
import java.io.File;

import workbench.TestUtil;
import workbench.WbTestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class CsvLineReaderTest
  extends WbTestCase
{

  public CsvLineReaderTest()
  {
    super("CsvLineReaderTest");
  }

  @Before
  public void setUp()
  {
  }

  @After
  public void tearDown()
  {
  }

  @Test
  public void testMultiLineWithQuotes()
    throws Exception
  {
    TestUtil util = new TestUtil("testContinuationLines");
    File f = new File(util.getBaseDir(), "multiline_data.csv");
    TestUtil.writeFile(f,
      "1,\"line\none\",foo\n" +
      "2,\"\",bar\n", "UTF-8");

    BufferedReader in = null;
    try
    {
      in = EncodingUtil.createBufferedReader(f, "UTF-8");
      CsvLineReader reader = new CsvLineReader(in, '"', QuoteEscapeType.duplicate, true, "\n");

      String line = reader.readLine();
      assertEquals("1,\"line\none\",foo", line);
      line = reader.readLine();
      assertNotNull(line);
      assertEquals("2,\"\",bar", line);
    }
    finally
    {
      FileUtil.closeQuietely(in);
    }
  }

  @Test
  public void testMultiline()
    throws Exception
  {
    TestUtil util = new TestUtil("testContinuationLines");
    File f = new File(util.getBaseDir(), "data.csv");

    TestUtil.writeFile(f,
      "42,\"this is a \nlong\nline\",foo\n" +
      "\n" +
      "43,\"second row\",bar\n", "UTF-8");

    BufferedReader in = null;
    try
    {
      in = EncodingUtil.createBufferedReader(f, "UTF-8");
      CsvLineReader reader = new CsvLineReader(in, '"', QuoteEscapeType.none, true, "\n");
      reader.setIgnoreEmptyLines(true);

      String line = reader.readLine();
      assertEquals("42,\"this is a \nlong\nline\",foo", line);
      line = reader.readLine();
      assertNotNull(line);
      assertEquals("43,\"second row\",bar", line);
    }
    finally
    {
      FileUtil.closeQuietely(in);
    }
  }

  @Test
  public void testReader()
    throws Exception
  {
    String content =
      "firstname\tlastname\tnr\n" +
      "First\t\"Last\nname\"\t1\n" +
      "first2\tlast2\t2\n" +
      "first3\t\"last3\nlast3last3\"\t3\n" +
      "first4\t\"last4\tlast4\"\t4\n";

    TestUtil util = new TestUtil("tesxtContinuationLines");
    File f = new File(util.getBaseDir(), "data2.csv");

    TestUtil.writeFile(f, content, "UTF-8");

    BufferedReader in = null;
    try
    {
      in = EncodingUtil.createBufferedReader(f, "UTF-8");
      CsvLineReader reader = new CsvLineReader(in, '"', QuoteEscapeType.none, true, "\n");
      reader.setIgnoreEmptyLines(true);

      String line = reader.readLine();
      assertEquals("firstname\tlastname\tnr", line);

      line = reader.readLine();
      assertEquals("First\t\"Last\nname\"\t1", line);

      line = reader.readLine();
      assertEquals("first2\tlast2\t2", line);

      line = reader.readLine();
      assertEquals("first3\t\"last3\nlast3last3\"\t3", line);

      line = reader.readLine();
      assertEquals("first4\t\"last4\tlast4\"\t4", line);

      line = reader.readLine();
      assertNull(line);
    }
    finally
    {
      FileUtil.closeQuietely(in);
    }
  }

}
