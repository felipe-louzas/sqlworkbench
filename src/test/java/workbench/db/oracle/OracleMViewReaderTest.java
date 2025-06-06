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
package workbench.db.oracle;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.DbMetadata;
import workbench.db.DbObjectFinder;
import workbench.db.DropType;
import workbench.db.OracleTest;
import workbench.db.TableDefinition;
import workbench.db.TableIdentifier;
import workbench.db.TableSourceBuilder;
import workbench.db.TableSourceBuilderFactory;
import workbench.db.WbConnection;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
@Category(OracleTest.class)
public class OracleMViewReaderTest
  extends WbTestCase
{

  public OracleMViewReaderTest()
  {
    super("OracleMViewReaderTest");
  }

  @Before
  public void setUpClass()
    throws Exception
  {
    OracleTestUtil.initTestCase();
  }

  @After
  public void tearDownTest()
    throws Exception
  {
    OracleTestUtil.cleanUpTestCase();
  }

  @Test
  public void testPartitionedMview()
    throws Exception
  {
    WbConnection con = OracleTestUtil.getOracleConnection();
    assertNotNull("Oracle not available", con);

    String sql =
      "CREATE table person (id integer primary key, name varchar(100));" +
      "create materialized view mv_person  \n" +
      "  partition by list (id) \n" +
      "  ( \n " +
      "    partition p_1 values (1), \n" +
      "    partition p_2 values (2) \n" +
      "  )\n" +
      "  build deferred \n" +
      "  refresh complete on demand with rowid \n" +
      "  enable query rewrite \n" +
      "as\n" +
      "select * from person;";
    TestUtil.executeScript(con, sql);
    TableIdentifier mview = new DbObjectFinder(con).findObject(new TableIdentifier("MV_PERSON"));
    TableSourceBuilder builder = TableSourceBuilderFactory.getBuilder(con);
    String source = builder.getTableSource(mview, DropType.none, false);
    assertNotNull(source);
//    System.out.println(source);
    String expected =
      "CREATE MATERIALIZED VIEW MV_PERSON\n" +
      "  PARTITION BY LIST (ID)\n" +
      "  (\n" +
      "    PARTITION P_1 VALUES (1),  \n" +
      "    PARTITION P_2 VALUES (2)\n" +
      "  )\n" +
      "  BUILD DEFERRED\n" +
      "  REFRESH COMPLETE ON DEMAND WITH ROWID\n" +
      "  ENABLE QUERY REWRITE\n" +
      "AS\n" +
      "SELECT PERSON.ID ID,PERSON.NAME NAME FROM PERSON PERSON;";
    assertEquals(expected, source.trim());
  }

  @Test
  public void testGetMViewSource1()
    throws Exception
  {
    WbConnection con = OracleTestUtil.getOracleConnection();
    assertNotNull("Oracle not available", con);

    String sql =
      "CREATE table person (id integer primary key, name varchar(100));" +
      "create materialized view v_person  \n" +
      "  build immediate \n" +
      "  refresh complete on demand with rowid \n" +
      "  enable query rewrite \n" +
      "as\n" +
      "select * from person;";
    TestUtil.executeScript(con, sql, true);
    DbObjectFinder finder = new DbObjectFinder(con);
    TableIdentifier mview = finder.findObject(new TableIdentifier("V_PERSON"));
    assertEquals(DbMetadata.MVIEW_NAME, mview.getType());
    TableDefinition def = con.getMetadata().getTableDefinition(mview);
    OracleMViewReader reader = new OracleMViewReader();
    String source = reader.getMViewSource(con, def, null, DropType.none, true).toString();
//    System.out.println(source);
    String expected =
      "CREATE MATERIALIZED VIEW V_PERSON\n" +
      "(\n" +
      "  ID,\n" +
      "  NAME\n" +
      ")\n"+
      "  TABLESPACE USERS\n" +
      "  BUILD IMMEDIATE\n" +
      "  REFRESH COMPLETE ON DEMAND WITH ROWID\n" +
      "  ENABLE QUERY REWRITE\n" +
      "AS\n" +
      "SELECT PERSON.ID ID,PERSON.NAME NAME FROM PERSON PERSON;";
    assertEquals(expected, source.trim());

    TestUtil.executeScript(con,
      "comment on materialized view v_person is 'the mview';\n"+
      "comment on column v_person.id is 'the person PK';\n");

    mview = finder.findObject(new TableIdentifier("V_PERSON"));
    def = con.getMetadata().getTableDefinition(mview);
    expected += "\n\n" +
      "COMMENT ON MATERIALIZED VIEW V_PERSON IS 'the mview';\n"+
      "COMMENT ON COLUMN V_PERSON.ID IS 'the person PK';";
    source = con.getMetadata().getViewReader().getExtendedViewSource(def, DropType.none, false).toString().trim();
    assertEquals(expected, source);
  }

  @Test
  public void testGetMViewSource2()
    throws Exception
  {
    WbConnection con = OracleTestUtil.getOracleConnection();
    if (con == null) return;
    String sql =
      "CREATE table person (id integer primary key, name varchar(100)); \n" +
      "create materialized view v_person  \n" +
      "  build deferred \n" +
      "  refresh force on commit with primary key\n" +
      "  disable query rewrite \n" +
      "as\n" +
      "select * from person;";
    TestUtil.executeScript(con, sql);
    TableIdentifier mview = new DbObjectFinder(con).findObject(new TableIdentifier("V_PERSON"));
    TableSourceBuilder builder = TableSourceBuilderFactory.getBuilder(con);
    String source = builder.getTableSource(mview, DropType.none, false);
    assertNotNull(source);
    String expected =
      "CREATE MATERIALIZED VIEW V_PERSON\n" +
      "  TABLESPACE USERS\n" +
      "  BUILD DEFERRED\n" +
      "  REFRESH FORCE ON COMMIT WITH PRIMARY KEY\n" +
      "  DISABLE QUERY REWRITE\n" +
      "AS\n" +
      "SELECT PERSON.ID ID,PERSON.NAME NAME FROM PERSON PERSON;";
    assertEquals(expected, source.trim());
  }

  @Test
  public void testGetMViewSource3()
    throws Exception
  {
    WbConnection con = OracleTestUtil.getOracleConnection();
    if (con == null) return;
    String sql =
      "CREATE table person (id integer primary key, name varchar(100)); \n" +
      "create table v_person (id integer primary key, name varchar(100)); \n" +
      "create materialized view v_person  \n" +
      "  on prebuilt table \n " +
      "  refresh force on commit with primary key\n" +
      "  disable query rewrite \n" +
      "as\n" +
      "select * from person;";
    TestUtil.executeScript(con, sql);
    TableIdentifier mview = new DbObjectFinder(con).findObject(new TableIdentifier("V_PERSON"));
    TableSourceBuilder builder = TableSourceBuilderFactory.getBuilder(con);
    String source = builder.getTableSource(mview, DropType.none, false);
    assertNotNull(source);
    String expected =
      "CREATE MATERIALIZED VIEW V_PERSON\n" +
      "  ON PREBUILT TABLE\n" +
      "  REFRESH FORCE ON COMMIT WITH PRIMARY KEY\n" +
      "  DISABLE QUERY REWRITE\n" +
      "AS\n" +
      "SELECT PERSON.ID ID,PERSON.NAME NAME FROM PERSON PERSON;";
//    System.out.println(source.trim() +"\n*********\n" +expected);
    assertEquals(expected, source.trim());
  }

  @Test
  public void testRefreshSource()
    throws Exception
  {
    WbConnection con = OracleTestUtil.getOracleConnection();
    if (con == null) return;
    String sql =
      "CREATE TABLE person (id integer);\n " +
      "create materialized view mv_person \n" +
      "refresh complete  \n" +
      "start with sysdate next round(sysdate + 1) + 5/24  \n" +
      "as  \n" +
      "select count(*) \n" +
      "from person \n" +
      ";";

    TestUtil.executeScript(con, sql);
    TableIdentifier mview = new DbObjectFinder(con).findObject(new TableIdentifier("MV_PERSON"));
    TableSourceBuilder builder = TableSourceBuilderFactory.getBuilder(con);
    String source = builder.getTableSource(mview, DropType.none, false);
    assertNotNull(source);
    String expected =
      "CREATE MATERIALIZED VIEW MV_PERSON\n" +
      "  TABLESPACE USERS\n" +
      "  BUILD IMMEDIATE\n" +
      "  REFRESH COMPLETE ON DEMAND WITH ROWID\n" +
      "  NEXT round(sysdate + 1) + 5/24\n" +
      "  DISABLE QUERY REWRITE\n" +
      "AS\n" +
      "select count(*) \n" +
      "from person;";
//    System.out.println("***\n"+expected + "\n++++\n" + source);
    assertEquals(expected, source.trim());
  }
}
