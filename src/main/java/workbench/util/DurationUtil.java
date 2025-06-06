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

package workbench.util;

/**
 * A class to handle durations based on units.
 *
 * @author Thomas Kellerer
 */
public class DurationUtil
{
  private static final char UNIT_SECONDS = 's';
  private static final char UNIT_MINUTES = 'm';
  private static final char UNIT_HOURS = 'h';
  private static final char UNIT_DAYS = 'd';

  public static final long ONE_SECOND = 1000L;
  public static final long ONE_MINUTE = ONE_SECOND * 60;
  public static final long ONE_HOUR = ONE_MINUTE * 60;
  public static final long ONE_DAY = ONE_HOUR * 24;

  public static boolean isValid(String definition)
  {
    if (StringUtil.isBlank(definition))
    {
      return false;
    }
    String pattern = "^[0-9]+[smhd]{1}$";
    return definition.trim().toLowerCase().matches(pattern);
  }

  public static String getTimeDisplay(long millis)
  {
    if (millis == 0) return "";

    if (millis < 60 * 1000)
    {
      return Long.toString((millis / 1000)) + "s";
    }
    return Long.toString((millis / (60 * 1000))) + "m";
  }

  public static long parseDuration(String definition)
  {
    if (StringUtil.isEmpty(definition)) return 0;

    definition = definition.trim().toLowerCase().replace(" ", "");
    if (definition.isEmpty()) return 0;

    if (definition.length() == 1)
    {
      return StringUtil.getLongValue(definition, 0);
    }

    char lastChar = definition.charAt(definition.length() - 1);
    long value = -1;
    if (Character.isDigit(lastChar))
    {
      value = StringUtil.getLongValue(definition, 0);
    }
    else
    {
      value = StringUtil.getLongValue(definition.substring(0, definition.length() - 1), 0);
    }

    switch (lastChar)
    {
      case UNIT_SECONDS:
        return value * ONE_SECOND;
      case UNIT_MINUTES:
        return value * ONE_MINUTE;
      case UNIT_HOURS:
        return value * ONE_HOUR;
      case UNIT_DAYS:
        return value * ONE_DAY;
    }
    return value;
  }

}
