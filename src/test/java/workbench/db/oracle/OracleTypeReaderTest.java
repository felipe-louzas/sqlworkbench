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
package workbench.db.oracle;

import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.OracleTest;
import workbench.db.WbConnection;

import workbench.sql.DelimiterDefinition;
import workbench.sql.parser.ParserType;
import workbench.sql.parser.ScriptParser;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
@Category(OracleTest.class)
public class OracleTypeReaderTest
  extends WbTestCase
{

  public OracleTypeReaderTest()
  {
    super("OracleTypeReaderTest");
  }

  @BeforeClass
  public static void setUp()
    throws Exception
  {
    OracleTestUtil.initTestCase();
    WbConnection con = OracleTestUtil.getOracleConnection();
    assertNotNull("Oracle not available", con);

    String sql =
      "CREATE TYPE address_type AS OBJECT (street varchar(100), city varchar(50), zipcode varchar(10));\n" +
      "/\n" +
      "\n "+
      "CREATE TYPE TYP1 AS OBJECT  \n" +
      "(  \n" +
      "   my_data NUMBER(16,2),  \n" +
      "   MEMBER FUNCTION get_value(add_to NUMBER) RETURN NUMBER  \n" +
      ");\n" +
      "/\n" +
      "CREATE TYPE BODY TYP1 IS    \n" +
      "  MEMBER FUNCTION get_value(add_to NUMBER) RETURN NUMBER  IS  \n" +
      "  BEGIN  \n" +
      "     RETURN (my_data + add_to); \n" +
      "  END;  \n" +
      "END;  \n" +
      "/";
    TestUtil.executeScript(con, sql, DelimiterDefinition.DEFAULT_ORA_DELIMITER);
  }

  @AfterClass
  public static void tearDown()
    throws Exception
  {
    OracleTestUtil.cleanUpTestCase();
  }

  @Test
  public void testGetTypes()
    throws Exception
  {
    WbConnection con = OracleTestUtil.getOracleConnection();
    assertNotNull("Oracle not available", con);

    OracleTypeReader reader = new OracleTypeReader();

    List<OracleObjectType> types = reader.getTypes(con, "WBJUNIT", null);
    assertNotNull(types);
    assertEquals(2, types.size());

    // List is sorted by name, so the first must be the address_type
    OracleObjectType address = types.get(0);
    assertEquals("ADDRESS_TYPE", address.getObjectName());
    assertEquals(3, address.getNumberOfAttributes());
    assertEquals(0, address.getNumberOfMethods());

    OracleObjectType typ1 = types.get(1);
    assertEquals("TYP1", typ1.getObjectName());
    assertEquals(1, typ1.getNumberOfAttributes());
    assertEquals(1, typ1.getNumberOfMethods());
    String source = typ1.getSource(con).toString();
    ScriptParser p = new ScriptParser(source, ParserType.Oracle);
    p.setAlternateDelimiter(DelimiterDefinition.DEFAULT_ORA_DELIMITER);
//    System.out.println(source);
    assertEquals(2, p.getSize());

    types = reader.getTypes(con, "WBJUNIT", "ADDRESS_TYPE");
    assertNotNull(types);
    assertEquals(1, types.size());
  }

}
