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
import workbench.resource.Settings;

import workbench.db.OracleTest;
import workbench.db.WbConnection;

import workbench.storage.DataStore;

import workbench.sql.StatementHook;
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
public class AutotraceTest
  extends WbTestCase
{

  public AutotraceTest()
  {
    super("AutotraceTest");
  }

  @BeforeClass
  public static void setUp()
    throws Exception
  {
    OracleTestUtil.initTestCase();
    WbConnection con = OracleTestUtil.getOracleConnection();
    if (con == null) return;

    String sql =
      "create table some_table (id integer, some_data varchar(100));\n" +
      "insert into some_table values (1,'foo');\n" +
      "insert into some_table values (2,'bar');\n" +
      "commit;\n";
    TestUtil.executeScript(con, sql);
  }

  @AfterClass
  public static void tearDown()
    throws Exception
  {
    OracleTestUtil.cleanUpTestCase();
  }

  @Test
  public void testAutotrace()
    throws Exception
  {

    boolean oldFlag = Settings.getInstance().getBoolProperty("workbench.db.oracle.autotrace.statistics.valuefirst", true);

    try
    {

      WbConnection con = OracleTestUtil.getOracleConnection();
      assertNotNull(con);

      Settings.getInstance().setProperty("workbench.db.oracle.autotrace.statistics.valuefirst", true);

      StatementRunner runner = new StatementRunner();
      runner.setConnection(con);
      StatementHook hook = runner.getStatementHook();
      assertTrue(hook instanceof OracleStatementHook);

      StatementRunnerResult result = runner.runStatement("set autotrace on");
      assertTrue(result.isSuccess());
      String attr = runner.getSessionAttribute("autotrace");
      assertNotNull(attr);
      assertEquals("on", attr);

      result = runner.runStatement("select id, some_data from some_table order by id");
      List<DataStore> data = result.getDataStores();
      assertEquals(3, data.size());

      // result of the select
      DataStore table = data.get(0);
      assertEquals(2, table.getRowCount());
      assertEquals(1, table.getValueAsInt(0, 0, -1));
      assertEquals("foo", table.getValueAsString(0, 1));

      assertEquals("Statistics", data.get(1).getResultName());
      DataStore stat = data.get(1);

//      DataStorePrinter printer = new DataStorePrinter(stat);
//      printer.printTo(System.out);

      int rowIndex = findRow("rows processed", stat);
      assertTrue(rowIndex > -1);
      int rows = stat.getValueAsInt(rowIndex, 0, -1); // rows processed property
      assertEquals(2, rows);

      rowIndex = findRow("consistent gets", stat);
      assertTrue(rowIndex > -1);

      rowIndex = findRow("bytes sent via SQL*Net to client", stat);
      assertTrue(rowIndex > -1);

      rowIndex = findRow("db block gets", stat);
      assertTrue(rowIndex > -1);

      rowIndex = findRow("physical reads", stat);
      assertTrue(rowIndex > -1);

      assertEquals("Execution plan", data.get(2).getResultName());

      runner.runStatement("set autotrace traceonly");
      result = runner.runStatement("select id, some_data from some_table order by id");
      data = result.getDataStores();
      assertEquals(2, data.size());
      assertEquals("Statistics", data.get(0).getResultName());
      assertEquals("Execution plan", data.get(1).getResultName());

      runner.runStatement("set autotrace on statistics");
      result = runner.runStatement("--@wbresult tabledata\n select id, some_data from some_table order by id");
      data = result.getDataStores();
      assertEquals(2, data.size());
      assertEquals("tabledata", data.get(0).getResultName());
      assertEquals("Statistics", data.get(1).getResultName());
      stat = data.get(1);

      rowIndex = findRow("rows processed", stat);
      assertTrue(rowIndex > -1);
      rows = stat.getValueAsInt(rowIndex, 0, -1); // rows processed property
      assertEquals(2, rows);

      runner.runStatement("set autotrace traceonly statistics");
      result = runner.runStatement("--@wbresult tabledata\n select id, some_data from some_table order by id");
      data = result.getDataStores();
      assertEquals(1, data.size());
      assertEquals("Statistics", data.get(0).getResultName());
      stat = data.get(0);
      rowIndex = findRow("rows processed", stat);
      assertTrue(rowIndex > -1);
      rows = stat.getValueAsInt(rowIndex, 0, -1); // rows processed property
      assertEquals(2, rows);

      runner.runStatement("set autotrace on explain");
      result = runner.runStatement("--@wbresult tabledata\n select id, some_data from some_table order by id");
      data = result.getDataStores();
      assertEquals(2, data.size());
      assertEquals("tabledata", data.get(0).getResultName());
      assertEquals("Execution plan", data.get(1).getResultName());

      runner.runStatement("set autotrace traceonly statistics realplan");
      result = runner.runStatement("--@wbresult tabledata\n select id, some_data from some_table order by id");
      data = result.getDataStores();
      assertEquals(2, data.size());
      assertEquals("Statistics", data.get(0).getResultName());
      assertEquals("tabledata", data.get(1).getResultName());
      StringBuilder plan = new StringBuilder();
      for (int row=0; row < data.get(1).getRowCount(); row++)
      {
        plan.append(data.get(1).getValueAsString(row, 0));
        plan.append('\n');
      }
      assertTrue(plan.indexOf("A-Rows") > 0); // make sure the real plan was retrieved, not a "regular" explain plan
      assertTrue(plan.indexOf("A-Time") > 0); // make sure the real plan was retrieved, not a "regular" explain plan
      assertTrue(plan.indexOf("/*+ gather_plan_statistics */") > 0); // make sure the real plan was retrieved, not a "regular" explain plan
    }
    finally
    {
      Settings.getInstance().setProperty("workbench.db.oracle.autotrace.statistics.valuefirst", oldFlag);
    }
  }

  private int findRow(String property, DataStore ds)
  {
    for (int row=0; row < ds.getRowCount(); row ++)
    {
      String prop = ds.getValueAsString(row, 1);
      if (property.equals(prop)) return row;
    }
    return -1;
  }
}
