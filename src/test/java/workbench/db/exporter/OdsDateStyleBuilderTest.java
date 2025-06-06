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
package workbench.db.exporter;

import org.junit.*;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class OdsDateStyleBuilderTest
{

  public OdsDateStyleBuilderTest()
  {
  }

  @Test
  public void testGetXML()
  {
    OdsDateStyleBuilder builder = new OdsDateStyleBuilder("yyyy-MM-dd HH:mm:ss");
    String xml = builder.getXML("");
    String expected =
      "<number:year number:style=\"long\"/>\n" +
      "<number:text>-</number:text>\n" +
      "<number:month number:style=\"long\"/>\n" +
      "<number:text>-</number:text>\n" +
      "<number:day number:style=\"long\"/>\n" +
      "<number:text> </number:text>\n" +
      "<number:hours number:style=\"long\"/>\n" +
      "<number:text>:</number:text>\n" +
      "<number:minutes/>\n" +
      "<number:text>:</number:text>\n" +
      "<number:seconds number:style=\"long\"/>";
    assertEquals(expected, xml.trim());

    builder = new OdsDateStyleBuilder("dd.MM.yy HH:mm");
    xml = builder.getXML("");
    expected =
      "<number:day number:style=\"long\"/>\n" +
      "<number:text>.</number:text>\n" +
      "<number:month number:style=\"long\"/>\n" +
      "<number:text>.</number:text>\n" +
      "<number:year/>\n" +
      "<number:text> </number:text>\n" +
      "<number:hours number:style=\"long\"/>\n" +
      "<number:text>:</number:text>\n" +
      "<number:minutes/>";
    assertEquals(expected, xml.trim());

  }

}
