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

import java.io.BufferedReader;
import java.io.StringReader;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * A collection of utility methods around String handling.
 *
 * @author Thomas Kellerer
 */
public class StringUtil
{
  public static final String REGEX_CRLF = "((\r\n)|(\n\r)|\r|\n)";
  public static final Pattern PATTERN_CRLF = Pattern.compile(REGEX_CRLF);

  public static final String LINE_TERMINATOR = System.getProperty("line.separator");
  public static final String EMPTY_STRING = "";

  public static final String ISO_DATE_FORMAT = "yyyy-MM-dd";
  public static final String ISO_TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS";
  public static final String ISO_TZ_TIMESTAMP_FORMAT = "yyyy-MM-dd HH:mm:ss.SSS z";

  public static final DateTimeFormatter ISO_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern(ISO_TIMESTAMP_FORMAT);
  public static final DateTimeFormatter ISO_TZ_TIMESTAMP_FORMATTER = DateTimeFormatter.ofPattern(ISO_TZ_TIMESTAMP_FORMAT);

  private static final char[] HEX_DIGIT = {'0','1','2','3','4','5','6','7','8','9','A','B','C','D','E','F'};

  // the \u000b is used in Microsoft PowerPoint
  // when doing copy & paste from there this yields an invalid character
  // see makePlainLinefeed()
  private static final Pattern PATTERN_NON_LF = Pattern.compile("(\r\n)|(\n\r)|(\r)|(\u000b)");

  public static StringBuilder emptyBuilder()
  {
    return new StringBuilder(0);
  }

  public static String getCurrentTimestamp()
  {
    return ISO_TIMESTAMP_FORMATTER.format(LocalDateTime.now());
  }

  public static String getCurrentTimestampWithTZString()
  {
    return ISO_TZ_TIMESTAMP_FORMATTER.format(ZonedDateTime.now());
  }

  /**
   * Checks if the given string is a valid pattern for a SimpleDateFormat.
   *
   * @param pattern
   * @return the exception's message if an error occurs, null if everything is OK.
   */
  @SuppressWarnings("ResultOfObjectAllocationIgnored")
  public static String isDatePatternValid(String pattern)
  {
    if (isBlank(pattern)) return null;
    try
    {
      new DateTimeFormatterBuilder().appendPattern(pattern);
    }
    catch (Exception e)
    {
      return ExceptionUtil.getDisplay(e);
    }
    return null;
  }

  public static String getPathSeparator()
  {
    return System.getProperty("path.separator");
  }

  public static int hashCode(CharSequence val)
  {
    int len = val.length();
    int hash = 0;

    for (int i = 0; i < len; i++)
    {
      hash = 31*hash + val.charAt(i);
    }
    return hash;
  }

  /**
   * Replace various combinations of linefeeds in the input string with \n.
   *
   * @param input
   * @return input string with only \n linefeeds
   * @see #PATTERN_NON_LF
   */
  public static String makePlainLinefeed(String input)
  {
    Matcher m = PATTERN_NON_LF.matcher(input);
    return m.replaceAll("\n");
  }

  public static boolean endsWith(CharSequence s, String end)
  {
    if (s == null) return false;
    if (isEmpty(end)) return false;
    int len = s.length();
    if (len == 0) return false;
    if (len < end.length()) return false;
    String last = s.subSequence(len - end.length(), len).toString();
    return end.equals(last);
  }

  public static boolean endsWith(CharSequence s, char c)
  {
    if (s == null) return false;
    int len = s.length();
    if (len == 0) return false;
    return s.charAt(len - 1) == c;
  }

  public static boolean lineStartsWith(CharSequence text, int lineStartPos, String compareTo)
  {
    if (isEmpty(compareTo)) return false;
    if (isEmpty(text)) return false;

    int textLength = text.length();
    int len = compareTo.length();

    // skip whitespace at the beginning
    int pos = findFirstNonWhitespace(text, lineStartPos, false);
    if (pos > lineStartPos)
    {
      lineStartPos = pos;
    }

    for (int i=0; i < len; i++)
    {
      char thisChar = 0;
      char otherChar = compareTo.charAt(i);
      if (lineStartPos + i < textLength)
      {
        thisChar = text.charAt(lineStartPos + i);
      }
      if (thisChar != otherChar) return false;
    }
    return true;
  }

  /**
   * Returns the length of the line without any line ending characters
   */
  public static int getRealLineLength(String line)
  {
    int len = line.length();
    if (len == 0) return 0;

    char c = line.charAt(len - 1);

    while ((len > 0) && (c == '\r' || c == '\n'))
    {
      len--;
      if (len > 0) c = line.charAt(len - 1);
    }
    return len;
  }

  public static boolean isWhitespace(CharSequence s)
  {
    if (s == null) return false;
    int len = s.length();
    if (len == 0) return false;
    int pos = 0;
    while (pos < len)
    {
      char c = s.charAt(pos);
      if (!Character.isWhitespace(c)) return false;
      pos++;
    }
    return true;
  }

  /**
   * Null safe trim().
   *
   * @param toTrim
   */
  public static String trim(String toTrim)
  {
    if (toTrim == null) return toTrim;
    return toTrim.trim();
  }

  public static String trimToNull(String toTrim)
  {
    String result = trim(toTrim);
    if (isEmpty(result)) return null;
    return result;
  }

  public static String removeTrailing(String s, char remove)
  {
    if (s == null) return s;
    int pos = s.length();
    if (pos == 0) return s;

    char last = s.charAt(pos - 1);
    if (last != remove) return s;

    while (pos > 0 && s.charAt(pos - 1) == remove)
    {
      pos --;
    }
    return s.substring(0, pos);
  }

  public static String removeLeading(String s, char toRemove)
  {
    if (s == null || s.length() < 1) return s;
    int pos = 0;
    while (pos < s.length() && s.charAt(pos) == toRemove)
    {
      pos ++;
    }
    if (pos >= s.length()) return EMPTY_STRING;

    if (pos > 0)
    {
      return s.substring(pos);
    }
    return s;
  }

  public static String ltrim(String s)
  {
    if (s == null) return s;
    if (s.isBlank()) return EMPTY_STRING;

    int pos = findFirstNonWhitespace(s);
    if (pos < 0) return s;
    return s.substring(pos);
  }

  public static String rtrim(String s)
  {
    if (s == null) return s;
    int pos = s.length();
    if (pos == 0) return s;

    char last = s.charAt(pos - 1);
    if (last > ' ') return s;

    while (pos > 0 && s.charAt(pos - 1) <= ' ')
    {
      pos --;
    }

    return s.substring(0, pos);
  }

  public static CharSequence rtrim(CharSequence s)
  {
    if (s == null) return s;
    int pos = s.length();
    if (pos == 0) return s;

    char last = s.charAt(pos - 1);
    if (last > ' ') return s;

    while (pos > 0 && s.charAt(pos - 1) <= ' ')
    {
      pos --;
    }

    return s.subSequence(0, pos);
  }

  public static int indexOf(CharSequence value, char c)
  {
    return indexOf(value, c, 1);
  }

  public static int indexOf(CharSequence value, char c, int occurance)
  {
    if (value == null) return -1;
    if (occurance <= 0) occurance = 1;
    int numFound = 0;

    for (int i=0; i < value.length(); i++)
    {
      if (value.charAt(i) == c)
      {
        numFound++;
        if (numFound == occurance) return i;
      }
    }
    return -1;
  }

  public static void trimTrailingWhitespace(StringBuilder value)
  {
    if (value == null || value.length() == 0) return;
    int len = value.length();
    int pos = len - 1;
    char c = value.charAt(pos);

    if (!Character.isWhitespace(c)) return;

    while (pos > 0 && Character.isWhitespace(value.charAt(pos - 1)))
    {
      pos --;
    }
    value.setLength(pos);
  }

  public static boolean isMixedCase(String s)
  {
    return !isUpperCase(s) && !isLowerCase(s);
  }

  public static boolean isLowerCase(String s)
  {
    if (s == null) return false;
    int l = s.length();
    for (int i = 0; i < l; i++)
    {
      char c = s.charAt(i);
      if (Character.isUpperCase(c)) return false;
    }
    return true;
  }

  public static boolean isUpperCase(String s)
  {
    if (s == null) return false;
    int l = s.length();
    for (int i = 0; i < l; i++)
    {
      char c = s.charAt(i);
      if (Character.isLowerCase(c)) return false;
    }
    return true;
  }

  public static boolean arraysEqual(String[] one, String[] other)
  {
    if (one == null && other == null) return true;
    if (one == null || other == null) return false;

    if (one.length != other.length) return false;
    for (int i = 0; i < one.length; i++)
    {
      if (!equalString(one[i],other[i])) return false;
    }
    return true;
  }

  public static String toLowerCase(String source)
  {
    if (source == null) return null;
    return source.toLowerCase();
  }

  public static String[] toUpperCase(String[] source)
  {
    if (source == null) return source;
    String[] result = new String[source.length];
    for (int i=0; i < source.length; i++)
    {
      result[i] = source[i] == null ? null : source[i].toUpperCase();
    }
    return result;
  }

  public static boolean hasOpenQuotes(String data, char quoteChar, QuoteEscapeType escapeType)
  {
    if (isEmpty(data)) return false;
    int chars = data.length();
    boolean inQuotes = false;
    for (int i = 0; i < chars; i++)
    {
      char current = data.charAt(i);
      if (current == quoteChar)
      {
        if (escapeType == QuoteEscapeType.escape)
        {
          char last = 0;
          if (i > 1) last = data.charAt(i - 1);
          if (last != '\\')
          {
            inQuotes = !inQuotes;
          }
        }
        else if (escapeType == QuoteEscapeType.duplicate)
        {
          char next = 0;
          if (i < data.length() - 1) next = data.charAt(i + 1);
          if (next == quoteChar)
          {
            i++;
          }
          else
          {
            inQuotes = !inQuotes;
          }
        }
        else
        {
          inQuotes = !inQuotes;
        }
      }
    }
    return inQuotes;
  }

  /**
   * Capitalize the first word of the passed String.
   * (write the first character in uppercase, the rest in lower case)
   * This does not loop through the entire string to capitalize every word.
   */
  public static String capitalize(String word)
  {
    if (word == null) return null;
    if (word.length() == 0) return word;
    StringBuilder s = new StringBuilder(word.toLowerCase());
    char c = s.charAt(0);
    s.setCharAt(0, Character.toUpperCase(c));
    return s.toString();
  }

  /**
   * Remove all characters from the input string that might not be allowed in a filename.
   *
   * @param input the value to be used as a filename
   * @return input value without any characters invalid for a filename converted to lowercase
   *
   * @see #makeFilename(java.lang.String, boolean)
   */
  public static String makeFilename(String input)
  {
    return makeFilename(input, true);
  }

  /**
   * Remove all characters from the input string that might not be allowed in a filename.
   *
   * @param input         the value to be used as a filename
   * @param makeLowerCase if true the clean name will be returend in lowercase, otherwise unchanged
   *
   * @return input value without any characters invalid for a filename
   */
  public static String makeFilename(String input, boolean makeLowerCase)
  {
    if (input == null) return null;
    if (input.equals("..")) return "__";
    if (input.equals(".")) return "_";
    String fname = input.replaceAll("[\t:\\\\/\\?\\*\\|<>\"'%\u00A7\\^&\u0000]", EMPTY_STRING);
    return makeLowerCase ? fname.toLowerCase() : fname;
  }


  /**
   * Replacement for StringBuilder.lastIndexOf() which does
   * a lot of object creation and copying to achieve this.
   *
   * This implementation should be a lot faster for StringBuilder
   * and StringBuffer, and will basically be the same for String
   * objects.
   *
   * @param haystack the string to search in
   * @param needle the character to look for
   * @return -1 if c was not found, the position of c in s otherwise
   */
  public static int lastIndexOf(CharSequence haystack, char needle)
  {
    if (haystack == null) return -1;

    int len = haystack.length();
    if (len == 0) return -1;

    for (int i=(len - 1); i > 0; i--)
    {
      if (haystack.charAt(i) == needle) return i;
    }
    return -1;
  }

  /**
   * Null safe string replacement.
   *
   * This is faster than using String.replace() as that uses regular expressions internally.
   *
   * @param haystack the string in which to replace. If null, null is returned
   * @param needle the string to search for. If null, haystack is returned.
   * @param replacement the replacement. If null haystack is returned
   * @return the haystack with all occurances of needle replaced with replacement
   */
  public static String replace(String haystack, String needle, String replacement)
  {
    if (replacement == null) return haystack;
    if (needle == null) return haystack;
    if (haystack == null) return null;

    int pos = haystack.indexOf(needle);
    if (pos == -1)
    {
      return haystack;
    }

    int add = replacement.length() - needle.length() * 2;
    if (add < 0) add = 0;

    StringBuilder result = new StringBuilder(haystack.length() + add);

    int lastpos = 0;
    int len = needle.length();
    while (pos != -1)
    {
      result.append(haystack.substring(lastpos, pos));
      result.append(replacement);
      lastpos = pos + len;
      pos = haystack.indexOf(needle, lastpos);
    }
    if (lastpos < haystack.length())
    {
      result.append(haystack.substring(lastpos));
    }
    return result.toString();
  }

  private static final int[] LIMITS =
  {
    9,99,999,9999,99999,999999,9999999,99999999,999999999,Integer.MAX_VALUE
  };

  /**
   * Returns the number of Digits of the value
   *
   * @param x the value to check
   * @return the number of digits that x consists of
   */
  public static int numDigits(int x )
  {
    for (int i = 0; i < LIMITS.length; i++)
    {
      if ( x <= LIMITS[i])
      {
        return i+1;
      }
    }
    return LIMITS.length + 1;
  }

  /**
   * Find the longest line in the given string and return its length.
   *
   * Up to maxLines lines are evaluated.
   *
   * @param text
   * @param maxLines
   * @return the length of the longest line
   */
  public static String getLongestLine(String text, int maxLines)
  {
    if (isEmpty(text)) return EMPTY_STRING;
    Matcher m = PATTERN_CRLF.matcher(text);

    int lastpos = 0;
    int linecount = 0;
    int maxlen = 0;
    int linestart = 0;
    int lineend = 0;

    while (m.find(lastpos))
    {
      linecount ++;
      int pos = m.start();
      int len = pos - lastpos;
      if (len > maxlen)
      {
        linestart = lastpos;
        lineend = pos;
        maxlen = len;
      }
      lastpos = pos + 1;
      if (linecount >= maxLines) break;
    }

    if (m.hitEnd())
    {
      int len = text.length() - lastpos;
      if (len > maxlen)
      {
        maxlen = len;
        linestart = lastpos;
        lineend = text.length();
      }
    }
    if (linestart >= 0 && lineend <= text.length())
    {
      return text.substring(linestart, lineend);
    }
    return text;
  }

  /**
   * Check if the given string is a number.
   *
   * This is done by parsing the String using Double.parseDouble() and catching all possible exceptions.
   *
   * @param value
   */
  public static boolean isNumber(String value)
  {
    if (value == null) return false;
    try
    {
       Double.valueOf(value);
       return true;
    }
    catch (Throwable e)
    {
      return false;
    }
  }

  /**
   * Checks if the given parameter is not empty and does not only consist of whitespace
   *
   * @param value
   * @return true if at least one non-whitespace character is returned
   * @see #isBlank(java.lang.CharSequence)
   */
  public static boolean isNotBlank(CharSequence value)
  {
    return !isBlank(value);
  }

  public static boolean isNoneBlank(CharSequence... values)
  {
    if (values == null) return false;
    for (CharSequence value : values)
    {
      if (isBlank(value)) return false;
    }
    return true;
  }

  /**
   * Checks if the given parameter is "empty".
   * i.e: either null, length == 0 or contains only whitespace
   */
  public static boolean isBlank(CharSequence value)
  {
    if (isEmpty(value)) return true;
    return isWhitespace(value);
  }

  /**
   * Checks if all given parameters are "empty".
   */
  public static boolean isAllBlank(CharSequence... values)
  {
    if (values == null) return true;
    for (CharSequence value : values)
    {
      if (isNotBlank(value)) return false;
    }
    return true;
  }

  /**
   * Checks if any of given parameters is empty.
   *
   * @see #isBlank(CharSequence)
   */
  public static boolean isAnyBlank(CharSequence... values)
  {
    if (values == null) return true;
    for (CharSequence value : values)
    {
      if (isBlank(value)) return true;
    }
    return false;
  }


  /**
   * Checks if the given string value is not empty (!= null && length() > 0).
   *
   * Whitespaces are considered "not empty".
   *
   * @param value
   * @see #isEmpty(java.lang.CharSequence)
   */
  public static boolean isNotEmpty(CharSequence value)
  {
    return value != null && value.length() > 0;
  }

  /**
   * Checks if the given String is null or has a zero length.
   * A String containing only whitespaces is not considered empty.
   *
   * @param value the String to test
   * @return true if the String is empty (or null)
   */
  public static boolean isEmpty(CharSequence value)
  {
    if (value == null) return true;
    return value.length() == 0;
  }

  public static boolean allNonEmpty(CharSequence ... values)
  {
    if (values == null) return false;
    for (CharSequence value : values)
    {
      if (isEmpty(value)) return false;
    }
    return true;
  }

  public static boolean allEmpty(CharSequence ... values)
  {
    if (values == null) return true;
    for (CharSequence value : values)
    {
      if (isNotEmpty(value)) return false;
    }
    return true;
  }

  public static char getFirstNonWhitespace(CharSequence line)
  {
    int pos = findFirstNonWhitespace(line);
    if (pos > -1 && pos < line.length())
    {
      return line.charAt(pos);
    }
    return 0;
  }

  /**
   * Return the position of the first non-whitespace character in the String
   * @param line
   * @return the position of the first whitespace or the length of the string
   */
  public static int findFirstNonWhitespace(CharSequence line)
  {
    return findFirstNonWhitespace(line, 0, true);
  }

  /**
   * Return the position of the first non-whitespace character in the String
   * @param line
   * @param startPos the position where to start searching
   * @return the position of the first whitespace or the length of the string
   */
  public static int findFirstNonWhitespace(final CharSequence line, int startPos, boolean treatNewLineAsWhitespace)
  {
    if (line == null) return -1;
    int len = line.length();
    if (len == 0) return -1;
    if (startPos >= len) return -1;

    int pos = startPos;
    char c = line.charAt(pos);

    do
    {
      c = line.charAt(pos);
      if (!treatNewLineAsWhitespace)
      {
        if (c == '\n' || c == '\r') return -1;
      }
      if (c > ' ') return pos;
      pos ++;
    }
    while (pos < len);

    return -1;
  }

  public static String getStartingWhiteSpace(final String line)
  {
    if (line == null) return null;
    int pos = findFirstNonWhitespace(line);
    if (pos <= 0) return null;
    String result = line.substring(0, pos);
    return result;
  }

  /**
   * Returns the first number from a string.
   *
   * @param input  the string to test
   * @return -1 if no number is found, the number otherwise
   */
  public static int getFirstNumber(String input)
  {
    if (isBlank(input)) return -1;
    Pattern p = Pattern.compile("[0-9]+");
    Matcher m = p.matcher(input);
    if (m.find())
    {
      return getIntValue(m.group(), 0);
    }
    return -1;
  }

  /**
   * Increment the first number in a string.
   *
   * @param input  the string to test with a possible counter
   * @param startValue the value to start the counter from if no number is found
   * @return -1 if no number is found, the number otherwise
   */
  public static String incrementCounter(String input, int startValue)
  {
    if (isBlank(input)) return input;
    String result = input;
    Pattern p = Pattern.compile("[0-9]+");
    Matcher m = p.matcher(input);
    if (m.find())
    {
      int nr = getIntValue(m.group(), 0) + 1;
      int start = m.start();
      int end = m.end();
      result = result.substring(0,start) + Integer.toString(nr) + result.substring(end);
    }
    else
    {
      result = result + " (" + Integer.toString(startValue) + ")";
    }
    return result;
  }


  /**
   * Parse the given value as a double.
   * If the value cannot be parsed, the default value will be returned.
   *
   * @param value  the string to parse
   * @param defaultValue the value to be returned if something goes wrong.
   * @return the parse value of the input.
   */
  public static double getDoubleValue(String value, double defaultValue)
  {
    if (value == null) return defaultValue;

    double result = defaultValue;
    try
    {
      result = Double.parseDouble(value.trim());
    }
    catch (NumberFormatException e)
    {
      // Ignore
    }
    return result;
  }

  public static int getIntValue(String aValue)
  {
    return getIntValue(aValue, 0);
  }

  public static int getIntValue(String aValue, int aDefault)
  {
    if (aValue == null) return aDefault;

    int result = aDefault;
    try
    {
      result = Integer.parseInt(aValue.trim());
    }
    catch (NumberFormatException e)
    {
      // Ignore
    }
    return result;
  }

  public static long getLongValue(String aValue, long aDefault)
  {
    if (aValue == null) return aDefault;

    long result = aDefault;
    try
    {
      result = Long.parseLong(aValue.trim());
    }
    catch (NumberFormatException e)
    {
      // Ignore
    }
    return result;
  }

  /**
   * Checks if both Strings are equal considering null values.
   *
   * A null String and an empty String (length==0 or all whitespace) are
   * considered equal as well (because both are "empty")
   *
   * @param one the first String, maybe null
   * @param other the second String, maybe null
   * @return true if both strings are equals
   *
   * @see #isBlank(java.lang.CharSequence)
   */
  public static boolean equalStringOrEmpty(String one, String other)
  {
    if (isBlank(one) && isBlank(other)) return true;
    return equalString(one, other);
  }

  /**
   * Checks if both Strings are equal considering null values.
   * A null String and an empty String (length==0 or all whitespace) are
   * considered equal as well (because both are "empty")
   *
   * @param one the first String, maybe null
   * @param other the second String, maybe null
   * @param ignoreCase if true the string comparison is done using compareToIgnoreCase()
   * @return true if both strings are equals
   * @see #isBlank(java.lang.CharSequence)
   */
  public static boolean equalStringOrEmpty(String one, String other, boolean ignoreCase)
  {
    if (isBlank(one) && isBlank(other)) return true;
    return compareStrings(one, other, ignoreCase) == 0;
  }

  public static boolean equalString(String one, String other)
  {
    return compareStrings(one, other, false) == 0;
  }

  public static boolean stringsAreNotEqual(String one, String other)
  {
    return compareStrings(one, other, false) != 0;
  }

  public static String concatWithSeparator(String separator, String... elements)
  {
    if (elements == null || elements.length == 0) return "";

    StringBuilder result = new StringBuilder(elements.length * 10);
    for (int i=0; i < elements.length; i++)
    {
      if (StringUtil.isNotBlank(elements[i]))
      {
        if (i > 0 && result.length() > 0) result.append(separator);
        result.append(elements[i]);
      }
    }
    return result.toString();
  }

  /**
   * @param value1 the first String, maybe null
   * @param value2 the second String, maybe null
   * @param ignoreCase if true the string comparison is done using compareToIgnoreCase()
   * @return 0 if both are null
   */
  public static int compareStrings(String value1, String value2, boolean ignoreCase)
  {
    if (value1 == null && value2 == null) return 0;
    if (value1 == null) return -1;
    if (value2 == null) return 1;
    if (ignoreCase) return value1.compareToIgnoreCase(value2);
    return value1.compareTo(value2);
  }

  public static boolean equalStringIgnoreCase(String one, String other)
  {
    if (one == null && other == null) return true;
    if (one == null || other == null) return false;
    return one.equalsIgnoreCase(other);
  }

  public static List<String> stringToList(String aString)
  {
    return stringToList(aString, ",");
  }

  public static List<String> stringToList(String aString, String aDelimiter)
  {
    return stringToList(aString, aDelimiter, false, false);
  }

  public static List<String> stringToList(String aString, String aDelimiter, boolean removeEmpty)
  {
    return stringToList(aString, aDelimiter, removeEmpty, false);
  }

  public static List<String> stringToList(String aString, String aDelimiter, boolean removeEmpty, boolean trimEntries)
  {
    return stringToList(aString, aDelimiter, removeEmpty, trimEntries, false, false);
  }

  public static List<String> stringToList(String aString, String aDelimiter, boolean removeEmpty, boolean trimEntries, boolean checkBrackets)
  {
    return stringToList(aString, aDelimiter, removeEmpty, trimEntries, checkBrackets, false);
  }

  /**
   * Parses the given String and creates a List containing the elements
   * of the string that are separated by <tt>aDelimiter</aa>
   *
   * @param aString       the value to be parsed
   * @param aDelimiter    the delimiter to user
   * @param removeEmpty   flag to remove empty entries
   * @param trimEntries   flag to trim entries (will be applied beore checking for empty entries)
   * @param checkBrackets flag to check for opening and closing brackets (delimiter inside brackets will not be taken into account)
   * @return A List of Strings
   */
  public static List<String> stringToList(String aString, String aDelimiter, boolean removeEmpty, boolean trimEntries, boolean checkBrackets, boolean keepQuotes)
  {
    if (isEmpty(aString)) return new ArrayList<>(0);
    WbStringTokenizer tok = new WbStringTokenizer(aString, aDelimiter);
    tok.setDelimiterNeedsWhitspace(false);
    tok.setKeepQuotes(keepQuotes);
    tok.setCheckBrackets(checkBrackets);
    List<String> result = new ArrayList<>();
    while (tok.hasMoreTokens())
    {
      String element = tok.nextToken();
      if (element == null) continue;
      if (trimEntries) element = element.trim();
      if (removeEmpty && isEmpty(element)) continue;
      result.add(element);
    }
    return result;
  }

  public static String[] toArray(Collection<String> strings, boolean toUpper)
  {
    return toArray(strings, toUpper, false);
  }

  public static String[] toArray(Collection<String> strings, boolean toUpper, boolean unique)
  {
    if (strings == null) return null;
    if (strings.isEmpty()) return new String[0];

    if (unique)
    {
      Set<String> temp = CollectionUtil.caseInsensitiveSet();
      temp.addAll(strings);
      strings = temp;
    }

    int i = 0;
    String[] result = new String[strings.size()];
    for (String s : strings)
    {
      result[i++] = (s == null ? null : (toUpper ? s.toUpperCase() : s ) );
    }
    return result;
  }

  /**
   * Create a String from the given Collection, where the elements are delimited
   * with the supplied delimiter
   *
   * @return The elements of the Collection as a String
   * @param aList The list to process
   * @param aDelimiter The delimiter to use
   */
  public static String listToString(Collection aList, char aDelimiter)
  {
    return listToString(aList, String.valueOf(aDelimiter), false);
  }

  public static String listToString(Collection aList, char aDelimiter, boolean quoteEntries)
  {
    return listToString(aList, String.valueOf(aDelimiter), quoteEntries);
  }
  /**
   * Create a String from the given list, where the elements are delimited
   * with the supplied delimiter
   *
   * @return The elements of the Collection as a String
   * @param aList The list to process
   * @param aDelimiter The delimiter to use
   * @param quoteEntries if true, all entries are quoted with a double quote
   */
  public static String listToString(Collection aList, String aDelimiter, boolean quoteEntries)
  {
    return listToString(aList, aDelimiter, quoteEntries, '"');
  }

  public static String listToString(Collection aList, String aDelimiter, boolean quoteEntries, char quote)
  {
    if (aList == null || aList.isEmpty()) return EMPTY_STRING;
    if (quoteEntries)
    {
      return (String)aList.
          stream().
          filter(o -> o != null).
          map(o -> quote + o.toString() + quote).
          collect(Collectors.joining(aDelimiter));
    }
    else
    {
      return (String)aList.
          stream().
          filter(o -> o != null).
          map(Object::toString).
          collect(Collectors.joining(aDelimiter));
    }
  }

  /**
   * Removes the given quote character from the input string, but only if the
   * input string starts with that quote character
   *
   * @param input the string to "trim"
   * @param quote the quote character to be used
   * @return the input string with the quote character removed at the start and end
   */
  public static String removeQuotes(String input, String quote)
  {
    if (isEmpty(input)) return input;
    input = input.trim();
    if (input.equals(quote)) return input;
    if (!(input.startsWith(quote) && input.endsWith(quote))) return input;
    return input.substring(quote.length(), input.length() - quote.length());
  }

  public static String removeBrackets(String input)
  {
    if (isEmpty(input)) return input;
    if (input.length() < 2) return input;

    char first = input.charAt(0);
    char last = input.charAt(input.length() - 1);

    if ( (first == '(' && last == ')') || (first == '{' && last == '}') || (first == '[' && last == '}') )
    {
      return input.substring(1, input.length() - 1);
    }
    return input;
  }

  /**
   * Removes single or double quote character from the start and the beginning of a string.
   * <br/>
   * Removes the matching quote character at the beginning from the end of the string.
   * The string is trimmed before testing for the presence of the quotes.
   *
   * @param input the string from which the quotes should be removed
   * @return the input with quotes removed
   * @see SqlUtil#removeObjectQuotes(java.lang.String)
   */
  public static String trimQuotes(String input)
  {
    if (isBlank(input)) return input;

    String result = input.trim();
    int len = result.length();

    if (len < 2) return input;

    char firstChar = result.charAt(0);
    char lastChar = result.charAt(len - 1);

    if ( (firstChar == '"' && lastChar == '"') ||
         (firstChar == '\'' && lastChar == '\''))
    {
      return result.substring(1, len - 1);
    }
    return input;
  }


  public static String trimQuotes(String input, char c)
  {
    if (isBlank(input)) return input;

    String result = input.trim();
    int len = result.length();

    if (len < 2) return input;

    char firstChar = result.charAt(0);
    char lastChar = result.charAt(len - 1);

    if ((firstChar == c && lastChar == c))
    {
      return result.substring(1, len - 1);
    }
    return input;
  }

  public static boolean isBoolean(String value)
  {
    if (value == null) return false;
    return "false".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
  }

  public static boolean stringToBool(String value)
  {
    if (value == null) return false;
    value = value.trim();
    return ("true".equalsIgnoreCase(value) || "1".equals(value) || "y".equalsIgnoreCase(value) || "yes".equalsIgnoreCase(value) || "on".equalsIgnoreCase(value) );
  }

  public static String getMaxSubstring(String s, int maxLen, String add)
  {
    if (s == null) return EMPTY_STRING;
    if (maxLen < 1) return s;
    if (s.length() < maxLen) return s;
    String result = s.substring(0, maxLen);
    if (add != null)
    {
      result += add;
    }
    return result;
  }

  public static String getMaxSubstring(String s, int maxLen)
  {
    return getMaxSubstring(s, maxLen, "...");
  }

  public static final String REGEX_SPECIAL_CHARS = "\\[](){}.*+?$^|";

  /**
   *  Quote the characters in a String that have a special meaning
   *  in regular expression.
   */
  public static String quoteRegexMeta(String str)
  {
    if (str == null) return null;
    if (str.length() == 0)
    {
      return EMPTY_STRING;
    }
    int len = str.length();
    StringBuilder buf = new StringBuilder(len + 5);
    for (int i = 0; i < len; i++)
    {
      char c = str.charAt(i);
      if (REGEX_SPECIAL_CHARS.indexOf(c) != -1)
      {
        buf.append('\\');
      }
      buf.append(c);
    }
    return buf.toString();
  }

  public static int findPreviousWhitespace(String data, int pos)
  {
    if (data == null) return -1;
    int count = data.length();
    if (pos > count || pos <= 1) return -1;
    for (int i=pos; i > 0; i--)
    {
      if (Character.isWhitespace(data.charAt(i))) return i;
    }
    return -1;
  }

  /**
   * Find the start of the word for the word left of the cursor position.
   *
   * @param data             the string to use
   * @param cursorPosition   the cursor position
   * @param wordBoundaries   characters that define wordboundaries (in addition to whitespace).
   *                         If null, the first non-whitespace character will be returned.
   * @return the position of the word boundary or -1 if not found
   *
   * @see #findPreviousWhitespace(String, int)
   */
  public static int findWordBoundary(String data, int cursorPosition, String wordBoundaries)
  {
    if (wordBoundaries == null) return findPreviousWhitespace(data, cursorPosition);
    if (data == null) return -1;
    int count = data.length();
    if (cursorPosition > count) return -1;
    if (cursorPosition == count) cursorPosition --;
    if (cursorPosition <= 1) return 0;
    for (int i=cursorPosition; i > 0; i--)
    {
      char c = data.charAt(i);
      if (wordBoundaries.indexOf(c) > -1 || Character.isWhitespace(c)) return i;
    }
    return 0;
  }

  public static String getFirstWord(String value)
  {
    if (StringUtil.isEmpty(value)) return "";

    value = value.trim();
    int pos = findFirstWhiteSpace(value);
    if (pos < 0) return value;
    return value.substring(0, pos);
  }

  /**
   * Find the first non-quoted whitespace in the given String.
   *
   * Whitespace inside double quotes are not considered
   *
   * @param data the data in which to search
   * @return the position of the first whitespace or -1 if no whitespace was found.
   */
  public static int findFirstWhiteSpace(CharSequence data)
  {
    return findFirstWhiteSpace(data, '"', 0);
  }

  public static int findFirstWhiteSpace(CharSequence data, char quote)
  {
    return findFirstWhiteSpace(data, quote, 0);
  }

  public static int findFirstWhiteSpace(CharSequence data, char quote, int startPos)
  {
    if (data == null) return -1;
    int count = data.length();
    if (count == 0) return -1;
    if (startPos >= count) return -1;
    boolean inQuotes = false;
    for (int i=startPos; i < count; i++)
    {
      char c = data.charAt(i);
      if (c == quote)
      {
        inQuotes = !inQuotes;
      }
      if (!inQuotes)
      {
        if (Character.isWhitespace(data.charAt(i))) return i;
      }
    }
    return -1;
  }

  public static String findWordLeftOfCursor(String text, int pos)
  {
    if (pos < 0) return null;

    // skip whitespace until a character is found
    try
    {
      int index = pos;
      char c = text.charAt(index);
      while (index > 0 && Character.isWhitespace(c))
      {
        index --;
        c = text.charAt(index);
      }
      if (index > 0)
      {
        return getWordLeftOfCursor(text, index + 1, null);
      }
      return null;
    }
    catch (Exception e)
    {
      return null;
    }
  }


  public static String getWordLeftOfCursor(String text, int pos, String wordBoundaries)
  {
    try
    {
      if (pos < 0) return null;
      int len = text.length();
      int testPos = -1;
      if (pos >= len)
      {
        testPos = len - 1;
      }
      else
      {
        testPos = pos - 1;
      }
      if (testPos < 1) return null;

      if (Character.isWhitespace(text.charAt(testPos))) return null;

      String word = null;
      int startOfWord = findWordBoundary(text, testPos, wordBoundaries);
      if (startOfWord == 0)
      {
        word = text.substring(0, Math.min(pos,len));
      }
      else if (startOfWord > 0)
      {
        word = text.substring(startOfWord+1, Math.min(pos,len));
      }
      return word;
    }
    catch (Exception e)
    {
      return null;
    }
  }

  public static int findPattern(String regex, String data, int startAt)
  {
    Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    return findPattern(p, data, startAt);
  }

  public static int findPattern(Pattern p, String data, int startAt)
  {
    if (startAt < 0) return -1;
    Matcher m = p.matcher(data);
    int result = -1;
    if (m.find(startAt)) result = m.start();
    return result;
  }

  public static String decodeUnicode(String theString)
  {
    if (theString == null) return null;

    char ch;
    int len = theString.length();
    if (len == 0) return theString;
    StringBuilder outBuffer = new StringBuilder(len);

    for (int x=0; x < len ; )
    {
      ch = theString.charAt(x++);
      if (ch == '\\' && x < len)
      {
        ch = theString.charAt(x++);

        if (ch == 'u')
        {
          // Read the xxxx
          int value = -1;
          int i=0;
          for (i=0; i<4; i++)
          {
            if ( x + i >= len)
            {
              value = -1;
              break;
            }

            ch = theString.charAt(x + i);
            switch (ch)
            {
              case '0': case '1': case '2': case '3': case '4':
              case '5': case '6': case '7': case '8': case '9':
                value = (value << 4) + ch - '0';
                break;
              case 'a': case 'b': case 'c':
              case 'd': case 'e': case 'f':
                value = (value << 4) + 10 + ch - 'a';
                break;
              case 'A': case 'B': case 'C':
              case 'D': case 'E': case 'F':
                value = (value << 4) + 10 + ch - 'A';
                break;
              default:
                // Invalid ecape sequence
                value = -1;
                break;
            }
            if (value == -1) break;
          }

          if ( value != -1)
          {
            outBuffer.append((char)value);
          }
          else
          {
            // When we wind up here, this means an invalid encoded unicode character was present.
            // do not convert the stuff, but copy the characters into the result buffer
            outBuffer.append("\\u");
            if (i == 0 && x < len)
            {
              outBuffer.append(ch);
            }
            else
            {
              for (int k=0; k < i; k++)
              {
                outBuffer.append(theString.charAt(x+k));
              }
            }
            x++;
          }
          x += i;
        }
        else
        {
          // The character after the backslash was not a 'u'
          // so we are not dealing with a uXXXX value
          // The following code applies popular "encodings" for non-printable characters
          // e.g. \n for a newline or \t for a tab character
          switch (ch)
          {
            case '\\':
              ch = '\\';
              break;
            case 't':
              ch = '\t';
              break;
            case 'r':
              ch = '\r';
              break;
            case 'n':
              ch = '\n';
              break;
            case 'f':
              ch = '\f';
              break;
            default:
              outBuffer.append('\\');
              break;
          }
          outBuffer.append(ch);
        }
      }
      else
      {
        outBuffer.append(ch);
      }
    }
    return outBuffer.toString();
  }

  public static String escapeText(String value, CharacterRange range)
  {
    return escapeText(value, range, EMPTY_STRING);
  }


  /**
   * Encodes characters to Unicode &#92;uxxxx (or simple escape like \r)
   *
   * This has partially been "borrowed" from the Properties class, because the code
   * there is not usable from the outside.
   *
   * Backslash, CR, LF, Tab and FormFeed (\f) will always be replaced.
   *
   * @param value the string to be encoded
   * @param range the CharacterRange which defines the characters to be encoded. If isOutsideRange()
   *        returns true, the character will be encoded.
   * @param additionalCharsToEncode additional characters not covered by the range may be null
   */
  public static String escapeText(String value, CharacterRange range, String additionalCharsToEncode)
  {
    return escapeText(value, range, additionalCharsToEncode, CharacterEscapeType.unicode);
  }

  public static String escapeText(String value, CharacterRange range, String additionalCharsToEncode, CharacterEscapeType type)
  {
    if (value == null) return null;

    int len = value.length();
    if (len == 0) return value;

    if (additionalCharsToEncode == null) additionalCharsToEncode = EMPTY_STRING;

    StringBuilder outBuffer = null;

    for (int x = 0; x < len; x++)
    {
      char aChar = value.charAt(x);

      switch (aChar)
      {
        case '\\':
          // creating the copy of the input value only on demand is much faster
          // if nothing needs to be replaced. If values have to be escaped,
          // doing a lazy creation of the buffer isn't slower, so this
          // increases the performance of an export if only few rows actually need escaping
          if (outBuffer == null) outBuffer = createStringBuilder(value, x);
          outBuffer.append("\\\\");
          break;
        case '\t':
          if (outBuffer == null) outBuffer = createStringBuilder(value, x);
          outBuffer.append("\\t");
          break;
        case '\n':
          if (outBuffer == null) outBuffer = createStringBuilder(value, x);
          outBuffer.append("\\n");
          break;
        case '\r':
          if (outBuffer == null) outBuffer = createStringBuilder(value, x);
          outBuffer.append("\\r");
          break;
        case '\f':
          if (outBuffer == null) outBuffer = createStringBuilder(value, x);
          outBuffer.append("\\f");
          break;
        default:
          if (range.isOutsideRange(aChar) || additionalCharsToEncode.indexOf(aChar) > -1)
          {
            if (outBuffer == null) outBuffer = createStringBuilder(value, x);
            switch (type)
            {
              case pgHex:
                // 0x00 is valid Java Unicode but rejected by PostgreSQL
                if (aChar == '\0') break;
                outBuffer.append("\\x");
                outBuffer.append(NumberStringCache.getHexString(aChar));
                break;
              case hex:
                outBuffer.append(hexString(aChar, 4));
                break;
              default:
                outBuffer.append("\\u");
                appendUnicode(outBuffer, aChar);
            }
          }
          else if (outBuffer != null)
          {
            outBuffer.append(aChar);
          }
      }
    }
    if (outBuffer == null) return value;
    return outBuffer.toString();
  }

  private static StringBuilder createStringBuilder(String value, int currentPos)
  {
    int len = value.length();
    StringBuilder outBuffer = new StringBuilder((int)(len*1.2));
    outBuffer.append(value.substring(0, currentPos));
    return outBuffer;
  }

  public static CharSequence getOctalString(int input)
  {
    StringBuilder result = new StringBuilder(3);
    String s = Integer.toOctalString(input);
    int len = s.length();
    if (len == 1)
    {
      result.append("00");
    }
    else if (len == 2)
    {
      result.append('0');
    }
    result.append(s);
    return result;
  }

  public static String padRight(String input, int length)
  {
    return padRight(input, length, ' ');
  }

  public static String padRight(String input, int length, char padChar)
  {
    if (input == null) return null;
    if (input.length() >= length) return input;
    StringBuilder result = new StringBuilder(length);
    result.append(input);
    while (result.length() < length)
    {
      result.append(padChar);
    }
    return result.toString();
  }

  /**
   * Convert an int value to a String with leading zeros
   * @param value
   * @param length
   */
  public static CharSequence formatInt(int value, int length)
  {
    StringBuilder result = new StringBuilder(length);
    if (value < 0)
    {
      result.append('-');
    }
    String nr = Integer.toString(Math.abs(value));
    for (int i=0; i < length - nr.length(); i++)
    {
      result.append('0');
    }
    result.append(nr);
    return result;
  }

  public static String formatNumber(int value, int length, boolean fillRight)
  {
    String s = NumberStringCache.getNumberString(value);
    int l = s.length();
    if (l >= length) return s;
    StringBuilder result = new StringBuilder(length);
    if (fillRight)
    {
      result.append(s);
    }
    for (int k = l; k < length; k++)
    {
      result.append(' ');
    }
    if (!fillRight)
    {
      result.append(s);
    }
    return result.toString();
  }

  private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\$\\{[0-9a-zA-Z_\\.\\-]*\\}");

  /**
   * Replaces "variables" of the form ${some.thing} in the input string.
   * Any variable name is assumed to be a system property, this means
   * <tt>${user.home}</tt> will be replaced with the value of System.getProperty("user.home")
   *
   * @param input a string that might contain system property variables
   * @return the string with replaced system properties or <tt>null</tt> if the input was <tt>null</tt>
   */
  public static String replaceProperties(String input)
  {
    return replaceProperties((Map)(System.getProperties()), input);
  }

  public static String replaceProperties(Map<String, String> props, String input)
  {
    if (isEmpty(input)) return input;

    Matcher m = VARIABLE_PATTERN.matcher(input);
    if (m == null)
    {
      return input;
    }

    while (m.find())
    {
      int start = m.start();
      int end = m.end();
      String var = input.substring(start, end);
      String propName = input.substring(start + 2, end - 1);
      String propValue = props.get(propName);
      if (propValue != null)
      {
        input = input.replace(var, propValue);
        m = VARIABLE_PATTERN.matcher(input);
        if (m == null)
        {
          return input;
        }
      }
    }
    return input;
  }

  public static void appendUnicode(StringBuilder buffer, char c)
  {
    buffer.append(hexDigit(c >> 12));
    buffer.append(hexDigit(c >>  8));
    buffer.append(hexDigit(c >>  4));
    buffer.append(hexDigit(c));
  }

  public static String hexString(int toConvert, int length)
  {
    String hex = Integer.toHexString(toConvert);
    StringBuilder result = new StringBuilder(length);
    for (int i= 0; i < length - hex.length(); i++)
    {
      result.append('0');
    }
    result.append(hex);
    return result.toString();
  }

  public static char hexDigit(int nibble)
  {
    return HEX_DIGIT[(nibble & 0xF)];
  }

  public static boolean containsWords(CharSequence toSearch, List<String> searchValues, boolean matchAll, boolean ignoreCase)
  {
    return containsWords(toSearch, searchValues, matchAll, ignoreCase, false);
  }

  /**
   * Searches for multiples words inside a string.
   *
   * @param toSearch the string in which to search
   * @param searchValues the pattern(s) to search for
   * @param matchAll if true all patterns must be found
   *
   * @return true if searchValues were found
   */
  public static boolean containsWords(CharSequence toSearch, List<String> searchValues, boolean matchAll, boolean ignoreCase, boolean useRegex)
  {
    if (StringUtil.isBlank(toSearch)) return false;
    if (CollectionUtil.isEmpty(searchValues)) return false;

    for (String search : searchValues)
    {
      String expression = null;
      if (useRegex)
      {
        expression = search;
      }
      else
      {
        expression = "(" + StringUtil.quoteRegexMeta(search) + ")";
      }
      int flags = ignoreCase ? Pattern.CASE_INSENSITIVE : 0;
      if (useRegex)
      {
        flags += Pattern.MULTILINE;
      }

      Pattern p = Pattern.compile(expression, flags);
      Matcher m = p.matcher(toSearch);

      boolean found = m.find();

      if (!matchAll && found) return true;
      if (matchAll && !found) return false;
    }
    return matchAll;
  }

  public static String quoteIfNeeded(String input)
  {
    if (isBlank(input)) return input;
    String quote = "\'";
    boolean needQuote = false;
    if (input.indexOf('\'') > -1)
    {
      quote = "\"";
      needQuote = true;
    }
    if (input.indexOf(' ') > -1)
    {
      needQuote = true;
    }
    if (needQuote)
    {
      return quote + input + quote;
    }
    return input;
  }

  public static List<String> getLines(String source)
  {
    if (source == null) return Collections.emptyList();
    return FileUtil.getLines(new BufferedReader(new StringReader(source)), false, false, false, Integer.MAX_VALUE);
  }

  /**
   * Convert a string that is expected to have standard "filename wildcards" to
   * a matching regular expression.
   *
   * <tt>*</tt> and <tt>%</tt> are treated the same.
   * For single character wildcards only a question mark is used.
   * The SQL single character wildcard (<tt>_</tt>) is not supported (even when supportSQLWildcard is true)
   *
   * @param toSearch            the search expression
   * @param supportSQLWildcard  if true, % is also recognized as a wildcard character
   *
   * @return a pattern that can be used as a regular expression
   */
  public static String wildcardToRegex(String toSearch, boolean supportSQLWildcard)
  {
    StringBuilder s = new StringBuilder(toSearch.length() + 5);

    s.append('^');

    for (int i = 0, is = toSearch.length(); i < is; i++)
    {
      char c = toSearch.charAt(i);
      if (c == '*' || (c == '%' && supportSQLWildcard)) // support filesystem wildcards and SQL wildcards
      {
        s.append(".*");
      }
      else if (c == '?' )
      {
        s.append(".");
      }
      else
      {
        if (REGEX_SPECIAL_CHARS.indexOf(c) != -1)
        {
          s.append('\\');
        }
        s.append(c);
      }
    }
    s.append('$');
    return s.toString();
  }

  public static void removeFromEnd(StringBuilder data, int numChars)
  {
    if (data == null) return;

    if (numChars > data.length())
    {
      data.setLength(0);
    }
    else
    {
      data.delete(data.length() - numChars, data.length());
    }
  }


  /**
   * Calculate the start-offset of the line indicated by the position.
   *
   * @param text      the text to check
   * @param position  the position inside the text
   * @return the start offset of the line where position is located in
   */
  public static int getLineStart(CharSequence text, int position)
  {
    if (StringUtil.isEmpty(text)) return 0;
    if (position >= text.length()) return 0;

    int start = 0;

    // find the beginning of the line
    for (int i=position; i > 0; i--)
    {
      char c = text.charAt(i);
      if (c == '\r' || c == '\n')
      {
        start = i + 1;
        break;
      }
    }
    return start;
  }

  public static String trimAfterLineFeed(String line)
  {
    if (line == null) return line;
    Matcher m = Pattern.compile(REGEX_CRLF + "([ \t\f]+)$").matcher(line);
    if (m.find())
    {
      int pos = m.end(1);
      line = line.substring(0,pos);
    }
    return line;
  }

  public static boolean hasLineFeed(String text)
  {
    if (isEmpty(text)) return false;
    if (text.endsWith("\r\n")) return true;
    if (text.endsWith("\n")) return true;
    if (text.endsWith("\n\r")) return true;
    if (text.endsWith("\r")) return true;
    return false;
  }

  /**
   * Calculate the end-offset of the line indicated by the position.
   *
   * @param text      the text to check
   * @param position  the position inside the text
   * @return the end offset of the line where position is located in
   */
  public static int getLineEnd(String text, int position)
  {
    if (StringUtil.isEmpty(text)) return 0;

    int count = text.length();
    int end = count;

    // find the end of the line
    for (int i=position; i < count; i++)
    {
      char c = text.charAt(i);
      if (c == '\r' || c == '\n')
      {
        end = i;
        break;
      }
    }
    return end;
  }

  public static int findNextLineStart(String text, int position)
  {
    int pos = getLineEnd(text, position);
    if (pos <= 0) return 0;
    int count = text.length();
    if (pos >= text.length()) return pos;

    char c = text.charAt(pos);

    while (pos < count - 1 && (c == '\r' || c == '\n'))
    {
      pos ++;
      c = text.charAt(pos);
    }
    return pos;
  }

  public static String arrayToString(String[] values)
  {
    return arrayToString(values, ',');
  }

  public static String arrayToString(String[] values, char delimiter)
  {
    if (values == null || values.length == 0) return "";

    StringBuilder sb = new StringBuilder(values.length);
    for (int i=0; i < values.length; i++)
    {
      if (i > 0) sb.append(delimiter);
      sb.append(values[i]);
    }
    return sb.toString();
  }

  public static int[] stringToArray(String values)
  {
    if (isBlank(values)) return null;
    String[] elements = values.split(",");
    int[] result = new int[elements.length];
    for (int i=0; i < elements.length; i++)
    {
      result[i] = getIntValue(elements[i], 0);
    }
    return result;
  }

  public static String arrayToString(int[] values)
  {
    if (values == null) return null;

    StringBuilder sb = new StringBuilder(values.length);
    for (int i=0; i < values.length; i++)
    {
      if (i > 0) sb.append(',');
      sb.append(Integer.toString(values[i]));
    }
    return sb.toString();
  }

  public static int findOccurance(String value, char toFind, int occurance)
  {
    int found = 0;
    int pos = -1;
    do
    {
      pos = value.indexOf(toFind, pos + 1);
      if (pos == -1)
      {
        break;
      }
      else
      {
        found ++;
      }
    }
    while (found < occurance);

    return pos;
  }

  public static String coalesce(String ... args)
  {
    if (args == null) return null;
    for (String s : args)
    {
      if (s != null) return s;
    }
    return null;
  }

  public static String firstNonBlank(String ... args)
  {
    if (args == null) return null;
    for (String s : args)
    {
      if (isNotBlank(s)) return s;
    }
    return null;
  }

  public static String unescape(String value)
  {
    if (StringUtil.isBlank(value))
    {
      return value;
    }
    if (value.startsWith("&") && value.endsWith(";"))
    {
      return HtmlUtil.unescapeHTML(value);
    }
    if (!value.startsWith("\\"))
    {
      return value;
    }
    return StringUtil.decodeUnicode(value);
  }

}
