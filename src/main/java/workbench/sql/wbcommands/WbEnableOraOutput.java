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
package workbench.sql.wbcommands;

import java.sql.SQLException;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;

import workbench.db.DbMetadata;
import workbench.db.oracle.DbmsOutput;

import workbench.sql.SqlCommand;
import workbench.sql.StatementRunner;
import workbench.sql.StatementRunnerResult;

import workbench.util.ArgumentParser;
import workbench.util.ArgumentType;
import workbench.util.StringUtil;

/**
 * A class to turn on support for Oracle's <tt>DBMS_OUTPUT</tt> package.
 * <br/>
 * If the support is enabled, messages from dbms_output.put_line() will
 * be shown in the message tab of the GUI.
 *
 * @author Thomas Kellerer
 *
 * @see DbmsOutput
 * @see DbMetadata#enableOutput()
 */
public class WbEnableOraOutput extends SqlCommand
{
  public static final String HIDE_HINT = "hide";
  public static final String VERB = "ENABLEOUT";
  public static final String PARAM_QUIET = "quiet";

  public WbEnableOraOutput()
  {
    super();
    this.cmdLine = new ArgumentParser(false);
    this.cmdLine.addArgument(PARAM_QUIET, ArgumentType.BoolSwitch);
  }


  @Override
  public String getVerb()
  {
    return VERB;
  }

  @Override
  public StatementRunnerResult execute(String sql)
    throws SQLException, Exception
  {
    long limit = -1;

    cmdLine.parse(getCommandLine(sql));

    String value = cmdLine.getNonArguments();

    if (StringUtil.isNotBlank(value))
    {
      try
      {
        limit = Long.parseLong(value);
      }
      catch (NumberFormatException nfe)
      {
        limit = -1;
      }
    }
    currentConnection.getMetadata().enableOutput(limit);
    StatementRunnerResult result = new StatementRunnerResult();
    if (cmdLine.getBoolean(PARAM_QUIET))
    {
      runner.setSessionProperty(StatementRunner.SERVER_MSG_PROP, HIDE_HINT);
      LogMgr.logDebug(new CallerInfo(){}, "Support for dbms_output enabled (limit=" + limit + ")");
    }
    else
    {
      runner.removeSessionProperty(StatementRunner.SERVER_MSG_PROP);
      result.addMessage(ResourceMgr.getString("MsgDbmsOutputEnabled"));
    }
    return result;
  }

  @Override
  public boolean isWbCommand()
  {
    return true;
  }

}
