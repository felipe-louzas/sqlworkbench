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
package workbench.storage;

import workbench.WbTestCase;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Thomas Kellerer
 */
public class RowDataListTest
  extends WbTestCase
{

  public RowDataListTest()
  {
    super("RowDataListTest");
  }

  @Test
  public void testAdd()
  {
    RowDataList list = new RowDataList();
    list.reset();

    // Make sure add() still works properly after calling reset()
    RowData row = new RowData(2);
    row.setValue(0, "Test");
    row.setValue(1, 42);
    list.add(row);
    RowData r = list.get(0);
    assertNotNull(r);
    assertEquals(r.getValue(0), "Test");
    assertEquals(r.getValue(1), 42);
  }

  @Test
  public void testList()
  {
    RowDataList list = new RowDataList();
    assertEquals(0, list.size());

    for (int i=0; i < 500; i++)
    {
      RowData row = new RowData(2);
      row.setValue(0, "Foobar");
      row.setValue(1, i);
      list.add(row);
    }
    assertEquals(500, list.size());
    for (int i=0; i < 500; i++)
    {
      Integer value = (Integer)list.get(i).getValue(1);
      assertNotNull(value);
      assertEquals(i, value.intValue());
    }

    for (int i=0; i < 200; i++)
    {
      list.remove(0);
    }
    assertEquals(300, list.size());
    list.reset();
    assertEquals(0, list.size());

    list = new RowDataList();
    RowData one = new RowData(2);
    one.setValue(0, "foobar");
    one.setValue(1, 1);

    RowData two = new RowData(2);
    two.setValue(0, "foobar");
    two.setValue(1, 2);

    RowData three = new RowData(2);
    three.setValue(0, "foobar");
    three.setValue(1, 3);

    list.add(one);
    list.add(three);
    list.add(1, two);

    assertEquals(3, list.size());

    Object value = list.get(0).getValue(1);
    assertEquals(Integer.valueOf(1), value);

    value = list.get(1).getValue(1);
    assertEquals(Integer.valueOf(2), value);

    value = list.get(2).getValue(1);
    assertEquals(Integer.valueOf(3), value);

    assertSame(one, list.get(0));
    assertSame(two, list.get(1));
    assertSame(three, list.get(2));

    list = new RowDataList(0);
    list.add(one);
    list.add(two);
    list.add(three);
    assertEquals(3, list.size());

    list = new RowDataList(0);
    list.add(0, one);
    list.add(0, two);
    list.add(0, three);
    assertEquals(3, list.size());
    assertSame(three, list.get(0));
    assertSame(two, list.get(1));
    assertSame(one, list.get(2));
  }

}
