/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2020, Thomas Kellerer
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
package workbench.sql.annotations;


import java.util.List;


/**
 * A class to mark the next result to be shown as "text", rather than a tab in the GUI.
 *
 * @author Thomas Kellerer
 */
public class ResultAsTextAnnotation
  extends WbAnnotation
{
  public static final String ANNOTATION = "WbResultAsText";

  public ResultAsTextAnnotation()
  {
    super(ANNOTATION);
  }

  @Override
  public boolean needsValue()
  {
    return false;
  }

  public static ResultAsTextMode getMode(List<WbAnnotation> annotations)
  {
    for (WbAnnotation toCheck : annotations)
    {
      if (toCheck instanceof ResultAsTextAnnotation)
      {
        String value = toCheck.getValue();
        if (value == null)
        {
          return ResultAsTextMode.onceOnly;
        }
        if ("on".equalsIgnoreCase(value))
        {
          return ResultAsTextMode.turnOn;
        }
        if ("off".equalsIgnoreCase(value))
        {
          return ResultAsTextMode.turnOff;
        }
      }
    }
    return ResultAsTextMode.noChange;
  }

}
