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
package workbench.db;

import java.util.List;
import workbench.TestUtil;
import workbench.WbTestCase;
import static org.junit.Assert.*;
import org.junit.Test;


/**
 *
 * @author Thomas Kellerer
 */
public class DbMetadataTest
  extends WbTestCase
{

  public DbMetadataTest()
  {
    super("DbMetadataTest");
  }

  @Test
  public void testGetTableDefinition()
    throws Exception
  {
    try
    {
      TestUtil util = getTestUtil();
      WbConnection con = util.getConnection();
      TestUtil.executeScript(con, "create table \"MyTest\" (id integer);\n" +
        "create table person (id integer primary key, firstname varchar(20));\n" +
        "commit;");

      DbMetadata meta = con.getMetadata();
      DbObjectFinder finder = new DbObjectFinder(con);
      TableIdentifier tbl = finder.findObject(new TableIdentifier("\"MyTest\""));
      assertNotNull(tbl);
      assertTrue(tbl.getNeverAdjustCase());
      assertEquals("MyTest", tbl.getTableName());

      TableDefinition def = meta.getTableDefinition(tbl);
      assertNotNull(def);
      assertNotNull(def.getTable());
      assertEquals("MyTest", def.getTable().getTableName());

      tbl = finder.findObject(new TableIdentifier("MyTest"), false, false);
      assertNotNull(tbl);
      assertEquals("MyTest", tbl.getTableName());

      TableIdentifier tbl2 = finder.findObject(new TableIdentifier("Person"));
      assertNotNull(tbl2);
      assertEquals("PERSON", tbl2.getTableName());

      List<TableIdentifier> tables = meta.getTableList();
      assertNotNull(tables);
      assertEquals(2, tables.size());
    }
    finally
    {
      ConnectionMgr.getInstance().disconnectAll();
    }
  }
}
