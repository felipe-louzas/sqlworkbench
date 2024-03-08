/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2024 Thomas Kellerer
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

package workbench.util;


import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class DurationUtilTest
{

  @Test
  public void testGetTime()
  {
    assertEquals(50, DurationUtil.parseDuration("50"));
    assertEquals(1000, DurationUtil.parseDuration(" 1s"));
    assertEquals(1000 * 60, DurationUtil.parseDuration(" 1 m "));
    assertEquals(1000 * 60 * 60 * 2, DurationUtil.parseDuration(" 2h"));
    assertEquals(1000 * 60 * 60 * 24, DurationUtil.parseDuration("1d"));
    assertEquals(1000 * 60 * 60 * 24 * 5, DurationUtil.parseDuration("5d"));
    assertEquals(0, DurationUtil.parseDuration("x"));
    assertEquals(0, DurationUtil.parseDuration(null));
  }

  @Test
  public void testIsValid()
  {
    assertTrue(DurationUtil.isValid("5d"));
    assertTrue(DurationUtil.isValid("100s"));
    assertTrue(DurationUtil.isValid("2h"));
    assertFalse(DurationUtil.isValid("42x"));
    assertFalse(DurationUtil.isValid("xyz"));
    assertFalse(DurationUtil.isValid(" "));
    assertFalse(DurationUtil.isValid(""));
  }

}
