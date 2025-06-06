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
package workbench.db.mssql;

import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;
import workbench.interfaces.ObjectDropper;

import workbench.db.DbObjectFinder;
import workbench.db.GenericObjectDropper;
import workbench.db.IndexDefinition;
import workbench.db.MsSQLTest;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.sql.parser.ParserType;
import workbench.sql.parser.ScriptParser;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
@Category(MsSQLTest.class)
public class SqlServerDropTest
  extends WbTestCase
{

  public SqlServerDropTest()
  {
    super("SqlServerDropTest");
  }

  @Before
  public void setup()
    throws Exception
  {
    SQLServerTestUtil.initTestcase("SqlServerDropTest");
    WbConnection conn = SQLServerTestUtil.getSQLServerConnection();
    Assume.assumeNotNull("No connection available", conn);
    SQLServerTestUtil.dropAllObjects(conn);
    String sql =
      "create table foo \n" +
      "( \n" +
      "   id integer, \n" +
      "   some_data varchar(100) \n" +
      ");\n" +
      "create index idx_foo_1 on foo (id); \n"+
      "create index idx_foo_2 on foo (some_data); \n"  +
      "commit";
    TestUtil.executeScript(conn, sql);
  }

  @After
  public void cleanup()
    throws Exception
  {
    WbConnection conn = SQLServerTestUtil.getSQLServerConnection();
    Assume.assumeNotNull("No connection available", conn);
    SQLServerTestUtil.dropAllObjects(conn);
  }

  @Test
  public void testTableDrop()
    throws SQLException
  {
    WbConnection conn = SQLServerTestUtil.getSQLServerConnection();
    assertNotNull("No connection available", conn);

    TableIdentifier tbl = new DbObjectFinder(conn).findTable(new TableIdentifier("foo"));
    assertNotNull(tbl);
    ObjectDropper dropper = new GenericObjectDropper();
    dropper.setConnection(conn);
    dropper.setObjects(Collections.singletonList(tbl));

    CharSequence sql = dropper.getScript();
    ScriptParser p = new ScriptParser(sql.toString(), ParserType.SqlServer);
    assertEquals(2, p.getSize());
    String drop = p.getCommand(0);
//    System.out.println(drop);
    assertEquals("IF OBJECT_ID('wb_junit.dbo.foo', 'U') IS NOT NULL DROP TABLE wb_junit.dbo.foo", drop);
    drop = p.getCommand(1);
    assertEquals("COMMIT", drop);

    TestUtil.executeScript(conn, sql.toString());
    tbl = new DbObjectFinder(conn).findTable(new TableIdentifier("foo"));
    assertNull(tbl);
  }

  @Test
  public void testIndexDrop()
    throws SQLException
  {
    WbConnection conn = SQLServerTestUtil.getSQLServerConnection();
    assertNotNull("No connection available", conn);

    TableIdentifier tbl = new DbObjectFinder(conn).findTable(new TableIdentifier("foo"));
    assertNotNull(tbl);
    List<IndexDefinition> indexes = conn.getMetadata().getIndexReader().getTableIndexList(tbl, false);
    assertNotNull(indexes);

    assertEquals(2, indexes.size());
    ObjectDropper dropper = new GenericObjectDropper();
    dropper.setConnection(conn);
    dropper.setObjects(indexes);
    dropper.setObjectTable(tbl);
    CharSequence sql = dropper.getScript();

    ScriptParser p = new ScriptParser(sql.toString(), ParserType.SqlServer);
    assertEquals(3, p.getSize());
    String drop = p.getCommand(0);
    assertEquals("DROP INDEX idx_foo_1 ON wb_junit.dbo.foo", drop);
    drop = p.getCommand(1);
    assertEquals("DROP INDEX idx_foo_2 ON wb_junit.dbo.foo", drop);
    drop = p.getCommand(2);
    assertEquals("COMMIT", drop);
    
    TestUtil.executeScript(conn, sql.toString());
    indexes = conn.getMetadata().getIndexReader().getTableIndexList(tbl, false);
    assertTrue(indexes.isEmpty());
  }

}
