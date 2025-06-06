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
package workbench.db.vertica;

import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.SequenceDefinition;
import workbench.db.WbConnection;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class VerticaSequenceReaderTest
  extends WbTestCase
{
  public VerticaSequenceReaderTest()
  {
    super("VerticaSequenceReaderTest");
  }

  /**
   * Test of getSequenceSource method, of class VerticaSequenceReader.
   */
  @Test
  public void testGetSequenceSource()
    throws Exception
  {
    TestUtil util = new TestUtil("vertica_fake");
    WbConnection conn = util.getHSQLConnection("vertica_fake", ";get_column_name=false");
    String sql =
      "create schema v_catalog \n" +
      " \n" +
      "create table v_catalog.sequences  \n" +
      "( \n" +
      "   current_database varchar(200), \n" +
      "   SEQUENCE_SCHEMA varchar(200), \n" +
      "   SEQUENCE_NAME   varchar(200), \n" +
      "   IDENTITY_TABLE_NAME varchar(200), \n" +
      "   SESSION_CACHE_COUNT  bigint, \n" +
      "   ALLOW_CYCLE boolean, \n" +
      "   OUTPUT_ORDERED boolean, \n" +
      "   INCREMENT_BY bigint, \n" +
      "   MINIMUM bigint, \n" +
      "   MAXIMUM BIGINT, \n" +
      "   CURRENT_VALUE bigint, \n" +
      "   SEQUENCE_SCHEMA_ID integer, \n" +
      "   SEQUENCE_ID integer, \n" +
      "   OWNER_ID integer, \n" +
      "   IDENTITY_TABLE_ID integer \n" +
      ");\n" +
    "INSERT INTO V_CATALOG.SEQUENCES \n" +
    "( \n" +
    "  CURRENT_DATABASE, \n" +
    "  SEQUENCE_SCHEMA, \n" +
    "  SEQUENCE_NAME, \n" +
    "  IDENTITY_TABLE_NAME, \n" +
    "  SESSION_CACHE_COUNT, \n" +
    "  ALLOW_CYCLE, \n" +
    "  OUTPUT_ORDERED, \n" +
    "  INCREMENT_BY, \n" +
    "  MINIMUM, \n" +
    "  MAXIMUM, \n" +
    "  CURRENT_VALUE, \n" +
    "  SEQUENCE_SCHEMA_ID, \n" +
    "  SEQUENCE_ID, \n" +
    "  OWNER_ID, \n" +
    "  IDENTITY_TABLE_ID \n" +
    ") \n" +
    "VALUES \n" +
    "( \n" +
    "  'vertica', \n" +
    "  'public', \n" +
    "  'foo_sequence', \n" +
    "  'foo', \n" +
    "  250000, \n" +
    "  false, \n" +
    "  false, \n" +
    "  1, \n" +
    "  1, \n" +
    "  " + Long.toString(Long.MAX_VALUE) + ", \n" +
    "  0, \n" +
    "  0, \n" +
    "  0, \n" +
    "  0, \n" +
    "  0 \n" +
    "); \n" +
    "commit;\n";
    TestUtil.executeScript(conn, sql);

    VerticaSequenceReader reader = new VerticaSequenceReader(conn);
    List<SequenceDefinition> sequences = reader.getSequences(null, "public", null);
    assertNotNull(sequences);
    assertEquals(1, sequences.size());
    SequenceDefinition seq = sequences.get(0);
    assertEquals("foo_sequence", seq.getObjectName());
    assertEquals("public", seq.getSchema());
    assertEquals("vertica", seq.getCatalog());
    String create = reader.getSequenceSource(seq, null).toString();
    System.out.println(create);
    String expected =
      "CREATE SEQUENCE foo_sequence\n" +
      "       INCREMENT BY 1\n" +
      "       MINVALUE 1\n" +
      "       NO CYCLE;";
    assertEquals(expected, create.trim());

    SequenceDefinition def = reader.getSequenceDefinition(null, "public", "foo_sequence");
    create = def.getSource().toString();
    assertEquals(expected, create.trim());

    sequences = reader.getSequences(null, null, null);
    assertEquals(1, sequences.size());
  }


}
