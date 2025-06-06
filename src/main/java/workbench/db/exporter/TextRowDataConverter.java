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
package workbench.db.exporter;

import java.io.File;
import java.util.Collection;
import java.util.Set;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.DbSettings;
import workbench.db.WbConnection;

import workbench.storage.DataConverter;
import workbench.storage.RowData;
import workbench.storage.reader.RowDataReader;

import workbench.util.CharacterEscapeType;
import workbench.util.CharacterRange;
import workbench.util.CollectionUtil;
import workbench.util.QuoteEscapeType;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * Converts data from the database into text files.
 *
 * The format of the output can be modified for different needs.
 *
 * The following things can be configured
 * <ul>
 *  <li>Timestamp and Date formats</li>
 *  <li>Decimal characters</li>
 *  <li>Escaping of non-ASCI characters</li>
 *  <li>Line ending uses</li>
 *  <li>Column delimiter</li>
 * </ul>
 * @author  Thomas Kellerer
 */
public class TextRowDataConverter
  extends RowDataConverter
{
  private String delimiter = "\t";
  private String quoteCharacter;
  private boolean quoteAlways;
  private CharacterRange escapeRange = CharacterRange.RANGE_NONE;
  private String delimiterAndQuote;
  private String lineEnding = StringUtil.LINE_TERMINATOR;
  private boolean writeBlobFiles = true;
  private boolean writeClobFiles;
  private boolean quoteWarningAdded;
  private boolean abortOnMissingQuoteChar;
  private QuoteEscapeType quoteEscape = QuoteEscapeType.none;
  private String rowIndexColumnName;
  private DataConverter converter;
  private CharacterEscapeType escapeType;
  private String escapedQuote;
  private final Set<String> clobColumns = CollectionUtil.caseInsensitiveSet();

  public TextRowDataConverter()
  {
    this.abortOnMissingQuoteChar = Settings.getInstance().getAbortExportWithMissingQuoteChar();
  }

  public void setAbortOnMissingQuoteChar(boolean flag)
  {
    this.abortOnMissingQuoteChar = flag;
  }

  public void setWriteClobToFile(boolean flag)
  {
    this.writeClobFiles = flag;
  }

  public void setWriteBlobToFile(boolean flag)
  {
    writeBlobFiles = flag;
  }

  @Override
  public void setOriginalConnection(WbConnection conn)
  {
    super.setOriginalConnection(conn);
    converter = RowDataReader.getConverterInstance(conn);
  }

  public void setClobColumns(Collection<String> extraColumns)
  {
    clobColumns.clear();
    if (extraColumns != null)
    {
      clobColumns.addAll(extraColumns);
    }
  }

  /**
   * Define a column name to include the rowindex in the output
   * If the name is null, the rowindex column will not be written.
   *
   * @param colname
   */
  public void setRowIndexColName(String colname)
  {
    if (StringUtil.isEmpty(colname))
    {
      this.rowIndexColumnName = null;
    }
    else
    {
      this.rowIndexColumnName = colname;
    }
  }

  @Override
  public StringBuilder getEnd(long totalRows)
  {
    return null;
  }

  public void setQuoteEscaping(QuoteEscapeType type)
  {
    if (type != null)
    {
      this.quoteEscape = type;
    }
  }

  public QuoteEscapeType getQuoteEscaping()
  {
    return this.quoteEscape;
  }

  @Override
  public StringBuilder convertRowData(RowData row, long rowIndex)
  {
    return convertRowData(row, rowIndex, null);
  }

  private boolean isConverted(int jdbcType, String dbmsType)
  {
    if (converter == null) return false;
    return converter.convertsType(jdbcType, dbmsType);
  }

  public StringBuilder convertRowData(RowData row, long rowIndex, int[] colMap)
  {
    int count = this.metaData.getColumnCount();
    StringBuilder result = new StringBuilder(count * 30);

    boolean hasQuoteChar = this.quoteCharacter != null;

    DbSettings dbs = originalConnection != null ? this.originalConnection.getDbSettings() : null;

    final CallerInfo ci = new CallerInfo(){};

    int currentColIndex = 0;

    if (rowIndexColumnName != null)
    {
      result.append(Long.toString(rowIndex + 1));
      result.append(this.delimiter);
    }

    for (int c=0; c < count; c++)
    {
      int colIndex = getRealIndex(c, colMap);
      if (!this.includeColumnInExport(colIndex)) continue;

      if (currentColIndex > 0)
      {
        result.append(this.delimiter);
      }

      int colType = this.metaData.getColumnType(colIndex);
      String dbmsType = this.metaData.getDbmsTypeName(colIndex);
      String colName = this.metaData.getColumnName(colIndex);
      String value = null;

      boolean addQuote = quoteAlways;
      boolean isConverted = false;

      if (converter != null)
      {
        isConverted = isConverted(colType, dbmsType);
      }

      if (!isConverted && writeBlobFiles && SqlUtil.isBlobType(colType))
      {
        try
        {
          File blobFile = createBlobFile(row, colIndex, rowIndex);
          value = getBlobFileValue(blobFile);
          long blobSize = writeBlobFile(row.getValue(colIndex), blobFile);
          if (blobSize <= 0)
          {
            value = null;
          }
        }
        catch (Exception e)
        {
          LogMgr.logError(ci, "Error writing BLOB file", e);
          throw new RuntimeException("Error writing BLOB file", e);
        }
      }
      else if (!isConverted && writeClobFiles && (clobColumns.contains(colName) || isClob(dbmsType, colType, dbs)))
      {
        Object clobData = row.getValue(colIndex);
        if (clobData != null)
        {
          try
          {
            File clobFile = createBlobFile(row, colIndex, rowIndex);
            value = getBlobFileValue(clobFile);
            String s = clobData.toString();
            writeClobFile(s, clobFile, this.encoding);
          }
          catch (Exception e)
          {
            LogMgr.logError(ci, "Error writing CLOB file", e);
            throw new RuntimeException("Error writing CLOB file", e);
          }
        }
      }
      else
      {
        value = this.getValueAsFormattedString(row, colIndex);
      }

      if (value == null)
      {
        value = getNullDisplay();
        // Never quote null values
        boolean quoteNulls = exporter == null ? false : exporter.getQuoteNulls();
        addQuote = value.isEmpty() ? quoteNulls : quoteAlways;
      }
      else if (needsQuoting(colType, dbmsType))
      {
        addQuote = needsQuotes(value);

        if (this.escapeRange != CharacterRange.RANGE_NONE)
        {
          if (addQuote)
          {
            value = StringUtil.escapeText(value, this.escapeRange, this.quoteCharacter, getEscapeType());
          }
          else
          {
            value = StringUtil.escapeText(value, this.escapeRange, this.delimiterAndQuote, getEscapeType());
          }
        }

        if (this.quoteEscape == QuoteEscapeType.escape)
        {
          // the escaping of the backslash has to be done before escaping the quote character
          value = StringUtil.replace(value, "\\", "\\\\");
        }

        if (this.quoteEscape != QuoteEscapeType.none && hasQuoteChar)
        {
          value = StringUtil.replace(value, this.quoteCharacter, this.escapedQuote);
        }
      }

      if (addQuote && !hasQuoteChar && !quoteWarningAdded)
      {
        String msg = ResourceMgr.getString("ErrExportNoQuoteChar");
        if ((exporter != null && exporter.getContinueOnError()) || !abortOnMissingQuoteChar)
        {
          quoteWarningAdded = true;
          exporter.addWarning(msg);
        }
        else if (abortOnMissingQuoteChar)
        {
          throw new IllegalStateException(msg);
        }
      }

      if (addQuote && hasQuoteChar) result.append(this.quoteCharacter);
      result.append(value);
      if (addQuote && hasQuoteChar) result.append(this.quoteCharacter);

      currentColIndex ++;
    }
    result.append(lineEnding);
    return result;
  }

  private boolean needsQuoting(int colType, String dbmsType)
  {
    if (SqlUtil.isCharacterType(colType)) return true;
    return typesNeedingQuotes.contains(dbmsType);
  }

  private boolean isClob(String dbmsType, int jdbcType, DbSettings dbs)
  {
    if (SqlUtil.isClobType(jdbcType, dbmsType, dbs)) return true;
    boolean treatXMLAsClob = dbs != null && dbs.exportXMLAsClob();
    if (treatXMLAsClob && SqlUtil.isXMLType(jdbcType, dbmsType)) return true;
    return false;
  }

  private boolean needsQuotes(String value)
  {
    if (quoteAlways) return true;
    if (value == null) return false;

    boolean containsDelimiter = value.contains(this.delimiter);
    boolean containsLineFeed = lineEnding != null && value.contains(this.lineEnding);
    return containsDelimiter || containsLineFeed;
  }

  public void setLineEnding(String ending)
  {
    if (ending != null) this.lineEnding = ending;
  }

  @Override
  public StringBuilder getStart()
  {
    return getStart(null);
  }

  public StringBuilder getStart(int[] colMap)
  {
    this.setAdditionalEncodeCharacters();
    this.initEscapedQuote();

    if (!this.writeHeader) return null;

    boolean quoteHeader = exporter != null ? exporter.getQuoteHeader() : false;

    int colCount = this.metaData.getColumnCount();
    StringBuilder result = new StringBuilder(colCount * 10);

    boolean first = true;
    if (rowIndexColumnName != null)
    {
      result.append(rowIndexColumnName);
      first = false;
    }

    for (int c=0; c < colCount; c ++)
    {
      int colIndex = getRealIndex(c, colMap);
      if (!this.includeColumnInExport(colIndex)) continue;
      String name = SqlUtil.removeObjectQuotes(this.metaData.getColumnDisplayName(colIndex));

      if (first)
      {
        first = false;
      }
      else
      {
        result.append(delimiter);
      }

      boolean addQuotes = false;
      if (quoteHeader)
      {
        addQuotes = needsQuotes(name);
      }
      if (addQuotes) result.append(quoteCharacter);
      result.append(name);
      if (addQuotes) result.append(quoteCharacter);
    }
    result.append(lineEnding);
    return result;
  }

  private int getRealIndex(int colIndex, int[] colMap)
  {
    if (colMap == null) return colIndex;
    if (colIndex >= colMap.length) return -1;
    return colMap[colIndex];
  }

  public void setDelimiter(String delimit)
  {
    if (StringUtil.isBlank(delimit)) return;

    if (delimit.contains("\\t"))
    {
      this.delimiter = delimit.replace("\\t", "\t");
    }
    else
    {
      this.delimiter = delimit;
    }
    setAdditionalEncodeCharacters();
  }

  private void setAdditionalEncodeCharacters()
  {
    if (this.escapeRange == CharacterRange.RANGE_NONE) return;
    if (this.quoteCharacter == null && this.delimiter == null) return;

    this.delimiterAndQuote = this.delimiter;

    // Make sure we have a quote character if quoteAlways was requested
    if (quoteAlways && this.quoteCharacter == null) quoteCharacter="\"";

    // If values should always be quoted, then we need to
    // escape the quote character in values
    if (this.quoteCharacter != null)
    {
      this.delimiterAndQuote += this.quoteCharacter;
    }
  }

  public void setQuoteCharacter(String quote)
  {
    if (StringUtil.isNotBlank(quote))
    {
      this.quoteCharacter = quote;
      setAdditionalEncodeCharacters();
    }
  }

  private void initEscapedQuote()
  {
    if (this.quoteCharacter != null)
    {
      switch (quoteEscape)
      {
        case duplicate:
          this.escapedQuote = this.quoteCharacter + this.quoteCharacter;
          break;
        case escape:
          this.escapedQuote = '\\' + this.quoteCharacter;
          break;
        default:
          this.escapedQuote = this.quoteCharacter;
      }
    }
    else
    {
      this.escapedQuote = null;
    }
  }

  public void setQuoteAlways(boolean flag)
  {
    this.quoteAlways = flag;
  }

  /**
   *  Define the range of characters to be escaped
   *  @see workbench.util.StringUtil
   */
  public void setEscapeRange(CharacterRange range)
  {
    if (range != null)
    {
      this.escapeRange = range;
    }
  }

  public void setEscapeType(CharacterEscapeType type)
  {
    this.escapeType = type;
  }

  public CharacterEscapeType getEscapeType()
  {
    if (this.escapeType == null && exporter != null)
    {
      return exporter.getEscapeType();
    }
    return escapeType;
  }
}
