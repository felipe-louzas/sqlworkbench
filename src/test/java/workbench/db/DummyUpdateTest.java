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


import workbench.TestUtil;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class DummyUpdateTest
{

  @Test
  public void testGetSource()
    throws Exception
  {
    TestUtil util = new TestUtil("DummyUpdateGen1");
    WbConnection con = util.getConnection();

    try
    {
      TestUtil.executeScript(con,
        "create table person (nr integer not null primary key, firstname varchar(20), lastname varchar(20));\n" +
        "commit;");
      TableIdentifier person = new DbObjectFinder(con).findTable(new TableIdentifier("PERSON"));
      DummyUpdate update = new DummyUpdate(person);
      update.setDoFormatSql(false);

      assertEquals("UPDATE", update.getObjectType());
      String sql = update.getSource(con).toString();

      String expected =
        "UPDATE PERSON\n" +
        "   SET FIRSTNAME = 'FIRSTNAME_value',\n" +
        "       LASTNAME = 'LASTNAME_value'\n" +
        "WHERE NR = NR_value;";
//      System.out.println("Got: \n" + sql + "\n------Expected\n" + expected);
      assertEquals(expected, sql);
    }
    finally
    {
      ConnectionMgr.getInstance().disconnectAll();
    }
  }

  @Test
  public void testMultiColumnPK()
    throws Exception
  {
    TestUtil util = new TestUtil("DummyUpdateGen1");
    WbConnection con = util.getConnection();

    try
    {
      TestUtil.executeScript(con,
        "create table link_table (some_data varchar(20), id1 integer not null, id2 integer not null, primary key (id1, id2));\n" +
        "commit;");

      TableIdentifier person = new DbObjectFinder(con).findTable(new TableIdentifier("LINK_TABLE"));
      DummyUpdate update = new DummyUpdate(person);
      update.setDoFormatSql(false);
      assertEquals("UPDATE", update.getObjectType());
      String sql = update.getSource(con).toString();

      String expected =
        "UPDATE LINK_TABLE\n" +
        "   SET SOME_DATA = 'SOME_DATA_value'\n" +
        "WHERE ID1 = ID1_value\n" +
        "  AND ID2 = ID2_value;";
//      System.out.println("Got: \n" + sql + "\n------Expected\n" + expected);
      assertEquals(expected, sql);
    }
    finally
    {
      ConnectionMgr.getInstance().disconnectAll();
    }
  }


}
