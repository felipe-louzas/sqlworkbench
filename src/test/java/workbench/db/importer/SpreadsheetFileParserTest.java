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
package workbench.db.importer;

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.ColumnIdentifier;
import workbench.db.TableIdentifier;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class SpreadsheetFileParserTest
  extends WbTestCase
{
  private final String filename = "data.ods";
  private int importedRows;
  private int colCount;

  public SpreadsheetFileParserTest()
  {
    super("TabularDataParser");
  }

  @Test
  public void testSecondSheet()
    throws Exception
  {
    TestUtil util = getTestUtil();
    File input = util.copyResourceFile(this, filename);

    SpreadsheetFileParser parser = new SpreadsheetFileParser();
    parser.setContainsHeader(true);
    parser.setReceiver(getReceiver());
    parser.setInputFile(input);
    parser.setSheetIndex(1);
    try
    {
      colCount = 0;
      importedRows = 0;

      parser.processOneFile();
      assertEquals(4, importedRows);
      assertEquals(4, colCount);
    }
    finally
    {
      parser.done();
    }
    assertTrue(input.delete());
  }

  @Test
  public void testImportByName()
    throws Exception
  {
    TestUtil util = getTestUtil();
    File input = util.getResourceFile(this, filename);

    SpreadsheetFileParser parser = new SpreadsheetFileParser();
    parser.setContainsHeader(true);
    parser.setReceiver(getReceiver());
    parser.setInputFile(input);
    parser.setSheetName("orders");
    try
    {
      colCount = 0;
      importedRows = 0;

      parser.processOneFile();
      assertEquals(4, importedRows);
      assertEquals(4, colCount);
    }
    finally
    {
      parser.done();
    }
  }

  @Test
  public void testFirstSheet()
    throws Exception
  {
    TestUtil util = getTestUtil();
    File input = util.getResourceFile(this, filename);

    SpreadsheetFileParser parser = new SpreadsheetFileParser();
    parser.setContainsHeader(true);
    parser.setReceiver(getReceiver());
    parser.setInputFile(input);

    try
    {
      colCount = 0;
      importedRows = 0;

      parser.processOneFile();
      assertEquals(2, importedRows);
      assertEquals(6, colCount);
    }
    finally
    {
      parser.done();
    }
  }

  private DataReceiver getReceiver()
  {
    return new DataReceiver()
    {
      @Override
      public boolean getCreateTarget()
      {
        return false;
      }

      @Override
      public boolean isColumnExpression(String colName)
      {
        return false;
      }

      @Override
      public boolean shouldProcessNextRow()
      {
        return true;
      }

      @Override
      public void nextRowSkipped()
      {
      }

      @Override
      public void setTableList(List<TableIdentifier> targetTables)
      {
      }

      @Override
      public void deleteTargetTables()
        throws SQLException
      {
      }

      @Override
      public void beginMultiTable()
        throws SQLException
      {
      }

      @Override
      public void endMultiTable()
      {
      }

      @Override
      public void processFile(StreamImporter stream)
        throws SQLException, IOException
      {
      }

      @Override
      public boolean isTransactionControlEnabled()
      {
        return true;
      }

      @Override
      public void processRow(Object[] row)
        throws SQLException
      {
        importedRows ++;
        colCount = row.length;
      }

      @Override
      public void setTableCount(int total)
      {
      }

      @Override
      public void setCurrentTable(int current)
      {
      }

      @Override
      public void setTargetTable(TableIdentifier table, List<ColumnIdentifier> columns, File currentFile)
        throws SQLException
      {
      }

      @Override
      public void importFinished()
      {
      }

      @Override
      public void importCancelled()
      {
      }

      @Override
      public void tableImportError()
      {
      }

      @Override
      public void tableImportFinished()
        throws SQLException
      {
      }

      @Override
      public void recordRejected(String record, long importRow, Throwable e)
      {
      }
    };

  }
}
