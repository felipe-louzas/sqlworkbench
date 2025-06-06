/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
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
package workbench.db.teradata;

import workbench.WbTestCase;

import workbench.db.IndexDefinition;
import workbench.db.TableIdentifier;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class TeradataIndexReaderTest
  extends WbTestCase
{
  public TeradataIndexReaderTest()
  {
    super("TeradataIndexReaderTest");
  }

  @Test
  public void testGetIndexSource()
  {
    TableIdentifier table = new TableIdentifier("FOO");
    IndexDefinition idx = new IndexDefinition(table, "IX_NAME");
    idx.addColumn("FIRSTNAME", null);
    idx.addColumn("LASTNAME", null);
    TeradataIndexReader instance = new TeradataIndexReader(null);
    CharSequence result = instance.getIndexSource(table, idx);
    assertNotNull(result);
    assertEquals("CREATE INDEX (FIRSTNAME, LASTNAME) ON FOO;", result.toString());
  }

}
