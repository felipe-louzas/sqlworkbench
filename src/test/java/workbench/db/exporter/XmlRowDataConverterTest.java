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

import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.storage.ResultInfo;
import workbench.storage.RowData;

import workbench.util.ValueConverter;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class XmlRowDataConverterTest
  extends WbTestCase
{

  public XmlRowDataConverterTest()
  {
    super("XmlRowDataConverterTest");
  }

  @Test
  public void testConvert()
    throws Exception
  {
    String[] cols = new String[] { "char_col", "int_col", "date_col", "ts_col"};
    int[] types = new int[] { Types.VARCHAR, Types.INTEGER, Types.DATE, Types.TIMESTAMP };
    int[] sizes = new int[] { 10, 10, 10, 10 };

    ResultInfo info = new ResultInfo(cols, types, sizes);
    XmlRowDataConverter converter = new XmlRowDataConverter();
    converter.setDefaultTimestampFormat("yyyy-MM-dd HH:mm:ss");
    converter.setDefaultDateFormat("yyyy-MM-dd");
    converter.setWriteHeader(true);
    converter.setResultInfo(info);

    String generatingSql = "SELECT * FROM some_table WHERE x < 1000";
    converter.setGeneratingSql(generatingSql);

    StringBuilder header = converter.getStart();
    assertNotNull(header);

    RowData data = new RowData(info);
    data.setValue(0, "char_column_data");
    data.setValue(1, 42);
    ValueConverter valueConverter = new ValueConverter("yyyy-MM-dd", "yyyy-MM-dd HH:mm:ss");
    LocalDate d1 = (LocalDate)valueConverter.convertValue("2008-07-23", Types.DATE);
    data.setValue(2, d1);
    LocalDateTime ts1 = (LocalDateTime)valueConverter.convertValue("2008-07-23 13:42:01", Types.TIMESTAMP);
    data.setValue(3, ts1);

    StringBuilder converted = converter.convertRowData(data, 0);
    assertNotNull(converted);
    String xml = converted.toString();

    String colValue = TestUtil.getXPathValue(xml, "/row-data[@row-num='1']/column-data[@index='0']/text()");
    assertEquals(data.getValue(0), colValue);

    colValue = TestUtil.getXPathValue(xml, "/row-data[@row-num='1']/column-data[@index='1']/text()");
    assertEquals(data.getValue(1).toString(), colValue);

    colValue = TestUtil.getXPathValue(xml, "/row-data[@row-num='1']/column-data[@index='2']/text()");
    assertEquals("2008-07-23", colValue);

    colValue = TestUtil.getXPathValue(xml, "/row-data[@row-num='1']/column-data[@index='3']/text()");
    assertEquals("2008-07-23 13:42:01", colValue);

    String head = converter.getStart().toString();
    head += converter.getEnd(1).toString();

    String sql = TestUtil.getXPathValue(head, "/wb-export/meta-data/generating-sql");
    assertEquals(generatingSql, sql.trim());
  }

}

