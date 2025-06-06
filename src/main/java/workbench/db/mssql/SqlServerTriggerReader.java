/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
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

package workbench.db.mssql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.DefaultTriggerReader;
import workbench.db.JdbcUtils;
import workbench.db.TriggerListDataStore;
import workbench.db.WbConnection;

import workbench.storage.SortDefinition;

import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class SqlServerTriggerReader
  extends DefaultTriggerReader
{

  public SqlServerTriggerReader(WbConnection conn)
  {
    super(conn);
  }

  @Override
  public TriggerListDataStore getTriggers(String dbName, String schema, String baseTable)
    throws SQLException
  {
    String currentDb = dbConnection.getCurrentCatalog();
    boolean changeCatalog = StringUtil.stringsAreNotEqual(currentDb, dbName) && StringUtil.isNotBlank(dbName);

    TriggerListDataStore result = null;

    try
    {
      if (changeCatalog)
      {
        SqlServerUtil.changeDatabase(dbConnection, dbName);
      }

      result = super.getTriggers(dbName, schema, baseTable);

      if (SqlServerUtil.isSqlServer2005(dbConnection) && StringUtil.isEmpty(baseTable))
      {
        readDDLTriggers(result);
      }
    }
    finally
    {
      if (changeCatalog)
      {
        SqlServerUtil.changeDatabase(dbConnection, currentDb);
      }
    }
    return result;
  }

  private void readDDLTriggers(TriggerListDataStore triggers)
  {
    String sql =
      "select tr.name as trigger_name, \n" +
      "       'ON DATABASE' as trigger_type, \n" +
      "       te.type_desc as trigger_event, " +
      "       db_name() as db_name \n" +
      "from sys.triggers tr with (nolock) \n" +
      "  join sys.trigger_events te with (nolock) on te.object_id = tr.object_id \n" +
      "where tr.is_ms_shipped = 0 \n" +
      "  and tr.parent_class_desc = 'DATABASE' \n" +
      "union all \n" +
      "select tr.name, \n" +
      "       'ON SERVER' as trigger_type, \n" +
      "       te.type_desc as trigger_event, " +
      "       null as db_name \n" +
      "from sys.server_triggers tr with (nolock)  \n" +
      "  join sys.server_trigger_events te with (nolock) on te.object_id = tr.object_id \n" +
      "where is_ms_shipped = 0" +
      "  and tr.parent_class_desc = 'SERVER'";

    LogMgr.logMetadataSql(new CallerInfo(){}, "DDL triggers", sql);

    Statement stmt = null;
    ResultSet rs = null;

    int triggerCount = 0;
    try
    {
      stmt = dbConnection.createStatementForQuery();
      rs = stmt.executeQuery(sql);

      while (rs.next())
      {
        triggerCount ++;
        String name = rs.getString(1);
        String type = rs.getString(2);
        String event = rs.getString(3);
        int row = triggers.addRow();
        triggers.setTriggerName(row, name);
        triggers.setTriggerType(row, type);
        triggers.setRemarks(row, event);
      }
    }
    catch (Exception ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "DDL triggers", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }

    if (triggerCount > 0)
    {
      // sort the datastore again
      SortDefinition def = new SortDefinition();
      def.addSortColumn(0, true);
      triggers.sort(def);
    }
    triggers.resetStatus();

  }

}
