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
package workbench.db.mysql;

import workbench.AppArguments;
import workbench.TestUtil;

import workbench.db.ConnectionMgr;
import workbench.db.ConnectionProfile;
import workbench.db.WbConnection;

import workbench.sql.BatchRunner;

import workbench.util.ArgumentParser;

/**
 *
 * @author Thomas Kellerer
 */
public class MySQLTestUtil
{

  public static final String TEST_USER = "wbjunit";
  public static final String TEST_PWD = "wbjunit";
  public static final String PROFILE_NAME = "WBJUnitMySQL";
  public static final String DB_NAME = "wbjunit";

  /**
   * Return a connection to a locally running MySQL database
   */
  public static WbConnection getMySQLConnection()
  {
    try
    {
      WbConnection con = ConnectionMgr.getInstance().findConnection(PROFILE_NAME);
      if (con != null) return con;

      ArgumentParser parser = new AppArguments();
      parser.parse("-url='jdbc:mysql://localhost/" + DB_NAME + "?serverTimezone=GMT ' -username=" + TEST_USER + " -password=" + TEST_PWD + " -driver=com.mysql.cj.jdbc.Driver");
      ConnectionProfile prof = BatchRunner.createCmdLineProfile(parser);
      prof.setName(PROFILE_NAME);
      ConnectionMgr.getInstance().addProfile(prof);
      con = ConnectionMgr.getInstance().getConnection(prof, PROFILE_NAME);
      return con;
    }
    catch (Throwable th)
    {
      th.printStackTrace();
      return null;
    }
  }

  public static void cleanUpTestCase()
  {
    ConnectionMgr.getInstance().disconnectAll();
  }

  public static void initTestcase(String name)
    throws Exception
  {
    TestUtil util = new TestUtil(name);
    util.prepareEnvironment();
  }
}
