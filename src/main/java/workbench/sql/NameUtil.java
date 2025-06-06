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
package workbench.sql;

import workbench.db.IdentifierCase;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * Utility methods that can be used in an XSLT Script.
 *
 * To use them, add a namespace for this class:
 *
 * <tt>xmlns:wb="workbench.sql.NameUtil"</tt>
 *
 * then inside the XSLT, this can be used like this:
 *
 * <code>
 * &lt;xsl:variable name="tablename" select="wb:camelCaseToSnake(table-name)"/&gt;
 * </code>
 *
 * @author Thomas Kellerer
 */
public class NameUtil
{
  private static final String INVALID_CHARS = "- .:\\/\"'!%&()=?+*";

  /**
   * Cleanup an identifier and convert CamelCase to SNAKE_CASE.
   *
   * @param input the identifier to cleanup
   * @return a clean identifier in uppercase
   */
  public static String camelCaseToSnakeUpper(String input)
  {
    return camelCaseToSnake(input, IdentifierCase.upper);
  }

  /**
   * Cleanup an identifier and convert CamelCase to snake_case
   *
   * @param input the identifier to cleanup
   * @return a clean identifier in lowercase
   */
  public static String camelCaseToSnakeLower(String input)
  {
    return camelCaseToSnake(input, IdentifierCase.lower);
  }

  public static String camelCaseToSnake(String input)
  {
    return camelCaseToSnake(input, IdentifierCase.mixed);
  }

  /**
   * Convert CamelCase to snake_case (either upper or lower case).
   *
   * Invalid characters are replaced with with an underscore.
   *
   * @param input   the identifier to clean up
   * @param idCase
   * @return a cleaned identifier that does not need quoting
   */
  public static String camelCaseToSnake(String input, IdentifierCase idCase)
  {
    if (input == null) return "";
    input = SqlUtil.removeObjectQuotes(input);

    StringBuilder result = new StringBuilder(input.length() + 5);
    char current = 0;
    char previous = 0;

    for (int i = 0; i < input.length(); i++)
    {
      current = input.charAt(i);
      if (Character.isUpperCase(current) && (Character.isLowerCase(previous) || Character.isWhitespace(previous)) && previous != '_')
      {
        result.append('_');
      }
      if (current == '-' || Character.isWhitespace(current) || (!Character.isDigit(current) && !Character.isLetter(current)) || INVALID_CHARS.indexOf(current) > -1)
      {
        current = '_';
      }
      previous = current;
      switch (idCase)
      {
        case lower:
          result.append(Character.toLowerCase(current));
          break;
        case upper:
          result.append(Character.toUpperCase(current));
          break;
        default:
          result.append(current);
          break;
      }
    }
    return SqlUtil.quoteObjectname(result.toString(), false, true, '"');
  }

  /**
   * Cleanup an identifier and optionally convert to lowercase
   *
   * @param input the identifier to cleanup
   * @return a clean identifier
   */
  public static String cleanupIdentifier(String input, String lowerCase)
  {
    if (input == null) return "";
    boolean toLowerCase = StringUtil.stringToBool(lowerCase);
    input = SqlUtil.removeObjectQuotes(input);
    if (toLowerCase)
    {
      input = input.toLowerCase();
    }
    return SqlUtil.cleanupIdentifier(input);
  }

  public static String quoteIfNeeded(String input)
  {
    return SqlUtil.quoteObjectname(input, false, true, '"');
  }

  /**
   * Quotes the identifier if it is in mixed case.
   */
  public static String preserveCase(String input)
  {
    if (StringUtil.isMixedCase(input))
    {
      return '"' + input + '"';
    }
    return SqlUtil.quoteObjectname(input, false, true, '"');
  }

  /**
   * Quotes the identifier if it is in mixed or lowercase.
   */
  public static String preserveLowercase(String input)
  {
    if (StringUtil.isMixedCase(input) || StringUtil.isLowerCase(input))
    {
      return '"' + input + '"';
    }
    return SqlUtil.quoteObjectname(input, false, true, '"');
  }

  /**
   * Quotes the identifier if it is in mixed or uppercase.
   */
  public static String preserveUppercase(String input)
  {
    if (StringUtil.isMixedCase(input) || StringUtil.isUpperCase(input))
    {
      return '"' + input + '"';
    }
    return SqlUtil.quoteObjectname(input, false, true, '"');
  }

  public static String toLowerCase(String input)
  {
    if (input == null) return "";
    return input.toLowerCase();
  }

  public static String toUpperCase(String input)
  {
    if (input == null) return "";
    return input.toUpperCase();
  }

}
