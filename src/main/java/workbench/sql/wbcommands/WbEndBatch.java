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

/**
 * @author  Thomas Kellerer
 */
public class WbEndBatch
  extends SqlCommand
{
  public static final String VERB = "WbEndBatch";

  @Override
  public String getVerb()
  {
    return VERB;
  }

  @Override
  public StatementRunnerResult execute(String aSql)
    throws SQLException, Exception
  {
    StatementRunnerResult result = new StatementRunnerResult();
    result.setSuccess();
    result.addMessageByKey("MsgJdbcBatchProcessingEnded");
    return result;
  }

  @Override
  public boolean isWbCommand()
  {
    return true;
  }

}
