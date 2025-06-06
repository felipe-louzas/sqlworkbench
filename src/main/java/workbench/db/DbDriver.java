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
package workbench.db;

import java.io.File;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.mssql.NativeLibraryClassLoader;
import workbench.db.postgres.PostgresUtil;

import workbench.util.ClasspathUtil;
import workbench.util.CollectionUtil;
import workbench.util.StringUtil;
import workbench.util.WbFile;

/**
 *  Represents a JDBC Driver definition.
 *
 *  The definition includes a (logical) name, a driver class
 *  and (optional) a library from which the driver is to
 *  be loaded.
 *
 *  @author  Thomas Kellerer
 */
public class DbDriver
  implements Comparable<DbDriver>
{
  public static final String LIB_SEPARATOR = "|";

  private Driver driverClassInstance;
  private URLClassLoader classLoader;

  protected String name;
  private String driverClass;
  private final List<String> libraryList = new ArrayList<>();
  private boolean isTemporary;
  private String sampleUrl;

  public DbDriver()
  {
  }

  public DbDriver(Driver aDriverClassInstance)
  {
    this.driverClassInstance = aDriverClassInstance;
    this.driverClass = aDriverClassInstance.getClass().getName();
    this.name = this.driverClass;
  }

  public DbDriver(String aDriverClassname)
  {
    this.setDriverClass(aDriverClassname);
    this.setName(aDriverClassname);
  }

  public DbDriver(String aName, String aClass, String aLibrary)
  {
    this.setName(aName);
    this.setDriverClass(aClass);
    this.setLibrary(aLibrary);
  }

  public boolean isTemporaryDriver()
  {
    return isTemporary;
  }

  /**
   * Marks this driver as a temporary driver that should not be saved.
   *
   */
  public void setTemporary()
  {
    isTemporary = true;
  }

  public String getName()
  {
    return this.name;
  }

  public final void setName(String name)
  {
    this.name = name;
  }

  public String getDriverClass()
  {
    return this.driverClass;
  }

  public final void setDriverClass(String className)
  {
    this.driverClass = StringUtil.trimToNull(className);
    this.driverClassInstance = null;
    this.classLoader = null;
  }

  public String getDescription()
  {
    StringBuilder b = new StringBuilder(100);
    if (this.name != null)
    {
      b.append(this.name);
      b.append(" (");
      b.append(this.driverClass);
      b.append(')');
    }
    else
    {
      b.append(this.driverClass);
    }
    return b.toString();
  }

  public void setLibraryList(List<String> files)
  {
    this.libraryList.clear();
    if (CollectionUtil.isNonEmpty(files))
    {
      this.libraryList.addAll(files);
    }
  }

  public List<String> getRealLibraryList()
  {
    return Collections.unmodifiableList(libraryList);
  }

  public List<String> getLibraryList()
  {
    List<String> result = new ArrayList<>(this.libraryList.size());
    ClasspathUtil cp = new ClasspathUtil();
    for (String entry : libraryList)
    {
      if (entry.endsWith(ClasspathUtil.EXT_DIR))
      {
        result.add(ClasspathUtil.EXT_DIR);
      }
      else
      {
        result.add(entry);
      }
    }
    return result;
  }

  public static List<String> splitLibraryList(String libList)
  {
    if (libList == null) return Collections.emptyList();

    if (libList.contains(LIB_SEPARATOR))
    {
      return StringUtil.stringToList(libList, LIB_SEPARATOR, true, true, false);
    }
    else if (!StringUtil.isEmpty(libList))
    {
      return StringUtil.stringToList(libList, StringUtil.getPathSeparator(), true, true, false);
    }
    return Collections.emptyList();
  }

  public final void setLibrary(String libList)
  {
    this.libraryList.clear();
    this.libraryList.addAll(splitLibraryList(libList));
    this.driverClassInstance = null;
    this.classLoader = null;
  }

  public boolean canReadLibrary()
  {
    // When running in testmode, all necessary libraries are added through
    // the classpath already, so there is no need to check them here.
    if (Settings.getInstance().isTestMode()) return true;

    if (isTemporary) return true;

    if ("sun.jdbc.odbc.JdbcOdbcDriver".equals(driverClass)) return true;

    ClasspathUtil cpUtil = new ClasspathUtil();
    File extDir = cpUtil.getExtDir();

    if (libraryList != null)
    {
      for (String lib : libraryList)
      {
        String realLib = Settings.getInstance().replaceLibDirKey(lib);
        if (ClasspathUtil.EXT_DIR.equals(realLib)) return true;

        File f = new File(realLib);
        if (f.equals(extDir)) return true;

        if (f.getParentFile() == null)
        {
          f = new File(Settings.getInstance().getLibDir(), realLib);
          if (!f.exists())
          {
            f = new File(extDir, realLib);
          }
        }
        if (!f.exists()) return false;
      }
      return true;
    }
    return false;
  }

  @Override
  public String toString()
  {
    return this.getDescription();
  }

  public void setSampleUrl(String anUrl)
  {
    this.sampleUrl = anUrl;
  }

  public String getSampleUrl()
  {
    return this.sampleUrl;
  }

  public Class loadClassFromDriverLib(String className)
    throws ClassNotFoundException
  {
    if (this.isExtDriver())
    {
      return Class.forName(className);
    }
    if (this.classLoader == null) return null;
    Thread.currentThread().setContextClassLoader(classLoader);
    Class clz = this.classLoader.loadClass(className);
    return clz;
  }

  public boolean isExtDriver()
  {
    ClasspathUtil cpUtil = new ClasspathUtil();
    for (String fname : libraryList)
    {
      if (fname.contains(Settings.LIB_DIR_KEY)) return false;
      if (fname.equals(ClasspathUtil.EXT_DIR)) return true;
      File f = new File(fname);
      if (f.getParentFile() == null || f.isAbsolute())
      {
        if (!cpUtil.isInExtDir(f)) return false;
      }
    }
    return true;
  }

  private URLClassLoader createClassLoader(URL[] path)
  {
    Set<String> driversWithDlls = Settings.getInstance().getDriversLoadingDLLs();
    if (driversWithDlls.contains(this.driverClass))
    {
      // This is mainly for SQL Server's JDBC driver so that the DLL for integrated security
      // is found without the need to mess around with java.library.path
      WbFile jarFile = buildFile(libraryList.get(0));
      return new NativeLibraryClassLoader(jarFile, path, ClassLoader.getSystemClassLoader());
    }

    // Use a standard URLClassLoader for everything else
    return new URLClassLoader(path, ClassLoader.getSystemClassLoader());
  }

  private synchronized void loadDriverClass()
    throws ClassNotFoundException, Exception, UnsupportedClassVersionError
  {
    if (this.driverClassInstance != null) return;
    final CallerInfo ci = new CallerInfo(){};

    try
    {
      if (!isExtDriver() && this.classLoader == null && CollectionUtil.isNonEmpty(this.libraryList))
      {
        URL[] url = new URL[libraryList.size()];
        int index = 0;
        for (String fname : libraryList)
        {
          File f = buildFile(fname);
          url[index] = f.toURI().toURL();
          String fpath = url[index].toString();
          if (Settings.getInstance().getObfuscateLogInformation())
          {
            fpath = WbFile.getPathForLogging(fpath);
          }
          LogMgr.logInfo(ci, "Adding ClassLoader URL=" + fpath);
          index ++;
        }
        classLoader = createClassLoader(url);
      }

      Class<? extends Driver> drvClass = null;
      if (classLoader != null)
      {
        // New Firebird 2.0 driver needs this, and it does not seem to do any harm
        // for other drivers
        Thread.currentThread().setContextClassLoader(classLoader);
        drvClass = (Class<? extends Driver>)this.classLoader.loadClass(driverClass);
      }
      else
      {
        // Assume the driver class is available on the classpath
        LogMgr.logInfo(ci, "Loading driver " + this.driverClass + " through default classloader");
        drvClass = (Class<? extends Driver>)Class.forName(this.driverClass);
      }

      driverClassInstance = drvClass.getDeclaredConstructor().newInstance();
      if (Settings.getInstance().getBoolProperty("workbench.db.registerdriver", false))
      {
        // Some drivers expect to be registered with the DriverManager
        try
        {
          LogMgr.logDebug(ci, "Registering new driver instance for " + this.driverClass + " with DriverManager");
          DriverManager.registerDriver(this.driverClassInstance);
        }
        catch (Throwable th)
        {
          LogMgr.logError(ci, "Error registering driver instance with DriverManager", th);
        }
      }
    }
    catch (UnsupportedClassVersionError e)
    {
      LogMgr.logError(ci, "Driver class could not be loaded because it's intended for a different Java version", e);
      throw e;
    }
    catch (ClassNotFoundException e)
    {
      LogMgr.logError(ci, "Class not found when loading driver through using the classpath: " + libraryList, e);
      throw e;
    }
    catch (Throwable e)
    {
      this.classLoader = null;
      LogMgr.logError(ci, "Error loading driver class: " + this.driverClass, e);
      throw new Exception("Could not load driver class " + this.driverClass, e);
    }
  }

  private WbFile buildFile(String fname)
  {
    String realFile = Settings.getInstance().replaceLibDirKey(fname);
    WbFile f = new WbFile(realFile);
    if (f.getParentFile() == null)
    {
      f = new WbFile(Settings.getInstance().getLibDir(), realFile);
    }
    return f;
  }

  public DbDriver createCopy()
  {
    DbDriver copy = new DbDriver();
    copy.driverClass = this.driverClass;
    copy.libraryList.addAll(this.libraryList);
    copy.sampleUrl = this.sampleUrl;
    copy.name = this.name;

    // the internal attribute should not be copied!

    return copy;
  }

  private boolean useEmptyStringForEmptyPassword()
  {
    return Settings.getInstance().getBoolProperty(this.driverClass + ".use.emptypassword", false);
  }

  private boolean useEmptyStringForEmptyUser()
  {
    return Settings.getInstance().getBoolProperty(this.driverClass + ".use.emptyuser", false);
  }

  public Connection connect(String url, String user, String password, String id, Properties connProps)
    throws ClassNotFoundException, NoConnectionException, SQLException
  {
    String loggingUrl = getURLForLogging(url);
    String loggingUser = getUsernameForLogging(user);

    final CallerInfo ci = new CallerInfo(){};
    Connection conn = null;
    try
    {
      loadDriverClass();

      Properties props = new Properties();
      if (StringUtil.isNotBlank(user))
      {
        props.put("user", user);
      }
      else if (useEmptyStringForEmptyUser())
      {
        props.put("user", "");
      }

      if (StringUtil.isNotBlank(password))
      {
        props.put("password", password);
      }
      else if (useEmptyStringForEmptyPassword())
      {
        props.put("password", "");
      }

      // copy the user defined connection properties into the actually used ones!
      if (connProps != null)
      {
        Enumeration keys = connProps.propertyNames();
        while (keys.hasMoreElements())
        {
          String key = (String)keys.nextElement();
          if (!props.containsKey(key))
          {
            String value = StringUtil.replaceProperties(connProps.getProperty(key));
            props.put(key, value);
          }
        }
      }

      // this replaces the deleted MySQLTableCommentReader
      if (url.startsWith("jdbc:mysql")
          && Settings.getInstance().getBoolProperty("workbench.db.mysql.tablecomments.retrieve", false)
          && !props.containsKey("useInformationSchema"))
      {
        // see: https://bugs.mysql.com/bug.php?id=65213
        props.setProperty("useInformationSchema", "true");
      }

      setAppInfo(props, url.toLowerCase(), id, user);

      conn = this.driverClassInstance.connect(url, props);

      if (url.startsWith("jdbc:firebirdsql:"))
      {
        // The system property for the Firebird driver is only needed when the connection is created
        // so after the connect was successful, we can clean up the system properties
        System.clearProperty("org.firebirdsql.jdbc.processName");
      }

      JdbcClientInfoBuilder clientInfo = new JdbcClientInfoBuilder(user, id);
      clientInfo.setClientInfo(conn, url);
    }
    catch (ClassNotFoundException | UnsupportedClassVersionError | ThreadDeath e )
    {
      throw e;
    }
    catch (Throwable th)
    {
      LogMgr.logError(ci, "Error connecting to the database using URL=" + loggingUrl + ", username=" + loggingUser, th);
      if (th instanceof SQLException) throw (SQLException)th;
      throw new SQLException(th.getMessage(), th);
    }

    if (conn == null)
    {
      LogMgr.logError(ci, "No connection returned by driver " + this.driverClass + " for URL=" + loggingUrl, null);
      throw new NoConnectionException("Driver did not return a connection for url=" + loggingUrl);
    }

    return conn;
  }

  public void releaseDriverInstance()
  {
    final CallerInfo ci = new CallerInfo(){};
    LogMgr.logDebug(ci, "Releasing classloader and driver");
    if (this.driverClassInstance != null)
    {
      try
      {
        DriverManager.deregisterDriver(this.driverClassInstance);
      }
      catch (SQLException sql)
      {
        LogMgr.logWarning(ci, "Could not de-register driver", sql);
      }
      this.driverClassInstance = null;
    }
    this.classLoader = null;
    System.gc();
  }

  private String getAppName()
  {
    return Settings.getInstance().getProperty("workbench.db.connection.info.programname", null);
  }

  private String getProgramName()
  {
    String userPrgName = getAppName();
    if (userPrgName != null) return userPrgName;

    return ResourceMgr.TXT_PRODUCT_NAME + " " + ResourceMgr.getBuildNumber();
  }

  private boolean doSetAppName(String url)
  {
    String dbid = JdbcUtils.getDbIdFromUrl(url);
    boolean defaultValue = Settings.getInstance().getBoolProperty("workbench.db.connection.set.appname", true);
    return Settings.getInstance().getBoolProperty("workbench.db." + dbid + ".connection.set.appname", defaultValue);
  }

  /**
   * Pust the application name and connection information into the passed connection properties.
   *
   * @param props the properties to be used when establishing the connection
   * @param url the JDBC url (needed to identify the DBMS)
   * @param id the internal connection id
   * @param user the user for the connection
   */
  private void setAppInfo(Properties props, String url, String id, String user)
  {
    if (!doSetAppName(url)) return;

    // identify the program name when connecting
    // this is different for each DBMS.
    String appNameProperty = null;
    String prgName = getProgramName();

    if (url.startsWith("jdbc:postgresql") && PostgresUtil.supportsAppInfoProperty(this.driverClassInstance.getClass()))
    {
      appNameProperty = PostgresUtil.APP_NAME_PROPERTY;
      prgName += " (" + id + ")";
    }

    if (url.startsWith("jdbc:oracle:thin"))
    {
      appNameProperty = "v$session.program";
      if (id != null && !props.containsKey("v$session.terminal")) props.put("v$session.terminal", StringUtil.getMaxSubstring(id, 30));

      // it seems that the Oracle 10 driver does not
      // add this to the properties automatically
      // (as the drivers for 8 and 9 did)
      user = System.getProperty("user.name",null);
      if (user != null && !props.containsKey("v$session.osuser")) props.put("v$session.osuser", user);
    }

    if (url.startsWith("jdbc:inetdae"))
    {
      appNameProperty = "appname";
    }

    if (url.startsWith("jdbc:jtds"))
    {
      appNameProperty = "APPNAME";
    }

    if (url.startsWith("jdbc:microsoft:sqlserver"))
    {
      // Old MS SQL Server driver
      appNameProperty = "ProgramName";
    }

    if (url.startsWith("jdbc:sqlserver:"))
    {
      // New SQL Server 2005 JDBC driver
      appNameProperty = "applicationName";
      if (!props.containsKey("workstationID"))
      {
        String localName = getLocalHostname();
        if (localName != null)
        {
          props.put("workstationID", localName);
        }
      }
    }

    if (url.startsWith("jdbc:db2:"))
    {
      props.put("clientApplicationInformation", id);
      appNameProperty = "clientProgramName";
    }

    if (url.startsWith("jdbc:firebirdsql:"))
    {
      System.setProperty("org.firebirdsql.jdbc.processName", StringUtil.getMaxSubstring(prgName, 250));
    }

    if (url.startsWith("jdbc:sybase:tds"))
    {
      appNameProperty = "APPLICATIONNAME";
    }

    if (appNameProperty == null)
    {
      String dbid = JdbcUtils.getDbIdFromUrl(url);
      appNameProperty = Settings.getInstance().getProperty("workbench.db." + dbid + ".connection.property.appname", null);
    }

    if (appNameProperty != null && !props.containsKey(appNameProperty))
    {
      props.put(appNameProperty, prgName);
    }
  }

  private String getLocalHostname()
  {
    try
    {
      InetAddress localhost = InetAddress.getLocalHost();
      String localName = localhost.getHostName();
      if (localName == null)
      {
        localName = localhost.getHostAddress();
      }
      return localName;
    }
    catch (Throwable th)
    {
      return null;
    }
  }

  /**
   *  This is a "simplified version of the connect() method
   *  for issuing a "shutdown command" to Cloudscape
   */
  public void commandConnect(String url)
    throws SQLException, ClassNotFoundException, Exception
  {
    this.loadDriverClass();
    Properties props = new Properties();
    LogMgr.logDebug(new CallerInfo(){}, "Sending command URL=" + getURLForLogging(url) + " to database");
    this.driverClassInstance.connect(url, props);
  }

  @Override
  public boolean equals(Object other)
  {
    if (other == null) return false;
    if (this.driverClass == null) return false;

    if (other instanceof DbDriver)
    {
      DbDriver o = (DbDriver)other;
      return StringUtil.equalString(o.getId(), getId());
    }
    else if (other instanceof String)
    {
      return StringUtil.equalString(this.driverClass, (String)other);
    }
    else
    {
      return false;
    }
  }

  protected String getId()
  {
    StringBuilder b = new StringBuilder(driverClass == null ? name.length() : driverClass.length() + name.length() + 1);
    b.append(driverClass == null ? "" : driverClass);
    b.append('$');
    b.append(name);
    return b.toString();
  }

  @Override
  public int hashCode()
  {
    return getId().hashCode();
  }

  @Override
  public int compareTo(DbDriver o)
  {
    return getId().compareTo(o.getId());
  }

  public static String getURLForLogging(ConnectionProfile profile)
  {
    if (profile == null) return "";
    return getURLForLogging(profile.getUrl());
  }

  public static String getURLForLogging(String url)
  {
    if (url == null) return "";
    if (Settings.getInstance().getObfuscateLogInformation())
    {
      return JdbcUtils.extractPrefix(url) + "******";
    }
    return url;
  }

  public static String getUsernameForLogging(String user)
  {
    if (user == null) return "";
    if (Settings.getInstance().getObfuscateLogInformation())
    {
      return "****";
    }
    return user;
  }
}
