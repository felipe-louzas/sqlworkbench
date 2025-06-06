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
package workbench.storage.filter;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class ColumnExpressionTest
{

  @Test
  public void testComparators()
  {
    Map<String, Object> values = new HashMap<String, Object>();
    values.put("firstname", "zaphod");
    values.put("lastname", "Beeblebrox");
    values.put("age", Integer.valueOf(43));
    values.put("spaceship", null);

    ColumnExpression col = new ColumnExpression("firstname", new IsNotNullComparator(), null);
    assertTrue(col.evaluate(values));

    col = new ColumnExpression("spaceship", new IsNullComparator(), null);
    assertTrue(col.evaluate(values));
    col = new ColumnExpression("firstname", new IsNullComparator(), null);
    assertFalse(col.evaluate(values));

    col = new ColumnExpression("spaceship", new IsNotNullComparator(), null);
    assertFalse(col.evaluate(values));
    col = new ColumnExpression("firstname", new IsNotNullComparator(), null);
    assertTrue(col.evaluate(values));

    col = new ColumnExpression("age", new LessOrEqualComparator(), Integer.valueOf(43));
    assertTrue(col.evaluate(values));
    col = new ColumnExpression("age", new LessOrEqualComparator(), Integer.valueOf(10));

    assertFalse(col.evaluate(values));
    col = new ColumnExpression("age", new LessThanComparator(), Integer.valueOf(100));
    assertTrue(col.evaluate(values));
    col = new ColumnExpression("age", new LessThanComparator(), Integer.valueOf(43));
    assertFalse(col.evaluate(values));

    col = new ColumnExpression("age", new NumberNotEqualsComparator(), Integer.valueOf(100));
    assertTrue(col.evaluate(values));
    col = new ColumnExpression("age", new NumberNotEqualsComparator(), Integer.valueOf(43));
    assertFalse(col.evaluate(values));

    col = new ColumnExpression("age", new NumberEqualsComparator(), Integer.valueOf(43));
    assertTrue(col.evaluate(values));
    col = new ColumnExpression("age", new NumberEqualsComparator(), Integer.valueOf(44));
    assertFalse(col.evaluate(values));

    col = new ColumnExpression("firstname", new NotStartsWithComparator(), "Tricia");
    col.setIgnoreCase(true);
    assertTrue(col.evaluate(values));

    col = new ColumnExpression("firstname", new NotStartsWithComparator(), "Zaphod");
    col.setIgnoreCase(false);
    assertTrue(col.evaluate(values));

    col.setIgnoreCase(true);
    assertFalse(col.evaluate(values));

    col = new ColumnExpression("firstname", new ContainsComparator(), "Pho");
    col.setIgnoreCase(true);
    assertTrue(col.evaluate(values));
    col.setIgnoreCase(false);
    assertFalse(col.evaluate(values));


    col = new ColumnExpression("firstname", new ContainsNotComparator(), "Pho");
    col.setIgnoreCase(true);
    assertFalse(col.evaluate(values));
    col.setIgnoreCase(false);
    assertTrue(col.evaluate(values));

    col = new ColumnExpression("firstname", new StringEqualsComparator(), "Zaphod");
    col.setIgnoreCase(true);
    assertTrue(col.evaluate(values));
    col.setIgnoreCase(false);
    assertFalse(col.evaluate(values));

    col = new ColumnExpression("firstname", new StringEqualsComparator(), "zaphod");
    col.setIgnoreCase(false);
    assertTrue(col.evaluate(values));
    col.setIgnoreCase(true);
    assertTrue(col.evaluate(values));

    col = new ColumnExpression("firstname", new StringNotEqualsComparator(), "Zaphod");
    col.setIgnoreCase(false);
    assertTrue(col.evaluate(values));
    col.setIgnoreCase(true);
    assertFalse(col.evaluate(values));
  }

  @Test
  public void testDateComparison()
    throws Exception
  {
    SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd");
    Map<String, Object> values = new HashMap<>();
    values.put("changed_on", f.parse("2006-11-01"));

    ColumnExpression expr = new ColumnExpression("changed_on", new GreaterThanComparator(), f.parse("2006-10-01"));
    assertTrue(expr.evaluate(values));

    expr = new ColumnExpression("changed_on", new DateEqualsComparator(), f.parse("2006-11-01"));
    assertTrue(expr.evaluate(values));

    expr = new ColumnExpression("changed_on", new DateEqualsComparator(), f.parse("2006-11-02"));
    assertFalse(expr.evaluate(values));

    expr = new ColumnExpression("changed_on", new LessThanComparator(), f.parse("2006-11-02"));
    assertTrue(expr.evaluate(values));

    expr = new ColumnExpression("changed_on", new LessThanComparator(), f.parse("2006-10-20"));
    assertFalse(expr.evaluate(values));
  }

}
