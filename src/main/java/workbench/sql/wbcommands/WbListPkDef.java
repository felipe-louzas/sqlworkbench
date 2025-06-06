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

import workbench.storage.PkMapping;

import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;

/**
 *
 * @author Thomas Kellerer
 */
public class WbListPkDef
  extends SqlCommand
{

  public static final String VERB = "WbListPkDef";

  public WbListPkDef()
  {
    super();
    this.isUpdatingCommand = false;
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
  public StatementRunnerResult execute(String aSql)
    throws SQLException
  {
    StatementRunnerResult result = new StatementRunnerResult();

    result.setSuccess();

    String info = PkMapping.getInstance().getMappingAsText();
    if (info != null)
    {
      result.addMessageByKey("MsgPkDefinitions");
      result.addMessageNewLine();
      result.addMessage(info);
      result.addMessageByKey("MsgPkDefinitionsEnd");
    }
    else
    {
      result.addMessageByKey("MsgPkDefinitionsEmpty");
    }
    return result;
  }

  @Override
  public boolean isWbCommand()
  {
    return true;
  }
}
