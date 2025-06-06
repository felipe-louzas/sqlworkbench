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

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.JdbcProcedureReader;
import workbench.db.WbConnection;

import workbench.storage.DataStore;

import workbench.db.JdbcUtils;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class HanaProcedureReader
  extends JdbcProcedureReader
{

  public HanaProcedureReader(WbConnection conn)
  {
    super(conn);
  }

  @Override
  public DataStore getProcedures(String catalog, String schema, String name)
    throws SQLException
  {
    // The SAP JDBC driver does not return functions, only procedures
    DataStore ds = super.getProcedures(catalog, schema, name);

    if (connection.getDbSettings().getBoolProperty("retrieve.functions", true))
    {
      appendFunctions(ds, schema);
    }

    return ds;
  }

  private void appendFunctions(DataStore ds, String schema)
  {
    ResultSet rs = null;
    Statement stmt = null;
    String sql =
      "select null as PROCEDURE_CAT, \n" +
      "       schema_name as PROCEDURE_SCHEM, \n" +
      "       function_name as PROCEDURE_NAME,\n" +
      "       null as remarks, \n" +
      "       " + DatabaseMetaData.procedureReturnsResult + " as PROCEDURE_TYPE \n" +
      "from sys.functions\n";
    try
    {
      if (StringUtil.isNotEmpty(schema))
      {
        sql += " where schema_name = '" + schema + "'";
      }

      stmt = this.connection.createStatement();
      rs = stmt.executeQuery(sql);
      fillProcedureListDataStore(rs, ds, false);
    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not read functions using: \n" + sql, ex);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
  }


}
