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
package workbench.db.importer;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;

import workbench.db.DbObjectFinder;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.storage.RowActionMonitor;

import workbench.util.MessageBuffer;
import workbench.db.JdbcUtils;

/**
 * @author Thomas Kellerer
 */
class ImportTableDeleter
{
  private RowActionMonitor rowMonitor;
  private boolean checkDependencies = false;
  private WbConnection dbConn;
  private boolean cancel = false;
  private MessageBuffer messages = new MessageBuffer();

  ImportTableDeleter(WbConnection conn, boolean checkOrder)
  {
    dbConn = conn;
    checkDependencies = checkOrder;
  }

  private List<TableIdentifier> sortTables(List<TableIdentifier> tables)
  {
    if (this.rowMonitor != null)
    {
      this.rowMonitor.setMonitorType(RowActionMonitor.MONITOR_PLAIN);
      this.rowMonitor.setCurrentObject(ResourceMgr.getString("MsgFkDeps"), -1, -1);
    }

    TableDependencySorter sorter = new TableDependencySorter(dbConn);
    List<TableIdentifier> sorted = sorter.sortForDelete(tables, false);

    if (this.rowMonitor != null)
    {
      this.rowMonitor.jobFinished();
    }
    return sorted;
  }

  MessageBuffer getMessages()
  {
    return messages;
  }

  void cancel()
  {
    this.cancel = true;
  }

  void setRowMonitor(RowActionMonitor monitor)
  {
    this.rowMonitor = monitor;
  }

  /**
   * Delete all rows from the passed tables.
   * For each table a DELETE FROM table; will be sent to the database.
   * If commitEach is true, a commit() will be sent after each DELETE statement,
   * otherwise only one commit at the end.
   *
   * @param tables the list of tables to be deleted
   * @param commitEach
   * @throws java.sql.SQLException
   */
  void deleteRows(List<TableIdentifier> tables, boolean commitEach)
    throws SQLException
  {
    if (checkDependencies)
    {
      tables = sortTables(tables);
    }

    boolean needsCommit = !this.dbConn.getAutoCommit();
    Statement statement = null;
    try
    {
      if (this.rowMonitor != null)
      {
        this.rowMonitor.setMonitorType(RowActionMonitor.MONITOR_DELETE);
      }

      statement = dbConn.createStatement();
      int table = 1;
      for (TableIdentifier sourceTable : tables)
      {
        if (this.cancel)
        {
          if (needsCommit)
          {
            dbConn.rollbackSilently(new CallerInfo(){});
          }
          break;
        }

        DbObjectFinder finder = new DbObjectFinder(dbConn);
        TableIdentifier targetTable = finder.findTable(new TableIdentifier(sourceTable.getTableName()), false);
        if (this.rowMonitor != null)
        {
          this.rowMonitor.setCurrentObject(targetTable.getTableName(), table, tables.size());
        }
        String sql = "DELETE FROM " + targetTable.getTableExpression(dbConn);
        LogMgr.logInfo(new CallerInfo(){}, "Running: " + sql);
        long rows = statement.executeUpdate(sql);
        this.messages.append(rows + " " + ResourceMgr.getString("MsgImporterRowsDeleted") + " " + targetTable.getTableExpression(this.dbConn) + "\n");
        if (needsCommit && commitEach)
        {
          dbConn.commit();
        }
        table ++;
      }
      if (needsCommit && !commitEach)
      {
        dbConn.commit();
      }
    }
    catch (SQLException e)
    {
      if (needsCommit)
      {
        dbConn.rollbackSilently(new CallerInfo(){});
      }
      throw e;
    }
    finally
    {
      this.dbConn.setBusy(false);
      JdbcUtils.closeStatement(statement);
      if (this.rowMonitor != null)
      {
        this.rowMonitor.jobFinished();
      }
    }
  }

}
