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
package workbench.gui;

import java.util.List;

import workbench.resource.ResourceMgr;

import workbench.db.ConnectionProfile;
import workbench.db.WbConnection;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class WindowTitleBuilderTest
{

  public WindowTitleBuilderTest()
  {
  }

  @Test
  public void testBuilder()
    throws Exception
  {
    ConnectionProfile profile = new ConnectionProfile();
    profile.setGroups(List.of("Postgres", "Production", "Shop"));
    profile.setName("Shop-DWH");
    profile.setUrl("jdbc:postgresql://prod-db/shop");
    WbConnection conn = new WbConnection("one", profile)
    {
      @Override
      public String getDisplayCatalog()
      {
        return "postgres";
      }

      @Override
      public String getDisplayString(boolean useDisplaySchema)
      {
        return getDisplayString();
      }

      @Override
      public String getDisplayString()
      {
        return "postgres@shop-db";
      }

      @Override
      public String getDisplayUser()
      {
        return "postgres";
      }

      @Override
      public String getDisplaySchema()
      {
        return "public";
      }
    };
    WindowTitleBuilder builder = new WindowTitleBuilder();
    builder.setShowProfileGroup(true);
    builder.setShowWorkspace(false);
    builder.setShowAppNameAtEnd(false);
    String title = builder.getWindowTitle(conn);
    assertEquals("SQL Workbench/J - Postgres/Production/Shop/Shop-DWH", title);
    builder.setShowAppNameAtEnd(true);
    title = builder.getWindowTitle(conn);
    assertEquals("Postgres/Production/Shop/Shop-DWH - SQL Workbench/J", title);
    builder.setEncloseGroup("[]");
    title = builder.getWindowTitle(conn);
    assertEquals("[Postgres/Production/Shop/Shop-DWH] - SQL Workbench/J", title);
    builder.setShowURL(true);
    builder.setRemoveJDBCProductFromURL(false);
    builder.setEncloseGroup(null);
    title = builder.getWindowTitle(conn);
    assertEquals("postgresql://prod-db/shop - SQL Workbench/J", title);
  }

  @Test
  public void testTemplate()
  {
    WindowTitleBuilder builder = new WindowTitleBuilder();
    builder.setEncloseWksp("[");
    builder.setTitleTemplate("{conn} - {wksp}");
    String title = builder.getWindowTitle(null, null, null);
    assertEquals(ResourceMgr.getString("TxtNotConnected"), title);
  }

  @Test
  public void testMakeCleanUrl()
  {
    WindowTitleBuilder builder = new WindowTitleBuilder();
    String result = builder.makeCleanUrl("jdbc:oracle:thin:@localhost:1521:orcl");
    assertEquals("@localhost:1521:orcl", result);

    result = builder.makeCleanUrl("jdbc:postgresql://localhost/postgres");
    assertEquals("//localhost/postgres", result);

    result = builder.makeCleanUrl("jdbc:jtds:sqlserver://localhost/ThomasDB");
    assertEquals("//localhost/ThomasDB", result);

    result = builder.makeCleanUrl("jdbc:sqlserver://localhost:1433");
    assertEquals("//localhost:1433", result);
  }

}
