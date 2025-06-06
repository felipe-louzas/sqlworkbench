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
package workbench.util;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class LobFileStatementTest
  extends WbTestCase
{
  private TestUtil util;

  public LobFileStatementTest()
  {
    super("LobFileStatementTest");
    util = getTestUtil();
  }

  @Before
  public void setUp()
  {
    util.emptyBaseDirectory();
  }

  @Test
  public void testSyntaxError()
  {
    boolean hasException = false;
    try
    {
      String sql = "update bla set col = {$blobfile=dummy_file.data where x = 1";
      LobFileStatement stmt = new LobFileStatement(sql);
    }
    catch (FileNotFoundException e)
    {
      // a FileNotFound is not expected as the syntax is not correct
      hasException = false;
    }
    catch (IllegalArgumentException e)
    {
      hasException = true;
    }
    assertEquals("Wrong exception or no exception thrown", true, hasException);

    try
    {
      String sql = "insert into test (x,y,z) values (1,2, {$blobfile=dummy_file.data)";
      LobFileStatement stmt = new LobFileStatement(sql);
      hasException = false;
    }
    catch (FileNotFoundException e)
    {
      // a FileNotFound is not expected as the syntax is not correct
      hasException = false;
    }
    catch (IllegalArgumentException e)
    {
      hasException = true;
    }
    assertEquals("Wrong exception or no exception thrown", true, hasException);

    try
    {
      String sql = "insert into test (x,y,z) values (1,2, {$blobfile=dummy_file_should_not_be_found.data})";
      LobFileStatement stmt = new LobFileStatement(sql);
      hasException = false;
    }
    catch (FileNotFoundException e)
    {
      hasException = true;
    }
    catch (IllegalArgumentException e)
    {
      // The syntax is correct, it should not throw an exception
      hasException = false;
    }
    assertEquals("Wrong exception or no exception thrown", true, hasException);

  }

  @Test
  public void testGetParameterCount()
    throws Exception
  {
    File f = new File(util.getBaseDir(), "test.data");
    try
    {
      // LobFileStatement checks for the presence of the file!
      f.createNewFile();
      String sql = "update bla set col = {$blobfile=" + f.getAbsolutePath() + "} where x = 1";
      LobFileStatement stmt = new LobFileStatement(sql);
      assertEquals("Wrong parameter count", 1, stmt.getParameterCount());
      List<LobFileParameter> parms = stmt.getParameters();
      assertEquals("Wrong parameter type", true, parms.get(0).isBinary());

      f = new File(util.getBaseDir(), "some file.data");
      f.createNewFile();

      sql = "update bla set col = {$clobfile='" + f.getAbsolutePath() + "' encoding=utf8} where x = 1";
      stmt = new LobFileStatement(sql);
      assertEquals("Wrong parameter count", 1, stmt.getParameterCount());
      parms = stmt.getParameters();
      assertEquals("Wrong parameter type", false, parms.get(0).isBinary());
      assertEquals("Wrong encoding", "utf8", parms.get(0).getEncoding());

      File target = new File(parms.get(0).getFilename());
      assertEquals("Wrong filename parsed", "some file.data", f.getName());
    }
    finally
    {
      f.delete();
    }
  }

  @Test
  public void testGetPreparedSql()
    throws Exception
  {
    File f = new File(util.getBaseDir(), "test.data");
    try
    {
      // LobFileStatement checks for the presence of the file!
      f.createNewFile();

      String sql = "update bla set col = {$blobfile=" + f.getAbsolutePath() + "} where x = 1";
      LobFileStatement stmt = new LobFileStatement(sql);
      String newSql = stmt.getPreparedSql();
      assertEquals("Wrong SQL generated", "update bla set col =  ?  where x = 1", newSql);

      sql = "update bla set col = {$clobfile='" +  f.getAbsolutePath() + "'} where x = 1";
      stmt = new LobFileStatement(sql);
      assertEquals("Wrong SQL generated", "update bla set col =  ?  where x = 1", stmt.getPreparedSql());

      sql = "update bla set col = {$clobfile='" +  f.getAbsolutePath() + "' encoding='UTF-8'} where x = 1";
      stmt = new LobFileStatement(sql);
      assertEquals("Wrong SQL generated", "update bla set col =  ?  where x = 1", stmt.getPreparedSql());
    }
    finally
    {
      f.delete();
    }
  }


}
