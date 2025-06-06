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
package workbench;

import java.io.BufferedReader;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

import workbench.sql.wbcommands.CommonArgs;
import workbench.sql.wbcommands.CommonDiffParameters;
import workbench.sql.wbcommands.WbCopy;

import workbench.util.ArgumentParser;
import workbench.util.ArgumentType;
import workbench.util.EncodingUtil;
import workbench.util.FileUtil;
import workbench.util.StringUtil;

/**
 * A class to define and parse the arguments that are available when the application is started.
 *
 * @author Thomas Kellerer
 */
public class AppArguments
  extends ArgumentParser
{
  // Parameters for batch execution used by BatchRunner
  public static final String ARG_SCRIPT = "script";
  public static final String ARG_COMMAND = "command";
  public static final String ARG_SCRIPT_ENCODING = "encoding";
  public static final String ARG_ABORT = "abortOnError";
  public static final String ARG_SHOWPROGRESS = "showProgress";

  // Connection related parameters
  public static final String ARG_PROFILE = "profile";
  public static final String ARG_PROFILE_GROUP = "profilegroup";
  public static final String ARG_CONN_URL = "url";
  public static final String ARG_CONN_DRIVER = "driver";
  public static final String ARG_CONN_DRIVER_NAME = "driverName";
  public static final String ARG_CONN_DRIVER_CLASS = "driverclass";
  public static final String ARG_CONN_JAR = "driverjar";
  public static final String ARG_CONN_USER = "username";
  public static final String ARG_CONN_PWD = "password";
  public static final String ARG_CONN_PROPS = "connectionProperties";
  public static final String ARG_CONN_DESCRIPTOR = "connection";
  public static final String ARG_CONN_AUTOCOMMIT = "autocommit";
  public static final String ARG_CONN_SSH_HOST = "sshHost";
  public static final String ARG_CONN_SSH_USER = "sshUser";
  public static final String ARG_CONN_SSH_KEYFILE = "sshPrivateKey";
  public static final String ARG_CONN_SSH_PWD = "sshPassword";
  public static final String ARG_CONN_SSH_LOCAL_PORT = "sshLocalPort";
  public static final String ARG_CONN_SSH_DB_PORT = "sshDBPort";
  public static final String ARG_CONN_SSH_DB_HOST = "sshDBHost";
  public static final String ARG_CONN_SSH_PORT = "sshPort";
  public static final String ARG_CONN_SEPARATE = "separateConnection";
  public static final String ARG_CONN_EMPTYNULL = "emptyStringIsNull";
  public static final String ARG_CONN_ROLLBACK = "rollbackOnDisconnect";
  public static final String ARG_CONN_CHECK_OPEN_TRANS = "checkUncommitted";
  public static final String ARG_CONN_TRIM_CHAR = "trimCharData";
  public static final String ARG_CONN_FETCHSIZE = "fetchSize";
  public static final String ARG_IGNORE_DROP = "ignoreDropErrors";
  public static final String ARG_READ_ONLY = "readOnly";
  public static final String ARG_CONN_REMOVE_COMMENTS = "removeComments";
  public static final String ARG_HIDE_WARNINGS = "hideWarnings";
  public static final String ARG_INTERACTIVE = "interactive";
  public static final String ARG_PROPFILE = "arguments";
  public static final String ARG_LB_CONN = "lbDefaults";
  public static final String ARG_CONN_NAME = "connectionName";

  public static final String ARG_DISPLAY_RESULT = "displayResult";
  public static final String ARG_SUCCESS_SCRIPT = "cleanupSuccess";
  public static final String ARG_ERROR_SCRIPT = "cleanupError";
  public static final String ARG_SHOW_TIMING = "showTiming";
  public static final String ARG_FEEDBACK = "feedback";
  public static final String ARG_WORKSPACE = "workspace";
  public static final String ARG_ALT_DELIMITER = "altDelimiter";
  public static final String ARG_DELIMITER = "delimiter";
  public static final String ARG_CONSOLIDATE_LOG = "consolidateMessages";

  // Initial tool parameters
  public static final String ARG_SHOW_PUMPER = "dataPumper";
  public static final String ARG_SHOW_DBEXP = "dbExplorer";
  public static final String ARG_SHOW_SEARCHER = "objectSearcher";

  // Other parameters
  public static final String ARG_PROFILE_STORAGE = "profileStorage";
  public static final String ARG_MACRO_STORAGE = "macroStorage";
  public static final String ARG_CONFIGDIR = "configDir";
  public static final String ARG_LIBDIR = "libdir";
  public static final String ARG_LOGLEVEL = "logLevel";
  public static final String ARG_LOGFILE = "logfile";
  public static final String ARG_VARDEF = "varDef";
  public static final String ARG_VARIABLE = "variable";
  public static final String ARG_VAR_FILE = "varFile";
  public static final String ARG_LANG = "language";
  public static final String ARG_NOSETTNGS = "noSettings";
  public static final String ARG_NOTEMPLATES = "noTemplates";
  public static final String ARG_CONSOLE_OPT_COLS = "optimizeColWidth";
  public static final String ARG_CONSOLE_BUFFER_RESULTS = "bufferResults";
  public static final String ARG_PROP = "prop";
  public static final String ARG_LOG_ALL_STMT = "logAllStatements";
  public static final String ARG_EXTENSION = "extension";

  public AppArguments()
  {
    super();
    addArgument(ARG_PROPFILE);
    addArgument(ARG_LB_CONN);
    addArgument(ARG_PROFILE, ArgumentType.ProfileArgument);
    addArgument(ARG_FEEDBACK, ArgumentType.BoolArgument);
    addArgument(ARG_PROFILE_GROUP);
    addArgument(ARG_PROFILE_STORAGE, ArgumentType.Repeatable);
    addArgument(ARG_MACRO_STORAGE);
    addArgument(ARG_CONFIGDIR);
    addArgument(ARG_LIBDIR);
    addArgument(ARG_SCRIPT);
    addArgument(ARG_COMMAND);
    addArgument(ARG_SCRIPT_ENCODING);
    addArgument(ARG_LOGLEVEL);
    addArgument(ARG_LOGFILE);
    addArgument(ARG_ABORT, ArgumentType.BoolArgument);
    addArgument(ARG_SUCCESS_SCRIPT);
    addArgument(ARG_ERROR_SCRIPT);
    addArgument(ARG_VARDEF, ArgumentType.RepeatableValue);
    addArgument(ARG_VARIABLE, ArgumentType.RepeatableValue);
    addArgument(ARG_VAR_FILE, ArgumentType.RepeatableValue);
    addArgument(ARG_CONN_URL);
    addArgument(ARG_CONN_PROPS, ArgumentType.RepeatableValue);
    addArgument(ARG_CONN_DRIVER);
    addArgument(ARG_CONN_DRIVER_CLASS);
    addArgument(ARG_CONN_JAR);
    addArgument(ARG_CONN_FETCHSIZE);
    addArgument(ARG_CONN_USER);
    addArgument(ARG_CONN_NAME);
    addArgument(ARG_CONN_PWD);
    addArgument(ARG_CONN_SEPARATE, ArgumentType.BoolArgument);
    addArgument(ARG_CONN_EMPTYNULL, ArgumentType.BoolArgument);
    addArgument(ARG_CONN_AUTOCOMMIT, ArgumentType.BoolArgument);
    addArgument(ARG_CONN_REMOVE_COMMENTS, ArgumentType.BoolArgument);
    addArgument(ARG_CONN_CHECK_OPEN_TRANS, ArgumentType.BoolArgument);
    addArgument(ARG_CONN_ROLLBACK, ArgumentType.BoolArgument);

    addArgument(ARG_CONN_SSH_HOST);
    addArgument(ARG_CONN_SSH_KEYFILE, ArgumentType.Filename);
    addArgument(ARG_CONN_SSH_PWD);
    addArgument(ARG_CONN_SSH_USER);
    addArgument(ARG_CONN_SSH_LOCAL_PORT);
    addArgument(ARG_CONN_SSH_DB_PORT);
    addArgument(ARG_CONN_SSH_DB_HOST);
    addArgument(ARG_CONN_SSH_PORT);

    addArgument(ARG_SHOW_PUMPER, ArgumentType.BoolArgument);
    addArgument(ARG_IGNORE_DROP, ArgumentType.BoolArgument);
    addArgument(ARG_DISPLAY_RESULT, ArgumentType.BoolArgument);
    addArgument(ARG_SHOW_DBEXP, ArgumentType.BoolSwitch);
    addArgument(ARG_SHOW_SEARCHER, ArgumentType.BoolSwitch);
    addArgument(ARG_SHOW_TIMING, ArgumentType.BoolSwitch);
    addArgument(ARG_SHOWPROGRESS, ArgumentType.BoolArgument);
    addArgument(ARG_CONSOLE_OPT_COLS, ArgumentType.BoolArgument);
    addArgument(ARG_CONSOLE_BUFFER_RESULTS, ArgumentType.BoolArgument);
    addArgument(ARG_WORKSPACE);
    addArgument(ARG_NOSETTNGS, ArgumentType.BoolArgument);
    addArgument(ARG_NOTEMPLATES, ArgumentType.BoolSwitch);
    addArgument(ARG_HIDE_WARNINGS, ArgumentType.BoolArgument);
    addArgument(ARG_ALT_DELIMITER);
    addArgument(ARG_DELIMITER);
    addArgument(ARG_READ_ONLY, ArgumentType.BoolArgument);
    addArgument(ARG_CONN_TRIM_CHAR, ArgumentType.BoolArgument);
    addArgument(ARG_LANG);
    addArgument(ARG_CONSOLIDATE_LOG, ArgumentType.BoolArgument);
    addArgument(ARG_INTERACTIVE, ArgumentType.BoolArgument);
    addArgument("help");
    addArgument("version");
    addArgument(ARG_PROP, ArgumentType.Repeatable);
    addArgument(ARG_LOG_ALL_STMT, ArgumentType.BoolSwitch);
    addArgument(ARG_CONN_DESCRIPTOR);
    addArgument(ARG_EXTENSION);
    addArgument(WbCopy.PARAM_SOURCE_CONN);
    addArgument(WbCopy.PARAM_TARGET_CONN);
    addArgument(WbCopy.PARAM_SOURCEPROFILE);
    addArgument(WbCopy.PARAM_SOURCEPROFILE_GROUP);
    addArgument(WbCopy.PARAM_TARGETPROFILE);
    addArgument(WbCopy.PARAM_TARGETPROFILE_GROUP);
    addArgument(CommonArgs.ARG_CONTINUE, ArgumentType.BoolSwitch);

    addArgument(CommonDiffParameters.PARAM_SOURCE_CONN);
    addArgument(CommonDiffParameters.PARAM_SOURCEPROFILE);
    addArgument(CommonDiffParameters.PARAM_SOURCEPROFILE_GROUP);
  }

  @Override
  public void parse(String[] args)
  {
    List<String> entries = new ArrayList<>(args.length);
    for (String entry : args)
    {
      if (StringUtil.isBlank(entry)) continue;
      if (entry.startsWith("-"))
      {
        entry = entry.substring(1);
      }
      entries.add(entry);
    }
    super.parse(entries);

    String propfile = getValue(ARG_PROPFILE);
    if (propfile != null)
    {
      try
      {
        File f = new File(propfile);
        parseProperties(f);
      }
      catch (Exception e)
      {
        System.err.println("Could not read properties file: " + propfile);
        e.printStackTrace();
      }
    }
    String lb = getValue(ARG_LB_CONN);
    if (lb != null)
    {
      try
      {
        File f = new File(lb);
        BufferedReader in = EncodingUtil.createBufferedReader(f, null);
        List<String> lines = FileUtil.getLines(in, true, true);
        List<String> translated = new ArrayList<>(lines.size());
        for (String line : lines)
        {
          if (line.startsWith("classpath:") && isArgNotPresent(ARG_CONN_JAR))
          {
            String filename = line.substring("classpath:".length()).trim();
            File lib = new File(filename);
            if (lib.getParent() == null)
            {
							// If no directory is given, Liquibase assumes the current directory
              // DbDriver on the other hand will search the jar file in the config directory, if no directory is specified.
              // By appending "./" to the filename we force the use of the current directory
              filename = "./" + lib.getName();
            }
            line = ARG_CONN_JAR + "=" + filename;
            translated.add(line);
          }
          else
          {
            processParameter(line, "driver:", ARG_CONN_DRIVER, translated);
            processParameter(line, "url:", ARG_CONN_URL, translated);
            processParameter(line, "username:", ARG_CONN_USER, translated);
            processParameter(line, "password:", ARG_CONN_PWD, translated);
          }
        }
        parse(translated);
      }
      catch (Exception e)
      {
        System.err.println("Could not read liquibase properties!");
        e.printStackTrace();
      }
    }
  }

  private void processParameter(String lbLine, String lbKeyword, String arg, List<String> translated)
  {
    if (lbLine.startsWith(lbKeyword) && isArgNotPresent(arg))
    {
      String l = lbLine.replace(lbKeyword, arg + "=");
      translated.add(l);
    }
  }

  public void setCommandString(String cmd)
  {
    arguments.put(ARG_COMMAND, cmd);
  }

  public String getHelp()
  {
    StringBuilder msg = new StringBuilder(100);
    List<String> args = getRegisteredArguments();
    msg.append("Available parameters:\n");
    for (String arg : args)
    {
      ArgumentType type = getArgumentType(arg);
      msg.append('-');
      msg.append(arg);
      if (type == ArgumentType.BoolArgument)
      {
        msg.append(" (true/false)");
      }
      msg.append("\n");
    }
    return msg.toString();
  }

}
