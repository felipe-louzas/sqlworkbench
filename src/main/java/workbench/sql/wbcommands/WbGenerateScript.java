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

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import workbench.interfaces.ScriptGenerationMonitor;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.DbExplorerSettings;
import workbench.resource.ResourceMgr;

import workbench.db.DbObject;
import workbench.db.ObjectScripter;
import workbench.db.ProcedureDefinition;
import workbench.db.ProcedureReader;
import workbench.db.TriggerDefinition;
import workbench.db.TriggerReader;
import workbench.db.TriggerReaderFactory;

import workbench.storage.RowActionMonitor;

import workbench.sql.DelimiterDefinition;
import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;

import workbench.util.ArgumentParser;
import workbench.util.ArgumentType;
import workbench.util.CollectionUtil;
import workbench.util.EncodingUtil;
import workbench.util.FileUtil;
import workbench.util.StringBuilderOutput;
import workbench.util.StringUtil;
import workbench.util.WbFile;

/**
 *
 * @author Thomas Kellerer
 */
public class WbGenerateScript
  extends SqlCommand
  implements ScriptGenerationMonitor
{
  public static final String VERB = "WbGenerateScript";
  public static final String SHORT_VERB = "WbGenScript";
  public static final String ARG_EXCLUDE = "exclude";
  public static final String ARG_INCLUDE_FK = "includeForeignkeys";
  public static final String ARG_INCLUDE_DROP = "includeDrop";
  public static final String ARG_INCLUDE_COMMIT = "includeCommit";
  public static final String ARG_USE_SEPARATOR = "useSeparator";
  public static final String ARG_STMT_DELIMITER = "statementDelimiter";

  private ObjectScripter scripter;

  public WbGenerateScript()
  {
    super();
    this.isUpdatingCommand = false;

    cmdLine = new ArgumentParser();
    cmdLine.addArgument(CommonArgs.ARG_TYPES, ArgumentType.ObjectTypeArgument);
    cmdLine.addArgument(CommonArgs.ARG_SCHEMAS, ArgumentType.SchemaArgument);
    cmdLine.addArgument(CommonArgs.ARG_CATALOG, ArgumentType.CatalogArgument);
    cmdLine.addArgument(CommonArgs.ARG_OBJECTS, ArgumentType.TableArgument);
    cmdLine.addArgument(ARG_EXCLUDE);
    cmdLine.addArgument(WbSchemaReport.ARG_INCLUDE_PROCS, ArgumentType.BoolSwitch);
    cmdLine.addArgument(WbSchemaReport.ARG_INCLUDE_TRIGGERS, ArgumentType.BoolSwitch);
    cmdLine.addArgument(WbSchemaReport.ARG_INCLUDE_GRANTS, ArgumentType.BoolArgument);
    cmdLine.addArgument(ARG_USE_SEPARATOR, ArgumentType.BoolSwitch);
    cmdLine.addArgument(ARG_INCLUDE_FK, ArgumentType.BoolArgument);
    cmdLine.addArgument(CommonArgs.ARG_FILE, ArgumentType.Filename);
    cmdLine.addArgument(ARG_INCLUDE_DROP, ArgumentType.BoolSwitch);
    cmdLine.addArgument(ARG_INCLUDE_COMMIT, ArgumentType.BoolSwitch);
    cmdLine.addArgument(ARG_STMT_DELIMITER, DelimiterDefinition.ABBREVIATIONS);
    CommonArgs.addEncodingParameter(cmdLine);
  }

  @Override
  public StatementRunnerResult execute(String sql)
    throws SQLException
  {
    StatementRunnerResult result = new StatementRunnerResult();
    String args = getCommandLine(sql);
    cmdLine.parse(args);

    if (displayHelp(result))
    {
      return result;
    }

    List<String> schemas = null;
    String catalog = null;
    Collection<String> types = null;
    String names = null;

    if (!cmdLine.hasArguments())
    {
      names = args;
    }
    else
    {
      if (cmdLine.hasUnknownArguments())
      {
        setUnknownMessage(result, cmdLine, ResourceMgr.getString("ErrGenScriptWrongParam"));
        return result;
      }
      names = cmdLine.getValue(CommonArgs.ARG_OBJECTS);
      schemas = cmdLine.getListValue(CommonArgs.ARG_SCHEMAS);
      catalog = cmdLine.getValue(CommonArgs.ARG_CATALOG);
      types = cmdLine.getListValue(CommonArgs.ARG_TYPES);
    }

    List<DbObject> objects = new ArrayList<>();

    if (CollectionUtil.isEmpty(schemas))
    {
      schemas = CollectionUtil.arrayList(currentConnection.getCurrentSchema());
    }

    if (StringUtil.isBlank(names))
    {
      names = "%";
    }

    String excluded = cmdLine.getValue(ARG_EXCLUDE);
    if (CollectionUtil.isEmpty(types))
    {
      // SourceTableArgument defaults to "table like" types only
      // but for WbGenerateScript we don't want to specify the needed types
      // if e.g. generating the source for just a few objects
      types = currentConnection.getMetadata().getObjectTypes();
    }

    String[] typesArray = StringUtil.toArray(types, true, true);

    for (String schema : schemas)
    {
      SourceTableArgument selector = new SourceTableArgument(names, excluded, schema, typesArray, currentConnection);
      objects.addAll(selector.getTables());
    }

    boolean treatSchemaAsCatalog = currentConnection.getDbSettings().schemaIsCatalog();

    List<String> procNames = getSearchNames(WbSchemaReport.ARG_INCLUDE_PROCS);

    if (procNames != null)
    {
      ProcedureReader reader = currentConnection.getMetadata().getProcedureReader();
      for (String schema : schemas)
      {
        if (isCancelled) break;
        String catalogToUse = treatSchemaAsCatalog ? schema : catalog;
        String schemaToUse = treatSchemaAsCatalog ? null : schema;

        for (String searchName : procNames)
        {
          List<ProcedureDefinition> procs = reader.getProcedureList(catalogToUse, schemaToUse, searchName);
          objects.addAll(procs);
        }
      }
    }

    List<String> trgNames = getSearchNames(WbSchemaReport.ARG_INCLUDE_TRIGGERS);
    if (trgNames != null)
    {
      TriggerReader reader = TriggerReaderFactory.createReader(currentConnection);
      for (String schema : schemas)
      {
        if (isCancelled) break;
        String catalogToUse = treatSchemaAsCatalog ? schema : catalog;
        String schemaToUse = treatSchemaAsCatalog ? null : schema;

        for (String searchName : trgNames)
        {
          List<TriggerDefinition> triggers = reader.getTriggerList(catalogToUse, schemaToUse, searchName);
          objects.addAll(triggers);
        }
      }
    }

    if (isCancelled)
    {
      result.setWarning();
      return result;
    }

    WbFile output = evaluateFileArgument(cmdLine.getValue(CommonArgs.ARG_FILE));

    scripter = new ObjectScripter(objects, currentConnection);
    scripter.setUseSeparator(cmdLine.getBoolean(ARG_USE_SEPARATOR, DbExplorerSettings.getGenerateScriptSeparator()));
    scripter.setIncludeDrop(cmdLine.getBoolean(ARG_INCLUDE_DROP, false));
    scripter.setIncludeGrants(cmdLine.getBoolean(WbSchemaReport.ARG_INCLUDE_GRANTS, true));
    scripter.setIncludeForeignKeys(cmdLine.getBoolean(ARG_INCLUDE_FK, true));
    scripter.setIncludeCommit(cmdLine.getBoolean(ARG_INCLUDE_COMMIT, true));

    String delimDef = cmdLine.getValue(ARG_STMT_DELIMITER);
    DelimiterDefinition delim = DelimiterDefinition.parseCmdLineArgument(delimDef);
    scripter.setDelimiterToUse(delim);

    if (this.rowMonitor != null)
    {
      rowMonitor.saveCurrentType(VERB);
      rowMonitor.setMonitorType(RowActionMonitor.MONITOR_PROCESS_TABLE);
    }
    scripter.setProgressMonitor(this);

    StringBuilderOutput script = new StringBuilderOutput(objects.size() * 250);
    scripter.setTextOutput(script);
    try
    {
      scripter.generateScript();
    }
    finally
    {
      if (rowMonitor != null)
      {
        rowMonitor.restoreType(VERB);
        rowMonitor.jobFinished();
      }
    }

    if (isCancelled)
    {
      result.setWarning();
      return result;
    }

    result.setSuccess();

    if (output != null)
    {
      try
      {
        String encoding = cmdLine.getValue(CommonArgs.ARG_ENCODING, EncodingUtil.getDefaultEncoding());
        FileUtil.writeString(output, script.toString(), encoding, false);
        result.addMessageByKey("MsgScriptWritten", output.getAbsolutePath());
      }
      catch (IOException io)
      {
        LogMgr.logError(new CallerInfo(){}, "Could not write outputfile", io);
        result.setFailure();
        result.addMessage(io.getLocalizedMessage());
      }
    }
    else
    {
      result.addMessage(script.toString());
    }
    return result;
  }

  private List<String> getSearchNames(String argName)
  {
    List<String> names = null;

    String arg = cmdLine.getValue(argName, null);
    if (StringUtil.isBlank(arg)) return names;

    if (StringUtil.isBoolean(arg))
    {
      if (StringUtil.stringToBool(arg))
      {
        names = CollectionUtil.arrayList("*");
      }
    }
    else
    {
      names = StringUtil.stringToList(arg, ",", true, true, false, false);
    }
    return names;
  }

  @Override
  public void cancel()
    throws SQLException
  {
    super.cancel();
    if (scripter != null)
    {
      scripter.cancel();
    }
  }

  @Override
  public String getVerb()
  {
    return VERB;
  }

  @Override
  public String getAlternateVerb()
  {
    return SHORT_VERB;
  }

  @Override
  public void done()
  {
    scripter = null;
  }

  @Override
  public void setCurrentObject(String anObject, int current, int count)
  {
    if (this.rowMonitor != null)
    {
      if (anObject.indexOf(' ') > -1)
      {
        try
        {
          rowMonitor.saveCurrentType("gen2");
          rowMonitor.setMonitorType(RowActionMonitor.MONITOR_PLAIN);
          rowMonitor.setCurrentObject(anObject, current, count);
        }
        finally
        {
          rowMonitor.restoreType("gen2");
        }
      }
      else
      {
        rowMonitor.setCurrentObject(anObject, current, count);
      }
    }
  }

  @Override
  public boolean isWbCommand()
  {
    return true;
  }

}
