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
package workbench.storage;

import java.sql.Types;

import workbench.WbTestCase;
import workbench.resource.Settings;

import workbench.db.ColumnIdentifier;
import workbench.db.DbSettings;
import workbench.db.GeneratedColumnType;
import workbench.db.TableIdentifier;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class StatementFactoryTest
  extends WbTestCase
{
  public StatementFactoryTest()
  {
    super("StatementFactoryTest");
  }

  @Before
  public void setUp()
    throws Exception
  {
    Settings.getInstance().setDoFormatInserts(false);
    Settings.getInstance().setDoFormatUpdates(false);
  }

  @Test
  public void testValueExpression()
    throws Exception
  {
    // Make sure the datatype defines a valuetemplate
    Settings.getInstance().setProperty("workbench.db.testmodedeb.dmlexpression.inet", "cast(? as inet)");
    DbSettings forTest = new DbSettings("testmodedeb");

    ColumnIdentifier inetCol = new ColumnIdentifier("ip_address", java.sql.Types.OTHER);
    inetCol.setDbmsType("inet");
    inetCol.setIsPkColumn(true);

    ColumnIdentifier idCol = new ColumnIdentifier("id", java.sql.Types.INTEGER);
    idCol.setDbmsType("int8");

    ResultInfo info = new ResultInfo(new ColumnIdentifier[] { inetCol, idCol });
    RowData row = new RowData(info);

    // this is not the correct class for an inet column, but for testing purposed this is enough
    row.setValue(0, "127.0.0.1");
    row.setValue(1, Integer.valueOf(42));
    row.resetStatus();

    row.setValue(0, "127.0.0.2");
    row.setValue(1, Integer.valueOf(43));

    TableIdentifier table = new TableIdentifier("inet_test");
    info.setUpdateTable(table);
    boolean oldFormat = Settings.getInstance().getDoFormatUpdates();
    try
    {
      Settings.getInstance().setDoFormatUpdates(false);

      StatementFactory factory = new StatementFactory(info, null);
      factory.setTestSettings(forTest);
      DmlStatement dml = factory.createUpdateStatement(row, false, "\n");
      String sql = dml.getSql();
      String expected = "UPDATE inet_test SET ip_address = cast(? as inet), id = ? WHERE ip_address = cast(? as inet)";
      assertEquals(expected, sql);
      SqlLiteralFormatter formatter = new SqlLiteralFormatter();
      expected = "UPDATE inet_test SET ip_address = cast('127.0.0.2' as inet), id = 43 WHERE ip_address = cast('127.0.0.1' as inet)";
      String result = dml.getExecutableStatement(formatter, null).toString();
//      System.out.println("----------------\n" + result + "\n---------\n" + expected);
      assertEquals(expected, result);
    }
    finally
    {
      Settings.getInstance().setDoFormatUpdates(oldFormat);
    }
  }

  @Test
  public void testCreateUpdateStatement()
  {
    String[] cols = new String[] { "key", "section", "firstname", "lastname" };
    int[] types = new int[] { Types.INTEGER, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR };
    ResultInfo info = new ResultInfo(cols, types, null);
    info.setIsPkColumn(0, true);
    info.setIsPkColumn(1, true);
    TableIdentifier table = new TableIdentifier("person");

    info.setUpdateTable(table);
    StatementFactory factory = new StatementFactory(info, null);
    factory.setTrimCharacterValues(true);
    RowData data = new RowData(info.getColumnCount());
    data.setValue(0, 42);
    data.setValue(1, " start ");
    data.setValue(2, "Zaphod");
    data.setValue(3, "Bla");
    data.resetStatus();

    data.setValue(2, "Beeblebrox");

    DmlStatement stmt = factory.createUpdateStatement(data, false, "\n");
    String sql = stmt.toString();
    assertEquals(true, sql.startsWith("UPDATE"));

    SqlLiteralFormatter formatter = new SqlLiteralFormatter();
    sql = stmt.getExecutableStatement(formatter).toString();
    assertEquals(true, sql.contains("key = 42"));
    assertEquals(true, sql.contains("section = 'start'"));
  }

  @Test
  public void testCreateInsertStatement()
  {
    String[] cols = new String[] { "key", "firstname", "lastname" };
    int[] types = new int[] { Types.INTEGER, Types.VARCHAR, Types.VARCHAR };

    ResultInfo info = new ResultInfo(cols, types, null);
    info.setIsPkColumn(0, true);
    TableIdentifier table = new TableIdentifier("person");

    info.setUpdateTable(table);
    StatementFactory factory = new StatementFactory(info, null);
    factory.setTrimCharacterValues(true);
    RowData data = new RowData(3);
    data.setValue(0, 42);
    data.setValue(1, "Zaphod");
    data.setValue(2, "Beeblebrox  ");

    DmlStatement stmt = factory.createInsertStatement(data, false, "\n");
    String sql = stmt.toString();
    assertEquals("Not an INSERT statement", true, sql.startsWith("INSERT"));

    SqlLiteralFormatter formatter = new SqlLiteralFormatter();
    sql = stmt.getExecutableStatement(formatter).toString();
    assertEquals("Wrong values inserted", true, sql.contains("VALUES (42,'Zaphod','Beeblebrox')"));
  }

  @Test
  public void testCreateInsertIdentityStatement()
  {
    String[] cols = new String[] { "key", "firstname", "lastname" };
    int[] types = new int[] { Types.INTEGER, Types.VARCHAR, Types.VARCHAR };

    ResultInfo info = new ResultInfo(cols, types, null);
    info.setIsPkColumn(0, true);
    info.getColumn(0).setGeneratedColumnType(GeneratedColumnType.identity);
    TableIdentifier table = new TableIdentifier("person");

    info.setUpdateTable(table);
    StatementFactory factory = new StatementFactory(info, null);
    RowData data = new RowData(3);
    data.setValue(0, 42);
    data.setValue(1, "Zaphod");
    data.setValue(2, "Beeblebrox");

    factory.setIncludeGeneratedColumns(false);
    DmlStatement stmt = factory.createInsertStatement(data, false, "\n");
    String sql = stmt.toString();
    assertEquals("Not an INSERT statement", true, sql.startsWith("INSERT"));

    SqlLiteralFormatter formatter = new SqlLiteralFormatter();
    sql = stmt.getExecutableStatement(formatter).toString();
    assertEquals("Wrong values inserted", true, sql.contains("VALUES ('Zaphod','Beeblebrox')"));

    factory.setIncludeGeneratedColumns(true);
    stmt = factory.createInsertStatement(data, false, "\n");
    sql = stmt.getExecutableStatement(formatter).toString();
    assertEquals("Wrong values inserted", true, sql.contains("VALUES (42,'Zaphod','Beeblebrox')"));
  }

  @Test
  public void testCreateInsertGeneratedColumn()
  {
    String[] cols = new String[] { "price", "pieces", "total_price" };
    int[] types = new int[] { Types.DECIMAL, Types.INTEGER, Types.DECIMAL};

    ResultInfo info = new ResultInfo(cols, types, null);
    info.getColumn(2).setGeneratedExpression("(price * pieces)", GeneratedColumnType.computed);
    TableIdentifier table = new TableIdentifier("order_line");

    info.setUpdateTable(table);
    StatementFactory factory = new StatementFactory(info, null);
    factory.setIncludeGeneratedColumns(false);

    RowData data = new RowData(3);
    data.setValue(0, 42.1);
    data.setValue(1, 100);

    DmlStatement stmt = factory.createInsertStatement(data, false, "\n");
    String sql = stmt.toString();
    assertEquals("INSERT INTO order_line (price,pieces) VALUES (?,?)", sql.trim());
  }

  @Test
  public void testCreateDeleteStatement()
  {
    String[] cols = new String[] { "keycol", "value", "firstname", "lastname" };
    int[] types = new int[] { Types.INTEGER, Types.VARCHAR, Types.VARCHAR, Types.VARCHAR };

    ResultInfo info = new ResultInfo(cols, types, null);
    info.setIsPkColumn(0, true);
    info.setIsPkColumn(1, true);
    TableIdentifier table = new TableIdentifier("person");

    info.setUpdateTable(table);
    StatementFactory factory = new StatementFactory(info, null);
    RowData data = new RowData(info.getColumnCount());
    data.setValue(0, 42);
    data.setValue(1, "otherkey");
    data.setValue(2, "Zaphod");
    data.setValue(3, "Beeblebrox");
    data.resetStatus();

    DmlStatement stmt = factory.createDeleteStatement(data, false);
    String sql = stmt.toString();
    assertEquals("Not a delete statement", true, sql.startsWith("DELETE"));

    SqlLiteralFormatter formatter = new SqlLiteralFormatter();
    sql = stmt.getExecutableStatement(formatter).toString();
    assertEquals("Wrong WHERE clause created", true, sql.contains("keycol = 42"));
    assertEquals("Wrong WHERE clause created", true, sql.contains("value = 'otherkey'"));
  }
}
