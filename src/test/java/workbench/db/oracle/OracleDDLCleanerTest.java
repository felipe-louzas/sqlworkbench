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

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class OracleDDLCleanerTest
{

  public OracleDDLCleanerTest()
  {
  }

  @Test
  public void testCleanupQuotedIdentifiers()
  {
    String input = "SELECT \"PERSON\".\"FIRSTNAME\" FROM \"PERSON\"";
    String expected = "SELECT PERSON.FIRSTNAME FROM PERSON";
    String clean = OracleDDLCleaner.cleanupQuotedIdentifiers(input);
    assertEquals(expected, clean);

    input = "SELECT \"Person\".\"FIRSTNAME\" FROM \"Person\"";
    expected = "SELECT \"Person\".FIRSTNAME FROM \"Person\"";
    clean = OracleDDLCleaner.cleanupQuotedIdentifiers(input);
    assertEquals(expected, clean);

    input = "SELECT '\"' as quote FROM \"Person\"";
    clean = OracleDDLCleaner.cleanupQuotedIdentifiers(input);
    assertEquals(input, clean);

    input = "SELECT ' \"TEST\" ' as constant FROM \"Person\"";
    clean = OracleDDLCleaner.cleanupQuotedIdentifiers(input);
    assertEquals(input, clean);
  }

}
