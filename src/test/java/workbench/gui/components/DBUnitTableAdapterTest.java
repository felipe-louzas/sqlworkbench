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

package workbench.gui.components;

import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;

import workbench.WbTestCase;

import workbench.db.ColumnIdentifier;
import workbench.db.TableIdentifier;

import workbench.storage.DataStore;
import workbench.storage.ResultInfo;

import org.dbunit.dataset.Column;
import org.dbunit.dataset.ITableMetaData;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class DBUnitTableAdapterTest
  extends WbTestCase
{

  public DBUnitTableAdapterTest()
  {
    super("DBUnitTableAdapterTest");
  }

  @Test
  public void testGetValue()
    throws Exception
  {
    ColumnIdentifier id = new ColumnIdentifier("ID", Types.INTEGER);
    id.setIsPkColumn(true);
    ColumnIdentifier fname = new ColumnIdentifier("FIRSTNAME", Types.VARCHAR);
    ColumnIdentifier lname = new ColumnIdentifier("LASTNAME", Types.VARCHAR);
    ColumnIdentifier sdt = new ColumnIdentifier("SOME_DATE", Types.DATE);
    ColumnIdentifier created = new ColumnIdentifier("CREATED", Types.TIMESTAMP);
    ColumnIdentifier[] c = {id, fname, lname, sdt, created};

    DataStore ds = new DataStore(new ResultInfo(c));
    ds.setUpdateTableToBeUsed(new TableIdentifier("PERSON"));
    int row = ds.addRow();
    ds.setValue(row, 0, Integer.valueOf(42));
    ds.setValue(row, 1, "Arthur");
    ds.setValue(row, 2, "Dent");
    ds.setValue(row, 3, LocalDate.of(2003,11,3));
    ds.setValue(row, 4, LocalDateTime.of(2000,01,2,3,4,5));

    DBUnitTableAdapter adapter = new DBUnitTableAdapter(ds);
    assertEquals(1, adapter.getRowCount());
    ITableMetaData meta = adapter.getTableMetaData();
    assertNotNull(meta);
    assertEquals("PERSON", meta.getTableName());
    Column[] cols = meta.getColumns();
    assertNotNull(cols);
    assertEquals(5, cols.length);

    assertEquals("FIRSTNAME", cols[1].getColumnName());
    assertEquals(0, meta.getColumnIndex("ID"));
    assertEquals(1, meta.getColumnIndex("FIRSTNAME"));
    Column[] pk = meta.getPrimaryKeys();
    assertNotNull(pk);
    assertEquals(1, pk.length);
    assertEquals("ID", pk[0].getColumnName());
    Object name = adapter.getValue(0, "FIRSTNAME");
    assertEquals("Arthur", name);

    Object dat = adapter.getValue(0, "SOME_DATE");
    assertEquals("2003-11-03", dat);
    Object cr = adapter.getValue(0, "CREATED");
    assertEquals("2000-01-02 03:04:05.000", cr);

  }

  @Test
  public void testSelectedRows()
    throws Exception
  {
    ColumnIdentifier id = new ColumnIdentifier("ID", Types.INTEGER);
    id.setIsPkColumn(true);
    ColumnIdentifier fname = new ColumnIdentifier("FIRSTNAME", Types.VARCHAR);
    ColumnIdentifier lname = new ColumnIdentifier("LASTNAME", Types.VARCHAR);
    ColumnIdentifier[] c = {id, fname, lname};

    DataStore ds = new DataStore(new ResultInfo(c));
    ds.setUpdateTableToBeUsed(new TableIdentifier("PERSON"));
    int row = ds.addRow();
    ds.setValue(row, 0, Integer.valueOf(42));
    ds.setValue(row, 1, "Arthur");
    ds.setValue(row, 2, "Dent");

    row = ds.addRow();
    ds.setValue(row, 0, Integer.valueOf(1));
    ds.setValue(row, 1, "Zaphod");
    ds.setValue(row, 2, "Beeblebrox");

    row = ds.addRow();
    ds.setValue(row, 0, Integer.valueOf(2));
    ds.setValue(row, 1, "Tricia");
    ds.setValue(row, 2, "McMillan");

    DBUnitTableAdapter adapter = new DBUnitTableAdapter(ds);
    int[] selected = new int[]{1};
    adapter.setSelectedRows(selected);

    assertEquals(1, adapter.getRowCount());
    Object name = adapter.getValue(0, "FIRSTNAME");
    assertEquals("Zaphod", name);

    selected = new int[]{0,2};
    adapter.setSelectedRows(selected);

    assertEquals(2, adapter.getRowCount());
    name = adapter.getValue(0, "FIRSTNAME");
    assertEquals("Arthur", name);
    name = adapter.getValue(1, "FIRSTNAME");
    assertEquals("Tricia", name);
  }

}
