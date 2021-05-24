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
package workbench.db.redshift;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;

import workbench.log.CallerInfo;

import workbench.db.JdbcProcedureReader;
import workbench.db.NoConfigException;
import workbench.db.ProcedureDefinition;
import workbench.db.ProcedureReader;
import workbench.db.WbConnection;
import workbench.db.postgres.PGProcName;

import workbench.log.LogMgr;

import workbench.storage.DataStore;

import workbench.util.ExceptionUtil;

import workbench.db.JdbcUtils;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/*
 * @author  Miguel Cornejo Silva
 */
public class RedshiftUDFReader
  extends JdbcProcedureReader
{
  public RedshiftUDFReader(WbConnection conn)
  {
    super(conn);
    try
    {
      this.useSavepoint = conn.supportsSavepoints();
    }
    catch (Throwable th)
    {
      this.useSavepoint = false;
    }
  }

  @Override
  public DataStore getProcedures(String catalog, String schemaPattern, String procName)
    throws SQLException
  {
    DataStore fullDs = super.getProcedures(catalog, procName, procName);
    DataStore ds = buildProcedureListDataStore(this.connection.getMetadata(), true);

    if ("*".equals(schemaPattern) || "%".equals(schemaPattern))
    {
      schemaPattern = null;
    }

    String namePattern = null;
    if ("*".equals(procName) || "%".equals(procName))
    {
      namePattern = null;
    }
    else if (StringUtil.isNonBlank(procName))
    {
      PGProcName pg = new PGProcName(procName);
      namePattern = pg.getName();
    }

    for (int i = 0; i < fullDs.getRowCount(); i++)
    {
      String specname = fullDs.getValueAsString(i, ProcedureReader.COLUMN_IDX_PROC_LIST_SPECIFIC_NAME);
      String cat = fullDs.getValueAsString(i, ProcedureReader.COLUMN_IDX_PROC_LIST_CATALOG);
      String schema = fullDs.getValueAsString(i, ProcedureReader.COLUMN_IDX_PROC_LIST_SCHEMA);
      String displayName = fullDs.getValueAsString(i, ProcedureReader.COLUMN_IDX_PROC_LIST_NAME);
      String bfDisplayName = i > 0 ? fullDs.getValueAsString(i - 1, ProcedureReader.COLUMN_IDX_PROC_LIST_NAME) : "";
      Object procType = fullDs.getValue(i, ProcedureReader.COLUMN_IDX_PROC_LIST_TYPE);
      String remark = fullDs.getValueAsString(i, ProcedureReader.COLUMN_IDX_PROC_LIST_REMARKS);
      ProcedureDefinition def = (ProcedureDefinition)fullDs.getRow(i).getUserObject();

      if ((namePattern == null || specname.contains(namePattern)) &&
          (schemaPattern == null || StringUtil.equalStringIgnoreCase(schema, schemaPattern)) &&
          (catalog == null || StringUtil.equalStringIgnoreCase(catalog, cat)) && !bfDisplayName.equals(displayName))
      {
        int row = ds.addRow();

        ds.setValue(row, ProcedureReader.COLUMN_IDX_PROC_LIST_SPECIFIC_NAME, specname);
        ds.setValue(row, ProcedureReader.COLUMN_IDX_PROC_LIST_CATALOG, cat);
        ds.setValue(row, ProcedureReader.COLUMN_IDX_PROC_LIST_SCHEMA, schema);
        ds.setValue(row, ProcedureReader.COLUMN_IDX_PROC_LIST_NAME, displayName);
        ds.setValue(row, ProcedureReader.COLUMN_IDX_PROC_LIST_TYPE, procType);
        ds.setValue(row, ProcedureReader.COLUMN_IDX_PROC_LIST_REMARKS, remark);
        ds.getRow(row).setUserObject(def);
      }
    }

    return ds;
  }

  @Override
  public void readProcedureSource(ProcedureDefinition def, String catalogForSource, String schemaForSource)
    throws NoConfigException
  {
    PGProcName name = new PGProcName(def);

    String sql = "SELECT DDL \n" +
      " FROM ( WITH arguments \n" +
      " AS \n" +
      " (SELECT oid, \n" +
      "        i, \n" +
      "        arg_name[i] AS argument_name, \n" +
      "        arg_types[i -1] argument_type \n" +
      " FROM (SELECT generate_series(1,arg_count) AS i, \n" +
      "              arg_name, \n" +
      "              arg_types, \n" +
      "              oid \n" +
      "       FROM (SELECT oid, \n" +
      "                    proargnames arg_name, \n" +
      "                    proargtypes arg_types, \n" +
      "                    pronargs arg_count \n" +
      "             FROM pg_proc \n" +
      "             WHERE proowner != 1) t) t)  \n" +
      "      SELECT schemaname,udfname,udfoid,seq,trim (ddl) ddl  \n" +
      "      FROM (SELECT n.nspname AS schemaname, \n" +
      "                   p.proname AS udfname, \n" +
      "                   p.oid AS udfoid, \n" +
      "                   1000 AS seq, \n" +
      "                   ('CREATE FUNCTION ' || QUOTE_IDENT(p.proname) || ' \\(')::VARCHAR (MAX) AS ddl  \n" +
      "      FROM pg_proc p \n" +
      "   LEFT JOIN pg_namespace n ON n.oid = p.pronamespace \n" +
      " WHERE p.proowner != 1 \n" +
      " UNION ALL \n" +
      " SELECT n.nspname AS schemaname, \n" +
      "        p.proname AS udfname, \n" +
      "        p.oid AS udfoid, \n" +
      "        2000 + nvl(i,0) AS seq, \n" +
      "        CASE \n" +
      "          WHEN i = 1 THEN NVL (argument_name,'') || ' ' || format_type (argument_type,NULL) \n" +
      "          ELSE ',' || NVL (argument_name,'') || ' ' || format_type (argument_type,NULL) \n" +
      "        END AS ddl \n" +
      " FROM pg_proc p \n" +
      "   LEFT JOIN pg_namespace n ON n.oid = p.pronamespace \n" +
      "   LEFT JOIN arguments a ON a.oid = p.oid \n" +
      " WHERE p.proowner != 1 \n" +
      " UNION ALL \n" +
      " SELECT n.nspname AS schemaname, \n" +
      "        p.proname AS udfname, \n" +
      "        p.oid AS udfoid, \n" +
      "        3000 AS seq, \n" +
      "        '\\)\\n' AS ddl \n" +
      " FROM pg_proc p \n" +
      "   LEFT JOIN pg_namespace n ON n.oid = p.pronamespace \n" +
      " WHERE p.proowner != 1 \n" +
      " UNION ALL \n" +
      " SELECT n.nspname AS schemaname, \n" +
      "        p.proname AS udfname, \n" +
      "        p.oid AS udfoid, \n" +
      "        4000 AS seq, \n" +
      "        '  RETURNS ' || pg_catalog.format_type(p.prorettype,NULL) || '\\n'AS ddl \n" +
      " FROM pg_proc p \n" +
      "   LEFT JOIN pg_namespace n ON n.oid = p.pronamespace \n" +
      " WHERE p.proowner != 1 \n" +
      " UNION ALL \n" +
      " SELECT n.nspname AS schemaname, \n" +
      "        p.proname AS udfname, \n" +
      "        p.oid AS udfoid, \n" +
      "        5000 AS seq, \n" +
      "        CASE \n" +
      "          WHEN p.provolatile = 'v' THEN 'VOLATILE\\n' \n" +
      "          WHEN p.provolatile = 's' THEN 'STABLE\\n' \n" +
      "          WHEN p.provolatile = 'i' THEN 'IMMUTABLE\\n' \n" +
      "          ELSE '' \n" +
      "        END AS ddl \n" +
      " FROM pg_proc p \n" +
      "   LEFT JOIN pg_namespace n ON n.oid = p.pronamespace \n" +
      " WHERE p.proowner != 1 \n" +
      " UNION ALL \n" +
      " SELECT n.nspname AS schemaname, \n" +
      "        p.proname AS udfname, \n" +
      "        p.oid AS udfoid, \n" +
      "        6000 AS seq, \n" +
      "        'AS $$' AS ddl \n" +
      " FROM pg_proc p \n" +
      "   LEFT JOIN pg_namespace n ON n.oid = p.pronamespace \n" +
      " WHERE p.proowner != 1 \n" +
      " UNION ALL \n" +
      " SELECT n.nspname AS schemaname, \n" +
      "        p.proname AS udfname, \n" +
      "        p.oid AS udfoid, \n" +
      "        7000 AS seq, \n" +
      "        p.prosrc AS DDL \n" +
      " FROM pg_proc p \n" +
      "   LEFT JOIN pg_namespace n ON n.oid = p.pronamespace \n" +
      " WHERE p.proowner != 1 \n" +
      " UNION ALL \n" +
      " SELECT n.nspname AS schemaname, \n" +
      "        p.proname AS udfname, \n" +
      "        p.oid AS udfoid, \n" +
      "        8000 AS seq, \n" +
      "        '$$ LANGUAGE ' + lang.lanname || '\\n;\\n\\n' AS ddl \n" +
      " FROM pg_proc p \n" +
      "   LEFT JOIN pg_namespace n ON n.oid = p.pronamespace \n" +
      "   LEFT JOIN (SELECT oid, lanname FROM pg_language) lang ON p.prolang = lang.oid \n" +
      " WHERE p.proowner != 1)) \n";

    sql += "WHERE udfname = '" + name.getName() + "' \n";
    if (StringUtil.isNonBlank(def.getSchema()))
    {
      sql += "  AND schemaname = '" + def.getSchema() + "' \n";
    }
    sql += "  ORDER BY udfoid, seq \n";

    LogMgr.logMetadataSql(new CallerInfo(){}, "procedure source", sql);
    StringBuilder source = new StringBuilder(500);

    ResultSet rs = null;
    Savepoint sp = null;
    Statement stmt = null;

    try
    {
      if (useSavepoint)
      {
        sp = this.connection.setSavepoint();
      }
      stmt = connection.createStatementForQuery();
      rs = stmt.executeQuery(sql);

      while (rs.next())
      {
        source.append(rs.getString(1));
      }

      connection.releaseSavepoint(sp);

      if (StringUtil.isNonBlank(def.getComment()))
      {
        source.append("\nCOMMENT ON FUNCTION ");
        source.append(name.getFormattedName());
        source.append(" IS '");
        source.append(SqlUtil.escapeQuotes(def.getComment()));
        source.append("'\n;\n");
      }
    }
    catch (SQLException e)
    {
      source = new StringBuilder(ExceptionUtil.getDisplay(e));
      connection.rollback(sp);
      LogMgr.logMetadataError(new CallerInfo(){}, e, "procedure source", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }

    def.setSource(source);
  }

}
