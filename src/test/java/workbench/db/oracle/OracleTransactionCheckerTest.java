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

import workbench.db.ConnectionMgr;
import workbench.db.DefaultTransactionChecker;
import workbench.db.OracleTest;
import workbench.db.TransactionChecker;
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
@Category(OracleTest.class)
public class OracleTransactionCheckerTest
  extends WbTestCase
{
  public OracleTransactionCheckerTest()
  {
    super("OracleTransactionCheckerTest");
  }

  @BeforeClass
  public static void setUpClass()
    throws Exception
  {
    OracleTestUtil.initTestCase();
    WbConnection con = OracleTestUtil.getOracleConnection();
    if (con == null) return;
    TestUtil.executeScript(con, "create table t (id integer);");
  }

  @AfterClass
  public static void tearDownClass()
    throws Exception
  {
    OracleTestUtil.cleanUpTestCase();
  }

  @Test
  public void testChecker()
    throws Exception
  {
    WbConnection con = OracleTestUtil.getOracleConnection();
    assertNotNull("Oracle not available", con);

    try
    {
      con.getProfile().setDetectOpenTransaction(true);
      TransactionChecker checker = con.getTransactionChecker();
      assertFalse(checker == TransactionChecker.NO_CHECK);
      assertTrue(checker instanceof DefaultTransactionChecker);

      assertFalse(checker.hasUncommittedChanges(con));

      TestUtil.executeScript(con, "insert into t values (42);");
      assertTrue(checker.hasUncommittedChanges(con));

      TestUtil.executeScript(con, "commit;");
      assertFalse(checker.hasUncommittedChanges(con));
    }
    finally
    {
      ConnectionMgr.getInstance().disconnectAll();
    }
  }


}
