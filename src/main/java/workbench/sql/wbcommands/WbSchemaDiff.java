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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;

import workbench.db.DbObjectFinder;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;
import workbench.db.diff.SchemaDiff;

import workbench.storage.RowActionMonitor;

import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;

import workbench.util.ArgumentParser;
import workbench.util.ArgumentType;
import workbench.util.EncodingUtil;
import workbench.util.FileUtil;
import workbench.util.StringUtil;
import workbench.util.WbFile;
import workbench.util.XsltTransformer;


/**
 * @author  Thomas Kellerer
 */
public class WbSchemaDiff
  extends SqlCommand
{
  public static final String VERB = "WbSchemaDiff";

  public static final String ARG_NAMESPACE = "namespace";
  public static final String ARG_INCLUDE_INDEX = "includeIndex";
  public static final String ARG_INCLUDE_FK = "includeForeignKeys";
  public static final String ARG_COMPARE_FK_RULES = "compareForeignKeyRules";
  public static final String ARG_INCLUDE_PK = "includePrimaryKeys";
  public static final String ARG_INCLUDE_CONSTRAINTS = "includeConstraints";
  public static final String ARG_INCLUDE_VIEWS = "includeViews";
  public static final String ARG_DIFF_JDBC_TYPES = "useJdbcTypes";
  public static final String ARG_VIEWS_AS_TABLES = "viewAsTable";
  public static final String ARG_COMPARE_CHK_CONS_BY_NAME = "useConstraintNames";
  public static final String ARG_ADD_TYPES = "additionalTypes";

  private SchemaDiff diff;
  private final CommonDiffParameters diffParams;

  public WbSchemaDiff()
  {
    super();
    cmdLine = new ArgumentParser();
    diffParams = new CommonDiffParameters(cmdLine, getBaseDir());
    cmdLine.addArgument(ARG_NAMESPACE);
    cmdLine.addArgument(ARG_INCLUDE_FK, ArgumentType.BoolArgument);
    cmdLine.addArgument(ARG_COMPARE_FK_RULES, ArgumentType.BoolArgument);
    cmdLine.addArgument(WbSchemaReport.ARG_INCLUDE_SEQUENCES, ArgumentType.BoolArgument);
    cmdLine.addArgument(ARG_INCLUDE_PK, ArgumentType.BoolArgument);
    cmdLine.addArgument(ARG_INCLUDE_INDEX, ArgumentType.BoolArgument);
    cmdLine.addArgument(ARG_INCLUDE_CONSTRAINTS, ArgumentType.BoolArgument);
    cmdLine.addArgument(ARG_INCLUDE_VIEWS, ArgumentType.BoolArgument);
    cmdLine.addArgument(WbSchemaReport.ARG_INCLUDE_PARTITIONS, ArgumentType.BoolArgument);
    cmdLine.addArgument(WbSchemaReport.ARG_INCLUDE_PROCS, ArgumentType.BoolArgument);
    cmdLine.addArgument(WbSchemaReport.ARG_INCLUDE_GRANTS, ArgumentType.BoolArgument);
    cmdLine.addArgument(WbSchemaReport.ARG_INCLUDE_TRIGGERS, ArgumentType.BoolArgument);
    cmdLine.addArgument(WbSchemaReport.ARG_FULL_SOURCE, ArgumentType.BoolSwitch);
    cmdLine.addArgument(ARG_DIFF_JDBC_TYPES, ArgumentType.BoolArgument);
    cmdLine.addArgument(ARG_VIEWS_AS_TABLES, ArgumentType.BoolArgument);
    WbXslt.addCommonXsltParameters(cmdLine);
    cmdLine.addArgument(ARG_COMPARE_CHK_CONS_BY_NAME, ArgumentType.BoolArgument);
    cmdLine.addArgument(ARG_ADD_TYPES, ArgumentType.ListArgument);
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
  public StatementRunnerResult execute(final String sql)
    throws SQLException
  {
    StatementRunnerResult result = new StatementRunnerResult(messageLogger);

    cmdLine.parse(getCommandLine(sql));

    if (cmdLine.getArgumentCount() == 0 || cmdLine.getBoolean(CommonArgs.ARG_HELP))
    {
      result.addErrorMessageByKey("ErrDiffWrongParameters");
      return result;
    }

    if (cmdLine.hasUnknownArguments())
    {
      setUnknownMessage(result, cmdLine, ResourceMgr.getString("ErrDiffWrongParameters"));
      return result;
    }

    if (this.rowMonitor != null) this.rowMonitor.setMonitorType(RowActionMonitor.MONITOR_PLAIN);
    diffParams.setMonitor(rowMonitor);

    WbConnection targetCon = diffParams.getTargetConnection(currentConnection, result);
    if (!result.isSuccess()) return result;

    WbConnection referenceConnection = diffParams.getSourceConnection(currentConnection, result);

    if (referenceConnection == null && targetCon != null && targetCon != currentConnection)
    {
      targetCon.disconnect();
      return result;
    }

    if (!result.isSuccess()) return result;

    if (isCancelled)
    {
      result.addWarning(ResourceMgr.getString("MsgDiffCancelled"));
      return result;
    }

    diff = new SchemaDiff(referenceConnection, targetCon);
    diff.setMonitor(this.rowMonitor);

    // this needs to be set before the tables are defined!
    diff.setIncludeForeignKeys(cmdLine.getBoolean(ARG_INCLUDE_FK, true));
    diff.setCompareFKRules(cmdLine.getBoolean(ARG_COMPARE_FK_RULES, true));
    diff.setIncludeIndex(cmdLine.getBoolean(ARG_INCLUDE_INDEX, true));
    diff.setIncludePrimaryKeys(cmdLine.getBoolean(ARG_INCLUDE_PK, true));
    diff.setIncludeTableConstraints(cmdLine.getBoolean(ARG_INCLUDE_CONSTRAINTS, true));
    diff.setIncludeViews(cmdLine.getBoolean(ARG_INCLUDE_VIEWS, true));
    diff.setCompareJdbcTypes(cmdLine.getBoolean(ARG_DIFF_JDBC_TYPES, false));
    diff.setIncludeProcedures(cmdLine.getBoolean(WbSchemaReport.ARG_INCLUDE_PROCS, false));
    diff.setIncludeTableGrants(cmdLine.getBoolean(WbSchemaReport.ARG_INCLUDE_GRANTS, false));
    diff.setIncludeSequences(cmdLine.getBoolean(WbSchemaReport.ARG_INCLUDE_SEQUENCES, false));
    diff.setTreatViewAsTable(cmdLine.getBoolean(ARG_VIEWS_AS_TABLES, false));
    diff.setCompareConstraintsByName(cmdLine.getBoolean(ARG_COMPARE_CHK_CONS_BY_NAME, true));
    diff.setIncludeTriggers(cmdLine.getBoolean(WbSchemaReport.ARG_INCLUDE_TRIGGERS, true));
    diff.setIncludePartitions(cmdLine.getBoolean(WbSchemaReport.ARG_INCLUDE_PARTITIONS, false));
    diff.setUseFullObjectSource(cmdLine.getBoolean(WbSchemaReport.ARG_FULL_SOURCE, false));
    List<String> types = cmdLine.getListValue(ARG_ADD_TYPES);
    diff.setAdditionalTypes(types);

    String refTables = cmdLine.getValue(CommonDiffParameters.PARAM_REFERENCETABLES);
    String targetTables = cmdLine.getValue(CommonDiffParameters.PARAM_TARGETTABLES);

    // Setting the tables to be excluded must be done before setting any other table selection
    String excludeTables = cmdLine.getValue(CommonDiffParameters.PARAM_EXCLUDE_TABLES);
    if (excludeTables != null)
    {
      List<String> l = StringUtil.stringToList(excludeTables, ",", true, true);
      diff.setExcludeTables(l);
    }

    String refSchema = cmdLine.getValue(CommonDiffParameters.PARAM_REFERENCESCHEMA);
    String targetSchema = cmdLine.getValue(CommonDiffParameters.PARAM_TARGETSCHEMA);

    if (refTables == null)
    {
      if (refSchema == null && targetSchema == null)
      {
        if (referenceConnection == targetCon)
        {
          result.addErrorMessageByKey("ErrDiffSameConnectionNoTableSelection");
          if (targetCon.getId().startsWith("Wb-Diff"))
          {
            targetCon.disconnectSilently();
          }
          if (referenceConnection.getId().startsWith("Wb-Diff"))
          {
            referenceConnection.disconnectSilently();
          }
          return result;
        }
        diff.compareAll();
      }
      else
      {
        diff.setSchemas(refSchema, targetSchema);
      }
    }
    else if (targetTables == null)
    {
      SourceTableArgument parms = new SourceTableArgument(refTables, referenceConnection);
      List<TableIdentifier> tables = new ArrayList<>();
      for (TableIdentifier tbl : parms.getTables())
      {
        TableIdentifier realTable = new DbObjectFinder(referenceConnection).findTable(tbl, false);
        if (realTable != null)
        {
          tables.add(realTable);
        }
      }
      diff.setTables(tables);
      diff.setSchemaNames(refSchema, targetSchema);
    }
    else
    {
      List<String> rl = StringUtil.stringToList(refTables, ",", true, true);
      List<String> tl = StringUtil.stringToList(targetTables, ",", true, true);
      if (rl.size() != tl.size())
      {
        result.addMessageByKey("ErrDiffTableListNoMatch");
        result.setFailure();
        return result;
      }
      diff.setTableNames(rl, tl);
      diff.setSchemaNames(refSchema, targetSchema);
    }

    if (isCancelled || diff.isCancelled())
    {
      result.addWarningByKey("MsgDiffCancelled");
      return result;
    }

    Writer out = null;
    boolean outputToConsole = false;
    WbFile output = evaluateFileArgument(cmdLine.getValue(CommonArgs.ARG_FILE));

    try
    {
      if (output == null)
      {
        out = new StringWriter(5000);
        outputToConsole = true;
      }
      else
      {
        String encoding = cmdLine.getValue(CommonArgs.ARG_ENCODING, "UTF-8");
        encoding = EncodingUtil.cleanupEncoding(encoding);
        diff.setEncoding(encoding);
        out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(output), encoding), 256*1024);
      }

      // this will start the actual diff process
      if (!diff.isCancelled())
      {
        diff.writeXml(out);
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error writing output file", e);
    }
    finally
    {
      FileUtil.closeQuietely(out);
      if (referenceConnection.getId().startsWith("Wb-Diff"))
      {
        referenceConnection.disconnectSilently();
      }
      if (targetCon.getId().startsWith("Wb-Diff"))
      {
        targetCon.disconnectSilently();
      }
    }

    if (diff.isCancelled())
    {
      result.addMessageByKey("MsgDiffCancelled");
    }
    else
    {
      if (outputToConsole)
      {
        result.addMessage(out.toString());
      }
      else
      {
        String msg = ResourceMgr.getString("MsgDiffFileWritten") + " " + output.getFullPath();
        result.addMessage(msg);

        File xslt = findXsltFile(cmdLine.getValue(WbXslt.ARG_STYLESHEET));
        File xsltOutput = evaluateFileArgument(cmdLine.getValue(WbXslt.ARG_OUTPUT));
        Map<String, String> xsltParams = WbXslt.getParameters(cmdLine);

        if (xslt != null && xsltOutput != null)
        {
          XsltTransformer transformer = new XsltTransformer();
          try
          {
            transformer.setXsltBaseDir(getXsltBaseDir());
            transformer.transform(output, xsltOutput, xslt, xsltParams);
            String xsltMsg = transformer.getAllOutputs();
            if (xsltMsg.length() != 0)
            {
              result.addMessage(xsltMsg);
              result.addMessageNewLine();
            }
            result.addMessage(ResourceMgr.getFormattedString("MsgXsltSuccessful", xsltOutput));
            result.setSuccess();
          }
          catch (FileNotFoundException fnf)
          {
            LogMgr.logError(new CallerInfo(){}, "Stylesheet " + xslt + " not found!", fnf);
            result.addErrorMessageByKey("ErrXsltNotFound", xslt.getAbsolutePath());
          }
          catch (Exception e)
          {
            LogMgr.logError(new CallerInfo(){}, "Error when transforming '" + output.getFullPath() + "' to '" + xsltOutput + "' using " + xslt, e);
            result.addErrorMessage(transformer.getAllOutputs(e));
          }
        }
      }
    }
    return result;
  }

  @Override
  public void cancel()
    throws SQLException
  {
    super.cancel();
    if (this.diff != null) this.diff.cancel();
  }

  @Override
  public boolean isWbCommand()
  {
    return true;
  }
}
