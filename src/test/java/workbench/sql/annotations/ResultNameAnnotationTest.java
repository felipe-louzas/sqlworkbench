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
package workbench.sql.annotations;


import static org.junit.Assert.*;

import org.junit.Test;

/**
 *
 * @author Thomas Kellerer
 */
public class ResultNameAnnotationTest
{

  @Test
  public void testGetResultName()
  {
    String sql = "/* test select */\nSELECT * FROM dummy;";
    ResultNameAnnotation p = new ResultNameAnnotation();
    String name = p.getResultName(sql);
    assertNull(name);

    sql = "/**@wbresult all rows*/\nSELECT * FROM dummy;";
    name = p.getResultName(sql);
    assertEquals("all rows", name);

    sql = "-- @WbResult all rows\nSELECT * FROM dummy;";
    name = p.getResultName(sql);
    assertEquals("all rows", name);

    sql = "/* @wbresult result for my select\nanother line */\nSELECT * FROM dummy;";
    name = p.getResultName(sql);
    assertEquals("result for my select", name);

    sql = "-- @wbresult test select\nSELECT * FROM dummy;";
    name = p.getResultName(sql);
    assertEquals("test select", name);

    sql = "-- @wbresulttest select\nSELECT * FROM dummy;";
    name = p.getResultName(sql);
    assertNull(name);

    sql = "-- some witty comment\n-- @wbresult foobar\nSELECT * FROM dummy;";
    name = p.getResultName(sql);
    assertEquals("foobar", name);

    sql = "/*@wbresult mystuff\tselected\r\nanother line */\nSELECT * FROM dummy;";
    name = p.getResultName(sql);
    assertEquals("mystuff\tselected", name);
  }
}
