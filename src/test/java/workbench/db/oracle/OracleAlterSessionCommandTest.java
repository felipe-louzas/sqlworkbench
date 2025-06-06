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


import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.OracleTest;
import workbench.db.WbConnection;

import workbench.sql.StatementRunner;

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
public class OracleAlterSessionCommandTest
  extends WbTestCase
{

  public OracleAlterSessionCommandTest()
  {
    super("OracleAlterSessionTest");
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
  public void testExecute()
    throws Exception
  {
    WbConnection con = OracleTestUtil.getOracleConnection();
    assertNotNull(con);

    //String oldZone = TestUtil.getSingleQueryValue(con, "select SESSIONTIMEZONE from dual").toString();
    StatementRunner runner = new StatementRunner();
    runner.setConnection(con);

    runner.runStatement("alter session set time_zone = 'Canada/Saskatchewan';");
    String newZone = TestUtil.getSingleQueryValue(con, "select SESSIONTIMEZONE from dual").toString();
    assertEquals("Canada/Saskatchewan", newZone);
  }

}
