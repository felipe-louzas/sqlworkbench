/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
 * that restricts the use for certain governments.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.sql-workbench.eu/manual/license.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.eu
 */
package workbench.db;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import workbench.interfaces.PropertyStorage;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;
import workbench.ssh.SshConfig;
import workbench.ssh.SshHostConfig;

import workbench.sql.wbcommands.ConnectionDescriptor;

import workbench.util.FileUtil;
import workbench.util.StringUtil;
import workbench.util.WbFile;
import workbench.util.WbProperties;

/**
 *
 * @author Thomas Kellerer
 */
public class IniProfileStorage
  implements ProfileStorage
{
  public static final String EXTENSION = "properties";
  public static final String DEFAULT_FILE_NAME = "wb-profiles." + EXTENSION;

  private static final String PROP_PREFIX = "profile";
  private static final String PROP_NAME = ".name";
  private static final String PROP_URL = ".url";
  private static final String PROP_PWD = ".password";
  private static final String PROP_USERNAME = ".username";
  private static final String PROP_DRIVERNAME = ".drivername";
  private static final String PROP_DRIVERCLASS = ".driverclass";
  private static final String PROP_DRIVERJAR = ".driverjar";
  private static final String PROP_AUTOCOMMMIT = ".autocommmit";
  private static final String PROP_FETCHSIZE = ".fetchsize";
  private static final String PROP_ALT_DELIMITER = ".alternate.delimiter";
  private static final String PROP_STORE_PWD = ".store.pwd";
  private static final String PROP_ROLLBACK_DISCONNECT = ".rollback.disconnect";

  private static final String PROP_GROUP = ".group";
  private static final String PROP_TAGS = ".tags";
  private static final String PROP_WORKSPACE = ".workspace";
  private static final String PROP_DEFAULT_DIR = ".default.dir";
  private static final String PROP_ICON = ".icon";
  private static final String PROP_CONNECTION_TIMEOUT = ".connection.timeout";
  private static final String PROP_HIDE_WARNINGS = ".hide.warnings";
  private static final String PROP_REMEMEMBER_SCHEMA = ".rememember.schema";
  private static final String PROP_REMOVE_COMMENTS = ".remove.comments";
  private static final String PROP_INCLUDE_NULL_ON_INSERT = ".include.null.insert";
  private static final String PROP_EMPTY_STRING_IS_NULL = ".empty.string.is.null";
  private static final String PROP_PROMPTUSERNAME = ".prompt.username";
  private static final String PROP_CONFIRM_UPDATES = ".confirm.updates";
  private static final String PROP_PREVENT_NO_WHERE = ".prevent.no.where.clause";
  private static final String PROP_READONLY = ".readonly";
  private static final String PROP_DETECTOPENTRANSACTION = ".detect.open.transaction";
  private static final String PROP_ORACLESYSDBA = ".oracle.sysdba";
  private static final String PROP_TRIMCHARDATA = ".trim.char.data";
  private static final String PROP_IGNOREDROPERRORS = ".ignore.drop.errors";
  private static final String PROP_SEPARATECONNECTION = ".separate.connection";
  private static final String PROP_STORECACHE = ".store.cache";
  private static final String PROP_IDLE_TIME = ".idle.time";
  private static final String PROP_SCRIPT_IDLE = ".script.idle";
  private static final String PROP_SCRIPT_DISCONNECT = ".script.disconnect";
  private static final String PROP_SCRIPT_CONNECT = ".script.connect";
  private static final String PROP_SCRIPT_ECHO = ".script.echo.statements";
  private static final String PROP_MACROFILE = ".macro.file";
  private static final String PROP_COPY_PROPS = ".copy.props";
  private static final String PROP_CONN_PROPS = ".connection.properties";
  private static final String PROP_CONN_VARS = ".connection.variables";
  private static final String PROP_INFO_COLOR = ".info.color";
  private static final String PROP_SCHEMA_FILTER = ".schema.filter";
  private static final String PROP_CATALOG_FILTER = ".catalog.filter";
  public static final String PROP_SSH_HOST = ".ssh.host";
  public static final String PROP_SSH_USER = ".ssh.user";
  public static final String PROP_SSH_PWD = ".ssh.pwd";
  public static final String PROP_SSH_KEYFILE = ".ssh.keyfile";
  public static final String PROP_SSH_TRY_AGENT = ".ssh.try.agent";
  public static final String PROP_SSH_GLOBAL_CONFIG = ".ssh.global.hostconfig";
  public static final String PROP_SSH_PORT = ".ssh.port";
  public static final String PROP_IGNORE_BANNER = ".ssh.ignore.banner";
  private static final String PROP_SSH_LOCAL_PORT = ".ssh.localport";
  private static final String PROP_SSH_DB_PORT = ".ssh.db.port";
  private static final String PROP_SSH_DB_HOST = ".ssh.db.host";

  private static final String XML_PREFIX = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\" standalone=\"no\"?><!DOCTYPE properties SYSTEM \"http://java.sun.com/dtd/properties.dtd\">";

  @Override
  public List<ConnectionProfile> readProfiles(WbFile inifile)
  {
    LogMgr.logDebug(new CallerInfo(){}, "Loading connection profiles from " + inifile.getFullPath());
    WbProperties props = new WbProperties(1);
    BufferedReader reader = null;
    List<ConnectionProfile> profiles = new ArrayList<>(25);

    try
    {
      File fileDir = inifile.getCanonicalFile().getParentFile();
      reader = new BufferedReader(new FileReader(inifile));
      props.loadFromReader(reader);
      Set<String> keys = getProfileKeys(props);
      for (String key : keys)
      {
        ConnectionProfile profile = readProfile(fileDir, key, props);
        if (profile != null)
        {
          profiles.add(profile);
        }
      }
    }
    catch (Throwable ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not read profiles from: " + inifile, ex);
    }
    finally
    {
      FileUtil.closeQuietely(reader);
    }
    LogMgr.logInfo(new CallerInfo(){}, "Loaded " + profiles.size() + " connection profiles from " + inifile.getFullpathForLogging());
    return profiles;
  }

  private ConnectionProfile readProfile(File baseDir, String key, WbProperties props)
  {
    ConnectionProfile def = new ConnectionProfile();
    key = "." + key;
    String url = props.getProperty(PROP_PREFIX + key + PROP_URL, null);
    String tags = props.getProperty(PROP_PREFIX + key + PROP_TAGS, null);
    String name = props.getProperty(PROP_PREFIX + key + PROP_NAME, null);
    String driverClass = props.getProperty(PROP_PREFIX + key + PROP_DRIVERCLASS, null);
    String driverJar = props.getProperty(PROP_PREFIX + key + PROP_DRIVERJAR, null);
    String driverName = props.getProperty(PROP_PREFIX + key + PROP_DRIVERNAME, null);
    String groupPath = props.getProperty(PROP_PREFIX + key + PROP_GROUP, null);
    String user = props.getProperty(PROP_PREFIX + key + PROP_USERNAME, null);
    String pwd = props.getProperty(PROP_PREFIX + key + PROP_PWD, null);
    String icon = props.getProperty(PROP_PREFIX + key + PROP_ICON, null);
    String wksp = props.getProperty(PROP_PREFIX + key + PROP_WORKSPACE, null);
    String delimiter = props.getProperty(PROP_PREFIX + key + PROP_ALT_DELIMITER, null);
    String macroFile = props.getProperty(PROP_PREFIX + key + PROP_MACROFILE, null);
    String postConnect = props.getProperty(PROP_PREFIX + key + PROP_SCRIPT_CONNECT, null);
    String preDisconnect = props.getProperty(PROP_PREFIX + key + PROP_SCRIPT_DISCONNECT, null);
    String idleScript = props.getProperty(PROP_PREFIX + key + PROP_SCRIPT_IDLE, null);
    String scriptDir = props.getProperty(PROP_PREFIX + key + PROP_DEFAULT_DIR, null);
    String xmlProps = props.getProperty(PROP_PREFIX + key + PROP_CONN_PROPS, null);
    String xmlVars = props.getProperty(PROP_PREFIX + key + PROP_CONN_VARS, null);
    String colorValue = props.getProperty(PROP_PREFIX + key + PROP_INFO_COLOR, null);

    Properties connProps = toProperties(xmlProps);
    Properties vars = toProperties(xmlVars);
    Color color = Settings.stringToColor(colorValue);

    boolean echoConnectStatements = props.getBoolProperty(PROP_PREFIX + key + PROP_SCRIPT_ECHO, def.getEchoConnectScriptStatements());
    boolean autoCommit = props.getBoolProperty(PROP_PREFIX + key + PROP_AUTOCOMMMIT, def.getAutocommit());
    boolean storeCache = props.getBoolProperty(PROP_PREFIX + key + PROP_STORECACHE, def.getStoreCacheLocally());
    boolean storePwd = props.getBoolProperty(PROP_PREFIX + key + PROP_STORE_PWD, def.getStorePassword());
    boolean rollback = props.getBoolProperty(PROP_PREFIX + key + PROP_ROLLBACK_DISCONNECT, def.getRollbackBeforeDisconnect());
    boolean seperateConnection = props.getBoolProperty(PROP_PREFIX + key + PROP_SEPARATECONNECTION, def.getUseSeparateConnectionPerTab());
    boolean ignoreDropError = props.getBoolProperty(PROP_PREFIX + key + PROP_IGNOREDROPERRORS, def.getIgnoreDropErrors());
    boolean trimCharData  = props.getBoolProperty(PROP_PREFIX + key + PROP_TRIMCHARDATA, def.getTrimCharData());
    boolean sysDBA  = props.getBoolProperty(PROP_PREFIX + key + PROP_ORACLESYSDBA, def.getOracleSysDBA());
    boolean detectOpen = props.getBoolProperty(PROP_PREFIX + key + PROP_DETECTOPENTRANSACTION, def.getDetectOpenTransaction());
    boolean readonly = props.getBoolProperty(PROP_PREFIX + key + PROP_READONLY, def.isReadOnly());
    boolean preventNoWhere = props.getBoolProperty(PROP_PREFIX + key + PROP_PREVENT_NO_WHERE, def.getPreventDMLWithoutWhere());
    boolean confirmUpdates = props.getBoolProperty(PROP_PREFIX + key + PROP_CONFIRM_UPDATES, def.getConfirmUpdates());
    boolean promptUsername = props.getBoolProperty(PROP_PREFIX + key + PROP_PROMPTUSERNAME, def.getPromptForUsername());
    boolean emptyStringIsNull = props.getBoolProperty(PROP_PREFIX + key + PROP_EMPTY_STRING_IS_NULL, def.getEmptyStringIsNull());
    boolean includeNullInInsert = props.getBoolProperty(PROP_PREFIX + key + PROP_INCLUDE_NULL_ON_INSERT, def.getIncludeNullInInsert());
    boolean removeComments = props.getBoolProperty(PROP_PREFIX + key + PROP_REMOVE_COMMENTS, def.getRemoveComments());
    boolean rememberExplorerSchema = props.getBoolProperty(PROP_PREFIX + key + PROP_REMEMEMBER_SCHEMA, def.getStoreExplorerSchema());
    boolean hideWarnings = props.getBoolProperty(PROP_PREFIX + key + PROP_HIDE_WARNINGS, def.isHideWarnings());
    boolean copyProps = props.getBoolProperty(PROP_PREFIX + key + PROP_COPY_PROPS, def.getCopyExtendedPropsToSystem());

    int idleTime = props.getIntProperty(PROP_PREFIX + key + PROP_IDLE_TIME, -1);
    int size = props.getIntProperty(PROP_PREFIX + key + PROP_FETCHSIZE, -1);
    int timeOut = props.getIntProperty(PROP_PREFIX + key + PROP_CONNECTION_TIMEOUT, -1);

    SshConfig config = readSshConfig(props, PROP_PREFIX, key);

    Integer fetchSize = null;
    if (size >= 0)
    {
      fetchSize = Integer.valueOf(size);
    }

    Integer connectionTimeOut = null;
    if (timeOut > 0)
    {
      connectionTimeOut = Integer.valueOf(timeOut);
    }

    if (StringUtil.isBlank(driverClass))
    {
      driverClass = ConnectionDescriptor.findDriverClassFromUrl(url);
      if (driverClass != null)
      {
        LogMgr.logInfo(new CallerInfo(){}, "Profile " + name + " does not contain a driver class definition. Using " + driverClass + " based on the URL: " + url);
      }
    }

    // if a driver jar was explicitely specified, that jar should be used
    // regardless of any registered driver that might be referenced through driverName
    if (StringUtil.isNotEmpty(driverJar))
    {
      if (StringUtil.isBlank(driverClass))
      {
        LogMgr.logError(new CallerInfo(){},
          "Profile " + name + " defines a JDBC driver but no driver class was specified. Ignoring the profile", null);
        return null;
      }
      WbFile drvFile = new WbFile(driverJar);
      if (!drvFile.isAbsolute())
      {
        drvFile = new WbFile(baseDir, driverJar);
        LogMgr.logDebug(new CallerInfo(){},
          "Using full path: " + drvFile.getFullpathForLogging() +
          " for driver jar " + driverJar + " from profile " + name);
        driverJar = drvFile.getFullPath();
      }
      else
      {
        driverJar = drvFile.getFullPath();
      }
      if (!drvFile.exists())
      {
        LogMgr.logError(new CallerInfo(){},
          "Driver jar: \"" + drvFile.getFullpathForLogging() + "\" for profile: " + name + " does not exist.", null);
      }
      DbDriver drv = ConnectionMgr.getInstance().registerDriver(driverClass, driverJar);
      driverName = drv.getName();
    }

    ObjectNameFilter schemaFilter = getSchemaFilter(props, key);
    ObjectNameFilter catalogFilter = getCatalogFilter(props, key);

    ConnectionProfile profile = new ConnectionProfile();
    profile.setName(name);
    profile.setUsername(user);
    profile.setUrl(url);
    profile.setInputPassword(pwd);
    profile.setDriverclass(driverClass);
    profile.setDriverName(driverName);
    profile.setGroupByPathString(groupPath);
    profile.setTagList(tags);
    profile.setDefaultFetchSize(fetchSize);
    profile.setOracleSysDBA(sysDBA);
    profile.setReadOnly(readonly);
    profile.setWorkspaceFile(wksp);
    profile.setIcon(icon);
    profile.setConnectionTimeout(connectionTimeOut);
    profile.setRollbackBeforeDisconnect(rollback);
    profile.setUseSeparateConnectionPerTab(seperateConnection);
    profile.setAlternateDelimiterString(delimiter);
    profile.setMacroFilename(macroFile);
    profile.setIgnoreDropErrors(ignoreDropError);
    profile.setTrimCharData(trimCharData);
    profile.setDetectOpenTransaction(detectOpen);
    profile.setPreventDMLWithoutWhere(preventNoWhere);
    profile.setConfirmUpdates(confirmUpdates);
    profile.setPromptForUsername(promptUsername);
    profile.setEmptyStringIsNull(emptyStringIsNull);
    profile.setIncludeNullInInsert(includeNullInInsert);
    profile.setRemoveComments(removeComments);
    profile.setStoreExplorerSchema(rememberExplorerSchema);
    profile.setHideWarnings(hideWarnings);
    profile.setStoreCacheLocally(storeCache);
    profile.setAutocommit(autoCommit);
    profile.setPreDisconnectScript(preDisconnect);
    profile.setPostConnectScript(postConnect);
    profile.setEchoConnectScriptStatements(echoConnectStatements);
    profile.setIdleScript(idleScript);
    profile.setIdleTime(idleTime);
    profile.setStorePassword(storePwd);
    profile.setCopyExtendedPropsToSystem(copyProps);
    profile.setConnectionProperties(connProps);
    profile.setConnectionVariables(vars);
    profile.setInfoDisplayColor(color);
    profile.setSchemaFilter(schemaFilter);
    profile.setCatalogFilter(catalogFilter);
    profile.setSshConfig(config);
    profile.setDefaultDirectory(scriptDir);

    return profile;
  }

  @Override
  public void saveProfiles(List<ConnectionProfile> profiles, WbFile filename)
  {
    LogMgr.logDebug(new CallerInfo(){}, "Saving profiles to: " + filename);
    WbProperties props = new WbProperties(2);

    long start = System.currentTimeMillis();
    // This comparator sorts the "name" attribute at the first place inside the keys for one profile
    // This is just for convenience, so that it's easier to read the properties file
    Comparator<String> comp = (String o1, String o2) ->
    {
      int pos1 = o1.indexOf('.', o1.indexOf('.') + 1);
      int pos2 = o2.indexOf('.', o2.indexOf('.') + 1);

      String base1 = o1.substring(0, pos1);
      String base2 = o2.substring(0, pos2);

      if (base1.equals(base2))
      {
        if (o1.endsWith(PROP_NAME) && !o2.endsWith(PROP_NAME)) return -1;
        if (!o1.endsWith(PROP_NAME) && o2.endsWith(PROP_NAME)) return 1;
      }
      return o1.compareTo(o2);
    };

    props.setSortComparator(comp);

    for (int i=0; i < profiles.size(); i++)
    {
      String key = StringUtil.formatInt(i + 1, 4).toString();
      storeProfile(key, profiles.get(i), props);
    }

    try
    {
      props.saveToFile(filename);
    }
    catch (Throwable ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Error saving profiles to: " + filename, ex);
    }
    long duration = System.currentTimeMillis() - start;
    LogMgr.logDebug(new CallerInfo(){}, "Saved " + profiles.size() + " profiles to " + filename + " in " + duration + "ms");
  }

  private void storeProfile(String key, ConnectionProfile profile, WbProperties props)
  {
    ConnectionProfile def = new ConnectionProfile();

    key = "." + key;
    props.setProperty(PROP_PREFIX + key + PROP_URL, profile.getUrl());
    props.setProperty(PROP_PREFIX + key + PROP_NAME, profile.getName());
    props.setProperty(PROP_PREFIX + key + PROP_DRIVERNAME, profile.getDriverName());
    props.setProperty(PROP_PREFIX + key + PROP_DRIVERCLASS, profile.getDriverclass());
    props.setProperty(PROP_PREFIX + key + PROP_USERNAME, profile.getUsername());
    props.setProperty(PROP_PREFIX + key + PROP_AUTOCOMMMIT, profile.getAutocommit());
    props.setProperty(PROP_PREFIX + key + PROP_TAGS, profile.getTagList());

    props.setProperty(PROP_PREFIX + key + PROP_STORE_PWD, profile.getStorePassword());
    if (profile.getStorePassword())
    {
      props.setProperty(PROP_PREFIX + key + PROP_PWD, profile.getPassword());
    }

    props.setProperty(PROP_PREFIX + key + PROP_ICON, profile.getIcon());
    props.setProperty(PROP_PREFIX + key + PROP_WORKSPACE, profile.getWorkspaceFile());
    props.setProperty(PROP_PREFIX + key + PROP_ALT_DELIMITER, profile.getAlternateDelimiterString());
    props.setProperty(PROP_PREFIX + key + PROP_MACROFILE, profile.getMacroFilename());
    props.setProperty(PROP_PREFIX + key + PROP_SCRIPT_CONNECT, profile.getPostConnectScript());
    props.setProperty(PROP_PREFIX + key + PROP_SCRIPT_DISCONNECT, profile.getPreDisconnectScript());
    props.setProperty(PROP_PREFIX + key + PROP_DEFAULT_DIR, profile.getDefaultDirectory());

    writeSshConfig(props, PROP_PREFIX, key, profile.getSshConfig());

    setNonDefaultProperty(props, PROP_PREFIX + key + PROP_STORECACHE, profile.getStoreCacheLocally(), def.getStoreCacheLocally());
    setNonDefaultProperty(props, PROP_PREFIX + key + PROP_ROLLBACK_DISCONNECT, profile.getRollbackBeforeDisconnect(), def.getRollbackBeforeDisconnect());
    setNonDefaultProperty(props, PROP_PREFIX + key + PROP_SEPARATECONNECTION, profile.getUseSeparateConnectionPerTab(), def.getUseSeparateConnectionPerTab());
    setNonDefaultProperty(props, PROP_PREFIX + key + PROP_IGNOREDROPERRORS, profile.getIgnoreDropErrors(), def.getIgnoreDropErrors());
    setNonDefaultProperty(props, PROP_PREFIX + key + PROP_TRIMCHARDATA, profile.getTrimCharData(), def.getTrimCharData());
    setNonDefaultProperty(props, PROP_PREFIX + key + PROP_ORACLESYSDBA, profile.getOracleSysDBA(), def.getOracleSysDBA());
    setNonDefaultProperty(props, PROP_PREFIX + key + PROP_DETECTOPENTRANSACTION, profile.getDetectOpenTransaction(), def.getDetectOpenTransaction());
    setNonDefaultProperty(props, PROP_PREFIX + key + PROP_READONLY, profile.isReadOnly(), def.isReadOnly());
    setNonDefaultProperty(props, PROP_PREFIX + key + PROP_PREVENT_NO_WHERE, profile.getPreventDMLWithoutWhere(), def.getPreventDMLWithoutWhere());
    setNonDefaultProperty(props, PROP_PREFIX + key + PROP_CONFIRM_UPDATES, profile.getConfirmUpdates(), def.getConfirmUpdates());
    setNonDefaultProperty(props, PROP_PREFIX + key + PROP_PROMPTUSERNAME, profile.getPromptForUsername(), def.getPromptForUsername());
    setNonDefaultProperty(props, PROP_PREFIX + key + PROP_EMPTY_STRING_IS_NULL, profile.getEmptyStringIsNull(), def.getEmptyStringIsNull());
    setNonDefaultProperty(props, PROP_PREFIX + key + PROP_INCLUDE_NULL_ON_INSERT, profile.getIncludeNullInInsert(), def.getIncludeNullInInsert());
    setNonDefaultProperty(props, PROP_PREFIX + key + PROP_REMOVE_COMMENTS, profile.getRemoveComments(), def.getRemoveComments());
    setNonDefaultProperty(props, PROP_PREFIX + key + PROP_REMEMEMBER_SCHEMA, profile.getStoreExplorerSchema(), def.getStoreExplorerSchema());
    setNonDefaultProperty(props, PROP_PREFIX + key + PROP_HIDE_WARNINGS, profile.isHideWarnings(), def.isHideWarnings());
    setNonDefaultProperty(props, PROP_PREFIX + key + PROP_SCRIPT_ECHO, profile.getEchoConnectScriptStatements(), def.getEchoConnectScriptStatements());

    if (profile.getGroups().size() > 0)
    {
      props.setProperty(PROP_PREFIX + key + PROP_GROUP, profile.getGroup());
    }

    props.setProperty(PROP_PREFIX + key + PROP_SCRIPT_IDLE, profile.getIdleScript());
    if (profile.getIdleTime() != def.getIdleTime())
    {
      props.setProperty(PROP_PREFIX + key + PROP_IDLE_TIME, Long.toString(profile.getIdleTime()));
    }
    props.setProperty(PROP_PREFIX + key + PROP_INFO_COLOR, Settings.colorToString(profile.getInfoDisplayColor()));

    Integer fetchSize = profile.getDefaultFetchSize();
    if (fetchSize != null)
    {
      props.setProperty(PROP_PREFIX + key + PROP_FETCHSIZE, fetchSize.intValue());
    }
    Integer timeout = profile.getConnectionTimeout();
    if (timeout != null)
    {
      props.setProperty(PROP_PREFIX + key + PROP_CONNECTION_TIMEOUT, timeout.intValue());
    }

    ObjectNameFilter filter = profile.getSchemaFilter();
    String expr = (filter != null ? filter.getFilterString() : null);
    if (expr != null && filter != null)
    {
      props.setProperty(PROP_PREFIX + key + PROP_SCHEMA_FILTER, expr);
      props.setProperty(PROP_PREFIX + key + PROP_SCHEMA_FILTER + ".include", filter.isInclusionFilter());
      props.setProperty(PROP_PREFIX + key + PROP_SCHEMA_FILTER + ".retrieval_filter", filter.isRetrievalFilter());
    }

    filter = profile.getCatalogFilter();
    expr = (filter != null ? filter.getFilterString() : null);
    if (expr != null && filter != null)
    {
      props.setProperty(PROP_PREFIX + key + PROP_CATALOG_FILTER, expr);
      props.setProperty(PROP_PREFIX + key + PROP_CATALOG_FILTER+ ".include", filter.isInclusionFilter());
    }

    String xml = toXML(profile.getConnectionProperties());
    props.setProperty(PROP_PREFIX + key + PROP_CONN_PROPS, xml);
    props.setProperty(PROP_PREFIX + key + PROP_CONN_VARS, toXML(profile.getConnectionVariables()));
    props.setProperty(PROP_PREFIX + key + PROP_COPY_PROPS, profile.getCopyExtendedPropsToSystem());
  }

  private void setNonDefaultProperty(WbProperties props, String key, boolean value, boolean defaultValue)
  {
    if (value != defaultValue)
    {
      props.setProperty(key, value);
    }
  }

  private String toXML(Properties props)
  {
    if (props == null || props.isEmpty()) return null;

    try
    {
      ByteArrayOutputStream out = new ByteArrayOutputStream(props.size() * 20);
      props.storeToXML(out, null, "ISO-8859-1");
      String xml = out.toString("ISO-8859-1");
      xml = xml.replaceAll(StringUtil.REGEX_CRLF, "");

      // Strip off the xml prefix to make the file easier to edit:
      int pos = xml.indexOf("<properties>");
      if (pos > -1)
      {
        xml = xml.substring(pos);
      }
      return xml;
    }
    catch (Throwable th)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not convert properties to XML", th);
      return null;
    }
  }

  private Properties toProperties(String xml)
  {
    if (StringUtil.isBlank(xml)) return null;

    if (!xml.startsWith("<?xml"))
    {
      xml = XML_PREFIX + xml;
    }
    try
    {
      Properties props = new Properties();
      ByteArrayInputStream in = new ByteArrayInputStream(xml.getBytes("ISO-8859-1"));
      props.loadFromXML(in);
      return props;
    }
    catch (Throwable th)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not convert XML properties", th);
      return null;
    }
  }

  private Set<String> getProfileKeys(WbProperties props)
  {
    Set<String> uniqueKeys = new TreeSet<>();
    List<String> keys = props.getKeysWithPrefix(PROP_PREFIX);
    for (String key : keys)
    {
      String[] elements = key.split("\\.");
      if (elements.length > 2)
      {
        uniqueKeys.add(elements[1]);
      }
    }
    return uniqueKeys;
  }

  private ObjectNameFilter getSchemaFilter(WbProperties props, String key)
  {
    ObjectNameFilter filter = getFilter(props, key, PROP_SCHEMA_FILTER);
    if (filter != null)
    {
      boolean retrieval = props.getBoolProperty(PROP_PREFIX + key + PROP_SCHEMA_FILTER + ".retrieval_filter", false);
      filter.setRetrievalFilter(retrieval);
    }
    return filter;
  }

  private ObjectNameFilter getCatalogFilter(WbProperties props, String key)
  {
    return getFilter(props, key, PROP_CATALOG_FILTER);
  }

  private ObjectNameFilter getFilter(WbProperties props, String key, String prop)
  {
    String filterList = props.getProperty(PROP_PREFIX + key + prop);
    if (StringUtil.isBlank(filterList)) return null;
    boolean inclusion = props.getBoolProperty(PROP_PREFIX + key + prop + ".include", false);
    ObjectNameFilter filter = new ObjectNameFilter();
    filter.setExpressionList(filterList);
    filter.setInclusionFilter(inclusion);
    return filter;
  }

  private void writeSshHost(PropertyStorage props, String prefix, String key, SshHostConfig config)
  {
    props.setProperty(prefix + key + PROP_SSH_HOST, config.getHostname());
    props.setProperty(prefix + key + PROP_SSH_USER, config.getUsername());
    props.setProperty(prefix + key + PROP_SSH_PWD, config.getPassword());
    props.setProperty(prefix + key + PROP_SSH_PORT, config.getSshPort());
    props.setProperty(prefix + key + PROP_SSH_KEYFILE, config.getPrivateKeyFile());
    props.setProperty(prefix + key + PROP_SSH_TRY_AGENT, config.getTryAgent());
  }

  private void writeSshConfig(PropertyStorage props, String prefix, String key, SshConfig config)
  {
    if (config == null) return;
    if (config.getSshHostConfigName() == null)
    {
      writeSshHost(props, prefix, key, config.getSshHostConfig());
    }
    else
    {
      props.setProperty(prefix + key + PROP_SSH_GLOBAL_CONFIG, config.getSshHostConfigName());
    }
    props.setProperty(prefix + key + PROP_SSH_LOCAL_PORT, config.getLocalPort());
    props.setProperty(prefix + key + PROP_SSH_DB_HOST, config.getDbHostname());
    props.setProperty(prefix + key + PROP_SSH_DB_PORT, config.getDbPort());
  }

  private SshHostConfig readHostConfig(PropertyStorage props, String prefix, String key)
  {
    String sshHost = props.getProperty(prefix + key + PROP_SSH_HOST, null);
    String sshUser = props.getProperty(prefix + key + PROP_SSH_USER, null);
    String sshPwd = props.getProperty(prefix + key + PROP_SSH_PWD, null);
    String keyFile = props.getProperty(prefix + key + PROP_SSH_KEYFILE, null);
    int sshPort = props.getIntProperty(prefix + key + PROP_SSH_PORT, Integer.MIN_VALUE);
    boolean tryAgent = props.getBoolProperty(prefix + key + PROP_SSH_TRY_AGENT, false);
    SshHostConfig config = new SshHostConfig();
    if (sshHost != null && sshUser != null)
    {
      config.setHostname(sshHost);
      config.setUsername(sshUser);
      config.setPassword(sshPwd);
      config.setPrivateKeyFile(keyFile);
      config.setTryAgent(tryAgent);
      if (sshPort != Integer.MIN_VALUE) config.setSshPort(sshPort);
    }
    return config;
  }

  private SshConfig readSshConfig(PropertyStorage props, String prefix, String key)
  {
    String dbHost = props.getProperty(prefix + key + PROP_SSH_DB_HOST, null);
    int localPort = props.getIntProperty(prefix + key + PROP_SSH_LOCAL_PORT, Integer.MIN_VALUE);
    int dbPort = props.getIntProperty(prefix + key + PROP_SSH_DB_PORT, Integer.MIN_VALUE);

    SshConfig config = null;
    if (dbHost != null || localPort != Integer.MIN_VALUE || dbPort != Integer.MIN_VALUE)
    {
      config = new SshConfig();
      config.setLocalPort(localPort);
      config.setDbHostname(dbHost);
      config.setDbPort(dbPort);
      String configName = props.getProperty(prefix + key + PROP_SSH_GLOBAL_CONFIG, null);
      if (StringUtil.isBlank(configName))
      {
        config.setHostConfig(readHostConfig(props, prefix, key));
      }
      else
      {
        config.setSshHostConfigName(configName);
      }
    }

    return config;
  }

}
