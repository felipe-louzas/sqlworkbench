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
package workbench.db.postgres;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.DbObjectFinder;
import workbench.db.PostgresDbTest;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
@Category(PostgresDbTest.class)
public class PostgresSequenceAdjusterTest
  extends WbTestCase
{
  private static final String TEST_SCHEMA = "sync_test";
  public PostgresSequenceAdjusterTest()
  {
    super("SequenceSync");
  }

  @Before
  public void beforeTest()
    throws Exception
  {
    PostgresTestUtil.initTestCase(TEST_SCHEMA);
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    if (con == null) return;

    TestUtil.executeScript(con,
      "CREATE table table_one (id serial not null);\n" +
      "CREATE schema \"other schema\";" +
      "create table \"other schema\".\"table two\" (id serial not null);\n" +
      "COMMIT; \n");
  }

  @After
  public void afterTest()
    throws Exception
  {
    PostgresTestUtil.cleanUpTestCase();
  }

  @Test
  public void testSyncSequences()
    throws Exception
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    assertNotNull(con);

    TestUtil.executeScript(con,
      "insert into table_one (id) values (1), (2), (7), (41);\n" +
      "insert into \"other schema\".\"table two\" (id) values (1), (2), (7), (41);\n" +
      "commit;" );

    DbObjectFinder finder = new DbObjectFinder(con);
    TableIdentifier tbl = finder.findTable(new TableIdentifier("table_one"));

    PostgresSequenceAdjuster sync = new PostgresSequenceAdjuster();
    sync.adjustTableSequences(con, tbl, true);
    Number value = (Number)TestUtil.getSingleQueryValue(con, "select nextval(pg_get_serial_sequence('sync_test.table_one', 'id'))");
    assertEquals(42, value.intValue());

    TableIdentifier other = finder.findTable(new TableIdentifier("\"other schema\"", "\"table two\""));
    sync.adjustTableSequences(con, other, true);
    value = (Number)TestUtil.getSingleQueryValue(con, "select nextval(pg_get_serial_sequence('\"other schema\".\"table two\"', 'id'))");
    assertEquals(42, value.intValue());
  }

}
