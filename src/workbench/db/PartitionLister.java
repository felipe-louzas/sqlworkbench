/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2020 Thomas Kellerer.
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
package workbench.db;

import java.util.List;

import workbench.db.oracle.OraclePartitionLister;
import workbench.db.postgres.PostgresPartitionLister;

/**
 *
 * @author Thomas Kellerer
 */
public interface PartitionLister
{
  public static final String PARTITION_TYPE_NAME = "PARTITION";

  List<? extends DbObject> getPartitions(TableIdentifier table);
  List<? extends DbObject> getSubPartitions(TableIdentifier baseTable, DbObject mainPartition);
  boolean supportsSubPartitions();

  public static class Factory
  {
    public static PartitionLister createReader(WbConnection conn)
    {
      try
      {
        DBID dbid = DBID.fromConnection(conn);
        switch (dbid)
        {
          case Postgres:
            return new PostgresPartitionLister(conn);
          case Oracle:
            return new OraclePartitionLister(conn);
        }
      }
      catch (Throwable th)
      {
        // ignore
      }
      return null;
    }
  }
}
