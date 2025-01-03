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
package workbench.gui.components;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.sql.Types;
import java.time.LocalDateTime;

import workbench.WbTestCase;
import workbench.resource.GuiSettings;

import workbench.db.ColumnIdentifier;
import workbench.db.IndexColumn;
import workbench.db.PkDefinition;
import workbench.db.TableIdentifier;

import workbench.storage.DataStore;
import workbench.storage.ResultInfo;

import workbench.util.CollectionUtil;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class DbUnitCopierTest
  extends WbTestCase
{
  public DbUnitCopierTest()
  {
    super("DbUnitCopierTest");
  }

  @Before
  public void clearClipboard()
  {
    Assume.assumeTrue(!java.awt.GraphicsEnvironment.isHeadless());
    Clipboard clp = Toolkit.getDefaultToolkit().getSystemClipboard();
    StringSelection sel = new StringSelection("");
    clp.setContents(sel, sel);
  }

  @Test
  public void testCopyAsDBUnitXML()
    throws Exception
  {
    Assume.assumeTrue(!java.awt.GraphicsEnvironment.isHeadless());
    try
    {
      GuiSettings.setDisplayNullString("<[NULL]>");
      DataStore ds = createDataStore();
      int row = ds.addRow();
      ds.setValue(row, 0, 1);
      ds.setValue(row, 1, "Marvin");
      ds.setValue(row, 2, null);
      ds.setUpdateTableToBeUsed(new TableIdentifier("PERSON"));
      DbUnitCopier copier = new DbUnitCopier();
      String xml = copier.createDBUnitXMLDataString(ds, null).trim();
      String expected =
        "<dataset>\n" +
        "  <person id=\"1\" firstname=\"Arthur\" lastname=\"Dent\" created_at=\"2024-01-21 14:00:00.000\"/>\n" +
        "  <person id=\"2\" firstname=\"Ford\" lastname=\"Prefect\" created_at=\"2024-01-21 14:00:00.000\"/>\n" +
        "  <person id=\"1\" firstname=\"Marvin\"/>\n" +
        "</dataset>";
//      System.out.println(expected + "\n*****************\n" + xml);
      assertEquals(expected, xml);
    }
    finally
    {
      GuiSettings.setDisplayNullString(null);
    }
  }

  private DataStore createDataStore()
  {
    ColumnIdentifier id = new ColumnIdentifier("id", Types.INTEGER);
    id.setIsPkColumn(true);
    ColumnIdentifier fname = new ColumnIdentifier("firstname", Types.VARCHAR);
    ColumnIdentifier lname = new ColumnIdentifier("lastname", Types.VARCHAR);
    ColumnIdentifier created = new ColumnIdentifier("created_at", Types.TIMESTAMP);
    ResultInfo info = new ResultInfo(new ColumnIdentifier[] {id, fname, lname, created});

    IndexColumn col = new IndexColumn("id", 1);
    PkDefinition pk = new PkDefinition("pk_person", CollectionUtil.arrayList(col));
    TableIdentifier tbl = new TableIdentifier("person");
    tbl.setPrimaryKey(pk);

    LocalDateTime ldt = LocalDateTime.of(2024,1,21,14,0,0);
    info.setUpdateTable(tbl);
    DataStore ds = new DataStore(info);
    int row = ds.addRow();
    ds.setValue(row, 0, 1);
    ds.setValue(row, 1, "Arthur");
    ds.setValue(row, 2, "Dent");
    ds.setValue(row, 3, ldt);

    row = ds.addRow();
    ds.setValue(row, 0, 2);
    ds.setValue(row, 1, "Ford");
    ds.setValue(row, 2, "Prefect");
    ds.setValue(row, 3, ldt);

    ds.setUpdateTableToBeUsed(tbl);
    return ds;
  }


}
