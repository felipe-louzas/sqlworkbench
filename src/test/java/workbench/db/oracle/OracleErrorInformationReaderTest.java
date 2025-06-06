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

import workbench.sql.DelimiterDefinition;
import workbench.sql.ErrorDescriptor;

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
public class OracleErrorInformationReaderTest
  extends WbTestCase
{
  public OracleErrorInformationReaderTest()
  {
    super("OracleErrorInformationReaderTest");
  }

  @BeforeClass
  public static void setUp()
    throws Exception
  {
    OracleTestUtil.initTestCase();
  }

  @AfterClass
  public static void tearDown()
    throws Exception
  {
    OracleTestUtil.cleanUpTestCase();
  }

  @Test
  public void testGetErrorInfo()
    throws Exception
  {
    WbConnection con = OracleTestUtil.getOracleConnection();
    assertNotNull(con);

    String sql =
      "create procedure nocando\n" +
      "as \n" +
      "begin \n" +
      "   null; \n" +
      "ende;\n/\n";
    TestUtil.executeScript(con, sql, DelimiterDefinition.DEFAULT_ORA_DELIMITER);

    try
    {
      con.setBusy(true);
      OracleErrorInformationReader reader = new OracleErrorInformationReader(con);
      ErrorDescriptor errorInfo = reader.getErrorInfo(null, null, "nocando", "procedure", true);
      con.setBusy(false);
      assertNotNull(errorInfo);

      assertTrue(errorInfo.getErrorMessage().startsWith("Errors for PROCEDURE NOCANDO"));
      assertTrue(errorInfo.getErrorMessage().contains("PLS-00103"));
      assertEquals(4, errorInfo.getErrorLine());
      assertEquals(4, errorInfo.getErrorColumn());

      errorInfo = reader.getErrorInfo(null, null, "nocando", "procedure", false);
      assertNotNull(errorInfo);
      String msg = errorInfo.getErrorMessage();
      assertFalse(msg.contains("Errors for PROCEDURE NOCANDO"));
      assertTrue(msg.startsWith("L:5"));
    }
    finally
    {
      con.setBusy(false);
    }
  }

}
