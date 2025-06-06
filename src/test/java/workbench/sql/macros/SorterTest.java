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
package workbench.sql.macros;

import java.util.Set;
import java.util.TreeSet;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class SorterTest
{
  @Test
  public void testCompare()
  {
    Set<SortOrderElement> list = new TreeSet<>(new Sorter());
    list.add(new SortOrderElement(4));
    list.add(new SortOrderElement(5));
    list.add(new SortOrderElement(1));
    list.add(new SortOrderElement(3));
    list.add(new SortOrderElement(2));

    int index = 1;
    for (SortOrderElement e : list)
    {
      assertEquals(index, e.getSortOrder());
      index ++;
    }
  }

  static class SortOrderElement
    implements Sortable
  {
    private int sortOrder;

    SortOrderElement(int i)
    {
      sortOrder = i;
    }

    @Override
    public void setSortOrder(int index)
    {
      sortOrder = index;
    }

    @Override
    public int getSortOrder()
    {
      return sortOrder;
    }


  }
}
