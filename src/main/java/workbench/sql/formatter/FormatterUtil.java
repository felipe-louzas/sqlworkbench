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

package workbench.sql.formatter;

import workbench.resource.GeneratedIdentifierCase;
import workbench.resource.Settings;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class FormatterUtil
{

  public static String getIdentifier(String input)
  {
    if (StringUtil.isEmpty(input)) return input;
    if (SqlUtil.isQuotedIdentifier(input)) return input;

    // maybe a multi-part identifier where just one part is quoted
    if (input.indexOf('"') > -1) return input;
    // take care of SQL Server's stupid brackets
    if (input.indexOf('[') > -1) return input;

    return adjustCase(input, Settings.getInstance().getFormatterIdentifierCase());
  }

  public static String getFunction(String input)
  {
    return adjustCase(input, Settings.getInstance().getFormatterIdentifierCase());
  }

  public static String getKeyword(String input)
  {
    return adjustCase(input, Settings.getInstance().getFormatterKeywordsCase());
  }

  public static String getDataType(String input)
  {
    return adjustCase(input, Settings.getInstance().getFormatterDatatypeCase());
  }

  private static String adjustCase(String input, GeneratedIdentifierCase keywordCase)
  {
    if (input == null) return null;
    switch (keywordCase)
    {
      case lower:
        return input.toLowerCase();
      case upper:
        return input.toUpperCase();
    }
    // asIs:
    return input;
  }

}
