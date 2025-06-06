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
package workbench.db.oracle;

import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.OracleTest;
import workbench.db.WbConnection;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
@Category(OracleTest.class)
public class OracleDataConverterTest
  extends WbTestCase
{

  public OracleDataConverterTest()
  {
    super("OracleDataConverterTest");
  }

  @BeforeClass
  public static void setUp()
    throws Exception
  {
    OracleTestUtil.initTestCase();
    WbConnection con = OracleTestUtil.getOracleConnection();
    if (con == null) return;

    String sql =
      "create table some_table (id integer, some_data raw(200));\n" +
      "insert into some_table (id, some_data) values (42, utl_raw.cast_to_raw('0123'));\n" +
      "commit;\n";
    TestUtil.executeScript(con, sql);
  }

  @AfterClass
  public static void tearDown()
    throws Exception
  {
    OracleTestUtil.cleanUpTestCase();
  }

  @Test
  public void testConvertValue()
    throws Exception
  {
    WbConnection con = OracleTestUtil.getOracleConnection();
    assertNotNull(con);

    String select = "SELECT rowid, some_data FROM some_table";
    Statement stmt = con.createStatement();
    ResultSet rs = stmt.executeQuery(select);
    OracleDataConverter converter = OracleDataConverter.getInstance();
    assertNotNull(converter);
    if (rs.next())
    {
      Object rowid = rs.getObject(1);
      Object convertedId = converter.convertValue(Types.ROWID, "ROWID", rowid);
      assertNotNull(convertedId);
      assertTrue(convertedId instanceof String);

      Object raw = rs.getObject(2);
      Object convertedRaw = converter.convertValue(Types.VARBINARY, "RAW", raw);
      assertNotNull(convertedId);
      assertTrue(convertedId instanceof String);
      assertEquals("30313233", convertedRaw);
    }
  }
}
