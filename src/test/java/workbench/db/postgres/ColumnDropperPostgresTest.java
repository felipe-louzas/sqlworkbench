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

import java.util.ArrayList;
import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.ColumnDropper;
import workbench.db.ColumnIdentifier;
import workbench.db.DbObjectFinder;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.sql.parser.ScriptParser;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

import workbench.db.PostgresDbTest;


/**
 *
 * @author Thomas Kellerer
 */
@Category(PostgresDbTest.class)
public class ColumnDropperPostgresTest
  extends WbTestCase
{
  private static final String TEST_ID = "columndropperpg";

  public ColumnDropperPostgresTest()
  {
    super(TEST_ID);
  }

  @BeforeClass
  public static void setUpClass()
    throws Exception
  {
    PostgresTestUtil.initTestCase(TEST_ID);
  }

  @AfterClass
  public static void tearDownClass()
    throws Exception
  {
    PostgresTestUtil.cleanUpTestCase();
  }

  @Test
  public void testDropObjects()
    throws Exception
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    assertNotNull(con);

    TestUtil.executeScript(con,
      "create table person (nr integer, firstname varchar(20), lastname varchar(20), dummy1 integer, dummy2 date);\n" +
      "commit;");

    TableIdentifier table = new DbObjectFinder(con).findTable(new TableIdentifier("person"));
    List<ColumnIdentifier> cols = new ArrayList<>();
    cols.add(new ColumnIdentifier("dummy1"));
    cols.add(new ColumnIdentifier("dummy2"));

    ColumnDropper dropper = new ColumnDropper(con, table, cols);
    String sql = dropper.getScript().toString();

    assertNotNull(sql);
//    System.out.println(sql);
    ScriptParser p = new ScriptParser(sql.trim());
    p.setReturnStartingWhitespace(false);
    assertEquals(3, p.getSize());

    assertEquals("ALTER TABLE "+ TEST_ID + ".person DROP COLUMN dummy1 CASCADE", p.getCommand(0).trim());
    assertEquals("ALTER TABLE "+ TEST_ID + ".person DROP COLUMN dummy2 CASCADE", p.getCommand(1).trim());
    assertEquals("COMMIT", p.getCommand(2).trim());

    dropper.dropObjects();

    List<ColumnIdentifier> tableCols = con.getMetadata().getTableColumns(table);
    assertEquals(3, tableCols.size());
    assertEquals("nr", tableCols.get(0).getColumnName());
    assertEquals("firstname", tableCols.get(1).getColumnName());
    assertEquals("lastname", tableCols.get(2).getColumnName());
  }
}
