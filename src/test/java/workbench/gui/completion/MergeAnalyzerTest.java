/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2024 Thomas Kellerer
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
package workbench.gui.completion;

import workbench.WbTestCase;

import workbench.db.TableIdentifier;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class MergeAnalyzerTest
  extends WbTestCase
{

  public MergeAnalyzerTest()
  {
    super("MergeAnalyzerTest");
  }

  @Test
  public void testUpdateColumnCompletion()
  {
    String sql =
      "merge into prod.target_table tt \n" +
      "  using staging.import_data as sd on (tt.id = sd.id) \n" +
      "when matched then update \n" +
      "  set c3 = sd. \n" +
      "when not matched then insert (c1, c2, c3, ) \n" +
      "values (sd. , sd.col_2, sd.col_3);";
    int pos = sql.indexOf("set c3 = sd.") + 12;
    StatementContext context = new StatementContext(null, sql, pos, false);
    MergeAnalyzer analyzer = (MergeAnalyzer)context.getAnalyzer();
    analyzer.checkContext();
    assertEquals(BaseAnalyzer.CONTEXT_COLUMN_LIST, analyzer.getContext());
    TableIdentifier tbl = analyzer.getTableForColumnList();
    assertEquals("import_data", tbl.getTableName());

    sql =
      "merge into prod.target_table tt \n" +
      "  using staging.import_data as sd on (tt.id = sd.id) \n" +
      "when matched then update \n" +
      "  set c3 = sd.col_3,  \n";
    pos = sql.indexOf("sd.col_3,") + 9;
    context = new StatementContext(null, sql, pos, false);
    analyzer = (MergeAnalyzer)context.getAnalyzer();
    analyzer.checkContext();
    assertEquals(BaseAnalyzer.CONTEXT_COLUMN_LIST, analyzer.getContext());
    tbl = analyzer.getTableForColumnList();
    assertEquals("target_table", tbl.getTableName());
  }

  @Test
  public void testInsertColumnCompletion()
  {
    String sql =
      "merge into prod.target_table tt \n" +
      "  using staging.import_data as sd on (tt.id = sd.id) \n" +
      "when matched then update \n" +
      "  set c3 = sd.col_1 \n" +
      "when not matched then insert (c1, c2, c3, ) \n" +
      "values (sd.);";
    int pos = sql.indexOf("(sd.") + 4;

    StatementContext context = new StatementContext(null, sql, pos, false);
    MergeAnalyzer analyzer = (MergeAnalyzer)context.getAnalyzer();
    analyzer.checkContext();
    assertEquals(BaseAnalyzer.CONTEXT_COLUMN_LIST, analyzer.getContext());
    TableIdentifier tbl = analyzer.getTableForColumnList();
    assertEquals("import_data", tbl.getTableName());

    pos = sql.indexOf("c2, c3, ") + 7;
    context = new StatementContext(null, sql, pos, false);
    analyzer = (MergeAnalyzer)context.getAnalyzer();
    analyzer.checkContext();
    assertEquals(BaseAnalyzer.CONTEXT_COLUMN_LIST, analyzer.getContext());
    TableIdentifier t2 = analyzer.getTableForColumnList();
    assertEquals("target_table", t2.getTableName());

    sql =
      "merge into prod.target_table tt \n" +
      "  using staging.import_data as sd on (tt.id = sd.id) \n" +
      "when matched then update \n" +
      "  set c3 = sd.col_1 \n" +
      "when not matched then insert ( ) \n" +
      "values (sd. , sd.col_2, sd.col_3);";
    pos = sql.indexOf("insert (") + 8;
    context = new StatementContext(null, sql, pos, false);
    analyzer = (MergeAnalyzer)context.getAnalyzer();
    analyzer.checkContext();
    assertEquals(BaseAnalyzer.CONTEXT_COLUMN_LIST, analyzer.getContext());
    TableIdentifier t3 = analyzer.getTableForColumnList();
    assertEquals("target_table", t3.getTableName());

  }

  @Test
  public void testTargetCompletion()
  {
    String sql = "merge into \n";
    int pos = sql.indexOf("into ") + 5;

    StatementContext context = new StatementContext(null, sql, pos, false);
    MergeAnalyzer analyzer = (MergeAnalyzer)context.getAnalyzer();
    analyzer.checkContext();
    assertEquals(BaseAnalyzer.CONTEXT_TABLE_LIST, analyzer.getContext());

    sql = "merge into  using (select foo) \n";
    pos = sql.indexOf("into ") + 5;

    context = new StatementContext(null, sql, pos, false);
    analyzer = (MergeAnalyzer)context.getAnalyzer();
    analyzer.checkContext();
    assertEquals(BaseAnalyzer.CONTEXT_TABLE_LIST, analyzer.getContext());
  }

  @Test
  public void testJoin()
  {
    String sql =
      "merge into prod.target_table tt \n" +
      "  using staging.import_data as sd on (tt.id = sd. ) \n" +
      "when matched then update \n" +
      "  set c3 = sd.col_1 \n" +
      "when not matched then insert (c1, c2, c3) \n" +
      "values (sd.col_1, sd.col_2, sd.col_3);";

    int pos = sql.indexOf("= sd.") + 5;
    StatementContext context = new StatementContext(null, sql, pos, false);
    MergeAnalyzer analyzer = (MergeAnalyzer)context.getAnalyzer();
    analyzer.checkContext();
    assertEquals(BaseAnalyzer.CONTEXT_COLUMN_LIST, analyzer.getContext());
    TableIdentifier tbl = analyzer.getTableForColumnList();
    assertEquals("import_data", tbl.getTableName());

    sql =
      "merge into prod.target_table tt \n" +
      "  using (select * from staging.import_data) as sd on sd.id = tt. ";

    pos = sql.indexOf("= tt.") + 5;
    context = new StatementContext(null, sql, pos, false);
    analyzer = (MergeAnalyzer)context.getAnalyzer();
    analyzer.checkContext();
    assertEquals(BaseAnalyzer.CONTEXT_COLUMN_LIST, analyzer.getContext());
    tbl = analyzer.getTableForColumnList();
    assertEquals("target_table", tbl.getTableName());

  }

  @Test
  public void testSimpleSource()
  {
    String sql =
      "merge into prod.target_table tt \n" +
      "  using staging.import_data as sd on (tt.id = sd.id) \n" +
      "when matched then update \n" +
      "  set c3 = sd. \n" +
      "when not matched then insert (c1, c2, c3) \n" +
      "values (sd.col_1, sd.col_2, sd.col_3);";

    int pos = sql.indexOf("sd. ") + 3;
    StatementContext context = new StatementContext(null, sql, pos, false);
    MergeAnalyzer analyzer = (MergeAnalyzer)context.getAnalyzer();
    analyzer.checkContext();
    TableIdentifier target = analyzer.getTargetTable();
    assertEquals("target_table", target.getTableName());
    assertEquals("prod", target.getSchema());
    assertEquals("tt", analyzer.getTargetAlias().getName());
  }

  @Test
  public void testQuerySource()
  {
    String sql =
      "merge into prod.target_table tt \n" +
      "  using (\n" +
      "    select imp. \n" +
      "    from staging.import_data imp \n" +
      "    where not processed \n" +
      "  ) as sd on (tt.id = sd.id) \n" +
      "when matched then update \n" +
      "  set c3 = sd. \n" +
      "when not matched then insert (c1, c2, c3) \n" +
      "values (sd.col_1, sd.col_2, sd.col_3);";

    int pos = sql.indexOf("imp.") + 4;
    StatementContext context = new StatementContext(null, sql, pos, false);
    BaseAnalyzer analyzer = context.getAnalyzer();
    analyzer.checkContext();
    assertEquals(BaseAnalyzer.CONTEXT_COLUMN_LIST, analyzer.getContext());
    TableIdentifier tbl = analyzer.getTableForColumnList();
    assertEquals("import_data", tbl.getTableName());
  }

}
