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
package workbench.db.oracle;

import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.OracleTest;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.sql.wbcommands.SourceTableArgument;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
@Category(OracleTest.class)
public class OracleSourceTableArgumentTest
  extends WbTestCase
{

  public OracleSourceTableArgumentTest()
  {
    super("OracleSourceTableArgumentTest");
  }

  @BeforeClass
  public static void setUpClass()
    throws Exception
  {
    OracleTestUtil.initTestCase();
  }

  @AfterClass
  public static void tearDownClass()
    throws Exception
  {
    OracleTestUtil.cleanUpTestCase();
  }

  @Test
  public void testGetTables()
    throws Exception
  {
    WbConnection con = OracleTestUtil.getOracleConnection();
    assertNotNull("Oracle not available", con);

    String script =
      "create table first_table (id integer);\n" +
      "create table second_table (id integer);\n" +
      "commit;\n";

    TestUtil.executeScript(con, script);

    String schema = con.getCurrentSchema();
    String tableNames = null;
    String excludedNames = null;
    SourceTableArgument parser = new SourceTableArgument(tableNames, excludedNames, schema, con);
    List<TableIdentifier> tables = parser.getTables();
    assertEquals("Wrong number of table", 2, tables.size());
    assertEquals(0, parser.getMissingTables().size());
  }

}
