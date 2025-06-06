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
package workbench.util;

import workbench.db.TableIdentifier;

import org.junit.Test;

import static org.junit.Assert.*;

public class TableAliasTest
{

  @Test
  public void testAlias()
  {
    String value = "table1";
    TableAlias ta = new TableAlias(value);

    assertEquals("Wrong table name", "table1", ta.getTable().getTableName());
    assertEquals("Wrong value name", "table1", ta.getNameToUse());
    assertNull("value is not null", ta.getAlias());

    value = "table2 t1";
    ta = new TableAlias(value);

    assertEquals("Wrong table name", "table2", ta.getTable().getTableName());
    assertEquals("Wrong value name", "t1", ta.getNameToUse());

    value = "table1 as t1";
    ta = new TableAlias(value);

    assertEquals("Wrong table name", "table1", ta.getTable().getTableName());
    assertEquals("Wrong value name", "t1", ta.getNameToUse());
  }

  @Test
  public void testDB2Separator()
  {
    String value = "mylib/table1";
    TableAlias ta = new TableAlias(value, '/', '.');
    assertEquals("mylib/table1", ta.getObjectName());
    TableIdentifier table = ta.getTable();
    assertEquals("mylib", table.getCatalog());
    assertEquals("table1", table.getTableName());
  }

  @Test
  public void testCompare()
  {
    String value = "table1";
    TableAlias ta = new TableAlias(value);
    assertEquals("Not recognized as the same", true, ta.isTableOrAlias("table1", '.', '.', true));

    value = "table1 t1";
    ta = new TableAlias(value);
    assertEquals("Not recognized as the same", true, ta.isTableOrAlias("table1", '.', '.', true));
    assertEquals("Not recognized as the same", true, ta.isTableOrAlias("t1", '.', '.', true));
  }

}
