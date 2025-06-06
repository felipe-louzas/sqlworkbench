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

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class CteParserTest
{
  public CteParserTest()
  {
  }

  @Test
  public void testPg12()
  {
    String sql =
      "with cte (one, two) as materialized (" +
      "  select x,y from bar " +
      ") " +
      "select * from cte";
    CteParser analyzer = new CteParser(sql);
    List<CteDefinition> result = analyzer.getCteDefinitions();
    assertNotNull(result);
    assertEquals(1, result.size());
    assertTrue(result.get(0).getInnerSql().trim().startsWith("select x"));
    assertTrue(result.get(0).getName().equals("cte"));
    assertEquals("select * from cte", analyzer.getBaseSql());

    sql = sql.replace("materialized", "not materialized");
    analyzer = new CteParser(sql);
    result = analyzer.getCteDefinitions();
    assertNotNull(result);
    assertEquals(1, result.size());
    assertTrue(result.get(0).getInnerSql().trim().startsWith("select x"));
    assertTrue(result.get(0).getName().equals("cte"));
    assertEquals("select * from cte", analyzer.getBaseSql());
  }

  @Test
  public void testColDefs()
  {
    String cte =
      "with cte (one, two) as (" +
      "  select x,y from bar " +
      "), " +
      "other as ( " +
      "   select c.x as x2, c.y as y2, f.a \n" +
      "   from cte c \n" +
      "     join foo f on c.x = f.id \n" +
      ") " +
      "select * from other";

    CteParser analyzer = new CteParser(cte);
    List<CteDefinition> result = analyzer.getCteDefinitions();
    assertNotNull(result);
    assertEquals(2, result.size());
    CteDefinition t1 = result.get(0);
    assertEquals("cte", t1.getName());
    assertEquals(2, t1.getColumns().size());
    assertEquals("one", t1.getColumns().get(0).getColumnName());
    assertEquals("two", t1.getColumns().get(1).getColumnName());
    assertEquals("select x,y from bar", t1.getInnerSql().trim());
    assertEquals("select * from other", analyzer.getBaseSql().trim());
    assertEquals(t1.getStartInStatement(), cte.indexOf("as (") + 4);
    assertEquals("select x,y from bar", cte.substring(t1.getStartInStatement(), t1.getEndInStatement()).trim());
  }

  @Test
  public void testSplitCtes()
  {
    String cte = "with cte as (" +
      "  select x,y from bar " +
      "), " +
      "other as ( " +
      "   select c.x as x2, c.y as y2, f.a " +
      "   from cte c " +
      "     join foo f on c.x = f.id " +
      ") " +
      "select * from other";

    CteParser analyzer = new CteParser(cte);
    List<CteDefinition> result = analyzer.getCteDefinitions();
    assertNotNull(result);
    assertEquals(2, result.size());
    CteDefinition t1 = result.get(0);
    assertEquals("cte", t1.getName());
    assertEquals(2, t1.getColumns().size());
    assertEquals("x", t1.getColumns().get(0).getColumnName());
    assertEquals("y", t1.getColumns().get(1).getColumnName());

//    System.out.println(result.get(1));
//    System.out.println(cte.substring(result.get(1).getStartInStatement(), result.get(1).getEndInStatement()));

    CteDefinition t2 = result.get(1);
    assertEquals("other", t2.getName());
    assertEquals(3, t2.getColumns().size());
    assertEquals("x2", t2.getColumns().get(0).getColumnName());
    assertEquals("y2", t2.getColumns().get(1).getColumnName());
    assertEquals("a", t2.getColumns().get(2).getColumnName());
  }

  @Test
  public void testRecursive()
  {
    String cte =
      "with recursive cte as (" +
      "  select x, y from bar " +
      ") " +
      "select * from cte";

    CteParser analyzer = new CteParser(cte);
    List<CteDefinition> result = analyzer.getCteDefinitions();
    assertNotNull(result);
    assertEquals(1, result.size());
    CteDefinition t1 = result.get(0);
    assertEquals("cte", t1.getName());
    assertEquals(2, t1.getColumns().size());
    assertEquals("x", t1.getColumns().get(0).getColumnName());
    assertEquals("y", t1.getColumns().get(1).getColumnName());
  }

  @Test
  public void testWriteable()
  {
    String cte =
      "with new_rows (id, nr) as (" +
      "  insert into foo values (1,2) returning * " +
      ") " +
      "select * from new_rows";

    CteParser analyzer = new CteParser(cte);
    List<CteDefinition> result = analyzer.getCteDefinitions();
    assertNotNull(result);
    assertEquals(1, result.size());
    CteDefinition t1 = result.get(0);
    assertEquals("new_rows", t1.getName());
    assertEquals(2, t1.getColumns().size());
    assertEquals("id", t1.getColumns().get(0).getColumnName());
    assertEquals("nr", t1.getColumns().get(1).getColumnName());
    assertEquals("insert into foo values (1,2) returning *", t1.getInnerSql().trim());
  }


  @Test
  public void testWriteable2()
  {
    String cte =
      "with old_data as (\n" +
      "  delete from orders where id < 100 \n" +
      "  returning * \n" +
      ") \n" +
      "insert into archive select * from old_data";

    CteParser analyzer = new CteParser(cte);
    List<CteDefinition> result = analyzer.getCteDefinitions();
    assertNotNull(result);
    assertEquals(1, result.size());
    CteDefinition t1 = result.get(0);
    assertTrue(t1.getInnerSql().trim().startsWith("delete"));
    assertTrue(analyzer.getBaseSql().trim().startsWith("insert"));
  }


}
