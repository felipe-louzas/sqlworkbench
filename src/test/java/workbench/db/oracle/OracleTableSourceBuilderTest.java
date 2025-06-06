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
import workbench.resource.Settings;

import workbench.db.DbObjectFinder;
import workbench.db.JdbcUtils;
import workbench.db.OracleTest;
import workbench.db.TableDefinition;
import workbench.db.TableIdentifier;
import workbench.db.TableSourceBuilder;
import workbench.db.TableSourceBuilderFactory;
import workbench.db.WbConnection;

import workbench.sql.parser.ParserType;
import workbench.sql.parser.ScriptParser;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
@Category(OracleTest.class)
public class OracleTableSourceBuilderTest
  extends WbTestCase
{

  public OracleTableSourceBuilderTest()
  {
    super("OracleTableSourceBuilderTest");
  }

  @BeforeClass
  public static void setUpClass()
    throws Exception
  {
    String sql =
      "CREATE TABLE index_test (test_id integer not null, tenant_id integer);\n" +
      "ALTER TABLE index_test \n" +
      "   ADD CONSTRAINT pk_indexes PRIMARY KEY (test_id)  \n" +
      "   USING INDEX (CREATE INDEX idx_pk_index_test ON index_test (test_id, tenant_id) REVERSE);" +
      "CREATE TABLE uc_test (test_id integer not null, tenant_id integer); \n" +
      "create unique index ux_test on uc_test(test_id); \n" +
      "alter table uc_test add constraint unique_test_id unique (test_id) using index ux_test;";

    OracleTestUtil.initTestCase();
    WbConnection con = OracleTestUtil.getOracleConnection();
    if (con == null) return;
    TestUtil.executeScript(con, sql, false);
  }

  @AfterClass
  public static void tearDownClass()
    throws Exception
  {
    OracleTestUtil.cleanUpTestCase();
  }

  @Test
  public void testIOT()
    throws Exception
  {
    WbConnection con = OracleTestUtil.getOracleConnection();
    assertNotNull("Oracle not available", con);

    try
    {
      String sql =
        "create table iot1  \n" +
        "( \n" +
        "  id1 integer not null, \n" +
        "  id2 integer not null, \n" +
        "  some_column integer, \n" +
        "  constraint pk_iot1 primary key (id1, id2) \n" +
        ") \n" +
        "organization index \n" +
        "compress 1 \n" +
        "overflow tablespace users";
      TestUtil.executeScript(con, sql);
      TableSourceBuilder builder = TableSourceBuilderFactory.getBuilder(con);
      TableDefinition tbl = con.getMetadata().getTableDefinition(new TableIdentifier("IOT1"));
      String tableSource = builder.getTableSource(tbl.getTable(), tbl.getColumns());
//      System.out.println(tableSource);
      assertTrue(tableSource.contains("CONSTRAINT PK_IOT1 PRIMARY KEY (ID1, ID2)"));
      assertFalse(tableSource.contains("ALTER TABLE"));
      assertTrue(tableSource.contains("COMPRESS 1"));
      assertTrue(tableSource.contains("ORGANIZATION INDEX"));
      assertTrue(tableSource.contains("INCLUDING SOME_COLUMN"));
    }
    finally
    {
      TestUtil.executeScript(con, "drop table iot1 cascade constraints purge");
    }
  }

  @Test
  public void testReadOnly()
    throws Exception
  {
    WbConnection con = OracleTestUtil.getOracleConnection();
    assertNotNull("Oracle not available", con);

    try
    {
      String sql =
        "create table no_writes \n" +
        "( \n" +
        "  id1 integer not null, \n" +
        "  id2 integer not null, \n" +
        "  some_column integer \n" +
        ");\n" +
        "alter table no_writes read only;";
      TestUtil.executeScript(con, sql);
      TableSourceBuilder builder = TableSourceBuilderFactory.getBuilder(con);
      TableDefinition tbl = con.getMetadata().getTableDefinition(new TableIdentifier("NO_WRITES"));
      String tableSource = builder.getTableSource(tbl.getTable(), tbl.getColumns());
//      System.out.println(tableSource);
      assertTrue(tableSource.contains("ALTER TABLE NO_WRITES READ ONLY"));
    }
    finally
    {
      TestUtil.executeScript(con, "drop table no_writes cascade constraints purge");
    }
  }

  @Test
  public void testPartitions()
    throws Exception
  {
    WbConnection con = OracleTestUtil.getOracleConnection();
    assertNotNull("Oracle not available", con);
    try
    {
      String sql =
        "create table part_test (\n" +
        "  part_key integer not null, \n" +
        "  some_data varchar(50)\n" +
        ")\n" +
        "partition by list (part_key)\n" +
        "(\n" +
        "  partition p_1 values (1),\n" +
        "  partition p_2 values (2)\n" +
        ");";

      TestUtil.executeScript(con, sql);

      TableSourceBuilder builder = TableSourceBuilderFactory.getBuilder(con);
      TableDefinition tbl = con.getMetadata().getTableDefinition(new TableIdentifier("PART_TEST"));
      String source = builder.getTableSource(tbl.getTable(), tbl.getColumns());
      assertTrue(source.contains("PARTITION BY LIST (PART_KEY)"));
      assertTrue(source.contains("PARTITION P_1 VALUES (1)"));
      assertTrue(source.contains("PARTITION P_2 VALUES (2)"));
    }
    finally
    {
      String cleanup =
        "drop table part_test cascade constraints purge; \n" +
        "purge recyclebin;";
      TestUtil.executeScript(con, cleanup);
    }
  }

  @Test
  public void testTemplateSubPartitions()
    throws Exception
  {
    WbConnection con = OracleTestUtil.getOracleConnection();
    assertNotNull("Oracle not available", con);
    try
    {
      String sql =
        "create table subpart_test (\n" +
        "  part_key  integer not null, \n" +
        "  sub_key   integer not null, \n" +
        "  some_data varchar(50)\n" +
        ")\n" +
        "partition by list (part_key)\n" +
        "subpartition by list (sub_key) \n" +
        "subpartition template \n" +
        "(\n" +
        "  subpartition sp_1 values (1,2,3)," +
        "  subpartition sp_2 values (4,5,6)" +
        ")\n" +
        "(\n" +
        "  partition p_1 values (1),\n" +
        "  partition p_2 values (2)\n" +
        ");";

      TestUtil.executeScript(con, sql);

      TableSourceBuilder builder = TableSourceBuilderFactory.getBuilder(con);
      TableDefinition tbl = con.getMetadata().getTableDefinition(new TableIdentifier("SUBPART_TEST"));
      String source = builder.getTableSource(tbl.getTable(), tbl.getColumns());
      String expected =
        "CREATE TABLE SUBPART_TEST\n" +
        "(\n" +
        "   PART_KEY   NUMBER              NOT NULL,\n" +
        "   SUB_KEY    NUMBER              NOT NULL,\n" +
        "   SOME_DATA  VARCHAR2(50 Byte)\n" +
        ")\n" +
        "PARTITION BY LIST (PART_KEY)\n" +
        "SUBPARTITION BY LIST (SUB_KEY)\n" +
        "SUBPARTITION TEMPLATE\n" +
        "(\n" +
        "  SUBPARTITION SP_1 VALUES (1, 2, 3),\n" +
        "  SUBPARTITION SP_2 VALUES (4, 5, 6)\n" +
        ")\n" +
        "(\n" +
        "  PARTITION P_1 VALUES (1)\n" +
        "  (\n" +
        "      SUBPARTITION P_1_SP_1 VALUES (1, 2, 3),\n" +
        "      SUBPARTITION P_1_SP_2 VALUES (4, 5, 6)\n" +
        "  ),\n" +
        "  PARTITION P_2 VALUES (2)\n" +
        "  (\n" +
        "      SUBPARTITION P_2_SP_1 VALUES (1, 2, 3),\n" +
        "      SUBPARTITION P_2_SP_2 VALUES (4, 5, 6)\n" +
        "  )\n" +
        ")\n" +
        "TABLESPACE USERS;";
//      System.out.println(expected + "\n---\n" + source);
      assertEquals(expected, source.trim());
    }
    finally
    {
      String cleanup =
        "drop table subpart_test cascade constraints purge; \n" +
        "purge recyclebin;";
      TestUtil.executeScript(con, cleanup);
    }
  }

  @Test
  public void testSubPartitions()
    throws Exception
  {
    WbConnection con = OracleTestUtil.getOracleConnection();
    assertNotNull("Oracle not available", con);
    try
    {
      String sql =
        "create table subpart_test (\n" +
        "  part_key  integer not null, \n" +
        "  sub_key   integer not null, \n" +
        "  some_data varchar(50)\n" +
        ")\n" +
        "partition by list (part_key)\n" +
        "subpartition by list (sub_key) \n" +
        "(\n" +
        "  partition p_1 values (1) (subpartition p1_1 values (1) ),\n" +
        "  partition p_2 values (2) (subpartition p2_1 values (1) )\n" +
        ");";

      TestUtil.executeScript(con, sql);

      TableSourceBuilder builder = TableSourceBuilderFactory.getBuilder(con);
      TableDefinition tbl = con.getMetadata().getTableDefinition(new TableIdentifier("SUBPART_TEST"));
      String source = builder.getTableSource(tbl.getTable(), tbl.getColumns());
//      System.out.println(source);
      assertTrue(source.contains("PARTITION BY LIST (PART_KEY)"));
      assertTrue(source.contains("PARTITION P_1 VALUES (1)"));
      assertTrue(source.contains("PARTITION P_2 VALUES (2)"));
      assertTrue(source.contains("SUBPARTITION P1_1 VALUES (1)"));
      assertTrue(source.contains("SUBPARTITION P2_1 VALUES (1)"));
    }
    finally
    {
      String cleanup =
        "drop table subpart_test cascade constraints purge; \n" +
        "purge recyclebin;";
      TestUtil.executeScript(con, cleanup);
    }
  }

  @Test
  public void testIdentity2()
    throws Exception
  {
    WbConnection con = OracleTestUtil.getOracleConnection();
    assertNotNull("Oracle not available", con);

    if (!JdbcUtils.hasMinimumServerVersion(con, "12.1"))
    {
      System.out.println("No Oracle 12c available, skipping test");
      return;
    }
    String sql =
      "CREATE TABLE sample \n" +
      "(\n" +
      "  id NUMBER GENERATED ALWAYS AS IDENTITY,\n" +
      "  sno GENERATED ALWAYS AS (CONCAT('SNO_#', to_char(id, 'FM000000'))) VIRTUAL\n" +
      ");";

    try
    {
      TestUtil.executeScript(con, sql);

      TableSourceBuilder builder = TableSourceBuilderFactory.getBuilder(con);
      TableDefinition def = con.getMetadata().getTableDefinition(new TableIdentifier("SAMPLE"));

      String source = builder.getTableSource(def.getTable(), def.getColumns());
      assertTrue(source.contains("ID   NUMBER              GENERATED ALWAYS AS IDENTITY NOT NULL"));
      assertTrue(source.contains("SNO  GENERATED ALWAYS AS ('SNO_#'||TO_CHAR(\"ID\",'FM000000'))"));
    }
    finally
    {
      TestUtil.executeScript(con, "drop table sample cascade constraints purge;");
    }
  }

  @Test
  public void test12c()
    throws Exception
  {
    WbConnection con = OracleTestUtil.getOracleConnection();
    assertNotNull("Oracle not available", con);

    if (!JdbcUtils.hasMinimumServerVersion(con, "12.1"))
    {
      System.out.println("No Oracle 12c available, skipping test");
      return;
    }

    try
    {
      String sql =
        "create table default_null (id integer not null primary key, some_value varchar(100) default on null 'Arthur');\n" +
        "create sequence test_sequence;\n" +
        "create table sequence_default (id integer default test_sequence.nextval not null primary key);\n" +
        "create table ident_default (id integer generated always as identity);\n" +
        "create table ident_options (id integer generated always as identity start with 42 cache 50 maxvalue 42424242 cycle);\n";
      TestUtil.executeScript(con, sql);

      TableSourceBuilder builder = TableSourceBuilderFactory.getBuilder(con);

      {
        TableDefinition defNull = con.getMetadata().getTableDefinition(new TableIdentifier("DEFAULT_NULL"));
        String source = builder.getTableSource(defNull.getTable(), defNull.getColumns());
        assertTrue(source.contains("DEFAULT ON NULL 'Arthur' NOT NULL"));
      }

      {
        TableDefinition seqDef = con.getMetadata().getTableDefinition(new TableIdentifier("SEQUENCE_DEFAULT"));
        String seqDefSql = builder.getTableSource(seqDef.getTable(), seqDef.getColumns());
        String owner = seqDef.getTable().getSchema();
        String expression = "DEFAULT \"" + owner + "\".\"TEST_SEQUENCE\".\"NEXTVAL\" NOT NULL";
        assertTrue(seqDefSql.contains(expression));
      }

      {
        TableDefinition ident1 = con.getMetadata().getTableDefinition(new TableIdentifier("IDENT_DEFAULT"));
        String identSql = builder.getTableSource(ident1.getTable(), ident1.getColumns());
        assertTrue(identSql.contains("ID  NUMBER   GENERATED ALWAYS AS IDENTITY NOT NULL"));
      }

      {
        TableDefinition ident1 = con.getMetadata().getTableDefinition(new TableIdentifier("IDENT_OPTIONS"));
        String identSql = builder.getTableSource(ident1.getTable(), ident1.getColumns());
        assertTrue(identSql.contains("ID  NUMBER   GENERATED ALWAYS AS IDENTITY START WITH 42 MAXVALUE 42424242 CYCLE CACHE 50 NOT NULL"));
      }
    }
    finally
    {
      String cleanup =
        "drop table default_null cascade constraints purge; \n" +
        "drop sequence test_sequence; \n" +
        "drop table sequence_default cascade constraints purge; \n" +
        "drop table ident_default cascade constraints purge; \n" +
        "drop table ident_options cascade constraints purge;" +
        "purge recyclebin;";
      TestUtil.executeScript(con, cleanup);
    }
  }

  @Test
  public void testGetSource()
    throws Exception
  {
    WbConnection con = OracleTestUtil.getOracleConnection();
    assertNotNull("Oracle not available", con);

    TableIdentifier table = new DbObjectFinder(con).findTable(new TableIdentifier("INDEX_TEST"));
    assertNotNull(table);
    String sql = table.getSource(con).toString();

//    System.out.println(sql);
    //assertTrue(sql.indexOf("USING INDEX (") > 0);
    ScriptParser p = new ScriptParser(sql, ParserType.Oracle);
    assertEquals(2, p.getSize());
    String indexSql = p.getCommand(1);
    indexSql = indexSql.replaceAll("\\s+", " ");
//    System.out.println(indexSql);
    String expected = "ALTER TABLE INDEX_TEST ADD CONSTRAINT PK_INDEXES PRIMARY KEY (TEST_ID) USING INDEX ( CREATE INDEX IDX_PK_INDEX_TEST ON INDEX_TEST (TEST_ID ASC, TENANT_ID ASC) TABLESPACE USERS REVERSE )";
    assertEquals(expected, indexSql);
  }

  @Test
  public void testUniqueConstraintWithIndex()
    throws Exception
  {
    WbConnection con = OracleTestUtil.getOracleConnection();
    assertNotNull("Oracle not available", con);

    boolean showTablespace = Settings.getInstance().getBoolProperty("workbench.db.oracle.retrieve_tablespace", true);
    TableIdentifier table = new DbObjectFinder(con).findTable(new TableIdentifier("UC_TEST"));
    assertNotNull(table);
    try
    {
      Settings.getInstance().setProperty("workbench.db.oracle.retrieve_tablespace", false);
      String sql = table.getSource(con).toString();

//      System.out.println(sql);
      ScriptParser parser = new ScriptParser(sql, ParserType.Oracle);
      int size = parser.getSize();
      assertEquals(3, size);

      String createIdx =
        "CREATE UNIQUE INDEX UX_TEST\n" +
        "   ON UC_TEST (TEST_ID ASC)";
      assertEquals(createIdx, parser.getCommand(1).trim());

      String alter =
        "ALTER TABLE UC_TEST\n" +
        "   ADD CONSTRAINT UNIQUE_TEST_ID UNIQUE (TEST_ID)\n" +
        "   USING INDEX UX_TEST";
      assertEquals(alter, parser.getCommand(2).trim());
    }
    finally
    {
      Settings.getInstance().setProperty("workbench.db.oracle.retrieve_tablespace", showTablespace);
    }
  }

  @Test
  public void testLobOptions()
    throws Exception
  {
    WbConnection con = OracleTestUtil.getOracleConnection();
    assertNotNull("Oracle not available", con);

    String sql =
      "CREATE TABLE foo \n" +
      "( \n" +
      "  ID   INTEGER NOT NULL, \n" +
      "  B1   BLOB, \n" +
      "  B2   BLOB, \n" +
      "  B3   BLOB, \n" +
      "  B4   BLOB, \n" +
      "  B5   CLOB, \n" +
      "  X1   XMLTYPE, \n" +
      "  X2   XMLTYPE \n" +
      ") \n" +
      "LOB (B1) STORE AS SECUREFILE (DISABLE STORAGE IN ROW RETENTION NONE COMPRESS MEDIUM NOCACHE ) \n" +
      "LOB (B2) STORE AS SECUREFILE (ENABLE STORAGE IN ROW RETENTION AUTO COMPRESS HIGH CACHE READS ) \n" +
      "LOB (B3) STORE AS SECUREFILE (ENABLE STORAGE IN ROW RETENTION MIN 1000 NOCOMPRESS CACHE ) \n" +
      "LOB (B4) STORE AS BASICFILE (ENABLE STORAGE IN ROW NOCACHE) \n" +
      "LOB (B5) STORE AS SECUREFILE (ENABLE STORAGE IN ROW CACHE DEDUPLICATE) \n" +
      "xmltype x1 STORE AS BASICFILE CLOB " +
      "xmltype x2 STORE AS SECUREFILE BINARY XML;";

    TestUtil.executeScript(con, sql);
    TableIdentifier foo = new DbObjectFinder(con).findTable(new TableIdentifier("FOO"));
    String source = foo.getSource(con).toString().trim();
//    System.out.println(source);
    assertTrue(source.contains("LOB (B1) STORE AS SECUREFILE (DISABLE STORAGE IN ROW RETENTION NONE COMPRESS MEDIUM NOCACHE)"));
    assertTrue(source.contains("LOB (B2) STORE AS SECUREFILE (ENABLE STORAGE IN ROW RETENTION AUTO COMPRESS HIGH CACHE READS)"));
    assertTrue(source.contains("LOB (B3) STORE AS SECUREFILE (ENABLE STORAGE IN ROW RETENTION MIN 1000 NOCOMPRESS CACHE)"));
    assertTrue(source.contains("LOB (B4) STORE AS BASICFILE (ENABLE STORAGE IN ROW NOCACHE)"));
    assertTrue(source.contains("LOB (B5) STORE AS SECUREFILE (ENABLE STORAGE IN ROW RETENTION AUTO NOCOMPRESS DEDUPLICATE CACHE)"));
    assertTrue(source.contains("XMLTYPE X1 STORE AS BASICFILE CLOB (ENABLE STORAGE IN ROW NOCACHE)"));
    assertTrue(source.contains("XMLTYPE X2 STORE AS SECUREFILE BINARY XML (ENABLE STORAGE IN ROW RETENTION AUTO NOCOMPRESS CACHE)"));
  }

}
