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

import workbench.WbTestCase;

import workbench.sql.VariablePool;

import workbench.util.ArgumentParser;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class ConditionCheckTest
  extends WbTestCase
{


  public ConditionCheckTest()
  {
    super("ConditionCheckTest");
  }


  @Test
  public void testIfEquals()
  {
    try
    {
      VariablePool.getInstance().clear();
      VariablePool.getInstance().setParameterValue("do_update", "y");

      ArgumentParser cmdLine = new ArgumentParser();
      ConditionCheck.addParameters(cmdLine);

      cmdLine.parse("-ifEquals=\"do_update=y\"");

      ConditionCheck.Result result = ConditionCheck.checkConditions(cmdLine, null);
      assertTrue(result.isOK());

      VariablePool.getInstance().setParameterValue("do_update", "n");
      result = ConditionCheck.checkConditions(cmdLine, null);
      assertFalse(result.isOK());
      assertEquals("do_update", result.getVariable());
      assertEquals("y", result.getExpectedValue());
      assertEquals(ConditionCheck.PARAM_IF_EQUALS, result.getFailedCondition());
      String msg = ConditionCheck.getMessage(WbInclude.VERB, result);
      assertEquals(WbInclude.VERB + " not executed because variable \"do_update\" is not equal to y", msg);
    }
    finally
    {
      VariablePool.getInstance().clear();
    }
  }

  @Test
  public void testIfNotEquals()
  {
    try
    {
      VariablePool.getInstance().clear();
      VariablePool.getInstance().setParameterValue("do_update", "y");

      ArgumentParser cmdLine = new ArgumentParser();
      ConditionCheck.addParameters(cmdLine);

      cmdLine.parse("-ifNotEquals=\"do_update=n\"");

      ConditionCheck.Result result = ConditionCheck.checkConditions(cmdLine, null);
      assertTrue(result.isOK());

      VariablePool.getInstance().setParameterValue("do_update", "n");
      result = ConditionCheck.checkConditions(cmdLine, null);
      assertFalse(result.isOK());
      assertEquals("do_update", result.getVariable());
      assertEquals("n", result.getExpectedValue());
      assertEquals(ConditionCheck.PARAM_IF_NOTEQ, result.getFailedCondition());
      String msg = ConditionCheck.getMessage(WbInclude.VERB, result);
      assertEquals(WbInclude.VERB + " not executed because variable \"do_update\" is equal to n", msg);
    }
    finally
    {
      VariablePool.getInstance().clear();
    }
  }

  @Test
  public void testIdDefined()
  {
    try
    {
      VariablePool.getInstance().clear();
      VariablePool.getInstance().setParameterValue("do_update", "x");

      ArgumentParser cmdLine = new ArgumentParser();
      ConditionCheck.addParameters(cmdLine);

      cmdLine.parse("-ifDefined=do_update");

      ConditionCheck.Result result = ConditionCheck.checkConditions(cmdLine, null);
      assertTrue(result.isOK());

      VariablePool.getInstance().setParameterValue("do_update", null);
      result = ConditionCheck.checkConditions(cmdLine, null);
      assertTrue(result.isOK());

      VariablePool.getInstance().removeVariable("do_update");
      result = ConditionCheck.checkConditions(cmdLine, null);
      assertFalse(result.isOK());
      assertEquals(ConditionCheck.PARAM_IF_DEF, result.getFailedCondition());
      String msg = ConditionCheck.getMessage(WbInclude.VERB, result);
      System.out.println(msg);
      assertEquals(WbInclude.VERB + " not executed because variable \"do_update\" is not defined", msg);
    }
    finally
    {
      VariablePool.getInstance().clear();
    }
  }
  @Test
  public void testIfNotEmpty()
  {
    try
    {
      VariablePool.getInstance().clear();
      VariablePool.getInstance().setParameterValue("do_update", "y");

      ArgumentParser cmdLine = new ArgumentParser();
      ConditionCheck.addParameters(cmdLine);

      cmdLine.parse("-ifNotEmpty=do_update");

      ConditionCheck.Result result = ConditionCheck.checkConditions(cmdLine, null);
      assertTrue(result.isOK());

      VariablePool.getInstance().setParameterValue("do_update", null);
      result = ConditionCheck.checkConditions(cmdLine, null);
      assertFalse(result.isOK());
      assertEquals("do_update", result.getVariable());
      assertNull(result.getExpectedValue());
      assertEquals(ConditionCheck.PARAM_IF_NOTEMPTY, result.getFailedCondition());
      String msg = ConditionCheck.getMessage(WbInclude.VERB, result);
      assertEquals(WbInclude.VERB + " not executed because variable \"do_update\" is empty or not defined", msg);
    }
    finally
    {
      VariablePool.getInstance().clear();
    }
  }

}
