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
package workbench.db.firebird;

import java.util.Collection;
import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.FirebirdDbTest;
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
@Category(FirebirdDbTest.class)
public class FirebirdViewGrantReaderTest
  extends WbTestCase
{

  public FirebirdViewGrantReaderTest()
  {
    super("FirebirdViewGrantReaderTest");
  }

  @BeforeClass
  public static void setUp()
    throws Exception
  {
    FirebirdTestUtil.initTestCase();
    WbConnection con = FirebirdTestUtil.getFirebirdConnection();
    if (con == null) return;

    TestUtil.executeScript(con,
      "create table foo (id integer);\n" +
      "create view v_foo as select * from foo;\n" +
      "grant select on v_foo to public;");
  }

  @AfterClass
  public static void tearDown()
    throws Exception
  {
    FirebirdTestUtil.cleanUpTestCase();
  }


  @Test
  public void testUpdateColumnDefinition()
    throws Exception
  {
    WbConnection con = FirebirdTestUtil.getFirebirdConnection();
    assertNotNull("No connection available", con);

    List<TableIdentifier> tables = con.getMetadata().getTableList();
    assertNotNull(tables);
    assertEquals(1, tables.size());

    List<TableIdentifier> views = con.getMetadata().getObjectList(null, new String[] { "VIEW"});
    assertNotNull(views);
    assertEquals(1, views.size());

    FirebirdViewGrantReader reader = new FirebirdViewGrantReader();
    Collection<GrantItem> grants = reader.getViewGrants(con, views.get(0));
    assertNotNull(grants);
    assertEquals(1, grants.size());
    GrantItem grant = grants.iterator().next();
    assertEquals("SELECT", grant.getPrivilege());
    assertEquals("PUBLIC", grant.getGrantee());
  }

}
