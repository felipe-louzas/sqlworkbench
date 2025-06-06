/*
 * RowDataList.java
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
package workbench.storage;

import java.util.Arrays;
import java.util.Comparator;

/**
 * A class to store a dynamic array of {@link RowData} objects.
 *
 * @author Thomas Kellerer
 */

public class RowDataList
{
  private static final int DEFAULT_SIZE = 150;

  // growth factor when increasing the array
  private final float grow = 1.2f;
  private int size;
  private RowData[] data;

  public RowDataList()
  {
    this(DEFAULT_SIZE);
  }

  public RowDataList(int len)
  {
    this.data = new RowData[len];
    this.size = 0;
  }

  private void grow(int minStorage)
  {
    if (this.data == null)
    {
      data = new RowData[(int) (minStorage * grow)];
    }
    else
    {
      int newStorage = (int) (this.data.length * grow) + 1;
      if (newStorage < minStorage)
      {
        newStorage = minStorage;
      }

      RowData[] newBuf = new RowData[newStorage];
      System.arraycopy(this.data, 0, newBuf, 0, this.size);
      this.data = newBuf;
    }
  }

  public void removeColumn(int index)
  {
    if (data == null) return;

    for (RowData row : data)
    {
      if (row != null)
      {
        row.removeColumn(index);
      }
    }
  }
  
  /**
   * Increase the storage to be able to contain one more column.
   *
   * @see RowData#addColum()
   */
  public void addColumn()
  {
    if (data == null) return;

    for (RowData row : data)
    {
      if (row != null)
      {
        row.addColum();
      }
    }
  }

  public void addColumn(int index)
  {
    if (data == null) return;

    for (RowData row : data)
    {
      if (row != null)
      {
        row.addColum(index);
      }
    }
  }

  public void ensureCapacity(int newSize)
  {
    this.grow(newSize);
  }

  /**
   * Free all objects stored in the internal array.
   *
   * This will also call reset on all RowData objects, so if rows are shared
   * between to RowDataList instances (moved back and forth, e.g.
   * when filtering) this will also remove the data from the original
   * source.
   *
   * @see #clear()
   * @see RowData#reset()
   */
  public void reset()
  {
    if (data == null) return;
    for (RowData row : data)
    {
      if (row != null) row.reset();
    }
    clear();
  }

  /**
   * set the number of rows to zero.
   * The RowData instances currently stored in this list
   * are not freed by calling RowData.reset()
   * @see #reset()
   */
  public void clear()
  {
    this.size = 0;
    this.data = null;
  }

  /**
   * Return the number of rows in this list
   */
  public int size()
  {
    return this.size;
  }

  /**
   * Return the row at the given index
   */
  public RowData get(int index)
  {
    if (data == null) throw new ArrayIndexOutOfBoundsException(index);
    return this.data[index];
  }

  /**
   * Remove the row at the specified index.
   */
  public void remove(int index)
  {
    if (data == null) throw new ArrayIndexOutOfBoundsException(index);

    int count = size - index - 1;
    if (count > 0)
    {
      System.arraycopy(data, index+1, data, index, count);
    }
    this.size --;
    this.data[size] = null;
  }

  /**
   * Add a row to this list.
   *
   * @return the new size of this list
   */
  public int add(RowData row)
  {
    if (data == null)
    {
      grow(DEFAULT_SIZE);
    }

    int newlen = this.size + 1;
    if (newlen > this.data.length) grow(newlen);
    this.data[newlen - 1] = row;
    this.size = newlen;
    return this.size;
  }

  /**
   * Add a row at a specific index in this list.
   */
  public int add(int index, RowData row)
  {
    int newlen = this.size + 1;

    if (data == null)
    {
      grow(DEFAULT_SIZE);
    }
    else if (newlen > this.data.length)
    {
      // we are not using ensureCapacity here to optimize the calls to System.arraycopy
      // copying the data before the index and after the index should be more efficient
      // than first growing everything (which copies the entire list) and then copying the part after the index again.
      RowData[] newBuf = new RowData[(int)(newlen * grow)];
      System.arraycopy(this.data, 0, newBuf, 0, index);
      System.arraycopy(this.data, index, newBuf, index + 1, (size - index));
      this.data = newBuf;
    }
    else
    {
      System.arraycopy(this.data, index, this.data, index + 1, (size - index));
    }
    this.data[index] = row;
    this.size = newlen;
    return index;
  }

  public void sort(Comparator<RowData> comp)
  {
    if (size == 0 || data == null) return;
    Arrays.sort(this.data, 0, this.size, comp);
  }

}
