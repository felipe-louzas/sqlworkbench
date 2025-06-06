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
package workbench.db.mssql;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;
import workbench.resource.Settings;

import workbench.db.DbObjectFinder;
import workbench.db.JdbcUtils;
import workbench.db.MsSQLTest;
import workbench.db.TableCommentReader;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
@Category(MsSQLTest.class)
public class SqlServerObjectListEnhancerTest
  extends WbTestCase
{

  public SqlServerObjectListEnhancerTest()
  {
    super("SqlServerObjectListEnhancerTest");
  }

  @BeforeClass
  public static void setUpClass()
    throws Exception
  {
    SQLServerTestUtil.initTestcase("SqlServerObjectListEnhancerTest");
    WbConnection conn = SQLServerTestUtil.getSQLServerConnection();
    Assume.assumeNotNull("No connection available", conn);
    SQLServerTestUtil.dropAllObjects(conn);
    String sql =
        "create table person \n" +
        "( \n" +
        "   id integer, \n" +
        "   firstname varchar(100), \n" +
        "   lastname varchar(100) \n" +
        ")";
    TestUtil.executeScript(conn, sql);
  }

  @AfterClass
  public static void tearDownClass()
    throws Exception
  {
    WbConnection conn = SQLServerTestUtil.getSQLServerConnection();
    Assume.assumeNotNull("No connection available", conn);
    SQLServerTestUtil.dropAllObjects(conn);
  }

  @Test
  public void testRemarks()
    throws SQLException
  {
    WbConnection conn = SQLServerTestUtil.getSQLServerConnection();
    assertNotNull("No connection available", conn);
    Settings.getInstance().setProperty("workbench.db.microsoft_sql_server.remarks.object.retrieve", true);
    TableIdentifier sales = new DbObjectFinder(conn).findTable(new TableIdentifier("person"));

    sales.setComment("One person");
    TableCommentReader reader = new TableCommentReader();
    String sql = reader.getTableCommentSql(conn, sales);
    Statement stmt = null;
    try
    {
      stmt = conn.createStatement();
      stmt.execute(sql);
      conn.commit();
    }
    catch (SQLException e)
    {
      e.printStackTrace();
      throw e;
    }
    finally
    {
      JdbcUtils.closeStatement(stmt);
    }
    List<TableIdentifier> tables = conn.getMetadata().getTableList("person", "dbo");
    assertEquals(1, tables.size());
    sales = tables.get(0);
    assertEquals("One person", sales.getComment());
    sales = new DbObjectFinder(conn).findTable(new TableIdentifier("person"));
    assertEquals("One person", sales.getComment());
  }

}
