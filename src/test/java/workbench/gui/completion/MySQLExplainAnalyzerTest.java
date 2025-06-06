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
import org.junit.Test;
import workbench.WbTestCase;
import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class MySQLExplainAnalyzerTest
  extends WbTestCase
{

  public MySQLExplainAnalyzerTest()
  {
    super("MySQLExplainAnalyzerTest");
  }

  @Test
  public void testCheckContext()
  {
    String sql = "explain ";
    MySQLExplainAnalyzer analyzer = new MySQLExplainAnalyzer(null, sql, 8);
    assertNull(analyzer.getExplainedStatement());

    analyzer.checkContext();
    List options = analyzer.getData();
    assertNotNull(options);
    assertEquals(2, options.size());

    sql = "explain extended ";
    analyzer = new MySQLExplainAnalyzer(null, sql, sql.length() - 1);
    analyzer.checkContext();
    options = analyzer.getData();
    assertNotNull(options);
    assertEquals(0, options.size());

    sql = "explain extended ";
    analyzer = new MySQLExplainAnalyzer(null, sql, 8);
    analyzer.checkContext();
    options = analyzer.getData();
    assertNotNull(options);
    assertEquals(0, options.size());
  }

  @Test
  public void testGetExplainedStatement()
  {
    String sql = "explain select * from person";
    MySQLExplainAnalyzer analyzer = new MySQLExplainAnalyzer(null, sql, 1);
    assertNotNull(analyzer.getExplainedStatement());

    sql = "explain ";
    analyzer = new MySQLExplainAnalyzer(null, sql, 1);
    assertNull(analyzer.getExplainedStatement());
  }
}
