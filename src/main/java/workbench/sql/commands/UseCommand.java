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

import workbench.resource.ResourceMgr;

import workbench.db.CatalogChanger;

import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;

import workbench.util.ExceptionUtil;
import workbench.util.StringUtil;

/**
 * MS SQL Server's and MySQL's USE command.
 * <br/>
 * This command will also be "activated if the JDBC driver reports
 * that catalogs are supported
 * <p>
 * This class will notify the connection of this statement that the current database has changed
 * so that the connection display in the main window can be updated.
 *
 * @see workbench.db.CatalogChanger#setCurrentCatalog(workbench.db.WbConnection, java.lang.String)
 * @see workbench.sql.CommandMapper#getCommandToUse(java.lang.String)
 * @see workbench.sql.CommandMapper#addCommand(workbench.sql.SqlCommand)
 *
 * @author Thomas Kellerer
 */
public class UseCommand
  extends SqlCommand
{
  public static final String VERB = "USE";

  @Override
  public StatementRunnerResult execute(String sql)
    throws SQLException
  {
    StatementRunnerResult result = createResult(sql);
    try
    {
      // everything after the USE command is the catalog name
      String catName = getCommandLine(sql);

      // CatalogChanger.setCurrentCatalog() will fire the
      // catalogChanged() event on the connection!
      CatalogChanger changer = new CatalogChanger();
      changer.setCurrentCatalog(currentConnection, catName);

      String newCatalog = currentConnection.getMetadata().getCurrentCatalog();

      String term = StringUtil.capitalize(currentConnection.getMetadata().getCatalogTerm());
      String msg = ResourceMgr.getFormattedString("MsgCatalogChanged", term, newCatalog);

      result.addMessage(msg);
      result.setSuccess();
    }
    catch (Exception e)
    {
      result.addMessageByKey("MsgExecuteError");
      result.addErrorMessage(ExceptionUtil.getAllExceptions(e).toString());
    }
    finally
    {
      this.done();
    }

    return result;
  }

  @Override
  public String getVerb()
  {
    return VERB;
  }

}
