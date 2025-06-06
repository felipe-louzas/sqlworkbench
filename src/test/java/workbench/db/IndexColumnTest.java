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
package workbench.db;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class IndexColumnTest
{

  @Test
  public void testEquals()
  {
    IndexColumn col1 = new IndexColumn("name", "ASC");
    IndexColumn col2 = new IndexColumn("name", "asc");
    assertTrue(col1.equals(col2));
    assertTrue(col2.equals(col1));

    col1 = new IndexColumn("name", null);
    col2 = new IndexColumn("name", null);
    assertTrue(col1.equals(col2));
    assertTrue(col2.equals(col1));

    col1 = new IndexColumn("name", "asc");
    col2 = new IndexColumn("name", "desc");
    assertFalse(col1.equals(col2));
    assertFalse(col2.equals(col1));
  }


}
