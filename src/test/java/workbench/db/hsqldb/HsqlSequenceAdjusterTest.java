/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
 * that restricts the use for certain governments.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.sql-workbench.eu/manual/license.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.eu
 */
package workbench.db.hsqldb;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.DbObjectFinder;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class HsqlSequenceAdjusterTest
  extends WbTestCase
{
  public HsqlSequenceAdjusterTest()
  {
    super("SequenceSync");
  }

  @Test
  public void testIdentitySync()
    throws Exception
  {
    TestUtil util = getTestUtil();
    WbConnection con = util.getHSQLConnection("seq_test");

    TestUtil.executeScript(con,
      "create table table_one (id identity not null);\n" +
      "insert into table_one (id) values (1), (2), (7), (41);\n" +
      "commit;" );

    TableIdentifier tbl = new DbObjectFinder(con).findTable(new TableIdentifier("table_one"));

    HsqlSequenceAdjuster sync = new HsqlSequenceAdjuster();
    sync.adjustTableSequences(con, tbl, true);

    TestUtil.executeScript(con,
      "insert into table_one (id) values (default);\n" +
      "commit;" );

    Number value = (Number)TestUtil.getSingleQueryValue(con, "select max(id) from table_one");
    assertEquals(42, value.intValue());
  }

}
