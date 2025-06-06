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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import workbench.WbManager;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;
import workbench.ssh.SshConfig;
import workbench.ssh.SshException;
import workbench.ssh.SshManager;

import workbench.db.objectcache.DbObjectCacheFactory;
import workbench.db.shutdown.DbShutdownFactory;
import workbench.db.shutdown.DbShutdownHook;

import workbench.gui.profiles.ProfileKey;

import workbench.util.ClasspathUtil;
import workbench.util.CollectionUtil;
import workbench.util.ExceptionUtil;
import workbench.util.FileUtil;
import workbench.util.PropertiesCopier;
import workbench.util.VersionNumber;
import workbench.util.WbFile;
import workbench.util.WbPersistence;

/**
 * A connection factory for SQL Workbench/J.
 *
 * @author  Thomas Kellerer
 */
public class ConnectionMgr
{
  private final Map<String, WbConnection> activeConnections = Collections.synchronizedMap(new HashMap<>());
  private final ProfileManager profileMgr;
  private List<DbDriver> drivers;
  private boolean templatesImported;
  private List<PropertyChangeListener> driverChangeListener;
  private final Object driverLock = new Object();
  private SshManager sshManager;

  private final static ConnectionMgr INSTANCE = new ConnectionMgr();

  private ConnectionMgr()
  {
    profileMgr = new ProfileManager(Settings.getInstance().getProfileStorage());

    // make sure the inputPassword is not stored.
    // Only the password property should be stored because that might be encrypted
    WbPersistence.makeTransient(ConnectionProfile.class, "inputPassword");

    WbPersistence.makeTransient(ConnectionProfile.class, "alternateDelimiterString");
    WbPersistence.makeTransient(ConnectionProfile.class, "groups");
  }

  public static ConnectionMgr getInstance()
  {
    return INSTANCE;
  }

  /**
   * Create a new connection.
   *
   * The profile to be used is searched by the given profile key
   *
   * @param profKey        the key of the connection profile profile to be used
   * @param anId           the id to be assigned to the connection
   * @param variablePoolId the ID of the variable pool to be used by the connection scripts
   * @return a new Connection
   *
   * @throws java.lang.ClassNotFoundException
   * @throws java.sql.SQLException
   * @throws java.lang.Exception
   */
  public WbConnection getConnection(ProfileKey profKey, String anId, String variablePoolId)
    throws ClassNotFoundException, SQLException, Exception
  {
    ConnectionProfile prof = getProfile(profKey);
    if (prof == null) return null;

    return this.getConnection(prof, anId, variablePoolId);
  }

  /**
   * Finds a connection based on the ID.
   *
   * For testing purposes only.
   * @param id the id to find
   * @return the connection, null if not found
   */
  public WbConnection findConnection(String id)
  {
    return this.activeConnections.get(id);
  }

  public int getOpenCount()
  {
    return this.activeConnections.size();
  }

  public String getProfilesPath()
  {
    return profileMgr.getProfilesPath();
  }

  public WbFile getProfilesFile()
  {
    return profileMgr.getFile();
  }

  public List<WbFile> getProfileSources()
  {
    return profileMgr.getSourceFiles();
  }

  public void setProfileSource(ConnectionProfile profile, WbFile source)
  {
    profileMgr.setSourceFile(profile, source);
  }

  public synchronized SshManager getSshManager()
  {
    if (sshManager == null)
    {
      sshManager = new SshManager();
    }
    return sshManager;
  }

  public WbConnection getConnection(ConnectionProfile profile, String connId)
    throws ClassNotFoundException, SQLException, UnsupportedClassVersionError, NoConnectionException, SshException
  {
    return getConnection(profile, connId, null);
  }

  /**
   * Create a new connection to the database.
   *
   * @param profile        the connection profile for which to make the connection
   * @param connId         the ID for the connection to be created
   * @param variablePoolId the ID of the variable pool to be used by the connect scripts
   *
   * @return a new phyiscal connection to the database
   *
   * @throws ClassNotFoundException        if the driver class was not found
   * @throws SQLException                  if something went wrong during the connect
   * @throws UnsupportedClassVersionError  if the driver is for a different Java version
   */
  public WbConnection getConnection(ConnectionProfile profile, String connId, String variablePoolId)
    throws ClassNotFoundException, SQLException, UnsupportedClassVersionError, NoConnectionException, SshException
  {
    CallerInfo ci = new CallerInfo(){};
    if (this.activeConnections.containsKey(connId))
    {
      int count = getPrefixCount(connId) + 1;
      String newId = connId + "/" + Integer.toString(count);
      LogMgr.logWarning(ci, "A new connection for ID " + connId + " was requested, but there is already an active one with that ID! Using ID=" + newId + " instead.");
      connId = newId;
    }

    LogMgr.logInfo(ci, "Creating new connection for " + profile.debugString());
    WbConnection conn = this.connect(profile, connId);

    profile.applyProfileVariables(variablePoolId);
    conn.runPostConnectScript(variablePoolId);

    String driverVersion = conn.getDriverVersion();
    String jdbcVersion = conn.getJDBCVersion();
    String dbVersion = conn.getDatabaseProductVersion();
    VersionNumber versionNr = conn.getDatabaseVersion();

    LogMgr.logInfo(ci, "Connected to: [" +
      conn.getMetadata().getProductName() + "], Database version info: [" + dbVersion + "], Database version number: [" + versionNr.toString() + "], Driver version: [" +
      driverVersion + "], JDBC version: [" + jdbcVersion + "], ID: ["  + connId + "]"
    );

    this.activeConnections.put(connId, conn);

    return conn;
  }

  private int getPrefixCount(String idPrefix)
  {
    int count = 0;
    for (String key : activeConnections.keySet())
    {
      if (key.startsWith(idPrefix))
      {
        count++;
      }
    }
    return count;
  }

  public Class loadClassFromDriverLib(ConnectionProfile profile, String className)
    throws ClassNotFoundException, UnsupportedClassVersionError
  {
    String drvClass = profile.getDriverclass();
    String drvName = profile.getDriverName();
    DbDriver drv = this.findDriverByName(drvClass, drvName);
    if (drv == null) return null;
    return drv.loadClassFromDriverLib(className);
  }

  private Properties getConnectionProperties(ConnectionProfile profile)
  {
    Properties props = new Properties(profile.getConnectionProperties());
    if (profile.getOracleSysDBA())
    {
      props.put("internal_logon", "sysdba");
    }
    return props;
  }

  public Connection switchURL(WbConnection toSwitch, String newUrl, String variablePoolId)
    throws SQLException, NoConnectionException, SshException
  {
    try
    {
      Connection sqlConn = doConnect(toSwitch.getProfile(), newUrl, toSwitch.getId());
      closeConnection(toSwitch, false, variablePoolId);
      return sqlConn;
    }
    catch (ClassNotFoundException | UnsupportedClassVersionError ex)
    {
      // This should not happen, as we have already established such a connection
      LogMgr.logError(new CallerInfo(){}, "Could not switch to new URL", ex);
      return null;
    }
  }

  Connection doConnect(ConnectionProfile profile, String targetUrl, String anId)
    throws ClassNotFoundException, SQLException, UnsupportedClassVersionError, NoConnectionException, SshException
  {
    String drvClass = profile.getDriverclass();
    String drvName = profile.getDriverName();

    if (drvClass == null)
    {
      throw new SQLException("No driver class configured");
    }

    DbDriver drv = this.findDriverByName(drvClass, drvName);
    if (drv == null)
    {
      throw new SQLException("Driver class not registered");
    }

    copyPropsToSystem(profile);

    int oldTimeout = DriverManager.getLoginTimeout();
    Connection sqlConn = null;
    try
    {
      int timeout = profile.getConnectionTimeoutValue();
      if (timeout > 0)
      {
        DriverManager.setLoginTimeout(timeout);
      }
      String url = initSSH(profile.getSshConfig(), targetUrl, profile.getKey());
      sqlConn = drv.connect(url, profile.getLoginUser(), profile.getLoginPassword(), anId, getConnectionProperties(profile));
    }
    finally
    {
      DriverManager.setLoginTimeout(oldTimeout);
    }

    if (sqlConn == null)
    {
      throw new NoConnectionException("Could not connect to url: " + targetUrl);
    }

    try
    {
      sqlConn.setAutoCommit(profile.getAutocommit());
    }
    catch (Throwable th)
    {
      // some drivers do not support this, so we just ignore the error
      LogMgr.logInfo(new CallerInfo(){}, "Driver (" + drv.getDriverClass() + ") does not support the autocommit property: " + ExceptionUtil.getDisplay(th));
    }
    return sqlConn;
  }

  WbConnection connect(ConnectionProfile profile, String anId)
    throws ClassNotFoundException, SQLException, UnsupportedClassVersionError, NoConnectionException, SshException {

    Connection sqlConn = doConnect(profile, profile.getActiveUrl(), anId);

    WbConnection conn = new WbConnection(anId, sqlConn, profile);
    if (profile.isReadOnly())
    {
      conn.syncReadOnlyState();
    }

    return conn;
  }

  private String initSSH(SshConfig config, String profileUrl, ProfileKey key)
    throws SshException
  {
    if (config == null)
    {
      return profileUrl;
    }
    return getSshManager().initializeSSHSession(config, profileUrl, key);
  }

  private void copyPropsToSystem(ConnectionProfile profile)
  {
    if (profile != null && profile.getCopyExtendedPropsToSystem())
    {
      PropertiesCopier copier = new PropertiesCopier();
      copier.copyToSystem(profile.getConnectionProperties());
    }
  }

  private void removePropsFromSystem(ConnectionProfile profile)
  {
    if (profile != null && profile.getCopyExtendedPropsToSystem())
    {
      // Check if there is another connection open which uses
      // the same profile. If that is the case the
      // properties should not be removed from the system properties
      for (WbConnection con : activeConnections.values())
      {
        if (con.getProfile().equals(profile))
        {
          return;
        }
      }
      PropertiesCopier copier = new PropertiesCopier();
      copier.removeFromSystem(profile.getConnectionProperties());
    }
  }

  public DbDriver findDriverByName(String drvClassName, String driverName)
  {
    DbDriver firstMatch = null;

    this.readDrivers();

    if (driverName == null || driverName.length() == 0) return this.findDriver(drvClassName);

    for (DbDriver db : drivers)
    {
      if (db == null) continue;

      if (db.getDriverClass() == null)
      {
        LogMgr.logWarning(new CallerInfo(){}, "Got driver without a driver class: " + db.getName() + ", classpath=" + db.getLibraryList());
        continue;
      }

      if (db.getDriverClass().equals(drvClassName))
      {
        // if the classname and the driver name are the same, return the driver immediately
        // If we don't find a match for the name, we'll use the first match for the classname
        if (driverName.equals(db.getName())) return db;
        if (firstMatch == null)
        {
          firstMatch = db;
        }
      }
    }

    // In batch mode the default drivers (DriverTemplates.xml) are not loaded.
    if (firstMatch == null && WbManager.getInstance().isBatchMode())
    {
      // We simple pretend there is one available, this will e.g. make
      // the ODBC Bridge work without a WbDrivers.xml
      return new DbDriver(driverName, drvClassName, null);
    }

    LogMgr.logDebug(new CallerInfo(){}, "Did not find driver with name="+ driverName + ", using " + (firstMatch == null ? "(n/a)" : firstMatch.getName()));

    return firstMatch;
  }

  public DbDriver findDriverByClass(String drvClassName)
  {
    this.readDrivers();

    for (DbDriver driver : this.drivers)
    {
      if (driver.getDriverClass() == null) continue;
      if (drvClassName.equals(driver.getDriverClass()))
      {
        return driver;
      }
    }
    return null;
  }

  public DbDriver findRegisteredDriver(String drvClassName)
  {
    this.readDrivers();

    for (DbDriver driver : this.drivers)
    {
      if (driver.getDriverClass() == null)
      {
        LogMgr.logWarning(new CallerInfo(){}, "Got driver without a driver class: " + driver.getName() + ", classpath=" + driver.getLibraryList());
        continue;
      }
      if (drvClassName.equals(driver.getDriverClass()) && driver.canReadLibrary())
      {
        return driver;
      }
    }
    return null;
  }

  public DbDriver findDriver(String drvClassName)
  {
    if (drvClassName == null)
    {
      LogMgr.logDebug(new CallerInfo(){}, "Called with a null classname!", new Exception("Backtrace"));
      return null;
    }

    DbDriver db = this.findRegisteredDriver(drvClassName);

    if (db == null)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Did not find a registered driver with classname = ["+drvClassName+"]");
      try
      {
        // not found --> maybe it's present in the normal classpath...
        // eg the ODBC bridge
        Class<? extends Driver> drvcls = (Class<? extends Driver>)Class.forName(drvClassName);
        Driver drv = drvcls.getDeclaredConstructor().newInstance();
        db = new DbDriver(drv);
      }
      catch (Exception cnf)
      {
        LogMgr.logError(new CallerInfo(){}, "Error creating instance for driver class [" + drvClassName + "] ", cnf);
        db = null;
      }
    }
    return db;
  }

  /**
   * Add a new, dynamically defined driver to the list of available drivers.
   *
   * This is used if a driver definition is passed on the commandline.
   *
   * Drivers registered through this, won't be persisted into WbDrivers.xml
   *
   * @param drvClassName  the classname of the driver
   * @param jarFile       the jarfile in which the driver is located
   * @return a new DbDriver instance.
   *
   * @see workbench.sql.BatchRunner#createCmdLineProfile(workbench.util.ArgumentParser)
   */
  public DbDriver registerDriver(String drvClassName, String jarFile)
  {
    this.readDrivers();

    DbDriver drv = new DbDriver("$JdbcDriver$-" + Integer.toString(drivers.size() + 1), drvClassName, jarFile);
    drv.setTemporary();

    // this method is called from BatchRunner.createCmdLineProfile() when
    // the user passed all driver information on the command line.
    // as most likely this is the correct driver it has to be put
    // at the beginning of the list, to prevent a different driver
    // with the same driver class in WbDrivers.xml to be used instead
    this.drivers.add(0,drv);

    return drv;
  }

  /**
   * Returns a List of registered drivers.
   * This list is read from WbDrivers.xml
   *
   * @return all registered drivers
   * @see #registerDriver(java.lang.String, java.lang.String)
   */
  public List<DbDriver> getDrivers()
  {
    this.readDrivers();
    return this.drivers;
  }

  public void setDrivers(List<DbDriver> aDriverList)
  {
    this.drivers = new ArrayList<>(aDriverList);
    if (this.driverChangeListener != null)
    {
      PropertyChangeEvent evt = new PropertyChangeEvent(this, "drivers", null, null);
      for (PropertyChangeListener l : this.driverChangeListener)
      {
        l.propertyChange(evt);
      }
    }
  }

  public void addDriverChangeListener(PropertyChangeListener l)
  {
    if (this.driverChangeListener == null) this.driverChangeListener = new ArrayList<>();
    this.driverChangeListener.add(l);
  }

  public void removeDriverChangeListener(PropertyChangeListener l)
  {
    if (this.driverChangeListener == null) return;
    this.driverChangeListener.remove(l);
  }

  /**
   * Return a list with profile keys that can be displayed to the user.
   *
   * @return all profiles keys (sorted).
   */
  public List<String> getProfileKeys()
  {
    synchronized (profileMgr)
    {
      profileMgr.ensureLoaded();
      return profileMgr.getProfileKeys();
    }
  }

  /**
   * Returns a List with the current profiles.
   *
   * @return  all connection profiles.
   */
  public List<ConnectionProfile> getProfiles()
  {
    LogMgr.logTrace(new CallerInfo(){}, "getProfiles() called at " + System.currentTimeMillis() + " from " + Thread.currentThread().getName());

    synchronized (profileMgr)
    {
      profileMgr.ensureLoaded();
      return profileMgr.getProfiles();
    }
  }

  /**
   * Re-load the profiles, regardless whether they have been loaded or not.
   * This should only be used in unit-testing
   */
  public void reloadProfiles()
  {
    synchronized (profileMgr)
    {
      profileMgr.load();
    }
  }

  /**
   * Disconnects all connections.
   *
   * @see #closeConnection(workbench.db.WbConnection)
   */
  public void disconnectAll()
  {
    disconnectAll(false);
  }

  public void disconnectAll(boolean saveCaches)
  {
    for (WbConnection con : this.activeConnections.values())
    {
      if (saveCaches)
      {
        DbObjectCacheFactory.getInstance().saveCache(con);
      }
      this.closeConnection(con);
    }
    activeConnections.clear();
    if (sshManager != null)
    {
      sshManager.disconnectAll();
    }
    DbObjectCacheFactory.getInstance().clear();
  }

  /**
   * Close all connections in a background thread.
   *
   * The list of active connections is cleared immediately, so any getConnection() after
   * calling this method, will create a new physical connection, even if the current ones
   * have not all been disconnected.
   *
   * @param toAbort the connections to abort
   */
  public void abortAll(List<WbConnection> toAbort)
  {
    if (CollectionUtil.isEmpty(toAbort)) return;

    for (WbConnection con : toAbort)
    {
      if (con != null)
      {
        activeConnections.remove(con.getId());
      }
    }

    for (WbConnection con : toAbort)
    {
      if (con != null)
      {
        con.shutdownInBackround();
      }
    }
  }

  public String listActiveConnections()
  {
    StringBuilder msg = new StringBuilder(activeConnections.size() * 20);
    for (WbConnection conn : activeConnections.values())
    {
      msg.append("Active connection: ");
      msg.append((conn == null ? "(null)" : conn.toString() + ", busy: " + conn.isBusy()));
      msg.append('\n');
    }
    return msg.toString().trim();
  }

  public void dumpConnections()
  {
    if (LogMgr.isDebugEnabled())
    {
      String msg = listActiveConnections();
      if (msg.length() > 0)
      {
        LogMgr.logDebug(new CallerInfo(){}, msg);
      }
      else
      {
        LogMgr.logDebug(new CallerInfo(){}, "No more active connections.");
      }
    }
  }

  public void disconnect(WbConnection con)
  {
    if (con == null) return;
    this.activeConnections.remove(con.getId());
    LogMgr.logDebug(new CallerInfo(){}, "Trying to physically close the connection with id=" + con.getId());
    this.closeConnection(con);
  }

  /**
   * Disconnect the given connection.
   * @param conn the connection to disconnect.
   */
  private void closeConnection(WbConnection conn)
  {
    closeConnection(conn, true, null);
  }

  private void closeConnection(WbConnection conn, boolean releaseSsh, String variablePoolId)
  {
    if (conn == null) return;
    if (conn.isClosed()) return;

    try
    {
      long start = System.currentTimeMillis();
      if (conn.getProfile() != null)
      {
        LogMgr.logInfo(new CallerInfo(){}, "Disconnecting: [" + conn.getProfile().getName() + "], ID=" + conn.getId());
      }
      else
      {
        LogMgr.logInfo(new CallerInfo(){}, "Disconnecting connection with ID=" + conn.toString());
      }

      conn.runPreDisconnectScript(variablePoolId);
      removePropsFromSystem(conn.getProfile());

      DbShutdownHook hook = DbShutdownFactory.getShutdownHook(conn);
      if (hook != null)
      {
        hook.shutdown(conn);
      }
      else
      {
        conn.shutdown();
      }
      if (releaseSsh)
      {
        ConnectionProfile profile = conn.getProfile();
        if (profile.getSshConfig() != null)
        {
          getSshManager().decrementUsage(profile.getSshConfig());
        }
      }
      long duration = System.currentTimeMillis() - start;
      LogMgr.logDebug(new CallerInfo(){}, "Disconnecting connection with ID=" + conn.toString() + " took " + duration + "ms");
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, ResourceMgr.getString("ErrOnDisconnect"), e);
    }
  }

  /**
   * Check if there is another connection active with the same URL.
   *
   * This is used when the connection to an embedded database that
   * needs a {@link workbench.db.shutdown.DbShutdownHook} is called.
   *
   * @param aConn the connection to test
   * @return true if there is another active connection.
   */
  public boolean isActive(WbConnection aConn)
  {
    String url = aConn.getUrl();
    String id = aConn.getId();

    for (WbConnection conn : this.activeConnections.values())
    {
      if (conn == null) continue;

      if (conn.getId().equals(id)) continue;

      String u = conn.getUrl();
      if (u == null) continue;
      // we found one connection with the same URL
      if (u.equals(url)) return true;
    }

    return false;
  }

  /**
   * Saves the driver definitions to an external file.
   *
   * The name of the file defaults to <tt>WbDrivers.xml</tt>. The exact location
   * can be set in the configuration file.
   * @see workbench.resource.Settings#getDriverConfigFilename()
   * @see WbPersistence#writeObject(Object)
   */
  public void saveDrivers()
  {
    if (Settings.getInstance().getCreateDriverBackup())
    {
      WbFile f = new WbFile(Settings.getInstance().getDriverConfigFilename());
      FileUtil.createBackup(f);
    }

    WbPersistence writer = new WbPersistence(Settings.getInstance().getDriverConfigFilename());

    // As drivers and profiles can be saved in console mode, we need to make
    // sure, that the "internal" drivers that are created "on-the-fly" when connecting
    // from the commandline are not stored in the configuration file.
    List<DbDriver> allDrivers = drivers.stream().filter(drv -> !drv.isTemporaryDriver()).collect(Collectors.toList());
    try
    {
      writer.writeObject(allDrivers);
    }
    catch (Throwable e)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not save drivers", e);
    }
  }

  private void readDrivers()
  {
    synchronized (driverLock)
    {
      if (this.drivers != null) return;

      LogMgr.logInfo(new CallerInfo(){}, "Using libdir: " + Settings.getInstance().getLibDir());
      try
      {
        WbPersistence reader = new WbPersistence(Settings.getInstance().getDriverConfigFilename());
        Object result = reader.readObject();
        if (result == null)
        {
          this.drivers = Collections.synchronizedList(new ArrayList<>());
        }
        else if (result instanceof ArrayList)
        {
          this.drivers = Collections.synchronizedList((List<DbDriver>) result);
          fixOracleSampleURL();
        }
      }
      catch (FileNotFoundException fne)
      {
        LogMgr.logDebug(new CallerInfo(){}, "WbDrivers.xml not found. Using defaults.");
        this.drivers = null;
      }
      catch (Throwable e)
      {
        LogMgr.logDebug(new CallerInfo(){}, "Could not load driver definitions. Creating new one...", e);
        this.drivers = null;
      }

      if (this.drivers == null)
      {
        this.drivers = Collections.synchronizedList(new ArrayList<>());
      }
    }
    if (readDriverTemplates())
    {
      this.importTemplateDrivers();
    }
  }

  private void fixOracleSampleURL()
  {
    if (drivers == null) return;
    for (DbDriver drv : drivers)
    {
      if (drv.getDriverClass().equals("oracle.jdbc.OracleDriver") && drv.getSampleUrl() != null && drv.getSampleUrl().contains("thin://@"))
      {
        String oldUrl = drv.getSampleUrl();
        String fixedUrl = oldUrl.replace("thin://@", "thin:@//");
        drv.setSampleUrl(fixedUrl);
        LogMgr.logInfo(new CallerInfo(){}, "Changed invalid sample URL in driver \"" + drv.getName() + "\" from: " + oldUrl + " to: " + fixedUrl);
      }
    }
  }

  private boolean readDriverTemplates()
  {
    return Settings.getInstance().getBoolProperty(Settings.PROP_READ_DRIVER_TEMPLATES, true);
  }

  private void importTemplateDrivers()
  {
    synchronized (driverLock)
    {
      if (this.templatesImported) return;

      this.readDrivers();

      List<DbDriver> templates = getDriverTemplates();
      for (DbDriver drv : templates)
      {
        if (!this.isDriverNameUsed(drv.getName()))
        {
          this.drivers.add(drv);
        }
      }
    }
    this.templatesImported = true;
  }

  private boolean isDriverNameUsed(String aName)
  {
    for (DbDriver db : drivers)
    {
      if (db.getName().equals(aName))
      {
        return true;
      }
    }
    return false;
  }

  public List<DbDriver> getDriverTemplates()
  {
    if (WbManager.getInstance() == null)
    {
      // this can happen in test mode
      return new ArrayList<>();
    }

    List<DbDriver> templates = null;
    InputStream in = null;
    try
    {
      in = openDriverTemplatesFile();
      WbPersistence reader = new WbPersistence();
      templates = (List<DbDriver>) reader.readObject(in);
    }
    catch (Throwable io)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not read driver templates!", io);
      templates = new ArrayList<>();
    }
    finally
    {
      FileUtil.closeQuietely(in);
    }
    return templates;
  }

  private InputStream openDriverTemplatesFile()
    throws IOException
  {
    ClasspathUtil cp = new ClasspathUtil();
    WbFile f = new WbFile(cp.getJarPath(), "DriverTemplates.xml");
    if (f.exists())
    {
      LogMgr.logInfo(new CallerInfo(){}, "Reading external DriverTemplates from " + f.getFullPath());
      return new FileInputStream(f);
    }
    return this.getClass().getResourceAsStream("DriverTemplates.xml");
  }

  public ConnectionProfile getProfile(ProfileKey key)
  {
    synchronized (profileMgr)
    {
      profileMgr.ensureLoaded();
      return profileMgr.getProfile(key);
    }
  }

  /**
   * Save the connection profiles.
   *
   * This will also resetChangedFlags the changed flag for any modified or new
   * profiles. The name of the file defaults to <tt>WbProfiles.xml</tt>, but
   * can be defined in the configuration properties.
   *
   * @see ProfileManager#save()
   */
  public void saveProfiles()
  {
    synchronized (profileMgr)
    {
      if (profileMgr.isLoaded())
      {
        profileMgr.save();
      }
    }
  }

  /**
   * Returns true if any of the profile definitions has changed.
   * (Or if a profile has been deleted or added)
   *
   * @return true if at least one profile has been changed, deleted or added
   *
   * @see ProfileManager#profilesAreModified()
   */
  public boolean profilesAreModified()
  {
    synchronized (profileMgr)
    {
      profileMgr.ensureLoaded();
      return profileMgr.profilesAreModified();
    }
  }

  public void applyProfiles(List<ConnectionProfile> newProfiles)
  {
    synchronized (profileMgr)
    {
      profileMgr.ensureLoaded();
      profileMgr.applyProfiles(newProfiles);
    }
  }

  public void removeProfile(ConnectionProfile profile)
  {
    synchronized (profileMgr)
    {
      profileMgr.ensureLoaded();
      profileMgr.removeProfile(profile);
    }
  }

  public void addProfile(ConnectionProfile profile)
  {
    synchronized (profileMgr)
    {
      profileMgr.ensureLoaded();
      profileMgr.addProfile(profile);
    }
  }

  public void clearProfiles()
  {
    synchronized (profileMgr)
    {
      profileMgr.reset();
    }
  }
}
