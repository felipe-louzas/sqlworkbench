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

import java.util.ArrayList;
import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Thomas Kellerer
 */
public class ObjectScripterTest
  extends WbTestCase
{
  public ObjectScripterTest()
  {
    super("ObjectScripterTest");
  }

  private void setupDatabase(WbConnection conn)
    throws Exception
  {
    String script =
      "create sequence test_sequence;\n" +
      "create table person (id integer primary key, firstname varchar(50), lastname varchar(50)); \n" +
      "create table address (id integer primary key, person_id integer not null, address_info varchar(500)); \n" +
      "alter table address add constraint fk_adr_per foreign key (person_id) references person(id); \n" +
      "create view v_person as select * from person\n;";

    TestUtil.executeScript(conn, script);
  }

  @Test
  public void testGenerateScript()
    throws Exception
  {
    TestUtil util = getTestUtil();
    WbConnection con = util.getConnection();
    setupDatabase(con);
    List<DbObject> objects = new ArrayList<>();

    objects.add(new SequenceDefinition(null, "TEST_SEQUENCE"));
    TableIdentifier tbl = new TableIdentifier("PERSON");
    tbl.setType("TABLE");
    objects.add(tbl);

    tbl = new TableIdentifier("ADDRESS");
    tbl.setType("TABLE");
    objects.add(tbl);

    objects.add(new ViewDefinition("V_PERSON"));
    ObjectScripter scripter = new ObjectScripter(objects, con);
    scripter.setUseSeparator(false);
    String script = scripter.getScript();
//    System.out.println(script);

    int personPos = script.indexOf("CREATE TABLE PERSON");
    assertTrue(personPos > -1);

    int addressPos = script.indexOf("CREATE TABLE ADDRESS");
    assertTrue(addressPos > -1);

    int seqPos = script.indexOf("CREATE SEQUENCE TEST_");
    assertTrue(seqPos > -1);

    assertTrue(personPos > seqPos);
    assertTrue(addressPos > seqPos);

    int fkPos = script.indexOf("CONSTRAINT FK_ADR_PER");
    assertTrue(fkPos > personPos);
    assertTrue(fkPos > addressPos);

    int viewPos = script.indexOf("CREATE VIEW V_PERSON");
    assertTrue(viewPos > -1);
    assertTrue(viewPos > personPos);
    assertTrue(viewPos > addressPos);
  }
}
