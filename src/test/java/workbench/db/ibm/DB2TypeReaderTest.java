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

import workbench.db.IbmDb2Test;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

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
public class DB2TypeReaderTest
{

  public DB2TypeReaderTest()
  {
  }

  @BeforeClass
  public static void setUpClass()
    throws Exception
  {
    Db2TestUtil.initTestCase();
    WbConnection con = Db2TestUtil.getDb2Connection();
    if (con == null) return;

    String sql =
      "create type wbjunit.address_type as  \n" +
      "( \n" +
      "  street varchar(50),  \n" +
      "  city varchar(50), \n" +
      "  nr integer \n" +
      ") \n" +
      "MODE db2sql; \n" +
      "create type wbjunit.zz_type as  \n" +
      "( \n" +
      "  zz_street varchar(50),  \n" +
      "  zz_city varchar(50), \n" +
      "  zz_nr integer \n" +
      ") \n" +
      "MODE db2sql; \n" +
      "create type wbjunit.person_id_type as integer with comparisons; \n" +
      "create type wbjunit.id_list as integer array[10]; \n" +
      "create type wbjunit.person_row as row (id integer, name varchar(10)); \n" +
      "commit;\n";
    TestUtil.executeScript(con, sql);
  }

  @AfterClass
  public static void tearDownClass()
    throws Exception
  {
    WbConnection con = Db2TestUtil.getDb2Connection();
    if (con == null) return;

    String sql =
      "drop type wbjunit.address_type; \n" +
      "commit;\n";
    TestUtil.executeScript(con, sql);
    Db2TestUtil.cleanUpTestCase();
  }

  @Test
  public void testGetTypes()
    throws Exception
  {
    WbConnection con = Db2TestUtil.getDb2Connection();
    if (con == null) fail("No connection available");

    List<TableIdentifier> objects = con.getMetadata().getObjectList(Db2TestUtil.getSchemaName(), new String[] {"TYPE"} );
    assertNotNull(objects);
    assertEquals(5, objects.size());
    assertEquals("TYPE", objects.get(0).getType());

    DB2TypeReader reader = new DB2TypeReader();
    List<DB2ObjectType> types = reader.getTypes(con, Db2TestUtil.getSchemaName(), null);
    assertNotNull(types);
    assertEquals(5, types.size());

    DB2ObjectType type = types.get(0);
    assertEquals("ADDRESS_TYPE", type.getObjectName());
    String src = type.getSource(con).toString().trim();
    String sql =
      "CREATE TYPE ADDRESS_TYPE AS\n" +
      "(\n" +
      "  STREET  VARCHAR(50),\n" +
      "  CITY    VARCHAR(50),\n" +
      "  NR      INTEGER\n" +
      ");";
    assertEquals(src, sql);

    type = types.get(1);
    src = type.getSource(con).toString().trim();
    assertEquals("CREATE TYPE ID_LIST AS INTEGER ARRAY[10];", src);

    type = types.get(2);
    src = type.getSource(con).toString().trim();
    assertEquals("CREATE TYPE PERSON_ID_TYPE AS INTEGER WITH COMPARISONS;", src);

    type = types.get(3);
    src = type.getSource(con).toString().trim();
    sql =
      "CREATE TYPE PERSON_ROW AS ROW \n" +
      "(\n" +
      "  ID    INTEGER,\n" +
      "  NAME  VARCHAR(10)\n" +
      ");";
    assertEquals(src, sql);

    type = types.get(4);
    src = type.getSource(con).toString().trim();
    sql =
      "CREATE TYPE ZZ_TYPE AS\n" +
      "(\n" +
      "  ZZ_STREET  VARCHAR(50),\n" +
      "  ZZ_CITY    VARCHAR(50),\n" +
      "  ZZ_NR      INTEGER\n" +
      ");";
    assertEquals(src, sql);
  }

}
