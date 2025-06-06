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

import workbench.resource.Settings;

import workbench.db.TableIdentifier;

import workbench.storage.RowData;

import workbench.util.CharacterRange;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;
import workbench.util.WbDateFormatter;
import workbench.util.WbFile;
import workbench.util.WbNumberFormatter;

/**
 * Converts data from the database into JSON format.
 *
 * @author  Thomas Kellerer
 */
public class JsonRowDataConverter
  extends RowDataConverter
{
  private boolean useResultName;

  public JsonRowDataConverter()
  {
    defaultDateFormatter = new WbDateFormatter(StringUtil.ISO_DATE_FORMAT);
    defaultTimestampFormatter = new WbDateFormatter(StringUtil.ISO_TIMESTAMP_FORMAT);
    defaultNumberFormatter = new WbNumberFormatter(-1, '.');
    defaultTimeFormatter = new WbDateFormatter("HH:mm:ss");
    useResultName = Settings.getInstance().getBoolProperty("workbench.export.json.default.include.resultname", false);
  }

  public void setUseResultName(boolean flag)
  {
    useResultName = flag;
  }

  @Override
  public StringBuilder getStart()
  {
    String resultName = getResultName();

    StringBuilder header = new StringBuilder(20);
    if (StringUtil.isBlank(resultName))
    {
      // no result name, write an array directly, not wrapped into an object
      header.append("[\n");
    }
    else
    {
      header.append("{\n  \"");
      header.append(resultName.toLowerCase());
      header.append("\":\n  [\n");
    }
    return header;
  }

  private String getResultName()
  {
    if (!useResultName)
    {
      return null;
    }

    if (exporter != null && exporter.getResultName() != null)
    {
      return exporter.getResultName();
    }

    TableIdentifier updateTable = this.metaData.getUpdateTable();
    if (updateTable != null)
    {
      return updateTable.getRawTableName();
    }
    else if (getOutputFile() != null)
    {
      WbFile f = new WbFile(getOutputFile());
      return f.getFileName();
    }
    return null;
  }

  private boolean hasObjectWrapper()
  {
    return StringUtil.isNotBlank(getResultName());
  }

  @Override
  public StringBuilder getEnd(long totalRows)
  {
    StringBuilder end = new StringBuilder(10);

    if (hasObjectWrapper())
    {
      end.append("\n  ]\n}");
    }
    else
    {
      end.append("\n]");
    }
    return end;
  }

  @Override
  public StringBuilder convertRowData(RowData row, long rowIndex)
  {
    int count = this.metaData.getColumnCount();

    StringBuilder result = new StringBuilder(count * 30);

    String indent = hasObjectWrapper() ? "    " : "  ";
    int currentColIndex = 0;

    if (rowIndex > 0)
    {
      result.append(",\n");
    }
    result.append(indent);
    result.append("{");
    for (int c=0; c < count; c++)
    {
      if (!this.includeColumnInExport(c)) continue;

      if (currentColIndex > 0)
      {
        result.append(", ");
      }

      int colType = this.metaData.getColumnType(c);

      String value = this.getValueAsFormattedString(row, c);

      boolean isNull = (value == null);
      if (isNull)
      {
        value = "null";
      }

      if (SqlUtil.isCharacterType(colType) && !isNull)
      {
        value = StringUtil.escapeText(value, CharacterRange.RANGE_CONTROL, "");
        if (value.indexOf('"') > -1)
        {
          value = value.replace("\"", "\\\"");
        }
      }

      result.append("\"");
      result.append(this.metaData.getColumnName(c));
      result.append("\": ");
      if (!isNull) result.append('"');
      result.append(value);
      if (!isNull) result.append('"');

      currentColIndex ++;
    }
    result.append("}");
    return result;
  }

}
