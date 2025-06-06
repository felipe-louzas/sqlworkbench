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

import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;
import workbench.interfaces.StatementParameterPrompter;

import workbench.db.IbmDb2Test;
import workbench.db.WbConnection;

import workbench.storage.DataStore;

import workbench.sql.DelimiterDefinition;
import workbench.sql.StatementRunner;
import workbench.sql.StatementRunnerResult;
import workbench.sql.preparedstatement.StatementParameters;
import workbench.sql.wbcommands.WbCall;

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
public class WbCallDb2Test
  extends WbTestCase
{
  private static final String TEST_ID = "wbcalltest";

  public WbCallDb2Test()
  {
    super(TEST_ID);
  }

  @BeforeClass
  public static void setUp()
    throws Exception
  {
    Db2TestUtil.initTestCase();

    WbConnection con = Db2TestUtil.getDb2Connection();
    assertNotNull(con);

    TestUtil.executeScript(con,
      "create or replace procedure " + Db2TestUtil.getSchemaName() + ".some_proc(inout id integer, out some_text varchar(1000), out some_more_text varchar(2000)) \n" +
      " language SQL \n" +
      "begin  \n" +
      "  set some_text = 'foo'; \n" +
      "  set some_more_text = 'bar'; \n" +
      "  set id = id * 2; \n" +
      "end \n" +
      "/\n", DelimiterDefinition.DEFAULT_ORA_DELIMITER );
  }

  @AfterClass
  public static void tearDown()
    throws Exception
  {
    Db2TestUtil.cleanUpTestCase();
  }

  @Test
  public void testInOutParameter()
    throws Exception
  {
    WbConnection con = Db2TestUtil.getDb2Connection();
    assertNotNull(con);

    WbCall call = new WbCall();
    StatementRunner runner = new StatementRunner();
    runner.setConnection(con);
    call.setStatementRunner(runner);
    call.setConnection(con);
    StatementParameterPrompter prompter = new StatementParameterPrompter()
    {
      @Override
      public boolean showParameterDialog(StatementParameters parms, boolean showNames)
      {
        parms.setParameterValue(0, "21");
        return true;
      }
    };
    call.setParameterPrompter(prompter);
    String cmd = "WbCall " + Db2TestUtil.getSchemaName() + ".some_proc(?,?,?)";
    StatementRunnerResult result = call.execute(cmd);
    assertTrue(result.isSuccess());
    List<DataStore> data = result.getDataStores();
    assertNotNull(data);
    assertEquals(1, data.size());
    DataStore store = data.get(0);
    assertNotNull(store);
//    DataStorePrinter printer = new DataStorePrinter(store);
//    printer.printTo(System.out);
    assertEquals(3, store.getRowCount());
    assertEquals(42, store.getValueAsInt(0, 1, -1));
    assertEquals("foo", store.getValueAsString(1, 1));
    assertEquals("bar", store.getValueAsString(2, 1));
  }


}
