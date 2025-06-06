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

import java.io.BufferedReader;
import java.io.File;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.ColumnIdentifier;
import workbench.db.JdbcUtils;
import workbench.db.PostgresDbTest;
import workbench.db.TableDefinition;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;
import workbench.db.importer.TextImportOptions;

import workbench.sql.StatementRunner;
import workbench.sql.StatementRunnerResult;
import workbench.sql.parser.ScriptParser;

import workbench.util.CollectionUtil;
import workbench.util.EncodingUtil;
import workbench.util.QuoteEscapeType;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
@Category(PostgresDbTest.class)
public class PgCopyImporterTest
  extends WbTestCase
{
  public PgCopyImporterTest()
  {
    super("PgCopyImporterTest");
  }

  @Test
  public void testImport()
    throws Exception
  {
    PostgresTestUtil.initTestCase("copy_importer");
    WbConnection conn = PostgresTestUtil.getPostgresConnection();
    assertNotNull(conn);

    TestUtil.executeScript(conn,
      "create table foo (id integer, firstname varchar(6), lastname text);\n" +
      "commit;\n");

    TestUtil util = getTestUtil();
    File data = new File(util.getBaseDir(), "foo.txt");
    String content =
      "id|firstname|lastname\n" +
      "1|Arthur|Dent\n" +
      "2|Ford|Prefect\n" +
      "3|������|Something\n";

    TestUtil.writeFile(data, content, "UTF-8");
    PgCopyManager copy = new PgCopyManager(conn);

    TableDefinition def = conn.getMetadata().getTableDefinition(new TableIdentifier("foo"));
    BufferedReader in = EncodingUtil.createBufferedReader(data, "UTF-8");
    TextImportOptions options = createOptions();
    options.setTextDelimiter("|");
    options.setContainsHeader(true);
    copy.setup(def.getTable(), def.getColumns(), in, options, "UTF-8");
    long rows = copy.processStreamData();
    conn.commit();
    assertEquals(3, rows);
    Statement stmt = null;
    ResultSet rs = null;
    try
    {
      stmt = conn.createStatement();
      rs = stmt.executeQuery("select count(*) from foo");
      if (rs.next())
      {
        rows = rs.getInt(1);
        assertEquals(3, rows);
      }
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
      PostgresTestUtil.cleanUpTestCase();
    }
  }

  @Test
  public void testSqlCopy()
    throws Exception
  {
    PostgresTestUtil.initTestCase("copy_importer");
    WbConnection conn = PostgresTestUtil.getPostgresConnection();
    assertNotNull(conn);
    if (!JdbcUtils.hasMinimumServerVersion(conn, "9.0")) return;

    TestUtil.executeScript(conn,
      "create table person (id integer, firstname text, lastname text);\n" +
      "commit;\n");

    StatementRunner runner = new StatementRunner();
    runner.setConnection(conn);

    ScriptParser parser = ScriptParser.createScriptParser(conn);
    String sql =
      "copy person (id, firstname, lastname) from stdin WITH (format csv, header false, delimiter ',');   \n" +
      "1,Arthur,Dent\n" +
      "2,Ford,Prefect\n" +
      "\\.\n" +
      "commit;\n";

    parser.setScript(sql);
    int size = parser.getSize();
    assertEquals(2, size);
    StatementRunnerResult result = runner.runStatement(parser.getCommand(0));
    assertTrue(result.getMessages().toString(), result.isSuccess());
    assertEquals(2, result.getTotalUpdateCount());
    runner.runStatement(parser.getCommand(1));

    int count = TestUtil.getNumberValue(conn, "select count(*) from person");
    assertEquals(2,count);
  }

  @Test
  public void testCreateSql()
  {
    PgCopyManager copy = new PgCopyManager(null);
    TableIdentifier tbl = new TableIdentifier("foo");
    ColumnIdentifier id = new ColumnIdentifier("id");
    ColumnIdentifier name = new ColumnIdentifier("name");
    List<ColumnIdentifier> cols = CollectionUtil.arrayList(id, name);
    TextImportOptions options = createOptions();

    String sql = copy.createCopyStatement(tbl, cols, options);
    System.out.println(sql);
    assertEquals("COPY foo (id,name) FROM stdin WITH (format csv, header true, quote '\"', delimiter '|', NULL '')", sql);
    options.setContainsHeader(false);
    options.setTextDelimiter("\t");
    cols = CollectionUtil.arrayList(name, id);
    sql = copy.createCopyStatement(tbl, cols, options);
    assertEquals("COPY foo (name,id) FROM stdin WITH (format csv, header false, quote '\"', delimiter E'\\t', NULL '')", sql);
  }

  private TextImportOptions createOptions()
  {
    return new TextImportOptions()
    {
      private boolean containsHeader = true;
      private String delimiter = "|";
      private String quote = "\"";

      @Override
      public String getTextDelimiter()
      {
        return delimiter;
      }

      @Override
      public void setTextDelimiter(String delim)
      {
        delimiter = delim;
      }

      @Override
      public boolean getContainsHeader()
      {
        return containsHeader;
      }

      @Override
      public void setContainsHeader(boolean flag)
      {
        containsHeader = flag;
      }

      @Override
      public String getTextQuoteChar()
      {
        return quote;
      }

      @Override
      public void setTextQuoteChar(String quote)
      {
        this.quote = quote;
      }

      @Override
      public boolean getDecode()
      {
        return false;
      }

      @Override
      public void setDecode(boolean flag)
      {
      }

      @Override
      public String getDecimalChar()
      {
        return ".";
      }

      @Override
      public void setDecimalChar(String s)
      {
      }

      @Override
      public String getNullString()
      {
        return null;
      }

      @Override
      public void setNullString(String nullString)
      {
      }

      @Override
      public void setEmptyStringIsNull(boolean flag)
      {
      }

      @Override
      public boolean getEmptyStringIsNull()
      {
        return false;
      }

      @Override
      public QuoteEscapeType getQuoteEscaping()
      {
        return QuoteEscapeType.none;
      }

      @Override
      public boolean getQuoteAlways()
      {
        return false;
      }
    };
  }
}
