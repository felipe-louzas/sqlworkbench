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
package workbench.db.exporter;

import java.util.regex.Pattern;

import workbench.db.ColumnIdentifier;

import workbench.storage.RowData;

import workbench.util.SqlUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class RegexReplacingModifier
  implements ExportDataModifier
{
  private Pattern searchPattern;
  private String replacement;

  public RegexReplacingModifier(String searchRegex, String replaceWith)
  {
    searchPattern = Pattern.compile(searchRegex);
    replacement = replaceWith;
  }

  public String getRegex()
  {
    return searchPattern.pattern();
  }

  public String getReplacement()
  {
    return replacement;
  }

  @Override
  public void modifyData(RowDataConverter converter, RowData row, long currentRowNumber)
  {
    int colCount = row.getColumnCount();

    for (int col=0; col < colCount; col ++)
    {
      ColumnIdentifier column = converter.getResultInfo().getColumn(col);
      if (converter.includeColumnInExport(col) && SqlUtil.isCharacterType(column.getDataType()))
      {
        String value = (String)row.getValue(col);
        if (value != null)
        {
          row.setValue(col, replacePattern(value));
        }
      }
    }
  }

  public String replacePattern(String value)
  {
    if (value == null) return null;
    if (searchPattern == null) return value;
    if (replacement == null) return value;

    return searchPattern.matcher(value).replaceAll(replacement);
  }
}
