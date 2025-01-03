/*
 * This file is part of SQL Workbench/J, http://www.sql-workbench.net
 *
 * Copyright 2002-2025 Thomas Kellerer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at.
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.net
 *
 */
package workbench.gui.dbobjects.objecttree;

import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import workbench.db.ColumnIdentifier;
import workbench.db.TableIdentifier;

import workbench.util.CollectionUtil;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class DbObjectSorterTest
{

  @Test
  public void testSortColumns()
  {
    List<ColumnIdentifier> cols = new ArrayList<>();
    ColumnIdentifier c1 = new ColumnIdentifier("bbb", Types.INTEGER);
    c1.setPosition(1);
    ColumnIdentifier c2 = new ColumnIdentifier("zzz", Types.INTEGER);
    c2.setPosition(2);
    ColumnIdentifier c3 = new ColumnIdentifier("\"aa a\"", Types.INTEGER);
    c3.setPosition(3);
    cols.add(c1);
    cols.add(c2);
    cols.add(c3);
    DbObjectSorter.sort(cols, true);
    assertEquals("\"aa a\"", cols.get(0).getColumnName());
    assertEquals("bbb", cols.get(1).getColumnName());
    assertEquals("zzz", cols.get(2).getColumnName());
  }

  @Test
  public void testSortTables()
  {
    TableIdentifier t1 = new TableIdentifier("bbb");
    TableIdentifier t2 = new TableIdentifier("zzz");
    TableIdentifier t3 = new TableIdentifier("aaa");
    TableIdentifier t4 = new TableIdentifier("www");
    List<TableIdentifier> tables = CollectionUtil.arrayList(t1,t2,t3,t4);
    DbObjectSorter.sort(tables, true);
    assertEquals("aaa", tables.get(0).getTableName());
    assertEquals("bbb", tables.get(1).getTableName());
    assertEquals("www", tables.get(2).getTableName());
    assertEquals("zzz", tables.get(3).getTableName());
  }

  @Test
  public void testNaturalSortTables()
  {
    TableIdentifier t1 = new TableIdentifier("a1");
    TableIdentifier t2 = new TableIdentifier("a20");
    TableIdentifier t3 = new TableIdentifier("a12");
    TableIdentifier t4 = new TableIdentifier("a21");
    TableIdentifier t5 = new TableIdentifier("a2");
    List<TableIdentifier> tables = CollectionUtil.arrayList(t1,t2,t3,t4,t5);
    DbObjectSorter.sort(tables, true);
    assertEquals("a1", tables.get(0).getTableName());
    assertEquals("a2", tables.get(1).getTableName());
    assertEquals("a12", tables.get(2).getTableName());
    assertEquals("a20", tables.get(3).getTableName());
    assertEquals("a21", tables.get(4).getTableName());
  }

}
