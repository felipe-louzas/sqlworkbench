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
package workbench.db.importer;

import java.io.File;
import java.io.IOException;
import java.util.List;

import workbench.util.MessageBuffer;
import workbench.util.WbFile;

/**
 *
 * @author Thomas Kellerer
 */
public interface SpreadsheetReader
{
  MessageBuffer getMessages();
  List<String> getHeaderColumns();
  void setActiveWorksheet(int index);
  void setActiveWorksheet(String name);
  List<Object> getRowValues(int row);
  void setNullString(String nullString);
  void setEmptyStringIsNull(boolean flag);
  void setReturnDatesAsString(boolean flag);
  void setReturnNumbersAsString(boolean flag);
  void enableRecalcOnLoad(boolean flag);

  /**
   * Return the total row count in the spreadsheet including a possible header row.
   *
   * @return the row count
   */
  int getRowCount();
  void done();
  void load()
    throws IOException;

  List<String> getSheets();

  public static class Factory
  {
    public static SpreadsheetReader createReader(File inputFile, int sheetIndex, String sheetName)
    {
      WbFile f = new WbFile(inputFile);
      String ext = f.getExtension();
      if (ext == null) return null;

      ext = ext.toLowerCase();

      if (ext.startsWith("xls"))
      {
        return new ExcelReader(inputFile, sheetIndex, sheetName);
      }
      else if (ext.equals("ods"))
      {
        return new SODSReader(inputFile, sheetIndex, sheetName);
      }
      return null;
    }
  }
}
