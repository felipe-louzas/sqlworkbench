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

import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Thomas Kellerer
 */
public class AliasTest
{

  @Test
  public void testGetAlias()
  {
    Alias alias = new Alias("f123 as first_name");
    assertEquals("first_name", alias.getAlias());
    assertEquals("f123", alias.getObjectName());
    assertEquals("first_name", alias.getNameToUse());

    alias = new Alias("f123");
    assertNull(alias.getAlias());
    assertEquals("f123", alias.getNameToUse());
    assertEquals("f123", alias.getObjectName());

    alias = new Alias("some_schema.my_table as bla");
    assertEquals("bla", alias.getAlias());
    assertEquals("some_schema.my_table", alias.getObjectName());

    alias = new Alias("\"Imbecile Schema Name\".\"Daft table name\"");
    assertEquals("\"Imbecile Schema Name\".\"Daft table name\"", alias.getObjectName());
    assertNull(alias.getAlias());
  }


  @Test
  public void testEquals()
  {
    TableAlias t1 = new TableAlias("t1");
    TableAlias t2 = new TableAlias("t1 as foo");
    assertTrue(t1.equals(t1));
    assertTrue(t2.equals(t2));
    assertFalse(t2.equals(t1));
    assertFalse(t1.equals(t2));
    TableAlias t3 = new TableAlias("t1");
    assertTrue(t1.equals(t3));
  }
}
