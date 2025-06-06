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

/**
 *
 * @author Thomas Kellerer
 */
public enum ExportType
{
  SQL_INSERT("SQL"),
  SQL_UPDATE("SQL Update"),
  SQL_DELETE_INSERT("SQL Delete/Insert"),
  SQL_DELETE("SQL Delete"),
  SQL_MERGE("SQL MERGE"),
  SQL_INSERT_IGNORE("SQL Insert/Ignore"),
  HTML("HTML"),
  TEXT("Text"),
  XML("XML"),
  ODS("OpenDocument Spreadsheet"),
  XLS("XLS"),
  XLSM("XLSM"),
  XLSX("XLSX"),
  JSON("JSON"),
  FORMATTED_TEXT("Formatted text"),
  DBUNIT_XML("DBUnit XML");

  private final String display;

  private ExportType(String disp)
  {
    display = disp;
  }

  @Override
  public String toString()
  {
    return display;
  }

  public static ExportType getExportType(String type)
  {
    if (type.equalsIgnoreCase("txt")) return TEXT;
    if (type.equalsIgnoreCase("sql")) return SQL_INSERT;
    if (type.equalsIgnoreCase("sqlinsert")) return SQL_INSERT;
    if (type.equalsIgnoreCase("sqlinsertignore")) return SQL_INSERT_IGNORE;
    if (type.equalsIgnoreCase("sqlupdate")) return SQL_UPDATE;
    if (type.equalsIgnoreCase("sqldeleteinsert")) return SQL_DELETE_INSERT;
    if (type.equalsIgnoreCase("sqldelete")) return SQL_DELETE;
    if (type.equalsIgnoreCase("sqlmerge")) return SQL_MERGE;
    if (type.equalsIgnoreCase("merge")) return SQL_MERGE;
    if (type.equalsIgnoreCase("formattedtext")) return FORMATTED_TEXT;
    if (type.equalsIgnoreCase("dbunit")) return DBUNIT_XML;

    try
    {
      return valueOf(type.toUpperCase());
    }
    catch (Exception ex)
    {
      return null;
    }
  }

  public static ExportType getTypeFromCode(String code)
  {
    if (code == null) return null;
    if (code.equals("1")) return SQL_INSERT;
    if (code.equals("2")) return TEXT;
    if (code.equals("3")) return XML;
    if (code.equals("4")) return HTML;
    if (code.equals("5")) return ODS;
    if (code.equals("6")) return XLS;
    if (code.equals("7")) return XLSM;
    if (code.equals("8")) return XLSX;
    if (code.equals("9")) return JSON;
    if (code.equals("10")) return FORMATTED_TEXT;
    if (code.equals("11")) return DBUNIT_XML;

    return null;
  }

  public boolean isSqlType()
  {
    return this == SQL_INSERT || this == SQL_UPDATE || this == SQL_DELETE_INSERT || this == SQL_DELETE || this == SQL_INSERT_IGNORE;
  }

  public String getDefaultFileExtension()
  {
    switch (this)
    {
      case SQL_INSERT:
      case SQL_UPDATE:
      case SQL_DELETE_INSERT:
      case SQL_DELETE:
      case SQL_INSERT_IGNORE:
        return ".sql";

      case TEXT:
      case FORMATTED_TEXT:
        return ".txt";

      case XML:
      case DBUNIT_XML:
        return ".xml";

      case HTML:
        return ".html";

      case ODS:
        return ".ods";

      case XLSX:
        return ".xlsx";

      case XLSM:
        return ".xml";

      case XLS:
        return ".xls";

      case JSON:
        return ".json";
    }
    return null;
  }

  public String getCode()
  {
    switch (this)
    {
      case SQL_INSERT:
      case SQL_UPDATE:
      case SQL_DELETE_INSERT:
      case SQL_DELETE:
      case SQL_INSERT_IGNORE:
        return "1";

      case TEXT:
        return "2";

      case XML:
        return "3";

      case HTML:
        return "4";

      case ODS:
        return "5";

      case XLS:
        return "6";

      case XLSM:
        return "7";

      case XLSX:
        return "8";

      case JSON:
        return "9";

      case FORMATTED_TEXT:
        return "10";

      case DBUNIT_XML:
        return "11";

    }
    return null;
  }

}
