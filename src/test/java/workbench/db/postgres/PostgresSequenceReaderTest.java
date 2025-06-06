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

import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.JdbcUtils;
import workbench.db.PostgresDbTest;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

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
public class PostgresSequenceReaderTest
  extends WbTestCase
{

  private static final String TEST_ID = "ruletest";

  public PostgresSequenceReaderTest()
  {
    super("PostgresSequenceReaderTest");
  }

  @BeforeClass
  public static void setUp()
    throws Exception
  {
    PostgresTestUtil.initTestCase(TEST_ID);
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    if (con == null)
    {
      return;
    }
    TestUtil.executeScript(con,
      "CREATE SEQUENCE seq_one;\n" +
      "CREATE SEQUENCE seq_two cache 25 minvalue 100 increment by 10;\n" +
      "CREATE TABLE seq_table (id integer);\n" +
      "ALTER SEQUENCE seq_one OWNED BY seq_table.id;\n" +
      "COMMIT; \n");
  }

  @AfterClass
  public static void tearDown()
    throws Exception
  {
    PostgresTestUtil.cleanUpTestCase();
  }

  @Test
  public void retrieveSequences()
    throws Exception
  {
    WbConnection con = PostgresTestUtil.getPostgresConnection();
    assertNotNull("No PostgreSQL connection available", con);
    List<TableIdentifier> objects = con.getMetadata().getObjectList(TEST_ID, new String[] { "SEQUENCE" });
    assertEquals(2, objects.size());
    TableIdentifier seq = objects.get(0);
    assertEquals("SEQUENCE", seq.getObjectType());
    String sql = seq.getSource(con).toString();
    String expected =
      "CREATE SEQUENCE IF NOT EXISTS seq_one\n" +
      "       INCREMENT BY 1\n" +
      "       MINVALUE 1\n" +
      "       CACHE 1\n" +
      "       NO CYCLE\n" +
      "       OWNED BY seq_table.id;";
    if (!JdbcUtils.hasMinimumServerVersion(con, "9.5"))
    {
      expected = expected.replace("IF NOT EXISTS ", "");
    }
//    System.out.println(sql + "\n-------------\n" + expected);

    assertEquals(expected, sql.trim());

    seq = objects.get(1);
    sql = seq.getSource(con).toString();
//    System.out.println(sql);
    expected = "CREATE SEQUENCE IF NOT EXISTS seq_two\n" +
             "       INCREMENT BY 10\n" +
             "       MINVALUE 100\n" +
             "       CACHE 25\n" +
             "       NO CYCLE;";
    if (!JdbcUtils.hasMinimumServerVersion(con, "9.5"))
    {
      expected = expected.replace("IF NOT EXISTS ", "");
    }
    assertEquals(expected, sql.trim());
  }

}
