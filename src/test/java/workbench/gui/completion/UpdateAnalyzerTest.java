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
package workbench.gui.completion;

import java.util.List;

import workbench.WbTestCase;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class UpdateAnalyzerTest
  extends WbTestCase
{
  public UpdateAnalyzerTest()
  {
    super("UpdateAnalyzerTest");
  }

  @Test
  public void testGetTargetTable()
  {
    String sql = "update public.foo set ";
    int pos = sql.indexOf("set ") + 4;
    UpdateAnalyzer check = new UpdateAnalyzer(null, sql, pos);
    check.checkContext();
    assertEquals(BaseAnalyzer.CONTEXT_COLUMN_LIST, check.getContext());
    assertEquals("foo", check.getTableForColumnList().getRawTableName());
    assertEquals("public", check.getTableForColumnList().getRawSchema());

    sql = "update \"public\".\"foo\" set ";
    pos = sql.indexOf("set ") + 4;
    check = new UpdateAnalyzer(null, sql, pos);
    check.checkContext();
    assertEquals(BaseAnalyzer.CONTEXT_COLUMN_LIST, check.getContext());
    assertEquals("foo", check.getTableForColumnList().getRawTableName());
    assertEquals("public", check.getTableForColumnList().getRawSchema());

    sql = "update public.\"Foo\" set ";
    pos = sql.indexOf("set ") + 4;
    check = new UpdateAnalyzer(null, sql, pos);
    check.checkContext();
    assertEquals(BaseAnalyzer.CONTEXT_COLUMN_LIST, check.getContext());
    assertEquals("Foo", check.getTableForColumnList().getRawTableName());
    assertEquals("public", check.getTableForColumnList().getRawSchema());
  }

  @Test
  public void testGetColumns()
  {
    String sql = "update foo set col_1 = 10, col_3 = y, col_5 = 'foobar'";

    int pos = sql.indexOf("10,");
    UpdateAnalyzer check = new UpdateAnalyzer(null, sql, pos);
    List<UpdateAnalyzer.ColumnInfo> cols = check.getColumns();
    assertEquals(3, cols.size());
    UpdateAnalyzer.ColumnInfo col = cols.get(0);
    assertEquals("10", sql.substring(col.valueStartPos, col.valueEndPos).trim());
    assertEquals("col_1", col.name);

    String currentColumn = check.getCurrentColumn();
    assertNotNull(currentColumn);
    assertEquals("col_1", currentColumn);

    sql = "update foo set col_2 = 42 where bar = 0;";
    pos = sql.indexOf("42 ");
    check = new UpdateAnalyzer(null, sql, pos);
    cols = check.getColumns();
    assertEquals(2, cols.size());
    currentColumn = check.getCurrentColumn();
    assertNotNull(currentColumn);
    assertEquals("col_2", currentColumn);

    sql = "update foo set col_2 =  where bar = 0;";
    pos = sql.indexOf(" = ") + 3;
    check = new UpdateAnalyzer(null, sql, pos);
    cols = check.getColumns();
    assertEquals(2, cols.size());
    currentColumn = check.getCurrentColumn();
    assertNotNull(currentColumn);
    assertEquals("col_2", currentColumn);

    sql = "update foo set col_3 = 42;";
    pos = sql.indexOf("42");
    check = new UpdateAnalyzer(null, sql, pos);
    cols = check.getColumns();
    assertEquals(1, cols.size());
    currentColumn = check.getCurrentColumn();
    assertNotNull(currentColumn);
    assertEquals("col_3", currentColumn);

    sql = "update foo set col_3 = 42 where bar =";
    pos = sql.indexOf("bar =") + "bar =".length();
    check = new UpdateAnalyzer(null, sql, pos);
    cols = check.getColumns();
    assertEquals(2, cols.size());
    currentColumn = check.getCurrentColumn();
    assertNotNull(currentColumn);
    assertEquals("bar", currentColumn);

    sql = "update foo set col_3 = 42 where bar <> ";
    pos = sql.indexOf("bar <>") + "bar <>".length();
    check = new UpdateAnalyzer(null, sql, pos);
    cols = check.getColumns();
    assertEquals(2, cols.size());
    currentColumn = check.getCurrentColumn();
    assertNotNull(currentColumn);
    assertEquals("bar", currentColumn);
  }

}
