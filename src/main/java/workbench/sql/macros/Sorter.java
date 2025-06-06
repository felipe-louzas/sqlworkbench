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

import java.util.Comparator;

/**
 * A comparator to sort <tt>Sortable</tt> objects.
 *
 * @author Thomas Kellerer
 * @see Sortable
 */
public class Sorter
  implements Comparator<Sortable>
{

  @Override
  public int compare(Sortable o1, Sortable o2)
  {
    if (o1 == null && o2 == null) return 0;
    if (o1 == null) return -1;
    if (o2 == null) return 1;
    return o1.getSortOrder() - o2.getSortOrder();
  }

}
