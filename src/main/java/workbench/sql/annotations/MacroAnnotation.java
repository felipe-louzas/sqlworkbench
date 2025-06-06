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
package workbench.sql.annotations;

import java.util.Map;
import java.util.TreeMap;

import workbench.util.ArgumentParser;
import workbench.util.ArgumentType;
import workbench.util.CaseInsensitiveComparator;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class MacroAnnotation
  extends WbAnnotation
{
  public static final String MAP_KEYWORD = "map";
  public static final String NAME_KEYWORD = "name";
  public static final String REFRESH_KEYWORD = "refresh";
  public static final String ANNOTATION = "WbMacro";

  private final Map<String, String> columnMap = new TreeMap<>(CaseInsensitiveComparator.INSTANCE);
  private boolean refreshResult;

  public MacroAnnotation()
  {
    super(ANNOTATION);
  }

  @Override
  public void setValue(String value)
  {
    if (StringUtil.isBlank(value))
    {
      super.setValue(null);
    }
    else
    {
      ArgumentParser parser = new ArgumentParser(false);
      parser.addArgument(MAP_KEYWORD, ArgumentType.RepeatableValue);
      parser.addArgument(NAME_KEYWORD);
      parser.addArgument(REFRESH_KEYWORD, ArgumentType.BoolArgument);
      parser.parse(value);
      Map<String, String> map = parser.getMapValue(MAP_KEYWORD);
      if (map != null)
      {
        columnMap.clear();
        columnMap.putAll(map);
      }
      refreshResult = parser.getBoolean(REFRESH_KEYWORD, false);
      super.setValue(parser.getValue(NAME_KEYWORD));
    }

  }

  public boolean getRefreshResult()
  {
    return refreshResult;
  }

  public String getMacroName()
  {
    return getValue();
  }

  public Map<String, String> getColumnMap()
  {
    return columnMap;
  }
  
}
