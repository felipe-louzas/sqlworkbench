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
package workbench.db;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import workbench.interfaces.ObjectDropper;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.sqltemplates.TemplateHandler;

import workbench.storage.RowActionMonitor;

import workbench.util.StringUtil;

/**
 * An implementation of the ObjectDropper interface to drop columns from a table.
 *
 * The necessary SQL statement is retrieved from the <tt>workbench.settings</tt>
 *
 * @author Thomas Kellerer
 * @see workbench.db.DbSettings#getDropMultipleColumnSql()
 * @see workbench.db.DbSettings#getDropSingleColumnSql()
 *
 */
public class ColumnDropper
  implements ObjectDropper
{
  private WbConnection conn;
  private List<ColumnIdentifier> columns;
  private TableIdentifier table;
  private boolean cancelDrop = false;
  private Statement currentStatement;

  public ColumnDropper()
  {
  }

  public ColumnDropper(WbConnection db, TableIdentifier tbl, List<ColumnIdentifier> toDrop)
  {
    this.conn = db;
    this.columns = toDrop;
    this.table = tbl;
  }

  @Override
  public void setRowActionMonitor(RowActionMonitor mon)
  {
  }

  @Override
  public boolean supportsCascade()
  {
    return false;
  }

  @Override
  public void setCascade(boolean flag)
  {
  }

  @Override
  public boolean supportsFKSorting()
  {
    return false;
  }

  @Override
  public void cancel()
    throws SQLException
  {
    cancelDrop = true;
    if (this.currentStatement != null)
    {
      this.currentStatement.cancel();
    }
  }

  @Override
  public WbConnection getConnection()
  {
    return this.conn;
  }

  @Override
  public void setConnection(WbConnection con)
  {
    this.conn = con;
  }

  @Override
  public void setObjectTable(TableIdentifier tbl)
  {
    this.table = tbl;
  }

  @Override
  public List<? extends DbObject> getObjects()
  {
    return columns;
  }

  @Override
  public void setObjects(List<? extends DbObject> toDrop)
  {
    this.columns = new ArrayList<>();
    if (toDrop == null) return;
    for (DbObject dbo : toDrop)
    {
      if (dbo instanceof ColumnIdentifier)
      {
        columns.add((ColumnIdentifier)dbo);
      }
    }
  }

  @Override
  public CharSequence getScript()
  {
    List<String> statements = getSql(table, columns, conn);
    StringBuffer result = new StringBuffer(statements.size() * 40);
    boolean needCommit = (conn != null ? conn.shouldCommitDDL() : false);

    for (String sql : statements)
    {
      result.append(sql);
      result.append(";\n");
    }
    if (needCommit) result.append("COMMIT;\n");
    return result;
  }

  @Override
  public void dropObjects()
    throws SQLException
  {
    if (this.conn == null) return;
    if (this.table == null)
    {
      return;
    }

    if (this.columns == null || this.columns.isEmpty())
    {
      LogMgr.logWarning(new CallerInfo(){}, "No columns to drop!");
      return;
    }

    List<String> statements = getSql(table, columns, conn);
    if (statements.isEmpty())
    {
      LogMgr.logWarning(new CallerInfo(){}, "No statements generated!");
      return;
    }

    try
    {
      this.currentStatement = this.conn.createStatement();

      for (String sql : statements)
      {
        if (cancelDrop) break;
        LogMgr.logDebug(new CallerInfo(){}, "Statement to drop column(s): " + sql);
        this.currentStatement.executeUpdate(sql);
      }

      if (conn.shouldCommitDDL())
      {
        if (cancelDrop)
        {
          conn.rollback();
        }
        else
        {
          conn.commit();
        }
      }
    }
    catch (SQLException e)
    {
      if (conn.shouldCommitDDL())
      {
        conn.rollback();
      }
      throw e;
    }
    finally
    {
      JdbcUtils.closeStatement(currentStatement);
      currentStatement = null;
    }
  }

  /**
   * Not implemented.
   *
   * @param toDrop
   * @param cascade
   *
   * @return always null
   */
  @Override
  public CharSequence getDropForObject(DbObject toDrop, boolean cascade)
  {
    return null;
  }

  /**
   * Not implemented.
   *
   * @param toDrop
   * @return always null
   */
  @Override
  public CharSequence getDropForObject(DbObject toDrop)
  {
    return null;
  }

  public static List<String> getSql(TableIdentifier table, List<ColumnIdentifier> columns, WbConnection conn)
  {
    String multiSql = conn.getDbSettings().getDropMultipleColumnSql();
    String singleSql = conn.getDbSettings().getDropSingleColumnSql();
    List<String> result = new ArrayList<>(columns.size());

    if (columns.size() == 1 || StringUtil.isEmpty(multiSql))
    {
      singleSql = TemplateHandler.replaceTablePlaceholder(singleSql, table, conn);
      for (ColumnIdentifier col : columns)
      {
        result.add(StringUtil.replace(singleSql, MetaDataSqlManager.COLUMN_NAME_PLACEHOLDER, col.getColumnName(conn)));
      }
    }
    else
    {
      multiSql = TemplateHandler.replaceTablePlaceholder(multiSql, table, conn);

      StringBuilder cols = new StringBuilder(columns.size());
      int nr = 0;
      for (ColumnIdentifier col : columns)
      {
        if (nr > 0) cols.append(", ");
        cols.append(col.getColumnName(conn));
        nr ++;
      }
      result.add(StringUtil.replace(multiSql, MetaDataSqlManager.COLUMN_LIST_PLACEHOLDER, cols.toString()));
    }

    return result;
  }

  @Override
  public boolean supportsObject(DbObject object)
  {
    return object instanceof ColumnIdentifier;
  }

}
