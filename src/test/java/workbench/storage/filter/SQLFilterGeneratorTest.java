/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2024 Thomas Kellerer.
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
package workbench.storage.filter;

import java.sql.Types;
import java.time.LocalDate;
import java.util.List;

import workbench.db.ColumnIdentifier;
import workbench.db.DBID;
import workbench.db.DbSettings;

import workbench.storage.SqlLiteralFormatter;

import workbench.util.CollectionUtil;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class SQLFilterGeneratorTest
{

  @Test
  public void testSetup()
  {
    for (DBID id : DBID.values())
    {
      testDBMSSetup(id);
    }
  }

  private void testDBMSSetup(DBID dbms)
  {
    ComparatorFactory factory = new ComparatorFactory();
    List<ColumnComparator> comps = factory.getAvailableComparators();
    ColumnIdentifier name = new ColumnIdentifier("first_name", Types.VARCHAR);

    SqlLiteralFormatter formatter = new SqlLiteralFormatter(dbms.getId());
    DbSettings dbs = new DbSettings(dbms.getId());
    SQLFilterGenerator gen = new SQLFilterGenerator(formatter, dbs);

    for (ColumnComparator comp : comps)
    {
      String sql = gen.getSQLCondition(comp, name, "arthur", false);
      assertNotNull("No " + dbms.getId() + " template configured for " + comp.getClass().getSimpleName(), sql);
      assertTrue(sql.contains(name.getColumnName()));

      sql = gen.getSQLCondition(comp, name, "Arthur", true);
      assertNotNull("No " + dbms.getId() + " template configured for ignore case " + comp.getClass().getSimpleName() , sql);
      assertTrue(sql.contains(name.getColumnName()));
    }
  }

  @Test
  public void testAllColumns()
  {
    ContainsComparator comp = new ContainsComparator();
    ColumnIdentifier id = new ColumnIdentifier("id", Types.INTEGER);
    ColumnIdentifier fname = new ColumnIdentifier("first_name", Types.VARCHAR);
    ColumnIdentifier lname = new ColumnIdentifier("last_name", Types.VARCHAR);
    ColumnIdentifier dob = new ColumnIdentifier("dob", Types.DATE);
    dob.setDbmsType("date");
    ColumnIdentifier created = new ColumnIdentifier("created_at", Types.TIMESTAMP);
    created.setDbmsType("timestamp");
    List<ColumnIdentifier> columns = CollectionUtil.arrayList(id,fname,lname,dob,created);
    SqlLiteralFormatter formatter = new SqlLiteralFormatter(DBID.Postgres.getId());
    DbSettings dbs = new DbSettings(DBID.Postgres.getId());
    SQLFilterGenerator gen = new SQLFilterGenerator(formatter, dbs);
    String sql = gen.getSQLConditionForAll(comp, columns, "arthur", true);
    String expected =
      "(\n" +
      "  cast(id as text) ILIKE '%arthur%' OR\n" +
      "  first_name ILIKE '%arthur%' OR\n" +
      "  last_name ILIKE '%arthur%' OR\n" +
      "  cast(dob as text) ILIKE '%arthur%' OR\n" +
      "  cast(created_at as text) ILIKE '%arthur%'\n" +
      ")";
    assertEquals(expected, sql);
  }

  @Test
  public void testGeneration()
  {
    ContainsComparator contains = new ContainsComparator();
    GreaterThanComparator gt = new GreaterThanComparator();
    ColumnIdentifier name = new ColumnIdentifier("first_name", Types.VARCHAR);
    ColumnIdentifier nr = new ColumnIdentifier("nr", Types.INTEGER);
    ColumnIdentifier dob = new ColumnIdentifier("dob", Types.DATE);

    SqlLiteralFormatter formatter = new SqlLiteralFormatter(DBID.Postgres.getId());
    DbSettings dbs = new DbSettings(DBID.Postgres.getId());

    SQLFilterGenerator gen = new SQLFilterGenerator(formatter, dbs);

    String sql = gen.getSQLCondition(contains, name, "arthur", true);
    assertEquals("first_name ILIKE '%arthur%'", sql);

    sql = gen.getSQLCondition(gt, nr, 42, true);
    assertEquals("nr > 42", sql);

    sql = gen.getSQLCondition(gt, dob, LocalDate.of(2000,1,1), true);
    assertEquals("dob > DATE '2000-01-01'", sql);
  }

}
