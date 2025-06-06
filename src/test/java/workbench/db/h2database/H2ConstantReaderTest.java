/*
 * H2ConstantReaderTest.java
 *
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
package workbench.db.h2database;

import workbench.db.DbObject;
import java.util.List;
import workbench.TestUtil;
import workbench.db.ConnectionMgr;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;
import org.junit.AfterClass;
import org.junit.Test;
import workbench.WbTestCase;
import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class H2ConstantReaderTest
  extends WbTestCase
{

  public H2ConstantReaderTest()
  {
    super("H2ConstantReaderTest");
  }

  @AfterClass
  public static void tearDown()
  {
    ConnectionMgr.getInstance().disconnectAll();
  }

  @Test
  public void testGetConstantsList()
    throws Exception
  {
    TestUtil util = getTestUtil();
    WbConnection con = util.getConnection();

    String script = "CREATE CONSTANT THE_ANSWER VALUE 42;";
    TestUtil.executeScript(con, script);

    List<TableIdentifier> objects = con.getMetadata().getObjectList(null, new String[] { "CONSTANT" });
    assertNotNull(objects);
    assertEquals(1, objects.size());

    DbObject dbo = con.getMetadata().getObjectDefinition(objects.get(0));
    assertTrue(dbo instanceof H2Constant);
    H2Constant constant = (H2Constant)dbo;
    assertEquals("THE_ANSWER", constant.getObjectName());
    assertEquals("INTEGER", constant.getDataType());
    assertEquals("42", constant.getValue());
  }

}
