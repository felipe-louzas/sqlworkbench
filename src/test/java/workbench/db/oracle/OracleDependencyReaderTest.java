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
package workbench.db.oracle;

import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.DbObject;
import workbench.db.DbObjectFinder;
import workbench.db.OracleTest;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.sql.DelimiterDefinition;

import workbench.util.CollectionUtil;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
@Category(OracleTest.class)
public class OracleDependencyReaderTest
  extends WbTestCase
{

  public OracleDependencyReaderTest()
  {
    super("OracleDependencyTest");
  }

  @BeforeClass
  public static void setUp()
  {
    WbConnection conn = OracleTestUtil.getOracleConnection();
    OracleTestUtil.dropAllObjects(conn);
  }

  @After
  public void tearDown()
  {
    WbConnection conn = OracleTestUtil.getOracleConnection();
    OracleTestUtil.dropAllObjects(conn);
  }

  @Test
  public void testProcDependencies()
    throws Exception
  {
    WbConnection conn = OracleTestUtil.getOracleConnection();
    assertNotNull(conn);

    TestUtil.executeScript(conn,
      "create table tx (id integer); \n");

    TestUtil.executeScript(conn,
      "create or replace procedure delete_tx \n" +
      "as \n" +
      "begin  \n" +
      "  delete from tx; \n" +
      "end; \n" +
      "/", DelimiterDefinition.DEFAULT_ORA_DELIMITER);

    TableIdentifier t1 = new DbObjectFinder(conn).findObject(new TableIdentifier("TX"));
    OracleDependencyReader reader = new OracleDependencyReader();
    List<DbObject> usedBy = reader.getUsedBy(conn, t1);
    assertEquals(1, usedBy.size());
    assertEquals("DELETE_TX", usedBy.get(0).getObjectName());
    assertEquals("PROCEDURE", usedBy.get(0).getObjectType());
  }

  @Test
  public void testTableDependencies()
    throws Exception
  {
    WbConnection conn = OracleTestUtil.getOracleConnection();
    assertNotNull(conn);

    TestUtil.executeScript(conn,
      "create table t1 (id integer); \n" +
      "create view v1 as select * from t1;\n" +
      "create view v2 as select t1.id as id1, v1.id as id2 from v1 cross join t1;\n" +
      "commit;");

    DbObjectFinder finder = new DbObjectFinder(conn);
    TableIdentifier t1 = finder.findObject(new TableIdentifier("T1"));
    TableIdentifier v1 = finder.findObject(new TableIdentifier("V1"));
    TableIdentifier v2 = finder.findObject(new TableIdentifier("V2"));

    OracleDependencyReader reader = new OracleDependencyReader();
    List<DbObject> usedBy = reader.getUsedBy(conn, t1);
    assertNotNull(usedBy);
    assertEquals(2, usedBy.size());
    assertEquals("V1", usedBy.get(0).getObjectName());
    assertEquals("V2", usedBy.get(1).getObjectName());

    List<DbObject> usedObjects = reader.getUsedObjects(conn, v1);
    assertNotNull(usedObjects);
    assertEquals(1, usedObjects.size());
    assertEquals("T1", usedObjects.get(0).getObjectName());

    List<DbObject> v2Uses = reader.getUsedObjects(conn, v2);
    assertNotNull(v2Uses);
    assertEquals(2, v2Uses.size());
    assertEquals("T1", v2Uses.get(0).getObjectName());
    assertEquals("V1", v2Uses.get(1).getObjectName());

    TestUtil.executeScript(conn,
      "create materialized view mv1 as select * from t1;");
    TableIdentifier mv1 = finder.findObject(new TableIdentifier("MV1"));
    List<DbObject> mv1Uses = reader.getUsedObjects(conn, mv1);
    assertEquals(1, mv1Uses.size());
    assertEquals("T1", mv1Uses.get(0).getObjectName());
  }

  @Test
  public void testSupportsDependencies()
  {
    OracleDependencyReader reader = new OracleDependencyReader();
    List<String> types = CollectionUtil.arrayList("view", "procedure", "function");
    for (String type : types)
    {
      assertTrue(reader.supportsIsUsingDependency(type));
      assertTrue(reader.supportsUsedByDependency(type));
    }
    assertTrue(reader.supportsUsedByDependency("table"));
    assertFalse(reader.supportsIsUsingDependency("table"));

  }

}
