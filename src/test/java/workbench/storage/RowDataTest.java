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

import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Random;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.JdbcUtils;
import workbench.db.WbConnection;

import workbench.storage.reader.RowDataReader;

import workbench.util.SqlUtil;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class RowDataTest
  extends WbTestCase
{

  public RowDataTest()
  {
    super("RowDataTest");
  }

  @Test
  public void testTrimCharData()
    throws Exception
  {
    TestUtil util = getTestUtil();
    WbConnection  con = util.getHSQLConnection("charTest");

    // HSQLDB does not pad a CHAR column to the defined length as defined
    // by the ANSI standard. But it does not remove trailing spaces either
    // so by storing trailing spaces, the trimCharData feature can be tested
    TestUtil.executeScript(con,
      "CREATE TABLE char_test (char_data char(5), vchar varchar(10));\n" +
      "INSERT INTO char_test VALUES ('1    ', '1    ');\n" +
      "INSERT INTO char_test VALUES ('12   ', '12   ');\n" +
      "INSERT INTO char_test VALUES ('123  ', '123  ');\n" +
      "COMMIT;\n" +
      "");
    Statement stmt = null;
    ResultSet rs = null;
    try
    {
      stmt = con.createStatement();
      rs = stmt.executeQuery("select char_data, vchar from char_test");
      ResultInfo info = new ResultInfo(rs.getMetaData(), con);
      RowDataReader reader = new RowDataReader(info, con);
      rs.next();
      RowData row = reader.read(rs, true);
      String v = (String)row.getValue(0);
      assertEquals("1", v);
      v = (String)row.getValue(1);
      assertEquals("1    ", v);

      rs.next();
      row = reader.read(rs, false);
      v = (String)row.getValue(0);
      assertEquals("12   ", v);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
      con.disconnect();
    }
    util.emptyBaseDirectory();
  }

  @Test
  public void testConverter()
    throws Exception
  {
    TestUtil util = getTestUtil();
    WbConnection  con = util.getHSQLConnection("charTest");

    // HSQLDB does not pad a CHAR column to the defined length as defined
    // by the ANSI standard. But it does not remove trailing spaces either
    // so by storing trailing spaces, the trimCharData feature can be tested
    TestUtil.executeScript(con,
      "CREATE TABLE char_test (char_data char(5), vchar varchar(10));\n" +
      "INSERT INTO char_test VALUES ('1    ', '1    ');\n" +
      "INSERT INTO char_test VALUES ('12   ', '12   ');\n" +
      "INSERT INTO char_test VALUES ('123  ', '123  ');\n" +
      "COMMIT;\n" +
      "");
    Statement stmt = null;
    ResultSet rs = null;
    DataConverter trim = new DataConverter()
    {

      @Override
      public Object convertValue(int jdbcType, String dbmsType, Object originalValue)
      {
        if (originalValue instanceof String)
        {
          return ((String)originalValue).trim();
        }
        return originalValue;
      }

      @Override
      public Class getConvertedClass(int jdbcType, String dbmsType)
      {
        return String.class;
      }

      @Override
      public boolean convertsType(int jdbcType, String dbmsType)
      {
        return SqlUtil.isCharacterType(jdbcType);
      }
    };

    try
    {
      stmt = con.createStatement();
      rs = stmt.executeQuery("select char_data, vchar from char_test");
      ResultInfo info = new ResultInfo(rs.getMetaData(), con);

      RowDataReader reader = new RowDataReader(info, con);
      reader.setConverter(trim);
      rs.next();
      RowData row = reader.read(rs, false);
      String v = (String)row.getValue(0);
      assertEquals("1", v);
      v = (String)row.getValue(1);
      assertEquals("1", v);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
      con.disconnect();
    }
    util.emptyBaseDirectory();
  }

  @Test
  public void testBlobs()
  {
    RowData row = new RowData(2);
    row.setValue(0, 1);
    row.setValue(1, new byte[] {1,2,3});
    row.resetStatus();

    row.setValue(1, new byte[] {1,2,3});
    assertFalse(row.isColumnModified(1));
    assertFalse(row.isModified());
  }

  /**
   * Test comparing strings ignoring different line endings.
   *
   * This is used when comparing data between different DBMS.
   */
  @Test
  public void testClobs()
  {
    String one = "EditSettings.Property.MaskType=\"1\" \nEditSettings.Property.Mask=\"d\"";
    String other = "EditSettings.Property.MaskType=\"1\" \r\nEditSettings.Property.Mask=\"d\"";
    assertTrue(RowData.objectsAreEqual(one, other, true));

    one = "Line one\rLine two\nLine three";
    other = "Line one\r\nLine two\rLine three";
    assertTrue(RowData.objectsAreEqual(one, other, true));
  }

  @Test
  public void testResetStatus()
  {
    RowData row = new RowData(2);
    row.setValue(0, Integer.valueOf(42));
    row.setValue(1, "Test");
    row.resetStatus();

    row.setValue(0, Integer.valueOf(43));
    row.setValue(1, "Test2");
    assertTrue(row.isModified());

    row.resetStatusForColumn(1);
    assertTrue(row.isModified());
    assertTrue(row.isColumnModified(0));
    assertFalse(row.isColumnModified(1));

    row.resetStatusForColumn(0);
    assertFalse(row.isColumnModified(0));
    assertFalse(row.isColumnModified(1));
    assertFalse(row.isModified());

    row = new RowData(2);
    row.setValue(0, 42);
    row.setValue(1, "Test");
    row.resetStatus();

    row.resetStatusForColumn(0);
    row.resetStatusForColumn(1);
    assertEquals(Integer.valueOf(42), row.getValue(0));
    assertEquals("Test", row.getValue(1));

    row.setValue(0, Integer.valueOf(43));
    assertEquals(Integer.valueOf(43), row.getValue(0));
    row.resetStatusForColumn(0);
    assertEquals("Test", row.getValue(1));
  }


  @Test
  public void testChangeValues()
  {
    RowData row = new RowData(2);
    assertTrue(row.isNew());

    row.setValue(0, "123");
    row.setValue(1, 42);
    assertTrue(row.isNew());
    assertTrue(row.isModified());
    assertEquals("123", row.getValue(0));
    assertEquals(42, row.getValue(1));

    assertEquals("123", row.getOriginalValue(0));
    assertEquals(42, row.getOriginalValue(1));

    row.resetStatus();
    assertFalse(row.isModified());

    Object value = row.getValue(0);
    assertEquals(value, "123");
    value = row.getValue(1);
    assertEquals(value, 42);

    row.setValue(0, null);
    value = row.getValue(0);
    assertNull(value);
    assertEquals("123", row.getOriginalValue(0));
    assertTrue(row.isModified());

    row.resetStatus();
    row.setValue(0, "456");
    value = row.getValue(0);
    assertEquals(value, "456");
    assertNull(row.getOriginalValue(0));
    assertTrue(row.isColumnModified(0));

    row.setValue(0, "123");
    row.setValue(1, null);
    row.resetStatus();
    row.setValue(1, null);
    assertFalse(row.isModified());
  }

  @Test
  public void testCopy()
    throws Exception
  {
    Random r = new Random();
    int colCount = 15;
    RowData one = new RowData(colCount);
    for (int i=0; i < colCount; i++)
    {
      one.setValue(i, r.nextLong());
    }
    RowData copy = one.createCopy();
    assertTrue(copy.equals(one));
    assertTrue(Arrays.equals(one.getData(), copy.getData()));
  }

  @Test
  public void testRemoveColumn()
  {
    RowData one = new RowData(3);
    one.setValue(0, "One");
    one.setValue(1, "Two");
    one.setValue(2, "Three");
    one.removeColumn(1);
    assertEquals(2, one.getColumnCount());
    assertEquals("One", one.getValue(0));
    assertEquals("Three", one.getValue(1));

    one = new RowData(2);
    one.setValue(0,10);
    one.setValue(1,20);
    one.removeColumn(1);
    assertEquals(1, one.getColumnCount());
    assertEquals(10, one.getValue(0));

    one = new RowData(4);
    one.setValue(0,10);
    one.setValue(1,20);
    one.setValue(2,30);
    one.setValue(3,40);

    one.removeColumn(0);
    assertEquals(3, one.getColumnCount());
    assertEquals(20, one.getValue(0));
  }
}
