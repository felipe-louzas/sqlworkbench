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
package workbench.db;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class ObjectExpressionParserTest
{

  public ObjectExpressionParserTest()
  {
  }

  @Test
  public void testGetParts()
  {
    String id = "foobar";
    ObjectExpressionParser p = new ObjectExpressionParser("foobar", '.', '.');
    assertNull(p.getCatalog());

    p = new ObjectExpressionParser("foobar", ':', ':');
    assertNull(p.getCatalog());

    p = new ObjectExpressionParser("foo.bar", '.', '.', true, false);
    assertEquals("foo", p.getCatalog());
    assertEquals("bar", p.getName());

    p = new ObjectExpressionParser("foo.bar", '.', ':', true, false);
    assertEquals("foo", p.getCatalog());

    p = new ObjectExpressionParser("foo/bar", '.', '/');
    assertEquals("bar", p.getName());

    p = new ObjectExpressionParser("foo/bar.tbl", '.', '/');
    assertEquals("bar.tbl", p.getName());

    ObjectExpressionParser tbl = new ObjectExpressionParser("RICH/\"FOO.BAR\"", '/', '/');
    assertEquals("RICH", tbl.getSchema());
    assertEquals("\"FOO.BAR\"", tbl.getName());
    assertNull(tbl.getCatalog());
  }

  @Test
  public void testTwoElement()
  {
    ObjectExpressionParser tbl = new ObjectExpressionParser("cat.table", '.', '.', true, false);
    assertNull(tbl.getSchema());
    assertEquals("cat", tbl.getCatalog());
    assertEquals("table", tbl.getName());

    tbl = new ObjectExpressionParser("schema.table", '.', '.', false, true);
    assertNull(tbl.getCatalog());
    assertEquals("schema", tbl.getSchema());
    assertEquals("table", tbl.getName());

    tbl = new ObjectExpressionParser("schema.table", '.', '.', true, true);
    assertNull(tbl.getCatalog());
    assertEquals("schema", tbl.getSchema());
    assertEquals("table", tbl.getName());
  }

  @Test
  public void testAlternateSeparator()
  {
    ObjectExpressionParser tbl = new ObjectExpressionParser("somelib/sometable", '/', '/');
    assertEquals("somelib", tbl.getSchema());
    assertEquals("sometable", tbl.getName());

    tbl = new ObjectExpressionParser("somelib/sometable", '.', '.');
    assertNull(tbl.getSchema());
    assertEquals("somelib/sometable", tbl.getName());

    tbl = new ObjectExpressionParser("somelib:someschema.tablename", ':', '.');
    assertEquals("somelib", tbl.getCatalog());
    assertEquals("someschema", tbl.getSchema());
    assertEquals("tablename", tbl.getName());
  }

}
