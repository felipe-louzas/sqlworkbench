/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2021, Thomas Kellerer
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
import java.sql.Statement;
import java.sql.Types;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.JdbcUtils;
import workbench.db.ObjectListEnhancer;
import workbench.db.TableIdentifier;
import workbench.db.ObjectListDataStore;
import workbench.db.WbConnection;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class Db2iObjectListEnhancer
  implements ObjectListEnhancer
{

  public static final String SYSTEM_NAME_DS_COL = "SYSTEM_NAME";
  private static final String REMARKS_PROP = "remarks.tables.use_tabletext";
  private static final String SYSNAME_PROP = "tablelist.read.system_name";

  @Override
  public void updateObjectList(WbConnection con, ObjectListDataStore result, String aCatalog, String schemaPattern, String objectPattern, String[] requestedTypes)
  {
    if (result == null || result.getRowCount() == 0) return;

    boolean readRemarks = con.getDbSettings().getBoolProperty(REMARKS_PROP, false);
    boolean readSystemNames = con.getDbSettings().getBoolProperty(SYSNAME_PROP, false);

    if (readSystemNames)
    {
      ColumnIdentifier sysName = new ColumnIdentifier(SYSTEM_NAME_DS_COL, Types.VARCHAR, 50);
      result.addColumn(sysName);
    }

    if (readRemarks || readSystemNames)
    {
      updateResult(con, result, schemaPattern, objectPattern, readRemarks);
    }
  }

  protected void updateResult(WbConnection con, ObjectListDataStore result, String schemaPattern, String objectPattern, boolean readRemarks)
  {
    long start = System.currentTimeMillis();
    String sql = buildQuery(con, schemaPattern, objectPattern);
    LogMgr.logMetadataSql(new CallerInfo(){}, "object info", sql);

    Statement stmt = null;
    ResultSet rs = null;

    int systemNameCol = result.getColumnIndex(SYSTEM_NAME_DS_COL);
    try
    {
      stmt = con.createStatementForQuery();
      rs = stmt.executeQuery(sql);
      while (rs.next())
      {
        String schema = rs.getString(1);
        String objectname = rs.getString(2);
        String remark = rs.getString(3);
        String systemName = rs.getString(4);
        int row = findRow(result, schema, objectname);
        if (row > -1)
        {
          TableIdentifier tbl = (TableIdentifier)result.getRow(row).getUserObject();
          if (readRemarks)
          {
            result.setRemarks(row, remark);
            if (tbl != null)
            {
              tbl.setComment(remark);
            }
          }

          if (systemNameCol > -1)
          {
            result.setValue(row, systemNameCol, systemName);
            if (tbl != null)
            {
              tbl.setSystemTablename(systemName);
            }
          }
        }
      }
      long duration = System.currentTimeMillis() - start;
      LogMgr.logDebug(new CallerInfo(){}, "Reading additional object information took " + duration + "ms");
    }
    catch (Exception e)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, e, "object info", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
  }

  private String buildQuery(WbConnection con, String schemaPattern, String objectPattern)
  {
    StringBuilder sql = new StringBuilder(50);

    sql.append(
      "select table_schema, table_name, table_text, system_table_name \n" +
      "from qsys2" + con.getMetadata().getCatalogSeparator() + "systables \n");

    boolean whereAdded = false;
    if (schemaPattern != null)
    {
      whereAdded = true;
      sql.append("where ");
      SqlUtil.appendExpression(sql, "table_schema", schemaPattern, con);
    }

    if (objectPattern != null)
    {
      if (whereAdded)
      {
        sql.append("\n  and ");
      }
      else
      {
        sql.append("where ");
      }
      SqlUtil.appendExpression(sql, "table_name", objectPattern, con);
    }
    return sql.toString();
  }

  private int findRow(ObjectListDataStore result, String schema, String object)
  {
    if (result == null) return -1;
    for (int row=0; row < result.getRowCount(); row ++)
    {
      String resultName = result.getObjectName(row);
      String resultSchema = result.getSchema(row);
      if (StringUtil.equalStringIgnoreCase(resultName, object) &&
          StringUtil.equalStringIgnoreCase(resultSchema, schema))
      {
        return row;
      }
    }
    return -1;
  }
}
