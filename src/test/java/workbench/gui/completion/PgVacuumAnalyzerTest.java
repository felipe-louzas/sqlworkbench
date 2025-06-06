/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer.
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

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class PgVacuumAnalyzerTest
{

  @Test
  public void testOldSyntax()
  {
    String sql = "vacuum full analyze ";
    int pos = sql.length();
    PgVacuumAnalyzer analyzer = new PgVacuumAnalyzer(null, sql, pos);
    analyzer.checkContext();
    assertEquals(BaseAnalyzer.CONTEXT_TABLE_LIST, analyzer.getContext());
  }

  @Test
  public void testGetOptions()
  {
    String sql = "vacuum ( ) ";
    int pos = sql.indexOf('(') + 1;
    PgVacuumAnalyzer analyzer = new PgVacuumAnalyzer(null, sql, pos);
    analyzer.checkContext();
    assertEquals(BaseAnalyzer.CONTEXT_VALUE_LIST, analyzer.getContext());
    analyzer.buildResult();
    List options = analyzer.getData();
    assertTrue(options.contains("FULL"));
    assertTrue(options.contains("ANALYZE"));

    pos = sql.indexOf(')') + 1;
    analyzer = new PgVacuumAnalyzer(null, sql, pos);
    analyzer.checkContext();
    assertEquals(BaseAnalyzer.CONTEXT_TABLE_LIST, analyzer.getContext());
  }

}
