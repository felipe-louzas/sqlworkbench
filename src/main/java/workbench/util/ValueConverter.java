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
package workbench.util;

import java.io.File;
import java.math.BigDecimal;
import java.sql.Types;
import java.text.ParseException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeParseException;
import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.WbConnection;
import workbench.db.exporter.InfinityLiterals;

import workbench.storage.reader.TimestampTZHandler;

/**
 * Utility class to parse Strings into approriate Java classes according
 * to a type from java.sql.Type
 *
 * This class is not thread safe for parsing dates and timestamps due to the
 * fact that SimpleDateFormat is not thread safe.
 *
 * @author  Thomas Kellerer
 */
public class ValueConverter
{
  public static final String DETECT_FIRST = "detect_once";
  public static final String ALWAYS_CHECK_INTERNAL = "detect";

  /**
   *  Often used date formats which are tried when parsing a Date
   *  or a TimeStamp column
   */
  private final List<String> timestampFormats = CollectionUtil.arrayList(
                            "yyyy-MM-dd HH:mm:ss.SSS",
                            "yyyy-MM-dd HH:mm:ss",
                            "yyyy-MM-dd HH:mm",
                            "dd.MM.yyyy HH:mm:ss.SSS",
                            "dd.MM.yyyy HH:mm:ss",
                            "dd.MM.yy HH:mm:ss.SSS",
                            "dd.MM.yy HH:mm:ss",
                            "dd.MM.yy HH:mm",
                            "MM/dd/yyyy HH:mm:ss.SSS",
                            "MM/dd/yyyy HH:mm:ss",
                            "MM/dd/yy HH:mm:ss.SSS",
                            "MM/dd/yy HH:mm:ss",
                            "MM/dd/yy HH:mm",
                            "yyyy-MM-dd",
                            "dd.MM.yyyy",
                            "dd.MM.yy",
                            "MM/dd/yy",
                            "MM/dd/yyyy");

  private final List<String> dateFormats = CollectionUtil.arrayList(
                            "yyyy-MM-dd",
                            "dd.MM.yyyy",
                            "dd.MM.yy",
                            "MM/dd/yy",
                            "MM/dd/yyyy",
                            "dd-MMM-yyyy",
                            "yyyy-MM-dd HH:mm:ss.SSS",
                            "yyyy-MM-dd HH:mm:ss",
                            "dd.MM.yyyy HH:mm:ss",
                            "MM/dd/yy HH:mm:ss",
                            "MM/dd/yyyy HH:mm:ss");

  private final String[] timeFormats = new String[] { "HH:mm:ss.SS", "HH:mm:ss", "HH:mm", "HHmm", "HH" };

  private String defaultDateFormat;
  private String defaultTimestampFormat;
  private String defaultTimeFormat;

  private boolean timestampTZFormatSet = false;
  private char decimalCharacter = '.';
  private String decimalGroupingChar;
  private final WbDateFormatter dateFormatter = new WbDateFormatter();
  private final WbDateFormatter timestampFormatter = new WbDateFormatter();
  private final WbDateFormatter timestampTZFormatter = new WbDateFormatter();
  private final WbDateFormatter formatter = new WbDateFormatter();
  private boolean autoConvertBooleanNumbers = true;
  private final Map<String, Boolean> booleanValues = new HashMap<>();
  private boolean booleanUserMap;

  private Integer integerTrue;
  private Integer integerFalse = 0;
  private Long longTrue = Long.valueOf(1);
  private Long longFalse = Long.valueOf(0);
  private BigDecimal bigDecimalTrue = BigDecimal.valueOf(1);
  private BigDecimal bigDecimalFalse = BigDecimal.valueOf(0);

  private static final String FORMAT_MILLIS = "millis";
  private boolean checkBuiltInFormats = true;
  private boolean useFirstMatching = true;
  private boolean illegalDateIsNull;
  private boolean cleanupNumbers = false;
  private boolean logWarnings = true;

  private TimestampTZHandler tzType = TimestampTZHandler.DUMMY_HANDLER;
  private boolean checkDateTimeValuesDynamically = false;

  public ValueConverter()
  {
    this(null);
    this.integerTrue = 1;
  }

  public ValueConverter(WbConnection conn)
  {
    this.integerTrue = 1;
    Settings sett = Settings.getInstance();
    this.setDefaultDateFormat(sett.getDefaultDateFormat());
    this.setDefaultTimestampFormat(sett.getDefaultTimestampFormat());
    cleanupNumbers = Settings.getInstance().getBoolProperty("workbench.converter.cleanupdecimals", false);
    readConfiguredBooleanValues();
    this.tzType = TimestampTZHandler.Factory.getHandler(conn);
  }

  public void setLogWarnings(boolean flag)
  {
    logWarnings = flag;
  }

  private void readConfiguredBooleanValues()
  {
    List<String> trueValues = Settings.getInstance().getListProperty("workbench.converter.boolean.true", true, "1,t,true");
    List<String> falseValues = Settings.getInstance().getListProperty("workbench.converter.boolean.false", true, "0,f,false");
    fillBooleanMap(trueValues, falseValues);
    booleanUserMap = false;
  }

  private void fillBooleanMap(Collection<String> trueValues, Collection<String> falseValues)
  {
    booleanValues.clear();
    for (String value : trueValues)
    {
      booleanValues.put(value, Boolean.TRUE);
    }
    for (String value : falseValues)
    {
      booleanValues.put(value, Boolean.FALSE);
    }
  }

  public ValueConverter(String aDateFormat, String aTimeStampFormat)
  {
    this.integerTrue = 1;
    if (StringUtil.isEmpty(aDateFormat))
    {
      this.setDefaultDateFormat(Settings.getInstance().getDefaultDateFormat());
    }
    else
    {
      this.setDefaultDateFormat(aDateFormat);
    }

    if (StringUtil.isEmpty(aTimeStampFormat))
    {
      this.setDefaultTimestampFormat(Settings.getInstance().getDefaultTimestampFormat());
    }
    else
    {
      this.setDefaultTimestampFormat(aTimeStampFormat);
    }
  }

  public void setLocale(Locale locale)
  {
    dateFormatter.setLocale(locale);
    timestampFormatter.setLocale(locale);
    formatter.setLocale(locale);
  }

  public void setIllegalDateIsNull(boolean flag)
  {
    this.illegalDateIsNull = flag;
  }

  public void setCheckBuiltInFormats(boolean flag)
  {
    this.checkBuiltInFormats = flag;
  }

  public void setDefaultTimeFormat(String format)
  {
    this.defaultTimeFormat = StringUtil.trimToNull(format);
  }

  public final void setDefaultDateFormat(String dtFormat)
    throws IllegalArgumentException
  {
    if (DETECT_FIRST.equalsIgnoreCase(dtFormat))
    {
      this.defaultDateFormat = null;
      this.checkBuiltInFormats = true;
      this.useFirstMatching = true;
    }
    else if (ALWAYS_CHECK_INTERNAL.equalsIgnoreCase(dtFormat))
    {
      this.defaultDateFormat = null;
      this.checkBuiltInFormats = true;
      this.useFirstMatching = false;
    }
    else if (dtFormat.equalsIgnoreCase(FORMAT_MILLIS))
    {
      this.defaultDateFormat = FORMAT_MILLIS;
      this.checkBuiltInFormats = false;
    }
    else if (StringUtil.isNotEmpty(dtFormat))
    {
      this.checkBuiltInFormats = false;
      this.defaultDateFormat = dtFormat;
      this.dateFormatter.applyPattern(dtFormat);
    }
  }

  public final void setTimestampTZFormat(String format)
  {
    if (StringUtil.isNotBlank(format))
    {
      this.timestampTZFormatSet = true;
      this.timestampTZFormatter.applyPattern(format, true);
    }
  }

  public final void setDefaultTimestampFormat(String tsFormat)
    throws IllegalArgumentException
  {
    if (DETECT_FIRST.equalsIgnoreCase(tsFormat))
    {
      this.defaultTimestampFormat = null;
      this.checkBuiltInFormats = true;
      this.useFirstMatching = true;
    }
    else if (ALWAYS_CHECK_INTERNAL.equalsIgnoreCase(tsFormat))
    {
      this.defaultTimestampFormat = null;
      this.checkBuiltInFormats = true;
      this.useFirstMatching = false;
    }
    else if (tsFormat.equalsIgnoreCase(FORMAT_MILLIS))
    {
      this.defaultTimestampFormat = FORMAT_MILLIS;
      this.checkBuiltInFormats = false;
    }
    else if (StringUtil.isNotEmpty(tsFormat))
    {
      this.checkBuiltInFormats = false;
      this.defaultTimestampFormat = tsFormat;
      this.timestampFormatter.applyPattern(tsFormat, true);
    }
  }

  public void setNumericBooleanValues(int falseValue, int trueValue)
  {
    integerFalse = falseValue;
    integerTrue = trueValue;

    longFalse = Long.valueOf(falseValue);
    longTrue = Long.valueOf(trueValue);

    bigDecimalFalse = BigDecimal.valueOf(falseValue);
    bigDecimalTrue = BigDecimal.valueOf(trueValue);
  }

  public void setDecimalGroupingChar(String groupingChar)
  {
    this.decimalGroupingChar = StringUtil.trimToNull(groupingChar);
  }

  public char getDecimalCharacter()
  {
    return this.decimalCharacter;
  }

  public void setDecimalCharacter(char aChar)
  {
    this.decimalCharacter = aChar;
  }

  public void setAutoConvertBooleanNumbers(boolean flag)
  {
    this.autoConvertBooleanNumbers = flag;
  }

  /**
   * Define a list of literals that should be treated as true or
   * false when converting input values.
   * If either collection is null, both are considered null
   * If these values are not defined, the default boolean conversion implemented
   * in {@link workbench.util.StringUtil#stringToBool(String)} is used (this is the
   * default)
   * @param trueValues String literals to be considered as <tt>true</tt>
   * @param falseValues String literals to be considered as <tt>false</tt>
   */
  public void setBooleanLiterals(Collection<String> trueValues, Collection<String> falseValues)
  {
    if (CollectionUtil.isEmpty(trueValues) || CollectionUtil.isEmpty(falseValues))
    {
      LogMgr.logWarning(new CallerInfo(){}, "Ignoring attempt to set boolean literals because at least one collection is empty or null");
      readConfiguredBooleanValues();
    }
    else
    {
      fillBooleanMap(trueValues, falseValues);
      booleanUserMap = true;
    }
  }

  private Number getNumberFromString(String value, boolean useInt)
  {
    if (value == null) return null;

    try
    {
      BigDecimal d = new BigDecimal(this.adjustDecimalString(value));
      if (useInt)
      {
        return d.intValueExact();
      }
      else
      {
        return d.longValueExact();
      }
    }
    catch (Exception e)
    {
    // Ignore
    }
    return null;
  }

  public Number getLong(String value)
    throws ConverterException
  {
    if (StringUtil.isBlank(value)) return null;

    try
    {
      return Long.valueOf(value);
    }
    catch (NumberFormatException e)
    {
      // Maybe the long value is disguised as a decimal
      Number n = getNumberFromString(value, false);
      if (n != null)
      {
        return n;
      }

      // When exporting from a database that supports the boolean datatype
      // into a database that maps this to an integer, we assume that
      // true/false should be 1/0
      if (autoConvertBooleanNumbers)
      {
        Boolean b = getBoolean(value, Types.BOOLEAN);
        if (b != null)
        {
          return b ? longTrue : longFalse;
        }
      }
      throw new ConverterException(value, Types.BIGINT, e);
    }
  }

  public Number getInt(String value, int type)
    throws ConverterException
  {
    if (StringUtil.isBlank(value)) return null;

    try
    {
      return Integer.valueOf(value);
    }
    catch (NumberFormatException e)
    {
      // Maybe the integer value is disguised as a decimal
      Number n = getNumberFromString(value, true);
      if (n != null) return n;

      // When exporting from a database that supports the boolean datatype
      // into a database that maps this to an integer, we assume that
      // true/false should be 1/0
      if (autoConvertBooleanNumbers)
      {
        Boolean b = getBoolean(value, Types.BOOLEAN);
        if (b != null)
        {
          return b ? integerTrue : integerFalse;
        }
      }
      throw new ConverterException(value, type, e);
    }
  }

  public BigDecimal getBigDecimal(String value, int type)
    throws ConverterException
  {
    if (StringUtil.isBlank(value)) return null;

    try
    {
      return new BigDecimal(this.adjustDecimalString(value));
    }
    catch (NumberFormatException e)
    {
      // When exporting from a database that supports the boolean datatype
      // into a database that maps this to an integer, we assume that
      // true/false should be 1/0
      if (autoConvertBooleanNumbers)
      {
        Boolean b = getBoolean(value, Types.BOOLEAN);
        if (b != null)
        {
          return b ? bigDecimalTrue : bigDecimalFalse;
        }
      }
      throw new ConverterException(value, type, e);
    }
  }

  private String makeString(Object value)
  {
    return value.toString().trim();
  }

  public void setCompareDateTimeValuesToPatterns(boolean flag)
  {
    this.checkDateTimeValuesDynamically = flag;
  }

  private int fixDateTimeType(String inputValue, int originalType)
  {
    if (originalType == Types.TIMESTAMP && dateFormatter != null && dateFormatter.toPattern().length() == inputValue.length())
    {
      return Types.DATE;
    }

    if (originalType == Types.DATE && timestampFormatter != null && timestampFormatter.toPattern().length() == inputValue.length())
    {
      return Types.TIMESTAMP;
    }
    return originalType;
  }

  /**
   * Convert the given input value to a class instance
   * according to the given type (from java.sql.Types)
   * If the value is a blob file parameter as defined by {@link workbench.util.LobFileParameter}
   * then a File object is returned that points to the data file (as passed in the
   * blob file parameter)
   * @see workbench.storage.DataStore#convertCellValue(Object, int)
   */
  public Object convertValue(Object value, int type)
    throws ConverterException
  {
    if (value == null)
    {
      return null;
    }

    String strValue = makeString(value);

    if (checkDateTimeValuesDynamically && SqlUtil.isDateType(type))
    {
      type = fixDateTimeType(strValue, type);
    }

    switch (type)
    {
      case Types.BIGINT:
        return getLong(strValue);

      case Types.INTEGER:
      case Types.SMALLINT:
      case Types.TINYINT:
        return getInt(strValue, type);

      case Types.NUMERIC:
      case Types.DECIMAL:
      case Types.DOUBLE:
      case Types.REAL:
      case Types.FLOAT:
        return getBigDecimal(strValue, type);

      case Types.CHAR:
      case Types.NCHAR:
      case Types.VARCHAR:
      case Types.NVARCHAR:
      case Types.LONGVARCHAR:
      case Types.LONGNVARCHAR:
        return value.toString();

      case Types.DATE:
        if (value instanceof java.util.Date || value instanceof LocalDate)
        {
          return value;
        }
        if (StringUtil.isBlank(strValue)) return null;

        try
        {
          return this.parseDate(strValue);
        }
        catch (Exception e)
        {
          throw new ConverterException(value, type, e);
        }

      case Types.TIMESTAMP_WITH_TIMEZONE:
        if (value instanceof ZonedDateTime || value instanceof OffsetDateTime)
        {
          return value;
        }

        if (StringUtil.isBlank(strValue)) return null;
        try
        {
          Object tzValue = null;
          if (timestampTZFormatSet || timestampFormatter.patternContainesTimeZoneInformation())
          {
            tzValue = this.parseTimestampTZ(strValue);
          }
          else
          {
            tzValue = this.parseTimestamp(strValue);
          }
          return tzType.convertTimestampTZ(tzValue);
        }
        catch (Exception e)
        {
          throw new ConverterException(value, type, e);
        }

      case Types.TIMESTAMP:
        if (value instanceof java.util.Date || value instanceof LocalDateTime || value instanceof java.sql.Timestamp)
        {
          return value;
        }
        if (StringUtil.isBlank(strValue)) return null;
        try
        {
          return this.parseTimestamp(strValue);
        }
        catch (Exception e)
        {
          throw new ConverterException(value, type, e);
        }

      case Types.TIME:
        if (value instanceof java.util.Date || value instanceof LocalTime)
        {
          return value;
        }
        if (StringUtil.isBlank(strValue)) return null;

        try
        {
          return this.parseTime(strValue);
        }
        catch (Exception e)
        {
          throw new ConverterException(value, type, e);
        }

      case Types.BLOB:
      case Types.BINARY:
      case Types.LONGVARBINARY:
      case Types.VARBINARY:
        if (value instanceof File)
        {
          return value;
        }
        else if (value instanceof byte[])
        {
          return value;
        }
        else if (value instanceof String)
        {
          LobFileParameterParser p = new LobFileParameterParser(strValue);
          if (p.getParameterCount() > 0)
          {
            LobFileParameter p1 = p.getParameters().get(0);
            String fname = p1.getFilename();
            if (fname == null) return null;
            return new File(fname);
          }
        }
        return null;

      case Types.CLOB:
      case Types.NCLOB:
        if (value instanceof String)
        {
          LobFileParameterParser p = new LobFileParameterParser(strValue);
          if (p.getParameterCount() > 0 )
          {
            LobFileParameter p1 = p.getParameters().get(0);
            String fname = p1.getFilename();
            if (fname != null)
            {
              return new File(fname);
            }
          }
        }
        else if (value instanceof File)
        {
          return value;
        }
        return strValue;

      case Types.BIT:
      case Types.BOOLEAN:
        String b = makeString(value);
        return getBoolean(b, type);

      default:
        return value;
    }
  }

  public String getDatePattern()
  {
    return this.defaultDateFormat;
  }

  public String getTimestampPattern()
  {
    return this.defaultTimestampFormat;
  }

  public LocalTime parseTime(String time)
    throws ParseException
  {
    if (isCurrentTime(time))
    {
      return LocalTime.now();
    }

    LocalTime parsed = null;
    if (defaultTimeFormat != null)
    {
      formatter.applyPattern(defaultTimeFormat);
      parsed = this.formatter.parseTime(time);
    }

    if (parsed != null)
    {
      return parsed;
    }

    for (String timeFormat : timeFormats)
    {
      try
      {
        this.formatter.applyPattern(timeFormat);
        parsed = this.formatter.parseTime(time);

        LogMgr.logDebug(new CallerInfo(){}, "Succeeded parsing the time string [" + time + "] using the format: " + formatter.toPattern());
        break;
      }
      catch (Exception e)
      {
        parsed = null;
      }
    }

    if (parsed != null)
    {
      return parsed;
    }
    throw new ParseException("Could not parse [" + time + "] as a time value!", 0);
  }

  public Temporal parseTimestampTZ(String timestampInput)
    throws ParseException, NumberFormatException
  {
    if (isCurrentTimestamp(timestampInput))
    {
      return ZonedDateTime.now();
    }

    if (isCurrentDate(timestampInput))
    {
      return ZonedDateTime.of(LocalDate.now(), LocalTime.MIDNIGHT, ZoneId.systemDefault());
    }

    ZonedDateTime zdt = getInfinity(timestampInput);
    if (zdt != null)
    {
      return zdt;
    }

    Temporal result = null;
    if (timestampTZFormatSet)
    {
      timestampTZFormatter.setIllegalDateIsNull(illegalDateIsNull);
      try
      {
        result = this.timestampFormatter.parseTimestampTZ(timestampInput);
      }
      catch (Exception ex)
      {
        if (logWarnings)
        {
          LogMgr.logWarning(new CallerInfo(){}, "Could not parse '" + timestampInput + "' as a timestamp with time zone using the format: " + this.timestampTZFormatter.toPattern(), null);
        }
        throw new ParseException("Could not convert [" + timestampInput + "] to a timestamp with timezone value!", 0);
      }
    }
    else if (this.defaultTimestampFormat != null)
    {
      timestampFormatter.setIllegalDateIsNull(illegalDateIsNull);
      try
      {
        if (FORMAT_MILLIS.equalsIgnoreCase(defaultTimestampFormat))
        {
          long value = Long.parseLong(timestampInput);
          result = ZonedDateTime.ofInstant(Instant.ofEpochMilli(value), ZoneId.systemDefault());
        }
        else
        {
          result = this.timestampFormatter.parseTimestampTZ(timestampInput);
        }
      }
      catch (Exception e)
      {
        if (logWarnings)
        {
          LogMgr.logWarning(new CallerInfo(){}, "Could not parse '" + timestampInput + "' as a timestamp with time zone using the format: " + this.timestampFormatter.toPattern(), null);
        }
        throw new ParseException("Could not convert [" + timestampInput + "] to a timestamp with timezone value!", 0);
      }
    }
    return result;
  }

  public LocalDateTime parseTimestamp(String timestampInput)
    throws ParseException, NumberFormatException
  {
    if (isCurrentTimestamp(timestampInput))
    {
      return LocalDateTime.now();
    }

    if (isCurrentDate(timestampInput))
    {
      return LocalDateTime.now();
    }

    ZonedDateTime zdt = getInfinity(timestampInput);
    if (zdt != null)
    {
      return zdt.toLocalDateTime();
    }

    LocalDateTime result = null;
    timestampFormatter.setIllegalDateIsNull(illegalDateIsNull);

    if (this.defaultTimestampFormat != null)
    {
      try
      {
        if (FORMAT_MILLIS.equalsIgnoreCase(defaultTimestampFormat))
        {
          long value = Long.parseLong(timestampInput);
          result = new java.sql.Timestamp(value).toLocalDateTime();
        }
        else
        {
          result = this.timestampFormatter.parseLocalDateTime(timestampInput);
        }
      }
      catch (Exception e)
      {
        if (logWarnings) LogMgr.logWarning(new CallerInfo(){}, "Could not parse '" + timestampInput + "' using default format " + this.timestampFormatter.toPattern() + ". Trying to recognize the format...", null);
        result = null;
      }
    }

    if (result == null && illegalDateIsNull)
    {
      LogMgr.logInfo(new CallerInfo(){}, "Illegal timestamp value '" + timestampInput + "' set to null");
      return null;
    }

    if (result == null && checkBuiltInFormats)
    {
      for (String format : timestampFormats)
      {
        try
        {
          this.formatter.applyPattern(format, true);
          result = this.formatter.parseLocalDateTime(timestampInput);
          LogMgr.logDebug(new CallerInfo(){}, "Succeeded parsing '" + timestampInput + "' using the format: " + format);
          if (useFirstMatching)
          {
            this.defaultTimestampFormat = format;
          }
          break;
        }
        catch (DateTimeParseException e)
        {
          result = null;
        }
      }
    }

    if (result != null)
    {
      return result;
    }

    throw new ParseException("Could not convert [" + timestampInput + "] to a timestamp value!", 0);
  }

  public LocalDate parseDate(String dateInput)
    throws ParseException
  {
    if (isCurrentDate(dateInput))
    {
      return LocalDate.now();
    }

    if (isCurrentTimestamp(dateInput))
    {
      return LocalDate.now();
    }

    LocalDate result = null;

    final CallerInfo ci = new CallerInfo(){};
    dateFormatter.setIllegalDateIsNull(illegalDateIsNull);

    if (this.defaultDateFormat != null)
    {
      try
      {
        if (FORMAT_MILLIS.equalsIgnoreCase(defaultTimestampFormat))
        {
          long value = Long.parseLong(dateInput);
          result = new java.sql.Date(value).toLocalDate();
        }
        else
        {
          result = this.dateFormatter.parseDate(dateInput);
        }
      }
      catch (Exception e)
      {
        if (logWarnings) LogMgr.logWarning(ci, "Could not parse [" + dateInput + "] using: " + this.dateFormatter.toPattern(), null);
        result = null;
      }
    }

    if (result == null && illegalDateIsNull)
    {
      LogMgr.logInfo(ci, "Illegal date value '" + dateInput + "' set to null");
      return null;
    }

    // Apparently not a date, try the timestamp parser
    if (result == null && this.defaultTimestampFormat != null)
    {
      try
      {
        result = this.timestampFormatter.parseDate(dateInput);
      }
      catch (DateTimeParseException e)
      {
        if (logWarnings) LogMgr.logWarning(ci, "Could not parse [" + dateInput + "] using: " + this.timestampFormatter.toPattern() + ". Trying to recognize the format...", null);
      }
    }

    // Still no luck, try to detect the format by trying the built-in formats
    if (result == null && checkBuiltInFormats)
    {
      for (String format : dateFormats)
      {
        this.formatter.applyPattern(format);
        result = this.formatter.parseDateQuietely(dateInput);
        if (result != null)
        {
          if (useFirstMatching)
          {
            this.defaultDateFormat = format;
          }
          if (logWarnings) LogMgr.logDebug(ci, "Succeeded parsing [" + dateInput + "] using the format: " + format);
          break;
        }
      }

      // no luck with dates, try timestamps
      if (result == null)
      {
        for (String format : timestampFormats)
        {
          this.formatter.applyPattern(format, false);
          LocalDateTime ldt = this.formatter.parseLocalDateTimeQuietly(dateInput);
          if (ldt != null)
          {
            result = ldt.toLocalDate();
            if (useFirstMatching)
            {
              this.defaultDateFormat = format;
            }
            if (logWarnings) LogMgr.logDebug(ci, "Succeeded parsing [" + dateInput + "] using the format: " + format);
            break;
          }
        }
      }
    }

    if (result != null)
    {
      return result;
    }

    throw new ParseException("Could not convert [" + dateInput + "] to a date", 0);
  }

  private boolean isCurrentTime(String arg)
  {
    return isKeyword("current_time", arg);
  }

  private boolean isCurrentDate(String arg)
  {
    return isKeyword("current_date", arg);
  }

  private boolean isCurrentTimestamp(String arg)
  {
    return isKeyword("current_timestamp", arg);
  }

  private boolean isKeyword(String type, String arg)
  {
    if (StringUtil.isEmpty(arg))
    {
      return false;
    }

    List<String> keywords = Settings.getInstance().getListProperty("workbench.db.keyword." + type, true);
    return keywords.contains(arg.toLowerCase());
  }

  private String adjustDecimalString(String input)
  {
    if (input == null || input.length() == 0)
    {
      return input;
    }

    if (cleanupNumbers)
    {
      return cleanupNumberString(input);
    }

    if (decimalCharacter == '.' && decimalGroupingChar == null)
    {
      // no need to search and replace the decimal character
      return input;
    }

    if (decimalGroupingChar != null)
    {
      input = input.replace(decimalGroupingChar, "");
    }

    int pos = input.lastIndexOf(this.decimalCharacter);
    if (pos < 0) return input;

    StringBuilder result = new StringBuilder(input);
    // replace the decimal char with a . as that is required by BigDecimal(String)
    // this way we only leave the last decimal character
    result.setCharAt(pos, '.');
    return result.toString();
  }

  private String cleanupNumberString(String value)
  {
    int len = value.length();
    StringBuilder result = new StringBuilder(len);
    int pos = value.lastIndexOf(this.decimalCharacter);
    for (int i = 0; i < len; i++)
    {
      char c = value.charAt(i);
      if (i == pos)
      {
        // replace the decimal char with a . as that is required by BigDecimal(String)
        // this way we only leave the last decimal character
        result.append('.');
      }
      // filter out everything but valid number characters
      else if ("+-0123456789eE".indexOf(c) > -1)
      {
        result.append(c);
      }
    }
    return result.toString();
  }

  private Boolean getBoolean(String value, int type)
    throws ConverterException
  {
    if (!booleanUserMap)
    {
      value = value.toLowerCase().trim();
    }
    Boolean bool = booleanValues.get(value);
    if (booleanUserMap && bool == null)
    {
      throw new ConverterException("Input value [" + value + "] not in the list of defined true or false literals", type, null);
    }
    return bool;
  }

  private ZonedDateTime getInfinity(String input)
  {
    if (InfinityLiterals.PG_POSITIVE_LITERAL.equals(input))
    {
      return java.time.ZonedDateTime.of(LocalDateTime.MAX, ZoneId.ofOffset("", ZoneOffset.UTC));
    }

    if (InfinityLiterals.PG_NEGATIVE_LITERAL.equals(input))
    {
      return java.time.ZonedDateTime.of(LocalDateTime.MIN, ZoneId.ofOffset("", ZoneOffset.UTC));
    }
    return null;
  }
}
