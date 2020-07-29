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

import java.math.BigDecimal;
import java.util.List;

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
  private boolean trimItems = true;
  private String lineEnding = "\n";

  public ValuesListCreator(String input)
  {
    this(input, ",");
  }

  public ValuesListCreator(String input, String delimiter)
  {
    this.input = StringUtil.trim(input);
    this.delimiter = delimiter;
  }

  public void setLineEnding(String ending)
  {
    this.lineEnding = ending;
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
    WbStringTokenizer tokenizer = new WbStringTokenizer(delimiter, "\"'", true);
    int nr = 0;
    for (String line : lines)
    {
      line = line.trim();
      if (line.startsWith(delimiter))
      {
        line = line.substring(1);
      }
      if (line.endsWith(delimiter))
      {
        line = line.substring(0, line.length() - 1);
      }
      tokenizer.setSourceString(line);
      List<String> items = tokenizer.getAllTokens();
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

  private StringBuilder convertToEntry(List<String> items)
  {
    StringBuilder result = new StringBuilder(items.size() * 10);
    result.append('(');
    int nr = 0;
    for (String item : items)
    {
      if (trimItems) item = item.trim();
      if (nr > 0) result.append(", ");
      if (item.startsWith("'") || item.startsWith("\"") || isNumber(item))
      {
        result.append(item);
      }
      else
      {
        result.append('\'');
        result.append(item);
        result.append('\'');
      }
      nr ++;
    }
    result.append(')');
    return result;
  }

  private boolean isNumber(String value)
  {
    if (StringUtil.isBlank(value)) return false;
    try
    {
      new BigDecimal(value);
      return true;
    }
    catch (Throwable th)
    {
      return false;
    }
  }
}
