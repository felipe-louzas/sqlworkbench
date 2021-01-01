/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2021, Thomas Kellerer
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
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import workbench.WbManager;

import workbench.util.StringUtil;

import org.apache.log4j.Appender;
import org.apache.log4j.Category;
import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.Priority;
import org.apache.log4j.spi.LoggerFactory;
import org.apache.log4j.spi.LoggingEvent;

/**
 * An implementation of WbLogger that uses Log4J for logging.
 *
 * @author Thomas Kellerer
 * @author Peter Franken
 */
public class Log4JLogger
	extends Logger
	implements WbLogger
{
	// Creates some debug-messages on system.out (without the use of Log4J to avoid confusion)
	static boolean debug = false;
	static boolean useReflectionClass = true;
  private List<LogListener> listenerList = new ArrayList<>(1);

	/**
	 * Create a Logger for name and geht the FQCN of the LoggerController-class from the
	 * LoggerFactory. Generating the Locationinfo by Log4J just uses a private fqcn (due to call of
	 * {@link #forcedLog(String, Priority, Object, Throwable)}). Making this modifiable allows a more
	 * flexible generation of the LocationInfo.
	 *
	 * <br>
	 * Problem: If the log-statement is generated by the LoggerController itself, the LocationInfo
	 * returns the first StackTraceElement after the LoggerController-class. That is, for the
	 * following example, with a log-call in LogMgr.setLevel(), the code position
	 * Settings.initLogging(Settings.java:222).
	 * <br>
	 *
	 * Example: Log-calll in LogMgr.setLevel() <code><br>
	 *  at workbench.log.Log4JLogger.getLogger(Log4JLogger.java:223)<br>
	 * 	at workbench.log.LogMgr.getLogger(LogMgr.java:175)<br>
	 * 	at workbench.log.LogMgr.logInfo(LogMgr.java:109)<br>
	 * 	at workbench.log.LogMgr.setLevel(LogMgr.java:74)<br>
	 * 	at workbench.resource.Settings.initLogging(Settings.java:222)<br>
	 * </code>
	 *
	 * <b>Log-Calls from within the LoggerController should be avoided or should get a special marker
	 * in the message part!</b>
	 *
	 * @param name Caller
	 * TODO: make this constructor more private, as soon as <loggerFactory> works with factories from
	 *       other packages as org.apache.log4j
	 */
	public Log4JLogger(String name)
	{
		super(name);

		String loggerFqcn = Log4JLoggerFactory.getLoggerFqcn().getCanonicalName();
		setFqcn(loggerFqcn);
		// Get the fqcn of the logger controller class from the factory
		if (debug)
		{
			System.out.println("New logger for class " + name + " @(" + loggerFqcn + ")");
		}
	}

	protected static LoggerFactory loggerFactory = new Log4JLoggerFactory();
	/**
	 * Modifiable fully qualified name of the LoggerController Class (in opposite to the fix fqcn in
	 * {@link Logger} and {@link Category}
	 */
	private String fqcn = Log4JLogger.class.getName();

	/**
	 * Don't use the given fqcn, but our own. This method always gets only {@link Logger} (for trace)
	 * or {@link Category} (for the other levels), so let us be smarter ,-)
	 *
	 * This method creates a new logging event and logs the event without further checks.
	 *
	 * @param fqcn is ignored and replaced by the value defined through {@link #setFqcn(String)}
	 *
	 */
	@Override
	protected void forcedLog(String fqcn, Priority level, Object message, Throwable t)
	{
		callAppenders(new LoggingEvent(this.fqcn, this, level, message, t));
	}

	/**
	 * @param fQCN the fQCN to set
	 */
	public final void setFqcn(String fQCN)
	{
		fqcn = fQCN;
	}

	/**
	 * @return the fQCN
	 */
	public String getFqcn()
	{
		return fqcn;
	}

	/**
	 * Always use our own factory
	 *
	 * @param name The name of the logger to retrieve.
	 * @return {@link Log4JLogger} instance according to {@link Log4JLoggerFactory}, which uses
	 *         {@link Log4JLogger#Log4JLogger(String)}
	 */
	public static Logger getLogger(String name)
	{
		return LogManager.getLogger(name, loggerFactory);
	}

	/**
	 * Always use our own factory
	 *
	 * @param clazz The name of the class for the logger to retrieve.
	 * @return {@link Log4JLogger} instance according to {@link Log4JLoggerFactory}, which uses
	 *         {@link Log4JLogger#Log4JLogger(String)}
	 */
	public static Logger getLogger(Class clazz)
	{
		return LogManager.getLogger(clazz.getCanonicalName(), loggerFactory);
	}

	private LogLevel toWbLevel(Level level)
	{
		if (level == Level.DEBUG)
		{
			return LogLevel.debug;
		}
		if (level == Level.ERROR)
		{
			return LogLevel.error;
		}
		if (level == Level.INFO)
		{
			return LogLevel.info;
		}
		if (level == Level.WARN)
		{
			return LogLevel.warning;
		}
		if (level == Level.TRACE)
		{
			return LogLevel.trace;
		}
		return LogLevel.error;
	}

	@Override
	public void setRootLevel(LogLevel level)
	{
		// ignored, set by configuration file
	}

	@Override
	public LogLevel getRootLevel()
	{
		return toWbLevel(getRootLogger().getLevel());
	}

	public static WbLogger getLogger()
	{
		String callerClass = getCallerClassName();

		if (debug)
		{
			System.out.println("callerClass in getLogger(): " + callerClass);
		}

		if (callerClass != null)
		{
			Logger logger = getLogger(callerClass);
			// If a specific class level is defined, but not correct, the Logger might not be a
			// Log4JLogger
			if (!(logger instanceof Log4JLogger))
			{
				System.err.println("Please don't define LoggerClasses without the correct loggerFactory");
				return null;
			}
			return (Log4JLogger) logger;
		}
		else
		{
			return (Log4JLogger) getLogger(Log4JLogger.class);
		}
	}

	private static String getCallerClassName()
	{
		if (useReflectionClass)
		{
			try
			{
				return getCallerClass(5).getName();
			}
			catch (Throwable th)
			{
				System.err.println("Could not get caller class using Reflection.getCallerClass(). Using getStackTrace() instead");
				useReflectionClass = false;
			}
		}
		StackTraceElement[] trace = Thread.currentThread().getStackTrace();
		if (trace != null && trace.length > 4)
		{
			return trace[5].getClassName();
		}
		return Log4JLogger.class.getName();
	}

	public static Class getCallerClass(int i)
	{
		Class[] classContext = null;
		try
		{
			classContext = new SecurityManager()
			{
				@Override
				public Class[] getClassContext()
				{
					return super.getClassContext();
				}
			}.getClassContext();
		}
		catch (NoSuchMethodError e)
		{
		}

		if (classContext != null)
		{
			for (int j = 0; j < classContext.length; j++)
			{
				if (classContext[j] == Log4JLogger.class)
				{
					return classContext[i + j];
				}
			}
		}
		else
		{
			try
			{
				StackTraceElement[] classNames = Thread.currentThread().getStackTrace();
				for (int j = 0; j < classNames.length; j++)
				{
					if (Class.forName(classNames[j].getClassName()) == Log4JLogger.class)
					{
						return Class.forName(classNames[i + j].getClassName());
					}
				}
			}
			catch (ClassNotFoundException e)
			{
			}
		}
		return null;
	}

	@Override
	public void logMessage(LogLevel level, Object caller, CharSequence msg, Throwable th)
	{
		switch (level)
		{
			case trace:
				trace(msg, th);
				break;
			case debug:
				debug(msg, th);
				break;
			case info:
				info(msg, th);
				break;
			case warning:
				warn(msg, th);
				break;
			default:
				error(msg, th);
		}
    if (levelEnabled(level))
    {
      notifyListener(msg);
    }
	}

	@Override
	public void setMessageFormat(String newFormat)
	{
		// ignored, should be done by log4j.xml
	}

	@Override
	public void logToSystemError(boolean flag)
	{
		// ignored, should be done by log4j.xml
	}

	@Override
	public File getCurrentFile()
	{
		Logger wb = getLogger("workbench.log.LogMgr");
		File logfile = findLogFile(wb);
		if (logfile == null)
		{
			// No specific logger found, try the root logger
			logfile = findLogFile(Logger.getRootLogger());
		}
		return logfile;
	}

	private File findLogFile(Logger start)
	{
		Enumeration appenders = start.getAllAppenders();
		while (appenders.hasMoreElements())
		{
			Appender app = (Appender) appenders.nextElement();
			if (app instanceof FileAppender)
			{
				FileAppender file = (FileAppender) app;
				String fname = file.getFile();
				if (fname != null)
				{
					return new File(fname);
				}
			}
		}
		return null;
	}

	@Override
	public void setOutputFile(File logfile, int maxFilesize, int maxBackups)
	{
		Logger log = getLogger(getClass());
		log.info("=================== Log started ===================");
		String configFile = System.getProperty("log4j.configuration");
		if (StringUtil.isNonBlank(configFile))
		{
			log.info("Log4J initialized from: " + configFile);
		}
	}

	@Override
	public void shutdownWbLog()
	{
		getLogger(getClass()).info("=================== Log stopped ===================");
		if (WbManager.shouldDoSystemExit())
		{
			LogManager.shutdown();
		}
	}

	@Override
	public boolean levelEnabled(LogLevel tolog)
	{
		Logger root = Logger.getRootLogger();
		switch (tolog)
		{
			case trace:
				return root.isTraceEnabled();
			case debug:
				return root.isDebugEnabled();
			case info:
				return root.isInfoEnabled();
			case warning:
				return root.isEnabledFor(Level.WARN);
		}
		return true;
	}

  private void notifyListener(CharSequence msg)
  {
    for (LogListener listener : listenerList)
    {
      if (listener != null)
      {
        listener.messageLogged(msg);
      }
    }
  }

  @Override
  public void addLogListener(LogListener listener)
  {
    listenerList.add(listener);
  }

  @Override
  public void removeLogListener(LogListener listener)
  {
    listenerList.remove(listener);
  }

}
