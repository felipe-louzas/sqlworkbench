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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import workbench.WbManager;
import workbench.interfaces.ImportFileParser;
import workbench.interfaces.TabularDataParser;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.ColumnIdentifier;
import workbench.db.DBID;
import workbench.db.WbConnection;
import workbench.db.exporter.BlobMode;
import workbench.db.exporter.OdfHelper;
import workbench.db.exporter.PoiHelper;
import workbench.db.importer.AbstractXmlDataFileParser;
import workbench.db.importer.ConstantColumnValues;
import workbench.db.importer.CycleErrorException;
import workbench.db.importer.DataImporter;
import workbench.db.importer.DeleteType;
import workbench.db.importer.GenericXmlFileParser;
import workbench.db.importer.ImportDMLStatementBuilder;
import workbench.db.importer.ImportFileLister;
import workbench.db.importer.ImportMode;
import workbench.db.importer.OverrideIdentityType;
import workbench.db.importer.ParsingInterruptedException;
import workbench.db.importer.RowDataProducer;
import workbench.db.importer.SpreadsheetFileParser;
import workbench.db.importer.TableStatements;
import workbench.db.importer.TextFileParser;
import workbench.db.importer.WbXmlDataFileParser;
import workbench.db.postgres.PgCopyManager;

import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;

import workbench.util.ArgumentParser;
import workbench.util.ArgumentType;
import workbench.util.ArgumentValue;
import workbench.util.CollectionUtil;
import workbench.util.ExceptionUtil;
import workbench.util.FileUtil;
import workbench.util.StringUtil;
import workbench.util.ValueConverter;
import workbench.util.WbFile;

/**
 *
 * @author Thomas Kellerer
 */
public class WbImport
  extends SqlCommand
{
  public static final String VERB = "WbImport";

  public static final String ARG_TYPE = "type";
  public static final String ARG_FILE = "file";
  public static final String ARG_TARGETTABLE = "table";
  public static final String ARG_CONTAINSHEADER = "header";
  public static final String ARG_FILECOLUMNS = "fileColumns";
  public static final String ARG_KEYCOLUMNS = "keyColumns";
  public static final String ARG_EMPTY_STRING_IS_NULL = "emptyStringIsNull";
  public static final String ARG_DECODE = "decode";
  public static final String ARG_IMPORTCOLUMNS = "importColumns";
  public static final String ARG_COL_FILTER = "columnFilter";
  public static final String ARG_LINE_FILTER = "lineFilter";
  public static final String ARG_DIRECTORY = "sourceDir";
  public static final String ARG_USE_TRUNCATE = "useTruncate";
  public static final String ARG_TRIM_VALUES = "trimValues";
  public static final String ARG_FILE_EXT = "extension";
  public static final String ARG_UPDATE_WHERE = "updateWhere";
  public static final String ARG_CREATE_TABLE = "createTarget";
  public static final String ARG_BLOB_ISFILENAME = "blobIsFilename";
  public static final String ARG_CLOB_ISFILENAME = "clobIsFilename";
  public static final String ARG_MULTI_LINE = "multiLine";
  public static final String ARG_START_ROW = "startRow";
  public static final String ARG_END_ROW = "endRow";
  public static final String ARG_BADFILE = "badFile";
  public static final String ARG_CONSTANTS = "constantValues";
  public static final String ARG_COL_WIDTHS = "columnWidths";
  public static final String ARG_IGNORE_OWNER = "ignoreOwner";
  public static final String ARG_EXCLUDE_FILES = "excludeFiles";
  public static final String ARG_USE_SAVEPOINT = "useSavepoint";
  public static final String ARG_INSERT_START = "insertSQL";
  public static final String ARG_ILLEGAL_DATE_NULL = "illegalDateIsNull";
  public static final String ARG_READ_DATES_AS_STRINGS = "stringDates";
  public static final String ARG_READ_NUMBERS_AS_STRINGS = "stringNumbers";
  public static final String ARG_EMPTY_FILE = "emptyFile";
  public static final String ARG_PG_COPY = "usePgCopy";
  public static final String ARG_SHEET_NR = "sheetNumber";
  public static final String ARG_SHEET_NAME = "sheetName";
  public static final String ARG_IGNORE_MISSING_COLS = "ignoreMissingColumns";
  public static final String ARG_ADJUST_SEQ = "adjustSequences";
  public static final String ARG_RECALC_FORMULAS = "recalculateFormulas";
  public static final String ARG_SHEET_TABLE_NAME_MAP = "sheetTableName";
  public static final String ARG_XML_ROW_TAG = "xmlRowTag";
  public static final String ARG_XML_ATT_MAPPING = "xmlAttCol";
  public static final String ARG_XML_COL_TAGS = "xmlTagCol";
  public static final String ARG_COLUMN_EXPR = "columnExpression";
  public static final String ARG_OVERRIDE_IDENTITY = "overrideIdentity";

  private DataImporter imp;

  public WbImport()
  {
    super();
    this.isUpdatingCommand = true;
    this.cmdLine = new ArgumentParser();
    initArgumentParser();
  }

  @Override
  public void setConnection(WbConnection conn)
  {
    super.setConnection(conn);
    cmdLine.clear();
    initArgumentParser();
  }

  private void initArgumentParser()
  {
    CommonArgs.addDelimiterParameter(cmdLine);
    CommonArgs.addEncodingParameter(cmdLine);
    CommonArgs.addProgressParameter(cmdLine);
    CommonArgs.addCommitParameter(cmdLine);
    CommonArgs.addContinueParameter(cmdLine);
    CommonArgs.addCommitAndBatchParams(cmdLine);
    CommonArgs.addQuoteEscaping(cmdLine);
    CommonArgs.addConverterOptions(cmdLine, true);
    CommonArgs.addCheckDepsParameter(cmdLine);
    CommonArgs.addTableStatements(cmdLine);
    CommonArgs.addTransactionControL(cmdLine);
    CommonArgs.addImportModeParameter(cmdLine);

    List<String> types = CollectionUtil.arrayList("text", "xml");
    types.addAll(getSupportedSpreadSheetTypes());

    cmdLine.addArgument(ARG_TYPE, types);
    cmdLine.addArgument(ARG_IGNORE_MISSING_COLS, ArgumentType.BoolArgument);
    cmdLine.addArgument(ARG_SHEET_NR);
    cmdLine.addArgument(ARG_SHEET_NAME);
    cmdLine.addArgument(ARG_EMPTY_FILE, EmptyImportFileHandling.class);
    cmdLine.addArgument(ARG_UPDATE_WHERE);
    cmdLine.addArgument(ARG_FILE, ArgumentType.Filename);
    cmdLine.addArgument(ARG_TARGETTABLE, ArgumentType.TableArgument);
    cmdLine.addArgument(CommonArgs.ARG_QUOTE_CHAR);
    cmdLine.addArgument(ARG_CONTAINSHEADER, ArgumentType.BoolArgument);
    cmdLine.addArgument(ARG_FILECOLUMNS);
    cmdLine.addArgument(ARG_KEYCOLUMNS);
    cmdLine.addArgument(CommonArgs.ARG_DELETE_TARGET, ArgumentType.BoolArgument);
    cmdLine.addArgument(CommonArgs.ARG_TRUNCATE_TABLE, ArgumentType.BoolArgument);
    cmdLine.addArgument(ARG_EMPTY_STRING_IS_NULL, ArgumentType.BoolArgument);
    cmdLine.addArgument(ARG_DECODE, ArgumentType.BoolArgument);
    cmdLine.addArgument(ARG_IMPORTCOLUMNS);
    cmdLine.addArgument(ARG_COL_FILTER);
    cmdLine.addArgument(ARG_LINE_FILTER);
    cmdLine.addArgument(ARG_DIRECTORY, ArgumentType.DirName);
    cmdLine.addArgument(CommonArgs.ARG_SCHEMA, ArgumentType.SchemaArgument);
    cmdLine.addArgument(ARG_USE_TRUNCATE, ArgumentType.BoolArgument);
    cmdLine.addArgument(ARG_ILLEGAL_DATE_NULL, ArgumentType.BoolArgument);
    cmdLine.addArgument(ARG_TRIM_VALUES, ArgumentType.BoolArgument);
    cmdLine.addArgument(ARG_FILE_EXT);
    cmdLine.addArgument(ARG_CREATE_TABLE, ArgumentType.BoolArgument);
    cmdLine.addArgument(CommonArgs.ARG_IGNORE_IDENTITY, ArgumentType.BoolArgument);
    cmdLine.addArgument(ARG_BLOB_ISFILENAME, ArgumentType.BoolArgument);
    cmdLine.addArgument(WbExport.ARG_BLOB_TYPE, CollectionUtil.arrayList("file", "ansi", "base64"));
    if (ImportDMLStatementBuilder.supportsOverrideIdentity(currentConnection))
    {
      cmdLine.addArgument(ARG_OVERRIDE_IDENTITY, OverrideIdentityType.class);
    }
    cmdLine.addArgument(ARG_CLOB_ISFILENAME, ArgumentType.BoolArgument);
    cmdLine.addArgument(ARG_MULTI_LINE, ArgumentType.BoolArgument);
    cmdLine.addArgument(ARG_START_ROW, ArgumentType.IntegerArgument);
    cmdLine.addArgument(ARG_END_ROW, ArgumentType.IntegerArgument);
    cmdLine.addArgument(ARG_BADFILE);
    cmdLine.addArgument(ARG_CONSTANTS, ArgumentType.Repeatable);
    cmdLine.addArgument(ARG_COL_WIDTHS);
    cmdLine.addArgument(ARG_EXCLUDE_FILES);
    cmdLine.addArgument(ARG_IGNORE_OWNER, ArgumentType.BoolArgument);
    cmdLine.addArgument(ARG_USE_SAVEPOINT, ArgumentType.BoolArgument);
    cmdLine.addArgument(WbExport.ARG_QUOTE_ALWAYS);
    cmdLine.addArgument(WbExport.ARG_NULL_STRING);
    cmdLine.addArgument(ARG_INSERT_START);
    cmdLine.addArgument(ARG_PG_COPY, ArgumentType.BoolSwitch);
    cmdLine.addArgument(ARG_ADJUST_SEQ, ArgumentType.BoolSwitch);
    cmdLine.addArgument(WbCopy.PARAM_SKIP_TARGET_CHECK, ArgumentType.BoolSwitch);
    cmdLine.addArgument(ARG_READ_DATES_AS_STRINGS, ArgumentType.BoolArgument);
    cmdLine.addArgument(ARG_READ_NUMBERS_AS_STRINGS, ArgumentType.BoolArgument);
    cmdLine.addArgument(ARG_RECALC_FORMULAS, ArgumentType.BoolArgument);
    cmdLine.addArgument(ARG_XML_ROW_TAG);
    cmdLine.addArgument(ARG_XML_ATT_MAPPING, ArgumentType.Repeatable);
    cmdLine.addArgument(ARG_XML_COL_TAGS, ArgumentType.Repeatable);
    cmdLine.addArgument(ARG_COLUMN_EXPR, ArgumentType.Repeatable);
    cmdLine.addArgument(CommonArgs.ARG_COLUMN_BLOB_MODE, ArgumentType.Repeatable);
    cmdLine.addArgument(ARG_SHEET_TABLE_NAME_MAP, ArgumentType.Repeatable);

    ModifierArguments.addArguments(cmdLine);
    ConditionCheck.addParameters(cmdLine);
  }
  public static List<String> getSupportedSpreadSheetTypes()
  {
    List<String> types = new ArrayList<>(3);
    if (PoiHelper.isPoiAvailable())
    {
      types.add("xls");
    }
    if (PoiHelper.isXLSXAvailable())
    {
      types.add("xlsx");
    }

    if (OdfHelper.isODFAvailable())
    {
      types.add("ods");
    }
    return types;
  }

  @Override
  public String getVerb()
  {
    return VERB;
  }

  private void addWrongParamsMessage(StatementRunnerResult result)
  {
    if (WbManager.getInstance().isBatchMode()) return;
    String msg = getWrongParamsMessage();
    result.addMessageNewLine();
    result.addMessage(msg);

    // only set the failure indicator if it's not already set
    // to avoid overwriting an ErrorDescriptor that is already set
    if (result.isSuccess() || result.getErrorDescriptor() == null)
    {
      result.setFailure();
    }
  }

  private String getWrongParamsMessage()
  {
    String result = ResourceMgr.getString("ErrImportWrongParameters");
    result = StringUtil.replace(result, "%continue_default%", Boolean.toString(getContinueDefault()));
    result = StringUtil.replace(result, "%multiline_default%", Boolean.toString(getMultiDefault()));
    result = StringUtil.replace(result, "%header_default%", Boolean.toString(getHeaderDefault()));
    result = StringUtil.replace(result, "%trim_default%", Boolean.toString(getTrimDefault()));
    result = StringUtil.replace(result, "%default_encoding%", Settings.getInstance().getDefaultDataEncoding());
    boolean useSP = (currentConnection == null ? false : currentConnection.getDbSettings().useSavepointForImport());
    result = result.replace("%savepoint_default%", Boolean.toString(useSP));
    return result;
  }

  static boolean getIgnoreMissingDefault()
  {
    return Settings.getInstance().getBoolProperty("workbench.import.default.ignoremissingcolumns", true);
  }

  static boolean getContinueDefault()
  {
    return Settings.getInstance().getBoolProperty("workbench.import.default.continue", false);
  }

  static boolean getHeaderDefault()
  {
    return Settings.getInstance().getBoolProperty("workbench.import.default.header", true);
  }

  static boolean getMultiDefault()
  {
    return Settings.getInstance().getBoolProperty("workbench.import.default.multilinerecord", false);
  }

  private boolean getTrimDefault()
  {
    return Settings.getInstance().getBoolProperty("workbench.import.default.trimvalues", false);
  }

  @Override
  public StatementRunnerResult execute(final String sqlCommand)
    throws SQLException
  {
    StatementRunnerResult result = createResult(sqlCommand);
    String options = getCommandLine(sqlCommand);
    cmdLine.parse(options);

    if (displayHelp(result))
    {
      return result;
    }

    if (cmdLine.hasUnknownArguments())
    {
      setUnknownMessage(result, cmdLine, getWrongParamsMessage());
      return result;
    }

    if (!cmdLine.hasArguments())
    {
      addWrongParamsMessage(result);
      return result;
    }

    if (!checkConditions(result))
    {
      return result;
    }

    imp = new DataImporter();
    imp.setConnection(currentConnection);

    WbFile inputFile = evaluateFileArgument(cmdLine.getValue(ARG_FILE));
    String type = cmdLine.getValue(ARG_TYPE);
    String dir = cmdLine.getValue(ARG_DIRECTORY);
    String defaultExtension = null;

    if (inputFile == null && dir == null)
    {
      result.addErrorMessageByKey("ErrImportFileMissing");
      addWrongParamsMessage(result);
      return result;
    }

    if (type == null && inputFile != null)
    {
      type = findTypeFromFilename(inputFile.getFullPath());
    }

    if (type == null)
    {
      result.addErrorMessageByKey("ErrImportTypeMissing");
      addWrongParamsMessage(result);
      return result;
    }

    type = type.toLowerCase();

    Set<String> validTypes = CollectionUtil.caseInsensitiveSet("txt");
    for (ArgumentValue val : cmdLine.getAllowedValues(ARG_TYPE))
    {
      validTypes.add(val.getValue());
    }

    if (!validTypes.contains(type))
    {
      result.addErrorMessageByKey("ErrImportInvalidType");
      return result;
    }

    boolean multiFileImport = (dir != null && inputFile == null);

    String badFile = cmdLine.getValue(ARG_BADFILE);
    if (badFile != null)
    {
      File bf = new File(badFile);
      if (multiFileImport && !bf.isDirectory())
      {
        result.addErrorMessageByKey("ErrImportBadFileNoDir");
        return result;
      }
    }
    CommonArgs.setCommitAndBatchParams(imp, cmdLine);

    boolean continueOnError = cmdLine.getBoolean(CommonArgs.ARG_CONTINUE, getContinueDefault());
    imp.setContinueOnError(continueOnError);

    boolean ignoreMissingCols = cmdLine.getBoolean(ARG_IGNORE_MISSING_COLS, getIgnoreMissingDefault());

    imp.setUseSavepoint(cmdLine.getBoolean(ARG_USE_SAVEPOINT, currentConnection.getDbSettings().useSavepointForImport()));
    imp.setIgnoreIdentityColumns(cmdLine.getBoolean(CommonArgs.ARG_IGNORE_IDENTITY, false));
    imp.setAdjustSequences(cmdLine.getBoolean(ARG_ADJUST_SEQ, false));
    imp.setOverrideIdentity(cmdLine.getEnumValue(ARG_OVERRIDE_IDENTITY, OverrideIdentityType.class));

    boolean skipTargetCheck = cmdLine.getBoolean(WbCopy.PARAM_SKIP_TARGET_CHECK, false);
    imp.skipTargetCheck(skipTargetCheck);

    String table = cmdLine.getValue(ARG_TARGETTABLE);
    String schema = cmdLine.getValue(CommonArgs.ARG_SCHEMA);

    EmptyImportFileHandling emptyHandling = null;

    final CallerInfo ci = new CallerInfo(){};

    try
    {
      emptyHandling = cmdLine.getEnumValue(ARG_EMPTY_FILE, EmptyImportFileHandling.fail);
    }
    catch (IllegalArgumentException iae)
    {
      String emptyValue = cmdLine.getValue(ARG_EMPTY_FILE);
      LogMgr.logError(ci, "Invalid value '" + emptyValue + "' specified for parameter: " + ARG_EMPTY_FILE, iae);
      String msg = ResourceMgr.getFormattedString("ErrInvalidArgValue", emptyValue, ARG_EMPTY_FILE);
      result.addErrorMessage(msg);
      return result;
    }

    if (inputFile != null)
    {
      if (!inputFile.exists())
      {
        String msg = ResourceMgr.getFormattedString("ErrImportFileNotFound", inputFile.getFullPath());

        result.addMessage(msg);
        if (continueOnError)
        {
          LogMgr.logWarning(ci, msg, null);
          result.setWarning();
        }
        else
        {
          LogMgr.logError(ci, msg, null);
          result.setFailure();
        }
        return result;
      }

      if (inputFile.length() == 0 && emptyHandling != EmptyImportFileHandling.ignore)
      {
        String msg = ResourceMgr.getFormattedString("ErrImportFileEmpty", inputFile.getFullPath());
        result.addMessage(msg);
        if (continueOnError || emptyHandling == EmptyImportFileHandling.warning)
        {
          LogMgr.logWarning(ci, msg, null);
          result.setWarning();
        }
        else
        {
          LogMgr.logError(ci, msg, null);
          result.setFailure();
        }
        return result;
      }
    }
    else
    {
      WbFile d = evaluateFileArgument(dir);
      if (!d.exists())
      {
        String msg = ResourceMgr.getFormattedString("ErrImportSourceDirNotFound", dir);
        LogMgr.logError(ci, msg, null);
        result.addErrorMessage(msg);
        return result;
      }
      if (!d.isDirectory())
      {
        String msg = ResourceMgr.getFormattedString("ErrImportNoDir", dir);
        LogMgr.logError(ci, msg, null);
        result.addErrorMessage(msg);
        return result;
      }
    }

    String value = cmdLine.getValue(CommonArgs.ARG_PROGRESS);
    if (value == null && inputFile != null)
    {
      int batchSize = imp.getBatchSize();
      if (batchSize > 0)
      {
        imp.setReportInterval(batchSize);
      }
      else
      {
        int interval = DataImporter.estimateReportIntervalFromFileSize(inputFile);
        imp.setReportInterval(interval);
      }
    }
    else if ("true".equalsIgnoreCase(value))
    {
      this.imp.setReportInterval(1);
    }
    else if ("false".equalsIgnoreCase(value))
    {
      this.imp.setReportInterval(0);
    }
    else if (value != null)
    {
      int interval = StringUtil.getIntValue(value, 0);
      this.imp.setReportInterval(interval);
    }
    else
    {
      this.imp.setReportInterval(10);
    }

    String encoding = cmdLine.getValue(CommonArgs.ARG_ENCODING);
    ImportFileParser parser = null;

    String importMode = cmdLine.getValue(CommonArgs.ARG_IMPORT_MODE, ImportMode.insert.name());

    if ("text".equalsIgnoreCase(type) || "txt".equalsIgnoreCase(type))
    {
      if (table == null && dir == null)
      {
        String msg = ResourceMgr.getString("ErrImportRequiresTableName");
        LogMgr.logError(ci, msg, null);
        result.addErrorMessage(msg);
        return result;
      }

      if (!CommonArgs.checkQuoteEscapting(cmdLine))
      {
        String msg = ResourceMgr.getString("ErrQuoteAlwaysEscape");
        LogMgr.logError(ci, msg, null);
        result.addErrorMessage(msg);
        return result;
      }

      defaultExtension = "txt";

      TextFileParser textParser = new TextFileParser();
      parser = textParser;

      textParser.setTableName(table);

      if (inputFile != null)
      {
        textParser.setInputFile(inputFile);
      }

      boolean multi = cmdLine.getBoolean(ARG_MULTI_LINE, getMultiDefault());
      textParser.setEnableMultilineRecords(multi);
      textParser.setTargetSchema(schema);
      textParser.setConnection(currentConnection);
      textParser.setTreatClobAsFilenames(cmdLine.getBoolean(ARG_CLOB_ISFILENAME, false));
      textParser.setNullString(cmdLine.getValue(WbExport.ARG_NULL_STRING, null));
      textParser.setAlwaysQuoted(cmdLine.getBoolean(WbExport.ARG_QUOTE_ALWAYS, false));
      textParser.setIllegalDateIsNull(cmdLine.getBoolean(ARG_ILLEGAL_DATE_NULL, false));
      textParser.setAbortOnError(!continueOnError);
      textParser.setIgnoreMissingColumns(ignoreMissingCols);

      String delimiter = cmdLine.getEscapedString(CommonArgs.ARG_DELIM);
      if (cmdLine.isArgPresent(CommonArgs.ARG_DELIM) && StringUtil.isEmpty(delimiter))
      {
        result.addErrorMessageByKey("ErrImpDelimEmpty");
        return result;
      }

      if (delimiter != null) textParser.setTextDelimiter(delimiter);

      String quote = cmdLine.getEscapedString(CommonArgs.ARG_QUOTE_CHAR);
      if (quote != null) textParser.setTextQuoteChar(quote);

      textParser.setDecode(cmdLine.getBoolean(ARG_DECODE, false));

      if (encoding != null) textParser.setEncoding(encoding);

      textParser.setEmptyStringIsNull(cmdLine.getBoolean(ARG_EMPTY_STRING_IS_NULL, true));

      initParser(table, textParser, result, multiFileImport, skipTargetCheck);
      if (!result.isSuccess())
      {
        textParser.done();
        return result;
      }

      String filter = cmdLine.getValue(ARG_LINE_FILTER);
      if (filter != null)
      {
        textParser.setLineFilter(StringUtil.trimQuotes(filter));
      }
      textParser.setQuoteEscaping(CommonArgs.getQuoteEscaping(cmdLine));

      // when all columns are defined we can check for a fixed-width import
      String width = cmdLine.getValue(ARG_COL_WIDTHS);
      if (!StringUtil.isEmpty(width))
      {
        try
        {
          ColumnWidthDefinition def = new ColumnWidthDefinition(width);
          textParser.setColumnWidths(def.getColumnWidths());
        }
        catch (MissingWidthDefinition e)
        {
          textParser.done();
          result.addErrorMessageByKey("ErrImpWrongWidth", e.getColumnName());
          return result;
        }
      }

      if (cmdLine.isArgPresent(ARG_PG_COPY) && DBID.Postgres.isDB(currentConnection))
      {
        PgCopyManager pg = new PgCopyManager(currentConnection);
        Set<String> allowedModes = CollectionUtil.caseInsensitiveSet(ImportMode.insert.getArgumentString());
        if (pg.supportsInsertIgnore())
        {
          allowedModes.add(ImportMode.insertIgnore.getArgumentString());
        }

        if (!allowedModes.contains(importMode) )
        {
          String modes = allowedModes.stream().map(m -> "-mode=" + m).collect(Collectors.joining(", "));
          result.addErrorMessage("COPY only possible with " + modes);
          return result;
        }
        if (pg.isSupported())
        {
          pg.setIgnoreErrors(ImportMode.insertIgnore.getArgumentString().equalsIgnoreCase(importMode));
          textParser.setStreamImporter(pg);
          imp.setReportInterval(0);
        }
        else
        {
          result.addWarning("PostgreSQL copy API not supported!");
        }
      }
    }
    else if ("xml".equalsIgnoreCase(type))
    {
      defaultExtension = "xml";

      AbstractXmlDataFileParser xmlParser;

      String rowTag = cmdLine.getValue(ARG_XML_ROW_TAG);

      if (StringUtil.isBlank(rowTag))
      {
        xmlParser = new WbXmlDataFileParser();
      }
      else
      {
        if (table == null)
        {
          String msg = ResourceMgr.getString("ErrImportRequiresTableName");
          LogMgr.logError(ci, msg, null);
          result.addErrorMessage(msg);
          return result;
        }

        GenericXmlFileParser gparser = new GenericXmlFileParser();
        if (cmdLine.isArgPresent(ARG_XML_COL_TAGS))
        {
          Map<String, String> tag2ColumnMap = cmdLine.getMapValue(ARG_XML_COL_TAGS);
          gparser.setRowAndColumnTags(rowTag, tag2ColumnMap);
        }
        else
        {
          Map<String, String> attribute2ColumnMap = cmdLine.getMapValue(ARG_XML_ATT_MAPPING);
          gparser.setRowAttributeMap(rowTag, attribute2ColumnMap);
        }
        xmlParser = gparser;
      }
      xmlParser.setConnection(currentConnection);
      xmlParser.setAbortOnError(!continueOnError);
      xmlParser.setIgnoreMissingColumns(ignoreMissingCols);
      xmlParser.setNullString(cmdLine.getValue(WbExport.ARG_NULL_STRING));

      parser = xmlParser;
      parser.setCheckTargetWithQuery(skipTargetCheck);

      // The encoding must be set as early as possible
      // as data file parser might need it to read
      // the table structure!
      if (encoding != null) xmlParser.setEncoding(encoding);
      if (table != null) xmlParser.setTableName(table);

      if (dir == null)
      {
        xmlParser.setInputFile(inputFile);
        String cols = cmdLine.getValue(ARG_IMPORTCOLUMNS);
        if (cols != null)
        {
          try
          {
            xmlParser.setColumns(cols);
          }
          catch (Exception e)
          {
            String col = StringUtil.listToString(xmlParser.getMissingColumns(), ',');
            String msg = ResourceMgr.getFormattedString("ErrImportColumnNotFound", col, xmlParser.getSourceFilename(), table);
            result.addErrorMessage(msg);
            LogMgr.logError(ci, msg, null);
            return result;
          }
        }
      }
      imp.setCreateTarget(cmdLine.getBoolean(ARG_CREATE_TABLE, false));
    }
    else if (type.startsWith("xls") || type.equals("ods"))
    {
      String snr = cmdLine.getValue(ARG_SHEET_NR, "");
      String sname = cmdLine.getValue(ARG_SHEET_NAME);
      boolean importAllSheets = ("*".equals(snr) || "%".equals(snr) || "*".equals(sname) || "%".equals(sname));

      if (type.startsWith("xls") && !PoiHelper.isPoiAvailable())
      {
        result.addErrorMessageByKey("ErrNoXLS");
        return result;
      }

      if ((type.equals("xlsx") || (inputFile != null && !inputFile.isDirectory() && inputFile.getExtension().equalsIgnoreCase("xlsx")))
           && !PoiHelper.isXLSXAvailable())
      {
        result.addErrorMessageByKey("ErrNoXLSX");
        return result;
      }

      if (type.equals("ods") && !OdfHelper.isODFAvailable())
      {
        result.addErrorMessageByKey("ErrNoODS");
        return result;
      }

      if (table == null && dir == null && !importAllSheets)
      {
        String msg = ResourceMgr.getString("ErrImportRequiresTableName");
        LogMgr.logError(ci, msg, null);
        result.addErrorMessage(msg);
        return result;
      }

      defaultExtension = type;

      SpreadsheetFileParser spreadSheetParser = new SpreadsheetFileParser();
      parser = spreadSheetParser;
      spreadSheetParser.setTableName(table);
      spreadSheetParser.setTargetSchema(schema);
      spreadSheetParser.setConnection(currentConnection);
      spreadSheetParser.setContainsHeader(cmdLine.getBoolean(WbExport.ARG_HEADER, true));
      spreadSheetParser.setNullString(cmdLine.getValue(WbExport.ARG_NULL_STRING, null));
      spreadSheetParser.setReadDatesAsStrings(cmdLine.getBoolean(ARG_READ_DATES_AS_STRINGS, false));
      spreadSheetParser.setReadNumbersAsStrings(cmdLine.getBoolean(ARG_READ_NUMBERS_AS_STRINGS, false));
      spreadSheetParser.setIllegalDateIsNull(cmdLine.getBoolean(ARG_ILLEGAL_DATE_NULL, false));
      spreadSheetParser.setEmptyStringIsNull(cmdLine.getBoolean(ARG_EMPTY_STRING_IS_NULL, true));
      spreadSheetParser.setCheckDependencies(cmdLine.getBoolean(CommonArgs.ARG_CHECK_FK_DEPS, false));
      spreadSheetParser.setIgnoreOwner(cmdLine.getBoolean(ARG_IGNORE_OWNER, false));
      spreadSheetParser.setAbortOnError(!continueOnError);
      spreadSheetParser.setIgnoreMissingColumns(ignoreMissingCols);
      spreadSheetParser.setRecalcFormulas(cmdLine.getBoolean(ARG_RECALC_FORMULAS, true));
      Map<String, String> sheetToTableNames = cmdLine.getMapValue(ARG_SHEET_TABLE_NAME_MAP);
      spreadSheetParser.setSheetTableMap(sheetToTableNames);

      if (inputFile != null)
      {
        if (importAllSheets)
        {
          spreadSheetParser.setSheetIndex(-1);
          multiFileImport = true;
        }
        else if (cmdLine.isArgPresent(ARG_SHEET_NAME))
        {
          // sheet name overrides the index parameter if both are supplied, so test this first
          String name = cmdLine.getValue(ARG_SHEET_NAME);
          spreadSheetParser.setSheetName(name);
        }
        else
        {
          int index = cmdLine.getIntValue(ARG_SHEET_NR, 1);
          // the index is zero-based in POI and ODS, but the user supplies a one-based index
          spreadSheetParser.setSheetIndex(index - 1);
        }
        spreadSheetParser.setInputFile(inputFile);
      }

      initParser(table, spreadSheetParser, result, multiFileImport, skipTargetCheck);
      if (!result.isSuccess())
      {
        spreadSheetParser.done();
        return result;
      }
    }

    // The column filter has to bee applied after the
    // columns are defined!
    String colFilter = cmdLine.getValue(ARG_COL_FILTER);
    if (colFilter != null)
    {
      addColumnFilter(colFilter, parser);
    }

    setBlobModes(parser);

    Map<String, String> columnExpressions = cmdLine.getMapValue(ARG_COLUMN_EXPR);
    imp.setColumnExpressions(columnExpressions);

    imp.setProducer(parser);
    parser.setRowMonitor(this.rowMonitor);
    imp.setInsertStart(cmdLine.getValue(ARG_INSERT_START));

    ImportFileLister lister = getFileNameLister(cmdLine, defaultExtension);
    if (lister != null)
    {
      parser.setSourceFiles(lister);
    }
    parser.setTrimValues(cmdLine.getBoolean(ARG_TRIM_VALUES, getTrimDefault()));

    ValueConverter converter = null;
    try
    {
      converter = CommonArgs.getConverter(cmdLine, result, currentConnection);
    }
    catch (Exception e)
    {
      LogMgr.logError(ci, "Error creating ValueConverter", e);
      result.addErrorMessage(e.getMessage());
      return result;
    }

    imp.setTransactionControl(cmdLine.getBoolean(CommonArgs.ARG_TRANS_CONTROL, true));

    RowDataProducer prod = imp.getProducer();
    if (prod != null)
    {
      prod.setValueConverter(converter);
    }

    try
    {
      // Column Modifiers will only be evaluated for
      // single file imports to avoid confusion of columns
      if (dir == null)
      {
        ModifierArguments args = new ModifierArguments(cmdLine);
        parser.setValueModifier(args.getModifier());
      }
    }
    catch (NumberFormatException e)
    {
      result.addErrorMessageByKey("ErrImportWrongLimit");
      return result;
    }

    if (StringUtil.isNotEmpty(table) && StringUtil.isNotEmpty(dir))
    {
      parser.setMultiFileImport(true);
    }

    if (badFile != null) imp.setBadfileName(badFile);

    List<String> constants = cmdLine.getList(ARG_CONSTANTS);
    if (CollectionUtil.isNonEmpty(constants))
    {
      try
      {
        ConstantColumnValues values = new ConstantColumnValues(constants, this.currentConnection, table, converter);
        imp.setConstantColumnValues(values);
      }
      catch (Exception e)
      {
        LogMgr.logError(ci, "Column constants could no be parsed", e);
        result.addErrorMessage(e.getMessage());
        return result;
      }
    }

    if (!imp.setMode(importMode))
    {
      result.addMessageByKey("ErrInvalidModeIgnored", importMode);
    }

    String where = cmdLine.getValue(ARG_UPDATE_WHERE);
    imp.setWhereClauseForUpdate(where);

    String keyColumns = cmdLine.getValue(ARG_KEYCOLUMNS);
    imp.setKeyColumns(keyColumns);

    if (!parser.isMultiFileImport())
    {
      DeleteType delete = CommonArgs.getDeleteType(cmdLine);
      imp.setDeleteTarget(delete);
    }

    int startRow = cmdLine.getIntValue(ARG_START_ROW, -1);
    if (startRow > 0) imp.setStartRow(startRow);

    int endRow = cmdLine.getIntValue(ARG_END_ROW, -1);
    if (endRow > 0) imp.setEndRow(endRow);

    imp.setRowActionMonitor(this.rowMonitor);
    imp.setPerTableStatements(new TableStatements(cmdLine));

    try
    {
      imp.startImport();
      if (imp.isSuccess())
      {
        result.setSuccess();
      }
      else
      {
        result.setFailure();
      }
      if (imp.hasWarnings())
      {
        result.setWarning();
      }
    }
    catch (CycleErrorException | ParsingInterruptedException e)
    {
      // Logging already done.
      result.setFailure();
    }
    catch (Exception e)
    {
      LogMgr.logError(ci, "Error importing " + (inputFile == null ? dir : inputFile), e);
      result.setFailure();
      addErrorInfo(result, sqlCommand, e);
    }

    result.addMessage(imp.getMessages());

    if (!result.isSuccess() && lister != null)
    {
      appendRestartMessage(parser, result);
    }
    return result;
  }

  private void setBlobModes(ImportFileParser parser)
  {
    String btype = cmdLine.getValue(WbExport.ARG_BLOB_TYPE);
    BlobMode mode = BlobMode.getMode(btype);
    if (btype != null && mode != null)
    {
      parser.setDefaultBlobMode(mode);
    }
    else if (cmdLine.isArgPresent(ARG_BLOB_ISFILENAME))
    {
      boolean flag = cmdLine.getBoolean(ARG_BLOB_ISFILENAME, true);
      if (flag)
      {
        parser.setDefaultBlobMode(BlobMode.SaveToFile);
      }
      else
      {
        parser.setDefaultBlobMode(BlobMode.None);
      }
    }

    Map<String, BlobMode> blobModes = CommonArgs.getColumnBlobModes(cmdLine);
    parser.setColumnBlobModes(blobModes);
  }

  private void initParser(String tableName, TabularDataParser parser, StatementRunnerResult result, boolean isMultifile, boolean skipTargetCheck)
  {
    boolean headerDefault = Settings.getInstance().getBoolProperty("workbench.import.default.header", true);
    boolean header = cmdLine.getBoolean(ARG_CONTAINSHEADER, headerDefault);

    String filecolumns = cmdLine.getValue(ARG_FILECOLUMNS);

    // The flag for a header lines must be specified before setting the columns
    parser.setContainsHeader(header);

    if (StringUtil.isBlank(tableName))
    {
      return;
    }

    String importcolumns = cmdLine.getValue(ARG_IMPORTCOLUMNS);
    List<ColumnIdentifier> toImport = null;
    if (StringUtil.isNotBlank(importcolumns))
    {
      toImport = stringToCols(importcolumns);
    }

    parser.setCheckTargetWithQuery(skipTargetCheck);

    try
    {
      parser.checkTargetTable();
    }
    catch (Exception e)
    {
      result.addMessage(parser.getMessages());
      result.setFailure();
      return;
    }

    // don't check the columns if this is a multi-file import
    if (isMultifile && StringUtil.isBlank(filecolumns) && CollectionUtil.isEmpty(toImport)) return;

    // read column definition from header line
    // if no header was specified, the text parser
    // will assume the columns in the text file
    // map to the column in the target table
    try
    {
      if (StringUtil.isBlank(filecolumns))
      {
        parser.setupFileColumns(toImport);
      }
      else
      {
        List<ColumnIdentifier> fileCols = stringToCols(filecolumns);
        parser.setColumns(fileCols, toImport);
      }
    }
    catch (Exception e)
    {
      result.setFailure();
      result.addMessage(parser.getMessages());
      LogMgr.logError(new CallerInfo(){}, ExceptionUtil.getDisplay(e), null);
    }
  }

  private void appendRestartMessage(ImportFileParser importer, StatementRunnerResult result)
  {
    List<File> files = importer.getProcessedFiles();
    if (CollectionUtil.isEmpty(files)) return;
    StringBuilder param = new StringBuilder(files.size() * 15);
    param.append('-');
    param.append(ARG_EXCLUDE_FILES);
    param.append('=');
    boolean first = true;
    for (File f : files)
    {
      if (first) first = false;
      else param.append(',');
      param.append(f.getName());
    }
    result.addMessageNewLine();
    result.addMessageByKey("ErrImportRestartWith");
    result.addMessage(param);
  }

  private List<ColumnIdentifier> stringToCols(String columns)
  {
    List<String> names = StringUtil.stringToList(columns, ",", true, true);
    List<ColumnIdentifier> cols = new ArrayList<>(names.size());
    for (String name : names)
    {
      cols.add(new ColumnIdentifier(name));
    }
    return cols;
  }

  private ImportFileLister getFileNameLister(ArgumentParser cmdLine, String defaultExt)
  {
    String fname = cmdLine.getValue(ARG_FILE);
    String dir = cmdLine.getValue(ARG_DIRECTORY);
    if (StringUtil.isEmpty(dir) && StringUtil.isEmpty(fname)) return null;

    ImportFileLister lister = null;

    if (FileUtil.hasWildcard(fname))
    {
      WbFile f = evaluateFileArgument(fname);
      lister = new ImportFileLister(this.currentConnection, f.getFullPath());
    }
    else if (dir != null)
    {
      String ext = cmdLine.getValue(ARG_FILE_EXT);
      if (ext == null) ext = defaultExt;

      WbFile fdir = evaluateFileArgument(dir);
      lister = new ImportFileLister(this.currentConnection, fdir, ext);
    }

    if (lister != null)
    {
      lister.setIgnoreSchema(cmdLine.getBoolean(ARG_IGNORE_OWNER, false));
      lister.ignoreFiles(cmdLine.getListValue(ARG_EXCLUDE_FILES));
      lister.setCheckDependencies(cmdLine.getBoolean(CommonArgs.ARG_CHECK_FK_DEPS, false));
    }
    return lister;
  }

  private void addColumnFilter(String filters, ImportFileParser parser)
  {
    List<String> filterList = StringUtil.stringToList(filters, ",", false);

    if (filterList.size() < 1) return;

    for (String filterDef : filterList)
    {
      List<String> l = StringUtil.stringToList(filterDef, "=", true);
      if (l.size() != 2) continue;

      String col = l.get(0);
      String regex = l.get(1);
      parser.addColumnFilter(col, StringUtil.trimQuotes(regex));
    }
  }

  @Override
  public void done()
  {
    super.done();
    this.imp = null;
  }

  @Override
  public void cancel()
    throws SQLException
  {
    super.cancel();
    if (this.imp != null)
    {
      this.imp.cancelExecution();
    }
  }

  public static String findTypeFromFilename(String fname)
  {
    if (fname == null) return null;
    String name = fname.toLowerCase();
    if (name.endsWith(".txt")) return "text";
    if (name.endsWith(".xml")) return "xml";
    if (name.endsWith(".text")) return "text";
    if (name.endsWith(".csv")) return "text";
    if (name.endsWith(".tsv")) return "text";
    if (name.endsWith(".xls")) return "xls";
    if (name.endsWith(".xlsx")) return "xlsx";
    if (name.endsWith(".ods")) return "ods";
    return null;
  }

  @Override
  public boolean isWbCommand()
  {
    return true;
  }
}
