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
package workbench.gui.dbobjects.objecttree;

import java.util.Comparator;
import java.util.List;

import workbench.db.DbObject;

import workbench.util.NaturalOrderComparator;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class DbObjectSorter
  implements Comparator<DbObject>
{
  private boolean includeType;
  private boolean useNaturalSort;
  private boolean ignoreCase = true;
  private final NaturalOrderComparator comparator = new NaturalOrderComparator(true);

  public DbObjectSorter(boolean naturalSort)
  {
    useNaturalSort = naturalSort;
  }

  /**
   * Case-insensitive sorting is only applied when natural sort is disabled.
   */
  public void setIgnoreCase(boolean flag)
  {
    this.ignoreCase = flag;
  }

  public void setIncludeType(boolean flag)
  {
    this.includeType = flag;
  }

  public void setUseNaturalSort(boolean flag)
  {
    this.useNaturalSort = flag;
  }

  @Override
  public int compare(DbObject o1, DbObject o2)
  {
    if (o1 == null && o2 != null) return 1;
    if (o1 != null && o2 == null) return -1;
    if (o1 == null && o2 == null) return 0;

    if (includeType)
    {
      String t1 = o1.getObjectType();
      String t2 = o2.getObjectType();
      int compare = StringUtil.compareStrings(t1, t2, ignoreCase);
      if (compare != 0)
      {
        return compare;
      }
    }
    // types are the same, so sort by name (and only by name
    String name1 = SqlUtil.removeObjectQuotes(o1.getObjectName());
    String name2 = SqlUtil.removeObjectQuotes(o2.getObjectName());
    if (useNaturalSort)
    {
      return comparator.compare(name1, name2);
    }
    return StringUtil.compareStrings(name1, name2, ignoreCase);
  }

  public static void sort(List<? extends DbObject> objects, boolean useNaturalSort)
  {
    sort(objects, useNaturalSort, false, true);
  }

  public static void sort(List<? extends DbObject> objects, boolean useNaturalSort, boolean ignoreCase)
  {
    sort(objects, useNaturalSort, false, ignoreCase);
  }

  /**
   * Sorts the given list of DbObjects.
   *
   * @param objects          the objects to sort
   * @param useNaturalSort   if true, use natural sort when comparing object names
   * @param includeType      include the object's types when comparing only needed for list with different types
   * @param ignoreCase       if natural sort is disable, controls case sensitive comparison
   */
  public static void sort(List<? extends DbObject> objects, boolean useNaturalSort, boolean includeType, boolean ignoreCase)
  {
    if (objects == null || objects.isEmpty()) return;
    DbObjectSorter sorter = new DbObjectSorter(useNaturalSort);
    sorter.setIgnoreCase(ignoreCase);
    sorter.setIncludeType(includeType);
    objects.sort(sorter);
  }
}
