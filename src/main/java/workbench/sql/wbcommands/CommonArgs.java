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

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import workbench.interfaces.BatchCommitter;
import workbench.interfaces.Committer;
import workbench.interfaces.ProgressReporter;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.DropType;
import workbench.db.WbConnection;
import workbench.db.exporter.BlobMode;
import workbench.db.importer.DeleteType;
import workbench.db.importer.ImportMode;

import workbench.sql.StatementRunnerResult;

import workbench.util.ArgumentParser;
import workbench.util.ArgumentType;
import workbench.util.CollectionUtil;
import workbench.util.QuoteEscapeType;
import workbench.util.StringUtil;
import workbench.util.ValueConverter;

/**
 * A class to manage common parameters for various WbCommands.
 * <p>
 * When adding the parameters to the ArgumentParser, the necessary
 * values for the code completion are also supplied.
 * For parameters with no fixed set of values a sample list
 * with popular (or so I think) values is added, e.g. the -encoding
 * parameter.
 *
 * @author Thomas Kellerer
 */
public class CommonArgs
{
  public static final String ARG_PROGRESS = "showProgress";
  public static final String ARG_ENCODING = "encoding";
  public static final String ARG_COMMIT_EVERY = "commitEvery";
  public static final String ARG_DELIM = "delimiter";
  public static final String ARG_VERBOSE_XML = "verboseXML";
  public static final String ARG_IMPORT_MODE = "mode";
  public static final String ARG_CONTINUE = "continueOnError";
  public static final String ARG_BATCHSIZE = "batchSize";
  public static final String ARG_COMMIT_BATCH = "commitBatch";
  public static final String ARG_QUOTE_CHAR= "quotechar";
  public static final String ARG_QUOTE_ESCAPE = "quoteCharEscaping";
  public static final String ARG_AUTO_BOOLEAN = "booleanToNumber";
  public static final String ARG_LOCALE = "locale";
  public static final String ARG_DATE_FORMAT = "dateFormat";
  public static final String ARG_TIME_FORMAT = "timeFormat";
  public static final String ARG_TIMESTAMP_FORMAT = "timestampFormat";
  public static final String ARG_TIMESTAMP_TZ_FORMAT = "timestampTZFormat";
  public static final String ARG_DECIMAL_CHAR = "decimal";
  public static final String ARG_DECIMAL_GROUPING = "decimalGroup";
  public static final String ARG_NUMERIC_TRUE = "numericTrue";
  public static final String ARG_NUMERIC_FALSE = "numericFalse";
  public static final String ARG_FALSE_LITERALS = "literalsFalse";
  public static final String ARG_TRUE_LITERALS = "literalsTrue";
  public static final String ARG_CHECK_FK_DEPS = "checkDependencies";
  public static final String ARG_PRE_TABLE_STMT = "preTableStatement";
  public static final String ARG_POST_TABLE_STMT = "postTableStatement";
  public static final String ARG_IGNORE_TABLE_STMT_ERRORS = "ignorePrePostErrors";
  public static final String ARG_RUN_POST_STMT_ON_ERROR = "runTableStatementOnError";
  public static final String ARG_TRANS_CONTROL = "transactionControl";
  public static final String ARG_DATE_LITERAL_TYPE = "sqlDateLiterals";
  public static final String ARG_DELETE_TARGET = "deleteTarget";
  public static final String ARG_TRUNCATE_TABLE = "truncateTable";
  public static final String ARG_SCHEMA = "schema";
  public static final String ARG_CATALOG = "catalog";
  public static final String ARG_TABLES = "tables";
  public static final String ARG_VIEWS = "views";
  public static final String ARG_EXCLUDE_TABLES = "excludeTables";
  public static final String ARG_SCHEMAS = "schemas";
  public static final String ARG_TYPES = "types";
  public static final String ARG_OBJECTS = "objects";
  public static final String ARG_IGNORE_IDENTITY = "ignoreIdentityColumns";
  public static final String ARG_HELP = "help";
  public static final String ARG_VERBOSE = "verbose";
  public static final String ARG_FILE = "file";
  public static final String ARG_OUTPUT_FILE = "outputFile";
  public static final String ARG_OUTPUT_DIR = "outputDir";
  public static final String ARG_COLUMN_BLOB_MODE = "columnBlobType";

  private static List<String> getDelimiterArguments()
  {
    return StringUtil.stringToList("'\\t',';',\"','\",'|',<char>");
  }

  public static void addTransactionControL(ArgumentParser cmdLine)
  {
    cmdLine.addArgument(ARG_TRANS_CONTROL, ArgumentType.BoolArgument);
  }

  public static List<String> getListArgument(ArgumentParser cmdLine, String arg)
  {
    String value = cmdLine.getValue(arg);
    if (StringUtil.isEmpty(value)) return null;
    List<String> items = StringUtil.stringToList(value, ",");
    return items;
  }

  public static DeleteType getDeleteType(ArgumentParser cmdLine)
  {
    if (cmdLine.getBoolean(ARG_TRUNCATE_TABLE, false)) return DeleteType.truncate;
    if (cmdLine.getBoolean(ARG_DELETE_TARGET, false)) return DeleteType.delete;
    return DeleteType.none;
  }

  public static void addTableStatements(ArgumentParser cmdLine)
  {
    cmdLine.addArgument(ARG_PRE_TABLE_STMT);
    cmdLine.addArgument(ARG_POST_TABLE_STMT);
    cmdLine.addArgument(ARG_IGNORE_TABLE_STMT_ERRORS, ArgumentType.BoolSwitch);
    cmdLine.addArgument(ARG_RUN_POST_STMT_ON_ERROR, ArgumentType.BoolSwitch);
  }

  public static void addCheckDepsParameter(ArgumentParser cmdLine)
  {
    cmdLine.addArgument(ARG_CHECK_FK_DEPS, ArgumentType.BoolArgument);
  }

  public static void addContinueParameter(ArgumentParser cmdLine)
  {
    cmdLine.addArgument(ARG_CONTINUE, ArgumentType.BoolArgument);
  }

  public static void addImportModeParameter(ArgumentParser cmdLine)
  {
    cmdLine.addArgument(ARG_IMPORT_MODE, CollectionUtil.arrayList(
      ImportMode.insert.getArgumentString(),
      ImportMode.update.getArgumentString(),
      ImportMode.updateInsert.getArgumentString(),
      ImportMode.insertUpdate.getArgumentString(),
      ImportMode.upsert.getArgumentString(),
      ImportMode.insertIgnore.getArgumentString()));
  }

  public static void addSqlDateLiteralParameter(ArgumentParser cmdLine)
  {
    cmdLine.addArgument(ARG_DATE_LITERAL_TYPE, Settings.getInstance().getLiteralTypeList());
  }

  public static void addVerboseXmlParameter(ArgumentParser cmdLine)
  {
    cmdLine.addArgument(ARG_VERBOSE_XML, ArgumentType.BoolArgument);
  }

  public static void addCommitParameter(ArgumentParser cmdLine)
  {
    cmdLine.addArgument(ARG_COMMIT_EVERY, StringUtil.stringToList("none,atEnd,<number>"));
  }

  public static void addDelimiterParameter(ArgumentParser cmdLine)
  {
    cmdLine.addArgument(ARG_DELIM, getDelimiterArguments());
  }

  public static Map<String, BlobMode> getColumnBlobModes(ArgumentParser cmdLine)
  {
    Map<String, BlobMode> modes = new HashMap<>();
    Map<String, String> values = cmdLine.getMapValue(ARG_COLUMN_BLOB_MODE);
    for (Map.Entry<String, String> entry : values.entrySet())
    {
      String colname = entry.getKey();
      String modeString = entry.getValue();
      BlobMode mode = BlobMode.getMode(modeString);
      modes.put(colname, mode);
    }

    return modes;
  }

  public static boolean checkQuoteEscapting(ArgumentParser cmdLine)
  {
    boolean quoteAlways = cmdLine.getBoolean(WbExport.ARG_QUOTE_ALWAYS, false);
    QuoteEscapeType escape = getQuoteEscaping(cmdLine);
    if (quoteAlways && escape == QuoteEscapeType.duplicate) return false;
    return true;
  }

  /**
   * Adds the quoteCharEscaping argument. Valid values
   * are none, duplicate, escape.
   *
   * @see workbench.util.QuoteEscapeType
   */
  public static void addQuoteEscaping(ArgumentParser cmdLine)
  {
    cmdLine.addArgument(ARG_QUOTE_ESCAPE, StringUtil.stringToList("none,duplicate,escape"));
  }

  /**
   * Adds the -encoding parameter to the ArgumentParser.
   * The encodings that are added to the code completion list
   * are retrieved from the Settings class.
   *
   * @param cmdLine the ArgumentParser to which the parameter should be added
   *
   * @see workbench.resource.Settings#getPopularEncodings()
   */
  public static void addEncodingParameter(ArgumentParser cmdLine)
  {
    cmdLine.addArgument(ARG_ENCODING, StringUtil.stringToList(Settings.getInstance().getPopularEncodings()));
  }

  public static void addProgressParameter(ArgumentParser cmdLine)
  {
    cmdLine.addArgument(ARG_PROGRESS, StringUtil.stringToList("true,false,<number>"));
  }

  public static void setProgressInterval(ProgressReporter reporter, ArgumentParser cmdLine)
  {
    String progress = cmdLine.getValue(ARG_PROGRESS, cmdLine.getValue(ARG_BATCHSIZE));

    if ("true".equalsIgnoreCase(progress))
    {
      reporter.setReportInterval(1);
    }
    else if ("false".equalsIgnoreCase(progress))
    {
      reporter.setReportInterval(0);
    }
    else if (progress != null)
    {
      int interval = StringUtil.getIntValue(progress, 0);
      reporter.setReportInterval(interval);
    }
    else
    {
      reporter.setReportInterval(ProgressReporter.DEFAULT_PROGRESS_INTERVAL);
    }
  }

  public static void setCommitEvery(Committer committer, ArgumentParser cmdLine)
  {
    String commitParam = cmdLine.getValue(ARG_COMMIT_EVERY);
    if (commitParam == null) return;

    if ("none".equalsIgnoreCase(commitParam) || "false".equalsIgnoreCase(commitParam))
    {
      committer.commitNothing();
    }
    else if ("atEnd".equalsIgnoreCase(commitParam))
    {
      committer.setCommitEvery(0);
    }
    else
    {
      committer.setCommitEvery(StringUtil.getIntValue(commitParam, 0));
    }

  }

  public static void addCommitAndBatchParams(ArgumentParser cmdLine)
  {
    cmdLine.addArgument(ARG_BATCHSIZE);
    cmdLine.addArgument(ARG_COMMIT_BATCH, ArgumentType.BoolArgument);
  }

  public static void setCommitAndBatchParams(BatchCommitter committer, ArgumentParser cmdLine)
  {
    int batchSize = cmdLine.getIntValue(ARG_BATCHSIZE, -1);
    String commitParam = cmdLine.getValue(ARG_COMMIT_EVERY);

    if (batchSize > 0)
    {
      committer.setUseBatch(true);
      committer.setBatchSize(batchSize);

      if (cmdLine.isArgPresent(ARG_COMMIT_BATCH))
      {
        committer.setCommitBatch(cmdLine.getBoolean(ARG_COMMIT_BATCH, false));
      }
      else if ("none".equalsIgnoreCase(commitParam) || "false".equalsIgnoreCase(commitParam))
      {
        committer.commitNothing();
      }
    }
    else
    {
      setCommitEvery(committer, cmdLine);
    }
  }

  public static QuoteEscapeType getQuoteEscaping(ArgumentParser cmdLine)
  {
    String esc = cmdLine.getValue(ARG_QUOTE_ESCAPE);
    if (esc != null)
    {
      try
      {
        QuoteEscapeType escapeType = QuoteEscapeType.valueOf(esc.trim().toLowerCase());
        return escapeType;
      }
      catch (Exception e)
      {
        // ignore --> return none
      }
    }
    return QuoteEscapeType.none;
  }

  public static void addConverterOptions(ArgumentParser cmdLine, boolean includeDateFormats)
  {
    cmdLine.addArgument(ARG_AUTO_BOOLEAN, ArgumentType.BoolArgument);
    cmdLine.addArgument(ARG_DECIMAL_CHAR);
    cmdLine.addArgument(ARG_DECIMAL_GROUPING);
    if (includeDateFormats)
    {
      cmdLine.addArgument(ARG_DATE_FORMAT);
      cmdLine.addArgument(ARG_TIME_FORMAT);
      cmdLine.addArgument(ARG_TIMESTAMP_FORMAT);
      cmdLine.addArgument(ARG_TIMESTAMP_TZ_FORMAT);
    }
    cmdLine.addArgument(ARG_FALSE_LITERALS);
    cmdLine.addArgument(ARG_TRUE_LITERALS);
    cmdLine.addArgument(ARG_NUMERIC_FALSE);
    cmdLine.addArgument(ARG_NUMERIC_TRUE);
    cmdLine.addArgument(ARG_LOCALE);
  }

  public static Locale getLocale(ArgumentParser cmdLine, StatementRunnerResult result)
  {
    String localeName = cmdLine.getValue(ARG_LOCALE);
    Locale locale = null;
    if (StringUtil.isNotBlank(localeName))
    {
      try
      {
        if (localeName.contains("_"))
        {
          String[] def = localeName.split("_");
          if (def.length > 1)
          {
            locale = new Locale(def[0], def[1]);
          }
          else
          {
            locale = new Locale(def[0]);
          }
        }
        else
        {
          locale = new Locale(localeName);
        }
        // check if this is valid, it will throw an exception when
        // an invalid language was specified
        locale.getISO3Language();
      }
      catch (Exception ex)
      {
        locale = null;
        LogMgr.logWarning(new CallerInfo(){}, "Illegale locale " + localeName + " ignored", ex);
        String msg = ResourceMgr.getFormattedString("ErrIllegalLocaleIgnore", localeName);
        result.addWarning(msg);
      }
    }
    return locale;
  }

  public static ValueConverter getConverter(ArgumentParser cmdLine, StatementRunnerResult result, WbConnection conn)
    throws IllegalArgumentException
  {
    ValueConverter converter = new ValueConverter(conn);
    converter.setAutoConvertBooleanNumbers(cmdLine.getBoolean(ARG_AUTO_BOOLEAN, true));
    converter.setLocale(getLocale(cmdLine, result));

    String format = null;
    try
    {
      format = cmdLine.getValue(ARG_DATE_FORMAT);
      addMonthWarning(format, result);
      if (format != null) converter.setDefaultDateFormat(format);

      format = cmdLine.getValue(ARG_TIMESTAMP_FORMAT);
      addMonthWarning(format, result);
      if (format != null) converter.setDefaultTimestampFormat(format);

      format = cmdLine.getValue(ARG_TIMESTAMP_TZ_FORMAT);
      if (format != null)
      {
        addMonthWarning(format, result);
        converter.setTimestampTZFormat(format);
      }
      format = cmdLine.getValue(ARG_TIME_FORMAT);
      if (format != null)
      {
        converter.setDefaultTimeFormat(format);
      }
    }
    catch (Exception e)
    {
      String msg = ResourceMgr.getFormattedString("ErrIllegalDateTimeFormat", format);
      throw new IllegalArgumentException(msg, e);
    }

    String decimal = cmdLine.getValue(ARG_DECIMAL_CHAR);
    if (decimal != null) converter.setDecimalCharacter(decimal.charAt(0));
    String groupingChar = cmdLine.getValue(ARG_DECIMAL_GROUPING);
    if (groupingChar != null) converter.setDecimalGroupingChar(groupingChar);

    List<String> falseValues = cmdLine.getListValue(ARG_FALSE_LITERALS);
    List<String> trueValues = cmdLine.getListValue(ARG_TRUE_LITERALS);
    if (falseValues.size() > 0 && trueValues.size() > 0)
    {
      converter.setBooleanLiterals(trueValues, falseValues);
    }

    if (cmdLine.isArgPresent(ARG_NUMERIC_FALSE) && cmdLine.isArgPresent(ARG_NUMERIC_TRUE))
    {
      int trueValue = cmdLine.getIntValue(ARG_NUMERIC_TRUE, 1);
      int falseValue = cmdLine.getIntValue(ARG_NUMERIC_FALSE, 0);
      converter.setNumericBooleanValues(falseValue, trueValue);
      converter.setAutoConvertBooleanNumbers(true);
    }
    return converter;
  }

  private static void addMonthWarning(String format, StatementRunnerResult result)
  {
    if (StringUtil.isBlank(format)) return;
    if (result == null) return;

    Set<String> patterns = Set.of("yyyy-mm-dd", "yyyy/mm/dd", "yyyy.mm.dd",
                                  "dd-mm-yyyy", "dd.mm.yyyy", "dd/mm/yyyy",
                                  "mm/dd/yyyy");
    for (String pattern : patterns)
    {
      if (format.contains(pattern))
      {
        String fixed = format.replace(pattern, pattern.replace("mm", "MM"));
        result.addWarning(ResourceMgr.getFormattedString("MsgMonthLowerCase", fixed));
      }
    }
  }

  public static DropType getDropType(ArgumentParser cmdLine)
  {
    String drop = cmdLine.getValue(WbCopy.PARAM_DROPTARGET, null);
    DropType dropType = DropType.none;
    if (drop != null)
    {
      if (drop.startsWith("cascade"))
      {
        dropType = DropType.cascaded;
      }
      else if (StringUtil.stringToBool(drop))
      {
        dropType = DropType.regular;
      }
    }
    return dropType;
  }

  public static void appendArgument(StringBuilder result, String arg, boolean value, CharSequence indent)
  {
    appendArgument(result, arg, Boolean.toString(value), indent);
  }

  public static void appendArgument(StringBuilder result, String arg, String value, CharSequence indent)
  {
    if (StringUtil.isNotBlank(value))
    {
      if (indent.charAt(0) != '\n')
      {
        result.append('\n');
      }
      result.append(indent);
      result.append('-');
      result.append(arg);
      result.append('=');

      if (value.indexOf('-') > -1 || value.indexOf(';') > -1)
      {
        result.append('"');
      }
      else if ("\"".equals(value))
      {
        result.append('\'');
      }
      else if ("\'".equals(value))
      {
        result.append('\"');
      }

      result.append(value);

      if (value.indexOf('-') > -1 || value.indexOf(';') > -1)
      {
        result.append('"');
      }
      else if ("\"".equals(value))
      {
        result.append('\'');
      }
      else if ("\'".equals(value))
      {
        result.append('\"');
      }
    }
  }

}
