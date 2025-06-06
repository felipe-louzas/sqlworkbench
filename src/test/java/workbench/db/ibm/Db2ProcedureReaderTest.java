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
package workbench.db.ibm;

import java.sql.SQLException;
import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.IbmDb2Test;
import workbench.db.ProcedureDefinition;
import workbench.db.ProcedureReader;
import workbench.db.WbConnection;

import workbench.sql.DelimiterDefinition;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
@Category(IbmDb2Test.class)
public class Db2ProcedureReaderTest
  extends WbTestCase
{
  public Db2ProcedureReaderTest()
  {
    super("Db2ProcedureReaderTest");
  }

  @BeforeClass
  public static void setUpClass()
    throws Exception
  {
    Db2TestUtil.initTestCase();
    WbConnection con = Db2TestUtil.getDb2Connection();
    if (con == null) return;

    String sql = "create or replace procedure " + Db2TestUtil.getSchemaName() + ".wb_test () \n" +
             "language SQL \n" +
             "begin \n" +
             "end \n" +
             "/\n" +
             "commit\n" +
             "/\n";
    TestUtil.executeScript(con, sql, DelimiterDefinition.DEFAULT_ORA_DELIMITER);
  }

  @AfterClass
  public static void tearDownClass()
    throws Exception
  {
    WbConnection con = Db2TestUtil.getDb2Connection();
    if (con == null) return;

    String sql =
      "drop procedure " + Db2TestUtil.getSchemaName() + ".wb_test; \n" +
      "commit;\n";
    TestUtil.executeScript(con, sql);
    Db2TestUtil.cleanUpTestCase();
  }

  @Test
  public void testGetProcedures()
    throws SQLException
  {
    WbConnection con = Db2TestUtil.getDb2Connection();
    if (con == null) fail("No connection available");

    ProcedureReader reader = con.getMetadata().getProcedureReader();
    assertTrue(reader instanceof Db2ProcedureReader);

    List<ProcedureDefinition> procs = reader.getProcedureList(null, Db2TestUtil.getSchemaName(), null);

    con.getProfile().setAlternateDelimiter(DelimiterDefinition.DEFAULT_ORA_DELIMITER);
    assertNotNull(procs);
    assertEquals(1, procs.size());
    CharSequence source = procs.get(0).getSource(con);
    String expected =
      "create or replace procedure " + Db2TestUtil.getSchemaName() + ".wb_test () \n" +
      "language SQL \n" +
      "begin \n" +
      "end\n" +
      "/";
    assertEquals(expected, source.toString().trim());
  }
}
