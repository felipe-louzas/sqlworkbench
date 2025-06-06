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
package workbench.db.ibm;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ConstraintDefinition;
import workbench.db.DBID;
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
public class DB2UniqueConstraintReader
  implements UniqueConstraintReader
{

  @Override
  public void readUniqueConstraints(TableIdentifier table, List<IndexDefinition> indexList, WbConnection con)
  {
    if (CollectionUtil.isEmpty(indexList))  return;
    if (con == null) return;

    StringBuilder sql = new StringBuilder(500);

    switch (DBID.fromConnection(con))
    {
      case DB2_LUW:
        sql.append(
          "select indname, indschema, constname \n " +
          "from ( \n" +
          "  select ind.indname, ind.indschema, tc.constname \n" +
          "  from syscat.indexes ind \n" +
          "    join syscat.tabconst tc \n " +
          "      on ind.tabschema = tc.tabschema \n " +
          "     and ind.tabname = tc.tabname \n " +
          "     and tc.constname = ind.indname \n" +
          "  where type = 'U' \n" +
          ") t \n " +
          "where (");
        break;

      case DB2_ZOS:
        // DB2 host
        sql.append(
          "select indname, indschema, constname \n" +
          "from ( \n" +
          "  select ind.name as indname, ind.creator as indschema, tc.constname  \n" +
          "  from sysibm.sysindexes ind \n" +
          "    join sysibm.systabconst tc \n " +
          "      on ind.tbcreator = tc.tbcreator \n " +
          "     and ind.tbname = tc.tbname \n " +
          "     and tc.constname = ind.name \n" +
          "  where type = 'U' \n " +
          ") t\n " +
          "where (");
      default:
        // Not yet supported for db2 iSeries
        return;
    }

    boolean first = true;
    int idxCount = 0;

    for (IndexDefinition idx : indexList)
    {
      if (!idx.isUnique() || idx.isPrimaryKeyIndex())
      {
        continue;
      }
      if (first)
      {
        first = false;
      }
      else
      {
        sql.append(" OR ");
      }

      idxCount ++;
      String schema = con.getMetadata().removeQuotes(idx.getSchema());
      String idxName = con.getMetadata().removeQuotes(idx.getObjectName());
      sql.append(" (indname = '");
      sql.append(idxName);
      sql.append("' AND indschema = '");
      sql.append(schema);
      sql.append("') ");
    }
    sql.append(')');

    if (idxCount == 0)
    {
      return;
    }

    LogMgr.logMetadataSql(new CallerInfo(){}, "unique constraints", sql);

    Statement stmt = null;
    ResultSet rs = null;
    try
    {
      stmt = con.createStatement();
      rs = stmt.executeQuery(sql.toString());
      while (rs.next())
      {
        String idxName = rs.getString(1).trim();
        String idxSchema = rs.getString(2).trim();
        String consName = rs.getString(3).trim();
        IndexDefinition def = IndexDefinition.findIndex(indexList, idxName, idxSchema);
        if (def != null)
        {
          ConstraintDefinition cons = ConstraintDefinition.createUniqueConstraint(consName);
          def.setUniqueConstraint(cons);
          def.setIncludeIndexForUniqueConstraint(false);
        }
      }
    }
    catch (SQLException se)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, se, "unique constraints", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
  }

}
