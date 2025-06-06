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

import java.sql.Types;

import workbench.WbTestCase;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class OracleDataTypeResolverTest
  extends WbTestCase
{
  public OracleDataTypeResolverTest()
  {
    super("OracleDataTypeResolverTest");
  }

  @Test
  public void testGetSqlTypeDisplay()
  {
    // Test with BYTE as default semantics
    OracleDataTypeResolver resolver = new OracleDataTypeResolver(OracleDataTypeResolver.CharSemantics.Byte, false);

    // Test non-Varchar types
    assertEquals("CLOB", resolver.getSqlTypeDisplay("CLOB", Types.CLOB, -1, -1));
    assertEquals("NVARCHAR(300)", resolver.getSqlTypeDisplay("NVARCHAR", Types.VARCHAR, 300, -1, OracleDataTypeResolver.CharSemantics.Byte));
    assertEquals("CHAR(5)", resolver.getSqlTypeDisplay("CHAR", Types.CHAR, 5, -1, OracleDataTypeResolver.CharSemantics.Byte));
    assertEquals("NUMBER(10,2)", resolver.getSqlTypeDisplay("NUMBER", Types.NUMERIC, 10, 2));
    assertEquals("NUMBER(10,-2)", resolver.getSqlTypeDisplay("NUMBER", Types.NUMERIC, 10, -2));
    assertEquals("NUMBER(*,-2)", resolver.getSqlTypeDisplay("NUMBER", Types.NUMERIC, Integer.MAX_VALUE, -2));
    assertEquals("NUMBER", resolver.getSqlTypeDisplay("NUMBER", Types.NUMERIC, 0, 0));

    String display = resolver.getSqlTypeDisplay("VARCHAR", Types.VARCHAR, 200, 0, OracleDataTypeResolver.CharSemantics.Byte);
    assertEquals("VARCHAR(200)", display);

    display = resolver.getSqlTypeDisplay("VARCHAR", Types.VARCHAR, 200, 0, OracleDataTypeResolver.CharSemantics.Char);
    assertEquals("VARCHAR(200 Char)", display);

    resolver = new OracleDataTypeResolver(OracleDataTypeResolver.CharSemantics.Char, false);

    display = resolver.getSqlTypeDisplay("VARCHAR", Types.VARCHAR, 200, 0, OracleDataTypeResolver.CharSemantics.Byte);
    assertEquals("VARCHAR(200 Byte)", display);

    display = resolver.getSqlTypeDisplay("VARCHAR", Types.VARCHAR, 200, 0, OracleDataTypeResolver.CharSemantics.Char);
    assertEquals("VARCHAR(200)", display);

    resolver = new OracleDataTypeResolver(OracleDataTypeResolver.CharSemantics.Char, true);

    display = resolver.getSqlTypeDisplay("VARCHAR", Types.VARCHAR, 200, 0, OracleDataTypeResolver.CharSemantics.Byte);
    assertEquals("VARCHAR(200 Byte)", display);

    display = resolver.getSqlTypeDisplay("VARCHAR", Types.VARCHAR, 200, 0,OracleDataTypeResolver.CharSemantics.Char);
    assertEquals("VARCHAR(200 Char)", display);
  }


}
