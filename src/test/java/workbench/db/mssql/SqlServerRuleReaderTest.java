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
import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.ConnectionMgr;
import workbench.db.MsSQLTest;
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
public class SqlServerRuleReaderTest
  extends WbTestCase
{
  public SqlServerRuleReaderTest()
  {
    super("SqlServerRuleReaderTest");
  }

  @BeforeClass
  public static void setUp()
    throws Exception
  {
    SQLServerTestUtil.initTestcase("SqlServerRuleReaderTest");
    WbConnection conn = SQLServerTestUtil.getSQLServerConnection();

    Assume.assumeNotNull("No connection available", conn);
    SQLServerTestUtil.dropAllObjects(conn);

    TestUtil.executeScript(conn,
      "CREATE rule positive_value as @value > 0;\n" +
      "COMMIT;\n"
    );
  }

  @AfterClass
  public static void tearDown()
    throws Exception
  {
    WbConnection con = SQLServerTestUtil.getSQLServerConnection();
    Assume.assumeNotNull("No connection available", con);

    SQLServerTestUtil.dropAllObjects(con);
    ConnectionMgr.getInstance().disconnect(con);
  }

  @Test
  public void testGetRuleList()
    throws SQLException
  {
    WbConnection con = SQLServerTestUtil.getSQLServerConnection();
    assertNotNull("No connection available", con);
    try
    {
      List<TableIdentifier> objects = con.getMetadata().getObjectList(null, new String[] { "RULE" } );
      assertEquals(1, objects.size());
      TableIdentifier tbl = objects.get(0);
      assertEquals("RULE", tbl.getType());
      CharSequence source = tbl.getSource(con);
      assertNotNull(source);
      assertEquals("CREATE rule positive_value as @value > 0;", source.toString().trim());
    }
    finally
    {
      ConnectionMgr.getInstance().disconnect(con);
    }
  }

  @Test
  public void testHandlesType()
  {
    SqlServerRuleReader reader = new SqlServerRuleReader();
    assertTrue(reader.handlesType("RULE"));
    assertTrue(reader.handlesType(new String[] {"TABLE", "RULE"}));
  }

  @Test
  public void testSupportedTypes()
  {
    SqlServerRuleReader reader = new SqlServerRuleReader();
    List<String> types = reader.supportedTypes();
    assertTrue(types.contains("RULE"));
  }

}
