/*
 * This file is part of SQL Workbench/J, http://www.sql-workbench.net
 *
 * Copyright 2002-2013, Thomas Kellerer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at.
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.net
 *
 */
package workbench.db;

import java.util.Properties;

import workbench.resource.ResourceMgr;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class JdbcClientInfoBuilderTest
{

  @Test
  public void testGetAS400Info()
  {
    JdbcClientInfoBuilder builder = new JdbcClientInfoBuilder("adent", "test-id");
    Properties props = builder.getClientInfo("jdbc:as400://hostname/database_name");
    assertEquals(props.get("ApplicationName"), ResourceMgr.TXT_PRODUCT_NAME);
    assertEquals(props.get("ClientUser"), System.getProperty("user.name"));
    assertEquals(props.get("ClientProgramID"), "test-id");
  }

  @Test
  public void testGetHanaInfo()
  {
    JdbcClientInfoBuilder builder = new JdbcClientInfoBuilder("adent", "test-id");
    Properties props = builder.getClientInfo("jdbc:sap://localhost:30015");
    assertEquals(props.get("APPLICATION"), ResourceMgr.TXT_PRODUCT_NAME);
    assertEquals(props.get("APPLICATIONUSER"), System.getProperty("user.name"));
    assertEquals(props.get("APPLICATIONSOURCE"), "test-id");
    assertNotNull(props.get("APPLICATIONVERSION"));
  }

}
