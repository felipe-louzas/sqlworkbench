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
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.SQLWarning;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

import workbench.interfaces.DbExecutionListener;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;
import workbench.ssh.SshException;

import workbench.db.greenplum.GreenplumUtil;
import workbench.db.mssql.SqlServerUtil;
import workbench.db.objectcache.DbObjectCache;
import workbench.db.objectcache.DbObjectCacheFactory;
import workbench.db.oracle.OracleUtils;
import workbench.db.oracle.OracleWarningsClearer;

import workbench.sql.DelimiterDefinition;
import workbench.sql.EndReadOnlyTrans;
import workbench.sql.ErrorDescriptor;
import workbench.sql.ErrorReportLevel;
import workbench.sql.StatementRunner;
import workbench.sql.StatementRunnerResult;
import workbench.sql.parser.ParserType;
import workbench.sql.parser.ScriptParser;
import workbench.sql.preparedstatement.PreparedStatementPool;
import workbench.sql.wbcommands.StringResultLogger;

import workbench.util.DdlObjectInfo;
import workbench.util.ExceptionUtil;
import workbench.util.SqlParsingUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;
import workbench.util.VersionNumber;
import workbench.util.WbThread;

/**
 *
 * @author  Thomas Kellerer
 */
public class WbConnection
  implements DbExecutionListener
{
  public static final String PROP_CATALOG = "catalog";
  public static final String PROP_CATALOG_LIST = "catalog-list";
  public static final String PROP_SCHEMA = "schema";
  public static final String PROP_AUTOCOMMIT = "autocommit";
  public static final String PROP_CONNECTION_STATE = "state";
  public static final String CONNECTION_CLOSED = "closed";
  public static final String CONNECTION_OPEN = "open";
  public static final String PROP_READONLY = "readonly";
  public static final String PROP_BUSY = "busy";

  // version information is cached to avoid
  // blocks on the connection if getDatabaseVersion() is called in the background.
  private VersionNumber dbVersion;
  private String dbProductVersion;
  private String dbProductName;
  private String driverVersion;

  private final String id;
  private StringBuilder scriptError;
  private Connection sqlConnection;
  private DbMetadata metaData;
  private final ConnectionProfile profile;
  private PreparedStatementPool preparedStatementPool;
  private final List<PropertyChangeListener> listeners = Collections.synchronizedList(new ArrayList<>(1));

  private OracleWarningsClearer oracleWarningsClearer;
  private boolean hasOracleContainers;

  private boolean busy;
  private boolean lastAutocommitState;
  private KeepAliveDaemon keepAlive;
  private String currentCatalog;
  private String currentSchema;

  private boolean removeComments;
  private boolean removeNewLines;
  private Integer fetchSize;

  private boolean supportsGetWarnings = true;

  private Boolean sessionReadOnly;
  private Boolean sessionConfirmUpdates;
  private final Map<String, String> sessionProps = new HashMap<>();
  private DdlObjectInfo lastDdlObject;
  private SqlParsingUtil keywordUtil;
  private boolean pingAvailable = true;
  private boolean supportsSavepoints = true;
  private boolean shared = false;
  private String switchedUrl;

  private boolean logSavepoints;
  private final List<Integer> usedSavepoints = new ArrayList<>(2);
  private final List<PropertyChangeEvent> pendingEvents = new ArrayList<>();

  public WbConnection(String id, ConnectionProfile profile)
  {
    this.id = id;
    this.profile = profile;
  }

  /**
   * Create a new wrapper connection around the original SQL connection.
   *
   * This will also initialize a {@link DbMetadata} instance.
   *
   * The {@link #removeComments} property is initialized from the connection profile
   * and the configuration for the current DBMS
   *
   * @see DbSettings#supportsCommentInSql
   */
  public WbConnection(String anId, Connection aConn, ConnectionProfile aProfile)
    throws SQLException
  {
    this.id = anId;
    this.profile = aProfile;
    setSqlConnection(aConn);

    supportsSavepoints = supportsSavepoints();
    initKeepAlive();

    // removeComments and removeNewLines are properties that are needed each time a SQL statement is executed
    // To speed up SQL parsing, the value for those properties are "cached" here
    if (profile != null)
    {
      removeComments = profile.getRemoveComments();
    }

    if (metaData != null)
    {
      DbSettings db = metaData.getDbSettings();
      removeNewLines = db.removeNewLinesInSQL();
      logSavepoints = db.getBoolProperty("savepoints.debug", true);
    }

    if (profile != null)
    {
      lastAutocommitState = profile.getAutocommit();
    }
  }

  public void switchURL(String newURL, CatalogInformationReader catReader, String variablePoolId)
    throws SQLException
  {
    boolean wasBusy = this.isBusy();
    try
    {
      this.setBusy(false);
      String oldDb = catReader == null ? this.getCurrentCatalog() : catReader.getCurrentCatalog();
      Connection newConn = ConnectionMgr.getInstance().switchURL(this, newURL, variablePoolId);
      setSqlConnection(newConn);
      if (profile != null)
      {
        lastAutocommitState = profile.getAutocommit();
      }
      switchedUrl = newURL;
      String newDb = catReader == null ? this.getCurrentCatalog() : catReader.getCurrentCatalog();
      getObjectCache().clear();
      fireConnectionStateChanged(PROP_CATALOG, oldDb, newDb);
    }
    catch (SshException ssh)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not initialize SSH session when switching URLs", ssh);
    }
    finally
    {
      this.setBusy(wasBusy);
    }
  }

  public boolean isShared()
  {
    return shared;
  }

  public void setShared(boolean flag)
  {
    this.shared = flag;
  }

  public synchronized SqlParsingUtil getParsingUtil()
  {
    if (keywordUtil == null)
    {
      keywordUtil = new SqlParsingUtil(this);
    }
    return keywordUtil;
  }

  public void setLastDDLObject(DdlObjectInfo object)
  {
    this.lastDdlObject = object;
  }

  public DdlObjectInfo getLastDdlObjectInfo()
  {
    return this.lastDdlObject;
  }

  public void setSessionProperty(String key, String value)
  {
    sessionProps.put(key, value);
  }

  public String getSessionProperty(String key)
  {
    return sessionProps.get(key);
  }

  public boolean hasMultipleOracleContainers()
  {
    return hasOracleContainers;
  }

  public DelimiterDefinition getAlternateDelimiter()
  {
    return Settings.getInstance().getAlternateDelimiter(this, null);
  }

  public TransactionChecker getTransactionChecker()
  {
    if (getProfile().getDetectOpenTransaction())
    {
      return TransactionChecker.Factory.createChecker(this);
    }
    return TransactionChecker.NO_CHECK;
  }

  public ObjectNameFilter getCatalogFilter()
  {
    return profile == null ? null : profile.getCatalogFilter();
  }

  public ObjectNameFilter getSchemaFilter()
  {
    return profile == null ? null : profile.getSchemaFilter();
  }

  public boolean getRemoveComments()
  {
    return removeComments;
  }

  public boolean getRemoveNewLines()
  {
    return removeNewLines;
  }
  /**
   * Returns the internal ID of this connection.
   *
   * @return the internal id of this connection.
   */
  public String getId()
  {
    return this.id;
  }

  public String getDbId()
  {
    if (getDbSettings() == null) return null;
    return getDbSettings().getDbId();
  }

  public void resetSessionReadOnly()
  {
    if (sessionReadOnly != null)
    {
      sessionReadOnly = null;
      fireConnectionStateChanged(PROP_READONLY, null, null);
      syncReadOnlyState();
    }
  }

  public void resetSessionFlags()
  {
    boolean wasSet = sessionReadOnly != null;
    sessionReadOnly = null;
    sessionConfirmUpdates = null;
    if (wasSet)
    {
      fireConnectionStateChanged(PROP_READONLY, null, null);
      syncReadOnlyState();
    }
  }

  public void setSessionReadOnly(boolean flag)
  {
    boolean oldValue = sessionReadOnly == null ? false : sessionReadOnly;
    boolean wasSet = sessionReadOnly != null;

    sessionReadOnly = flag;
    if (flag)
    {
      sessionConfirmUpdates = !flag;
    }
    if (!wasSet || oldValue != flag)
    {
      fireConnectionStateChanged(PROP_READONLY, Boolean.toString(oldValue), Boolean.toString(flag));
      syncReadOnlyState();
    }
  }

  /**
   * Synchronises the current state of the read only flag with the readOnly property of the SQL connection.
   *
   * @see #isSessionReadOnly()
   * @see #setSessionReadOnly(boolean)
   * @see Connection#setReadOnly(boolean)
   * @see DbSettings#syncConnectionReadOnlyState()
   */
  public void syncReadOnlyState()
  {
    if (!getDbSettings().syncConnectionReadOnlyState()) return;

    try
    {
      if (sqlConnection.isReadOnly() == isSessionReadOnly()) return;

      ReadOnlyChanger changer = ReadOnlyChanger.Factory.createChanger(this);
      boolean readOnly = isSessionReadOnly();
      changer.setReadOnly(this, readOnly);
    }
    catch (Throwable th)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not change read only flag", th);
    }
  }

  public boolean isSessionReadOnly()
  {
    if (sessionReadOnly != null) return sessionReadOnly;
    return getProfile().isReadOnly();
  }

  public void setSessionConfirmUpdate(boolean flag)
  {
    sessionConfirmUpdates = flag;
    if (flag)
    {
      sessionReadOnly = !flag;
      syncReadOnlyState();
    }
  }

  public boolean confirmUpdatesInSession()
  {
    if (sessionConfirmUpdates != null) return sessionConfirmUpdates;
    return getProfile().getConfirmUpdates();
  }

  public int getIsolationLevel()
  {
    if (sqlConnection == null) return Connection.TRANSACTION_NONE;
    try
    {
      return sqlConnection.getTransactionIsolation();
    }
    catch (Throwable sql)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not retrieve isolation level", sql);
      return Connection.TRANSACTION_NONE;
    }
  }

  public void setIsolationLevel(int newLevel)
  {
    if (sqlConnection == null) return;
    if (newLevel == Connection.TRANSACTION_NONE) return;

    try
    {
      sqlConnection.setTransactionIsolation(newLevel);
    }
    catch (Throwable sql)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not set isolation level", sql);
    }
  }


  /**
   * Returns the current isolation level as a readable string
   */
  public String getIsolationLevelName()
  {
    if (this.sqlConnection == null) return "";

    try
    {
      return SqlUtil.getIsolationLevelName(sqlConnection.getTransactionIsolation());
    }
    catch (Throwable e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error retrieving isolation level", e);
    }
    return "n/a";
  }

  public PreparedStatementPool getPreparedStatementPool()
  {
    if (this.preparedStatementPool == null)
    {
      this.preparedStatementPool = new PreparedStatementPool(this);
    }
    return this.preparedStatementPool;
  }

  public DbObjectCache getObjectCache()
  {
    return DbObjectCacheFactory.getInstance().getCache(this);
  }

  /**
   * Returns a "cached" version of the current schema. It is safe
   * to call this method any time as it does not send any
   * statement to the database, but might not necessarily
   * return the correct schema
   */
  public String getDisplaySchema()
  {
    return currentSchema;
  }

  /**
   * Return the current schema of the connection.
   * This will send a query to the database, so this might
   * not be usable if a statement or a transaction is currently
   * in progress.
   */
  public String getCurrentSchema()
  {
    if (metaData == null) return null;
    return this.metaData.getCurrentSchema();
  }

  /**
   * Return the name of the current user.
   * <br/>
   * @return the current user as returned by the database.
   */
  public String getCurrentUser()
  {
    if (this.profile != null && !profile.getPromptForUsername())
    {
      return this.profile.getLoginUser();
    }

    try
    {
      return this.sqlConnection.getMetaData().getUserName();
    }
    catch (Throwable e)
    {
      LogMgr.logDebug(new CallerInfo(){}, "Could not retrieve current database user", e);
      return StringUtil.EMPTY_STRING;
    }
  }

  private String getWindowsUser()
  {
    String url = this.getUrl();
    if (StringUtil.isEmpty(url)) return StringUtil.EMPTY_STRING;

    if (url.startsWith("jdbc:sqlserver:") && url.contains("integratedSecurity=true"))
    {
      String userName = System.getProperty("user.name");
      String domain = System.getenv("userdomain");
      if (domain != null)
      {
        return domain + "\\" + userName;
      }
      return userName;
    }
    return StringUtil.EMPTY_STRING;
  }

  public boolean supportsQueryTimeout()
  {
    if (this.metaData == null) return false;
    return this.metaData.getDbSettings().supportsQueryTimeout();
  }

  /**
   * @return The profile associated with this connection
   */
  public ConnectionProfile getProfile()
  {
    return this.profile;
  }

  public boolean trimCharData()
  {
    if (profile == null) return false;
    return this.profile.getTrimCharData();
  }

  void runPreDisconnectScript(String variablePoolId)
  {
    if (this.keepAlive != null)
    {
      this.keepAlive.shutdown();
    }
    if (this.profile == null) return;
    if (this.sqlConnection == null) return;
    String sql = profile.getPreDisconnectScript();
    runConnectScript(sql, "disconnect", variablePoolId, false);
  }

  void runPostConnectScript(String variablePoolId)
  {
    if (this.profile == null) return;
    if (this.sqlConnection == null) return;
    String sql = profile.getPostConnectScript();
    runConnectScript(sql, "connect", variablePoolId, profile.getEchoConnectScriptStatements());
    applyFilterReplacements(getSchemaFilter());
    applyFilterReplacements(getCatalogFilter());
  }

  private void applyFilterReplacements(ObjectNameFilter filter)
  {
    if (filter == null) return;
    Map<String, String> replacements = new HashMap<>();
    replacements.put(ObjectNameFilter.PARAM_CURRENT_USER, getCurrentUser());
    replacements.put(ObjectNameFilter.PARAM_CURRENT_SCHEMA, getCurrentSchema());
    replacements.put(ObjectNameFilter.PARAM_CURRENT_CATALOG, getCurrentCatalog());
    filter.setReplacements(replacements);
  }

  private synchronized void runConnectScript(String sql, String type, String variablePoolId, boolean echoStatements)
  {
    if (StringUtil.isBlank(sql)) return;

    final CallerInfo ci = new CallerInfo(){};
    LogMgr.logInfo(ci, "Executing " + type + " script for connection [" + getDbId() + "]: "+ getDisplayString(true) + " ..." );

    StatementRunner runner = new StatementRunner();
    runner.setVariablePoolID(variablePoolId);
    runner.setConnection(this);
    runner.setErrorReportLevel(ErrorReportLevel.none);

    StringResultLogger logger = new StringResultLogger();
    runner.setMessageLogger(logger);

    ScriptParser p = new ScriptParser(sql);
    p.setParserType(ParserType.getTypeFromConnection(this));

    // The statemenRunner will call clearMessages() when statementDone()
    // is called which in turn will call clearWarnings() on this instance.
    // This will also clear the scriptError and thus all messages
    // that are collected here. So I have to store the messages locally
    // and cannot use the scriptError variable directly
    StringBuilder messages = new StringBuilder(150);
    String resKey = "MsgConnScript" + type;

    String command = null;
    try
    {
      int count = p.getSize();
      for (int i=0; i < count; i++)
      {
        command = p.getCommand(i);
        String stmtSql = StringUtil.getMaxSubstring(SqlUtil.makeCleanSql(command, false, false, true, this),250);

        try
        {
          StatementRunnerResult result = runner.runStatement(command);
          if (echoStatements)
          {
            String msg = ResourceMgr.getString(resKey) + " " + stmtSql + "\n";
            messages.append(msg);
          }

          LogMgr.logDebug(ci, "  (" + getId() + ") Executed statement: " + stmtSql);
          if (!result.isSuccess())
          {
            messages.append("\n  ");
            messages.append(ResourceMgr.getString("TxtError"));
            messages.append(": ");
            ErrorDescriptor error = result.getErrorDescriptor();
            if (error != null)
            {
              messages.append(error.getErrorMessage());
            }
            messages.append("\n");
          }

          if (result.hasMessages())
          {
            messages.append(result.getMessages());
            messages.append("\n");
          }
          else if (logger.getMessages().getLength() > 0)
          {
            messages.append(logger.getMessages().getMessage());
            messages.append("\n");
          }
        }
        finally
        {
          runner.statementDone();
        }
      }
      messages.append("\n");
    }
    catch (Throwable e)
    {
      LogMgr.logError(ci, "Error executing " + type + " script for connection: " + getId(), e);
      messages = new StringBuilder(50);
      messages.append(ResourceMgr.getString("MsgBatchStatementError"));
      messages.append(": ");
      messages.append(command);
      messages.append('\n');
      messages.append(e.getMessage());
      messages.append('\n');
    }
    finally
    {
      runner.done();
    }
    this.scriptError = messages;
  }

  private void setSqlConnection(Connection aConn)
    throws SQLException
  {
    this.sqlConnection = aConn;
    this.metaData = new DbMetadata(this);

    if (DBID.Oracle.isDB(this))
    {
      if (!JdbcUtils.hasMiniumDriverVersion(this.getSqlConnection(), "10.0"))
      {
        oracleWarningsClearer = new OracleWarningsClearer();
      }
      hasOracleContainers = OracleUtils.hasMultipleContainers(this);
    }

    this.currentCatalog = metaData.getCurrentCatalog();
  }

  /**
   * Return any warnings that are stored in the underlying SQL Connection.
   * The warnings are then cleared from the connection object.
   *
   * @see #clearWarnings()
   * @return any warnings reported from the server, null if no warnings are available.
   */
  public String getWarnings()
  {
    if (!supportsGetWarnings) return null;

    try
    {
      SQLWarning warn = this.getSqlConnection().getWarnings();
      if (warn == null)
      {
        if (this.scriptError != null)
        {
          String error = this.scriptError.toString();
          this.scriptError = null;
          return error;
        }
        return null;
      }

      StringBuilder msg = new StringBuilder(200);
      if (!StringUtil.isEmpty(this.scriptError)) msg.append(this.scriptError);

      String s;
      while (warn != null)
      {
        s = warn.getMessage();
        msg.append('\n');
        msg.append(s);
        warn = warn.getNextWarning();
      }
      this.clearWarnings();
      return msg.toString();
    }
    catch (UnsupportedOperationException e)
    {
      supportsGetWarnings = false;
      LogMgr.logWarning(new CallerInfo(){}, "getWarnings() not supported by the driver");
      return null;
    }
    catch (Throwable e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error when retrieving SQL Warnings", e);
      return null;
    }
  }

  /**
   *  This will clear the warnings from the connection object.
   *
   *  Some drivers will not replace existing warnings until clearWarnings()
   *  is called, thus SQL Workbench would show the same error message over and
   *  over again.
   */
  public void clearWarnings()
  {
    this.scriptError = null;
    if (this.sqlConnection == null) return;

    try
    {
      this.sqlConnection.clearWarnings();
      if (oracleWarningsClearer != null)
      {
        oracleWarningsClearer.clearWarnings(sqlConnection);
      }
    }
    catch (Throwable th)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Error resetting warnings!", th);
    }
  }

  public Connection getSqlConnection()
  {
    return this.sqlConnection;
  }

  public void commit()
    throws SQLException
  {
    if (getAutoCommit())
    {
      LogMgr.logTrace(new CallerInfo(){}, "Commit() called on a connection with autocommit enabled", new Exception("Traceback"));
      return;
    }

    usedSavepoints.clear();
    if (getDbSettings().supportsTransactions())
    {
      this.sqlConnection.commit();
    }
  }

  private String debugString(Savepoint sp)
  {
    if (sp == null) return "null";

    try
    {
      return Integer.toString(sp.getSavepointId());
    }
    catch (Throwable th)
    {
      return sp.toString();
    }
  }

  private void logSavepoint(CallerInfo ci, String what, Savepoint sp)
  {
    if (sp == null) return;
    if (logSavepoints && LogMgr.isDebugEnabled())
    {
      if (ci == null) ci = new CallerInfo(){};
      LogMgr.logDebug(ci, what + " savepoint #" + debugString(sp) + ", connection: [" + id + "]");
    }
  }

  public Savepoint setSavepoint()
    throws SQLException
  {
    return setSavepoint(null);
  }

  public synchronized Savepoint setSavepoint(CallerInfo context)
    throws SQLException
  {
    if (this.getAutoCommit()) return null;
    if (!supportsSavepoints) return null;

    try
    {
      Savepoint sp = this.sqlConnection.setSavepoint();
      usedSavepoints.add(sp.getSavepointId());
      logSavepoint(context, "Set", sp);
      return sp;
    }
    catch (SQLFeatureNotSupportedException | AbstractMethodError ex)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Savepoints not supported", ex);
      supportsSavepoints = false;
    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not set Savepoint", ex);
    }
    return null;
  }

  /**
   * A non-exception throwing wrapper around Connection.rollback(Savepoint)
   */
  public void rollback(Savepoint sp)
  {
    rollback(sp, null);
  }

  public synchronized void rollback(Savepoint sp, CallerInfo context)
  {
    if (sp == null) return;
    if (this.sqlConnection == null) return;
    if (this.getAutoCommit()) return;
    int spId = -1;
    try
    {
      spId = sp.getSavepointId();
      if (!usedSavepoints.contains(spId))
      {
        LogMgr.logWarning(new CallerInfo(){}, "Rollback for non existing savepoint with ID=" + spId + " on connection [" + id + "] called. Context: " + context);
      }
      logSavepoint(context, "Rollback", sp);
      sqlConnection.rollback(sp);
    }
    catch (Throwable e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error rolling back savepoint (context: " + context + ") for connection: " + id, e);
    }
    finally
    {
      removeSavepoint(spId, context);
    }
  }

  private void removeSavepoint(int spId, CallerInfo context)
  {
    int index = usedSavepoints.indexOf(spId);
    if (index < 0) return;

    List<Integer> toRemove = usedSavepoints.subList(index, usedSavepoints.size());
    if (toRemove.size() > 1)
    {
      String msg = "Removing savepoint ID=" + spId + " on connection [" + this.id + "] removes " + toRemove;
      if (context != null)
      {
        msg += " Context: " + context;
      }
      LogMgr.logWarning(new CallerInfo(){}, msg);
    }
    toRemove.clear();
  }

  /**
   * A non-exception throwing wrapper around Connection.releaseSavepoint(Savepoint)
   */
  public void releaseSavepoint(Savepoint sp)
  {
    releaseSavepoint(sp, null);
  }

  public synchronized void releaseSavepoint(Savepoint sp, CallerInfo context)
  {
    if (sp == null) return;
    if (this.sqlConnection == null) return;
    if (getAutoCommit()) return;

    int spId = -1;
    try
    {
      spId = sp.getSavepointId();
      if (!usedSavepoints.contains(spId))
      {
        LogMgr.logWarning(new CallerInfo(){}, "Release for non existing savepoint with ID=" + spId + " on connection [" + id + "] called, Context: " + context);
      }
      else
      {
        logSavepoint(context, "Release", sp);
        sqlConnection.releaseSavepoint(sp);
      }
    }
    catch (Throwable e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error releasing savepoint with ID=" + spId + " for connection: [" + id + "], context: " + context, e);
    }
    finally
    {
      removeSavepoint(spId, context);
    }
  }

  /**
   * Execute a rollback on the connection.
   */
  public void rollback()
    throws SQLException
  {
    rollback((CallerInfo)null);
  }

  public synchronized void rollback(CallerInfo context)
    throws SQLException
  {
    if (isClosed()) return;
    if (getAutoCommit()) return;
    if (!getDbSettings().supportsTransactions()) return;

    if (logSavepoints)
    {
      String ctx = context == null ? "" : ", context: " + context;
      LogMgr.logDebug(new CallerInfo(){}, "Rollback all savepoints for connection: " + id + ctx);
    }

    if (!usedSavepoints.isEmpty())
    {
      LogMgr.logWarning(new CallerInfo(){}, "Rollback called with pending savepoints: " + usedSavepoints);
      usedSavepoints.clear();
    }
    this.sqlConnection.rollback();
  }

  public void rollbackSilently()
  {
    rollbackSilently(null);
  }

  public void rollbackSilently(CallerInfo context)
  {
    try
    {
      rollback(context);
    }
    catch (Exception e)
    {
      LogMgr.logWarning(context != null ? context : new CallerInfo(){}, "Could not rollback!", e);
    }
  }

  public void commitSilently(CallerInfo context)
  {
    try
    {
      commit();
    }
    catch (Exception e)
    {
      LogMgr.logWarning(context != null ? context : new CallerInfo(){}, "Could not commit!", e);
    }
  }

  public boolean getIgnoreDropErrors()
  {
    if (this.profile != null)
    {
      return this.profile.getIgnoreDropErrors();
    }
    else
    {
      return false;
    }
  }

  public void toggleAutoCommit()
  {
    boolean flag = this.getAutoCommit();
    try
    {
      setAutoCommit(!flag);
    }
    catch (Exception e)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Error when switching autocommit to " + !flag, e);
    }
  }

  public void changeAutoCommit(boolean flag)
  {
    try
    {
      setAutoCommit(flag);
    }
    catch (Exception ex)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Error when setting autocommit to " + flag, ex);
    }
  }

  public void setAutoCommit(boolean flag)
    throws SQLException
  {
    if (!getDbSettings().supportsTransactions()) return;

    boolean old = this.getAutoCommit();
    if (old != flag)
    {
      usedSavepoints.clear();
      sqlConnection.setAutoCommit(flag);
      fireConnectionStateChanged(PROP_AUTOCOMMIT, Boolean.toString(old), Boolean.toString(flag));
      lastAutocommitState = flag;
    }
  }

  /**
   * Some DBMS (e.g. MySQL) seem to start a new transaction in default
   * isolation mode. Which means that if the SELECT is not committed,
   * no changes will be visible until a commit is issued.
   * In the DbExplorer this is a problem, as the user has no way
   * of sending a commit to end the transation if the DbExplorer
   * uses a separate connection.
   * The {@link workbench.gui.dbobjects.TableDataPanel} will issue
   * a commit after retrieving the data if this method returns true.
   *
   * @see workbench.gui.dbobjects.TableDataPanel#doRetrieve(boolean)
   * @see workbench.gui.dbobjects.TableDataPanel#showRowCount()
   * @see DbSettings#selectStartsTransaction()
   */
  public boolean selectStartsTransaction()
  {
    DbSettings settings = getDbSettings();
    if (settings == null) return false;
    return settings.selectStartsTransaction();
  }

  public boolean getAutoCommit()
  {
    if (this.sqlConnection == null) return false;

    if (!getDbSettings().supportsTransactions())
    {
      return true;
    }

    if (this.isBusy())
    {
      // not perfect, but better then hanging the AWT thread when checking for auto commit
      return lastAutocommitState;
    }

    try
    {
      return this.sqlConnection.getAutoCommit();
    }
    catch (SQLException e)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Error when retrieving autoCommit attribute", e);
      return false;
    }
  }

  /**
   * Disconnect this connection.
   * <p>This is delegated to the Connection Manager because for certain DBMS some cleanup works needs to be done.</p>
   *
   * The ConnectionMgr is the only one who knows if there are more connections
   * around, which might influence what needs to be cleaned up and it also knows if any scripts
   * should be run before closing the connection.
   * <br/>
   * The ConnectionMgr will in turn call {@link shutdown()} once the connection should really be closed.
   * <br/>
   * This will also fire a connectionStateChanged event.
   */
  public void disconnect()
  {
    sessionProps.clear();
    ConnectionMgr.getInstance().disconnect(this);
    fireConnectionStateChanged(PROP_CONNECTION_STATE, CONNECTION_OPEN, CONNECTION_CLOSED);
    pendingEvents.clear();
  }

  public void disconnectSilently()
  {
    try
    {
      disconnect();
    }
    catch (Exception ex)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not disconnect connection: " + getId(), ex);
    }
  }
  /**
   * This will physically close the connection to the DBMS.
   *
   * <p>Calling disconnect() is the preferred method to close a connection.</p>
   * <p>
   * It will also free any resources from the DbMetadata object and
   * shutdown the keep alive thread.
   * </p>
   * <p>Normally {@link #disconnect()} should be used.</p>
   *
   * <p>This is only public to allow cross-package calls in the workbench.db
   * package (basically for the shutdown hooks)</p>
   *
   * <p>This will <b>not</b> notify the ConnectionMgr that this connection has been closed.
   * a connectionStateChanged event will <b>not</b> be fired.</p>
   *
   * @see #disconnect()
   */
  public void shutdown()
  {
    sessionProps.clear();
    boolean doRollback = this.profile != null && this.profile.getRollbackBeforeDisconnect();
    doShutdown(doRollback);
  }

  public void shutdownInBackround()
  {
    WbThread disconnect = new WbThread("DisconnectThread for " + getId())
    {
      @Override
      public void run()
      {
        long start = System.currentTimeMillis();
        doShutdown(true);
        long duration = System.currentTimeMillis() - start;
        LogMgr.logInfo(new CallerInfo(){}, "Connection closed after " + duration + "ms");
      }
    };
    disconnect.start();
  }

  private synchronized void doShutdown(boolean doRollback)
  {
    if (this.keepAlive != null)
    {
      this.keepAlive.shutdown();
      this.keepAlive = null;
    }

    final CallerInfo ci = new CallerInfo(){};
    if (this.preparedStatementPool != null)
    {
      this.preparedStatementPool.done();
    }

    if (doRollback)
    {
      try
      {
        this.rollback();
      }
      catch (Throwable e)
      {
        LogMgr.logWarning(ci, "Error when calling rollback before disconnect", e);
      }
    }

    try
    {
      if (this.metaData != null) this.metaData.close();
    }
    catch (Throwable th)
    {
      LogMgr.logWarning(ci, "Error when releasing metadata", th);
    }

    try
    {
      if (this.sqlConnection != null) this.sqlConnection.close();
    }
    catch (Throwable th)
    {
      LogMgr.logWarning(ci, "Error when closing connection", th);
    }
    finally
    {
      this.sqlConnection = null;
      this.metaData = null;
    }
    pendingEvents.clear();

    LogMgr.logDebug(ci, "Connection " + this.getId() + " closed.");
  }

  public boolean isClosed()
  {
    return (this.sqlConnection == null);
  }

  /**
   * Overwrite the fetch size defined in the connection profile
   * @param size
   */
  public void setFetchSize(int size)
  {
    if (size <= 0)
    {
      fetchSize = null;
    }
    else
    {
      fetchSize = size;
    }
  }

  /**
   * Return the fetch size to be used.
   *
   * <p>If a fetch size has been defined using {@link #setFetchSize(int)} that size
   * is used, otherwise the fetch size defined on the connection profile is used.</p>
   *
   * @return the defined fetch size, or -1 if no fetch size was defined
   */
  public int getFetchSize()
  {
    if (fetchSize != null)
    {
      return fetchSize;
    }
    if (getProfile() != null)
    {
      return getProfile().getFetchSize();
    }
    return -1;
  }


  /**
   * Create a statement that produces ResultSets that
   * are read only and forward only (for performance reasons).
   * <p>
   * If the profile defined a default fetch size, this
   * will be set as well.
   * </p>
   * @throws java.sql.SQLException
   * @see #getFetchSize()
   */
  public Statement createStatementForQuery()
    throws SQLException
  {
    Statement stmt = null;
    if (getDbSettings().allowsExtendedCreateStatement())
    {
      stmt = this.sqlConnection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
      if (stmt == null)
      {
        LogMgr.logError(new CallerInfo(){}, "Connection.createStatement(int, int) did not create a statement!", new Exception("Backtrace"));
        getDbSettings().setUseExtendedCreateStatement(false);
      }
    }

    if (stmt == null)
    {
      stmt = this.sqlConnection.createStatement();
    }

    try
    {
      int size = getFetchSize();
      if (size > -1) stmt.setFetchSize(size);
    }
    catch (Exception e)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Error when setting the fetchSize: " + ExceptionUtil.getDisplay(e));
    }
    return stmt;
  }

  /**
   * Create a new statement object.
   * <br/>
   * This is just a wrapper for java.sql.Connection.createStatement().
   *
   * If a default fetch size was defined in the connection profile, this is applied to
   * the created statement.
   *
   * @return a Statement object
   * @see #getFetchSize()
   */
  public Statement createStatement()
    throws SQLException
  {
    Statement stmt = this.sqlConnection.createStatement();
    if (Settings.getInstance().getBoolProperty("workbench.sql.fetchsize.always", true))
    {
      try
      {
        int size = getFetchSize();
        if (size > -1) stmt.setFetchSize(size);
      }
      catch (Exception e)
      {
        LogMgr.logWarning(new CallerInfo(){}, "Error when setting the fetchSize: " + ExceptionUtil.getDisplay(e));
      }
    }
    return stmt;
  }

  public boolean supportsSavepoints()
  {
    if (this.sqlConnection == null) return false;

    String flag = getDbSettings().getProperty("supports.savepoints", null);
    if (flag != null)
    {
      return StringUtil.stringToBool(flag);
    }

    try
    {
      return sqlConnection.getMetaData().supportsSavepoints();
    }
    catch (Throwable e)
    {
      return false;
    }
  }

  public boolean useJdbcCommit()
  {
    return this.metaData.getDbSettings().useJdbcCommit();
  }

  public DbSettings getDbSettings()
  {
    if (metaData == null) return null;
    return this.metaData.getDbSettings();
  }

  public DbMetadata getMetadata()
  {
    return this.metaData;
  }

  /**
   * Wrapper around DatabaseMetadata.getSearchStringEscape() that does not throw an exception.
   *
   * @return the escape characters to mask wildcards in a string literal
   * @see DbMetadata#getSearchStringEscape()
   */
  public String getSearchStringEscape()
  {
    if (metaData == null) return "\\";
    return metaData.getSearchStringEscape();
  }

  public String getUrl()
  {
    if (switchedUrl != null)
    {
      return switchedUrl;
    }

    if (profile != null && profile.getActiveUrl() != null)
    {
      return profile.getActiveUrl();
    }

    try
    {
      return this.sqlConnection.getMetaData().getURL();
    }
    catch (Throwable e)
    {
      return null;
    }
  }

  @Override
  public String toString()
  {
    return getId() + ", " + DbDriver.getUsernameForLogging(getDisplayUser()) + "@" + DbDriver.getURLForLogging(getUrl());
  }

  /**
   * Returns the username stored in the connection profile.
   *
   * @return the profile's username
   */
  public String getDisplayUser()
  {
    if (profile == null)
    {
      return getCurrentUser();
    }
    String username = profile.getLoginUser();
    if (StringUtil.isEmpty(username) && isSQLServer())
    {
      // This is for SQL Server connections with "Windows authentication"
      username = getWindowsUser();
    }
    return username;
  }

  private boolean isSQLServer()
  {
    if (metaData != null)
    {
      return DBID.SQL_Server.isDB(this);
    }
    String url = this.getUrl();
    if (StringUtil.isEmpty(url)) return false;

    return url.startsWith("jdbc:sqlserver:");
  }

  /**
   * Return a readable display of a connection.
   *
   * This might actually send a SELECT to the database to
   * retrieve the current user or schema.
   *
   * @see #getDisplayString(boolean)
   * @see #getCurrentUser()
   * @see DbMetadata#getSchemaToUse()
   * @see DbMetadata#getCurrentCatalog()
   */
  public String getDisplayString()
  {
    return getDisplayString(false);
  }

  /**
   * Return a readable display of a connection.
   *
   * @param useDisplaySchema if true a cached version of the current schema is used
   *
   * @see #getDisplayString(boolean)
   * @see #getCurrentUser()
   * @see DbMetadata#getSchemaToUse()
   * @see DbMetadata#getCurrentCatalog()
   */
  public String getDisplayString(boolean useDisplaySchema)
  {
    String displayString;
    boolean isBusy = this.isBusy();
    if (this.metaData == null) return "";

    try
    {
      StringBuilder buff = new StringBuilder(100);
      String user = getDisplayUser();
      boolean hasUser = false;
      boolean hasCatalog = false;
      if (StringUtil.isNotBlank(user))
      {
        buff.append(ResourceMgr.getString("TxtUser"));
        buff.append('=');
        buff.append(user);
        hasUser = true;
      }

      hasCatalog = appendCatalog(buff, hasUser, isBusy);

      String schema = useDisplaySchema ? getDisplaySchema() : null;
      if (schema == null && !isBusy)
      {
        schema = metaData.getCurrentSchema();
      }

      if (schema != null)
      {
        currentSchema = schema;
      }

      // the dummy schema in the ignoreSchema() call is there to prevent another lookup for the current schema
      if (schema != null && !schema.equalsIgnoreCase(user) && !metaData.ignoreSchema(schema, "<% INVALID %>"))
      {
        String schemaName = metaData.getSchemaTerm();
        if (hasUser || hasCatalog) buff.append(", ");
        buff.append(schemaName == null ? "Schema" : StringUtil.capitalize(schemaName));
        buff.append('=');
        buff.append(schema);
      }

      if (buff.length() > 0)
      {
        buff.append(", ");
      }
      buff.append("URL=");
      buff.append(getUrl());
      displayString = buff.toString();
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not retrieve connection information", e);
      displayString = toString();
    }
    return displayString;
  }

  private boolean appendCatalog(StringBuilder buff, boolean hasUser, boolean isBusy)
  {
    if (DBID.Oracle.isDB(this))
    {
      return appendOracleContainer(buff, hasUser, isBusy);
    }
    String catalog = isBusy ? currentCatalog : metaData.getCurrentCatalog();
    if (StringUtil.isBlank(catalog)) return false;

    String catName = metaData.getCatalogTerm();
    if (hasUser) buff.append(", ");
    buff.append(catName == null ? "Catalog" : StringUtil.capitalize(catName));
    buff.append('=');
    buff.append(catalog);
    return true;
  }

  private boolean appendOracleContainer(StringBuilder buff, boolean hasUser, boolean isBusy)
  {
    if (!OracleUtils.showContainerInfo()) return false;
    if (!hasOracleContainers) return false;
    if (isBusy) return false;
    if (!JdbcUtils.hasMinimumServerVersion(this, "12.1")) return false;

    String container = OracleUtils.getCurrentContainer(this);
    if (StringUtil.isBlank(container)) return false;

    if (hasUser) buff.append(", ");
    buff.append("Container=");
    buff.append(container);
    return true;
  }

  public String getJDBCVersion()
  {
    try
    {
      DatabaseMetaData jdbcmeta = getSqlConnection().getMetaData();
      int major = jdbcmeta.getJDBCMajorVersion();
      int minor = jdbcmeta.getJDBCMinorVersion();

      return major + "." + minor;
    }
    catch (Throwable e)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Error retrieving DB version (" + ExceptionUtil.getDisplay(e) + ")");
      return "n/a";
    }
  }

  /**
   * An exception safe version of getDatabaseProductVersion().
   *
   * @return the result of getDatabaseProductVersion() or an empty string if an exception occurred
   */
  public String getDatabaseProductVersion()
  {
    if (dbProductVersion == null && !isBusy())
    {
      try
      {
        if (DBID.SQL_Server.isDB(this) && getDbSettings().getBoolProperty("useversionfunction", true))
        {
          dbProductVersion = SqlServerUtil.getVersion(this);
        }
        else if (DBID.Greenplum.isDB(this))
        {
          dbProductVersion = GreenplumUtil.getDatabaseVersionString(this);
        }
        else
        {
          DatabaseMetaData jdbcmeta = getSqlConnection().getMetaData();
          dbProductVersion = jdbcmeta.getDatabaseProductVersion();
          if (dbProductVersion != null)
          {
            Matcher matcher = StringUtil.PATTERN_CRLF.matcher(dbProductVersion);
            dbProductVersion = matcher.replaceAll(" ");
          }
        }
      }
      catch (Throwable e)
      {
        LogMgr.logWarning(new CallerInfo(){}, "Error retrieving DB product ersion (" + ExceptionUtil.getDisplay(e) + ")");
        dbProductVersion = "N/A";
      }
    }
    return dbProductVersion;
  }

  private boolean useDatabaseProductVersion(String dbid)
  {
    String url = getUrl();
    if (StringUtil.isEmpty(url)) return false;

    if (dbid == null)
    {
      dbid = JdbcUtils.getDbIdFromUrl(url);
    }
    if (DBID.Greenplum.isDB(dbid)) return true;
    if (DBID.MySQL.isDB(dbid)) return true;

    // HSQLDB and Postgres return a full version (including patch level) from DatabaseMetaData.getDatabaseProductVersion()
    // so for those DBMS use that version because it's more accurate.
    return url.startsWith("jdbc:postgresql") || url.startsWith("jdbc:hsqldb");
  }

  /**
   * Return the database version as reported by DatabaseMetaData.getDatabaseMajorVersion() and getDatabaseMinorVersion()
   *
   * @return a string with the major and minor version concatenated with a dot.
   */
  public VersionNumber getDatabaseVersion()
  {
    return getDatabaseVersion(this.getDbId());
  }

  protected VersionNumber getDatabaseVersion(String dbid)
  {
    if (dbVersion == null && !isBusy())
    {
      try
      {
        if (useDatabaseProductVersion(dbid))
        {
          String version = getDatabaseProductVersion();
          dbVersion = new VersionNumber(version);
        }
        else
        {
          DatabaseMetaData jdbcmeta = getSqlConnection().getMetaData();
          int major = jdbcmeta.getDatabaseMajorVersion();
          int minor = jdbcmeta.getDatabaseMinorVersion();
          dbVersion = new VersionNumber(major, minor);
        }
      }
      catch (Throwable e)
      {
        LogMgr.logWarning(new CallerInfo(){}, "Error retrieving DB version (" + ExceptionUtil.getDisplay(e) + ")");
        dbVersion = new VersionNumber(0,0);
      }
    }
    return dbVersion;
  }

  public String getDatabaseProductName()
  {
    if (dbProductName == null)
    {
      if (metaData != null)
      {
        dbProductName = this.metaData.getProductName();
      }
    }
    return dbProductName;
  }

  public String getOutputMessages()
  {
    if (metaData == null) return "";
    return this.metaData.getOutputMessages();
  }

  @Override
  public int hashCode()
  {
    return id.hashCode();
  }

  @Override
  public boolean equals(Object o)
  {
    if (o instanceof WbConnection)
    {
      return this.id.equals(((WbConnection)o).id);
    }
    return false;
  }

  public String getDriverVersion()
  {
    if (driverVersion == null && !isBusy())
    {
      DatabaseMetaData db ;
      try
      {
        db = this.sqlConnection.getMetaData();
        driverVersion = db.getDriverVersion();
      }
      catch (Throwable e)
      {
        LogMgr.logError(new CallerInfo(){}, "Error retrieving driver version", e);
        driverVersion = "n/a";
      }
    }
    return driverVersion;
  }


  /**
   *  Some DBMS need to commit DDL (CREATE, DROP, ...) statements.
   *  If the current connection needs a commit for a DDL, this will return true.
   *  The metadata class reads the names of those DBMS from the Settings object!
   */
  protected boolean getDDLNeedsCommit()
  {
    if (metaData == null) return false;
    return this.metaData.getDbSettings().ddlNeedsCommit();
  }

  /**
   * Checks if DDL statements need a commit for this connection.
   *
   * @return false - if autocommit is on or the DBMS does not support DDL transactions<br>
   *         true - if the DBMS supports transactional DBMS and autocommit is disabled
   *
   * @see DbSettings#ddlNeedsCommit()
   * @see DbSettings#alwaysCommitDDL()
   */
  public boolean shouldCommitDDL()
  {
    if (this.getAutoCommit()) return false;
    return this.getDDLNeedsCommit();
  }

  /**
   * Checks if a commit should be appended to DDL scripts.
   *
   * @see DbSettings#ddlNeedsCommit()
   * @see DbSettings#getDDLScriptCommitType()
   */
  public boolean generateCommitForDDL()
  {
    boolean transactionalDDL = this.getDDLNeedsCommit();
    if (!transactionalDDL) return false;

    GenerateDDLCommit generateType = getDbSettings() == null ? GenerateDDLCommit.whenNeeded : getDbSettings().getDDLScriptCommitType();
    if (generateType == GenerateDDLCommit.always) return true;
    if (generateType == GenerateDDLCommit.never) return false;

    return this.getAutoCommit() == false;
  }


  public void addChangeListener(PropertyChangeListener l)
  {
    if (!this.listeners.contains(l))
    {
      this.listeners.add(l);
    }
  }

  public void removeChangeListener(PropertyChangeListener l)
  {
    this.listeners.remove(l);
  }

  private void firePendingEvents()
  {
    if (pendingEvents.isEmpty()) return;
    List<PropertyChangeListener> list = new ArrayList<>(listeners);

    for (PropertyChangeEvent event : pendingEvents)
    {
      fireEvent(list, event);
    }
    pendingEvents.clear();
  }

  private void fireConnectionStateChanged(String property, String oldValue, String newValue)
  {
    if (listeners.isEmpty()) return;

    List<PropertyChangeListener> list = new ArrayList<>(listeners);
    PropertyChangeEvent evt = new PropertyChangeEvent(this, property, oldValue, newValue);
    if (this.busy && !property.equals(PROP_BUSY))
    {
      // Any event that should have been fired while the connection is busy
      // should be dispatched after the connection is no longer busy
      this.pendingEvents.add(evt);
    }
    else
    {
      fireEvent(list, evt);
    }
  }

  private void fireEvent(List<PropertyChangeListener> changeListeners, PropertyChangeEvent evt)
  {
    for (PropertyChangeListener l : changeListeners)
    {
      if (l != null)
      {
        l.propertyChange(evt);
      }
    }
  }

  /**
   * Return the current catalog as returned by the JDBC driver.
   */
  public String getCurrentCatalog()
  {
    return this.metaData.getCurrentCatalog();
  }

  public String getDisplayCatalog()
  {
    return currentCatalog;
  }

  public void catalogListChanged()
  {
    this.fireConnectionStateChanged(PROP_CATALOG_LIST, null, null);
  }

  /**
   * This is called whenever the current catalog was changed.
   *
   * It will fire a connectionStateChanged event and will clear the object cache
   * as the cache is schema based. Changing the catalog means changing the
   * database in MySQL or SQL Server. If the new database has the same schemas
   * as the old, the object cache would show invalid data.
   *
   * @param oldCatalog
   * @param newCatalog
   */
  public void catalogChanged(String oldCatalog, String newCatalog)
  {
    boolean changed = currentCatalog != null && !currentCatalog.equals(newCatalog);
    this.currentCatalog = newCatalog;
    this.getObjectCache().clear();
    if (changed)
    {
      this.fireConnectionStateChanged(PROP_CATALOG, oldCatalog, newCatalog);
    }
  }

  public void containerChanged(String oldContainer, String newContainer)
  {
    if (DBID.Oracle.isDB(this))
    {
      fireConnectionStateChanged(PROP_CATALOG, oldContainer, newContainer);
    }
  }

  public void schemaChanged(String oldSchema, String newSchema)
  {
    boolean changed = (currentSchema != null && !currentSchema.equals(newSchema)) || StringUtil.stringsAreNotEqual(oldSchema, newSchema);
    this.currentSchema = newSchema;
    if (changed)
    {
      this.fireConnectionStateChanged(PROP_SCHEMA, oldSchema, newSchema);
    }
  }

  private void initKeepAlive()
  {
    if (this.keepAlive != null)
    {
      this.keepAlive.shutdown();
      this.keepAlive = null;
    }

    if (this.profile == null || !this.profile.hasValidIdleSetup()) return;

    String sql = this.profile.getIdleScript();
    long idleTime = this.profile.getIdleTime();

    this.keepAlive = new KeepAliveDaemon(idleTime, this, sql);
    this.keepAlive.startDaemon();
  }

  public boolean isBusy()
  {
    return this.busy;
  }

  /**
   * Marks or unmarks this connection as busy
   * @param flag   the new state
   * @return the old state of the busy flag
   */
  public boolean setBusy(boolean flag)
  {
    if (flag == this.busy) return this.busy;

    boolean wasBusy = this.busy;
    String oldValue = Boolean.toString(wasBusy);
    this.busy = flag;
    if (flag && this.keepAlive != null)
    {
      this.keepAlive.setLastDbAction(System.currentTimeMillis());
    }
    fireConnectionStateChanged(PROP_BUSY, oldValue, Boolean.toString(this.busy));
    if (!busy)
    {
      firePendingEvents();
    }
    return wasBusy;
  }

  @Override
  public void executionStart(WbConnection conn, Object source)
  {
    if (conn == this)
    {
      setBusy(true);
    }
  }

  /*
   *  Fired by the SqlPanel if DB access finished
   */
  @Override
  public void executionEnd(WbConnection conn, Object source)
  {
    if (conn == this)
    {
      setBusy(false);
    }
  }

  /**
   * Calls Oracle's own cancel() method on the current connection.
   *
   * This seems to make cancelling statements much more reliable.
   * If this is not an Oracle connection, nothing happens.
   */
  public void oracleCancel()
  {
    if (!DBID.Oracle.isDB(this)) return;

    try
    {
      Method cancel = sqlConnection.getClass().getMethod("cancel");
      cancel.setAccessible(true);

      LogMgr.logDebug(new CallerInfo(){}, "Using OracleConnection.cancel() to cancel the current statement");
      cancel.invoke(sqlConnection);
    }
    catch (Throwable th)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not call OracleConnection.cancel()", th);
    }

    if (!pingAvailable) return;

    try
    {
      // calling pingDatabase() after a cancel() fixes the problem that the next statement
      // right after calling cancel() is cancelled immediately again with "ORA-01013: user requested cancel of current operation"
      Method ping = sqlConnection.getClass().getMethod("pingDatabase");
      ping.setAccessible(true);

      LogMgr.logDebug(new CallerInfo(){}, "Calling pingDatabase() to clear the communication");
      ping.invoke(sqlConnection);
    }
    catch (NoSuchMethodException | SecurityException | IllegalAccessException ex)
    {
      LogMgr.logDebug(new CallerInfo(){}, "pingDatabase() not available", ex);
      // only try once for the livetime of this connection
      pingAvailable = false;
    }
    catch (Throwable th)
    {
      LogMgr.logDebug(new CallerInfo(){}, "Could not call OracleConnection.pingDatabase()", th);
    }
  }

  public String createFilename()
  {
    return ConnectionProfile.makeFilename(getUrl(), getDisplayUser());
  }

  public boolean endReadOnlyTransaction()
  {
    return endReadOnlyTransaction(null);
  }

  public boolean endReadOnlyTransaction(CallerInfo context)
  {
    if (this.getAutoCommit()) return true;
    if (this.getDbSettings() == null) return false;

    EndReadOnlyTrans endTransType = EndReadOnlyTrans.never;

    try
    {
      endTransType = this.getDbSettings().getAutoCloseReadOnlyTransactions();
      if (endTransType == EndReadOnlyTrans.never) return false;

      TransactionChecker transactionChecker = TransactionChecker.Factory.createChecker(this);

      if (transactionChecker == TransactionChecker.NO_CHECK)
      {
        LogMgr.logWarning(new CallerInfo(){}, "Ending read-only transactions has been configured, but there is no support for checking pending transactions for the current DBMS: " + getDatabaseProductName() + " (" + getDbId() + ")");
        return false;
      }

      if (!transactionChecker.hasUncommittedChanges(this))
      {
        String msg = "Sending a " + endTransType.name() + " to end the current transaction";
        if (context != null)
        {
          msg += " <" + context + ">";
        }
        LogMgr.logInfo(new CallerInfo(){}, msg);
        if (endTransType == EndReadOnlyTrans.commit)
        {
          commit();
        }
        else
        {
          rollbackSilently();
        }
        return true;
      }
    }
    catch (Exception ex)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not end transaction using: " + endTransType, ex);
    }
    return false;
  }

}
