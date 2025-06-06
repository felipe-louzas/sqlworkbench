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

import java.awt.Color;
import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;
import workbench.ssh.SshConfig;
import workbench.ssh.SshHostConfig;
import workbench.ssh.SshManager;

import workbench.db.postgres.PgPassReader;

import workbench.gui.profiles.ProfileKey;

import workbench.sql.DelimiterDefinition;
import workbench.sql.VariablePool;

import workbench.util.CollectionUtil;
import workbench.util.FileDialogUtil;
import workbench.util.GlobalPasswordManager;
import workbench.util.StringUtil;
import workbench.util.WbCipher;
import workbench.util.WbDesCipher;
import workbench.util.WbFile;
import workbench.util.WbProperties;

/**
 *  A class to store a connection definition including non-JDBC properties
 *  specific to the application.
 *  @author Thomas Kellerer
 */
public class ConnectionProfile
  implements Serializable
{
  public static final String PROPERTY_PROFILE_GROUP = "profileGroup";
  public static final String CRYPT_PREFIX = "@*@";
  public static final String MASTER_CRYPT_PREFIX = "$@wb@$";
  private String name;
  private String url;
  private String temporaryUrl;
  private String driverclass;
  private String username;
  private String temporaryUsername;
  private String password;
  private String driverName;
  private final List<String> groupPath = new ArrayList<>();
  private String icon;
  private boolean autocommit;
  private boolean storeCacheLocally;
  private boolean rollbackBeforeDisconnect;
  private boolean changed;
  private boolean groupChanged;
  private boolean isNew;
  private boolean usePgPass;
  private boolean storePassword = true;
  private boolean separateConnection;
  private Properties connectionProperties;
  private Properties variables;
  private String workspaceFile;
  private boolean ignoreDropErrors;
  private boolean trimCharData;
  private boolean temporaryProfile;
  private boolean oracleSysDBA;
  private boolean detectOpenTransaction;

  private boolean readOnly;
  private boolean preventNoWhere;
  private boolean confirmUpdates;
  private boolean promptForUsername;

  private Integer defaultFetchSize;

  private boolean emptyStringIsNull;
  private boolean includeNullInInsert = true;
  private boolean removeComments;
  private boolean rememberExplorerSchema;
  private boolean hideWarnings;
  private boolean echoConnectScriptStatements = true;
  private String postConnectScript;
  private String preDisconnectScript;
  private String idleScript;
  private long idleTime = 0;
  private Color infoColor;
  private boolean copyPropsToSystem;
  private Integer connectionTimeout;

  private DelimiterDefinition alternateDelimiter;
  private ObjectNameFilter schemaFilter;
  private ObjectNameFilter catalogFilter;
  private String macroFileName;
  private String defaultDirectory;
  private final Set<String> tags = CollectionUtil.caseInsensitiveSet();

  private SshConfig sshConfig;

  private static int nextId = 1;
  private int internalId;

  public ConnectionProfile()
  {
    this.isNew = true;
    this.changed = true;
    this.internalId = nextId++;
  }

  public ConnectionProfile(String profileName, String driverClass, String url, String userName, String pwd)
  {
    this();
    setUrl(url);
    setDriverclass(driverClass);
    setUsername(userName);
    setPassword(pwd);
    setName(profileName);
    resetChangedFlags();
  }

  public static ConnectionProfile createEmptyProfile()
  {
    ConnectionProfile cp = new ConnectionProfile();
    cp.setUseSeparateConnectionPerTab(Settings.getInstance().getProfileDefaultSeparateConnection());
    cp.setStoreExplorerSchema(Settings.getInstance().getProfileDefaultStoreExplorerSchema());
    cp.setName(ResourceMgr.getString("TxtEmptyProfileName"));
    return cp;
  }

  public int internalId()
  {
    return internalId;
  }

  public String getSettingsKey()
  {
    if (name == null || groupPath.isEmpty()) return null;
    Pattern p = Pattern.compile("[^0-9A-Za-z]+");
    String cleanPath = "";
    for (String group : getGroups())
    {
      Matcher gm = p.matcher(group);
      String cleanGroup = gm.replaceAll("").toLowerCase();
      if (!cleanPath.isEmpty()) cleanPath += ".";
      cleanPath += cleanGroup;
    }

    Matcher nm = p.matcher(name);
    String cleanName = nm.replaceAll("").toLowerCase();
    return cleanPath + "." + cleanName;
  }

  public String getDefaultDirectory()
  {
    return defaultDirectory;
  }

  public void setDefaultDirectory(String scriptDirectory)
  {
    String dir = StringUtil.trimToNull(scriptDirectory);
    if (this.defaultDirectory != null && dir != null)
    {
      File current = new File(defaultDirectory);
      File newDir = new File(dir);
      if (!current.equals(newDir))
      {
        this.defaultDirectory = dir;
        this.changed = true;
      }
    }
    else
    {
      this.defaultDirectory = dir;
      this.changed = true;
    }
  }

  public boolean isConfigured()
  {
    return StringUtil.isNotBlank(this.driverclass) && StringUtil.isNotBlank(this.url) && this.url.startsWith("jdbc:");
  }

  public String getMacroFilename()
  {
    return macroFileName;
  }

  public WbFile getMacroFile()
  {
    if (StringUtil.isBlank(macroFileName)) return null;
    String fname = FileDialogUtil.replaceMacroDir(macroFileName);
    fname = FileDialogUtil.replaceConfigDir(fname);
    WbFile f = new WbFile(fname);
    if (f.isAbsolute() && f.exists())
    {
      return f;
    }
    WbFile realFile = new WbFile(Settings.getInstance().getMacroBaseDirectory(), macroFileName);
    if (realFile.exists()) return realFile;
    return null;
  }

  public void setMacroFilename(String fname)
  {
    if (StringUtil.stringsAreNotEqual(fname, macroFileName))
    {
      this.macroFileName = fname;
      this.changed = true;
    }
  }

  public Set<String> getTags()
  {
    return Collections.unmodifiableSet(tags);
  }

  public String getTagList()
  {
    return StringUtil.listToString(tags, ',', false);
  }

  public void setTagList(String list)
  {
    Set<String> tagList = CollectionUtil.caseInsensitiveSet(StringUtil.stringToList(list, ",", true, true, false, false));
    changed = changed || !tags.equals(tagList);
    tags.clear();
    tags.addAll(tagList);
  }

  public boolean getStoreCacheLocally()
  {
    return storeCacheLocally;
  }

  public void setStoreCacheLocally(boolean flag)
  {
    if (flag != storeCacheLocally) changed = true;
    this.storeCacheLocally = flag;
  }

  public boolean getPromptForUsername()
  {
    return promptForUsername;
  }

  public void setPromptForUsername(boolean flag)
  {
    if (flag != promptForUsername) changed = true;
    this.promptForUsername = flag;
  }

  public boolean getDetectOpenTransaction()
  {
    return detectOpenTransaction;
  }

  public void setDetectOpenTransaction(boolean flag)
  {
    changed = (flag != detectOpenTransaction);
    detectOpenTransaction = flag;
  }

  public boolean isTemporaryProfile()
  {
    return temporaryProfile;
  }

  public void setTemporaryProfile(boolean flag)
  {
    this.temporaryProfile = flag;
  }

  public ObjectNameFilter getCatalogFilter()
  {
    return catalogFilter;
  }

  public void setCatalogFilter(ObjectNameFilter filter)
  {
    if (catalogFilter == null && filter == null) return;
    if (filter == null)
    {
      changed = true;
    }
    else
    {
      changed = changed || filter.isModified();
    }
    catalogFilter = filter;
  }

  public ObjectNameFilter getSchemaFilter()
  {
    return schemaFilter;
  }

  public void setSchemaFilter(ObjectNameFilter filter)
  {
    if (schemaFilter == null && filter == null) return;
    if (filter == null)
    {
      changed = true;
    }
    else
    {
      changed = changed || filter.isModified();
    }
    schemaFilter = filter;
  }

  public Color getInfoDisplayColor()
  {
    return this.infoColor;
  }

  public boolean isHideWarnings()
  {
    return hideWarnings;
  }

  public void setHideWarnings(boolean flag)
  {
    this.changed = hideWarnings != flag;
    this.hideWarnings = flag;
  }

  public boolean getOracleSysDBA()
  {
    return oracleSysDBA;
  }

  public void setOracleSysDBA(boolean flag)
  {
    this.changed = oracleSysDBA != flag;
    this.oracleSysDBA = flag;
  }

  public int getConnectionTimeoutValue()
  {
    if (connectionTimeout == null) return 0;
    return connectionTimeout.intValue();
  }

  public Integer getConnectionTimeout()
  {
    return connectionTimeout;
  }

  public void setConnectionTimeout(Integer seconds)
  {
    int currentValue = (connectionTimeout == null ? Integer.MIN_VALUE : connectionTimeout.intValue());
    int newValue = (seconds == null ? Integer.MIN_VALUE : seconds.intValue());

    if (currentValue != newValue)
    {
      this.connectionTimeout = (newValue > 0 ? seconds : null);
      this.changed = true;
    }
  }

  public void setInfoDisplayColor(Color c)
  {
    if (this.infoColor == null && c == null) return;
    if (this.infoColor != null && c != null)
    {
      this.changed = changed || !this.infoColor.equals(c);
    }
    else
    {
      this.changed = true;
    }
    this.infoColor = c;
  }

  public void setAlternateDelimiterString(String delim)
  {
    DelimiterDefinition def = new DelimiterDefinition(delim);
    if (!def.isStandard())
    {
      setAlternateDelimiter(def);
    }
  }

  public String getAlternateDelimiterString()
  {
    if (this.alternateDelimiter == null) return null;
    if (this.alternateDelimiter.isEmpty()) return null;
    return alternateDelimiter.getDelimiter();
  }

  public DelimiterDefinition getAlternateDelimiter()
  {
    if (this.alternateDelimiter == null) return null;
    if (this.alternateDelimiter.isEmpty()) return null;
    return this.alternateDelimiter;
  }

  public void setAlternateDelimiter(DelimiterDefinition def)
  {
    if (def == null && this.alternateDelimiter == null) return;

    // Do not accept a semicolon as the alternate delimiter
    if (def != null && def.isStandard()) return;

    if (!Objects.equals(def, this.alternateDelimiter) || (def != null && def.isChanged()))
    {
      this.alternateDelimiter = def;
      this.changed = true;
    }
  }

  public boolean getCopyExtendedPropsToSystem()
  {
    return this.copyPropsToSystem;
  }

  public void setCopyExtendedPropsToSystem(boolean flag)
  {
    if (flag != this.copyPropsToSystem) changed = true;
    this.copyPropsToSystem = flag;
  }

  public boolean isReadOnly()
  {
    return readOnly;
  }

  public void setReadOnly(boolean flag)
  {
    if (this.readOnly != flag) changed = true;
    this.readOnly = flag;
  }

  public void setPreventDMLWithoutWhere(boolean flag)
  {
    if (preventNoWhere != flag) changed = true;
    preventNoWhere = flag;
  }

  public boolean getPreventDMLWithoutWhere()
  {
    return preventNoWhere;
  }

  public boolean getTrimCharData()
  {
    return trimCharData;
  }

  public void setTrimCharData(boolean flag)
  {
    if (flag != trimCharData) changed = true;
    trimCharData = flag;
  }

  public boolean getStoreExplorerSchema()
  {
    return rememberExplorerSchema;
  }

  public void setStoreExplorerSchema(boolean value)
  {
    if (value != rememberExplorerSchema) changed = true;
    rememberExplorerSchema = value;
  }

  public String getLastGroupName()
  {
    List<String> groups = getGroups();
    return groups.get(groups.size() - 1);
  }

  /**
   * Returns the group's path string.
   * <p>
   * This returns a delimited string of the groups returned by {@link #getGroups()}.<br>
   * The path elements are delimited with a <tt>/</tt> and are quoted if needed. This format
   * is usable for storing the group path information.
   * </p>
   * <p>
   * Quotes inside the path elementes are escaped using a backslash.
   * </p>
   * <p>
   * As the elements of the path might be quoted to enable a safe writing and reading
   * to persistent storage, the value is not suited for display to the user. Use
   * {@link getGroupPathString()} instead.
   * </p>
   */
  public String getGroup()
  {
    return ProfileKey.getGroupPathEscaped(getGroups());
  }

  /**
   * Set the profile group path.
   * <p>
   * This method is delegated to {@link #setGroupByPathString(String)}.
   * </p>
   * @param path the group path, ignored if null or empty
   */
  public void setGroup(String path)
  {
    setGroupByPathString(path);
  }

  public String getGroupPathString()
  {
    return ProfileKey.getGroupPathAsString(getGroups());
  }

  /**
   * Set the profile group path.
   * <p>
   * The path string is expected to be escaped if the path
   * elements contain a forward slash or a double quote.
   * </p>
   * @param path the group path, ignored if null or empty
   * @see ProfileKey#parseGroupPath(String)
   */
  public void setGroupByPathString(String path)
  {
    if (StringUtil.isBlank(path)) return;
    List<String> groups = ProfileKey.parseGroupPath(path);
    setGroups(groups);
  }

  public List<String> getGroups()
  {
    if (groupPath.isEmpty()) return List.of(ResourceMgr.getString("LblDefGroup"));
    return Collections.unmodifiableList(groupPath);
  }

  public void setGroups(List<String> newPath)
  {
    if (newPath == null) return;
    if (newPath.equals(groupPath)) return;
    this.groupPath.clear();
    this.groupPath.addAll(newPath);
    this.changed = true;
    this.groupChanged = true;
  }

  public String getIcon()
  {
    return this.icon;
  }

  public void setIcon(String icon)
  {
    if (StringUtil.stringsAreNotEqual(this.icon, icon)) changed = true;
    this.icon = icon;
  }

  public boolean isProfileForKey(ProfileKey key)
  {
    ProfileKey myKey = getKey();
    return myKey.equals(key);
  }

  public ProfileKey getKey()
  {
    return new ProfileKey(this.getName(), getGroups());
  }

  /**
   * This method is used for backward compatibility. Old profiles
   * had this property and to be able to load XML files with
   * old profiles the setter must still be there.
   *
   * @deprecated
   * @param flag
   */
  public void setDisableUpdateTableCheck(boolean flag) { }

  /**
   * Return true if the application should use a separate connection
   * per tab or if all SQL tabs including DbExplorer tabs and windows
   * should share the same connection
   */
  public boolean getUseSeparateConnectionPerTab()
  {
    return this.separateConnection;
  }

  public void setUseSeparateConnectionPerTab(boolean aFlag)
  {
    if (this.separateConnection != aFlag) this.changed = true;
    this.separateConnection = aFlag;
  }

  public void setIncludeNullInInsert(boolean flag)
  {
    if (this.includeNullInInsert != flag) this.changed = true;
    this.includeNullInInsert = flag;
  }

  /**
   * Define how columns with a NULL value are treated when creating INSERT statements.
   * If this is set to false, then any column with an a NULL value
   * will not be included in an generated INSERT statement.
   *
   * @see workbench.storage.StatementFactory#createInsertStatement(workbench.storage.RowData, boolean, String, java.util.List)
   */
  public boolean getIncludeNullInInsert()
  {
    return this.includeNullInInsert;
  }

  /**
   * Define how empty strings (Strings with length == 0) are treated.
   * If this is set to true, then they are treated as a NULL value, else an
   * empty string is sent to the database during update and insert.
   * @see #setIncludeNullInInsert(boolean)
   */
  public void setEmptyStringIsNull(boolean flag)
  {
    if (this.emptyStringIsNull != flag) this.changed = true;
    this.emptyStringIsNull = flag;
  }

  public boolean getEmptyStringIsNull()
  {
    return this.emptyStringIsNull;
  }

  /**
   * Define how comments inside SQL statements are handled.
   * If this is set to true, then any comment (single line comments with --
   * or multi-line comments using /* are removed from the statement
   * before sending it to the database.
   *
   * @see workbench.sql.StatementRunner#runStatement(java.lang.String)
   */
  public void setRemoveComments(boolean flag)
  {
    if (this.removeComments != flag) this.changed = true;
    this.removeComments = flag;
  }

  public boolean getRemoveComments()
  {
    return this.removeComments;
  }

  public boolean getRollbackBeforeDisconnect()
  {
    return this.rollbackBeforeDisconnect;
  }

  public void setRollbackBeforeDisconnect(boolean flag)
  {
    if (flag != this.rollbackBeforeDisconnect) this.changed = true;
    this.rollbackBeforeDisconnect = flag;
  }

  /**
   * Sets the current password in plain text.
   * <p>
   * If the password is not already encrypted, it will be encrypted if needed/configured
   *
   * @see #getPassword()
   * @see #getDecryptedPassword()
   * @see workbench.util.WbCipher#encryptString(String)
   * @see GlobalPasswordManager#encrypt(String)
   */
  public final void setPassword(String pwd)
  {
    if (pwd == null)
    {
      if (this.password != null)
      {
        this.password = null;
        if (this.storePassword) this.changed = true;
      }
      return;
    }

    PasswordTrimType trimType = Settings.getInstance().getPassworTrimType();

    if (trimType == PasswordTrimType.always)
    {
      pwd = pwd.trim();
    }
    else if (trimType == PasswordTrimType.blankOnly && StringUtil.isBlank(pwd))
    {
      pwd = "";
    }

    // check encryption settings when reading the profiles...
    if (Settings.getInstance().getUseMasterPassword())
    {
      if (!isEncrypted(pwd))
      {
        pwd = MASTER_CRYPT_PREFIX + GlobalPasswordManager.getInstance().encrypt(pwd);
      }
    }
    else
    {
      // no encryption should be used, but password is encrypted, decrypt it now.
      if (this.isEncrypted(pwd))
      {
        pwd = this.decryptPassword(pwd);
      }
    }

    if (!pwd.equals(this.password))
    {
      this.password = pwd;
      if (this.storePassword) this.changed = true;
    }
  }

  public boolean isPasswordEncrypted()
  {
    return this.isEncrypted(this.password);
  }

  /**
   *  Returns the encrypted version of the password.
   *
   *  This getter/setter pair is used when saving the profile
   *
   *  @see #getDecryptedPassword()
   */
  public String getPassword()
  {
    if (this.storePassword)
    {
      return this.password;
    }
    else
    {
      return null;
    }
  }

  public boolean usePgPass()
  {
    return usePgPass;
  }

  /**
   * Set the password from a plain readable text
   * @param aPassword
   */
  public void setInputPassword(String aPassword)
  {
    this.setPassword(aPassword);
  }

  /**
   *  Returns the plain text version of the
   *  current password.
   *
   *  This method is used to populate the profile editor.
   *
   *  @see #encryptPassword(String)
   */
  public String getDecryptedPassword()
  {
    return this.decryptPassword(getPassword());
  }

  public void setEncryptedPassword(String encrypted)
  {
    if (this.getStorePassword())
    {
      this.changed = changed || StringUtil.stringsAreNotEqual(this.password, encrypted);
      this.password = encrypted;
    }
  }

  /**
   *  Returns the plain text version of the given password.
   *
   *  This is not put into the getPassword()
   *  method because the XMLEncode would write the
   *  password in plain text into the XML file.
   *
   *  A method beginning with decrypt is not
   *  regarded as a property and thus not written
   *  to the XML file.
   *
   *  @param aPwd the encrypted password
   */
  public String decryptPassword(String pwd)
  {
    if (StringUtil.isEmpty(pwd)) return "";
    if (pwd.startsWith(CRYPT_PREFIX))
    {
      WbCipher des = WbDesCipher.getInstance();
      return des.decryptString(pwd.substring(CRYPT_PREFIX.length()));
    }
    else if (pwd.startsWith(MASTER_CRYPT_PREFIX))
    {
      return GlobalPasswordManager.getInstance().decrypt(pwd.substring(MASTER_CRYPT_PREFIX.length()));
    }
    return pwd;
  }

  private boolean isEncrypted(String aPwd)
  {
    return aPwd.startsWith(CRYPT_PREFIX) || aPwd.startsWith(MASTER_CRYPT_PREFIX);
  }

  public void setNew()
  {
    this.changed = true;
    this.isNew = true;
  }

  public boolean isNew()
  {
    return this.isNew;
  }

  public boolean isChanged()
  {
    return this.changed || this.isNew;
  }

  public boolean isGroupChanged()
  {
    return this.groupChanged;
  }

  /**
   * Reset the changed and new flags.
   *
   * @see #isNew()
   * @see #isChanged()
   */
  public final void resetChangedFlags()
  {
    this.changed = false;
    this.groupChanged = false;
    this.isNew = false;
    if (this.alternateDelimiter != null) this.alternateDelimiter.resetChanged();
    if (this.schemaFilter != null) schemaFilter.resetModified();
    if (this.catalogFilter != null) catalogFilter.resetModified();
  }

  public String debugString()
  {
    return "\"" + getKey().toString() + "\":" +
      " [user=" + DbDriver.getUsernameForLogging(this.username) +
      ", url=" + DbDriver.getURLForLogging(this) +
      ", driverClass=" + driverclass +
      ", driverName=" + driverName + "]";
  }

  /**
   *  Returns the name of the Profile
   */
  @Override
  public String toString()
  {
    return this.name;
  }

  public String getIdString()
  {
    return getKey().toString() + " using: " + this.driverclass;
  }
  /**
   * The hashCode is based on the profile key's hash code.
   *
   * @see #getKey()
   * @see ProfileKey#hashCode()
   * @return the hashcode for the profile key
   */
  @Override
  public int hashCode()
  {
    return getKey().hashCode();
  }

  @Override
  public boolean equals(Object other)
  {
    try
    {
      ConnectionProfile prof = (ConnectionProfile)other;
      return this.getKey().equals(prof.getKey());
    }
    catch (ClassCastException e)
    {
      return false;
    }
  }

  public void resetTemporaryUrl()
  {
    this.temporaryUrl = null;
  }

  public void switchToTemporaryUrl(String tempURL)
  {
    this.temporaryUrl = tempURL;
  }

  public String getActiveUrl()
  {
    if (StringUtil.isBlank(this.temporaryUrl)) return this.url;
    return this.temporaryUrl;
  }

  public String getUrl()
  {
    return this.url;
  }

  public final void setUrl(String newUrl)
  {
    if (newUrl != null) newUrl = newUrl.trim();
    if (StringUtil.stringsAreNotEqual(newUrl, url)) changed = true;
    url = newUrl;
    usePgPass = (PgPassReader.isPgUrl(url) || PgPassReader.isGreenplumUrl(url)) && Settings.getInstance().usePgPassFile();
  }

  public String getDriverName()
  {
    return driverName;
  }

  public final void setDriverName(String name)
  {
    if (StringUtil.stringsAreNotEqual(name, this.driverName))
    {
      this.driverName = StringUtil.trim(name);
      this.changed = true;
    }
  }

  public String getDriverclass()
  {
    return this.driverclass;
  }

  public final void setDriverclass(String drvClass)
  {
    if (StringUtil.stringsAreNotEqual(drvClass, driverclass))
    {
      changed = true;
      driverclass = StringUtil.trim(drvClass);
    }
  }

  public void setDriver(DbDriver driver)
  {
    if (driver != null)
    {
      setDriverName(driver.getName());
      setDriverclass(driver.getDriverClass());
    }
  }

  public String getUrlInfo()
  {
    if (StringUtil.isBlank(this.username))
    {
      return this.url;
    }
    return this.username + "@" + url.replace("jdbc:", "");
  }

  public String getUsername()
  {
    if (temporaryUsername != null) return temporaryUsername;
    return this.username;
  }

  private String getPgPassPassword()
  {
    if (usePgPass())
    {
      String pwd = System.getenv("PGPASSWORD");
      if (pwd != null)
      {
        LogMgr.logDebug(new CallerInfo(){}, "Using password from environment variable PGPASSWORD");
        return pwd;
      }

      PgPassReader reader = new PgPassReader(url, getLoginUser());
      LogMgr.logDebug(new CallerInfo(){}, "Using password from " + PgPassReader.getPgPassFile().getFullpathForLogging());
      return reader.getPasswordFromFile();
    }
    return null;
  }

  public String getLoginPassword()
  {
    if (StringUtil.isNotEmpty(password))
    {
      return this.decryptPassword(password);
    }

    if (usePgPass() && !getStorePassword())
    {
      return getPgPassPassword();
    }
    return null;
  }

  public String getLoginUser()
  {
    String user = getUsername();
    if (usePgPass() && StringUtil.isEmpty(user))
    {
      user = System.getenv("PGUSER");
      if (user == null)
      {
        user = System.getProperty("user.name");
      }
    }

    if (Settings.getInstance().replaceEnvVarsInProfile())
    {
      user = StringUtil.replaceProperties(user); // replace standard Java properties first
      user = StringUtil.replaceProperties(System.getenv(), user); // now replace system environment variables
    }

    return user;
  }

  public boolean needsSSHPasswordPrompt()
  {
    SshHostConfig config = getSshHostConfig();
    if (config == null) return false;

    if (config.getTryAgent() == true) return false;

    if (config.getPrivateKeyFile() != null)
    {
      // Assume that non-encrypted key files don't need a passphrase
      SshManager sshManager = ConnectionMgr.getInstance().getSshManager();
      if (!sshManager.needsPassphrase(config)) return false;

      // Check for cached passphrases for this config
      String passphrase = sshManager.getPassphrase(config);
      if (passphrase != null)
      {
        config.setTemporaryPassword(passphrase);
        return false;
      }
    }

    return StringUtil.isBlank(config.getPassword());
  }

  private boolean integratedSecurityEnabled()
  {
    if (url == null) return false;

    if (url.startsWith("jdbc:sqlserver:"))
    {
      Pattern p = Pattern.compile(";\\s*integratedSecurity\\s*=\\s*true", Pattern.CASE_INSENSITIVE);
      Matcher m = p.matcher(url);
      return m.find();
    }
    return false;
  }

  public boolean needsPasswordPrompt()
  {
    if (integratedSecurityEnabled()) return false;
    return getLoginPassword() == null && getStorePassword() == false;
  }

  public void setTemporaryUsername(String tempName)
  {
    this.temporaryUsername = tempName;
  }

  public final void setUsername(String newName)
  {
    if (StringUtil.stringsAreNotEqual(newName, username) && !promptForUsername) changed = true;
    this.temporaryUsername = null;
    this.username = StringUtil.trim(newName);
  }

  public boolean getAutocommit()
  {
    return this.autocommit;
  }

  public void setAutocommit(boolean aFlag)
  {
    if (aFlag != this.autocommit)
    {
      this.changed = true;
    }
    this.autocommit = aFlag;
  }

  public String getName()
  {
    return this.name;
  }

  public final void setName(String aName)
  {
    if (StringUtil.stringsAreNotEqual(name, aName)) changed = true;
    this.name = aName;
  }

  public boolean getStorePassword()
  {
    return this.storePassword;
  }

  public void setStorePassword(boolean aFlag)
  {
    if (aFlag != this.storePassword)
    {
      this.changed = true;
    }
    this.storePassword = aFlag;
  }


  /**
   * Returns a copy of this profile keeping it's modified state and internalId.
   * isNew(), isChanged() and getInternalId() of the copy will return the same values as this instance
   *
   * @return a copy of this profile
   * @see #isNew()
   * @see #isChanged()
   */
  public ConnectionProfile createStatefulCopy()
  {
    ConnectionProfile result = createCopy();
    result.isNew = this.isNew;
    result.changed = this.changed;
    result.internalId = this.internalId;
    return result;
  }

  /**
   * Returns a copy of this profile.
   *
   * The copy is marked as "new" and "changed", so isNew() and isChanged()
   * will return true on the copy
   *
   * @return a copy of this profile
   * @see #isNew()
   * @see #isChanged()
   */
  public ConnectionProfile createCopy()
  {
    ConnectionProfile result = new ConnectionProfile();
    result.setUrl(url);
    result.setAutocommit(autocommit);
    result.setDriverclass(driverclass);
    result.setConnectionTimeout(connectionTimeout);
    result.setDriverName(driverName);
    result.setName(name);
    result.setGroups(groupPath);
    result.setIcon(icon);
    result.setPassword(getPassword());
    result.setOracleSysDBA(oracleSysDBA);
    result.setUsername(username);
    result.setWorkspaceFile(workspaceFile);
    result.setIgnoreDropErrors(ignoreDropErrors);
    result.setUseSeparateConnectionPerTab(separateConnection);
    result.setTrimCharData(trimCharData);
    result.setIncludeNullInInsert(includeNullInInsert);
    result.setEmptyStringIsNull(emptyStringIsNull);
    result.setRollbackBeforeDisconnect(rollbackBeforeDisconnect);
    result.setConfirmUpdates(confirmUpdates);
    result.setStorePassword(storePassword);
    result.setDefaultFetchSize(defaultFetchSize);
    result.setStoreExplorerSchema(rememberExplorerSchema);
    result.setIdleScript(idleScript);
    result.setIdleTime(idleTime);
    result.setDefaultDirectory(defaultDirectory);
    result.setPreDisconnectScript(preDisconnectScript);
    result.setPostConnectScript(postConnectScript);
    result.setEchoConnectScriptStatements(echoConnectScriptStatements);
    result.setInfoDisplayColor(infoColor);
    result.setReadOnly(readOnly);
    result.setAlternateDelimiter(alternateDelimiter == null ? null : alternateDelimiter.createCopy());
    result.setHideWarnings(hideWarnings);
    result.setCopyExtendedPropsToSystem(copyPropsToSystem);
    result.setRemoveComments(this.removeComments);
    result.setCatalogFilter(this.catalogFilter == null ? null : catalogFilter.createCopy());
    result.setSchemaFilter(this.schemaFilter == null ? null : schemaFilter.createCopy());
    result.setDetectOpenTransaction(this.detectOpenTransaction);
    result.setPreventDMLWithoutWhere(this.preventNoWhere);
    result.setPromptForUsername(this.promptForUsername);
    result.setStoreCacheLocally(this.storeCacheLocally);
    result.setMacroFilename(this.macroFileName);
    result.setSshConfig(sshConfig == null ? null : sshConfig.createCopy());
    result.tags.addAll(tags);
    result.temporaryUsername = null;
    result.connectionProperties = WbProperties.createCopy(this.connectionProperties);
    result.variables = WbProperties.createCopy(this.variables);
    return result;
  }

  public static Comparator<ConnectionProfile> getNameComparator()
  {
    return (ConnectionProfile o1, ConnectionProfile o2) ->
    {
      if (o1 == null && o2 == null) return 0;
      if (o1 == null) return -1;
      if (o2 == null) return 1;
      return StringUtil.compareStrings(o1.name, o2.name, true);
    };
  }

  public boolean getIgnoreDropErrors()
  {
    return this.ignoreDropErrors;
  }

  public void setIgnoreDropErrors(boolean aFlag)
  {
    if (aFlag != this.ignoreDropErrors) changed = true;
    this.ignoreDropErrors = aFlag;
  }

  public String getWorkspaceFile()
  {
    return this.workspaceFile;
  }

  public void setWorkspaceFile(String newFile)
  {
    if (StringUtil.stringsAreNotEqual(workspaceFile, newFile)) changed = true;
    this.workspaceFile = newFile;
  }

  public void addConnectionProperty(String key, String value)
  {
    if (key == null) return;
    if (this.connectionProperties == null)
    {
      this.connectionProperties = new Properties();
    }
    String old = (String)this.connectionProperties.put(key, value);
    if (StringUtil.stringsAreNotEqual(old, value)) this.changed = true;
  }

  public Properties getConnectionProperties()
  {
    return this.connectionProperties;
  }

  public void setConnectionProperties(Properties props)
  {
    if (CollectionUtil.isEmpty(props))
    {
      changed = CollectionUtil.isNonEmpty(connectionProperties);
      connectionProperties = null;
    }
    else if (!props.equals(connectionProperties))
    {
      changed = true;
      connectionProperties = WbProperties.createCopy(props);
    }
  }

  public Properties getConnectionVariables()
  {
    return variables;
  }

  public void setConnectionVariables(Properties vars)
  {
    if (CollectionUtil.isEmpty(vars))
    {
      changed = CollectionUtil.isNonEmpty(variables);
      variables = null;
    }
    else if (!vars.equals(variables))
    {
      changed = true;
      variables = WbProperties.createCopy(vars);
    }
  }

  public boolean getConfirmUpdates()
  {
    return confirmUpdates;
  }

  public void setConfirmUpdates(boolean flag)
  {
    if (flag != this.confirmUpdates) this.changed = true;
    this.confirmUpdates = flag;
  }

  public int getFetchSize()
  {
    if (this.defaultFetchSize == null) return -1;
    else return this.defaultFetchSize.intValue();
  }

  public Integer getDefaultFetchSize()
  {
    return defaultFetchSize;
  }

  public void setDefaultFetchSize(Integer fetchSize)
  {
    int currentValue = (defaultFetchSize == null ? Integer.MIN_VALUE : defaultFetchSize.intValue());
    int newValue = (fetchSize == null ? Integer.MIN_VALUE : fetchSize.intValue());

    if (currentValue != newValue)
    {
      this.defaultFetchSize = (newValue > 0 ? fetchSize : null);
      this.changed = true;
    }
  }

  public boolean getEchoConnectScriptStatements()
  {
    return echoConnectScriptStatements;
  }

  public void setEchoConnectScriptStatements(boolean flag)
  {
    if (echoConnectScriptStatements != flag) changed = true;
    echoConnectScriptStatements = flag;
  }

  public boolean hasConnectScript()
  {
    return
      StringUtil.isNotEmpty(postConnectScript) ||
      StringUtil.isNotEmpty(preDisconnectScript) ||
      (StringUtil.isNotEmpty(idleScript) && idleTime > 0);
  }

  public String getPostConnectScript()
  {
    return postConnectScript;
  }


  public void setPostConnectScript(String script)
  {
    if (StringUtil.stringsAreNotEqual(script, this.postConnectScript))
    {
      this.postConnectScript = StringUtil.trimToNull(script);
      this.changed = true;
    }
  }

  public String getPreDisconnectScript()
  {
    return preDisconnectScript;
  }

  public void setPreDisconnectScript(String script)
  {
    if (StringUtil.stringsAreNotEqual(script, this.preDisconnectScript))
    {
      this.preDisconnectScript = StringUtil.trimToNull(script);
      this.changed = true;
    }
  }

  public long getIdleTime()
  {
    return this.idleTime;
  }

  public void setIdleTime(long time)
  {
    if (time != this.idleTime)
    {
      this.changed = true;
    }
    this.idleTime = time;
  }

  public String getIdleScript()
  {
    return idleScript;
  }

  public void setIdleScript(String script)
  {
    if (StringUtil.stringsAreNotEqual(script, this.idleScript))
    {
      idleScript = StringUtil.trimToNull(script);
      this.changed = true;
    }
  }

  public boolean hasValidIdleSetup()
  {
    return this.idleTime > 0 && StringUtil.isNotBlank(idleScript);
  }
  
  public static String makeFilename(String jdbcUrl, String userName)
  {
    Pattern invalidChars = Pattern.compile("[^a-zA-Z0-9$]+");
    int pos = jdbcUrl.indexOf('?');
    if (pos > 0)
    {
      jdbcUrl = jdbcUrl.substring(0, pos);
    }
    Matcher urlMatcher = invalidChars.matcher(jdbcUrl);
    String url = urlMatcher.replaceAll("_");

    // remove the jdbc_ prefix, it is not needed
    url = url.substring(5);

    String user = "";
    if (StringUtil.isNotBlank(userName))
    {
      Matcher userMatcher = invalidChars.matcher(userName);
      user = userMatcher.replaceAll("_") + "@";
    }
    return user.toLowerCase() + url.toLowerCase();
  }

  public SshHostConfig getSshHostConfig()
  {
    if (sshConfig == null) return null;
    return sshConfig.getSshHostConfig();
  }

  public SshConfig getSshConfig()
  {
    return sshConfig;
  }

  public void setSshConfig(SshConfig config)
  {
    if (config == null)
    {
      changed = sshConfig != null;
      sshConfig = null;
    }
    else
    {
      if (sshConfig != null)
      {
        sshConfig.copyFrom(config);
        changed = sshConfig.isChanged();
      }
      else
      {
        sshConfig = config.createCopy();
        changed = true;
      }
    }
  }

  public void applyProfileVariables(String variablePoolId)
  {
    if (CollectionUtil.isNonEmpty(variables))
    {
      LogMgr.logInfo(new CallerInfo(){}, "Applying variables defined in the connection profile: " + variables);
      VariablePool.getInstance(variablePoolId).readFromProperties(variables, "connection profile " + getKey());
    }
  }

  public void removeProfileVariables(String variablePoolId)
  {
    if (CollectionUtil.isNonEmpty(variables))
    {
      LogMgr.logInfo(new CallerInfo(){}, "Removing variables defined in the connection profile.");
      VariablePool.getInstance(variablePoolId).removeVariables(variables);
    }
  }

}
