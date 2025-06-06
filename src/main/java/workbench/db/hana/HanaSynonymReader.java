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
package workbench.db.hana;


import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.JdbcUtils;
import workbench.db.SynonymReader;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

/**
 *
 * @author  Thomas Kellerer
 */
public class HanaSynonymReader
  implements SynonymReader
{

  @Override
  public List<TableIdentifier> getSynonymList(WbConnection con, String catalog, String owner, String namePattern)
    throws SQLException
  {
    // Nothing to do. The HANA driver already returns the SYNONYMs in the getTables() call
    return Collections.emptyList();
  }

  @Override
  public TableIdentifier getSynonymTable(WbConnection con, String catalog, String schema, String synonym)
    throws SQLException
  {
    String sql =
      "-- SQL Workbench \n" +
      "SELECT object_schema, object_name, object_type \n" +
      "FROM sys.synonyms \n" +
      "WHERE schema_name = ? \n" +
      "  AND synonym_name = ? ";

    if (schema == null)
    {
      schema = con.getCurrentSchema();
    }

    LogMgr.logMetadataSql(new CallerInfo(){}, "synonyms", sql, schema, synonym);

    PreparedStatement stmt = null;
    ResultSet rs = null;

    TableIdentifier result = null;
    try
    {
      stmt = con.getSqlConnection().prepareStatement(sql);
      stmt.setString(1, schema);
      stmt.setString(2, synonym);

      rs = stmt.executeQuery();
      if (rs.next())
      {
        String targetSchema = rs.getString(1);
        String targetTable = rs.getString(2);
        String type = rs.getString(3);
        result = new TableIdentifier(null, targetSchema, targetTable);
        result.setNeverAdjustCase(true);
        result.setType(type);
      }
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }

    return result;
  }

}
