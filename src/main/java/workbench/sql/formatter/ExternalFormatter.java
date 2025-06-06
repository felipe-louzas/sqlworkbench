/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
 * that restricts the use for certain governments.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.sql-workbench.eu/manual/license.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.eu
 */
package workbench.sql.formatter;

import java.io.File;
import java.io.IOException;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.util.FileUtil;
import workbench.util.StringUtil;
import workbench.util.WbFile;

/**
 *
 * @author Thomas Kellerer
 */
public class ExternalFormatter
  implements SqlFormatter
{
  public static final String DEFAULT_DBID = "default";
  public static final String INPUT_FILE = "${wbin}";
  public static final String OUTPUT_FILE = "${wbout}";

  private String program;
  private String cmdLine;
  private String inputEncoding;
  private String outputEncoding;
  private boolean supportsMultipleStatements;
  private boolean enabled = true;
  private String lastError;

  public ExternalFormatter()
  {
  }

  public void setEnabled(boolean flag)
  {
    enabled = flag;
  }

  public boolean isUsable()
  {
    return (enabled && programExists());
  }

  public boolean programExists()
  {
    if (StringUtil.isBlank(program)) return false;
    WbFile f = new WbFile(program);
    return f.exists();
  }

  public boolean isEnabled()
  {
    return enabled;
  }

  public String getProgram()
  {
    return program;
  }

  public void setProgram(String executable)
  {
    program = StringUtil.trimQuotes(executable, '"');
  }

  public String getCommandLine()
  {
    return cmdLine;
  }

  public void setCommandLine(String command)
  {
    cmdLine = command;
  }

  public void setInputEncoding(String encoding)
  {
    inputEncoding = encoding;
  }

  public void setOutputEncoding(String encoding)
  {
    outputEncoding = encoding;
  }

  @Override
  public boolean supportsMultipleStatements()
  {
    return supportsMultipleStatements;
  }

  public void setSupportsMultipleStatements(boolean supportsMultipleStatements)
  {
    this.supportsMultipleStatements = supportsMultipleStatements;
  }

  @Override
  public String getLastError()
  {
    return lastError;
  }

  @Override
  public String getFormattedSql(String sql)
  {
    lastError = null;

    try
    {
      return runFormatter(sql);
    }
    catch (IOException ex)
    {
      lastError = ex.getMessage();
      LogMgr.logError(new CallerInfo(){}, "Could not format SQL statement", ex);
      return sql;
    }
  }

  private String runFormatter(String sql)
    throws IOException
  {
    File infile = File.createTempFile("wbf$_in", ".sql");
    File outfile = File.createTempFile("wbf$_out", ".sql");
    File errFile = File.createTempFile("wbf$_syserr", ".txt");
    File sysOut = File.createTempFile("wbf$_sysout", ".txt");

    boolean useSystemOut = true;
    boolean useSystemIn = true;

    CallerInfo ci = new CallerInfo(){};

    String args = StringUtil.coalesce(cmdLine, "");

    if (args.contains(INPUT_FILE))
    {
      args = args.replace(INPUT_FILE, quotePath(infile.getAbsolutePath()));
      useSystemIn = false;
    }

    if (args.contains(OUTPUT_FILE))
    {
      // just to be sure that the tool doesn't fail because the file is already there
      FileUtil.deleteSilently(outfile);

      args = args.replace(OUTPUT_FILE, quotePath(outfile.getAbsolutePath()));
      useSystemOut = false;
    }

    if (inputEncoding == null)
    {
      inputEncoding =  System.getProperty("file.encoding");
      LogMgr.logInfo(ci, "Using encoding for SQL formatter input file: " + inputEncoding);
    }

    if (outputEncoding == null)
    {
      outputEncoding = inputEncoding;
      LogMgr.logInfo(ci, "Using encoding for SQL formatter output file: " + outputEncoding);
    }

    try
    {
      FileUtil.writeString(infile, sql, inputEncoding, false);
      LogMgr.logDebug(ci, "Input file written to: " + infile.getAbsolutePath());

      WbFile prg = new WbFile(program);

      // ProcessBuilder will add quotes around any argument that contains spaces
      // so if "-i foo -o bar" is passed to the constructor of ProcessBuilder it will add
      // quotes around the complete argument. Therefore we need to split the whole
      // args command line into tokens.
      List<String> argList = StringUtil.stringToList(args, " ", false, false, false, true);
      argList.add(0, prg.getFullPath());
      ProcessBuilder pb = new ProcessBuilder(argList);
      pb.directory(prg.getAbsoluteFile().getParentFile());

      if (useSystemIn)
      {
        pb.redirectInput(infile);
      }

      if (useSystemOut)
      {
        pb.redirectOutput(outfile);
      }
      else
      {
        pb.redirectOutput(sysOut);
      }

      pb.redirectError(errFile);

      LogMgr.logInfo(ci, "Running external formatter: " + pb.command());

      Process task = pb.start();
      task.waitFor();

      int exitValue = task.exitValue();
      LogMgr.logDebug(ci, "Return value was: " + exitValue);

      String formatted = FileUtil.readFile(outfile, outputEncoding);
      if (StringUtil.isEmpty(formatted))
      {
        LogMgr.logWarning(ci, "Result from formatter was empty!");
        formatted = sql;
      }

      if (!useSystemOut)
      {
        String out = readFile(sysOut);
        if (StringUtil.isNotEmpty(out))
        {
          LogMgr.logInfo(ci, "Output from formatter: " + out);
        }
      }

      lastError = readFile(errFile);
      if (StringUtil.isNotEmpty(lastError))
      {
        LogMgr.logWarning(ci, "Error message from formatter: " + lastError);
      }

      return StringUtil.trim(formatted);
    }
    catch (Exception ex)
    {
      lastError = readFile(errFile);
      if (StringUtil.isNotEmpty(lastError))
      {
        LogMgr.logError(ci, "Error message from formatter: " + lastError, null);
        LogMgr.logDebug(ci, "Error cause", ex);
      }
      else
      {
        LogMgr.logError(ci, "Error running formatter", ex);
      }
    }
    finally
    {
      FileUtil.deleteSilently(infile);
      FileUtil.deleteSilently(outfile);
      FileUtil.deleteSilently(errFile);
      FileUtil.deleteSilently(sysOut);
    }

    // something went wrong, return the original SQL statement
    return sql;
  }

  private String quotePath(String path)
  {
    if (StringUtil.isBlank(path) || path.length() < 3) return path;
    if (path.startsWith("\"") && path.endsWith("\"")) return path;
    return "\"" + path + "\"";
  }

  private String readFile(File errFile)
  {
    if (errFile == null) return null;
    if (!errFile.exists()) return null;

    String error = null;
    try
    {
      error = FileUtil.readFile(errFile, System.getProperty("file.encoding"));
      error = StringUtil.trimToNull(error);
    }
    catch (Throwable th)
    {
      // ignore
    }
    return error;
  }

  @Override
  public String toString()
  {
    if (StringUtil.isEmpty(program)) return "<empty>";
    WbFile f = new WbFile(program);
    return f.getAbsolutePath();
  }

  public static void saveDefinition(ExternalFormatter formatter, String dbId)
  {
    if (formatter == null) return;

    Settings.getInstance().setProperty("workbench.formatter." + dbId + ".program", formatter.program == null ? "" : formatter.program);
    Settings.getInstance().setProperty("workbench.formatter." + dbId + ".enabled", formatter.isEnabled());
    Settings.getInstance().setProperty("workbench.formatter." + dbId + ".cmdline", formatter.cmdLine);
    Settings.getInstance().setProperty("workbench.formatter." + dbId + ".inputencoding", formatter.inputEncoding);
    Settings.getInstance().setProperty("workbench.formatter." + dbId + ".outputencoding", formatter.outputEncoding);
    Settings.getInstance().setProperty("workbench.formatter." + dbId + ".supports.scripts", formatter.supportsMultipleStatements);
  }

  public static ExternalFormatter getDefinition(String dbId)
  {
    if (StringUtil.isEmpty(dbId))
    {
      dbId = DEFAULT_DBID;
    }

    String prg = Settings.getInstance().getProperty("workbench.formatter." + dbId + ".program", null);
    boolean enabled = Settings.getInstance().getBoolProperty("workbench.formatter." + dbId + ".enabled", false);

    String cmdLine = Settings.getInstance().getProperty("workbench.formatter." + dbId + ".cmdline", null);
    String inputEncoding = Settings.getInstance().getProperty("workbench.formatter." + dbId + ".inputencoding", null);
    String outputEncoding = Settings.getInstance().getProperty("workbench.formatter." + dbId + ".outputencoding", null);
    boolean supportsScripts = Settings.getInstance().getBoolProperty("workbench.formatter." + dbId + ".supports.scripts", false);

    ExternalFormatter f = new ExternalFormatter();
    f.setCommandLine(cmdLine);
    f.setProgram(prg);
    f.setInputEncoding(inputEncoding);
    f.setOutputEncoding(outputEncoding);
    f.setSupportsMultipleStatements(supportsScripts);
    f.setEnabled(enabled);
    return f;
  }
}
