/*
 * H2SequenceReaderTest.java
 *
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
package workbench.db.h2database;

import java.util.Collections;
import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.db.DbObject;
import workbench.db.SequenceDefinition;
import workbench.db.SequenceReader;
import workbench.db.WbConnection;

import workbench.util.StringUtil;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class H2SequenceReaderTest
  extends WbTestCase
{
  private WbConnection db;

  public H2SequenceReaderTest()
  {
    super("H2SequenceReaderTest");
  }

  @Before
  public void setUp()
    throws Exception
  {
    TestUtil util = new TestUtil(getName());
    db = util.getConnection("h2_seq_test");
    TestUtil.executeScript(db,
      "create sequence seq_aaa;\n" +
      "create sequence seq_bbb increment by 5;\n" +
      "commit;");
  }

  @After
  public void tearDown()
    throws Exception
  {
    db.disconnect();
  }

  @Test
  public void testGetSequences()
  {
    H2SequenceReader instance = new H2SequenceReader(db);
    List<SequenceDefinition> result = instance.getSequences(null, "PUBLIC", null);
    Collections.sort(result, (DbObject o1, DbObject o2) -> StringUtil.compareStrings(o1.getObjectName(), o2.getObjectName(), true));

    assertNotNull(result);
    assertEquals(2, result.size());
    assertEquals("SEQ_AAA", result.get(0).getSequenceName());
    assertEquals("SEQ_BBB", result.get(1).getSequenceName());
    assertEquals(Long.valueOf(5), result.get(1).getSequenceProperty(SequenceReader.PROP_INCREMENT));

    CharSequence sql = result.get(0).getSource();
    assertNotNull(sql);
    assertTrue(sql.toString().startsWith("CREATE SEQUENCE"));
  }

}
