/*
 * RegExComparator.java
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
package workbench.storage.filter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import workbench.resource.ResourceMgr;

/**
 * Implementation of the ColumnComparator using regular expressions.
 *
 * @author Thomas Kellerer
 */
public class RegExComparator
  implements ColumnComparator
{

  @Override
  public boolean supportsIgnoreCase()
  {
    return true;
  }

  @Override
  public String getValueExpression(Object value)
  {
    return "'" + value + "'";
  }

  @Override
  public String getUserDisplay()
  {
    return ResourceMgr.getString("TxtOpMatches");
  }

  @Override
  public String getOperator()
  {
    return "matches";
  }

  @Override
  public boolean needsValue()
  {
    return true;
  }

  @Override
  public boolean comparesEquality()
  {
    return false;
  }

  @Override
  public boolean supportsType(Class valueClass)
  {
    return (String.class.isAssignableFrom(valueClass));
  }

  @Override
  public boolean evaluate(Object reference, Object value, boolean ignoreCase)
  {
    if (reference == null || value == null)
    {
      return false;
    }

    Pattern p = null;
    if (ignoreCase)
    {
      p = Pattern.compile(reference.toString(), Pattern.CASE_INSENSITIVE);
    }
    else
    {
      p = Pattern.compile(reference.toString());
    }
    Matcher m = p.matcher(value.toString());

    return m.find();
  }

  @Override
  public boolean equals(Object other)
  {
    return (other.getClass().equals(this.getClass()));
  }

  @Override
  public boolean validateInput(Object value)
  {
    if (value == null)
    {
      return false;
    }

    try
    {
      Pattern.compile(value.toString());
      return true;
    }
    catch (Exception e)
    {
      return false;
    }
  }
}
