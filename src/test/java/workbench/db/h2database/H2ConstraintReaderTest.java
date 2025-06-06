/*
 * H2ConstraintReaderTest.java
 *
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
package workbench.db.h2database;

import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.ConnectionMgr;
import workbench.db.ConstraintReader;
import workbench.db.ConstraintType;
import workbench.db.ReaderFactory;
import workbench.db.TableConstraint;
import workbench.db.TableDefinition;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import org.junit.AfterClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class H2ConstraintReaderTest
  extends WbTestCase
{

  public H2ConstraintReaderTest()
  {
    super("H2ConstraintReaderTest");
  }

  @AfterClass
  public static void tearDownClass()
    throws Exception
  {
    ConnectionMgr.getInstance().disconnectAll();
  }

  @Test
  public void testReader()
    throws Exception
  {
    TestUtil util = getTestUtil();
    WbConnection con = util.getConnection();

    String sql = "CREATE TABLE check_test (id integer, constraint positive_id check (id > 42));";
    TestUtil.executeScript(con, sql);

    TableDefinition tbl = con.getMetadata().getTableDefinition(new TableIdentifier("CHECK_TEST"));
    ConstraintReader reader = ReaderFactory.getConstraintReader(con.getMetadata());
    assertTrue(reader instanceof H2ConstraintReader);

    List<TableConstraint> cons = reader.getTableConstraints(con, tbl);
    assertNotNull(cons);
    assertEquals(1, cons.size());
    TableConstraint constraint = cons.get(0);
    assertEquals("POSITIVE_ID", constraint.getConstraintName());
    assertEquals("(\"ID\" > 42)", constraint.getExpression());
    assertEquals(ConstraintType.Check, constraint.getConstraintType());
    assertEquals("CONSTRAINT POSITIVE_ID CHECK (\"ID\" > 42)", constraint.getSql());
  }

}
