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
package workbench.db.report;

import workbench.util.HtmlUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class TagAttribute
{
  private final CharSequence tagText;

  public TagAttribute(String name, boolean value)
  {
    this(name, Boolean.toString(value));
  }
  
  public TagAttribute(String name, int value)
  {
    this(name, Integer.toString(value));
  }

  public TagAttribute(String name, String value)
  {
    int len = name == null ? 0 : name.length();
    len += value == null ? 0 : value.length();
    StringBuilder b = new StringBuilder(len);
    b.append(name);
    b.append("=\"");
    b.append(value == null ? "" : HtmlUtil.escapeXML(value));
    b.append('"');
    tagText = b;
  }

  public CharSequence getTagText()
  {
    return tagText;
  }

}
