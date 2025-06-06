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
package workbench.db;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import workbench.WbTestCase;

import workbench.sql.DelimiterDefinition;

import workbench.util.CollectionUtil;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Thomas Kellerer
 */
public class ConnectionProfileTest
  extends WbTestCase
{
  public ConnectionProfileTest()
  {
    super("ConnectionProfileTest");
  }

  @Test
  public void testNonEmptyGroup()
  {
    ConnectionProfile profile = new ConnectionProfile();
    assertFalse(profile.getGroups().isEmpty());
  }

  @Test
  public void testSetGroupPathByString()
    throws Exception
  {
    ConnectionProfile profile = new ConnectionProfile();
    profile.setAutocommit(true);
    profile.setDriverName("Postgres");
    profile.setName("Test Group Path");
    profile.setGroupByPathString("/Production/Shops/Postgres");
    assertEquals(List.of("Production", "Shops", "Postgres"), profile.getGroups());
  }

  @Test
  public void testCreateCopy()
    throws Exception
  {
    ConnectionProfile old = new ConnectionProfile();
    old.setAlternateDelimiter(new DelimiterDefinition("/"));
    old.setAutocommit(false);
    old.setConfirmUpdates(true);
    old.setDriverName("Postgres");
    old.setConnectionTimeout(42);
    old.setEmptyStringIsNull(true);
    old.setUseSeparateConnectionPerTab(true);
    old.setDetectOpenTransaction(true);
    old.setIgnoreDropErrors(true);
    old.setStoreExplorerSchema(true);
    old.setName("First");
    old.setStorePassword(true);
    old.setCopyExtendedPropsToSystem(true);
    old.setIncludeNullInInsert(true);
    old.setEchoConnectScriptStatements(false);
    old.setIdleTime(42);
    old.setTrimCharData(true);
    old.setIdleScript("select 12 from dual");
    old.setPostConnectScript("drop database");
    old.setPreDisconnectScript("shutdown abort");
    old.setUrl("jdbc:some:database");
    old.setHideWarnings(true);
    old.setRemoveComments(true);
    old.setPromptForUsername(true);
    old.setTagList("pg,ora");
    List<String> groups = List.of("Production", "Postgres", "DWH");
    old.setGroups(groups);
    ObjectNameFilter filter = new ObjectNameFilter();
    filter.addExpression("^pg_toast.*");
    assertTrue(old.isChanged());
    filter.resetModified();
    old.setCatalogFilter(filter);

    ConnectionProfile copy = old.createCopy();
    // Make sure the groups of the copy are not modified if the old ones change
    old.setGroups(List.of("Production", "Postgres", "Shop"));
    assertFalse(copy.getAutocommit());
    assertTrue(copy.getConfirmUpdates());
    assertTrue(copy.getDetectOpenTransaction());
    assertEquals("Postgres", copy.getDriverName());
    assertEquals("First", copy.getName());
    assertTrue(copy.getStorePassword());
    assertTrue(copy.getUseSeparateConnectionPerTab());
    assertTrue(copy.getStoreExplorerSchema());
    assertTrue(copy.getIgnoreDropErrors());
    assertTrue(copy.getTrimCharData());
    assertTrue(copy.getIncludeNullInInsert());
    assertTrue(copy.getRemoveComments());
    assertTrue(copy.getPromptForUsername());
    assertNull(copy.getSchemaFilter());
    assertNotNull(copy.getCatalogFilter());
    assertEquals(1, copy.getCatalogFilter().getSize());
    assertEquals(42, copy.getIdleTime());
    assertEquals(filter, copy.getCatalogFilter());
    assertEquals(Integer.valueOf(42), copy.getConnectionTimeout());

    Set<String> tags = CollectionUtil.caseInsensitiveSet("ORA","pg");

    assertEquals(tags, copy.getTags());
    assertEquals(groups, copy.getGroups());

    assertEquals("select 12 from dual", old.getIdleScript());
    assertEquals("jdbc:some:database", copy.getUrl());
    assertTrue(copy.isHideWarnings());

    assertEquals("drop database", old.getPostConnectScript());
    assertEquals("shutdown abort", old.getPreDisconnectScript());

    DelimiterDefinition delim = copy.getAlternateDelimiter();
    assertNotNull(delim);
    assertEquals("/", delim.getDelimiter());
    assertTrue(delim.isSingleLine());
    assertTrue(copy.getCopyExtendedPropsToSystem());

    old.setAlternateDelimiter(null);
    copy = old.createCopy();
    assertNull(copy.getAlternateDelimiter());

    old.setStoreCacheLocally(true);
    copy = old.createCopy();
    assertTrue(copy.getStoreCacheLocally());
  }

  @Test
  public void testProps()
    throws Exception
  {
    ConnectionProfile profile = new ConnectionProfile();
    profile.setName("ProfileTest");
    profile.setAlternateDelimiter(new DelimiterDefinition("/"));
    profile.setAutocommit(false);
    profile.setConfirmUpdates(true);
    profile.setDriverName("Postgres");
    profile.resetChangedFlags();

    Properties props = new Properties();
    props.setProperty("remarksReporting", "true");
    profile.setConnectionProperties(props);
    assertTrue(profile.isChanged());
    assertEquals("true", profile.getConnectionProperties().getProperty("remarksReporting"));
    assertEquals(1, profile.getConnectionProperties().size());
    profile.setCopyExtendedPropsToSystem(true);
    profile.setEchoConnectScriptStatements(true);
    assertTrue(profile.isChanged());

    profile.setAutocommit(true);
    profile.setConfirmUpdates(false);
    assertTrue(profile.isChanged());

    profile.setAutocommit(true);
    profile.setConfirmUpdates(false);
    assertTrue(profile.isChanged());

    profile.setUrl("jdbc:postgres:local");
    assertTrue(profile.isChanged());

    profile.setUrl("jdbc:postgres:local");
    assertTrue(profile.isChanged());

    profile.setHideWarnings(false);
    profile.resetChangedFlags();
    profile.setHideWarnings(true);
    assertTrue(profile.isChanged());

    profile.resetChangedFlags();
    // Changing to a new URL has to be reflected
    profile.setUrl("jdbc:postgres:local;someProp=myValue");
    assertTrue(profile.isChanged());

    profile.setInputPassword("welcome");
    profile.setStorePassword(true);
    profile.resetChangedFlags();

    // check if changing the password sets the changed flag
    profile.setInputPassword("secret");
    assertTrue(profile.isChanged());

    profile.setStorePassword(false);
    profile.resetChangedFlags();
    profile.setInputPassword("welcome");
    // password are not saved, changing the password should not mark the profile
    // as changed
    assertFalse(profile.isChanged());

    profile.setEmptyStringIsNull(false);
    profile.resetChangedFlags();
    profile.setEmptyStringIsNull(true);
    assertTrue(profile.isChanged());
    profile.setEmptyStringIsNull(true);
    assertTrue(profile.isChanged());

    profile.setUseSeparateConnectionPerTab(false);
    profile.resetChangedFlags();
    profile.setUseSeparateConnectionPerTab(true);
    assertTrue(profile.isChanged());
    profile.setUseSeparateConnectionPerTab(true);
    assertTrue(profile.isChanged());

    profile.setStoreExplorerSchema(false);
    profile.resetChangedFlags();
    profile.setStoreExplorerSchema(true);
    assertTrue(profile.isChanged());
    profile.setStoreExplorerSchema(true);
    assertTrue(profile.isChanged());

    profile.resetChangedFlags();
    profile.setDriverName("Postgres 8.3");
    assertTrue(profile.isChanged());

    profile.resetChangedFlags();
    profile.setName("NewName");
    assertTrue(profile.isChanged());

    profile.setTrimCharData(false);
    profile.resetChangedFlags();
    profile.setTrimCharData(true);
    assertTrue(profile.isChanged());

    profile.setIgnoreDropErrors(false);
    profile.resetChangedFlags();
    profile.setIgnoreDropErrors(true);
    assertTrue(profile.isChanged());

    profile.setRollbackBeforeDisconnect(false);
    profile.resetChangedFlags();
    profile.setRollbackBeforeDisconnect(true);
    assertTrue(profile.isChanged());

    profile.setReadOnly(false);
    profile.resetChangedFlags();
    profile.setReadOnly(true);
    assertTrue(profile.isChanged());

    profile.resetChangedFlags();
    DelimiterDefinition def = new DelimiterDefinition("GO");
    profile.setAlternateDelimiter(def);
    assertTrue(profile.isChanged());

    profile.resetChangedFlags();
    profile.setInfoDisplayColor(Color.MAGENTA);
    assertTrue(profile.isChanged());

    profile.resetChangedFlags();
    profile.setWorkspaceFile("Arthur.wksp");
    assertTrue(profile.isChanged());

    profile.resetChangedFlags();
    profile.setDefaultFetchSize(4242);
    assertTrue(profile.isChanged());

    profile.setConnectionTimeout(1);
    profile.resetChangedFlags();
    profile.setConnectionTimeout(42);
    assertTrue(profile.isChanged());
  }

  @Test
  public void testFindInList()
  {
    ConnectionProfile profile = new ConnectionProfile();
    profile.setAlternateDelimiter(new DelimiterDefinition("/"));
    profile.setAutocommit(false);
    profile.setDriverName("Postgres");
    profile.setEmptyStringIsNull(true);
    profile.setIgnoreDropErrors(true);
    profile.setName("First");
    profile.setGroups(List.of("Primary"));
    profile.setStorePassword(true);

    List<ConnectionProfile> profiles = new ArrayList<>();
    profiles.add(profile);

    ConnectionProfile profile2 = new ConnectionProfile();
    profile2.setAutocommit(false);
    profile2.setDriverName("PostgreSQL");
    profile2.setEmptyStringIsNull(false);
    profile2.setIgnoreDropErrors(false);
    profile2.setName("First");
    profile2.setGroups(List.of("Primary"));
    profile2.setStorePassword(false);

    profiles.remove(profile2);
    profiles.add(profile2);
    assertEquals(1, profiles.size());
  }

  @Test
  public void testGetPwd()
  {
    ConnectionProfile profile = new ConnectionProfile();
    profile.setStorePassword(false);
    profile.setPassword("welcome");
    profile.setTemporaryUsername("arthur");
    assertEquals("welcome", profile.getLoginPassword());
    assertNull(profile.getPassword());
    assertEquals("arthur", profile.getLoginUser());
  }

  @Test
  public void testGetSettingsKey()
  {
    ConnectionProfile profile = new ConnectionProfile();
    profile.setName("Some/Connection");
    profile.setGroups(List.of("Default==Group"));
    String key = profile.getSettingsKey();
    assertEquals("defaultgroup.someconnection", key);
  }
}
