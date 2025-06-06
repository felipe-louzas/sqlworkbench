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
package workbench.db.importer.detector;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.util.CsvLineParser;
import workbench.util.CsvLineReader;
import workbench.util.EncodingUtil;
import workbench.util.FileUtil;
import workbench.util.QuoteEscapeType;
import workbench.util.StringUtil;
import workbench.util.ValueConverter;
import workbench.util.WbFile;

/**
 * A class to detect a table structure from a CSV file.
 *
 * @author Thomas Kellerer
 */
public class TextFileTableDetector
  extends TableDetector
{
  private final String encoding;
  private boolean enableMultiline;

  private final CsvLineParser parser;

  public TextFileTableDetector(File importFile, String delimiter, String quoteChar, String dateFmt, String timestampFmt, boolean containsHeader, String fileEncoding)
  {
    inputFile = new WbFile(importFile);
    withHeader = containsHeader;
    encoding = fileEncoding;

    converter = new ValueConverter(dateFmt, timestampFmt);
    converter.setLogWarnings(false);

    char quote = 0;
    if (StringUtil.isNotEmpty(quoteChar))
    {
      quote = quoteChar.charAt(0);
    }
    parser = new CsvLineParser(delimiter, quote);
    parser.setReturnEmptyStrings(true);
    parser.setTrimValues(true);
  }

  public void setDecimalChar(char decimal)
  {
    converter.setDecimalCharacter(decimal);
  }

  public void setEnableMultiline(boolean flag)
  {
    this.enableMultiline = flag;
  }

  public void setQuoteEscape(QuoteEscapeType type)
  {
    parser.setQuoteEscaping(type);
  }

  @Override
  protected void processFile()
  {
    int lineNr = 0;
    BufferedReader in = null;

    String lineEnd = StringUtil.LINE_TERMINATOR;
    if (enableMultiline)
    {
      lineEnd = FileUtil.getLineEnding(inputFile, encoding);
    }

    try
    {
      in = EncodingUtil.createBufferedReader(inputFile, encoding);
      CsvLineReader reader = new CsvLineReader(in, parser.getQuoteChar(), parser.getEscapeType(), enableMultiline, lineEnd);

      String firstLine = reader.readLine();
      if (firstLine == null) return;
      lineNr ++;

      initColumns(firstLine);

      String line = reader.readLine();
      while (line != null && lineNr < sampleSize)
      {
        List<String> values = parseLine(line);
        analyzeValues(values, columns);
        line = reader.readLine();
        lineNr ++;
      }
    }
    catch (IOException io)
    {
      messages.append(io.getLocalizedMessage());
      LogMgr.logError(new CallerInfo(){}, "Could not read file " + inputFile.getAbsolutePath(), io);
    }
    finally
    {
      FileUtil.closeQuietely(in);
    }
  }

  private void initColumns(String headerLine)
  {
    List<String> values = parseLine(headerLine);
    columns = new ArrayList<>(values.size());

    for (int i = 0; i < values.size(); i++)
    {
      String colName;
      if (withHeader)
      {
        colName = values.get(i);
      }
      else
      {
        colName = "column_" + Integer.valueOf(i + 1);
      }
      columns.add(new ColumnStatistics(colName));
    }
  }

  private List<String> parseLine(String line)
  {
    List<String> values = new ArrayList<>();
    if (StringUtil.isEmpty(line)) return values;

    parser.setLine(line);
    while (parser.hasNext())
    {
      values.add(parser.getNext());
    }
    return values;
  }

}
