/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
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
package workbench.storage;

import workbench.WbTestCase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class DataStoreWriterTest
  extends WbTestCase
{


  public DataStoreWriterTest()
  {
    super("DataStoreWriterTest");
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
  public void testWriter()
    throws Exception
  {
    DataStoreWriter writer = new DataStoreWriter("output");

    String output =
      "first line\n" +
      "second line\n" +
      "third line";

    char[] cbuf = output.toCharArray();

    writer.write(cbuf, 0, cbuf.length);
    writer.flush();
    DataStore ds = writer.getResult();
    assertNotNull(ds);
//    DataStorePrinter printer = new DataStorePrinter(ds);
//    printer.printTo(System.out);

    assertEquals(3, ds.getRowCount());
    assertEquals("first line", ds.getValueAsString(0, 0));
    assertEquals("second line", ds.getValueAsString(1, 0));
    assertEquals("third line", ds.getValueAsString(2, 0));

    writer.reset();
    writer.write(cbuf, 0, 20);
    writer.write(cbuf, 20, cbuf.length);
    writer.flush();

    ds = writer.getResult();

    assertNotNull(ds);
//    DataStorePrinter printer = new DataStorePrinter(ds);
//    printer.printTo(System.out);

    assertEquals(3, ds.getRowCount());
    assertEquals("first line", ds.getValueAsString(0, 0));
    assertEquals("second line", ds.getValueAsString(1, 0));
    assertEquals("third line", ds.getValueAsString(2, 0));
  }


}
