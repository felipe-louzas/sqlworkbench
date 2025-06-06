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

import java.util.List;

import workbench.WbTestCase;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class LobFileParameterParserTest
  extends WbTestCase
{

  public LobFileParameterParserTest()
  {
    super("LobFileParameterParserTest");
  }

  @Test
  public void testGetParameters()
    throws Exception
  {
    String sql = "update bla set col = {$blobfile=c:/temp/test.data} where x=1";

    LobFileParameterParser p = new LobFileParameterParser(sql);
    List<LobFileParameter> parms = p.getParameters();
    assertNotNull(parms);
    assertEquals("File not recognized", 1, parms.size());
    assertEquals("Wrong filename", "c:/temp/test.data", parms.get(0).getFilename());

    sql = "update bla set col = {$clobfile=c:/temp/test.data encoding=UTF8} where x=1";
    p = new LobFileParameterParser(sql);
    parms = p.getParameters();
    assertNotNull(parms);
    assertEquals("File not recognized", 1, parms.size());
    assertEquals("Wrong filename", "c:/temp/test.data", parms.get(0).getFilename());
    assertEquals("Wrong encoding", "UTF8", parms.get(0).getEncoding());

    sql = "update bla set col = {$clobfile='c:/my data/test.data' encoding='UTF-8'} where x=1";
    p = new LobFileParameterParser(sql);
    parms = p.getParameters();
    assertNotNull(parms);
    assertEquals("File not recognized", 1, parms.size());
    assertEquals("Wrong filename", "c:/my data/test.data", parms.get(0).getFilename());
    assertEquals("Wrong encoding", "UTF-8", parms.get(0).getEncoding());

    sql = "{$blobfile=c:/temp/test.data}";
    p = new LobFileParameterParser(sql);
    parms = p.getParameters();
    assertNotNull(parms);
    assertEquals("File not recognized", 1, parms.size());
    assertEquals("Wrong filename returned", "c:/temp/test.data", parms.get(0).getFilename());
  }

}
