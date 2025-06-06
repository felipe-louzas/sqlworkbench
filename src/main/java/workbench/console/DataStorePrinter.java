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
package workbench.console;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.Map;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;

import workbench.storage.*;

import workbench.util.StringUtil;

/**
 * A class to print the contents of a {@link DataStore} to a PrintStream.
 *
 * The column widths are optimized against the content of the DataStore
 * if column formatting {@link ConsolePrinter#setFormatColumns(boolean) }
 * is enabled
 *
 * @author Thomas Kellerer
 */
public class DataStorePrinter
  extends ConsolePrinter
{
  private final String longValueSuffix = " (...)";
  private final DataStore data;
  private int maxDataLength = Integer.MAX_VALUE;

  public DataStorePrinter(DataStore source)
  {
    super();
    this.data = source;
    columnWidths = getColumnSizes();
    printHeader = source.getPrintHeader();
  }

  public void setMaxDisplaySize(int maxLength)
  {
    this.maxDataLength = maxLength;
  }

  @Override
  protected String getResultName()
  {
    if (data == null) return null;
    return data.getResultName();
  }

  @Override
  protected int getColumnType(int col)
  {
    return data.getColumnType(col);
  }

  @Override
  protected String getColumnName(int col)
  {
    if (ConsoleSettings.useDisplayNameForColumns())
    {
      return data.getColumnDisplayName(col);
    }
    return data.getColumnName(col);
  }

  @Override
  protected int getColumnCount()
  {
    return data.getColumnCount();
  }

  @Override
  protected Map<Integer, Integer> getColumnSizes()
  {
    Map<Integer, Integer> widths = new HashMap<>();
    for (int i=0; i < data.getColumnCount(); i++)
    {
      int dataWidth = getMaxDataWidth(i);
      int width = getDataWidthToUse(dataWidth);
      widths.put(i, width);
    }
    return widths;
  }

  private int getMaxDataWidth(int col)
  {
    int width = getColumnName(col).length();
    for (int row = 0; row < data.getRowCount(); row ++)
    {
      RowData rowData = data.getRow(row);
      String value = getDisplayValue(rowData, col);
      if (value != null)
      {
        int len = value.length();
        if (value.indexOf('\n') > -1)
        {
          String line = StringUtil.getLongestLine(value, 25);
          len = line.length();
        }
        if (len > width) width = len;
      }
    }
    return getDataWidthToUse(width);
  }

  private int getDataWidthToUse(int width)
  {
    if (maxDataLength <= 0) return width;
    return Math.min(width, maxDataLength);
  }

  @Override
  protected String getDisplayValue(RowData row, int col)
  {
    String value = super.getDisplayValue(row, col);
    if (maxDataLength <= 0) return value;
    return StringUtil.getMaxSubstring(value, maxDataLength - longValueSuffix.length(), longValueSuffix);
  }

  /**
   * Print all rows to the specified stream.
   *
   * @param out    the print stream to use
   *
   * @see #printTo(java.io.TextPrinter)
   * @see #printTo(java.io.TextPrinter, int[])
   */
  public void printTo(PrintStream out)
  {
    printTo(TextPrinter.createPrinter(new PrintWriter(out)), null);
  }

  /**
   * Print all rows to the specified TextPrinter.
   *
   * @param pw    the TextPrinter to use
   * @see #printTo(java.io.TextPrinter, int[])
   */
  public void printTo(TextPrinter pw)
  {
    printTo(pw, null);
  }

  /**
   * Print rows to the specified TextPrinter.
   *
   * @param pw    the TextPrinter to use
   * @param rows  if <b>null</b> all rows are printed<br/>
   *              if <b>not null</b> only the selected rows are printed
   */
  public void printTo(TextPrinter pw, int[] rows)
  {
    int count = rows == null ? data.getRowCount() : rows.length;
    try
    {
      if (printHeader) printHeader(pw);
      for (int i=0; i < count; i++)
      {
        int row = rows == null ? i : rows[i];
        RowData rowData = data.getRow(row);
        printRow(pw, rowData, row);
        if (cancelled) break;
      }
      if (showRowCount)
      {
        pw.println();
        if (createMarkdownCodeBlock)
        {
          pw.print("    ");
        }
        pw.println(ResourceMgr.getFormattedString("MsgRows", count));
      }
      pw.flush();
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error when printing DataStore contents", e);
    }
  }

}
