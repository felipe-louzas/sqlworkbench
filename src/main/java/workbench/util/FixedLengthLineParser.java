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

import java.util.List;

/**
 *
 * @author Thomas Kellerer
 */
public class FixedLengthLineParser
  implements LineParser
{
  private int currentColIndex;
  private int[] widths;
  private String line;
  private int currentLineIndex;
  private boolean trimValues  = false;

  public FixedLengthLineParser(List<Integer> colWidths)
  {
    if (colWidths == null)
    {
      throw new IllegalArgumentException("Column widths may not be null");
    }
    this.widths = new int[colWidths.size()];
    for (int i=0; i < colWidths.size(); i++)
    {
      this.widths[i] = colWidths.get(i).intValue();
    }
  }

  @Override
  public void setTrimValues(boolean flag)
  {
    this.trimValues = flag;
  }

  @Override
  public void setLine(String newLine)
  {
    this.line = newLine;
    this.currentColIndex = 0;
    this.currentLineIndex = 0;
  }

  @Override
  public boolean hasNext()
  {
    return currentColIndex < widths.length;
  }

  @Override
  public String getNext()
  {
    if (!hasNext())
    {
      return null;
    }
    int end = currentLineIndex + widths[currentColIndex];
    if (end > line.length())
    {
      end = line.length();
    }
    String result = line.substring(currentLineIndex, end);
    currentLineIndex += widths[currentColIndex];
    currentColIndex++;
    if (trimValues) return result.trim();
    return result;
  }
}
