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

import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;

import workbench.util.ArgumentParser;
import workbench.util.ArgumentType;

import static workbench.sql.wbcommands.WbEnableOraOutput.*;

/**
 *
 * @author  Thomas Kellerer
 */
public class WbDisableOraOutput extends SqlCommand
{
  public static final String VERB = "DISABLEOUT";

  public WbDisableOraOutput()
  {
    super();
    this.cmdLine = new ArgumentParser(false);
    this.cmdLine.addArgument(WbEnableOraOutput.PARAM_QUIET, ArgumentType.BoolSwitch);
  }

  @Override
  public String getVerb()
  {
    return VERB;
  }

  @Override
  public StatementRunnerResult execute(String sql)
    throws SQLException
  {
    StatementRunnerResult result = new StatementRunnerResult();
    currentConnection.getMetadata().disableOutput();
    cmdLine.parse(getCommandLine(sql));
    if (cmdLine.getBoolean(PARAM_QUIET))
    {
      LogMgr.logDebug(new CallerInfo(){}, "Support for dbms_output disabled");
    }
    else
    {
      result.addMessageByKey("MsgDbmsOutputDisabled");
    }
    return result;
  }

  @Override
  public boolean isWbCommand()
  {
    return true;
  }

}
