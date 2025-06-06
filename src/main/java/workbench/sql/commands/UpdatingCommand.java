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
package workbench.sql.commands;

import java.io.FileNotFoundException;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;

import workbench.util.LobFileStatement;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * Handles DML statements (UPDATE, DELETE, INSERT, TRUNCATE)
 *
 * @author Thomas Kellerer
 */
public class UpdatingCommand
  extends SqlCommand
{
  public static SqlCommand getUpdateCommand()
  {
    return new UpdatingCommand("UPDATE");
  }

  public static SqlCommand getDeleteCommand()
  {
    return new UpdatingCommand("DELETE");
  }

  public static SqlCommand getInsertCommand()
  {
    return new UpdatingCommand("INSERT");
  }

  public static SqlCommand getTruncateCommand()
  {
    return new UpdatingCommand("TRUNCATE");
  }

  public static SqlCommand getMergeCommand()
  {
    return new UpdatingCommand("MERGE");
  }

  private final String verb;
  private final boolean checkLobParameter;

  private UpdatingCommand(String sqlVerb)
  {
    super();
    this.verb = sqlVerb;
    this.isUpdatingCommand = true;
    checkLobParameter = sqlVerb.equals("UPDATE") || sqlVerb.equals("INSERT");
  }

  @Override
  public StatementRunnerResult execute(String sql)
    throws SQLException
  {
    StatementRunnerResult result = createResult(sql);
    LobFileStatement lob = null;

    result.ignoreUpdateCounts(currentConnection.getDbSettings().verbsWithoutUpdateCount().contains(verb));

    try
    {
      boolean isPrepared = false;

      if (checkLobParameter)
      {
        try
        {
          lob = new LobFileStatement(sql, getBaseDir());
        }
        catch (FileNotFoundException e)
        {
          result.addErrorMessage(e.getMessage());
          return result;
        }
      }

      if (runner.useSavepointForDML())
      {
        runner.setSavepoint();
      }

      sql = getSqlToExecute(sql);

      if (lob != null && lob.containsParameter())
      {
        isPrepared = true;
        this.currentStatement = lob.prepareStatement(currentConnection);
      }
      else if (Settings.getInstance().getCheckPreparedStatements() && currentConnection.getPreparedStatementPool().isRegistered(sql))
      {
        this.currentStatement = currentConnection.getPreparedStatementPool().prepareStatement(sql);
        isPrepared = true;
      }
      else
      {
        this.currentStatement = currentConnection.createStatement();
      }

      boolean hasResult = false;
      boolean supportsResultSets = currentConnection.getDbSettings().supportsResultSetsWithDML();
      int updateCount = -1;

      if (isPrepared)
      {
        hasResult = ((PreparedStatement)this.currentStatement).execute();
      }
      else if (supportsResultSets)
      {
        hasResult = this.currentStatement.execute(sql);
      }
      else
      {
        updateCount = currentStatement.executeUpdate(sql);
      }

      String table = getAffectedTable(sql);
      if (StringUtil.isEmpty(table))
      {
        appendSuccessMessage(result);
      }
      else if (currentConnection.getDbSettings().showSuccessMessageForVerb(verb))
      {
        String msg = ResourceMgr.getFormattedString("MsgDMLSuccess", getMessageVerb(), table);
        result.addMessage(msg);
      }
      result.setSuccess();

      // adding the result/update count should be done after adding the success message
      // to the StatementRunnerResult object
      if (supportsResultSets || isPrepared)
      {
        processResults(result, hasResult);
      }
      else if (updateCount > -1)
      {
        result.addUpdateCountMsg(updateCount);
      }

      runner.releaseSavepoint();
    }
    catch (Exception e)
    {
      runner.rollbackSavepoint();
      String table = getAffectedTable(sql);
      if (StringUtil.isNotEmpty(table))
      {
        String msg = ResourceMgr.getFormattedString("MsgDMLNoSuccess", getMessageVerb(), table);
        result.addMessage(msg);
      }
      addErrorInfo(result, sql, e);
      LogMgr.logUserSqlError(new CallerInfo(){}, sql, e);
    }
    finally
    {
      if (lob != null) lob.done();
      this.done();
    }
    return result;
  }

  private String getMessageVerb()
  {
    String result = null;
    if (this.verb.equals("DELETE"))
    {
      result = "DELETE FROM";
    }
    else if (this.verb.equals("INSERT"))
    {
      result = "INSERT INTO";
    }
    else
    {
      result = verb;
    }
    return result;
  }

  private String getAffectedTable(String sql)
  {
    String tablename = null;
    if (this.verb.equals("UPDATE"))
    {
      tablename = SqlUtil.getUpdateTable(sql, SqlUtil.getCatalogSeparator(currentConnection), currentConnection);
    }
    else if (this.verb.equals("DELETE"))
    {
      tablename = SqlUtil.getDeleteTable(sql, SqlUtil.getCatalogSeparator(currentConnection), currentConnection);
    }
    else if (this.verb.equals("INSERT"))
    {
      tablename = SqlUtil.getInsertTable(sql, SqlUtil.getCatalogSeparator(currentConnection), currentConnection);
    }
    else if (this.verb.equals("TRUNCATE"))
    {
      tablename = SqlUtil.getTruncateTable(sql, SqlUtil.getCatalogSeparator(currentConnection), currentConnection);
    }
    else if (this.verb.equals("MERGE"))
    {
      tablename = SqlUtil.getMergeTable(sql, currentConnection);
    }
    return tablename;
  }

  @Override
  public String getVerb()
  {
    return verb;
  }

}
