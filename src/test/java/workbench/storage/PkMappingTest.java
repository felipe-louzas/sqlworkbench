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
package workbench.storage;

import java.util.List;

import workbench.TestUtil;

import workbench.db.TableIdentifier;

import workbench.util.WbFile;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class PkMappingTest
{

  @Test
  public void testMapping()
  {
    TestUtil util = new TestUtil("PkMappingTest");
    WbFile f = new WbFile(util.getBaseDir(), "mapping_test.properties");
    PkMapping map = new PkMapping(f);
    TableIdentifier tbl = new TableIdentifier("PERSON");
    map.addMapping(tbl, "id1,id2");

    TableIdentifier tbl2 = new TableIdentifier("person");
    List<String> col = map.getPKColumns(tbl2);
    assertEquals(2, col.size());
    assertEquals("id1", col.get(0));
    assertEquals("id2", col.get(1));
  }
}
