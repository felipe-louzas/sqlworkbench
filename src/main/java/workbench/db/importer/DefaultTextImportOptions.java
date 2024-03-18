/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2024 Thomas Kellerer
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
package workbench.db.importer;

import workbench.resource.Settings;

import workbench.util.QuoteEscapeType;
import workbench.util.StringUtil;

/**
 * @author Thomas Kellerer
 */
public class DefaultTextImportOptions
  implements TextImportOptions
{
  private String delimiter;
  private String quoteChar;
  private String decimalChar;
  private String nullString;
  private boolean quoteAlways = false;
  private boolean containsHeader = true;
  private boolean emptyStringIsNull = false;
  private QuoteEscapeType quoteEscape;
  private boolean decodeUnicode;

  public DefaultTextImportOptions(String delim, String quote)
  {
    this.delimiter = delim;
    this.quoteChar = quote;
  }

  @Override
  public String getTextDelimiter()
  {
    return delimiter;
  }

  @Override
  public boolean getContainsHeader()
  {
    return containsHeader;
  }

  @Override
  public boolean getQuoteAlways()
  {
    return quoteAlways;
  }

  @Override
  public String getTextQuoteChar()
  {
    return quoteChar;
  }

  @Override
  public QuoteEscapeType getQuoteEscaping()
  {
    return quoteEscape == null ? QuoteEscapeType.none : quoteEscape;
  }

  @Override
  public boolean getDecode()
  {
    return decodeUnicode;
  }

  @Override
  public String getDecimalChar()
  {
    return StringUtil.coalesce(decimalChar, Settings.getInstance().getDecimalSymbol());
  }

  @Override
  public void setTextDelimiter(String delim)
  {
    delimiter = delim;
  }

  @Override
  public void setContainsHeader(boolean flag)
  {
    containsHeader = flag;
  }

  @Override
  public void setTextQuoteChar(String quote)
  {
    quoteChar = quote;
  }

  @Override
  public void setDecode(boolean flag)
  {
    this.decodeUnicode = flag;
  }

  @Override
  public void setDecimalChar(String decimal)
  {
    this.decimalChar = decimal;
  }

  @Override
  public String getNullString()
  {
    return nullString;
  }

  @Override
  public void setNullString(String nullString)
  {
    this.nullString = nullString;
  }

  @Override
  public void setEmptyStringIsNull(boolean flag)
  {
    emptyStringIsNull = flag;
  }

  @Override
  public boolean getEmptyStringIsNull()
  {
    return emptyStringIsNull;
  }

  public static TextImportOptions fromOptions(TextImportOptions options)
  {
    DefaultTextImportOptions result = new DefaultTextImportOptions(options.getTextDelimiter(), options.getTextQuoteChar());
    result.emptyStringIsNull = options.getEmptyStringIsNull();
    result.nullString = options.getNullString();
    result.decodeUnicode = options.getDecode();
    result.decimalChar = options.getDecimalChar();
    result.quoteAlways = options.getQuoteAlways();
    result.quoteEscape = options.getQuoteEscaping();
    result.containsHeader = options.getContainsHeader();
    return result;
  }
}
