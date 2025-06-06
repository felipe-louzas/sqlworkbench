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

package workbench.db.firebird;

import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.util.Set;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ConnectionMgr;
import workbench.db.JdbcUtils;
import workbench.db.WbConnection;

import workbench.sql.StatementHook;
import workbench.sql.StatementRunner;
import workbench.sql.StatementRunnerResult;

import workbench.util.CollectionUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class FirebirdStatementHook
  implements StatementHook
{
  public static final String SESS_ATTR_SHOWPLAN = "fb_showplan";
  public static final String SESS_ATTR_PLAN_ONLY = "fb_planonly";

  private final Object lock = new Object();

  private boolean planOnly;
  private boolean showPlan;
  private Method getPlan;
  private boolean useDefaultClassloader;

  private Set<String> explainable = CollectionUtil.caseInsensitiveSet("with", "select", "update", "delete", "insert");
  private String toExplain;

  public FirebirdStatementHook(WbConnection connection)
  {
    initialize(connection);
  }

  @Override
  public String preExec(StatementRunner runner, String sql)
  {
    showPlan = runner.getBoolSessionAttribute(SESS_ATTR_SHOWPLAN);
    planOnly = runner.getBoolSessionAttribute(SESS_ATTR_PLAN_ONLY);
    if (planOnly)
    {
      // do not execute the statement
      // this is important because a DML statement can also be explained
      // and isql does not execute the statement at all when using set planonly on
      toExplain = sql;
      return null;
    }
    return sql;
  }

  @Override
  public boolean isPending()
  {
    return (showPlan || planOnly);
  }

  @Override
  public void postExec(StatementRunner runner, String sql, StatementRunnerResult result)
  {
    if (showPlan || planOnly)
    {
      String plan = getExecutionPlan(runner.getConnection(), sql == null ? toExplain : sql);
      if (plan != null)
      {
        result.addMessage("Execution plan:");
        result.addMessage("---------------");
        result.addMessage(plan.trim());
        result.addMessage("\n-- end of execution plan ---");
      }
    }
  }

  private String getExecutionPlan(WbConnection connection, String sql)
  {
    String verb = connection.getParsingUtil().getSqlVerb(sql);
    if (!explainable.contains(verb)) return null;

    if (getPlan == null) return null;

    String executionPlan = null;
    PreparedStatement pstmt = null;
    try
    {
      pstmt = connection.getSqlConnection().prepareStatement(sql);
      executionPlan = (String)getPlan.invoke(pstmt, (Object[])null);
    }
    catch (Exception ex)
    {
      executionPlan = null;
      LogMgr.logError(new CallerInfo(){}, "Could not retrieve execution plan", ex);
    }
    finally
    {
      JdbcUtils.closeStatement(pstmt);
    }
    return executionPlan;
  }

  @Override
  public boolean displayResults()
  {
    return !planOnly;
  }

  @Override
  public boolean fetchResults()
  {
    return !planOnly;
  }

  @Override
  public void close(WbConnection conn)
  {
  }

  private void initialize(WbConnection connection)
  {
    synchronized (lock)
    {
      try
      {
        Class pstmtClass = null;
        if (useDefaultClassloader)
        {
          pstmtClass = Class.forName("org.firebirdsql.jdbc.FirebirdPreparedStatement");
        }
        else
        {
          pstmtClass = ConnectionMgr.getInstance().loadClassFromDriverLib(connection.getProfile(), "org.firebirdsql.jdbc.FirebirdPreparedStatement");
        }

        getPlan = pstmtClass.getMethod("getExecutionPlan", (Class[])null);
      }
      catch (Throwable t)
      {
        LogMgr.logError(new CallerInfo(){}, "Could not obtain getExecutionPlan method", t);
        getPlan = null;
      }
    }
  }

  /**
   * Instruct this instance to use the default (system) classloader
   * instead of the the ConnectionMgr.loadClassFromDriverLib().
   *
   * During unit testing the classloader in the ConnectionMgr is not initialized
   * because all drivers are alread on the classpath. Therefor this switch is needed
   *
   * @param flag if true, load the FirebirdPreparedStatement class from the system classpath, otherwise use the ConnectionMgr.
   */
  public void setUseDefaultClassloader(boolean flag)
  {
    useDefaultClassloader = flag;
  }
}


