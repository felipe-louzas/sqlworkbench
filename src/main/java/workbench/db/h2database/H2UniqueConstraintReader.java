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
package workbench.db.h2database;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ConstraintDefinition;
import workbench.db.IndexDefinition;
import workbench.db.JdbcUtils;
import workbench.db.TableIdentifier;
import workbench.db.UniqueConstraintReader;
import workbench.db.WbConnection;

import workbench.util.CollectionUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class H2UniqueConstraintReader
  implements UniqueConstraintReader
{

  @Override
  public void readUniqueConstraints(TableIdentifier table, List<IndexDefinition> indexList, WbConnection con)
  {
    if (CollectionUtil.isEmpty(indexList))  return;
    if (con == null) return;

    String baseSql;
    if (JdbcUtils.hasMinimumServerVersion(con, "2.0"))
    {
      baseSql =
        "select unique_index_name, constraint_name\n" +
        "from information_schema.table_constraints \n" +
        "where constraint_type = 'UNIQUE' \n" +
        "  and (table_catalog, table_schema, table_name) = (?,?,?)";
    }
    else
    {
      baseSql =
        "select unique_index_name, constraint_name\n" +
        "from information_schema.constraints \n" +
        "where (table_catalog, table_schema, table_name) = (?,?,?)";
    }

    LogMgr.logMetadataSql(new CallerInfo(){}, "unique constraints", baseSql, table.getCatalog(), table.getSchema(), table.getTableName());

    PreparedStatement pstmt = null;
    ResultSet rs = null;
    try
    {
      pstmt = con.getSqlConnection().prepareStatement(baseSql);
      pstmt.setString(1, table.getCatalog());
      pstmt.setString(2, table.getSchema());
      pstmt.setString(3, table.getTableName());

      rs = pstmt.executeQuery();
      while (rs.next())
      {
        String idxName = rs.getString(1);
        String consName = rs.getString(2);

        IndexDefinition def = IndexDefinition.findIndex(indexList, idxName, null);
        if (def != null)
        {
          ConstraintDefinition cons = ConstraintDefinition.createUniqueConstraint(consName);
          def.setUniqueConstraint(cons);
          def.setIncludeIndexForUniqueConstraint(!idxName.equals(consName));
        }
      }
    }
    catch (SQLException se)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, se, "unique constraints", baseSql, table.getCatalog(), table.getSchema(), table.getTableName());
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }
  }

}
