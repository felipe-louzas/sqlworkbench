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


import workbench.WbTestCase;

import workbench.db.ConnectionMgr;
import workbench.db.PostgresDbTest;
import workbench.db.WbConnection;

import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
@Category(PostgresDbTest.class)
public class PostgresSchemaReaderTest
  extends WbTestCase
{
  public PostgresSchemaReaderTest()
  {
    super("PostgresSchemaReaderTest");
  }

  @Test
  public void testReadSchema()
    throws Exception
  {
    WbConnection conn = PostgresTestUtil.getPostgresConnection();
    assertNotNull(conn);

    try
    {
      String schema = conn.getCurrentSchema();
      assertEquals("public", schema);
    }
    finally
    {
      ConnectionMgr.getInstance().disconnectAll();
    }
  }

}
