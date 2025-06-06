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

import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.DbObjectFinder;
import workbench.db.IbmDb2Test;
import workbench.db.IndexDefinition;
import workbench.db.IndexReader;
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
@Category(IbmDb2Test.class)
public class Db2IndexReaderTest
  extends WbTestCase
{
  public Db2IndexReaderTest()
  {
    super("Db2IndexReaderTest");
  }

  @BeforeClass
  public static void beforeClass()
    throws Exception
  {
    Db2TestUtil.initTestCase();
  }

  @AfterClass
  public static void afterClass()
  {
    Db2TestUtil.cleanUpTestCase();
  }

  @Test
  public void testClusteredIndex()
    throws Exception
  {
    WbConnection conn = Db2TestUtil.getDb2Connection();
    Assume.assumeNotNull(conn);

    try
    {
      TestUtil.executeScript(conn,
        "create table junit_t1 (id integer, c1 integer);\n" +
        "create index junit_i1 on junit_t1 (id) cluster;\n" +
        "commit;");
      TableIdentifier table = new DbObjectFinder(conn).findTable(new TableIdentifier("JUNIT_T1"));
      IndexReader reader = conn.getMetadata().getIndexReader();
      assertTrue(reader instanceof Db2IndexReader);
      List<IndexDefinition> indexes = reader.getTableIndexList(table, false);
      assertNotNull(indexes);
      assertEquals(1, indexes.size());

      String expected = "CREATE INDEX JUNIT_I1\n   ON " + Db2TestUtil.getSchemaName() + ".JUNIT_T1 (ID ASC) CLUSTER;";
      String result = reader.getIndexSource(table, indexes.get(0)).toString().trim();
//      System.out.println("--------- expected: \n" + expected + "\n-----------------\n" + result);
      assertEquals(expected, result);
    }
    finally
    {
      TestUtil.executeScript(conn,
        "drop table junit_t1; \n" +
        "commit;\n");
    }
  }

  @Test
  public void testInclude()
    throws Exception
  {
    WbConnection conn = Db2TestUtil.getDb2Connection();
    Assume.assumeNotNull(conn);

    try
    {
      TestUtil.executeScript(conn,
        "create table junit_t1 (c1 integer, c2 integer);\n" +
        "create unique index junit_i1 on junit_t1 (c1) include (c2);\n" +
        "create table junit_t2 (c1 integer, c2 integer, c3 integer, c4 integer);\n" +
        "create unique index junit_i2 on junit_t2 (c1,c2) include (c3,c4);\n" +
        "commit;");
      DbObjectFinder finder = new DbObjectFinder(conn);
      TableIdentifier t1 = finder.findTable(new TableIdentifier("JUNIT_T1"));
      TableIdentifier t2 = finder.findTable(new TableIdentifier("JUNIT_T2"));

      IndexReader reader = conn.getMetadata().getIndexReader();
      assertTrue(reader instanceof Db2IndexReader);
      List<IndexDefinition> indexes = reader.getTableIndexList(t1, false);
      assertNotNull(indexes);
      assertEquals(1, indexes.size());

      String expected = "CREATE UNIQUE INDEX JUNIT_I1\n   ON " + Db2TestUtil.getSchemaName() + ".JUNIT_T1 (C1 ASC) INCLUDE (C2);";
      String result = reader.getIndexSource(t1, indexes.get(0)).toString().trim();
//      System.out.println("--------- expected: \n" + expected + "\n-----------------\n" + result);


      indexes = reader.getTableIndexList(t2, false);
      assertNotNull(indexes);
      assertEquals(1, indexes.size());
      expected = "CREATE UNIQUE INDEX JUNIT_I2\n   ON " + Db2TestUtil.getSchemaName() + ".JUNIT_T2 (C1 ASC, C2 ASC) INCLUDE (C3,C4);";
      result = reader.getIndexSource(t2, indexes.get(0)).toString().trim();
//      System.out.println("--------- expected: \n" + expected + "\n-----------------\n" + result);
      assertEquals(expected, result);
    }
    finally
    {
      TestUtil.executeScript(conn,
        "drop table junit_t1; \n" +
        "drop table junit_t2; \n" +
        "commit;\n");
    }
  }

}
