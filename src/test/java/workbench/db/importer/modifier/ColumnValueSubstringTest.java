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
package workbench.db.importer.modifier;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class ColumnValueSubstringTest
{
  public ColumnValueSubstringTest()
  {
  }

  @Test
  public void testGetSubstring()
  {
    ColumnValueSubstring sub = new ColumnValueSubstring(5, 20);
    String s = sub.getSubstring("1");
    assertEquals("1", s);

    s = sub.getSubstring("1234567890");
    assertEquals("67890", s);

    s = sub.getSubstring("123456789012345678901234567890");
    assertEquals("678901234567890", s);

  }
}
