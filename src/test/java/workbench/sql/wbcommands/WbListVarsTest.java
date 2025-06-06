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
package workbench.sql.wbcommands;

import org.junit.Before;
import org.junit.AfterClass;
import org.junit.Test;
import workbench.WbTestCase;
import workbench.sql.StatementRunnerResult;
import workbench.sql.VariablePool;
import workbench.storage.DataStore;
import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class WbListVarsTest
  extends WbTestCase
{

  public WbListVarsTest()
  {
    super("WbListVarsTest");
  }

  @AfterClass
  public static void tearDown()
  {
    VariablePool.getInstance().clear();
  }

  @Before
  public void setup()
  {
    VariablePool.getInstance().clear();
  }

  @Test
  public void testExecute()
    throws Exception
  {
    VariablePool.getInstance().setParameterValue("myvar", "42");
    VariablePool.getInstance().setParameterValue("another", "Arthur");
    WbListVars list = new WbListVars();
    assertFalse(list.isConnectionRequired());
    StatementRunnerResult result = list.execute(list.getVerb());
    assertNotNull(result);
    assertTrue(result.hasDataStores());
    assertEquals(1, result.getDataStores().size());
    DataStore ds = result.getDataStores().get(0);
    assertEquals(2, ds.getRowCount());

    // The result is sorted alphabetically
    assertEquals("another", ds.getValueAsString(0, 0));
    assertEquals("Arthur", ds.getValueAsString(0, 1));
    assertEquals("myvar", ds.getValueAsString(1, 0));
    assertEquals("42", ds.getValueAsString(1, 1));
  }

}
