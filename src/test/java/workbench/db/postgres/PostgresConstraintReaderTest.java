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

import java.util.Collections;
import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.ConstraintReader;
import workbench.db.ConstraintType;
import workbench.db.JdbcUtils;
import workbench.db.PostgresDbTest;
import workbench.db.ReaderFactory;
import workbench.db.TableConstraint;
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
public class PostgresConstraintReaderTest
  extends WbTestCase
{
  private static final String TEST_ID = "pgconstrainttest";

  public PostgresConstraintReaderTest()
  {
    super("PostgresConstraintReader");
  }

  @BeforeClass
  public static void setUp()
    throws Exception
  {
    PostgresTestUtil.initTestCase(TEST_ID);
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    if (con == null) return;
    if (!JdbcUtils.hasMinimumServerVersion(con, "9.0")) return;

    String sql =
      "CREATE TABLE check_test " +
      "(\n" +
      "   id integer, \n" +
      "   constraint aaa_check_id check (id > 42), \n" +
      "   constraint bbb_exclusion exclude (id WITH = )\n" +
      ");\n"+
      "commit;\n";
    TestUtil.executeScript(con, sql);
  }

  @AfterClass
  public static void tearDown()
    throws Exception
  {
    PostgresTestUtil.cleanUpTestCase();
  }

  @Test
  public void testGetConstraints()
    throws Exception
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    assertNotNull(con);
    if (!JdbcUtils.hasMinimumServerVersion(con, "9.0")) return;

    TableDefinition tbl = con.getMetadata().getTableDefinition(new TableIdentifier("check_test"));
    ConstraintReader reader = ReaderFactory.getConstraintReader(con.getMetadata());
    List<TableConstraint> cons = reader.getTableConstraints(con, tbl);
    assertNotNull(cons);
    assertEquals(2, cons.size());

    Collections.sort(cons);

    TableConstraint check = cons.get(0);
    assertEquals("aaa_check_id", check.getConstraintName());
    assertEquals("CHECK ((id > 42))", check.getExpression());
    assertEquals(ConstraintType.Check, check.getConstraintType());
    assertEquals("CONSTRAINT aaa_check_id CHECK ((id > 42))", check.getSql());

    TableConstraint exclusion = cons.get(1);
    assertEquals("bbb_exclusion", exclusion.getConstraintName());
    assertEquals("EXCLUDE USING btree (id WITH =)", exclusion.getExpression());
    assertEquals(ConstraintType.Exclusion, exclusion.getConstraintType());
    assertEquals("CONSTRAINT bbb_exclusion EXCLUDE USING btree (id WITH =)", exclusion.getSql());

  }

}
