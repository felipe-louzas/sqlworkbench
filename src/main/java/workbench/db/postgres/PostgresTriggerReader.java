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
package workbench.db.postgres;

import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.DefaultTriggerReader;
import workbench.db.JdbcUtils;
import workbench.db.NoConfigException;
import workbench.db.ProcedureDefinition;
import workbench.db.ProcedureReader;
import workbench.db.RoutineType;
import workbench.db.TableIdentifier;
import workbench.db.TriggerListDataStore;
import workbench.db.WbConnection;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * A TriggerReader for Postgres that retrieves not only the trigger source but also
 * the source code of the associated function.
 *
 * @author Thomas Kellerer
 */
public class PostgresTriggerReader
  extends DefaultTriggerReader
{
  private boolean is93 = false;

  public PostgresTriggerReader(WbConnection conn)
  {
    super(conn);
    is93 = JdbcUtils.hasMinimumServerVersion(conn, "9.3");
  }

  @Override
  public TriggerListDataStore getTriggers(String catalog, String schema)
    throws SQLException
  {
    TriggerListDataStore result = super.getTriggers(catalog, schema);
    if (is93)
    {
      PostgresEventTriggerReader reader = new PostgresEventTriggerReader();
      reader.retrieveEventTriggers(dbConnection, result, null);
    }
    return result;
  }

  @Override
  public String getTriggerSource(String triggerCatalog, String triggerSchema, String triggerName, TableIdentifier triggerTable, String trgComment, boolean includeDependencies)
    throws SQLException
  {
    if (triggerTable == null && is93)
    {
      return getEventTriggerSource(triggerName);
    }
    return super.getTriggerSource(triggerCatalog, triggerSchema, triggerName, triggerTable, trgComment, includeDependencies);
  }

  public String getEventTriggerSource(String triggerName)
    throws SQLException
  {
    StringBuilder result = new StringBuilder(100);
    PreparedStatement stmt = null;
    ResultSet rs = null;
    Savepoint sp = null;


    final String sql =
      "-- SQL Workbench/J \n" +
      "select pr.proname, \n" +
      "       trg.evtevent, \n " +
      "       trg.evttags, \n "+
      "       pg_catalog.pg_get_functiondef(pr.oid) as func_source \n" +
      "FROM pg_catalog.pg_event_trigger trg \n" +
      " JOIN pg_catalog.pg_proc pr on pr.oid = trg.evtfoid \n" +
      " join pg_catalog.pg_namespace nsp on nsp.oid = pr.pronamespace \n" +
      "where trg.evtname = ?";

    LogMgr.logMetadataSql(new CallerInfo(){}, "event trigger source", sql, triggerName);

    try
    {
      String funcName = null;
      String event = null;
      String funcSource = null;
      String[] tags = null;

      sp = dbConnection.setSavepoint();

      stmt = dbConnection.getSqlConnection().prepareStatement(sql);
      stmt.setString(1, triggerName);
      rs = stmt.executeQuery();

      if (rs.next())
      {
        event = rs.getString("evtevent");
        tags = JdbcUtils.getArray(rs, "evttags", String[].class);
        funcSource = rs.getString("func_source");
        funcName = rs.getString("proname");
      }

      result.append("DROP EVENT TRIGGER IF EXISTS ");
      result.append(dbConnection.getMetadata().quoteObjectname(funcName));
      result.append(" CASCADE;\n\n");
      result.append("CREATE EVENT TRIGGER ");
      result.append(dbConnection.getMetadata().quoteObjectname(funcName));
      result.append("\n  ON ");
      result.append(event);
      if (tags != null)
      {
        result.append("\n  WHEN TAG IN (");
        for (int i=0; i < tags.length; i++)
        {
          if (i > 0) result.append(", ");
          result.append('\'');
          result.append(tags[i]);
          result.append('\'');
        }
        result.append(')');
      }
      result.append("\n  EXECUTE PROCEDURE ");
      result.append(funcName);
      result.append("();\n\nCOMMIT;\n");

      result.append("\n---[ ");
      result.append(funcName);
      result.append(" ]---\n");
      result.append(funcSource);
      result.append('\n');

      dbConnection.releaseSavepoint(sp);
    }
    catch (SQLException ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "event trigger source", sql, triggerName);
      dbConnection.rollback(sp);
      throw ex;
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return result.toString();
  }

  @Override
  public CharSequence getDependentSource(String triggerCatalog, String triggerSchema, String triggerName, TableIdentifier triggerTable)
    throws SQLException
  {

    final String sql =
      "-- SQL Workbench/J \n" +
      "SELECT trgsch.nspname as function_schema, proc.proname as function_name \n" +
      "FROM pg_catalog.pg_trigger trg  \n" +
      "  JOIN pg_catalog.pg_class tbl ON tbl.oid = trg.tgrelid  \n" +
      "  JOIN pg_catalog.pg_proc proc ON proc.oid = trg.tgfoid \n" +
      "  JOIN pg_catalog.pg_namespace trgsch ON trgsch.oid = proc.pronamespace \n" +
      "  JOIN pg_catalog.pg_namespace tblsch ON tblsch.oid = tbl.relnamespace \n" +
      "WHERE trgsch.nspname = ? \n" +
      "  AND trg.tgname = ? \n" +
      "  AND tblsch.nspname = ? \n" +
      "  AND tbl.relname = ? ";

    final CallerInfo ci = new CallerInfo(){};
    LogMgr.logMetadataSql(ci, "dependent trigger source", sql, triggerName, triggerTable.getSchema());

    PreparedStatement stmt = null;
    ResultSet rs = null;
    String funcName = null;
    String funcSchema = null;
    StringBuilder result = null;
    Savepoint sp = null;

    try
    {
      sp = dbConnection.setSavepoint();

      stmt = dbConnection.getSqlConnection().prepareStatement(sql);
      stmt.setString(1, triggerSchema);
      stmt.setString(2, triggerName);
      stmt.setString(3, triggerTable.getRawSchema());
      stmt.setString(4, triggerTable.getRawTableName());
      rs = stmt.executeQuery();
      if (rs.next())
      {
        funcSchema = rs.getString(1);
        funcName = rs.getString(2);
      }
      dbConnection.releaseSavepoint(sp);
    }
    catch (SQLException ex)
    {
      dbConnection.rollback(sp);
      LogMgr.logMetadataError(ci, ex, "dependent trigger source", sql, triggerName, triggerTable.getSchema());
      throw ex;
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }

    if (funcName != null && funcSchema != null)
    {
      ProcedureReader reader = new PostgresProcedureReader(dbConnection);
      ProcedureDefinition def = new ProcedureDefinition(null, funcSchema, funcName, RoutineType.function, DatabaseMetaData.procedureNoResult);
      try
      {
        reader.readProcedureSource(def);
        CharSequence src = def.getSource();
        if (src != null)
        {
          result = new StringBuilder(src.length() + 50);
          result.append("\n---[ ");
          result.append(funcName);
          result.append(" ]---\n");
          result.append(src);
          result.append('\n');
        }
      }
      catch (NoConfigException cfg)
      {
        // nothing to do
      }
    }
    return result;
  }

  /**
   * Triggers on views are supported since Version 9.1
   */
  @Override
  public boolean supportsTriggersOnViews()
  {
    if (dbConnection == null) return false;
    return JdbcUtils.hasMinimumServerVersion(dbConnection, "9.1");
  }

  @Override
  protected String getListTriggerSQL(String catalog, String schema, String tableName)
  {
    if (PostgresUtil.isRedshift(dbConnection)) return null;

    String enabled = null;
    if (JdbcUtils.hasMinimumServerVersion(dbConnection, "8.3"))
    {
      enabled =
        "      case\n" +
        "        when trg.tgenabled in ('O', 'A') then 'ENABLED'\n" +
        "        else 'DISABLED'\n" +
        "      end as status";
    }
    else
    {
      enabled =
        "      case\n" +
        "        when trg.tgenabled then 'ENABLED'\n" +
        "        else 'DISABLED'\n" +
        "      end as status";
    }

    String sql =
      "-- SQL Workbench/J \n" +
      "select ns.nspname as trigger_schema,\n" +
      "       trg.tgname as trigger_name,\n" +
      "       CASE trg.tgtype::integer & 66 \n" +
      "         WHEN 2 THEN 'BEFORE'\n" +
      "         WHEN 64 THEN 'INSTEAD OF'\n" +
      "         ELSE 'AFTER'\n" +
      "       end as trigger_type,\n" +
      "       case trg.tgtype::integer & cast(28 as int2)\n" +
      "         when 4 then 'INSERT' \n" +
      "         when 8 then 'DELETE' \n" +
      "         when 12 then 'INSERT, DELETE' \n" +
      "         when 16 then 'UPDATE' \n" +
      "         when 20 then 'INSERT, UPDATE' \n" +
      "         when 28 then 'INSERT, UPDATE, DELETE' \n" +
      "         when 24 then 'UPDATE, DELETE' \n" +
      "       end as trigger_event, \n" +
      "       ns.nspname as trigger_table_schema, \n" +
      "       tbl.relname as trigger_table, \n" +
      "       obj_description(trg.oid) as remarks, \n" +
              enabled + ", \n" +
      "       case trg.tgtype::integer & 1 \n" +
      "         when 1 then 'ROW'::text \n" +
      "         else 'STATEMENT'::text \n" +
      "       end as trigger_level, \n" +
      "       case tbl.relkind \n" +
      "         when 'r' then 'TABLE' \n" +
      "         when 'p' then 'TABLE' \n" +
      "         when 'f' then 'FOREIGN TABLE' \n" +
      "         when 'v' then 'VIEW' \n" +
      "         when 'm' then 'MATERIALIZED VIEW' \n" +
      "       end as trigger_table_type \n" +
      "FROM pg_catalog.pg_trigger trg \n" +
      "  JOIN pg_catalog.pg_class tbl on trg.tgrelid = tbl.oid \n" +
      "  JOIN pg_catalog.pg_namespace ns ON ns.oid = tbl.relnamespace \n";

    if (JdbcUtils.hasMinimumServerVersion(dbConnection, "9.0"))
    {
      sql += "WHERE NOT trg.tgisinternal \n";
    }
    else
    {
      sql +=
        "WHERE trg.tgname not like 'RI_ConstraintTrigger%' \n" +
        "  AND trg.tgname not like 'pg_sync_pg%' \n";
    }

    if (StringUtil.isNotBlank(schema) && !"*".equals(schema))
    {
      sql += "  AND ns.nspname = '" + SqlUtil.escapeQuotes(schema) + "' \n";
    }

    if (StringUtil.isNotBlank(tableName))
    {
      sql += "  AND tbl.relname = '" + SqlUtil.escapeQuotes(tableName) + "' \n";
    }

    if (this.dbConnection.getDbSettings().returnAccessibleTablesOnly())
    {
      sql += "  AND pg_catalog.has_table_privilege(tbl.oid, 'select') \n";
    }

    return sql;
  }
}
