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
package workbench.db.postgres;

import java.sql.Types;
import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.ColumnIdentifier;
import workbench.db.TableIdentifier;
import workbench.db.exporter.DataExporter;
import workbench.db.exporter.RowDataConverter;

import workbench.storage.ResultInfo;
import workbench.storage.RowData;

import workbench.util.WbFile;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class PostgresCopyStatementWriterTest
  extends WbTestCase
{

  public PostgresCopyStatementWriterTest()
  {
    super("PostgresCopyStatementWriterTest");
  }

  @Test
  public void testWriteFormatFile()
    throws Exception
  {
    TestUtil util = getTestUtil();
    final WbFile export = new WbFile(util.getBaseDir(), "export.txt");
    DataExporter exporter = new DataExporter(null)
    {

      @Override
      public String getFullOutputFilename()
      {
        return export.getFullPath();
      }

      @Override
      public boolean getExportHeaders()
      {
        return true;
      }

      @Override
      public String getTextDelimiter()
      {
        return "\t";
      }

      @Override
      public String getTextQuoteChar()
      {
        return "\"";
      }
      @Override
      public String getEncoding()
      {
        return "UTF-8";
      }

      @Override
      public String getTableNameToUse()
      {
        return "person";
      }
    };

    ColumnIdentifier id = new ColumnIdentifier("id" ,Types.INTEGER, true);
    ColumnIdentifier firstname = new ColumnIdentifier("firstname", Types.VARCHAR);
    ColumnIdentifier lastname = new ColumnIdentifier("lastname", Types.VARCHAR);
    final TableIdentifier table = new TableIdentifier("person");

    final ResultInfo info = new ResultInfo(new ColumnIdentifier[] { id, firstname, lastname } );
    info.setUpdateTable(table);

    RowDataConverter converter = new RowDataConverter()
    {

      @Override
      public ResultInfo getResultInfo()
      {
        return info;
      }

      @Override
      public StringBuilder convertRowData(RowData row, long rowIndex)
      {
        return new StringBuilder();
      }

      @Override
      public StringBuilder getStart()
      {
        return null;
      }

      @Override
      public StringBuilder getEnd(long totalRows)
      {
        return null;
      }
    };

    try
    {
      PostgresCopyStatementWriter writer = new PostgresCopyStatementWriter();
      writer.writeFormatFile(exporter, converter);
      WbFile formatFile = new WbFile(util.getBaseDir(), "import_export.sql");
      assertTrue(formatFile.exists());

      List<String> contents = TestUtil.readLines(formatFile);
      assertNotNull(contents);
      assertEquals(3, contents.size());
      assertEquals("copy person (id, firstname, lastname)", contents.get(0).trim());
      assertEquals("from '" + export.getName() + "'", contents.get(1).trim());
      String expected = "with (format csv, header true, quote '\"', delimiter E'\\t', encoding 'UTF-8', null '');";
      assertEquals(expected, contents.get(2).trim());

    }
    finally
    {
      util.emptyBaseDirectory();
    }
  }
}
