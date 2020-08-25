/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2020 Thomas Kellerer.
 *
 * Licensed under a modified Apache License, Version 2.0 (the "License")
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
package workbench.gui.editor;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import workbench.util.StringUtil;
import workbench.util.WbStringTokenizer;

/**
 * Turns multiple lines into a String suitable for a <code>VALUES</code> clause.
 *
 * If the input is e.g.
 * <pre>
 * 1,foo,bar
 * 2,bla,blub
 * </pre>
 * The result will be
 * <pre>
 * (1,'foo','bar'),
 * (2,'bla','blub')
 * </pre>
 * <p>Anything that is clearly a number will not be enclosed in single quotes.
 * If an item is already quoted (single or double quotes) they will be kept.
 * Everything else will be quoted (with single quotes).</p>
 *
 * <p>By default the delimiter is a comma. If the delimiter appears at the start or at the end
 * of the string, it will be removed.</p>
 *
 * <p>By specifying an alter alternate delimiter, input strings like the following can be converted too:</p>
 * <br>
 * <pre>
 * | 42 | Foo | Bar |
 * | 24 | Bar | Foo |
 * </pre>
 *
 * @author Thomas Kellerer
 */
public class ValuesListCreator
{
  private final String input;
  private final String delimiter;
  private String nullString;
  private final boolean useRegex;
  private boolean emptyStringIsNull = true;
  private boolean trimDelimiter = false;
  private boolean trimItems = true;
  private String lineEnding = "\n";
  private WbStringTokenizer tokenizer;
  private Pattern splitPattern;

  public ValuesListCreator(String input)
  {
    this(input, ",", false);
  }

  public ValuesListCreator(String input, String delimiter, boolean isRegex)
  {
    this.input = StringUtil.trim(input);
    this.useRegex = isRegex;
    if (isRegex)
    {
      this.delimiter = delimiter;
    }
    else
    {
      this.delimiter = StringUtil.unescape(delimiter);
    }
    initTokenizer();
  }

  /**
   * Define the string that is treated as a NULL value and will never be quoted.
   */
  public void setNullString(String string)
  {
    this.nullString = string;
  }

  public void setLineEnding(String ending)
  {
    this.lineEnding = ending;
  }

  public void setEmptyStringIsNull(boolean flag)
  {
    this.emptyStringIsNull = flag;
  }

  public void setTrimDelimiter(boolean flag)
  {
    this.trimDelimiter = flag;
  }

  public void setTrimItems(boolean flag)
  {
    this.trimItems = flag;
  }

  public String createValuesList()
  {
    if (StringUtil.isBlank(input)) return "";
    List<String> lines = StringUtil.getLines(input);
    StringBuilder result = new StringBuilder(input.length() + 50);

    int nr = 0;
    for (String line : lines)
    {
      line = line.trim();
      if (!useRegex && trimDelimiter)
      {
        if (line.startsWith(delimiter))
        {
          line = line.substring(1);
        }
        if (line.endsWith(delimiter))
        {
          line = line.substring(0, line.length() - 1);
        }
      }

      List<String> items = splitLine(line);
      if (items == null) continue;

      if (nr > 0)
      {
        result.append(',');
        result.append(lineEnding);
      }

      StringBuilder entry = convertToEntry(items);
      if (entry.length() > 0)
      {
        result.append(entry);
        nr ++;
      }
    }

    return result.toString();
  }

  private void initTokenizer()
  {
    if (this.useRegex)
    {
      splitPattern = Pattern.compile(delimiter);
      tokenizer = null;
    }
    else
    {
      tokenizer = new WbStringTokenizer(delimiter, "\"'", true);
      splitPattern = null;
    }
  }

  private List<String> splitLine(String line)
  {
    if (tokenizer != null)
    {
      if (!line.contains(delimiter)) return null;
      tokenizer.setSourceString(line);
      return tokenizer.getAllTokens();
    }
    else
    {
      if (!splitPattern.matcher(line).find()) return null;
      String[] items = splitPattern.split(line);
      return Arrays.asList(items);
    }
  }

  private StringBuilder convertToEntry(List<String> items)
  {
    StringBuilder result = new StringBuilder(items.size() * 10);
    result.append('(');
    int nr = 0;
    for (String item : items)
    {
      if (trimItems && item != null) item = item.trim();
      if (nr > 0) result.append(", ");
      if (item != null && (item.startsWith("'") || item.startsWith("\"") || StringUtil.isNumber(item)))
      {
        result.append(item);
      }
      else
      {
        if (isNull(item))
        {
          result.append("NULL");
        }
        else
        {
          result.append('\'');
          result.append(item);
          result.append('\'');
        }
      }
      nr ++;
    }
    result.append(')');
    return result;
  }

  private boolean isNull(String item)
  {
    if (item == null) return true;
    if (emptyStringIsNull && StringUtil.isEmptyString(item)) return true;
    if (nullString != null && nullString.equals(item)) return true;
    return false;
  }
}
