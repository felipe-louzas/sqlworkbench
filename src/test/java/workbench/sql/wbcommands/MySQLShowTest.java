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
package workbench.sql.wbcommands;

import workbench.WbTestCase;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class MySQLShowTest
  extends WbTestCase
{
  public MySQLShowTest()
  {
    super("MySQLShowTest");
  }

  @Test
  public void testCheckSyntax()
    throws Exception
  {
    MySQLShow show = new MySQLShow();
    assertTrue(show.isInnoDBStatus("show engine innodb status"));
    assertFalse(show.isInnoDBStatus("show engine innodb mutex"));
    assertFalse(show.isInnoDBStatus("show create table foobar"));
  }

}
