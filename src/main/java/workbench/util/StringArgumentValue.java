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
package workbench.util;

/**
 *
 * @author Thomas Kellerer
 */
public class StringArgumentValue
  implements ArgumentValue
{
  private String value;

  public StringArgumentValue(String argValue)
  {
    value = argValue;
  }

  @Override
  public String getDisplay()
  {
    return value;
  }

  @Override
  public String getValue()
  {
    return value;
  }

  @Override
  public String toString()
  {
    return value;
  }

  @Override
  public boolean equals(Object obj)
  {
    if (obj == null)
    {
      return false;
    }
    if (getClass() != obj.getClass())
    {
      return false;
    }
    final StringArgumentValue other = (StringArgumentValue) obj;
    if ((this.value == null) ? (other.value != null) : !this.value.equals(other.value))
    {
      return false;
    }
    return true;
  }

  @Override
  public int hashCode()
  {
    int hash = 5;
    hash = 89 * hash + (this.value != null ? this.value.hashCode() : 0);
    return hash;
  }


}
