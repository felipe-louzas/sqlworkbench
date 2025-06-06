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

import java.io.File;
import java.sql.SQLException;

import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.storage.PkMapping;

import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;

import workbench.util.ArgumentParser;
import workbench.util.ArgumentType;
import workbench.util.StringUtil;

/**
 * A SQL command to load the PK mapping from a (non-default) file.
 * @author Thomas Kellerer
 */
public class WbLoadPkMapping
  extends SqlCommand
{
  public final static String VERB = "WbLoadPKMap";

  public WbLoadPkMapping()
  {
    super();
    this.isUpdatingCommand = false;
    cmdLine = new ArgumentParser();
    cmdLine.addArgument(CommonArgs.ARG_FILE, ArgumentType.Filename);
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
  public StatementRunnerResult execute(String sqlCommand)
    throws SQLException
  {
    StatementRunnerResult result = new StatementRunnerResult();
    String sql = getCommandLine(sqlCommand);
    cmdLine.parse(sql);
    if (displayHelp(result))
    {
      return result;
    }

    File mappingFile = evaluateFileArgument(cmdLine.getValue(CommonArgs.ARG_FILE), true);
    if (mappingFile == null)
    {
      mappingFile = Settings.getInstance().getPKMappingFile();
    }

    if (mappingFile == null)
    {
      result.setFailure();
      result.addMessageByKey("ErrPkDefNoFile");
      return result;
    }

    PkMapping.getInstance().loadMapping(mappingFile);
    String msg = ResourceMgr.getString("MsgPkMappingLoaded");

    msg = StringUtil.replace(msg, "%filename%", mappingFile.getAbsolutePath());
    result.addMessage(msg);
    result.addMessageNewLine();

    String info = PkMapping.getInstance().getMappingAsText();
    if (info != null)
    {
      result.addMessage(info);
      result.addMessageByKey("MsgPkDefinitionsEnd");
    }

    result.setSuccess();
    return result;
  }

  @Override
  public boolean isWbCommand()
  {
    return true;
  }

}
