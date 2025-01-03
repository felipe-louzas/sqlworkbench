/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer.
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
package workbench.db.h2database;

import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.DropType;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import org.junit.Test;

import static org.junit.Assert.*;

/*
 * @author Thomas Kellerer
 */
public class H2TableSourceBuilderTest
  extends WbTestCase
{

  public H2TableSourceBuilderTest()
  {
    super("H2TableSourceBuilderTest");
  }

  @Test
  public void testDomainSource()
    throws Exception
  {
    TestUtil util = new TestUtil(getName());
    WbConnection conn = util.getConnection("h2_ddl_test");
    String script =
      "CREATE DOMAIN positive_int AS integer CHECK (value > 0);\n" +
      "create table dtest (\n" +
      "  id integer primary key," +
      "  some_date date not null, " +
      "  some_number numeric(14,4)," +
      "  val positive_int not null" +
      ");\n" +
      "commit;";
    TestUtil.executeScript(conn, script);
    List<TableIdentifier> tables = conn.getMetadata().getTableList();
    assertEquals(1, tables.size());
    TableIdentifier tbl = tables.get(0);
    assertEquals("DTEST", tbl.getTableName());
    H2TableSourceBuilder builder = new H2TableSourceBuilder(conn);
    String ddl = builder.getTableSource(tbl, DropType.none, false, false);
//    System.out.println(ddl);
    assertTrue(ddl.contains("POSITIVE_INT    NOT NULL"));
    assertTrue(ddl.contains("-- domain POSITIVE_INT: INTEGER NOT NULL (VALUE > 0);"));
  }

}
