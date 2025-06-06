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
package workbench.storage;

import java.sql.Array;
import java.sql.Struct;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.importer.ValueDisplay;

import workbench.util.CollectionUtil;
import workbench.util.NumberUtil;
import workbench.util.StringUtil;

/**
 * A class to hold the data for a single row retrieved from the database.
 * <br/>
 * It will also save the originally retrieved information in case the data is changed. <br/>
 * <br/>
 * A row can be in one of three different statuses:<br/>
 * <ul>
 * <li><tt>NEW</tt> - the row has not been retrieved from the database (i.e. was created on the client)</li>
 * <li><tt>MODIFIED</tt> - the row has been retrieved but has been changed since then</li>
 * <li><tt>NOT_MODIFIED</tt> - The row has not been changed since it has been retrieved</li>
 * </ul>
 * @author Thomas Kellerer
 */
public class RowData
{
  /**
   * The row has not been modified since retrieval.
   */
  public static final int NOT_MODIFIED = 0;

  /**
   * The data has been modified since retrieval from the database
   */
  public static final int MODIFIED = 1;

  /**
   * The row has been inserted at the client (was not retrieved from the database)
   */
  public static final int NEW = 2;

  private static final Object NO_CHANGE_MARKER = new Object();

  private int status = NOT_MODIFIED;

  /**
   * Mark this row have beeing sent to the database.
   *
   * This flag will be used by the {@link DataStore}
   * to store the information for which rows the SQL statements
   * have been sent to the database during the update process
   * @see #setDmlSent(boolean)
   */
  private boolean dmlSent;

  private Object[] colData;
  private Object[] originalData;
  private List<String> dependencyDeletes;

  private Object userObject;
  private boolean normalizeNewlines;

  public RowData(Object[] data)
  {
    this.colData = data;
  }

  public RowData(ResultInfo info)
  {
    this(info.getColumnCount());
  }

  public RowData(int colCount)
  {
    this.colData = new Object[colCount];
    this.setNew();
  }

  public void setNormalizeNewLines(boolean flag)
  {
    this.normalizeNewlines = flag;
  }

  public Object[] getData()
  {
    return this.colData;
  }

  /**
   * Create a deep copy of this object.
   * <br/>
   * The status of the new row will be <tt>NEW</tt>, which means that the "original" data will
   * be lost after creating the copy.
   */
  public RowData createCopy()
  {
    RowData result = new RowData(this.colData.length);
    System.arraycopy(colData, 0, result.colData, 0, colData.length);
    result.userObject = this.userObject;
    return result;
  }

  public int getColumnCount()
  {
    if (this.colData == null) return 0;
    return this.colData.length;
  }

  private void createOriginalData()
  {
    this.originalData = new Object[this.colData.length];
    Arrays.fill(originalData, NO_CHANGE_MARKER);
  }

  public <E> E getUserObject(int row, Class<E> clz)
  {
    try
    {
      return (E)userObject;
    }
    catch (Throwable th)
    {
      return null;
    }
  }

  public Object getUserObject()
  {
    return userObject;
  }

  public void setUserObject(Object value)
  {
    userObject = value;
  }

  /**
   * Sets the new data for the given column.
   * <br>
   * After calling setValue(), isModified() will return true.
   * <br/>
   *
   * @throws IndexOutOfBoundsException
   * @see #isModified()
   */
  public void setValue(int aColIndex, Object newValue)
    throws IndexOutOfBoundsException
  {
    if (!this.isNew())
    {
      Object oldValue = this.colData[aColIndex];
      if (objectsAreEqual(oldValue, newValue, normalizeNewlines)) return;
      if (this.originalData == null)
      {
        createOriginalData();
      }

      if (this.originalData[aColIndex] == NO_CHANGE_MARKER)
      {
        this.originalData[aColIndex] = this.colData[aColIndex];
      }
    }
    this.colData[aColIndex] = newValue;
    this.setModified();
  }

  /**
   *  Returns the value for the given column.
   *
   *  @throws IndexOutOfBoundsException
   */
  public Object getValue(int aColumn)
    throws IndexOutOfBoundsException
  {
    return this.colData[aColumn];
  }

  /**
   * Returns the value from the specified column as it was retrieved from the database.
   *
   * If the column was not modified or this row is new
   * then the current value is returned.
   */
  public Object getOriginalValue(int aColumn)
    throws IndexOutOfBoundsException
  {
    if (!isNew() && this.isColumnModified(aColumn))
    {
      return this.originalData[aColumn];
    }
    return this.getValue(aColumn);
  }

  /**
   * Restore the value of a specific column to its original value.
   * <br/>
   * If the column has not been changed, nothing happens
   *
   * @param column the column to restore
   * @return the original (now current) value, null if no original value was present
   */
  public Object restoreOriginalValue(int column)
  {
    if (this.originalData == null) return null;
    if (isColumnModified(column))
    {
      this.colData[column] = this.originalData[column];
      resetStatusForColumn(column);
    }
    return this.colData[column];
  }

  /**
   * Restore the values to the ones initially retrieved from the database.
   * <br/>
   * After calling this, the status of this row will be <tt>NOT_MODIFIED</tt>
   *
   * @return true if there were original values
   *         false if nothing was restored
   */
  public boolean restoreOriginalValues()
  {
    if (this.originalData == null) return false;
    for (int i=0; i < this.originalData.length; i++)
    {
      if (this.originalData[i] != null)
      {
        this.colData[i] = this.originalData[i];
      }
    }
    this.originalData = null;
    this.resetStatus();
    return true;
  }

  public void resetStatusForColumn(int column)
  {
    if (!this.isNew() && this.originalData != null)
    {
      this.originalData[column] = NO_CHANGE_MARKER;
      for (Object originalData1 : originalData)
      {
        // if any other column has been modified, the status of the row
        // should not change
        if (originalData1 != NO_CHANGE_MARKER) return;
      }
      // all columns are now NOT_MODIFIED, so reset the row status as well
      this.resetStatus();
    }
  }

  /**
   * Returns true if the indicated column has been modified since the
   * initial retrieve. (i.e. since the last time resetStatus() was called
   *
   */
  public boolean isColumnModified(int aColumn)
  {
    if (this.isOriginal()) return false;
    if (this.isNew())
    {
      return this.colData[aColumn] != null;
    }
    else
    {
      if (this.originalData == null) return false;
      return (this.originalData[aColumn] != NO_CHANGE_MARKER);
    }
  }

  /**
   *  Resets the internal status. After a call to resetStatus()
   *  isModified() will return false, and isOriginal() will return true.
   */
  public void resetStatus()
  {
    this.status = NOT_MODIFIED;
    this.dmlSent = false;
    this.originalData = null;
  }

  /**
   * Resets data and status
   */
  public void reset()
  {
    Arrays.fill(colData, null);
    this.resetStatus();
  }

  /**
   *  Sets the status of this row to new.
   */
  public final void setNew()
  {
    this.status = NEW;
  }

  /**
   *  Returns true if the row is neither modified nor is a new row.
   *
   *  @return true if the row has not been altered since retrieval
   */
  public boolean isOriginal()
  {
    return this.status == NOT_MODIFIED;
  }

  /**
   * Check if the row has been modified.
   * <br/>
   * A row can be modified <b>and</b> new!
   *
   * @return true if the row has been modified since retrieval
   *
   */
  public boolean isModified()
  {
    return (this.status & MODIFIED) ==  MODIFIED;
  }

  /**
   * Check if the row has been added to the DataStore
   * after the initial retrieve.
   *
   * A row can be modified <b>and</b> new!
   *
   * @return true if it's a new row
   */
  public boolean isNew()
  {
    return (this.status & NEW) == NEW;
  }

  /**
   * Set the status to modified.
   */
  public void setModified()
  {
    this.status = this.status | MODIFIED;
  }

  void setDmlSent(boolean aFlag)
  {
    this.dmlSent = aFlag;
  }

  public boolean isDmlSent()
  {
    return this.dmlSent;
  }

  public List<String> getDependencyDeletes()
  {
    return this.dependencyDeletes;
  }

  public void setDependencyDeletes(List<String> statements)
  {
    if (CollectionUtil.isEmpty(statements))
    {
      this.dependencyDeletes = null;
    }
    else
    {
      this.dependencyDeletes = new ArrayList<>(statements);
    }
  }

  @Override
  public String toString()
  {
    ValueDisplay display = new ValueDisplay(colData);
    return display.toString();
  }

  @Override
  public int hashCode()
  {
    int hash = 7;
    hash = 59 * hash + (this.colData != null ? Arrays.hashCode(this.colData) : 0);
    return hash;
  }

  @Override
  public boolean equals(Object obj)
  {
    if (!(obj instanceof RowData)) return false;
    RowData other = (RowData)obj;
    if (other.colData.length != this.colData.length) return false;
    for (int i=0; i < colData.length; i++)
    {
      if (!objectsAreEqual(colData[i], other.colData[i])) return false;
    }
    return true;
  }

  /**
   * Compares the two values.
   * <br/>
   * Byte arrays are compared using Arrays.equals.
   * Numbers are compared using {@link NumberUtil#valuesAreEqual(java.lang.Number, java.lang.Number) }
   * which "normalized" that object's classes to compare the real values.
   * <br/>
   * For all other values, the equals() method is used.
   * <br/>
   *
   * @param one one value
   * @param other the other value
   *
   * @return true if they are equal
   *
   * @see NumberUtil#valuesAreEqual(java.lang.Number, java.lang.Number)
   * @see #objectsAreEqual(java.lang.Object, java.lang.Object, boolean)
   */
  public static boolean objectsAreEqual(Object one, Object other)
  {
    return objectsAreEqual(one, other, false);
  }

  /**
   * Compare the two values.
   *
   * @param one one value
   * @param other the other value
   * @param normalizeNewlines  if true, newlines are normalized for Strings before comparing them
   * @return true if they are equal
   *
   * @see NumberUtil#valuesAreEqual(java.lang.Number, java.lang.Number)
   */
  public static boolean objectsAreEqual(Object one, Object other, boolean normalizeNewlines)
  {
    if (one == null && other == null) return true;
    if (one == null || other == null) return false;

    // consider blobs
    if (one instanceof byte[] && other instanceof byte[])
    {
      return Arrays.equals((byte[])one, (byte[])other);
    }

    if (one instanceof Number && other instanceof Number)
    {
      return NumberUtil.valuesAreEqual((Number)one, (Number)other);
    }

    if (one instanceof Array && other instanceof Array)
    {
      return jdbcArraysAreEqual((Array)one, (Array)other, normalizeNewlines);
    }

    if (one instanceof Struct && other instanceof Struct)
    {
      return jdbcStructsAreEqual((Struct)one, (Struct)other, normalizeNewlines);
    }

    if (normalizeNewlines && (one instanceof String && other instanceof String))
    {
      String oneString = (String)one.toString();
      String otherString = (String)other.toString();

      if (containsNewLine(oneString) || containsNewLine(otherString))
      {
        String str1 = StringUtil.PATTERN_CRLF.matcher(oneString).replaceAll("\\n");
        String str2 = StringUtil.PATTERN_CRLF.matcher(otherString).replaceAll("\\n");

        return str1.equals(str2);
      }
    }
    return one.equals(other);
  }

  public static boolean jdbcStructsAreEqual(Struct one, Struct other, boolean normalizeNewLines)
  {
    if (one == null && other == null) return true;
    if (one == null || other == null) return false;

    try
    {
      Object[] attrOne = one.getAttributes();
      Object[] attrOther = other.getAttributes();
      return objectArraysAreEqual(attrOne, attrOther, normalizeNewLines);
    }
    catch (Throwable th)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not extract JDBC structure", th);
      return one.equals(other);
    }
  }

  public static boolean jdbcArraysAreEqual(Array one, Array other, boolean normalizeNewLines)
  {
    if (one == null && other == null) return true;
    if (one == null || other == null) return false;

    try
    {
      Object[] elementsOne = (Object[])one.getArray();
      Object[] elementsOther = (Object[])other.getArray();
      return objectArraysAreEqual(elementsOne, elementsOther, normalizeNewLines);
    }
    catch (Throwable th)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not extract JDBC array", th);
      return one.equals(other);
    }
  }

  private static boolean objectArraysAreEqual(Object[] one, Object[] other, boolean normalizeNewLines)
  {
    if (one == null && other == null) return true;
    if (one == null || other == null) return false;

    if (one.length != other.length) return false;

    int length = one.length;
    for (int i = 0; i < length; i++)
    {
      if (!objectsAreEqual(one[i], other[i], normalizeNewLines)) return false;
    }
    return true;
  }

  private static boolean containsNewLine(String s)
  {
    return s.indexOf('\r') > -1 || s.indexOf('\n') > -1;
  }

  public void dispose()
  {
    colData = null;
    originalData = null;
    if (dependencyDeletes != null)
    {
      dependencyDeletes.clear();
      dependencyDeletes = null;
    }
    userObject = null;
  }

  /**
   * Increases the internal storage and appends a new column to the end.
   *
   * This will clear any "modified" data and reset this row to "unmodified".
   */
  public void addColum()
  {
    Object[] newData = new Object[this.colData.length + 1];
    System.arraycopy(colData, 0, newData, 0, colData.length);
    colData = newData;
    resetStatus();
  }

  /**
   * Increases the internal storage and adds a new column at the specified position
   *
   * This will clear any "modified" data and reset this row to "unmodified".
   */
  public void addColum(int index)
  {
    int size = this.colData.length;
    Object[] newData = new Object[size + 1];

    System.arraycopy(this.colData, 0, newData, 0, index);
    System.arraycopy(this.colData, index, newData, index + 1, (size - index));
    colData = newData;
    resetStatus();
  }

  public void removeColumn(int index)
  {
    int size = this.colData.length;
    if (size <= 1) return;
    Object[] newData = new Object[size - 1];
    System.arraycopy(this.colData, 0, newData, 0, index);
    System.arraycopy(this.colData, index + 1, newData, index, (size - index - 1));
    colData = newData;
    resetStatus();
  }
}
