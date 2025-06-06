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

import java.sql.SQLException;

import workbench.RunMode;
import workbench.resource.Settings;

import workbench.db.WbConnection;

import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;

/**
 * This class simply ignores the command and does not send it to the DBMS.
 *
 * Thus scripts e.g. intended for SQL*Plus (containing WHENEVER or EXIT)
 * can be executed from within the workbench.
 * The commands to be ignored can be configured in workbench.settings
 *
 * @author  Thomas Kellerer
 */
public class IgnoredCommand
  extends SqlCommand
{
  private final String verb;
  private boolean silent;

  public IgnoredCommand(String aVerb)
  {
    super();
    this.verb = aVerb.toUpperCase();
  }

  @Override
  public StatementRunnerResult execute(String aSql)
    throws SQLException
  {
    StatementRunnerResult result = new StatementRunnerResult(messageLogger);
    if (Settings.getInstance().getShowIgnoredWarning() && !silent)
    {
      result.addMessageByKey("MsgCommandIgnored", this.verb);
    }
    result.setSuccess();
    this.done();
    return result;
  }

  public void setSilent(boolean flag)
  {
    this.silent = flag;
  }

  @Override
  public String getVerb()
  {
    return verb;
  }

  @Override
  public boolean isModificationAllowed(WbConnection con, String sql)
  {
    return true;
  }

  @Override
  public boolean isUpdatingCommand(WbConnection con, String sql)
  {
    return false;
  }

  @Override
  public boolean isUpdatingCommand()
  {
    return false;
  }

  @Override
  protected boolean isConnectionRequired()
  {
    return false;
  }

  @Override
  public boolean isModeSupported(RunMode mode)
  {
    return true;
  }

}
