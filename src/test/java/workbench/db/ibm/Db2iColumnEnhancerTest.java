/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
 * that restricts the use for certain governments.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.sql-workbench.eu/manual/license.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.eu
 */
package workbench.db.ibm;

import java.sql.Types;
import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.ColumnIdentifier;
import workbench.db.ConnectionMgr;
import workbench.db.IbmDb2Test;
import workbench.db.TableDefinition;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.util.CollectionUtil;

import org.junit.AfterClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
@Category(IbmDb2Test.class)
public class Db2iColumnEnhancerTest
  extends WbTestCase
{

  public Db2iColumnEnhancerTest()
  {
    super("Db2iColumnEnhancerTest");
  }

  @AfterClass
  public static void tearDown()
    throws Exception
  {
    ConnectionMgr.getInstance().disconnectAll();
  }

  @Test
  public void testUpdateColumnDefinition()
    throws Exception
  {
    WbConnection con = getTestUtil().getConnection();
    TestUtil.executeScript(con,
      "create schema qsys2;\n" +
      "create table qsys2.syscolumns (table_schema varchar(100), table_name varchar(100), column_name varchar(100), column_text varchar(100), ccsid integer);\n" +
      "insert into qsys2.syscolumns values ('PUBLIC', 'FOO', 'ID', 'The PK', null);\n" +
      "insert into qsys2.syscolumns values ('PUBLIC', 'FOO', 'FIRSTNAME', 'The firstname', null);\n" +
      "commit;");

    Db2iColumnEnhancer reader = new Db2iColumnEnhancer();
    TableIdentifier tbl = new TableIdentifier("PUBLIC", "FOO");
    ColumnIdentifier id = new ColumnIdentifier("ID", Types.INTEGER);
    ColumnIdentifier name = new ColumnIdentifier("FIRSTNAME", Types.VARCHAR);
    List<ColumnIdentifier> cols = CollectionUtil.arrayList(id, name);
    TableDefinition def = new TableDefinition(tbl, cols);
    reader.updateColumns(def, con, true, true);
    assertEquals("The PK", id.getComment());
    assertEquals("The firstname", name.getComment());
  }

}
