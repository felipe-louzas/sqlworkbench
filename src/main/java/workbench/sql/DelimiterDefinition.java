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

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * Encapsulate the alternate delimiter
 * @author Thomas Kellerer
 */
public class DelimiterDefinition
  implements Serializable
{
  /**
   * The default delimiter for ANSI SQL: a semicolon
   */
  public static final DelimiterDefinition STANDARD_DELIMITER = new DelimiterDefinition(";", true);

  /**
   * A default alternate delimiter. This is Oracle's slash on a single line
   */
  public static final DelimiterDefinition DEFAULT_ORA_DELIMITER = new DelimiterDefinition("/", true);

  /**
   * A default alternate delimiter that matches SQL Server's GO command
   */
  public static final DelimiterDefinition DEFAULT_MS_DELIMITER = new DelimiterDefinition("GO", true);

  public static final String ORA_ABBREVIATION = "oracle";
  public static final String SQLSERVER_ABBREVIATION = "mssql";
  public static final List<String> ABBREVIATIONS = Collections.unmodifiableList(CollectionUtil.arrayList(ORA_ABBREVIATION, SQLSERVER_ABBREVIATION));

  private String delimiter;
  private boolean singleLineDelimiter;
  private boolean changed;
  private Pattern slePattern;
  private boolean isImmutable;
  private boolean isStandard;

  public DelimiterDefinition()
  {
    this.delimiter = "";
    this.changed = false;
    this.slePattern = null;
  }

  private DelimiterDefinition(String delim, boolean immutable)
  {
    setDelimiter(delim);
    this.changed = false;
    initPattern();
    isImmutable = immutable;
  }

  public DelimiterDefinition(String delim)
  {
    setDelimiter(delim);
    this.changed = false;
    initPattern();
  }

  public DelimiterDefinition createCopy()
  {
    if (this.isImmutable) return this;
    DelimiterDefinition copy = new DelimiterDefinition(this.delimiter);
    copy.changed = false;
    return copy;
  }

  public boolean isEmpty()
  {
    return (this.delimiter == null || this.delimiter.length() == 0);
  }

  public boolean isStandard()
  {
    return this.isStandard;
  }

  public boolean isNonStandard()
  {
    return !this.isStandard();
  }

  public void resetChanged()
  {
    this.changed = false;
  }

  public static DelimiterDefinition parseCmdLineArgument(String arg)
  {
    if (StringUtil.isEmpty(arg)) return null;

    arg = arg.trim();
    if ("ORA".equalsIgnoreCase(arg) || ORA_ABBREVIATION.equalsIgnoreCase(arg) || "SQLPLUS".equalsIgnoreCase(arg))
    {
      return DEFAULT_ORA_DELIMITER;
    }
    else if (SQLSERVER_ABBREVIATION.equalsIgnoreCase(arg) || "sqlserver".equalsIgnoreCase(arg))
    {
      return DEFAULT_MS_DELIMITER;
    }

    if (arg.equals(";"))
    {
      return STANDARD_DELIMITER;
    }

    String delim = arg;

    int pos = arg.indexOf(':');
    if (pos == -1)
    {
      pos = arg.indexOf(';', 1);
    }

    if (pos > -1)
    {
      delim  = delim.substring(0, pos);
    }
    return new DelimiterDefinition(delim);
  }

  @Override
  public String toString()
  {
    return delimiter;
  }

  public String getDelimiter()
  {
    return this.delimiter;
  }

  public final void setDelimiter(String newDelimiter)
  {
    if (isImmutable) return;
    if (newDelimiter == null) return;

    if (StringUtil.stringsAreNotEqual(this.delimiter, newDelimiter))
    {
      this.delimiter = newDelimiter.trim();
      this.singleLineDelimiter = !delimiter.equals(";");
      this.changed = true;
      this.isStandard = delimiter.equals(";");
      initPattern();
    }
  }

  public boolean isChanged()
  {
    return this.changed;
  }

  public boolean isSingleLine()
  {
    return this.singleLineDelimiter;
  }

  public void setSingleLine(boolean flag)
  {
    if (isImmutable) return;

    if (flag != this.singleLineDelimiter)
    {
      this.singleLineDelimiter = flag;
      this.changed = true;
      initPattern();
    }
  }

  /**
   * Return true if the given SQL script ends
   * with this delimiter
   * @param sql
   */
  public boolean terminatesScript(String sql, boolean checkNonStandardComments, char nonStandardQuoteChar)
  {
    if (StringUtil.isEmpty(sql)) return false;

    // cleaning the SQL from all "noise" ensures that the alternate delimiter is still
    // recognized even if the script is terminated with only comments.
    sql = SqlUtil.makeCleanSql(sql, true, false, checkNonStandardComments, false, nonStandardQuoteChar);

    if (this.isSingleLine())
    {
      return slePattern.matcher(sql).find();
    }
    else
    {
      return sql.endsWith(this.delimiter);
    }
  }

  public boolean equals(String other)
  {
    return StringUtil.equalStringIgnoreCase(this.delimiter, other);
  }

  @Override
  public boolean equals(Object other)
  {
    if (other == null) return false;

    if (other instanceof DelimiterDefinition)
    {
      DelimiterDefinition od = (DelimiterDefinition)other;
      if (this.singleLineDelimiter == od.singleLineDelimiter)
      {
        return StringUtil.equalStringIgnoreCase(this.delimiter, od.delimiter);
      }
      return false;
    }
    else if (other instanceof String)
    {
      return StringUtil.equalStringIgnoreCase(this.delimiter, (String)other);
    }
    return false;
  }

  @Override
  public int hashCode()
  {
    return (this.delimiter + Boolean.toString(this.singleLineDelimiter)).hashCode();
  }

  private void initPattern()
  {
    if (this.singleLineDelimiter && this.delimiter != null)
    {
      slePattern = Pattern.compile("(?i)[\\r\\n|\\n]+[ \t]*" + StringUtil.quoteRegexMeta(this.delimiter) + "[ \t]*[\\r\\n|\\n]*$");
    }
    else
    {
      slePattern = null;
    }
  }

  public String removeFromEnd(String sql)
  {
    if (StringUtil.isEmpty(sql)) return sql;
    int startPos = -1;
    if (this.isSingleLine())
    {
      Matcher m = slePattern.matcher(sql);
      boolean found = m.find();
      if (found)
      {
        startPos = m.start();
      }
    }
    else
    {
      startPos = sql.lastIndexOf(this.delimiter);
    }
    if (startPos > -1)
    {
      return sql.substring(0,startPos).trim();
    }
    return sql;
  }

  public String getScriptText()
  {
    if (this.isNonStandard())
    {
      return "\n" + delimiter + "\n";
    }
    return delimiter;
  }

}
