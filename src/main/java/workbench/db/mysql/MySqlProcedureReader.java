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
package workbench.db.mysql;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.JdbcProcedureReader;
import workbench.db.JdbcUtils;
import workbench.db.ProcedureDefinition;
import workbench.db.ProcedureReader;
import workbench.db.WbConnection;

import workbench.storage.DataStore;

import workbench.util.StringUtil;

/**
 * A ProcedureReader for MySQL
 *
 * @author  Thomas Kellerer
 */
public class MySqlProcedureReader
  extends JdbcProcedureReader
{
  public MySqlProcedureReader(WbConnection con)
  {
    super(con);
  }

  @Override
  public StringBuilder getProcedureHeader(ProcedureDefinition def)
  {
    StringBuilder source = new StringBuilder(150);

    String sql =
      "SELECT routine_type, dtd_identifier \n" +
      "FROM information_schema.routines \n" +
      " WHERE routine_schema = ? \n" +
      "  and  routine_name = ? \n";

    LogMgr.logMetadataSql(new CallerInfo(){}, "procedure header", sql, def.getCatalog(), def.getProcedureName());

    String nl = Settings.getInstance().getInternalEditorLineEnding();
    PreparedStatement stmt = null;
    ResultSet rs = null;
    try
    {
      stmt = this.connection.getSqlConnection().prepareStatement(sql);
      stmt.setString(1, def.getCatalog());
      stmt.setString(2, def.getProcedureName());
      rs = stmt.executeQuery();
      String proctype = ProcedureReader.TYPE_NAME_PROC;
      String returntype = "";
      if (rs.next())
      {
        proctype = rs.getString(1);
        returntype = rs.getString(2);
      }
      source.append("CREATE ");
      source.append(proctype);
      source.append(' ');
      source.append(def.getProcedureName());
      source.append(" (");

      DataStore ds = this.getProcedureColumns(def);
      int count = ds.getRowCount();
      int added = 0;
      for (int i=0; i < count; i++)
      {
        String ret = ds.getValueAsString(i,ProcedureReader.COLUMN_IDX_PROC_COLUMNS_RESULT_TYPE);
        if (ret.equals("RETURN") || ret.equals("RESULTSET")) continue;
        String vartype = ds.getValueAsString(i,ProcedureReader.COLUMN_IDX_PROC_COLUMNS_DATA_TYPE);
        String name = ds.getValueAsString(i,ProcedureReader.COLUMN_IDX_PROC_COLUMNS_COL_NAME);
        if (added > 0) source.append(',');
        source.append(ret);
        source.append(' ');
        source.append(name);
        source.append(' ');
        source.append(vartype);
        added ++;
      }
      source.append(')');
      source.append(nl);
      if ("FUNCTION".equals(proctype))
      {
        source.append("RETURNS ");
        source.append(returntype);
        source.append(nl);
      }
    }
    catch (Exception e)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, e, "procedure header", sql, def.getCatalog(), def.getProcedureName());
      source = StringUtil.emptyBuilder();
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return source;
  }

}
