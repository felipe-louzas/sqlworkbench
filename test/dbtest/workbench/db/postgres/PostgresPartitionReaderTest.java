/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2020 Thomas Kellerer.
 *
 * Licensed under a modified Apache License, Version 2.0 (the "License")
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
package workbench.db.postgres;

import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.JdbcUtils;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;


/**
 *
 * @author Thomas Kellerer
 */
public class PostgresPartitionReaderTest
  extends WbTestCase
{
  private final static String TESTID = "part_test";

  public PostgresPartitionReaderTest()
  {
    super("PgPartitionTest");
  }

  @Before
  public void setUpClass()
    throws Exception
  {
    PostgresTestUtil.initTestCase(TESTID);
  }

  @After
  public void tearDownClass()
    throws Exception
  {
    PostgresTestUtil.cleanUpTestCase();
  }


  @Test
  public void testRangePartition()
    throws Exception
  {
    WbConnection conn = PostgresTestUtil.getPostgresConnection();
    if (!JdbcUtils.hasMinimumServerVersion(conn, "10")) return;

    String sql =
      "create table range_table\n" +
      "(\n" +
      "  id integer not null,\n" +
      "  order_date date not null,\n" +
      "  amount integer not null\n" +
      ")\n" +
      "partition by range (order_date);\n" +
      "\n" +
      "create table range_table_p1 \n" +
      "  partition of range_table\n" +
      "  for values from (minvalue) to (date '2018-01-01');\n" +
      "  \n" +
      "create table range_table_p2\n" +
      "  partition of range_table\n" +
      "  for values from (date '2018-01-01') to (date '2019-01-01');\n" +
      "\n" +
      "create table range_table_p3\n" +
      "  partition of range_table\n" +
      "  for values from (date '2019-01-01') to (date '2020-01-01');\n" +
      "commit;";

    TestUtil.executeScript(conn, sql);

    TableIdentifier rangeTable = conn.getMetadata().findTable(new TableIdentifier(TESTID + ".range_table"));

    PostgresPartitionReader reader = new PostgresPartitionReader(rangeTable, conn);
    reader.readPartitionInformation();
    assertEquals("PARTITION BY RANGE (order_date)", reader.getPartitionDefinition());
    assertEquals("RANGE", reader.getStrategy());
    List<PostgresPartition> partitions = reader.getTablePartitions();
    assertNotNull(partitions);
    assertEquals(3, partitions.size());
    assertEquals("range_table_p1", partitions.get(0).getName());
    assertEquals("FOR VALUES FROM (MINVALUE) TO ('2018-01-01')", partitions.get(0).getDefinition());
    assertEquals("range_table_p2", partitions.get(1).getName());
    assertEquals("FOR VALUES FROM ('2018-01-01') TO ('2019-01-01')", partitions.get(1).getDefinition());
    assertEquals("range_table_p3", partitions.get(2).getName());
    assertEquals("FOR VALUES FROM ('2019-01-01') TO ('2020-01-01')", partitions.get(2).getDefinition());

    String ddl = reader.getCreatePartitions();
    assertTrue(ddl.contains("CREATE TABLE range_table_p1"));
    assertTrue(ddl.contains("CREATE TABLE range_table_p2"));
    assertTrue(ddl.contains("CREATE TABLE range_table_p3"));
  }

  @Test
  public void testListPartition()
    throws Exception
  {
    WbConnection conn = PostgresTestUtil.getPostgresConnection();
    if (!JdbcUtils.hasMinimumServerVersion(conn, "10")) return;

    String sql =
      "create table list_table\n" +
      "(\n" +
      "  id integer not null,\n" +
      "  code integer not null\n" +
      ")\n" +
      "partition by list (code);\n" +
      "\n" +
      "create table list_table_p1 \n" +
      "  partition of list_table\n" +
      "  for values in (1,2,3,4);\n" +
      "  \n" +
      "create table list_table_p2 \n" +
      "  partition of list_table\n" +
      "  for values in (5,6,7,8);\n" +
      "commit;";

    TestUtil.executeScript(conn, sql);

    TableIdentifier rangeTable = conn.getMetadata().findTable(new TableIdentifier(TESTID + ".list_table"));

    PostgresPartitionReader reader = new PostgresPartitionReader(rangeTable, conn);
    reader.readPartitionInformation();
    assertEquals("LIST", reader.getStrategy());
    assertEquals("PARTITION BY LIST (code)", reader.getPartitionDefinition());
    List<PostgresPartition> partitions = reader.getTablePartitions();
    assertNotNull(partitions);
    assertEquals(2, partitions.size());
    assertEquals("list_table_p1", partitions.get(0).getName());
    assertEquals("FOR VALUES IN (1, 2, 3, 4)", partitions.get(0).getDefinition());
    assertEquals("list_table_p2", partitions.get(1).getName());
    assertEquals("FOR VALUES IN (5, 6, 7, 8)", partitions.get(1).getDefinition());

    String ddl = reader.getCreatePartitions();
    assertTrue(ddl.contains("CREATE TABLE list_table_p1"));
    assertTrue(ddl.contains("CREATE TABLE list_table_p2"));
  }

  @Test
  public void testHashPartition()
    throws Exception
  {
    WbConnection conn = PostgresTestUtil.getPostgresConnection();
    if (!JdbcUtils.hasMinimumServerVersion(conn, "11")) return;

    String sql =
      "create table hash_table\n" +
      "(\n" +
      "  id integer not null,\n" +
      "  code integer not null\n" +
      ")\n" +
      "partition by hash (code);\n" +
      "\n" +
      "create table hash_table_p01 \n" +
      "  partition of hash_table\n" +
      "  for values with (modulus 2, remainder 0);\n" +
      "\n" +
      "create table hash_table_p02\n" +
      "  partition of hash_table\n" +
      "  for values with (modulus 2, remainder 1);\n" +
      "commit;";

    TestUtil.executeScript(conn, sql);

    TableIdentifier rangeTable = conn.getMetadata().findTable(new TableIdentifier(TESTID + ".hash_table"));

    PostgresPartitionReader reader = new PostgresPartitionReader(rangeTable, conn);
    reader.readPartitionInformation();
    assertEquals("HASH", reader.getStrategy());
    assertEquals("PARTITION BY HASH (code)", reader.getPartitionDefinition());
    List<PostgresPartition> partitions = reader.getTablePartitions();
    assertNotNull(partitions);
    assertEquals(2, partitions.size());
    assertEquals("hash_table_p01", partitions.get(0).getName());
    assertEquals("FOR VALUES WITH (modulus 2, remainder 0)", partitions.get(0).getDefinition());
    assertEquals("hash_table_p02", partitions.get(1).getName());
    assertEquals("FOR VALUES WITH (modulus 2, remainder 1)", partitions.get(1).getDefinition());

    String ddl = reader.getCreatePartitions();
    assertTrue(ddl.contains("CREATE TABLE hash_table_p01"));
    assertTrue(ddl.contains("CREATE TABLE hash_table_p02"));
  }

}
