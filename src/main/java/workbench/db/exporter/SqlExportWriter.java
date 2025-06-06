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

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.TableIdentifier;

/**
 * Export data as SQL statements.
 *
 * @author  Thomas Kellerer
 */
public class SqlExportWriter
  extends ExportWriter
{

  public SqlExportWriter(DataExporter exp)
  {
    super(exp);
    canAppendStart = true;
  }

  @Override
  public RowDataConverter createConverter()
  {
    return new SqlRowDataConverter(exporter.getConnection());
  }

  @Override
  public void configureConverter()
  {
    super.configureConverter();
    SqlRowDataConverter conv = (SqlRowDataConverter)this.converter;
    conv.setIncludeTableOwner(exporter.getUseSchemaInSql());
    conv.setCommitEvery(exporter.getCommitEvery());
    conv.setChrFunction(exporter.getChrFunction());
    conv.setConcatString(exporter.getConcatString());
    conv.setConcatFunction(exporter.getConcatFunction());
    conv.setDateLiteralType(exporter.getDateLiteralType());
    conv.setLineEnding(exporter.getLineEnding());

    conv.setBlobMode(exporter.getBlobMode());

    if (exporter.getWriteClobAsFile())
    {
      String encoding = exporter.getEncoding();
      if (encoding == null) encoding = Settings.getInstance().getDefaultFileEncoding();
      conv.setClobAsFile(encoding, exporter.getClobSizeThreshold());
    }

    // the key columns need to be set before the createInsert flag!
    conv.setKeyColumnsToUse(exporter.getKeyColumnsToUse());
    try
    {
      conv.setType(exporter.getExportType());
      conv.setMergeType(exporter.getMergeType());
    }
    catch (IllegalArgumentException e)
    {
      LogMgr.logError(new CallerInfo(){}, "Illegal SQL type requested. Reverting to INSERT", null);
      conv.setCreateInsert();
    }

    switch (exporter.getExportType())
    {
        case SQL_INSERT:
        case SQL_DELETE_INSERT:
        case SQL_INSERT_IGNORE:
          conv.setApplySQLFormatting(Settings.getInstance().getDoFormatInserts());
        case SQL_UPDATE:
          conv.setApplySQLFormatting(Settings.getInstance().getDoFormatUpdates());
        case SQL_DELETE:
          conv.setApplySQLFormatting(Settings.getInstance().getDoFormatDeletes());
        default:
          conv.setApplySQLFormatting(false);
    }

    String table = exporter.getTableName();
    if (table != null)
    {
      conv.setAlternateUpdateTable(new TableIdentifier(table, exporter.getConnection()));
    }
    conv.setCreateTable(exporter.isIncludeCreateTable());
  }

}
