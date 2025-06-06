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
package workbench.db.mysql;


import java.util.List;

import workbench.TestUtil;

import workbench.db.IndexDefinition;
import workbench.db.IndexReader;
import workbench.db.MySQLTest;
import workbench.db.TableIdentifier;
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
@Category(MySQLTest.class)
public class MySQLIndexReaderTest
{
  public MySQLIndexReaderTest()
  {
  }

  @BeforeClass
  public static void setUpClass()
    throws Exception
  {
    MySQLTestUtil.initTestcase("MySQLDataStoreTest");

    WbConnection con = MySQLTestUtil.getMySQLConnection();
    if (con == null) return;

    String sql =
      "CREATE TABLE foo ( id integer, info1 varchar(100), info2 varchar(100));\n"  +
      "create index foo_idx on foo (info1(10), info2(20));";
    TestUtil.executeScript(con, sql);
  }

  @AfterClass
  public static void tearDownClass()
    throws Exception
  {
    WbConnection con = MySQLTestUtil.getMySQLConnection();
    if (con == null) return;
    String sql = "DROP TABLE foo;";
    TestUtil.executeScript(con, sql);
    MySQLTestUtil.cleanUpTestCase();
  }

  @Test
  public void testReadIndexes()
    throws Exception
  {
    WbConnection con = MySQLTestUtil.getMySQLConnection();
    assertNotNull("No connection available", con);

    IndexReader reader = con.getMetadata().getIndexReader();
    assertTrue(reader instanceof MySQLIndexReader);
    List<IndexDefinition> indexes = reader.getTableIndexList(new TableIdentifier("foo"), false);
    assertEquals(1, indexes.size());
    IndexDefinition idx = indexes.get(0);
    assertNotNull(idx);
    String expr = idx.getExpression();
    assertEquals("info1(10) ASC, info2(20) ASC", expr);
  }

}
