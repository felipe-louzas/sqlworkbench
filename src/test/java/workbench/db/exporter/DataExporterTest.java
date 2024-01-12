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
package workbench.db.exporter;

import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.ColumnIdentifier;
import workbench.db.ConnectionMgr;
import workbench.db.DbObjectFinder;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;
import workbench.db.importer.ExcelReader;

import workbench.gui.dialogs.export.SpreadSheetOptions;
import workbench.gui.dialogs.export.SqlOptions;
import workbench.gui.dialogs.export.TextOptions;

import workbench.storage.DataStore;

import workbench.sql.parser.ScriptParser;

import workbench.util.CharacterEscapeType;
import workbench.util.CharacterRange;
import workbench.util.CollectionUtil;
import workbench.util.FileUtil;
import workbench.util.QuoteEscapeType;
import workbench.util.SqlUtil;
import workbench.util.WbFile;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class DataExporterTest
  extends WbTestCase
{

  public DataExporterTest()
  {
    super("DataExporterTest");
  }

  @Test
  public void testDuplicateTable()
    throws Exception
  {
    TestUtil util = new TestUtil("DataExporterTest");
    util.emptyBaseDirectory();
    try
    {
      WbConnection con = util.getConnection();

      String script =
        "CREATE SCHEMA one;\n" +
        "CREATE SCHEMA two;\n" +
        "SET SCHEMA one;\n" +
        "CREATE TABLE person (nr integer, firstname varchar(20), lastname varchar(20));\n" +
        "INSERT INTO person (nr, firstname, lastname) VALUES (1, 'Arthur', 'Dent');\n" +
        "INSERT INTO person (nr, firstname, lastname) VALUES (2, 'Zaphod', 'Beeblebrox');\n" +
        "INSERT INTO person (nr, firstname, lastname) VALUES (3, 'Ford', 'Prefect');\n" +
        "INSERT INTO person (nr, firstname, lastname) VALUES (4, 'Tricia', 'McMillian');\n" +
        "SET SCHEMA two;" +
        "CREATE TABLE person (nr2 integer, firstname varchar(20), lastname varchar(20));\n" +
        "INSERT INTO person (nr2, firstname, lastname) VALUES (12, 'Arthur2', 'Dent2');\n" +
        "commit;\n" +
        "SET SCHEMA one;\n";

      TestUtil.executeScript(con, script);

      DataExporter exporter = new DataExporter(con);
      exporter.setOutputType(ExportType.TEXT);
      exporter.setTextOptions(getTextOptions());

      WbFile exportFile = new WbFile(util.getBaseDir(), "schema_table_export.txt");
      exporter.addTableExportJob(exportFile, new TableIdentifier("PERSON"));

      long rowCount = exporter.startExport();
      assertEquals(4, rowCount);
      List<String> lines = TestUtil.readLines(exportFile);
      assertEquals(5, lines.size());
      assertEquals("NR\tFIRSTNAME\tLASTNAME", lines.get(0));
      assertEquals("1\tArthur\tDent", lines.get(1));
    }
    finally
    {
      ConnectionMgr.getInstance().disconnectAll();
    }
  }

  @Test
  public void testExporQueryResult()
    throws Exception
  {
    TestUtil util = new TestUtil("DataExporterTest");
    util.emptyBaseDirectory();
    try
    {
      WbConnection con = util.getConnection();

      String script =
        "CREATE TABLE person (nr integer, firstname varchar(20), lastname varchar(20));\n" +
        "INSERT INTO person (nr, firstname, lastname) " +
        "VALUES " +
        "(1, 'Arthur', 'Dent'),\n" +
        "(2, 'Zaphod', 'Beeblebrox'),\n" +
        "(3, 'Ford', 'Prefect'),\n" +
        "(4, 'Tricia', 'McMillian');\n" +
        "commit;\n";

      TestUtil.executeScript(con, script);

      DataExporter exporter = new DataExporter(con);
      exporter.setOutputType(ExportType.TEXT);
      exporter.setTextOptions(getTextOptions());

      WbFile exportFile = new WbFile(util.getBaseDir(), "query_export.txt");
      exporter.addQueryJob("SELECT * FROM person ORDER BY nr;", exportFile, null);

      long rowCount = exporter.startExport();
      assertEquals(4, rowCount);
      List<String> lines = TestUtil.readLines(exportFile);
      assertEquals(5, lines.size());
      assertEquals("NR\tFIRSTNAME\tLASTNAME", lines.get(0));
      assertEquals("1\tArthur\tDent", lines.get(1));
    }
    finally
    {
      ConnectionMgr.getInstance().disconnectAll();
    }
  }

  @Test
  public void testReplaceValues()
    throws Exception
  {
    TestUtil util = new TestUtil("DataExporterTest");
    util.emptyBaseDirectory();
    try
    {
      WbConnection con = util.getConnection();

      String script =
        "CREATE TABLE person (nr integer, name varchar(20), description varchar(200));\n" +
        "INSERT INTO person (nr, name, description) VALUES (1, 'Arthur Dent', 'this\nshould\r\n\r\nbe\none\n\nline\t');\n" +
        "INSERT INTO person (nr, name, description) VALUES (2, 'Zaphod Beeblebrox', 'Some\tother stuff');\n" +
        "commit;\n";

      TestUtil.executeScript(con, script);

      DataExporter exporter = new DataExporter(con);
      exporter.setOutputType(ExportType.TEXT);
      ExportDataModifier modifier = new RegexReplacingModifier("(\\n|\\r\\n)+", "*");
      exporter.setDataModifier(modifier);
      exporter.setTextOptions(getTextOptions(true, ","));

      WbFile exportFile = new WbFile(util.getBaseDir(), "replaced.txt");
      TableIdentifier tbl = new DbObjectFinder(con).findTable(new TableIdentifier("PERSON"));
      exporter.addTableExportJob(exportFile, tbl);

      long rowCount = exporter.startExport();
      assertEquals(2, rowCount);
      List<String> lines = TestUtil.readLines(exportFile);
      assertEquals(3, lines.size());
      assertEquals("NR,NAME,DESCRIPTION", lines.get(0));
      assertEquals("1,Arthur Dent,this*should*be*one*line\\t", lines.get(1));
      assertEquals("2,Zaphod Beeblebrox,Some\\tother stuff", lines.get(2));
    }
    finally
    {
      ConnectionMgr.getInstance().disconnectAll();
    }
  }

  @Test
  public void testExportWhere()
    throws Exception
  {
    TestUtil util = new TestUtil("DataExporterTest");
    util.emptyBaseDirectory();
    try
    {
      WbConnection con = util.getConnection();

      String script =
        "CREATE TABLE person (nr integer, firstname varchar(20), lastname varchar(20));\n" +
        "INSERT INTO person (nr, firstname, lastname) \n" +
        "VALUES \n" +
        "(1, 'Arthur', 'Dent'),\n" +
        "(2, 'Zaphod', 'Beeblebrox'),\n" +
        "(3, 'Ford', 'Prefect'),\n" +
        "(4, 'Tricia', 'McMillian');\n" +
        "commit;\n";

      TestUtil.executeScript(con, script);

      DataExporter exporter = new DataExporter(con);
      exporter.setOutputType(ExportType.TEXT);
      exporter.setTextOptions(getTextOptions());

      WbFile exportFile = new WbFile(util.getBaseDir(), "query_export.txt");
      //exporter.addQueryJob("SELECT * FROM person ORDER BY nr;", exportFile);
      TableIdentifier tbl = new DbObjectFinder(con).findTable(new TableIdentifier("PERSON"));
      exporter.addTableExportJob(exportFile, tbl, "WHERE nr < 3");

      long rowCount = exporter.startExport();
      assertEquals(2, rowCount);
      List<String> lines = TestUtil.readLines(exportFile);
      assertEquals(3, lines.size());
      assertEquals("NR\tFIRSTNAME\tLASTNAME", lines.get(0));
      assertEquals("1\tArthur\tDent", lines.get(1));
    }
    finally
    {
      ConnectionMgr.getInstance().disconnectAll();
    }
  }

  @Test
  public void testExportColumns()
    throws Exception
  {
    TestUtil util = new TestUtil("DataExporterTest");
    util.emptyBaseDirectory();

    util.disableSqlFormatting();
    try
    {
      WbConnection con = util.getConnection();

      String script =
        "CREATE TABLE person (nr integer, firstname varchar(20), lastname varchar(20));\n" +
        "INSERT INTO person (nr, firstname, lastname) \n" +
        "VALUES \n" +
        "(1, 'Arthur', 'Dent'),\n" +
        "(2, 'Zaphod', 'Beeblebrox'),\n" +
        "(3, 'Ford', 'Prefect'),\n" +
        "(4, 'Tricia', 'McMillian');\n" +
        "commit;\n";

      TestUtil.executeScript(con, script);

      DataExporter exporter = new DataExporter(con);
      exporter.setOutputType(ExportType.TEXT);
      exporter.setTextOptions(getTextOptions());

      Statement stmt = con.createStatement();
      ResultSet rs = stmt.executeQuery("select nr, firstname, lastname from person");

      DataStore ds = new DataStore(rs, con, true);
      ds.setUpdateTable(new TableIdentifier("PERSON"));
      List<ColumnIdentifier> cols = CollectionUtil.arrayList(new ColumnIdentifier("FIRSTNAME"));

      WbFile exportFile = new WbFile(util.getBaseDir(), "column_export.txt");
      long rowCount = exporter.startExport(exportFile, ds, cols);

      assertEquals(4, rowCount);
      List<String> lines = TestUtil.readLines(exportFile);
      assertEquals(5, lines.size());
      assertEquals("FIRSTNAME", lines.get(0));
      assertEquals("Arthur", lines.get(1));

      exporter.setOutputType(ExportType.SQL_INSERT);
      exporter.setSqlOptions(getSqlOptions());
      WbFile sqlFile = new WbFile(util.getBaseDir(), "column_export.sql");
      rowCount = exporter.startExport(sqlFile, ds, cols);
      assertEquals(4, rowCount);
      String inserts = FileUtil.readFile(sqlFile, "ISO-8859-1");
      ScriptParser p = new ScriptParser(inserts);
      assertEquals(5, p.getSize()); // 4 inserts + 1 commit
      String sql = SqlUtil.makeCleanSql(p.getCommand(0), false);
      assertTrue(sql.contains("(FIRSTNAME)"));
    }
    finally
    {
      util.restoreSqlFormatting();
      ConnectionMgr.getInstance().disconnectAll();
    }
  }

  @Test
  public void testXLSXExport()
    throws Exception
  {
    TestUtil util = new TestUtil("XLSXExporterTest");
    util.emptyBaseDirectory();
    try
    {
      WbConnection con = util.getConnection();

      String script =
        "CREATE TABLE data_test (c1 integer, c2 varchar(20), c3 date, c4 timestamp, c5 decimal(14,4));\n" +
        "INSERT INTO data_test (c1, c2, c3, c4, c5) " +
        "VALUES " +
        "(1, 'One', date '2010-11-12', timestamp '2020-01-01 14:15:16', 1234.5678);\n" +
        "commit;\n";
      TestUtil.executeScript(con, script);
      
      DataExporter exporter = new DataExporter(con);
      exporter.setTimestampFormat("yyyy-MM-dd HH:mm:ss");
      exporter.setDateFormat("yyyy-MM-dd");
      exporter.setDecimalDigits(4, ",", false);
      exporter.setOutputType(ExportType.XLSX);
      exporter.setXlsXOptions(getXlsOptions());

      Statement stmt = con.createStatement();
      ResultSet rs = stmt.executeQuery("select c1, c2, c3, c4, c5 from data_test");

      WbFile output = new WbFile(util.getBaseDir(), "data_test.xlsx");
      exporter.exportResultSet(output, rs, script);

      ExcelReader reader = new ExcelReader(output, 0, null);
      reader.load();
      int rowCount = reader.getRowCount();
      assertEquals(2, rowCount); // header + data row
      List<Object> values = reader.getRowValues(1);
      assertEquals(5, values.size());
      Number c1 = (Number)values.get(0);
      assertEquals(1, c1.intValue());
      String c2 = (String)values.get(1);
      assertEquals("One", c2);
      java.util.Date c3 = (java.util.Date)values.get(2);
      Instant i = Instant.ofEpochMilli(c3.getTime());
      LocalDate ld = LocalDate.ofInstant(i, ZoneId.systemDefault());
      assertEquals(2010, ld.getYear());
      assertEquals(11, ld.getMonthValue());
      assertEquals(12, ld.getDayOfMonth());

      Object c4 = values.get(3);
      System.out.println("TS: " + c4.getClass());
      Object c5 = values.get(4);

      reader.done();
    }
    finally
    {
      ConnectionMgr.getInstance().disconnectAll();
    }
  }

  // <editor-fold defaultstate="collapsed" desc=" Text Options ">
  private TextOptions getTextOptions()
  {
    return getTextOptions(false, "\t");
  }

  private TextOptions getTextOptions(final boolean useEscape, final String delimiter)
  {
    return new TextOptions()
    {
      @Override
      public String getTextDelimiter() { return delimiter; }
      @Override
      public boolean getExportHeaders() { return true;  }
      @Override
      public String getTextQuoteChar() { return "\""; }
      @Override
      public boolean getQuoteAlways() { return false; }
      @Override
      public CharacterRange getEscapeRange()
      {
        if (useEscape)
        {
          return CharacterRange.RANGE_CONTROL;
        }
        return CharacterRange.RANGE_NONE;
      }
      @Override
      public String getLineEnding() { return "\n"; }
      @Override
      public String getDecimalSymbol() { return "."; }
      @Override
      public void setExportHeaders(boolean flag) { }
      @Override
      public void setTextDelimiter(String delim) { }
      @Override
      public void setTextQuoteChar(String quote) { }
      @Override
      public void setQuoteAlways(boolean flag) { }
      @Override
      public void setEscapeRange(CharacterRange range) { }
      @Override
      public void setLineEnding(String ending) { }
      @Override
      public void setDecimalSymbol(String decimal) { }
      @Override
      public void setQuoteHeader(boolean flag){}
      @Override
      public boolean getQuoteHeader() {return false;}
      @Override
      public void setEscapeType(CharacterEscapeType type) {}

      @Override
      public CharacterEscapeType getEscapeType()
      {
        return CharacterEscapeType.unicode;
      }

      @Override
      public QuoteEscapeType getQuoteEscaping()
      {
        return QuoteEscapeType.none;
      }

      @Override
      public Set<ControlFileFormat> getControlFiles()
      {
        return Collections.emptySet();
      }

      @Override
      public BlobMode getBlobMode()
      {
        return BlobMode.SaveToFile;
      }
    };
  }
  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc=" SQL Options ">
  private SqlOptions getSqlOptions()
  {
    return new SqlOptions() {

      @Override
      public boolean getCreateTable() { return false; }

      @Override
      public void setCreateTable(boolean flag) { }

      @Override
      public void setCommitEvery(int value) { }

      @Override
      public int getCommitEvery() { return 0; }

      @Override
      public ExportType getExportType() { return ExportType.SQL_INSERT; }

      @Override
      public void setExportType(ExportType type) { }

      @Override
      public String getAlternateUpdateTable() { return null; }

      @Override
      public void setAlternateUpdateTable(String table) { }

      @Override
      public List<String> getKeyColumns()
      {
        return CollectionUtil.arrayList("ID");
      }

      @Override
      public boolean getUseMultiRowInserts()
      {
        return false;
      }

      @Override
      public boolean ignoreIdentityColumns()
      {
        return false;
      }

      @Override
      public String getDateLiteralType()
      {
        return "jdbc";
      }

      @Override
      public String getMergeType()
      {
        return "ansi";
      }

      @Override
      public void setBlobMode(BlobMode mode)
      {
      }

      @Override
      public BlobMode getBlobMode()
      {
        return BlobMode.AnsiLiteral;
      }
    };
  }
  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc=" XLSX Options ">
  private SpreadSheetOptions getXlsOptions()
  {
    return new SpreadSheetOptions()
    {
      @Override
      public String getPageTitle()
      {
        return "Export Test";
      }

      @Override
      public void setPageTitle(String title)
      {
      }

      @Override
      public boolean getExportHeaders()
      {
        return true;
      }

      @Override
      public void setExportHeaders(boolean flag)
      {
      }

      @Override
      public boolean getCreateInfoSheet()
      {
        return false;
      }

      @Override
      public void setCreateInfoSheet(boolean flag)
      {
      }

      @Override
      public boolean getCreateFixedHeaders()
      {
        return false;
      }

      @Override
      public void setCreateFixedHeaders(boolean flag)
      {
      }

      @Override
      public boolean getCreateAutoFilter()
      {
        return false;
      }

      @Override
      public void setCreateAutoFilter(boolean flag)
      {
      }

      @Override
      public boolean getOptimizeColumns()
      {
        return true;
      }

      @Override
      public void setOptimizeColumns(boolean flag)
      {
      }

      @Override
      public boolean getIncludeComments()
      {
        return false;
      }

    };
  }
  // </editor-fold>
}
