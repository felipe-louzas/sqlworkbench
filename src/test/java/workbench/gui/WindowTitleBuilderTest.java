/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer
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

import workbench.resource.ResourceMgr;

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
  public void testTemplate()
  {
    WindowTitleBuilder builder = new WindowTitleBuilder();
    builder.setTitleTemplate("{conn} - ({wksp})");
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
