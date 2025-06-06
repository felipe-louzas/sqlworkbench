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

import java.awt.Desktop;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;

import workbench.util.ArgumentParser;
import workbench.util.ArgumentType;
import workbench.util.CollectionUtil;
import workbench.util.ExceptionUtil;
import workbench.util.FileUtil;
import workbench.util.PlatformHelper;
import workbench.util.StringUtil;
import workbench.util.WbFile;

/**
 * A workbench command to call an operating system program (or command)
 *
 * @author Thomas Kellerer
 */
public class WbSysExec
  extends SqlCommand
{
  public static final String VERB = "WbSysExec";
  public static final String ARG_PROGRAM = "program";
  public static final String ARG_PRG_ARG = "argument";
  public static final String ARG_WORKING_DIR = "dir";
  public static final String ARG_DOCUMENT = "document";
  public static final String ARG_ENCODING = "encoding";
  public static final String ARG_ENV = "env";

  private Process task;

  public WbSysExec()
  {
    super();
    cmdLine = new ArgumentParser();
    cmdLine.addArgument(ARG_PROGRAM);
    cmdLine.addArgument(ARG_WORKING_DIR);
    cmdLine.addArgument(ARG_DOCUMENT);
    cmdLine.addArgument(ARG_PRG_ARG, ArgumentType.Repeatable);
    cmdLine.addArgument(ARG_ENCODING);
    cmdLine.addArgument(ARG_ENV, ArgumentType.Repeatable);
    ConditionCheck.addParameters(cmdLine);
  }

  @Override
  public void cancel()
    throws SQLException
  {
    this.isCancelled = true;
    if (this.task != null)
    {
      task.destroy();
    }
  }

  @Override
  public StatementRunnerResult execute(String sql)
    throws SQLException, Exception
  {
    StatementRunnerResult result = createResult(sql);
    String command = getCommandLine(sql);

    if (StringUtil.isBlank(command))
    {
      result.setFailure();
      result.addMessageByKey("ErrExecNoParm");
      return result;
    }

    cmdLine.parse(command);

    if (displayHelp(result))
    {
      return result;
    }

    if (!checkConditions(result))
    {
      return result;
    }

    BufferedReader stdIn = null;
    BufferedReader stdError = null;
    try
    {
      String prg = cmdLine.getValue(ARG_PROGRAM);
      String doc = cmdLine.getValue(ARG_DOCUMENT);

      if (StringUtil.isNotBlank(doc) && Desktop.isDesktopSupported())
      {
        try
        {
          Desktop.getDesktop().open(new File(doc));
          result.setSuccess();
        }
        catch (IOException io)
        {
          result.setFailure();
          result.addMessage(io.getLocalizedMessage());
        }
        return result;
      }

      File cwd = new File(getBaseDir());
      List<String> args = new ArrayList<>();

      List<String> params = cmdLine.getList(ARG_PRG_ARG);

      if (StringUtil.isNotBlank(prg))
      {
        if (prg.startsWith("."))
        {
          WbFile f = evaluateFileArgument(prg);
          prg = f.getFullPath();
        }
        args.add(prg);
        // ProcessBuilder requires each parameter "element" to be passed separately
        // e.g. "-U someuser" has to be passed as "-U" and "someuser" individually
        // in the list of arguments passed to the constructor of ProcessBuilder
        for (String element : params)
        {
          List<String> paramParts = StringUtil.stringToList(element, " ", true, true, false, true);
          args.addAll(paramParts);
        }
      }
      else
      {
        List<String> cmd = StringUtil.stringToList(command, " ", true, true, false, true);
        args.addAll(cmd);
      }

      if (useShell(args.get(0)))
      {
        args = getShell(args);
      }

      // it seems that Windows actually needs IBM437...
      String encoding = cmdLine.getValue(ARG_ENCODING, System.getProperty("file.encoding"));
      LogMgr.logDebug(new CallerInfo(){}, "Using encoding: " + encoding);

      ProcessBuilder pb = new ProcessBuilder(args);
      Map<String, String> envArgs = cmdLine.getMapValue(ARG_ENV);
      if (CollectionUtil.isNonEmpty(envArgs))
      {
        Map<String, String> pbEnv = pb.environment();
        pbEnv.putAll(envArgs);
      }
      String dir = cmdLine.getValue(ARG_WORKING_DIR);
      if (StringUtil.isNotBlank(dir))
      {
        File f = evaluateFileArgument(dir);
        pb.directory(f);
      }
      else
      {
        pb.directory(cwd);
      }

      LogMgr.logDebug(new CallerInfo(){}, "Running program: " + pb.command());
      this.task = pb.start();

      stdIn = new BufferedReader(new InputStreamReader(task.getInputStream(), encoding));
      stdError = new BufferedReader(new InputStreamReader(task.getErrorStream(), encoding));

      String out = stdIn.readLine();
      while (out != null)
      {
        result.addMessage(out);
        out = stdIn.readLine();
      }

      String err = stdError.readLine();
      if (err != null)
      {
        result.setFailure();
      }

      while (err != null)
      {
        result.addErrorMessage(err);
        err = stdError.readLine();
      }
      task.waitFor();

      int exitValue = task.exitValue();
      if (exitValue != 0)
      {
        result.addMessage("Exit code: " + exitValue);
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error calling external program", e);
      result.addMessage(ExceptionUtil.getDisplay(e));
      result.setFailure();
    }
    finally
    {
      FileUtil.closeQuietely(stdIn, stdError);
      this.task = null;
    }
    return result;
  }

  private List<String> getShell(List<String> command)
  {
    if (CollectionUtil.isEmpty(command)) return command;

    String os = getOSID();
    List<String> args = new ArrayList<>(command.size() + 2);

    String first = StringUtil.getFirstWord(command.get(0)).toLowerCase();
    String shell = System.getenv("SHELL");

    WbFile file = evaluateFileArgument(first);

    if ("windows".equals(os))
    {
      if (!first.startsWith("cmd"))
      {
        args.add("cmd");
        args.add("/c");
      }
      args.addAll(command);
    }
    else if (file != null && file.canExecute())
    {
      // even though it is a shell script with a shebang, ProcessBuilder can not run this directly
      if (isShellScript(file))
      {
        args.add(shell);
      }
      args.add(StringUtil.listToString(command, ' '));
    }
    else if (!first.startsWith(shell))
    {
      args.add(shell);
      args.add("-c");
      args.add(StringUtil.listToString(command, ' '));
    }
    else
    {
      args.addAll(command);
    }
    return args;
  }

  private boolean isShellScript(WbFile file)
  {
    if (file == null) return false;
    if (!file.exists()) return false;
    if (file.length() < 3) return false;

    FileInputStream in = null;
    try
    {
      byte[] buffer = new byte[2];
      in = new FileInputStream(file);
      int numRead = in.read(buffer);
      if (numRead == 2)
      {
        return buffer[0] == '#' && buffer[1] == '!';
      }
      return false;
    }
    catch (Throwable th)
    {
      return false;
    }
    finally
    {
      FileUtil.closeQuietely(in);
    }
  }

  private boolean useShell(String command)
  {
    if (StringUtil.isEmpty(command)) return false;
    String os = getOSID();
    if (os == null) return false;

    command = StringUtil.getFirstWord(command);

    boolean ignoreCase = PlatformHelper.isWindows() || PlatformHelper.isMacOS();

    List<String> cmdlist = Settings.getInstance().getListProperty("workbench.exec." + os + ".useshell", false, "*");
    if (cmdlist.contains("*")) return true;
    for (String cmd : cmdlist)
    {
      if (StringUtil.compareStrings(cmd, command, ignoreCase) == 0) return true;
    }
    return false;
  }

  private String getOSID()
  {
    if (PlatformHelper.isWindows())
    {
      return "windows";
    }
    if (PlatformHelper.isMacOS())
    {
      return "macos";
    }
    if (PlatformHelper.isLinux())
    {
      return "linux";
    }
    return null;
  }

  @Override
  protected boolean isConnectionRequired()
  {
    return false;
  }


  @Override
  public String getVerb()
  {
    return VERB;
  }

  @Override
  public boolean isWbCommand()
  {
    return true;
  }

}
