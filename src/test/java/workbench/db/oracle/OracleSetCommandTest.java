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

import workbench.WbTestCase;

import workbench.db.OracleTest;
import workbench.db.WbConnection;

import workbench.sql.StatementRunner;
import workbench.sql.StatementRunnerResult;

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
public class OracleSetCommandTest
  extends WbTestCase
{

  public OracleSetCommandTest()
  {
    super("OracleSetCommandTest");
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
    assertNotNull("Oracle not available", con);

    StatementRunner runner = new StatementRunner();
    runner.setConnection(con);
    StatementRunnerResult result = runner.runStatement("set serveroutput on");

    assertTrue(result.isSuccess());

    result = runner.runStatement(
      "begin \n" +
      "  dbms_output.put_line('Hello, World'); \n" +
      "end;\n");

    String msg = result.getMessages().toString();
    assertTrue(msg.contains("Hello, World"));
  }

}
