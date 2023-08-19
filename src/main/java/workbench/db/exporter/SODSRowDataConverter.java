/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer.
 *
 * Licensed under a modified Apache License, Version 2.0 (the "License")
 * that restricts the use for certain governments.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.sql-workbench.eu/manual/license.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.eu
 */
package workbench.db.exporter;

import workbench.storage.RowData;
/**
 * A RowDataConverter that uses the SODS library to write ODS files.
 *
 * @author Thomas Kellerer
 */
public class SODSRowDataConverter
  extends RowDataConverter
{
  @Override
  public StringBuilder convertRowData(RowData row, long rowIndex)
  {
    return null;
  }

  @Override
  public StringBuilder getStart()
  {
    return null;
  }

  @Override
  public StringBuilder getEnd(long totalRows)
  {
    return null;
  }

}
