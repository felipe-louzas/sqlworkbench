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
package workbench.db.duckdb;

import java.sql.SQLException;
import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.ProcedureDefinition;
import workbench.db.WbConnection;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class DuckDbMacroReaderTest
  extends WbTestCase
{

  public DuckDbMacroReaderTest()
  {
    super("DuckDbMacroReaderTest");
  }

  @Test
  public void testRetrieve()
    throws SQLException
  {
    WbConnection conn = DuckDbTestUtil.getDuckConnection();
    assertNotNull(conn);
    TestUtil.executeScript(conn,
      "create macro add_vals(a, b) as a + b;\n" +
      "CREATE MACRO show_data(id1, id2) AS TABLE \n" +
      "SELECT id1 as id\n" +
      "union all\n" +
      "select id2;\n" +
      "comment on macro table show_data is 'Display static data';");

    DuckDbMacroReader reader = new DuckDbMacroReader();

    List<ProcedureDefinition> macros = reader.getMacros(conn, null, "%", "%");
    assertEquals(2, macros.size());
    ProcedureDefinition add = macros.get(0);
    assertEquals("add_vals", add.getObjectName());
    assertEquals("CREATE MACRO main.add_vals(a, b) AS (a + b);", add.getSource().toString());

    ProcedureDefinition show = macros.get(1);
    assertEquals("show_data", show.getObjectName());
    String src = show.getSource().toString();
    assertTrue(src.startsWith("CREATE MACRO main.show_data(id1, id2) AS TABLE"));
    assertTrue(src.contains("(SELECT id1 AS id) UNION ALL (SELECT id2);"));
    assertTrue(src.contains("COMMENT ON MACRO TABLE main.show_data IS 'Display static data';"));
  }


}
