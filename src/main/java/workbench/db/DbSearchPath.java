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
package workbench.db;

import java.util.Collections;
import java.util.List;

import workbench.db.ibm.Db2SearchPath;
import workbench.db.postgres.PostgresUtil;
import workbench.db.redshift.RedshiftSearchPathHandler;

import workbench.util.CollectionUtil;

/**
 *
 * @author Thomas Kellerer
 */
public interface DbSearchPath
{
  boolean isRealSearchPath();
  List<String> getSearchPath(WbConnection dbConn, String defaultSchema);

  DbSearchPath DEFAULT_HANDLER = new DbSearchPath()
  {
    @Override
    public List<String> getSearchPath(WbConnection dbConn, String defaultSchema)
    {
      if (defaultSchema == null)
      {
        defaultSchema = dbConn.getCurrentSchema();
      }
      if (defaultSchema == null)
      {
        return Collections.emptyList();
      }
      return Collections.singletonList(dbConn.getMetadata().adjustSchemaNameCase(defaultSchema));
    }

    @Override
    public boolean isRealSearchPath()
    {
      return false;
    }
  };

  DbSearchPath PG_HANDLER = new DbSearchPath()
  {
    @Override
    public List<String> getSearchPath(WbConnection dbConn, String defaultSchema)
    {
      if (defaultSchema != null && dbConn != null)
      {
        return CollectionUtil.arrayList(dbConn.getMetadata().adjustSchemaNameCase(defaultSchema));
      }
      return PostgresUtil.getSearchPath(dbConn);
    }

    @Override
    public boolean isRealSearchPath()
    {
      return true;
    }
  };

  class Factory
  {
    public static DbSearchPath getSearchPathHandler(WbConnection con)
    {
      switch (DBID.fromConnection(con))
      {
        case Postgres:
        case Greenplum:
          return PG_HANDLER;
        case Redshift:
          return new RedshiftSearchPathHandler();
        case DB2_ISERIES:
          return new Db2SearchPath();
        default:
          return DEFAULT_HANDLER;
      }
    }
  }
}
