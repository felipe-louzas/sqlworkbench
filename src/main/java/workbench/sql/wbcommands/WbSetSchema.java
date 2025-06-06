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

import workbench.util.ExceptionUtil;
import workbench.util.StringUtil;

/**
 * A workbench SQL command to change the current schema through JDBC.
 *
 * @author Thomas Kellerer
 */
public class WbSetSchema
  extends SqlCommand
{
  public static final String VERB = "WbSetSchema";

  @Override
  public StatementRunnerResult execute(String sql)
    throws SQLException
  {
    StatementRunnerResult result = createResult(sql);
    try
    {
      // everything after the WbSetSchema command is the schema name
      String newSchema = getCommandLine(sql);
      String oldSchema = currentConnection.getCurrentSchema();

      currentConnection.getMetadata().clearCachedSchemaInformation();
      currentConnection.getSqlConnection().setSchema(newSchema);
      appendWarnings(result, true);

      String effectiveSchema = newSchema;
      if (currentConnection.getDbSettings().validateSetSchema())
      {
        effectiveSchema = currentConnection.getCurrentSchema();
      }

      if (StringUtil.equalStringIgnoreCase(oldSchema, effectiveSchema))
      {
        result.addMessageByKey("MsgSchemaNotChanged");
        result.setWarning();
      }
      else
      {
        notifySchemaChange(oldSchema, newSchema);
        result.addMessageByKey("MsgSchemaChanged", newSchema);
        result.setSuccess();
      }
    }
    catch (Exception e)
    {
      if (currentConnection.getDbSettings().treatSchemaChangeErrorAsWarning())
      {
        result.addWarning(ExceptionUtil.getAllExceptions(e).toString());
      }
      else
      {
        result.addMessageByKey("MsgExecuteError");
        result.addErrorMessage(ExceptionUtil.getAllExceptions(e).toString());
      }
    }
    finally
    {
      this.done();
    }

    return result;
  }

  private void notifySchemaChange(String oldSchema, String newSchema)
  {
    if (StringUtil.equalStringIgnoreCase(oldSchema, newSchema)) return;

    // schemaChanged will trigger an update of the ConnectionInfo
    // but that only retrieves the current schema if the connection isn't busy,
    // so we need to reset the flag before sending the notification
    boolean busy = currentConnection.isBusy();
    try
    {
      currentConnection.setBusy(false);
      currentConnection.schemaChanged(oldSchema, newSchema);
    }
    finally
    {
      currentConnection.setBusy(busy);
    }
  }

  @Override
  public boolean isWbCommand()
  {
    return true;
  }

  @Override
  public String getVerb()
  {
    return VERB;
  }

}
