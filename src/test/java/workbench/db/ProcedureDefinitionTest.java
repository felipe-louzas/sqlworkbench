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
package workbench.db;


import java.sql.DatabaseMetaData;

import workbench.util.CollectionUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class ProcedureDefinitionTest
{

  public ProcedureDefinitionTest()
  {
  }

  @Before
  public void setUp()
  {
  }

  @After
  public void tearDown()
  {
  }

  @Test
  public void testGetNameForDrop()
  {
    ProcedureDefinition proc = new ProcedureDefinition("some_function", RoutineType.function);
    ColumnIdentifier col1 = new ColumnIdentifier("p1");
    col1.setArgumentMode("IN");
    col1.setDbmsType("integer");
    proc.setParameters(CollectionUtil.arrayList(col1));

    // Oracle way of dropping functions
    String dropName = proc.getObjectNameForDrop(null, false, false, false);
    assertEquals("some_function", dropName);

    // Postgres way of dropping functions
    dropName = proc.getObjectNameForDrop(null, true, false, false);
    assertEquals("some_function(integer)", dropName);

    ColumnIdentifier col2 = new ColumnIdentifier("p2");
    col2.setArgumentMode("INOUT");
    col2.setDbmsType("varchar");
    proc.setParameters(CollectionUtil.arrayList(col1, col2));

    dropName = proc.getObjectNameForDrop(null, true, false, false);
    assertEquals("some_function(integer,varchar)", dropName);

    ColumnIdentifier col3 = new ColumnIdentifier("p3");
    col3.setArgumentMode("OUT");
    col3.setDbmsType("integer");

    proc.setParameters(CollectionUtil.arrayList(col1, col2, col3));
    dropName = proc.getObjectNameForDrop(null, true, false, false);
    assertEquals("some_function(integer,varchar)", dropName);

    proc.setParameters(CollectionUtil.arrayList(col3, col2, col1));
    dropName = proc.getObjectNameForDrop(null, true, false, false);
    assertEquals("some_function(varchar,integer)", dropName);
  }

}
