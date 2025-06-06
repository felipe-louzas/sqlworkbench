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

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class WbStringTokenizerTest
{

  @Test
  public void testParameterTokens()
  {
    WbStringTokenizer tok = new WbStringTokenizer('-', "\"'", false);
    tok.setDelimiterNeedsWhitspace(true);
    tok.setSourceString("-other='stuff' -empty= -list='a','b' -one=' ' -nested='\"test\"'");
    List<String> tokens = tok.getAllTokens();
    assertEquals(5, tokens.size());
    assertEquals("other=stuff", tokens.get(0).trim());
    assertEquals("empty=", tokens.get(1).trim());
    assertEquals("list=a,b", tokens.get(2).trim());
    assertEquals("one=", tokens.get(3).trim());
    assertEquals("nested=\"test\"", tokens.get(4).trim());

    tok.setSourceString("-foo=#x=1,z=2 -foo=#a=5,b=6");
    tokens = tok.getAllTokens();
    assertEquals(2, tokens.size());
    assertEquals("foo=#x=1,z=2", tokens.get(0).trim());
    assertEquals("foo=#a=5,b=6", tokens.get(1).trim());
  }

  @Test
  public void testEmptyValue()
  {
    String data = "2\t\tPrefect";
    WbStringTokenizer tok = new WbStringTokenizer("\t", "\"", false);
    tok.setDelimiterNeedsWhitspace(false);
    tok.setSingleWordDelimiter(true);
    tok.setSourceString(data);
    List<String> tokens = tok.getAllTokens();
    assertEquals(3, tokens.size());

    tok.setSourceString("2\tPrefect\t");
    tokens = tok.getAllTokens();
    assertEquals(3, tokens.size());
  }

  @Test
  public void testTokenizer()
  {
    String data = "value1\t\"quoted value\"\t  \tlast";
    WbStringTokenizer tok = new WbStringTokenizer(data, "\t", true, "\"", false);

    int count = 0;
    while (tok.hasMoreTokens())
    {
      String value = tok.nextToken();
      switch (count)
      {
        case 0:
          assertEquals("Wrong first value", "value1", value);
          break;
        case 1:
          assertEquals("Wrong first value", "quoted value", value);
          break;
        case 2:
          assertEquals("Wrong first value", "  ", value);
          break;
        case 3:
          assertEquals("Wrong first value", "last", value);
          break;
        default:
          fail("Wrong number of parameters retrieved");
      }
      count ++;
    }
  }

}
