/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2022, Thomas Kellerer
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
package workbench.db;

import workbench.db.mssql.SqlServerTriggerReader;
import workbench.db.oracle.OracleTriggerReader;
import workbench.db.postgres.PostgresTriggerReader;
import workbench.db.postgres.PostgresUtil;

/**
 * A factory to create instances of TriggerReader.
 *
 * @author Thomas Kellerer
 */
public class TriggerReaderFactory
{
  public static TriggerReader createReader(WbConnection con)
  {
    if (con == null) return null;
    if (con.getMetadata() == null) return null;

    if (con.getMetadata().isPostgres() && !PostgresUtil.isRedshift(con))
    {
      return new PostgresTriggerReader(con);
    }
    if (con.getMetadata().isOracle())
    {
      return new OracleTriggerReader(con);
    }
    if (con.getMetadata().isSqlServer())
    {
      return new SqlServerTriggerReader(con);
    }
    return new DefaultTriggerReader(con);
  }
}
