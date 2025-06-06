/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
 * that restricts the use for certain governments.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.sql-workbench.eu/manual/license.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.eu
 */
package workbench.db.exporter;

import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDateTime;

import workbench.db.ColumnIdentifier;
import workbench.db.TableIdentifier;

import workbench.storage.ResultInfo;
import workbench.storage.RowData;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class JsonRowDataConverterTest
{

  @Test
  public void testConvertDataNoResultName()
  {
    ColumnIdentifier id = new ColumnIdentifier("id", Types.INTEGER, true);
    id.setPosition(1);

    ColumnIdentifier fname = new ColumnIdentifier("firstname", Types.VARCHAR);
    fname.setPosition(2);

    ColumnIdentifier lname = new ColumnIdentifier("lastname", Types.VARCHAR);
    lname.setPosition(3);

    ResultInfo info = new ResultInfo(new ColumnIdentifier[] {id, fname, lname});
    JsonRowDataConverter converter = new JsonRowDataConverter();
    converter.setResultInfo(info);
    converter.setUseResultName(false);

    RowData data = new RowData(info);
    data.setValue(0, 1);
    data.setValue(1, "Arthur");
    data.setValue(2, "Dent");

    String result = converter.getStart().toString();
    result += converter.convertRowData(data, 0).toString();

    data.setValue(0, 2);
    data.setValue(1, "Ford");
    data.setValue(2, "Prefect");
    result += converter.convertRowData(data, 1).toString();
    result += converter.getEnd(2).toString();
    String expected =
      "[\n" +
      "  {\"id\": \"1\", \"firstname\": \"Arthur\", \"lastname\": \"Dent\"},\n" +
      "  {\"id\": \"2\", \"firstname\": \"Ford\", \"lastname\": \"Prefect\"}\n" +
      "]";
    assertEquals(expected, result);
  }

  @Test
  public void testConvertRowData()
    throws Exception
  {
    ColumnIdentifier id = new ColumnIdentifier("id", Types.INTEGER, true);
    id.setPosition(1);

    ColumnIdentifier fname = new ColumnIdentifier("firstname", Types.VARCHAR);
    fname.setPosition(2);

    ColumnIdentifier lname = new ColumnIdentifier("lastname", Types.VARCHAR);
    lname.setPosition(3);

    ColumnIdentifier lastLogin = new ColumnIdentifier("last_login", Types.TIMESTAMP);
    lname.setPosition(4);

    ColumnIdentifier salaray = new ColumnIdentifier("salary", Types.DECIMAL);
    salaray.setPosition(5);

    ResultInfo info = new ResultInfo(new ColumnIdentifier[] { id, fname, lname, lastLogin, salaray });
    info.setUpdateTable(new TableIdentifier("PERSON"));
    JsonRowDataConverter converter = new JsonRowDataConverter();
    converter.setResultInfo(info);
    converter.setUseResultName(true);

    LocalDateTime login = LocalDateTime.of(2013,1,12,14,56,12,000);

    RowData data = new RowData(info);
    data.setValue(0, 1);
    data.setValue(1, "Arthur");
    data.setValue(2, "Dent");
    data.setValue(3, java.sql.Timestamp.valueOf(login));
    data.setValue(4, new BigDecimal("42.24"));

    String result = converter.getStart().toString();
    result += converter.convertRowData(data, 0).toString();

    data.setValue(0, 2);
    data.setValue(1, "Ford");
    data.setValue(2, "\"Prefect\"");
    data.setValue(3, null);
    data.setValue(4, new BigDecimal("24.42"));

    result += converter.convertRowData(data, 1).toString();
    result += converter.getEnd(2).toString();

    String expected =
      "{\n" +
      "  \"person\":\n" +
      "  [\n" +
      "    {\"id\": \"1\", \"firstname\": \"Arthur\", \"lastname\": \"Dent\", \"last_login\": \"2013-01-12 14:56:12.000\", \"salary\": \"42.24\"},\n" +
      "    {\"id\": \"2\", \"firstname\": \"Ford\", \"lastname\": \"\\\"Prefect\\\"\", \"last_login\": null, \"salary\": \"24.42\"}\n" +
      "  ]\n" +
      "}";
    assertEquals(expected, result);
  }

}
