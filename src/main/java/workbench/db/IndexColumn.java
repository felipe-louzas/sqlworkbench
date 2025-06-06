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
package workbench.db;

import java.io.Serializable;
import java.util.Comparator;

import workbench.db.objectcache.DbObjectCacheFactory;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class IndexColumn
  implements Serializable
{
  private static final long serialVersionUID = DbObjectCacheFactory.CACHE_VERSION_UID;

  private String column;
  private String direction;
  private int sequence;

  public IndexColumn(String col, int colSequence)
  {
    this.column = col;
    this.sequence = colSequence;
  }

  public IndexColumn(String col, String dir)
  {
    this.column = StringUtil.trim(col);
    this.direction = dir;
  }

  public void setColumn(String newName)
  {
    this.column = StringUtil.trim(newName);
  }

  public String getColumn()
  {
    return this.column;
  }

  public void setDirection(String dir)
  {
    this.direction = dir;
  }

  public String getDirection()
  {
    if (this.direction == null) return null;

    // Map JDBC direction info to SQL standard
    if (direction.equalsIgnoreCase("a")) return "ASC";
    if (direction.equalsIgnoreCase("d")) return "DESC";

    return this.direction;
  }

  public String getExpression()
  {
    return getExpression(false);
  }

  public String getExpression(boolean removeColumnQuotes)
  {
    String colName = removeColumnQuotes ? SqlUtil.removeObjectQuotes(column) : column;
    if (StringUtil.isEmpty(direction))
    {
      return colName;
    }
    else
    {
      return colName + " " + getDirection();
    }
  }

  @Override
  public boolean equals(Object other)
  {
    if (other instanceof IndexColumn)
    {
      IndexColumn otherCol = (IndexColumn)other;
      return SqlUtil.objectNamesAreEqual(column, otherCol.column) && StringUtil.equalStringIgnoreCase(getDirection(), otherCol.getDirection());
    }
    else
    {
      return false;
    }
  }

  @Override
  public int hashCode()
  {
    int hash = 7;
    hash = 73 * hash + (this.column != null ? this.column.hashCode() : 0);
    hash = 73 * hash + (this.direction != null ? this.direction.hashCode() : 0);
    return hash;
  }

  public static Comparator<IndexColumn> getSequenceSorter()
  {
    return (IndexColumn o1, IndexColumn o2) -> o1.sequence - o2.sequence;
  }

  @Override
  public String toString()
  {
    return column;
  }

}
