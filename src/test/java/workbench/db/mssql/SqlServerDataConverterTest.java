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
package workbench.db.mssql;

import java.sql.Types;

import org.junit.Test;

import static org.junit.Assert.*;
/**
 *
 * @author Thomas Kellerer
 */
public class SqlServerDataConverterTest
{

  @Test
  public void testConvertsType()
  {
    SqlServerDataConverter converter = SqlServerDataConverter.getInstance();
    byte[] data = new byte[] { 0, 0, 0, 0, 0, 0, 0, 0};
    Object newValue = converter.convertValue(Types.BINARY, "timestamp", data);
    assertEquals("0x0000000000000000", newValue.toString());

    data = new byte[] { 0, 0, 0, 0, 0, (byte)1, (byte)-36, (byte)-111 };
    newValue = converter.convertValue(Types.BINARY, "timestamp", data);
    assertEquals("0x000000000001dc91", newValue.toString());

    data = new byte[] { 1, 1, 1, 1, 1, 1, 1, 1 };
    newValue = converter.convertValue(Types.BINARY, "timestamp", data);
    assertEquals("0x0101010101010101", newValue.toString());
  }

  @Test
  public void testConvertValue()
  {
    SqlServerDataConverter converter = SqlServerDataConverter.getInstance();
    assertTrue(converter.convertsType(Types.BINARY, "timestamp"));
    assertFalse(converter.convertsType(Types.BLOB, "timestamp"));
    assertFalse(converter.convertsType(Types.BINARY, "image"));
  }
}
