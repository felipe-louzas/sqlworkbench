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
package workbench.sql.wbcommands;

import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;
import workbench.console.DataStorePrinter;

import workbench.db.ConnectionMgr;
import workbench.db.IndexDefinition;

import workbench.storage.DataStore;

import workbench.sql.StatementRunner;
import workbench.sql.StatementRunnerResult;

import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class WbDescribeObjectTest
  extends WbTestCase
{

  public WbDescribeObjectTest()
  {
    super("WbDescribeObjectTest");
  }

  @After
  public void tearDown()
    throws Exception
  {
    ConnectionMgr.getInstance().disconnectAll();
  }

  @Test
  public void testExecute()
    throws Exception
  {
    TestUtil util = getTestUtil();
    util.prepareEnvironment();
    StatementRunner runner = util.createConnectedStatementRunner();
    String sql = "create table describe_test (nr integer, info_text varchar(100));";
    StatementRunnerResult result = runner.runStatement(sql);
    assertEquals("Could not create table", true, result.isSuccess());

    sql = "-- show table definition\ndesc describe_test;";
    result = runner.runStatement(sql);
    assertEquals("Describe failed", true, result.isSuccess());

    List<DataStore> data = result.getDataStores();
    assertNotNull("No description returned", data);
    assertEquals("No data returned", 1, data.size());
    assertEquals("Wrong number of rows returned", 2, data.get(0).getRowCount());

    sql = "-- show table definition\ndescribe \"DESCRIBE_TEST\"\n-- for table;";
    result = runner.runStatement(sql);
    assertEquals("Describe failed", true, result.isSuccess());

    data = result.getDataStores();
    assertNotNull("No description returned", data);
    assertEquals("No data returned", 1, data.size());
    assertEquals("Wrong number of rows returned", 2, data.get(0).getRowCount());

    runner.runStatement("create index idx_nr on describe_test (nr);");

    sql = "describe describe_test;";
    result = runner.runStatement(sql);
    assertEquals("Describe failed", true, result.isSuccess());

    data = result.getDataStores();
    assertNotNull(data);
    assertEquals("Not enough returned", 2, data.size());
    DataStore indexDs = data.get(1);
    DataStorePrinter p = new DataStorePrinter(indexDs);

    assertEquals(1, indexDs.getRowCount());
    assertEquals("IDX_NR", indexDs.getValue(0, "INDEX_NAME"));
    assertEquals("NO", indexDs.getValue(0, "UNIQUE"));
    Object o = indexDs.getRow(0).getUserObject();
    assertTrue(o instanceof IndexDefinition);
    IndexDefinition idx = (IndexDefinition)o;
    String def = indexDs.getValueAsString(0, "DEFINITION");
    assertEquals("NR ASC", def);
    assertEquals(def, idx.getExpression());
  }
}
