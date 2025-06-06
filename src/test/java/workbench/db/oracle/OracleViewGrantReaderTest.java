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
package workbench.db.oracle;

import java.util.Collection;
import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.OracleTest;
import workbench.db.GrantItem;
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
@Category(OracleTest.class)
public class OracleViewGrantReaderTest
  extends WbTestCase
{

  public OracleViewGrantReaderTest()
  {
    super("OracleViewGrantReaderTest");
  }

  @BeforeClass
  public static void setUp()
    throws Exception
  {
    OracleTestUtil.initTestCase();
    WbConnection con = OracleTestUtil.getOracleConnection();
    if (con == null) return;

    String sql = "create table person (id integer primary key, first_name varchar(100), last_name varchar(100), check (id > 0));\n" +
      "create view v_person (id, full_name) as select id, first_name || ' ' || last_name from person;\n" +
      "grant select on v_person to public;\n";
    TestUtil.executeScript(con, sql);
  }

  @AfterClass
  public static void tearDown()
    throws Exception
  {
    OracleTestUtil.cleanUpTestCase();
  }

  @Test
  public void testRetrieveGrants()
    throws Exception
  {
    WbConnection con = OracleTestUtil.getOracleConnection();
    assertNotNull("Oracle not available", con);

    List<TableIdentifier> views = con.getMetadata().getObjectList(null, "WBJUNIT", new String[] { "VIEW" });
    assertEquals(1, views.size());
    TableIdentifier v = views.get(0);
    assertEquals("VIEW", v.getType());
    assertEquals("V_PERSON", v.getTableName());

    String sql = views.get(0).getSource(con).toString();
    assertTrue(sql.contains("GRANT SELECT ON V_PERSON TO PUBLIC"));

    OracleViewGrantReader reader = new OracleViewGrantReader();
    Collection<GrantItem> grants = reader.getViewGrants(con, views.get(0));
    assertNotNull(grants);
    assertEquals(1, grants.size());
    GrantItem grant = grants.iterator().next();
    assertEquals("SELECT", grant.getPrivilege());
    assertEquals("PUBLIC", grant.getGrantee());
  }

}
