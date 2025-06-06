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

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import workbench.db.ibm.Db2FormatFileWriter;
import workbench.db.mssql.SqlServerFormatFileWriter;
import workbench.db.mysql.MySQLLoadDataWriter;
import workbench.db.oracle.OracleControlFileWriter;
import workbench.db.postgres.PostgresCopyStatementWriter;
import workbench.util.StringUtil;

/**
 * Type definitions for various control file formats.
 *
 * Currently Oracle, SQL Server, PostgreSQL (COPY command), DB2 and MySQL (LOAD DATA INFILE ...) are covered
 *
 * @author Thomas Kellerer
 * @see OracleControlFileWriter
 * @see SqlServerFormatFileWriter
 * @see PostgresCopyStatementWriter
 * @see Db2FormatFileWriter
 * @see MySQLLoadDataWriter
 */
public enum ControlFileFormat
{
  none,
  oracle,
  sqlserver,
  postgres,
  db2,
  mysql;

  public static Set<ControlFileFormat> parseCommandLine(String args)
    throws WrongFormatFileException
  {
    if (StringUtil.isEmpty(args)) return Collections.emptySet();
    Set<ControlFileFormat> result = EnumSet.noneOf(ControlFileFormat.class);
    List<String> formats = StringUtil.stringToList(args);
    for (String fs : formats)
    {
      try
      {
        ControlFileFormat f = ControlFileFormat.valueOf(fs);
        result.add(f);
      }
      catch (Exception e)
      {
        throw new WrongFormatFileException(fs);
      }
    }
    return result;
  }

  public static FormatFileWriter createFormatWriter(ControlFileFormat format)
  {
    switch (format)
    {
      case postgres:
        return new PostgresCopyStatementWriter();
      case oracle:
        return new OracleControlFileWriter();
      case sqlserver:
        return new SqlServerFormatFileWriter();
      case db2:
        return new Db2FormatFileWriter();
      case mysql:
        return new MySQLLoadDataWriter();
      default:
        return null;
    }
  }
}
