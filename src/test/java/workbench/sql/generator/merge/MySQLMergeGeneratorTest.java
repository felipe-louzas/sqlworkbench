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
package workbench.sql.generator.merge;

import java.sql.Types;
import java.util.List;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import workbench.WbTestCase;

import static org.junit.Assert.*;

import workbench.db.ColumnIdentifier;
import workbench.db.TableIdentifier;

import workbench.storage.DataStore;
import workbench.storage.ResultInfo;

import workbench.util.CollectionUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class MySQLMergeGeneratorTest
  extends WbTestCase
{
  public MySQLMergeGeneratorTest()
  {
    super("MySQLMergeGeneratorTest");
  }

  @BeforeClass
  public static void setUpClass()
  {
  }

  @AfterClass
  public static void tearDownClass()
  {
  }

  @Before
  public void setUp()
  {
  }

  @After
  public void tearDown()
  {
  }
  /**
   * Test of generateMerge method, of class MySQLMergeGenerator.
   */
  @Test
  public void testGenerateMerge()
  {
    ColumnIdentifier id = new ColumnIdentifier("id", Types.INTEGER);
    id.setIsPkColumn(true);
    ColumnIdentifier fname = new ColumnIdentifier("fname", Types.VARCHAR);
    ColumnIdentifier lname = new ColumnIdentifier("lname", Types.VARCHAR);
    ResultInfo info = new ResultInfo(new ColumnIdentifier[] { id, fname, lname });

    TableIdentifier tbl = new TableIdentifier("person");
    info.setUpdateTable(tbl);
    DataStore ds = new DataStore(info);
    ds.forceUpdateTable(tbl);
    int row = ds.addRow();
    ds.setValue(row, 0, Integer.valueOf(42));
    ds.setValue(row, 1, "Arthur");
    ds.setValue(row, 2, "Dent");

    row = ds.addRow();
    ds.setValue(row, 0, Integer.valueOf(24));
    ds.setValue(row, 1, "Ford");
    ds.setValue(row, 2, "Prefect");
    MySQLMergeGenerator generator = new MySQLMergeGenerator();
    String sql = generator.generateMerge(ds);

    String expected =
      "INSERT INTO person\n" +
      "  (id, fname, lname)\n" +
      "VALUES\n" +
      "  (42,'Arthur','Dent'),\n" +
      "  (24,'Ford','Prefect')\n" +
      "ON DUPLICATE KEY UPDATE\n" +
      "  fname = values(fname),\n" +
      "  lname = values(lname);";
    assertEquals(expected, sql);

    List<ColumnIdentifier> cols = CollectionUtil.arrayList(id, lname);
    generator.setColumns(cols);
    String sql2 = generator.generateMerge(ds);
//    System.out.println(sql2);
    String expected2 =
      "INSERT INTO person\n" +
      "  (id, lname)\n" +
      "VALUES\n" +
      "  (42,'Dent'),\n" +
      "  (24,'Prefect')\n" +
      "ON DUPLICATE KEY UPDATE\n" +
      "  lname = values(lname);";
    assertEquals(expected2, sql2);
  }

  @Test
  public void testIncremental()
  {
    ColumnIdentifier id = new ColumnIdentifier("id", Types.INTEGER);
    id.setIsPkColumn(true);
    ColumnIdentifier fname = new ColumnIdentifier("fname", Types.VARCHAR);
    ColumnIdentifier lname = new ColumnIdentifier("lname", Types.VARCHAR);
    ResultInfo info = new ResultInfo(new ColumnIdentifier[] { id, fname, lname });

    TableIdentifier tbl = new TableIdentifier("person");
    info.setUpdateTable(tbl);
    DataStore ds = new DataStore(info);
    ds.forceUpdateTable(tbl);
    int row = ds.addRow();
    ds.setValue(row, 0, Integer.valueOf(42));
    ds.setValue(row, 1, "Arthur");
    ds.setValue(row, 2, "Dent");

    row = ds.addRow();
    ds.setValue(row, 0, Integer.valueOf(24));
    ds.setValue(row, 1, "Ford");
    ds.setValue(row, 2, "Prefect");

    MySQLMergeGenerator generator = new MySQLMergeGenerator();
    StringBuilder result = new StringBuilder(100);
    String part = generator.generateMergeStart(ds);

    result.append(part);
    part = generator.addRow(info, ds.getRow(0), 0);
    result.append(part);

    part = generator.addRow(info, ds.getRow(1), 1);
    result.append(part);

    part = generator.generateMergeEnd(ds);
    result.append(part);

    String expected =
      "INSERT INTO person\n" +
      "  (id, fname, lname)\n" +
      "VALUES\n" +
      "  (42,'Arthur','Dent'),\n" +
      "  (24,'Ford','Prefect')\n" +
      "ON DUPLICATE KEY UPDATE\n" +
      "  fname = values(fname),\n" +
      "  lname = values(lname);";
    assertEquals(expected, result.toString());
  }
}
