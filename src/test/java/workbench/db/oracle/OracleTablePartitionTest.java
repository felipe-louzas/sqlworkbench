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

import java.sql.SQLException;
import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.DbObjectFinder;
import workbench.db.OracleTest;
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
@Category(OracleTest.class)
public class OracleTablePartitionTest
  extends WbTestCase
{
  private static boolean partitioningAvailable;

  public OracleTablePartitionTest()
  {
    super("OraclePartitionReaderTest");
  }

  @BeforeClass
  public static void setUpClass()
    throws Exception
  {
    System.setProperty("workbench.db.oracle.retrieve_tablespace", "false");

    // <editor-fold defaultstate="collapsed" desc="DDL">
    // More examples: https://psoug.org/reference/partitions.html
    String sql =
      "CREATE TABLE wb_list_partition_test \n" +
      "( \n" +
      "  test_id integer not null primary key, \n" +
      "  tenant_id integer not null \n" +
      ") \n" +
      "PARTITION BY LIST (tenant_id)  \n" +
      "( \n" +
      "  PARTITION wb_list_part_1  VALUES (1),  \n" +
      "  PARTITION wb_list_part_2  VALUES (4),  \n" +
      "  PARTITION wb_list_part_3  VALUES (8), \n" +
      "  PARTITION wb_list_part_4  VALUES (16) \n" +
      ");  \n" +
      "\n" +
      "CREATE TABLE wb_hash_partition_test \n" +
      "( \n" +
      "  test_id integer not null primary key, \n" +
      "  tenant_id integer not null, \n" +
      "  region_id integer not null \n" +
      ") \n" +
      "PARTITION BY HASH (tenant_id, region_id)  \n" +
      "( \n" +
      "  PARTITION wb_hash_part_1,  \n" +
      "  PARTITION wb_hash_part_2,  \n" +
      "  PARTITION wb_hash_part_3, \n" +
      "  PARTITION wb_hash_part_4, \n" +
      "  PARTITION wb_hash_part_5 \n" +
      ");";
    // </editor-fold>

    OracleTestUtil.initTestCase();
    WbConnection con = OracleTestUtil.getOracleConnection();
    if (con == null) return;

    try
    {
      TestUtil.executeScript(con, sql, false);
      partitioningAvailable = true;
    }
    catch (SQLException e)
    {
      if (e.getErrorCode() == 439)
      {
        partitioningAvailable = false;
        return;
      }
    }

    // <editor-fold defaultstate="collapsed" desc="DDL">
    sql =
       "CREATE TABLE RANGE_SUB_PART_HASH \n" +
       "( \n" +
       "  invoice_no    NUMBER NOT NULL, \n" +
       "  invoice_date  DATE   NOT NULL \n" +
       ") \n" +
       "PARTITION BY RANGE (invoice_date) \n" +
       "SUBPARTITION BY HASH (invoice_no) \n" +
       "SUBPARTITIONS 8 \n" +
       "( \n" +
       "  PARTITION invoices_q1 VALUES LESS THAN (DATE '2010-01-01'), \n" +
       "  PARTITION invoices_q2 VALUES LESS THAN (DATE '2010-04-01'), \n" +
       "  PARTITION invoices_q3 VALUES LESS THAN (DATE '2010-09-01'), \n" +
       "  PARTITION invoices_q4 VALUES LESS THAN (DATE '2011-01-01') \n" +
       ");\n" +
       " \n" +
       "CREATE TABLE RANGE_SUB_PART_LIST \n" +
       "( \n" +
       "  CREATE_DATE  DATE, \n" +
       "  OBJ_CAT      CHAR(1), \n" +
       "  NODE_ID      NUMBER \n" +
       " ) \n" +
       "PARTITION BY RANGE (CREATE_DATE) \n" +
       "SUBPARTITION BY LIST (OBJ_CAT) \n" +
       "(   \n" +
       "  PARTITION RSP_1 VALUES LESS THAN (TIMESTAMP '2010-01-30 21:00:00') \n" +
       "  (  \n" +
       "    SUBPARTITION RSP_1_SUB_1 VALUES ('N'), \n" +
       "    SUBPARTITION RSP_1_SUB_2 VALUES ('L')  \n" +
       "  ),   \n" +
       "  PARTITION RSP_2 VALUES LESS THAN (TIMESTAMP '2010-01-30 21:15:00') \n" +
       "  (  \n" +
       "    SUBPARTITION RSP_2_SUB_1 VALUES ('N') , \n" +
       "    SUBPARTITION RSP_2_SUB_2 VALUES ('L')  \n" +
       "  ) \n" +
       ");\n" +
       "\n"+
       "CREATE TABLE wb_ref_part_main\n" +
       "(\n" +
       "   id          integer not null,\n" +
       "   part_key    integer not null,\n" +
       "   data varchar(100), \n" +
       "   constraint pk_main primary key (id)\n" +
       ")\n" +
       "partition by list (part_key) \n" +
       "(\n" +
       "  partition part_1 values (1),\n" +
       "  partition part_2 values (2),\n" +
       "  partition part_3 values (3),\n" +
       "  partition part_4 values (4)\n" +
       ");\n" +
       "\n" +
       "CREATE TABLE wb_ref_part_detail \n" +
       "(\n" +
       "   id integer,\n" +
       "   main_id integer not null, \n" +
       "   constraint pk_detail primary key (id),\n" +
       "   CONSTRAINT fk_detail_main FOREIGN KEY (main_id) REFERENCES wb_ref_part_main(id) \n" +
       ")\n" +
       "partition by reference (fk_detail_main);";
    // </editor-fold>

    try
    {
      TestUtil.executeScript(con, sql, true);
      partitioningAvailable = true;
    }
    catch (SQLException e)
    {
      if (e.getErrorCode() == 439)
      {
        partitioningAvailable = false;
      }
    }

   // <editor-fold defaultstate="collapsed" desc="DDL">
   sql =
      "create table wb_int_partition_test\n" +
      "(\n" +
      "  id           number not null,\n" +
      "  create_date  date   not null,\n" +
      "  data         varchar2(500)\n" +
      ")\n" +
      "partition by range (create_date)\n" +
      "interval (interval '2' month)\n" +
      "(\n" +
      "  partition invoices_past values less than (date '2024-01-01')\n" +
      ");\n";
    // </editor-fold>

    try
    {
      TestUtil.executeScript(con, sql, true);
      partitioningAvailable = true;
    }
    catch (SQLException e)
    {
      if (e.getErrorCode() == 439)
      {
        partitioningAvailable = false;
      }
    }
  }

  @AfterClass
  public static void tearDownClass()
    throws Exception
  {
    System.clearProperty("workbench.db.oracle.retrieve_tablespace");
    OracleTestUtil.cleanUpTestCase();
  }

  @Test
  public void testRetrieveIntervalPartition()
    throws Exception
  {
    if (!partitioningAvailable) return;

    WbConnection con = OracleTestUtil.getOracleConnection();
    assertNotNull("Oracle not available", con);

    TableIdentifier tbl = new DbObjectFinder(con).findTable(new TableIdentifier("WB_INT_PARTITION_TEST"));
    assertNotNull(tbl);
    OracleTablePartition reader = new OracleTablePartition(con, false);
    reader.retrieve(tbl, con);
    assertTrue(reader.isPartitioned());
    assertEquals("RANGE", reader.getPartitionType());
    List<OraclePartitionDefinition> partitions = reader.getPartitions();
    assertEquals(1, partitions.size());
    String ddl = reader.getSourceForTableDefinition();
    assertTrue(ddl.startsWith("PARTITION BY RANGE (CREATE_DATE) INTERVAL (INTERVAL '2' MONTH) "));
   }

  @Test
  public void testRetrieveListPartition()
    throws Exception
  {
    if (!partitioningAvailable) return;

    WbConnection con = OracleTestUtil.getOracleConnection();
    assertNotNull("Oracle not available", con);

    TableIdentifier tbl = new DbObjectFinder(con).findTable(new TableIdentifier("WB_LIST_PARTITION_TEST"));
    assertNotNull(tbl);
    OracleTablePartition reader = new OracleTablePartition(con, false);
    reader.retrieve(tbl, con);

    assertTrue(reader.isPartitioned());
    assertEquals("LIST", reader.getPartitionType());
    List<String> columns = reader.getColumns();
    assertEquals(1, columns.size());
    List<OraclePartitionDefinition> partitions = reader.getPartitions();
    assertEquals(4, partitions.size());
    for (int i=0; i < 4; i++)
    {
      assertEquals("WB_LIST_PART_" + Integer.toString(i+1), partitions.get(i).getName());
      assertEquals(i + 1, partitions.get(i).getPosition());
    }
    assertEquals("1", partitions.get(0).getPartitionValue());
    assertEquals("4", partitions.get(1).getPartitionValue());
    assertEquals("8", partitions.get(2).getPartitionValue());
    assertEquals("16", partitions.get(3).getPartitionValue());
    String expected = "PARTITION BY LIST (TENANT_ID)\n" +
      "(\n" +
      "  PARTITION WB_LIST_PART_1 VALUES (1),\n" +
      "  PARTITION WB_LIST_PART_2 VALUES (4),\n" +
      "  PARTITION WB_LIST_PART_3 VALUES (8),\n" +
      "  PARTITION WB_LIST_PART_4 VALUES (16)\n" +
      ")";
    assertEquals(expected, reader.getSourceForTableDefinition().trim());
  }

  @Test
  public void testRetrieveHashPartition()
    throws Exception
  {
    if (!partitioningAvailable) return;
    WbConnection con = OracleTestUtil.getOracleConnection();
    assertNotNull("Oracle not available", con);

    TableIdentifier tbl = new DbObjectFinder(con).findTable(new TableIdentifier("WB_HASH_PARTITION_TEST"));
    assertNotNull(tbl);
    OracleTablePartition reader = new OracleTablePartition(con, false);
    reader.retrieve(tbl, con);

    assertTrue(reader.isPartitioned());
    assertEquals("HASH", reader.getPartitionType());
    List<String> columns = reader.getColumns();
    assertEquals(2, columns.size());
    List<OraclePartitionDefinition> partitions = reader.getPartitions();
    assertEquals(5, partitions.size());
    for (int i=0; i < 5; i++)
    {
      assertEquals("WB_HASH_PART_" + Integer.toString(i+1), partitions.get(i).getName());
      assertEquals(i + 1, partitions.get(i).getPosition());
      assertNull(partitions.get(i).getPartitionValue());
    }
    String expected = "PARTITION BY HASH (TENANT_ID,REGION_ID)\n" +
      "(\n" +
      "  PARTITION WB_HASH_PART_1,\n" +
      "  PARTITION WB_HASH_PART_2,\n" +
      "  PARTITION WB_HASH_PART_3,\n" +
      "  PARTITION WB_HASH_PART_4,\n" +
      "  PARTITION WB_HASH_PART_5\n" +
      ")";
    assertEquals(expected, reader.getSourceForTableDefinition().trim());
  }

  @Test
  public void testRetrieveDefaultSubPartition()
    throws Exception
  {
    if (!partitioningAvailable) return;
    WbConnection con = OracleTestUtil.getOracleConnection();
    assertNotNull("Oracle not available", con);

    TableIdentifier tbl = new DbObjectFinder(con).findTable(new TableIdentifier("RANGE_SUB_PART_HASH"));
    assertNotNull(tbl);
    OracleTablePartition reader = new OracleTablePartition(con, false);
    reader.retrieve(tbl, con);

    assertTrue(reader.isPartitioned());
    assertEquals("RANGE", reader.getPartitionType());
    List<String> columns = reader.getColumns();
    assertEquals(1, columns.size());
    List<OraclePartitionDefinition> partitions = reader.getPartitions();
    assertEquals(4, partitions.size());
    for (int i=0; i < 4; i++)
    {
      assertEquals("INVOICES_Q" + Integer.toString(i+1), partitions.get(i).getName());
      assertEquals(i + 1, partitions.get(i).getPosition());
      assertNotNull(partitions.get(i).getPartitionValue());
    }
    String expected =
      "PARTITION BY RANGE (INVOICE_DATE)\n" +
      "SUBPARTITION BY HASH (INVOICE_NO)\n" +
      "SUBPARTITIONS 8\n" +
      "(\n" +
      "  PARTITION INVOICES_Q1 VALUES LESS THAN (TO_DATE(' 2010-01-01 00:00:00', 'SYYYY-MM-DD HH24:MI:SS', 'NLS_CALENDAR=GREGORIAN')),\n" +
      "  PARTITION INVOICES_Q2 VALUES LESS THAN (TO_DATE(' 2010-04-01 00:00:00', 'SYYYY-MM-DD HH24:MI:SS', 'NLS_CALENDAR=GREGORIAN')),\n" +
      "  PARTITION INVOICES_Q3 VALUES LESS THAN (TO_DATE(' 2010-09-01 00:00:00', 'SYYYY-MM-DD HH24:MI:SS', 'NLS_CALENDAR=GREGORIAN')),\n" +
      "  PARTITION INVOICES_Q4 VALUES LESS THAN (TO_DATE(' 2011-01-01 00:00:00', 'SYYYY-MM-DD HH24:MI:SS', 'NLS_CALENDAR=GREGORIAN'))\n" +
      ")";
    String sql = reader.getSourceForTableDefinition().trim();
//    System.out.println(sql);
    assertEquals(expected, sql);
  }

  @Test
  public void testReferencePartition()
    throws Exception
  {
    if (!partitioningAvailable) return;

    WbConnection con = OracleTestUtil.getOracleConnection();
    assertNotNull("Oracle not available", con);

    TableIdentifier tbl = new DbObjectFinder(con).findTable(new TableIdentifier("WB_REF_PART_DETAIL"));
    assertNotNull(tbl);
    String source = tbl.getSource(con).toString();
//    System.out.println(source);
    assertTrue(source.contains("CONSTRAINT FK_DETAIL_MAIN FOREIGN KEY (MAIN_ID) REFERENCES WB_REF_PART_MAIN (ID)"));
    assertTrue(source.contains("PARTITION BY REFERENCE (FK_DETAIL_MAIN)"));
    assertTrue(source.contains("PARTITION PART_1"));
  }

}
