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
package workbench.util;

import java.time.LocalDate;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class UpdateCheckTest
{

  @Test
  public void testNeedCheck()
  {
    UpdateCheck check = new UpdateCheck();
    int interval = 7;
    LocalDate last = LocalDate.of(2007, 3, 10);
    LocalDate now = LocalDate.of(2007, 3, 10);
    boolean need = check.needCheck(interval, now, last);
    assertFalse(need);

    now = LocalDate.of(2007, 3, 16);
    need = check.needCheck(interval, now, last);
    assertFalse(need);

    now = LocalDate.of(2007, 3, 17);
    need = check.needCheck(interval, now, last);
    assertTrue(need);

    need = check.needCheck(interval, now, null);
    assertTrue(need);

    now = LocalDate.of(2007, 3, 10);
    need = check.needCheck(1, now, last);
    assertFalse(need);

    now = LocalDate.of(2007, 3, 11);
    need = check.needCheck(1, now, last);
    assertTrue(need);

  }

}
