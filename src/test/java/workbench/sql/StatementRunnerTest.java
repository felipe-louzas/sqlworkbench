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

import java.sql.ResultSet;
import java.sql.Statement;

import workbench.TestUtil;
import workbench.WbTestCase;
import workbench.interfaces.ExecutionController;

import workbench.db.ConnectionMgr;
import workbench.db.DbObjectFinder;
import workbench.db.JdbcUtils;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.storage.DataStore;

import workbench.sql.commands.DdlCommand;
import workbench.sql.commands.UpdatingCommand;
import workbench.sql.wbcommands.WbCopy;
import workbench.sql.wbcommands.WbDefineVar;
import workbench.sql.wbcommands.WbFeedback;
import workbench.sql.wbcommands.WbInclude;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class StatementRunnerTest
  extends WbTestCase
{
  private TestUtil util;
  private boolean confirmExecution = false;
  private boolean controllerCalled = false;

  public StatementRunnerTest()
  {
    super("StatementRunnerTest");
    util = getTestUtil();
  }

  @Test
  public void testReadOnly()
    throws Exception
  {
    try
    {
      util.prepareEnvironment();

      WbConnection con = util.getConnection("readOnlyTest");
      StatementRunner runner = new StatementRunner();
      runner.setBaseDir(util.getBaseDir());
      runner.setConnection(con);
      con.getProfile().setReadOnly(false);

      StatementRunnerResult result = null;

      result = runner.runStatement("create table read_only_test (id integer, data varchar(100))");
      assertTrue(result.isSuccess());

      result = runner.runStatement("insert into read_only_test (id, data) values (1, 'test')");
      assertTrue(result.isSuccess());

      result = runner.runStatement("commit");
      assertTrue(result.isSuccess());

      boolean exists = new DbObjectFinder(con).tableExists(new TableIdentifier("read_only_test"));
      assertTrue(exists);

      con.getProfile().setReadOnly(true);
      result = runner.runStatement("insert into read_only_test (id, data) values (2, 'test')");
      assertTrue(result.hasWarning());

      result = runner.runStatement("commit");
      assertTrue(result.hasWarning());

      Statement stmt = con.createStatement();
      ResultSet rs = stmt.executeQuery("select count(*) from read_only_test");
      int count = -1;
      if (rs.next())
      {
        count = rs.getInt(1);
      }
      JdbcUtils.closeResult(rs);
      assertEquals(1, count);

      ExecutionController controller = new ExecutionController()
      {
        @Override
        public boolean confirmStatementExecution(String command)
        {
          controllerCalled = true;
          return confirmExecution;
        }

        @Override
        public boolean confirmExecution(String command, String yes, String no)
        {
          controllerCalled = true;
          return confirmExecution;
        }

        @Override
        public String getPassword(String title, String prompt)
        {
          return null;
        }

        @Override
        public String getInput(String prompt)
        {
          return null;
        }
      };

      con.getProfile().setReadOnly(false);
      con.getProfile().setConfirmUpdates(true);
      runner.setExecutionController(controller);

      controllerCalled = false;
      result = runner.runStatement("select count(*) from read_only_test");
      assertFalse(controllerCalled);
      assertTrue(result.isSuccess());
      assertTrue(result.hasDataStores());
      DataStore ds = result.getDataStores().get(0);
      assertEquals(1, ds.getRowCount());

      controllerCalled = false;
      confirmExecution = true;
      result = runner.runStatement("insert into read_only_test (id, data) values (2, 'test')");
      assertTrue(controllerCalled);
      assertTrue(result.isSuccess());

      controllerCalled = false;
      confirmExecution = false;
      result = runner.runStatement("insert into read_only_test (id, data) values (3, 'test')");
      assertTrue(controllerCalled);
      assertTrue(result.hasWarning());

      rs = stmt.executeQuery("select count(*) from read_only_test");
      count = -1;
      if (rs.next())
      {
        count = rs.getInt(1);
      }
      JdbcUtils.closeResult(rs);
      assertEquals(2, count);

      JdbcUtils.closeStatement(stmt);
    }
    finally
    {
      ConnectionMgr.getInstance().disconnectAll();
    }
  }

  @Test
  public void testWbCommands()
    throws Exception
  {
    try
    {
      util.prepareEnvironment();
      StatementRunner runner = util.createConnectedStatementRunner();

      runner.setVerboseLogging(true);

      String sql = "--comment\n\nwbfeedback off";
      SqlCommand command = runner.getCommandToUse(sql);
      assertTrue(command instanceof WbFeedback);
      runner.runStatement(sql);

      boolean verbose = runner.getVerboseLogging();
      assertEquals("Feedback not executed", false, verbose);
    }
    finally
    {
      ConnectionMgr.getInstance().disconnectAll();
    }
  }

  @Test
  public void testCommands()
    throws Exception
  {
    String sql = "\n\ninsert into bla (col) values (1)";
    StatementRunner runner = new StatementRunner();
    SqlCommand command = runner.getCommandToUse(sql);
    assertEquals(command.getVerb(), "INSERT");
    assertEquals(true, command.isUpdatingCommand());
    assertTrue(command instanceof UpdatingCommand);

    sql = "--do something\nupdate bla set col = value";
    command = runner.getCommandToUse(sql);
    assertEquals(command.getVerb(), "UPDATE");
    assertTrue(command instanceof UpdatingCommand);
    assertEquals(true, command.isUpdatingCommand());

    sql = "  delete from bla";
    command = runner.getCommandToUse(sql);
    assertEquals(command.getVerb(), "DELETE");
    assertEquals(true, command.isUpdatingCommand());
    assertTrue(command instanceof UpdatingCommand);

    sql = "  create table bla (col integer);";
    command = runner.getCommandToUse(sql);
    assertEquals(command.getVerb(), "CREATE");
    assertEquals(true, command.isUpdatingCommand());
    assertTrue(command instanceof DdlCommand);

    sql = "-- comment\n\n\ncreate view bla as select * from blub;";
    command = runner.getCommandToUse(sql);
    assertEquals(command.getVerb(), "CREATE");
    assertTrue(command instanceof DdlCommand);

    sql = "-- comment\n\n\ncreate \nor \nreplace \nview bla as select * from blub;";
    command = runner.getCommandToUse(sql);
    assertEquals(command.getVerb(), "CREATE");
    assertTrue(command instanceof DdlCommand);

    sql = "-- comment\n\n\ncreate trigger bla;";
    command = runner.getCommandToUse(sql);
    assertEquals(command.getVerb(), "CREATE");

    sql = "  drop table bla (col integer);";
    command = runner.getCommandToUse(sql);
    assertEquals(command.getVerb(), "DROP");
    assertTrue(command instanceof DdlCommand);
    assertEquals(true, command.isUpdatingCommand());

    sql = "/* this is \n a comment \n*/\n-- comment\nalter table bla drop constraint xyz;";
    command = runner.getCommandToUse(sql);
    assertEquals(command.getVerb(), "ALTER");
    assertTrue(command instanceof DdlCommand);
    assertEquals(true, command.isUpdatingCommand());

    boolean isDrop = ((DdlCommand)command).isDropCommand(sql);
    assertEquals(true, isDrop);

    sql = "-- bla\nwbvardef x=42;";
    command = runner.getCommandToUse(sql);
    assertTrue(command instanceof WbDefineVar);
    assertEquals(false, command.isUpdatingCommand());

    sql = "   -- comment\nwbcopy -sourceprofile=x";
    command = runner.getCommandToUse(sql);
    assertTrue(command instanceof WbCopy);

    sql = "@file.sql";
    command = runner.getCommandToUse(sql);
    assertTrue(command instanceof WbInclude);

    sql = "-- run the second script\n/* bla blub */\nwbinclude -file=file.sql";
    command = runner.getCommandToUse(sql);
    assertTrue(command instanceof WbInclude);
  }
}
