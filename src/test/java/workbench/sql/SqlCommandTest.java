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
package workbench.sql;
import java.util.List;
import org.junit.AfterClass;

import org.junit.Test;
import workbench.WbTestCase;
import static org.junit.Assert.*;import org.junit.BeforeClass;
import workbench.TestUtil;
import workbench.db.WbConnection;
import workbench.storage.DataStore;


/**
 *
 * @author Thomas Kellerer
 */
public class SqlCommandTest
  extends WbTestCase
{

  public SqlCommandTest()
  {
    super("SqlCommandTest");
  }

  @BeforeClass
  public static void setUpClass()
    throws Exception
  {
  }

  @AfterClass
  public static void tearDownClass()
    throws Exception
  {
  }

  @Test
  public void testExecute()
    throws Exception
  {
    TestUtil util = getTestUtil();

    StatementRunner runner = util.createConnectedStatementRunner();
    WbConnection con = runner.getConnection();

    TestUtil.executeScript(con,
      "create table person (id integer, firstname varchar(100), lastname varchar(100));\n" +
      "insert into person (id, firstname, lastname) values (1, 'Arthur', 'Dent');\n" +
      "insert into person (id, firstname, lastname) values (2, 'Zaphod', 'Beeblebrox');\n" +
      "commit;\n"
    );

    String sql = "select * from person order by id;";
    SqlCommand command = new SqlCommand();
    command.setConnection(con);
    command.setStatementRunner(runner);

    StatementRunnerResult result = command.execute(sql);

    assertTrue(result.isSuccess());
    assertTrue(result.hasDataStores());
    List<DataStore> data = result.getDataStores();
    assertEquals(1, data.size());
    DataStore person = data.get(0);
    assertEquals(2, person.getRowCount());
    assertEquals(3, person.getColumnCount());
    assertEquals(1, person.getValueAsInt(0, 0, -1));
    assertEquals("Arthur", person.getValueAsString(0, "firstname"));
    assertEquals("Dent", person.getValueAsString(0, "lastname"));

    assertEquals(2, person.getValueAsInt(1, 0, -1));
    assertEquals("Zaphod", person.getValueAsString(1, 1));
    assertEquals("Beeblebrox", person.getValueAsString(1, "lastname"));
  }

  @Test
  public void testCheckUpdatingCommand()
    throws Exception
  {
    TestUtil util = getTestUtil();

    StatementRunner runner = util.createConnectedStatementRunner();
    WbConnection con = runner.getConnection();

    SqlCommand command = new SqlCommand();
    command.setConnection(con);
    command.setStatementRunner(runner);

    String cte1 =
      "with old_data as (\n" +
      "  delete from orders where id < 100 \n" +
      "  returning * \n" +
      ") \n" +
      "insert into archive select * from old_data";
    assertTrue(command.isUpdatingCommand(con, cte1));
    String cte2 = "with old_data as (\n" +
      "  select * from orders where id < 100 \n" +
      ") \n" +
      "select * from old_data";
    assertFalse(command.isUpdatingCommand(con, cte2));

    String cte3 =
      "with rec as (\n" +
      "    select id from mytable\n" +
      "    where code = 'XXX'\n" +
      ")\n" +
      "update mytable\n" +
      "set status = 2\n" +
      "from mytable inner join rec on rec.id = mytable.id;";
    assertTrue(command.isUpdatingCommand(con, cte3));
  }
}
