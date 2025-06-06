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
import java.util.Map;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;

import workbench.util.ArgumentParser;
import workbench.util.ArgumentType;
import workbench.util.SqlUtil;
import workbench.util.WbFile;
import workbench.util.XsltTransformer;

/**
 *
 * @author  Thomas Kellerer
 */
public class WbXslt
  extends SqlCommand
{
  public static final String VERB = "WbXslt";
  public static final String ARG_STYLESHEET = "stylesheet";
  public static final String ARG_OUTPUT = "xsltOutput";
  public static final String ARG_INPUT = "inputFile";
  public static final String ARG_PARAMETER = "xsltParameter";

  public WbXslt()
  {
    super();
    cmdLine = new ArgumentParser();
    addCommonXsltParameters(cmdLine);
    cmdLine.addArgument(ARG_INPUT, ArgumentType.Filename);
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
    StatementRunnerResult result = new StatementRunnerResult(messageLogger);
    String parm = SqlUtil.stripVerb(aSql);

    cmdLine.parse(parm);
    if (displayHelp(result))
    {
      return result;
    }

    WbFile inputFile = evaluateFileArgument(cmdLine.getValue(ARG_INPUT));
    WbFile outputFile = evaluateFileArgument(cmdLine.getValue(ARG_OUTPUT));
    WbFile xsltFile = findXsltFile(cmdLine.getValue(ARG_STYLESHEET));

    if (!cmdLine.hasArguments())
    {
      result.addErrorMessageByKey("ErrXsltWrongParameter");
      return result;
    }

    if (inputFile == null)
    {
      result.addErrorMessageByKey("ErrXsltMissingInputFile");
      return result;
    }

    if (!inputFile.exists())
    {
      result.addErrorMessageByKey("ErrFileNotFound", cmdLine.getValue(ARG_INPUT));
      return result;
    }

    if (outputFile == null)
    {
      result.addErrorMessageByKey("ErrXsltMissingOutputFile");
      return result;
    }

    if (xsltFile == null)
    {
      result.addErrorMessageByKey("ErrXsltMissingStylesheet");
      return result;
    }

    if (!xsltFile.exists())
    {
      result.addErrorMessageByKey("ErrFileNotFound", cmdLine.getValue(ARG_STYLESHEET));
      return result;
    }

    Map<String, String> params = getParameters(cmdLine);

    XsltTransformer transformer = new XsltTransformer();

    try
    {
      transformer.setXsltBaseDir(getXsltBaseDir());

      transformer.transform(inputFile, outputFile, xsltFile, params);

      String msg = transformer.getAllOutputs();
      if (msg.length() != 0)
      {
        result.addMessage(msg);
        result.addMessageNewLine();
      }

      WbFile xsltUsed = new WbFile(transformer.getXsltUsed());
      WbFile userXslt = new WbFile(xsltFile);
      if (xsltUsed != null && !userXslt.equals(xsltUsed))
      {
        // If the xslt file has been "automatically" found, inform the user about this
        result.addMessageByKey("MsgXsltUsed", xsltUsed.getFullPath());
      }
      result.addMessageByKey("MsgXsltSuccessful", outputFile);
      result.setSuccess();
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error when transforming '" + inputFile + "' to '" + outputFile + "' using " + xsltFile, e);
      String msg = transformer.getAllOutputs(e);
      LogMgr.logError(new CallerInfo(){}, msg, null);
      result.addErrorMessage(msg);
    }
    return result;
  }

  @Override
  public boolean isWbCommand()
  {
    return true;
  }

  public static Map<String, String> getParameters(ArgumentParser cmdLine)
  {
    Map<String, String> params = cmdLine.getMapValue(ARG_PARAMETER);
    Map<String, String> old = cmdLine.getMapValue("xsltParameters");
    params.putAll(old);
    return params;
  }

  public static void addCommonXsltParameters(ArgumentParser cmdLine)
  {
    cmdLine.addArgument(ARG_STYLESHEET, ArgumentType.Filename);
    cmdLine.addArgument(ARG_PARAMETER, ArgumentType.Repeatable);
    cmdLine.addDeprecatedArgument("xsltParameters", ArgumentType.Repeatable); // for backward compatibility
    cmdLine.addArgument(ARG_OUTPUT, ArgumentType.Filename);
  }
}
