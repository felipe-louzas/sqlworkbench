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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import workbench.interfaces.ResultSetConsumer;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;

import workbench.storage.ResultInfo;
import workbench.storage.RowData;
import workbench.storage.reader.ResultHolder;
import workbench.storage.reader.ResultSetHolder;
import workbench.storage.reader.RowDataReader;
import workbench.storage.reader.RowDataReaderFactory;

import workbench.sql.StatementRunnerResult;

import workbench.util.CollectionUtil;
import workbench.util.WbFile;

/**
 * A class to print the contents of a ResultSet to a PrintStream.
 * The column widths are calculated by the suggested display size of the
 * columns of the ResultSet
 *
 * @see workbench.db.ColumnIdentifier#getDisplaySize()
 *
 * @author Thomas Kellerer
 */
public class ResultSetPrinter
  extends ConsolePrinter
  implements ResultSetConsumer, PropertyChangeListener
{
  private static final int MAX_WIDTH = 80;
  private TextPrinter pw;
  private ResultInfo info;
  private WbFile pagerToUse;

  public ResultSetPrinter(TextPrinter printer)
  {
    super();
    this.pw = printer;
  }

  public ResultSetPrinter(PrintStream out)
    throws SQLException
  {
    super();
    setOutput(out);
  }

  public void setExternalPager(WbFile pager)
  {
    this.pagerToUse = pager;
  }

  public void setOutput(PrintStream out)
  {
    this.pw = TextPrinter.createPrinter(new PrintWriter(out));
  }

  @Override
  public boolean ignoreMaxRows()
  {
    return false;
  }

  @Override
  public void cancel()
    throws SQLException
  {
  }

  @Override
  public void done()
  {
  }

  @Override
  protected String getResultName()
  {
    return null;
  }

  @Override
  protected int getColumnType(int col)
  {
    return (info == null ? Types.OTHER : info.getColumnType(col));
  }

  @Override
  protected int getColumnCount()
  {
    return (info == null ? 0 : info.getColumnCount());
  }

  @Override
  protected String getColumnName(int col)
  {
    if (info == null) return "";
    if (ConsoleSettings.useDisplayNameForColumns())
    {
      return info.getColumnDisplayName(col);
    }
    return info.getColumnName(col);
  }

  @Override
  protected Map<Integer, Integer> getColumnSizes()
  {
    Map<Integer, Integer> widths = new HashMap<>();
    if (info == null) return widths;

    for (int i=0; i < info.getColumnCount(); i++)
    {
      int nameWidth = getColumnName(i).length();
      int colSize = info.getColumn(i).getDisplaySize();

      int width = Math.max(nameWidth, colSize);
      width = Math.min(width, MAX_WIDTH);
      widths.put(Integer.valueOf(i), Integer.valueOf(width));
    }
    return widths;
  }

  @Override
  public void consumeResult(StatementRunnerResult toConsume)
  {
    if (toConsume == null) return;
    if (!toConsume.isSuccess()) return;

    List<ResultSet> results = toConsume.getResultSets();
    if (CollectionUtil.isEmpty(results)) return;

    for (ResultSet rs : toConsume.getResultSets())
    {
      printResultSet(rs, toConsume.getShowRowCount());
    }
  }

  public void printResultSet(ResultSet data)
  {
    printResultSet(data, showRowCount);
  }

  public void printResultSet(ResultSet data, boolean showRowCount)
  {
    TextPrinter output = pw;
    ExternalPager pager = null;
    if (pagerToUse != null && pagerToUse.exists())
    {
      pager = new ExternalPager(this.pagerToUse);
      if (pager.isValid())
      {
        pager.initialize();
        output = TextPrinter.createPrinter(new PrintWriter(pager.getOutput()));
      }
    }

    try
    {
      info = new ResultInfo(data.getMetaData(), null);
      columnWidths = getColumnSizes();
      if (printHeader) printHeader(pw);

      RowDataReader reader = RowDataReaderFactory.createReader(info, null);
      int count = 0;
      ResultHolder rh = new ResultSetHolder(data);
      while (data.next())
      {
        RowData row = reader.read(rh, false);
        printRow(output, row, count);
        reader.closeStreams();
        count ++;
        if (cancelled) break;
      }

      if (showRowCount)
      {
        output.println();
        output.println(ResourceMgr.getFormattedString("MsgRows", count));
      }
      output.flush();
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error when printing ResultSet", e);
    }
    finally
    {
      if (pager != null)
      {
        pager.waitFor();
        pager.done();
      }
    }
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt)
  {
    if (evt.getSource() != ConsoleSettings.getInstance()) return;

    if (evt.getPropertyName().equals(ConsoleSettings.EVT_PROPERTY_ROW_DISPLAY))
    {
      RowDisplay newDisplay = ConsoleSettings.getInstance().getNextRowDisplay();
      setPrintRowsAsLine(newDisplay == RowDisplay.SingleLine);
    }
    else if (evt.getPropertyName().equals(ConsoleSettings.PROP_NULL_STRING))
    {
      nullString = ConsoleSettings.getNullString();
    }
  }

}
