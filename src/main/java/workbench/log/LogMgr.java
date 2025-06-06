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
package workbench.log;

import java.io.File;
import java.sql.SQLException;

import workbench.resource.Settings;

import workbench.util.ExceptionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;
import workbench.util.WbFile;

/**
 * A facade to the actual logging implementation used.
 * <br>
 * Depending on the flag passed to {@link #init(boolean)}
 * either a {@link SimpleLogger} or a {@link Log4JLogger} will be created.
 * <br>
 * If Log4J is used as the logging sub-system, none of the SQL Workbench/J
 * log settings will be applied. Everything needs to be configured through Log4J
 * <br>
 * {@link Log4JHelper} is used to check if the Log4J classes are available at
 * runtime (using reflection).
 * The Log4J classes (log4j-api.jar and log4j-core.jar) need to be available through
 * the class path (for details see the manifest that is created in build.xml).
 *
 * @see SimpleLogger
 * @see Log4JLogger
 * @see WbLogger
 *
 * @author Thomas Kellerer
 */
public class LogMgr
{
  public static final String DEFAULT_ENCODING = "UTF-8";

  private static final String DUMMY_LOG4J_FACTORY = "org.apache.logging.log4j.simple.SimpleLoggerContextFactory";
  private static final String LOG4J_FACTORY_PROP = "log4j2.loggerContextFactory";
  private static WbLogger logger = null;
  private static boolean useLog4J;

  public synchronized static void init(boolean useLog4j)
  {
    if (!useLog4j && StringUtil.isBlank(System.getProperty(LOG4J_FACTORY_PROP)))
    {
      // Avoid an error message when Log4j is initialized and log4j-core is not available
      System.setProperty(LOG4J_FACTORY_PROP, DUMMY_LOG4J_FACTORY);
    }

    useLog4J = useLog4j && Log4JHelper.isLog4JAvailable();
    if (!useLog4j)
    {
      // Initialize the Workbench logging right away
      getLogger();
    }
  }

  public static WbFile getLogfile()
  {
    File f = getLogger().getCurrentFile();
    if (f == null)
    {
      return null;
    }
    return new WbFile(f);
  }

  public static void setMessageFormat(String msgFormat)
  {
    getLogger().setMessageFormat(msgFormat);
  }

  public static void logToSystemError(boolean flag)
  {
    getLogger().logToSystemError(flag);
  }

  public static String getLevel()
  {
    return getLogger().getRootLevel().toString();
  }

  public static void setLevel(String aType)
  {
    getLogger().setRootLevel(LogLevel.getLevel(aType));
  }

  public static void shutdown()
  {
    getLogger().shutdownWbLog();
  }

  public static void setOutputFile(File logfile, int maxFilesize, int maxBackups)
  {
    getLogger().setOutputFile(logfile, maxFilesize, maxBackups);
  }

  public static boolean isInfoEnabled()
  {
    return getLogger().levelEnabled(LogLevel.info);
  }

  public static boolean isDebugEnabled()
  {
    return getLogger().levelEnabled(LogLevel.debug);
  }

  public static boolean isTraceEnabled()
  {
    return getLogger().levelEnabled(LogLevel.trace);
  }

  public static void logDebug(CallerInfo caller, CharSequence message)
  {
    getLogger().logMessage(LogLevel.debug, caller, message, null);
  }

  public static void logMetadataSql(CallerInfo caller, String type, CharSequence sql, Object... parameters)
  {
    if (isInfoEnabled()|| Settings.getInstance().getDebugMetadataSql())
    {
      String msg = "Retrieving "  + type + " using:\n" + SqlUtil.replaceParameters(sql, parameters);
      getLogger().logMessage(LogLevel.info, caller, msg, null);
    }
  }

  public static void logMetadataError(CallerInfo caller, Throwable error, String type, CharSequence sql, Object... parameters)
  {
    String msg = "Error retrieving "  + type + " using:\n" + SqlUtil.replaceParameters(sql, parameters);
    getLogger().logMessage(LogLevel.error, caller, msg, error);
  }

  public static void logTrace(CallerInfo caller, CharSequence message)
  {
    getLogger().logMessage(LogLevel.trace, caller, message, null);
  }

  public static void logTrace(CallerInfo caller, CharSequence message, Throwable th)
  {
    getLogger().logMessage(LogLevel.trace, caller, message, th);
  }

  public static void logDebug(CallerInfo caller, CharSequence message, Throwable th)
  {
    getLogger().logMessage(LogLevel.debug, caller, message, th);
    logChainedException(LogLevel.debug, caller, th);
  }

  public static void logInfo(CallerInfo caller, CharSequence message)
  {
    getLogger().logMessage(LogLevel.info, caller, message, null);
  }

  public static void logInfo(CallerInfo caller, CharSequence message, Throwable th)
  {
    getLogger().logMessage(LogLevel.info, caller, message, th);
  }

  public static void logWarning(CallerInfo caller, CharSequence message)
  {
    getLogger().logMessage(LogLevel.warning, caller, message, null);
  }

  public static void logWarning(CallerInfo caller, CharSequence message, Throwable th)
  {
    getLogger().logMessage(LogLevel.warning, caller, message, th);
    logChainedException(LogLevel.warning, caller, th);
  }

  public static void logError(CallerInfo caller, CharSequence message, Throwable th)
  {
    getLogger().logMessage(LogLevel.error, caller, message, th);
    logChainedException(LogLevel.error, caller, th);
  }

  public static void logUserSqlError(CallerInfo caller, String sql, Throwable th)
  {
    String logMsg = "Error executing:\n" + sql + "\n  ";
    if (th instanceof java.sql.SQLFeatureNotSupportedException)
    {
      logError(caller, logMsg, th);
    }
    else if (th instanceof SQLException && !getLogger().levelEnabled(LogLevel.debug))
    {
      logMsg += ExceptionUtil.getDisplay(th);
      logError(caller, logMsg, null);
    }
    else
    {
      logError(caller, logMsg, th);
    }
  }

  public static void logChainedException(LogLevel level, CallerInfo caller, Throwable se)
  {
    if (getLogger().levelEnabled(level) && se instanceof SQLException)
    {
      SQLException next = ((SQLException)se).getNextException();
      while (next != null)
      {
        getLogger().logMessage(LogLevel.error, caller, "Chained exception: " + ExceptionUtil.getDisplay(next), null);
        next = next.getNextException();
      }
    }
  }

  public static void addLogListener(LogListener listener)
  {
    getLogger().addLogListener(listener);
  }

  public static void removeLogListener(LogListener listener)
  {
    getLogger().removeLogListener(listener);
  }

  private synchronized static WbLogger getLogger()
  {
    if (useLog4J)
    {
      try
      {
        return new Log4JLogger();
      }
      catch (Throwable e)
      {
        System.err.println("Could not create Log4J getLogger(). Using SimpleLogger!");
        e.printStackTrace(System.err);
        useLog4J = false;
      }
    }
    if (logger == null)
    {
      logger = new SimpleLogger();
    }
    return logger;
  }
}
