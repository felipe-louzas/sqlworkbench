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
package workbench.sql.preparedstatement;

import java.sql.ParameterMetaData;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.util.NumberStringCache;
import workbench.util.SqlUtil;
import workbench.util.ValueConverter;

/**
 * The defintion for a single parameter in StatementParameters
 *
 * @see StatementParameters
 * @author  Thomas Kellerer
 */
public class ParameterDefinition
{
  private final ValueConverter converter = new ValueConverter();
  private final int dataType;
  private final int parameterIndex;
  private String parameterName;

  /**
   * Parameter mode according to
   * ParameterMetaData.parameterModeOut
   * ParameterMetaData.parameterModeIn
   * ParameterMetaData.parameterModeInOut;
   */
  private int parameterMode = ParameterMetaData.parameterModeUnknown;

  private boolean valueValid = false;
  private Object value = null;

  public ParameterDefinition(int index, int type)
  {
    this.parameterIndex = index;
    this.dataType = type;
  }

  public int getIndex()
  {
    return this.parameterIndex;
  }

  public int getType()
  {
    return this.dataType;
  }

  public boolean isValueValid(String v)
  {
    try
    {
      converter.convertValue(v, this.dataType);
      return true;
    }
    catch (Exception e)
    {
      return false;
    }
  }

  public void setParameterName(String parm)
  {
    this.parameterName = parm;
  }

  public String getParameterName()
  {
    if (parameterName == null) return NumberStringCache.getNumberString(this.parameterIndex);
    return parameterName;
  }

  public boolean setValue(String v)
  {
    try
    {
      this.value = converter.convertValue(v, this.dataType);
      this.valueValid = true;
    }
    catch (Exception e)
    {
      this.valueValid = false;
      LogMgr.logError(new CallerInfo(){}, "Error applying value " + v + " for type " + SqlUtil.getTypeName(this.dataType), e);
    }
    return this.valueValid;
  }

  public Object getValue()
  {
    return this.value;
  }

  public void setStatementValue(PreparedStatement stmt)
    throws IllegalStateException, SQLException
  {
    if (!this.valueValid) throw new IllegalStateException("No valid value defined for parameter #" + this.parameterIndex);
    stmt.setObject(this.parameterIndex, this.value);
  }

  public static void sortByIndex(List<ParameterDefinition> parameters)
  {
    Comparator<ParameterDefinition> comp = (ParameterDefinition p1, ParameterDefinition p2) -> p1.parameterIndex - p2.parameterIndex;
    Collections.sort(parameters, comp);
  }

  @Override
  public String toString()
  {
    return this.parameterName + "=" + this.value + " (" + parameterIndex + ")";
  }

  public String getModeString()
  {
    switch (parameterMode)
    {
      case ParameterMetaData.parameterModeOut:
        return "OUT";
      case ParameterMetaData.parameterModeIn:
        return "IN";
      case ParameterMetaData.parameterModeInOut:
        return "INOUT";
      default:
        return "N/A (" + parameterMode + ")";
    }
  }

  public boolean isOutParameter()
  {
    return parameterMode == ParameterMetaData.parameterModeOut || parameterMode == ParameterMetaData.parameterModeInOut;
  }

  public int getParameterMode()
  {
    return parameterMode;
  }

  public void setParameterMode(int mode)
  {
    if (mode != ParameterMetaData.parameterModeOut &&
        mode != ParameterMetaData.parameterModeIn &&
        mode != ParameterMetaData.parameterModeInOut &&
        mode != ParameterMetaData.parameterModeUnknown)
    {
      throw new IllegalArgumentException("Incorrect parameter mode specified!");
    }
    this.parameterMode = mode;
  }
}
