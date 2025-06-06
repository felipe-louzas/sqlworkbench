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
package workbench.db.mssql;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.DbObjectFinder;
import workbench.db.DummyInsert;
import workbench.db.DummyUpdate;
import workbench.db.MsSQLTest;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
@Category(MsSQLTest.class)
public class SqlServerDummyDMLTest
  extends WbTestCase
{

  public SqlServerDummyDMLTest()
  {
    super("SqlServerDummyDMLTest");
  }

  @BeforeClass
  public static void setup()
    throws Exception
  {
    WbConnection conn = SQLServerTestUtil.getSQLServerConnection();
    Assume.assumeNotNull("No connection available", conn);
    TestUtil.executeScript(conn,
      "create table one (id integer not null primary key, some_data varchar(20), is_active bit not null);");
  }

  @AfterClass
  public static void tearDown()
    throws Exception
  {
    WbConnection conn = SQLServerTestUtil.getSQLServerConnection();
    Assume.assumeNotNull("No connection available", conn);
    SQLServerTestUtil.dropAllObjects(conn);
  }

  @Test
  public void testDummyInsert()
    throws Exception
  {
    WbConnection con = SQLServerTestUtil.getSQLServerConnection();
    assertNotNull(con);

    TableIdentifier tbl = new DbObjectFinder(con).findTable(new TableIdentifier("one"));
    DummyInsert insert = new DummyInsert(tbl);
    insert.setDoFormatSql(false);
    assertEquals("INSERT", insert.getObjectType());

    String sql = insert.getSource(con).toString();

    String expected =
      "INSERT INTO dbo.one\n" +
      "  (id, some_data, is_active)\n" +
      "VALUES\n" +
      "  (id_value, 'some_data_value', is_active_value);";
//    System.out.println(sql + "\n----\n" + expected);
    assertEquals(expected, sql);
  }

  @Test
  public void testDummyUpdate()
    throws Exception
  {
    WbConnection con = SQLServerTestUtil.getSQLServerConnection();
    assertNotNull(con);

    TableIdentifier tbl = new DbObjectFinder(con).findTable(new TableIdentifier("one"));
    DummyUpdate update = new DummyUpdate(tbl);
    update.setDoFormatSql(false);
    assertEquals("UPDATE", update.getObjectType());

    String sql = update.getSource(con).toString();

    String expected =
      "UPDATE dbo.one\n" +
      "   SET some_data = 'some_data_value',\n" +
      "       is_active = is_active_value\n" +
      "WHERE id = id_value;";
//    System.out.println(sql + "\n----\n" + expected);
    assertEquals(expected, sql);
  }

}
