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
package workbench.sql.wbcommands.console;

import java.sql.SQLException;
import java.util.List;

import workbench.AppArguments;

import workbench.db.ConnectionMgr;
import workbench.db.DbDriver;

import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;

import workbench.util.ArgumentParser;
import workbench.util.StringUtil;
import workbench.util.WbFile;

/**
 *
 * @author Thomas Kellerer
 */
public class WbDefineDriver
  extends SqlCommand
{
  public static final String VERB = "WbDefineDriver";
  public static final String ARG_DRV_NAME = "name";

  public WbDefineDriver()
  {
    super();
    cmdLine = new ArgumentParser();
    cmdLine.addArgument(ARG_DRV_NAME);
    cmdLine.addArgument(AppArguments.ARG_CONN_DRIVER_CLASS);
    cmdLine.addArgument(AppArguments.ARG_CONN_JAR);
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
    throws SQLException, Exception
  {
    StatementRunnerResult result = new StatementRunnerResult();

    String arguments = getCommandLine(sql);
    cmdLine.parse(arguments);
    if (!cmdLine.hasArguments())
    {
      result.addMessageByKey("ErrDefDrv");
      result.setFailure();
      return result;
    }

    String name = cmdLine.getValue(ARG_DRV_NAME);
    String drvClass = cmdLine.getValue(AppArguments.ARG_CONN_DRIVER_CLASS);
    String jarFile =  cmdLine.getValue(AppArguments.ARG_CONN_JAR);

    if (StringUtil.isBlank(name))
    {
      result.addMessageByKey("ErrDefDrvNoName");
      result.addMessageByKey("ErrDefDrv");
      result.setFailure();
      return result;
    }

    name = name.trim();

    DbDriver drv = findDriver(name);

    boolean driverExists = drv != null;

    if (StringUtil.isBlank(drvClass) && drv == null)
    {
      result.addMessageByKey("ErrDefDrvNoClass");
      result.addMessageByKey("ErrDefDrv");
      result.setFailure();
      return result;
    }


    if (drv == null)
    {
      drv = new DbDriver();
      drv.setName(name);
      ConnectionMgr.getInstance().getDrivers().add(drv);
    }

    if (StringUtil.isNotBlank(jarFile))
    {
      drv.setLibrary(jarFile);
    }

    if (StringUtil.isNotBlank(drvClass))
    {
      drv.setDriverClass(drvClass);
    }

    List<String> libs = drv.getLibraryList();
    for (String file : libs)
    {
      WbFile f = new WbFile(file);
      if (!f.exists())
      {
        result.addMessageByKey("ErrFileNotFound", f.getFullPath());
        result.setFailure();
      }
    }

    if (!result.isSuccess())
    {
      return result;
    }

    ConnectionMgr.getInstance().saveDrivers();

    if (driverExists)
    {
      result.addMessageByKey("MsgDriverUpdated", name);
    }
    else
    {
      result.addMessageByKey("MsgDriverAdded", name);
    }
    result.setSuccess();
    return result;
  }

  private DbDriver findDriver(String name)
  {
    List<DbDriver> drivers = ConnectionMgr.getInstance().getDrivers();
    for (DbDriver drv : drivers)
    {
      if (drv.getName().equalsIgnoreCase(name))
      {
        return drv;
      }
    }
    return null;
  }

  @Override
  public boolean isWbCommand()
  {
    return true;
  }

}
