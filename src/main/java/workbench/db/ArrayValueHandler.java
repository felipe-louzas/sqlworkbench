/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
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

import java.sql.PreparedStatement;
import java.sql.SQLException;

import workbench.db.compare.BatchedStatement;
import workbench.db.hsqldb.HsqlArrayHandler;
import workbench.db.postgres.PostgresArrayHandler;

/**
 *
 * @author Thomas Kellerer
 */
public interface ArrayValueHandler
{
  void setValue(BatchedStatement stmt, int columnIndex, Object data, ColumnIdentifier colInfo)
    throws SQLException;

  void setValue(PreparedStatement stmt, int columnIndex, Object data, ColumnIdentifier colInfo)
    throws SQLException;

  public static class Factory
  {
    public static ArrayValueHandler getInstance(WbConnection conn)
    {
      if (conn == null) return null;
      switch (DBID.fromConnection(conn))
      {
        case Postgres:
          return new PostgresArrayHandler(conn);
        case HSQLDB:
          return new HsqlArrayHandler(conn);
      }
      return null;
    }
  }
}
