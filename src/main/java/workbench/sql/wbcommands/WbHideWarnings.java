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

import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;
import workbench.sql.lexer.SQLLexer;
import workbench.sql.lexer.SQLLexerFactory;
import workbench.sql.lexer.SQLToken;

import workbench.util.ArgumentParser;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class WbHideWarnings
  extends SqlCommand
{
  public static final String VERB = "WbHideWarnings";

  public WbHideWarnings()
  {
    super();
    this.isUpdatingCommand = false;
    this.cmdLine = new ArgumentParser(false);
    this.cmdLine.addArgument("on");
    this.cmdLine.addArgument("off");
  }

  @Override
  public String getVerb()
  {
    return VERB;
  }

  @Override
  protected boolean isConnectionRequired()
  {
    return false;
  }

  @Override
  public StatementRunnerResult execute(String sql)
    throws SQLException
  {
    StatementRunnerResult result = new StatementRunnerResult();
    result.setSuccess();

    SQLLexer lexer = SQLLexerFactory.createLexer(currentConnection, sql);
    // Skip the SQL Verb
    SQLToken token = lexer.getNextToken(false, false);

    // get the parameter
    token = lexer.getNextToken(false, false);
    String parm = (token != null ? token.getContents() : null);

    if (parm != null)
    {
      if (!parm.equalsIgnoreCase("on") && !parm.equalsIgnoreCase("off") &&
          !parm.equalsIgnoreCase("true") && !parm.equalsIgnoreCase("false"))
      {
        result.setFailure();
        result.addMessageByKey("ErrShowWarnWrongParameter");
        return result;
      }
      else
      {
        this.runner.setHideWarnings(StringUtil.stringToBool(parm));
      }
    }

    if (runner.getHideWarnings())
    {
      result.addMessageByKey("MsgWarningsDisabled");
    }
    else
    {
      result.addMessageByKey("MsgWarningsEnabled");
    }
    return result;
  }

  @Override
  public boolean isWbCommand()
  {
    return true;
  }

}
