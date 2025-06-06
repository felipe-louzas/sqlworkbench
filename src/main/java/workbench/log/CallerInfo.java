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
package workbench.log;

import java.lang.reflect.Method;

/**
 *
 * @author Thomas Kellerer
 */
public abstract class CallerInfo
{
  private String info;

  public CallerInfo()
  {
  }

  public CallerInfo(String info)
  {
    this.info = info;
  }

  public Class getCallingClass()
  {
    return getClass().getEnclosingClass();
  }
  
  @Override
  public String toString()
  {
    if (info == null)
    {
      generateInfo();
    }
    return info;
  }

  private void generateInfo()
  {
    try
    {
      info = getClass().getEnclosingClass().getSimpleName() + ".";
      Method m = getClass().getEnclosingMethod();
      if (m == null)
      {
        info += "<init>";
      }
      else
      {
        info += m.getName() + "()";
      }
    }
    catch (Throwable th)
    {
      // ignore
    }
  }
}
