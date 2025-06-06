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
package workbench.db.postgres;

import java.sql.SQLException;
import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.PostgresDbTest;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.gui.completion.BaseAnalyzer;
import workbench.gui.completion.SelectAnalyzer;
import workbench.gui.completion.StatementContext;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
@Category(PostgresDbTest.class)
public class PostgresCompletionTest
  extends WbTestCase
{

  public PostgresCompletionTest()
  {
    super("PostgresCompletionTest");
  }

  @BeforeClass
  public static void setUpClass()
    throws Exception
  {
    PostgresTestUtil.initTestCase("completion_test");

    WbConnection con = PostgresTestUtil.getPostgresConnection();
    if (con == null) return;

    String sql =
      "CREATE TABLE data ( id integer primary key, info varchar(100));\n"  +
      "insert into data (id, info) values (1, 'gargleblaster');\n" +
      "commit;\n";
    TestUtil.executeScript(con, sql);
  }

  @AfterClass
  public static void tearDownClass()
    throws Exception
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    if (con == null) return;
    PostgresTestUtil.dropAllObjects(con);
  }

  @Test
  public void testSelectCompletion()
    throws SQLException
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    assertNotNull(con);

    String sql = "select  from  ";
    StatementContext context = new StatementContext(con, sql, sql.indexOf("from") + "from".length() + 1);
    BaseAnalyzer analyzer = context.getAnalyzer();
    assertTrue(analyzer instanceof SelectAnalyzer);
    List data = context.getData();
    assertNotNull(data);
    assertEquals(1, data.size());

    sql = "select  from data";
    context = new StatementContext(con, sql, sql.indexOf("select") + "select".length() + 1);
    analyzer = context.getAnalyzer();
    data = context.getData();
    assertNotNull(data);
    assertEquals(3, data.size());
  }

  @Test
  public void testDelete()
    throws SQLException
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    if (con == null) return;

    String sql = "delete from  ";
    StatementContext context = new StatementContext(con, sql, sql.indexOf("from") + "from".length() + 1);
    List data = context.getData();
    assertNotNull(data);
    assertEquals(1, data.size());
    assertTrue(data.get(0) instanceof TableIdentifier);
    TableIdentifier tbl = (TableIdentifier)data.get(0);
    assertEquals("data", tbl.getTableName());
  }

  @Test
  public void testInsert()
    throws SQLException
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    if (con == null) return;

    String sql = "insert into ";
    StatementContext context = new StatementContext(con, sql, sql.indexOf("into") + "into".length() + 1);
    List data = context.getData();
    assertNotNull(data);
    assertEquals(1, data.size());
    assertTrue(data.get(0) instanceof TableIdentifier);
    TableIdentifier tbl = (TableIdentifier)data.get(0);
    assertEquals("data", tbl.getTableName());

    sql = "insert into data (   ) values ";
    context = new StatementContext(con, sql, sql.indexOf('(') + 2);
    data = context.getData();
    assertNotNull(data);
    assertEquals(2, data.size());
  }

}
