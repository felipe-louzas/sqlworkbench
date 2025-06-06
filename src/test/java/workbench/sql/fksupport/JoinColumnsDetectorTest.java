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
package workbench.sql.fksupport;

import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;
import workbench.resource.GeneratedIdentifierCase;

import workbench.db.ConnectionMgr;
import workbench.db.QuoteHandler;
import workbench.db.WbConnection;

import workbench.util.TableAlias;

import org.junit.AfterClass;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class JoinColumnsDetectorTest
  extends WbTestCase
{

  public JoinColumnsDetectorTest()
  {
    super("JoinColumnsDetectorTest");
  }

  @AfterClass
  public static void tearDownClass()
  {
    ConnectionMgr.getInstance().disconnectAll();
  }

//  @Before
//  public void setup()
//  {
//    Settings.getInstance().setFormatterIdentifierCase(GeneratedIdentifierCase.lower);
//    Settings.getInstance().setFormatterKeywordsCase(GeneratedIdentifierCase.upper);
//  }

  @Test
  public void testGetJoinSQL()
    throws Exception
  {
    TestUtil util = getTestUtil();
    WbConnection conn = util.getConnection();
    TestUtil.executeScript(conn,
      "create table person (per_id integer not null, tenant_id integer not null, person_name varchar(10), primary key (per_id, tenant_id));\n" +
      "create table address_type (type_id integer primary key, type_name varchar(50));\n" +
      "create table address \n" +
      "( \n" +
      "   adr_id integer primary key, \n" +
      "   address varchar(50), \n " +
      "   person_id integer, \n "+
      "   person_tenant_id integer, \n" +
      "   adr_type_id integer, \n" +
      "   foreign key (person_id, person_tenant_id) \n" +
      "      references person(per_id, tenant_id), \n" +
      "  foreign key (adr_type_id) references address_type (type_id) \n" +
      ");\n" +
      "create table address_history " +
       "( \n " +
      "    ahi_id integer primary key, \n  " +
      "    old_address varchar(50), \n " +
      "    address_id integer, \n " +
      "    foreign key (address_id) references address(adr_id)\n" +
      ");\n" +
      "commit;"
    );

    TableAlias person = new TableAlias("person p");
    TableAlias address = new TableAlias("address a");
    TableAlias history = new TableAlias("address_history ah");
    TableAlias adt = new TableAlias("address_type adt");

    JoinColumnsDetector detector = new JoinColumnsDetector(conn, person, address);
    detector.setPreferUsingOperator(false);
    detector.setIdentifierCase(GeneratedIdentifierCase.lower);
    detector.setKeywordCase(GeneratedIdentifierCase.upper);

    List<JoinCondition> conditions = detector.getJoinConditions();
    assertEquals(1, conditions.size());
    String join = conditions.get(0).getJoinCondition(false, QuoteHandler.STANDARD_HANDLER);
//    System.out.println(join);
    assertTrue(join.contains("p.tenant_id = a.person_tenant_id"));
    assertTrue(join.contains("p.per_id = a.person_id"));

    detector = new JoinColumnsDetector(conn, address, history);
    detector.setIdentifierCase(GeneratedIdentifierCase.lower);
    detector.setKeywordCase(GeneratedIdentifierCase.upper);

    conditions = detector.getJoinConditions();
    assertEquals(1, conditions.size());
    join = conditions.get(0).getJoinCondition(false, QuoteHandler.STANDARD_HANDLER);

    assertEquals("a.adr_id = ah.address_id", join.trim());

    detector = new JoinColumnsDetector(conn, address, adt);
    detector.setPreferUsingOperator(false);
    detector.setIdentifierCase(GeneratedIdentifierCase.lower);
    detector.setKeywordCase(GeneratedIdentifierCase.upper);

    conditions = detector.getJoinConditions();
    assertEquals(1, conditions.size());
    join = conditions.get(0).getJoinCondition(false, QuoteHandler.STANDARD_HANDLER);
    assertEquals("adt.type_id = a.adr_type_id", join.trim());

    detector = new JoinColumnsDetector(conn, address, adt);
    detector.setPreferUsingOperator(true);
    detector.setIdentifierCase(GeneratedIdentifierCase.lower);
    detector.setKeywordCase(GeneratedIdentifierCase.upper);

    conditions = detector.getJoinConditions();
    assertEquals(1, conditions.size());
    join = conditions.get(0).getJoinCondition(false, QuoteHandler.STANDARD_HANDLER);
    assertEquals("adt.type_id = a.adr_type_id", join.trim());
  }

  @Test
  public void testUsingOperator()
    throws Exception
  {
    TestUtil util = getTestUtil();
    WbConnection conn = util.getConnection();
    TestUtil.executeScript(conn,
      "create table foo \n" +
      "( \n" +
      "  foo_id integer not null primary key, \n" +
      "  foo_name varchar(10) \n" +
      ");\n" +
      "create table bar \n" +
      "( \n" +
      "   bar_id integer primary key, \n" +
      "   bar_data varchar(50), \n " +
      "   foo_id integer, \n "+
      "   foreign key (foo_id) references foo(foo_id) \n" +
      ");\n" +
      "commit;"
    );

    TableAlias person = new TableAlias("foo f");
    TableAlias address = new TableAlias("bar b");
    JoinColumnsDetector detector = new JoinColumnsDetector(conn, person, address);
    detector.setPreferUsingOperator(true);
    detector.setIdentifierCase(GeneratedIdentifierCase.lower);
    detector.setKeywordCase(GeneratedIdentifierCase.upper);

    List<JoinCondition> conditions = detector.getJoinConditions();
    assertEquals(1, conditions.size());
    String join = conditions.get(0).getJoinCondition(false, QuoteHandler.STANDARD_HANDLER);

    conditions = detector.getJoinConditions();
    assertEquals(1, conditions.size());

    join = conditions.get(0).getJoinCondition(false, QuoteHandler.STANDARD_HANDLER);
    assertEquals("(foo_id)", join.trim());

    detector.setPreferUsingOperator(false);
    conditions = detector.getJoinConditions();
    join = conditions.get(0).getJoinCondition(false, QuoteHandler.STANDARD_HANDLER);
    assertEquals("f.foo_id = b.foo_id", join.trim());
  }
}
