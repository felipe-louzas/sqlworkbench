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

import workbench.db.ColumnIdentifier;
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
public class PostgresColumnEnhancerTest
{
  public PostgresColumnEnhancerTest()
  {
  }

  @BeforeClass
  public static void setUpClass()
    throws Exception
  {
    PostgresTestUtil.initTestCase(null);
  }

  @AfterClass
  public static void tearDownClass()
    throws Exception
  {
    PostgresTestUtil.cleanUpTestCase();
  }

  @Test
  public void testCaseSensitiveColumnNames()
    throws Exception
  {
    WbConnection conn = PostgresTestUtil.getPostgresConnection();
    assertNotNull(conn);
    String sql =
      "create table \"SomeTable\" (\"SomeTableID\" integer not null generated always as identity);\n"+
      "commit;\n";
    TestUtil.executeScript(conn, sql);
    TableDefinition tbl = conn.getMetadata().getTableDefinition(new TableIdentifier("\"SomeTable\""));
    assertNotNull(tbl);
    assertTrue(tbl.getColumns().get(0).isIdentityColumn());
    assertTrue(tbl.getColumns().get(0).getGenerationExpression().contains("ALWAYS AS IDENTITY"));
  }

  @Test
  public void testUpdateColumnDefinition()
    throws Exception
  {
    WbConnection conn = PostgresTestUtil.getPostgresConnection();
    assertNotNull(conn);

    String sql =
      "create table foo (" +
      "   pk_value integer not null, " +
      "   id1 integer[], " +
      "   id2 integer[][], " +
      "   id3 integer[][][], " +
      "   foo text[], " +
      "   bar1 varchar, " +
      "   bar2 varchar[], " +
      "   bar3 varchar(20)[], " +
      "   nr numeric(4,2)[]" +
      ");\n" +
      "commit;\n";
    TestUtil.executeScript(conn, sql);
    TableDefinition tbl = conn.getMetadata().getTableDefinition(new TableIdentifier("foo"));
    List<ColumnIdentifier> cols = tbl.getColumns();
    for (ColumnIdentifier col : cols)
    {
      if (col.getColumnName().equals("id1"))
      {
        assertEquals("integer[]", col.getDbmsType());
      }
      if (col.getColumnName().equals("id2"))
      {
        assertEquals("integer[][]", col.getDbmsType());
      }
      if (col.getColumnName().equals("id3"))
      {
        assertEquals("integer[][][]", col.getDbmsType());
      }
      if (col.getColumnName().equals("foo"))
      {
        assertEquals("text[]", col.getDbmsType());
      }
      if (col.getColumnName().equals("pk_value"))
      {
        assertEquals("integer", col.getDbmsType());
      }
      if (col.getColumnName().equals("bar1"))
      {
        assertEquals("varchar", col.getDbmsType());
      }
      if (col.getColumnName().equals("bar2"))
      {
        assertEquals("varchar[]", col.getDbmsType());
      }
      if (col.getColumnName().equals("bar3"))
      {
        assertEquals("varchar(20)[]", col.getDbmsType());
      }
      if (col.getColumnName().equals("nr"))
      {
        assertEquals("numeric(4,2)[]", col.getDbmsType());
      }

    }
  }

}
