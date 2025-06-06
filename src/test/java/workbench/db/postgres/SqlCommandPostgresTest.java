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

import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.PostgresDbTest;
import workbench.db.WbConnection;

import workbench.storage.DataStore;

import workbench.sql.StatementRunner;
import workbench.sql.StatementRunnerResult;

import org.junit.*;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
@Category(PostgresDbTest.class)
public class SqlCommandPostgresTest
  extends WbTestCase
{

  private static final String TEST_ID = "pgSqlCommand";

  public SqlCommandPostgresTest()
  {
    super("PostgresSqlCommandTest");
  }

  @BeforeClass
  public static void setUp()
    throws Exception
  {
    PostgresTestUtil.initTestCase(TEST_ID);
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    if (con == null) return;

    TestUtil.executeScript(con,
      "create table person (id integer, firstname varchar(100), lastname varchar(100));\n" +
      "commit;\n"
    );
  }

  @AfterClass
  public static void tearDown()
    throws Exception
  {
    PostgresTestUtil.cleanUpTestCase();
  }

  @Before
  public void setUpTest()
    throws Exception
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    if (con == null) return;

    TestUtil.executeScript(con,
      "insert into person (id, firstname, lastname) values (1, 'Arthur', 'Dent');\n" +
      "insert into person (id, firstname, lastname) values (2, 'Zaphod', 'Beeblebrox');\n" +
      "commit;\n"
    );
  }

  @After
  public void cleanupTest()
    throws Exception
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    if (con == null) return;

    TestUtil.executeScript(con,
      "truncate table person;\n" +
      "commit;\n"
    );
  }

  @Test
  public void testSelect()
    throws Exception
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    assertNotNull(con);

    String sql = "select * from person order by id;";
    StatementRunner runner = getTestUtil().createConnectedStatementRunner(con);

    StatementRunnerResult result = runner.runStatement(sql);

    assertTrue(result.isSuccess());
    assertTrue(result.hasDataStores());
    List<DataStore> data = result.getDataStores();
    assertEquals(1, data.size());
    DataStore person = data.get(0);
    assertEquals(2, person.getRowCount());
    assertEquals(3, person.getColumnCount());
    assertEquals(1, person.getValueAsInt(0, 0, -1));
    assertEquals("Arthur", person.getValueAsString(0, "firstname"));
    assertEquals("Dent", person.getValueAsString(0, "lastname"));

    assertEquals(2, person.getValueAsInt(1, 0, -1));
    assertEquals("Zaphod", person.getValueAsString(1, 1));
    assertEquals("Beeblebrox", person.getValueAsString(1, "lastname"));
  }

  @Test
  public void testDeleteReturning()
    throws Exception
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    assertNotNull(con);

    String sql = "delete from person where id = 1 returning *;";

    StatementRunner runner = getTestUtil().createConnectedStatementRunner(con);

    StatementRunnerResult result = runner.runStatement(sql);

    assertTrue(result.getMessages().toString(), result.isSuccess());

    assertTrue(result.hasDataStores());
    List<DataStore> data = result.getDataStores();
    assertEquals(1, data.size());
    DataStore person = data.get(0);
    assertEquals(1, person.getRowCount());
    assertEquals(3, person.getColumnCount());
    assertEquals(1, person.getValueAsInt(0, 0, -1));
    assertEquals("Arthur", person.getValueAsString(0, "firstname"));
    assertEquals("Dent", person.getValueAsString(0, "lastname"));
  }

}
