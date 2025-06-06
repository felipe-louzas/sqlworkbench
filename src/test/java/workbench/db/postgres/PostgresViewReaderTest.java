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

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.DbObjectFinder;
import workbench.db.PostgresDbTest;
import workbench.db.TableDefinition;
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
@Category(PostgresDbTest.class)
public class PostgresViewReaderTest
  extends WbTestCase
{

  private static final String TEST_SCHEMA = "viewreadertest";

  public PostgresViewReaderTest()
  {
    super("PostgresViewReaderTest");
  }

  @BeforeClass
  public static void setUpClass()
    throws Exception
  {
    PostgresTestUtil.initTestCase(TEST_SCHEMA);
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    if (con == null) return;

    TestUtil.executeScript(con,
      "create table some_table (id integer, some_data varchar(100));\n" +
      "commit;\n");
  }

  @AfterClass
  public static void tearDownClass()
    throws Exception
  {
    PostgresTestUtil.cleanUpTestCase();
  }

  @Test
  public void testRules()
    throws Exception
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    assertNotNull(con);

    TestUtil.executeScript(con,
      "create view v_view as select * from some_table;\n" +
      "create rule insert_view AS ON insert to v_view do instead insert into some_table values (new.id, new.some_data);\n" +
      "commit;\n");

    TableIdentifier view = new DbObjectFinder(con).findObject(new TableIdentifier(TEST_SCHEMA, "v_view"));
    TableDefinition def = con.getMetadata().getTableDefinition(view, false);
    assertNotNull(def);
    assertEquals(2, def.getColumnCount());
    String sql = con.getMetadata().getViewReader().getExtendedViewSource(view).toString();
    assertTrue(sql.contains("CREATE RULE insert_view AS\n    ON INSERT TO v_view DO"));
  }

  @Test
  public void testQuotedIdentifer()
    throws Exception
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    assertNotNull(con);

    TestUtil.executeScript(con,
      "create view \"View_Test\" as select * from some_table;\n" +
      "commit;\n");

    TableIdentifier tbl = new TableIdentifier(TEST_SCHEMA, "View_Test");
    tbl.setNeverAdjustCase(true);

    String sql = con.getMetadata().getViewReader().getViewSource(tbl).toString();
    assertTrue(sql.contains("FROM some_table"));
  }

  @Test
  public void testColumnDefaults()
    throws Exception
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    assertNotNull(con);

    TestUtil.executeScript(con,
      "create view v_def_test as select * from some_table;\n" +
      "alter table v_def_test alter id set default 42;\n" +
      "commit;\n");

    TableIdentifier tbl = new TableIdentifier(TEST_SCHEMA, "v_def_test");

    String sql = con.getMetadata().getViewReader().getExtendedViewSource(tbl).toString();
    assertTrue(sql.contains("FROM some_table"));
    assertTrue(sql.contains("SET DEFAULT 42"));
  }
}
