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

import workbench.WbTestCase;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class DurationFormatterTest
  extends WbTestCase
{
  public DurationFormatterTest()
  {
    super("DurationFormatterTest");
  }

  @Test
  public void testFixedFormat()
  {
    DurationFormatter f = new DurationFormatter('.');

    long millis = 1234;
    assertEquals("1234ms", f.formatDuration(millis, DurationFormat.millis, true));
    assertEquals("1234ms", f.formatDuration(millis, DurationFormat.millis, false));

    millis = DurationFormatter.ONE_SECOND + 500;
    assertEquals("1.5s", f.formatDuration(millis, DurationFormat.seconds, false));
    assertEquals("1.5s", f.formatDuration(millis, DurationFormat.seconds, true));

    assertEquals("1.75s", f.formatDuration(DurationFormatter.ONE_SECOND + 750, DurationFormat.seconds, true));
    assertEquals("0.25s", f.formatDuration(250, DurationFormat.seconds, true));
  }

  @Test
  public void testDynamicFormat()
  {
    DurationFormatter f = new DurationFormatter('.');
    long millis = DurationFormatter.ONE_SECOND + 500;
    String s = f.getDurationAsSeconds(millis);
    assertEquals("1.5s", s);

    millis = DurationFormatter.ONE_SECOND * 102 + (DurationFormatter.ONE_SECOND / 2);
    s = f.getDurationAsSeconds(millis);
    assertEquals("102.5s", s);

    millis = DurationFormatter.ONE_HOUR * 3 + DurationFormatter.ONE_MINUTE * 12 + DurationFormatter.ONE_SECOND * 12 + 300;
    s = f.formatDuration(millis, DurationFormat.dynamic, true);
    assertEquals("3h 12m 12.3s", s);

    millis = DurationFormatter.ONE_HOUR * 26 + DurationFormatter.ONE_MINUTE * 12 + DurationFormatter.ONE_SECOND * 12 + 300;
    s = f.formatDuration(millis, DurationFormat.dynamic, true);
    assertEquals("26h 12m 12.3s", s);

    millis = DurationFormatter.ONE_MINUTE * 59 + DurationFormatter.ONE_SECOND * 59;
    s = f.formatDuration(millis, DurationFormat.dynamic, true);
    assertEquals("59m 59s", s);

    millis += DurationFormatter.ONE_SECOND;
    s = f.formatDuration(millis, DurationFormat.dynamic, true);
    assertEquals("1h 0m 0s", s);

    millis += 500;
    s = f.formatDuration(millis, DurationFormat.dynamic, true);
    assertEquals("1h 0m 0.5s", s);

    millis = DurationFormatter.ONE_MINUTE * 60 + DurationFormatter.ONE_SECOND * 59;
    s = f.formatDuration(millis, DurationFormat.dynamic, true);
    assertEquals("1h 0m 59s", s);

    millis = DurationFormatter.ONE_SECOND * 59;
    s = f.formatDuration(millis, DurationFormat.dynamic, true);
    assertEquals("59s", s);

    millis += DurationFormatter.ONE_SECOND;
    s = f.formatDuration(millis, DurationFormat.dynamic, true);
    assertEquals("1m 0s", s);

    s = f.formatDuration(DurationFormatter.ONE_MINUTE, DurationFormat.dynamic, false, false);
    assertEquals("1m", s.trim());

    s = f.formatDuration(42, DurationFormat.dynamic, true, false, 100);
    assertEquals("42ms", s.trim());

    s = f.formatDuration(101, DurationFormat.dynamic, true, false, 100);
    assertEquals("0.1s", s.trim());

    s = f.formatDuration(123, DurationFormat.dynamic, true, false, -1);
    assertEquals("0.12s", s.trim());

    f = new DurationFormatter('.', 3);
    s = f.formatDuration(123, DurationFormat.dynamic, true, false, 100);
    assertEquals("0.123s", s.trim());
  }
}
