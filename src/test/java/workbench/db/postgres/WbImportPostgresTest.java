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

import java.io.File;
import java.sql.ResultSet;
import java.sql.SQLXML;
import java.sql.Statement;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.JdbcUtils;
import workbench.db.PostgresDbTest;
import workbench.db.WbConnection;

import workbench.sql.StatementRunner;
import workbench.sql.StatementRunnerResult;
import workbench.sql.wbcommands.WbImport;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
@Category(PostgresDbTest.class)
public class WbImportPostgresTest
  extends WbTestCase
{

  private static final String TEST_ID = "wb_import_pg";

  public WbImportPostgresTest()
  {
    super(TEST_ID);
  }

  @BeforeClass
  public static void setUp()
    throws Exception
  {
    PostgresTestUtil.initTestCase(TEST_ID);
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    if (con == null) return;

    TestUtil.executeScript(con,
      "create table foo (id integer, firstname text, lastname text);\n" +
      "create table xml_test (id integer, test_data xml);\n" +
      "create table clob_test (id integer, content text);\n" +
      "commit;\n");
  }

  @AfterClass
  public static void tearDown()
    throws Exception
  {
    PostgresTestUtil.cleanUpTestCase();
  }

  @Test
  public void testImportXML()
    throws Exception
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    assertNotNull(con);

    TestUtil util = getTestUtil();
    WbImport cmd = new WbImport();
    cmd.setConnection(con);

    File data = new File(util.getBaseDir(), "foo.txt");
    String content = "id|test_data\n" +
      "1|<xml><person>Arthur Dent</person></xml>\n" +
      "2|\n";
    TestUtil.writeFile(data, content, "UTF-8");
    StatementRunnerResult result = cmd.execute("WbImport -file='" + data.getAbsolutePath() + "' -emptyStringIsNull=true -table=xml_test -type=text -header=true -delimiter='|'");
    assertTrue(result.getMessages().toString(), result.isSuccess());
    Number count = (Number)TestUtil.getSingleQueryValue(con, "select count(*) from xml_test");
    assertEquals(2, count.intValue());

    Object xml = TestUtil.getSingleQueryValue(con, "select test_data from xml_test where id=1");
    assertNotNull(xml);
    SQLXML xmlo = (SQLXML)xml;
    assertEquals("<xml><person>Arthur Dent</person></xml>", xmlo.getString());

    xml = TestUtil.getSingleQueryValue(con, "select test_data from xml_test where id=2");
    assertNull(xml);
  }

  @Test
  public void testImportCopyWithError()
    throws Exception
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    assertNotNull(con);

    TestUtil.executeScript(con,
      "create table t1 (id integer, data text);\n" +
      "create table t2 (id integer, data text);\n" +
      "commit;");

    TestUtil util = getTestUtil();
    StatementRunner runner = util.createConnectedStatementRunner(con);

    String content = "id|data\n1|foo";

    File t1 = new File(util.getBaseDir(), "t1.txt");
    TestUtil.writeFile(t1, content, "UTF-8");

    File t2 = new File(util.getBaseDir(), "t2.txt");
    TestUtil.writeFile(t2, content, "UTF-8");

    File t3 = new File(util.getBaseDir(), "t3.txt");
    TestUtil.writeFile(t3, content, "UTF-8");

    StatementRunnerResult result = runner.runStatement("wbimport -usePgCopy -continueOnError=false -ignoreMissingColumns=true -sourceDir='" + util.getBaseDir() + "' -type=text -delimiter='|';");

//    System.out.println(result.getMessages().toString());
    assertFalse(result.isSuccess());

    int rows = TestUtil.getNumberValue(con, "select count(*) from t1");
    assertEquals(1, rows);

    rows = TestUtil.getNumberValue(con, "select count(*) from t2");
    assertEquals(1, rows);
  }

  @Test
  public void testImportCopy()
    throws Exception
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    assertNotNull(con);

    TestUtil util = getTestUtil();
    StatementRunner runner = util.createConnectedStatementRunner(con);

    File data = new File(util.getBaseDir(), "foo.txt");
    String content = "id|firstname|lastname\n1|Arthur|Dent\n2|Ford|Prefect\n";
    TestUtil.writeFile(data, content, "UTF-8");

    StatementRunnerResult result = runner.runStatement("WbImport -file='" + data.getAbsolutePath() + "' -table=foo -type=text -header=true -delimiter='|' -usePgCopy");

    String msg = result.getMessages().toString();
//    System.out.println(msg);

    assertTrue(msg, result.isSuccess());

    int rows = TestUtil.getNumberValue(con, "select count(*) from foo");
    assertEquals(2, rows);

    content = "id\tfirstname\tlastname\n1\tArthur\tDent\n2\tFord\tPrefect\n";
    TestUtil.writeFile(data, content, "UTF-8");

    result = runner.runStatement("WbImport -truncateTable=true -file='" + data.getAbsolutePath() + "' -table=foo -type=text -header=true -delimiter='\\t' -usePgCopy");

    Statement stmt = null;
    ResultSet rs = null;
    try
    {
      stmt = con.createStatement();
      rs = stmt.executeQuery("select id, firstname, lastname from foo");
      rows = 0;
      while (rs.next())
      {
        rows++;
        int id = rs.getInt(1);
        String fname = rs.getString(2);
        String lname = rs.getString(3);
        if (id == 1)
        {
          assertEquals("Arthur", fname);
          assertEquals("Dent", lname);
        }
        else if (id == 2)
        {
          assertEquals("Ford", fname);
          assertEquals("Prefect", lname);
        }
        else
        {
          fail("Incorrect id imported");
        }
      }
      assertEquals(2, rows);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }

  }

  @Test
  public void testUpsert()
    throws Exception
  {

    WbConnection connection = PostgresTestUtil.getPostgresConnection();
    assertNotNull(connection);

    if (!JdbcUtils.hasMinimumServerVersion(connection, "9.5"))
    {
      // can't test native upsert without 9.5
      return;
    }

    File input = new File(getTestUtil().getBaseDir(), "id_data.txt");

    WbImport importCmd = new WbImport();
    importCmd.setConnection(connection);

    TestUtil.executeScript(connection,
      "CREATE TABLE person (id integer primary key, firstname varchar(50), lastname varchar(50));\n" +
      "commit;\n");

    TestUtil.writeFile(input,
      "id\tfirstname\tlastname\n" +
      "1\tArthur\tDent\n" +
      "2\tFord\tPrefect\n" +
      "3\tZaphod\tBeeblebrox\n",
      "ISO-8859-1");

    StatementRunnerResult result = importCmd.execute(
      "wbimport -file='" + input.getAbsolutePath() + "' " +
      "-type=text " +
      "-header=true " +
      "-continueonerror=false " +
      "-table=person");

    assertTrue(input.delete());

    String msg = result.getMessages().toString();
    assertTrue(msg, result.isSuccess());

    String name = (String)TestUtil.getSingleQueryValue(connection, "select lastname from person where id=1");
    assertEquals("Dent", name);

    name = (String)TestUtil.getSingleQueryValue(connection, "select lastname from person where id=2");
    assertEquals("Prefect", name);

    name = (String)TestUtil.getSingleQueryValue(connection, "select lastname from person where id=3");
    assertEquals("Beeblebrox", name);

    TestUtil.writeFile(input,
      "id\tfirstname\tlastname\n" +
      "1\tArthur\tDENT\n" +
      "2\tFord\tPrefect\n" +
      "4\tTricia\tMcMillan\n",
      "ISO-8859-1");

    result = importCmd.execute(
      "wbimport -file='" + input.getAbsolutePath() + "' " +
      "-type=text " +
      "-mode=upsert " +
      "-header=true " +
      "-useSavepoint=false " +
      "-continueonerror=false " +
      "-table=person");

    assertTrue(input.delete());

    msg = result.getMessages().toString();
    assertTrue(msg, result.isSuccess());

    name = (String)TestUtil.getSingleQueryValue(connection, "select lastname from person where id=1");
    assertEquals("DENT", name);

    name = (String)TestUtil.getSingleQueryValue(connection, "select lastname from person where id=2");
    assertEquals("Prefect", name);

    name = (String)TestUtil.getSingleQueryValue(connection, "select lastname from person where id=3");
    assertEquals("Beeblebrox", name);

    name = (String)TestUtil.getSingleQueryValue(connection, "select firstname from person where id=4");
    assertEquals("Tricia", name);
  }

  @Test
  public void testUpsertWithConstantQuery()
    throws Exception
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    assertNotNull(con);

    String ddl =
      "drop table if exists lookup, data cascade;\n" +
      "create sequence seq_import_test start with 141; \n" +
      "create table lookup (id integer primary key, data varchar(100));\n" +
      "create table data(\n" +
      "  id integer primary key, \n" +
      "  code integer references lookup, \n" +
      "  value varchar(50), \n" +
      "  counter int\n" +
      ");\n" +
      "insert into lookup (id, data) values (100, 'one');\n" +
      "insert into lookup (id, data) values (200, 'two');\n" +
      "insert into lookup (id, data) values (300, 'three');\n" +
      "--insert into data (id, code, value) values (1, 1, null);\n" +
      "--insert into data (id, code, value) values (2, null, 'bla');\n" +
      "commit;";
    TestUtil.executeScript(con, ddl);

    TestUtil util = getTestUtil();
    WbImport cmd = new WbImport();
    cmd.setConnection(con);

    String data =
      "id,code_name,value_plain\n"+
      "1,two,first value\n" +
      "2,one,second value\n" +
      "3,three,third value";

    File importFile = new File(util.getBaseDir(), "lookup_test.txt");
    TestUtil.writeFile(importFile, data, "ISO-8859-1");

    String sql =
      "WbImport -file=\"" + importFile.getAbsolutePath() + "\" -type=text \n" +
      "-table=data \n" +
      "-delimiter=, -mode=upsert \n" +
      "-importColumns=id,value \n" +
      "-constantValues='code=$@{select id from lookup where data = $2}' \n" +
      "-constantValues='value=$@{select upper($3)}' \n" +
      "-constantValues=\"counter=${nextval('seq_import_test')}\"";

    StatementRunnerResult result = cmd.execute(sql);
    if (!result.isSuccess())
    {
      System.out.println(result.getMessages());
    }
    assertTrue(result.isSuccess());
    Number code = (Number)TestUtil.getSingleQueryValue(con, "select code from data where id=1");
    assertEquals(200, code);
    String value = (String)TestUtil.getSingleQueryValue(con, "select value from data where id=1");
    assertEquals("FIRST VALUE", value);
    Number counter = (Number)TestUtil.getSingleQueryValue(con, "select counter from data where id=2");
    assertEquals(142, counter);

    code = (Number)TestUtil.getSingleQueryValue(con, "select code from data where id=2");
    assertEquals(100, code);
    code = (Number)TestUtil.getSingleQueryValue(con, "select code from data where id=3");
    assertEquals(300, code);
  }

  @Test
  public void testTextClobImport()
    throws Exception
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    assertNotNull(con);

    TestUtil util = getTestUtil();
    File importFile = new File(util.getBaseDir(), "import_text_clob.txt");

    TestUtil.writeFile(importFile,
      "id\tcontent\n" +
      "1\ttext_data_r1_c2.data\n" +
      "2\ttext_data_r2_c2.data\n", "UTF-8");

    String[] data = {"This is the string for row 1",
                     "This is the string for row 2"};

    File datafile = new File(util.getBaseDir(), "text_data_r1_c2.data");
    TestUtil.writeFile(datafile, data[0]);

    datafile = new File(util.getBaseDir(), "text_data_r2_c2.data");
    TestUtil.writeFile(datafile, data[1]);

    WbImport importCmd = new WbImport();
    importCmd.setConnection(con);
    StatementRunnerResult result = importCmd.execute(
      "wbimport -encoding=utf8 -file='" + importFile.getAbsolutePath() + "' -clobIsFilename=true " +
      "         -type=text -header=true -continueonerror=false -table=clob_test");

    assertEquals("Import failed: " + result.getMessages().toString(), result.isSuccess(), true);

    Statement stmt = con.createStatement();
    ResultSet rs = stmt.executeQuery("select id, content from clob_test order by id");
    while (rs.next())
    {
      int id = rs.getInt(1);
      String content = rs.getString(2);
      assertEquals(data[id - 1], content);
    }
  }
}
