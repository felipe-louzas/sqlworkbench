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

import java.lang.reflect.Field;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.DbExplorerSettings;
import workbench.resource.Settings;

import workbench.db.exporter.RowDataConverter;
import workbench.db.importer.SetObjectStrategy;
import workbench.db.oracle.OracleUtils;
import workbench.db.sqltemplates.TemplateHandler;

import workbench.gui.dbobjects.TableSearchPanel;

import workbench.storage.BlobLiteralType;
import workbench.storage.DmlStatement;

import workbench.sql.EndReadOnlyTrans;
import workbench.sql.SqlCommand;
import workbench.sql.commands.TransactionEndCommand;

import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;
import workbench.util.VersionNumber;

/**
 * Stores and manages db specific settings.
 * <br/>
 * The settings are stored in the global Settings file using
 * {@link workbench.resource.Settings}
 * <br/>
 * Any setting returned from this class will be specific to the DBMS
 * that it was initialized for, identified by the DBID passed to the constructor
 *
 * @author Thomas Kellerer
 * @see DbMetadata#getDbId()
 */
public class DbSettings
{
  public static final String IDX_TYPE_NORMAL = "NORMAL";
  public static final String DEFAULT_CREATE_TABLE_TYPE = "default";
  public static final String DBID_PLACEHOLDER = "[dbid]";

  private static final String NOT_THERE = "$wb$_not_there_$wb$";

  private final String dbId;
  private final Set<String> updatingCommands = CollectionUtil.caseInsensitiveSet();
  private final Set<String> noUpdateCountVerbs = CollectionUtil.caseInsensitiveSet();
  private final Set<String> useMaxRowsVerbs = CollectionUtil.caseInsensitiveSet();
  private boolean alwaysUseMaxRows;

  private final DbSettings aliasSettings;
  private final String prefix;
  private final String versionPrefix;

  public static enum GenerateOwnerType
  {
    always,
    whenNeeded,
    never;
  }

  public DbSettings(String id)
  {
    this(id, -1, -1);
  }

  public DbSettings(String id, int majorVersion, int minorVersion)
  {
    dbId = id;
    prefix = "workbench.db." + id + ".";
    versionPrefix = findVersionPrefixTouse(majorVersion, minorVersion);

    Settings settings = Settings.getInstance();

    CallerInfo nci = new CallerInfo(){};
    String aliasID = settings.getProperty(prefix + "aliasid", null);
    if (aliasID != null)
    {
      LogMgr.logInfo(nci, "Using alias DBID: " + aliasID + " for: " + dbId);
      aliasSettings = new DbSettings(aliasID, majorVersion, minorVersion);
    }
    else
    {
      aliasSettings = null;
    }
    readNoUpdateCountVerbs();
    readUpdatingCommands();
    readMaxRowVerbs();
  }

  public String getAliasId()
  {
    if (aliasSettings != null)
    {
      return aliasSettings.getDbId();
    }
    return null;
  }

  public final String getDbId()
  {
    return this.dbId;
  }

  public List<String> getListProperty(String prop)
  {
    return getListProperty(prop, null, false);
  }

  public List<String> getListProperty(String prop, String defaultValue)
  {
    return getListProperty(prop, defaultValue, false);
  }

  public List<String> getListProperty(String prop, String defaultValue, boolean makeLowerCase)
  {
    return getListProperty(prop, defaultValue, makeLowerCase, false);
  }

  public List<String> getListProperty(String prop, String defaultValue, boolean makeLowerCase, boolean keepQuotes)
  {
    String value = getProperty(prop, defaultValue);
    if (makeLowerCase && value != null)
    {
      value = value.toLowerCase();
    }
    return StringUtil.stringToList(value, ",", true, true, false, keepQuotes);
  }

  public String getProperty(String prop, String defaultValue)
  {
    return getVersionedString(prop, defaultValue);
  }

  /*
   * Finds the highest "version key" for the current DBMS
   * that is smaller or equal than the current DB version.
   */
  private String findVersionPrefixTouse(int major, int minor)
  {
    if (major <= 0) return null;

    Settings set = Settings.getInstance();

    List<String> keys = set.getKeysWithPrefix("workbench.db." + this.dbId + "_");
    if (keys.isEmpty()) return null;

    Comparator<VersionNumber> comp = (VersionNumber o1, VersionNumber o2) ->
    {
      if (o1.equals(o2)) return 0;
      if (o1.isNewerThan(o2)) return -1;
      return 1;
    };
    VersionNumber dbVersion = new VersionNumber(major, minor);

    List<VersionNumber> numbers = new ArrayList<>();

    Pattern p = Pattern.compile("^[0-9]+(\\_[0-9]+){0,1}$");

    for (String key : keys)
    {
      key = key.substring(prefix.length());
      int pos = key.indexOf('.');
      key = key.substring(0, pos);
      Matcher matcher = p.matcher(key);
      if (matcher.matches())
      {
        VersionNumber nr = new VersionNumber(key.replace('_', '.'));
        if (dbVersion.isNewerOrEqual(nr))
        {
          numbers.add(nr);
        }
      }
    }

    if (numbers.isEmpty()) return null;

    Collections.sort(numbers, comp);
    VersionNumber vn = numbers.get(0);
    return "workbench.db." + dbId + "_" + vn.toString().replace('.', '_') + ".";
  }

  private String getVersionedString(String prop, String defaultValue)
  {
    Settings set = Settings.getInstance();

    String result = NOT_THERE;

    if (versionPrefix != null) result = set.getProperty(versionPrefix + prop, NOT_THERE);
    if (result != NOT_THERE) return result;

    if (aliasSettings == null)
    {
      return set.getProperty(prefix + prop, defaultValue);
    }
    result = set.getProperty(prefix + prop, NOT_THERE);
    if (result == NOT_THERE)
    {
      return aliasSettings.getVersionedString(prop, defaultValue);
    }
    return result;
  }

  public void setPropertyTemporary(String prop, boolean flag)
  {
    Settings.getInstance().setTemporaryProperty(prefix + prop, Boolean.toString(flag));
  }

  public void setPropertyTemporary(String prop, String value)
  {
    Settings.getInstance().setTemporaryProperty(prefix + prop, value);
  }

  public void setProperty(String prop, boolean flag)
  {
    Settings.getInstance().setProperty(prefix + prop, flag);
  }

  public void setProperty(String prop, String value)
  {
    Settings.getInstance().setProperty(prefix + prop, value);
  }

  /**
   * Checks if the given SQL verb updates the database.
   * In addition to the built-in detected (e.g. UPDATE, DELETE), the user
   * can configure DB-specific SQL commands that should be considered to update
   * the database.
   * <br/>
   * This is used by the options "confirm updates" and "read only"
   * in the connection profile
   * <br/><br/>
   * The related property is: <tt>workbench.db.[dbid].updatingcommands</tt> (comma separated list)
   * @param verb the SQL command to check
   *
   * @return true if the command was configured
   *
   * @see workbench.sql.SqlCommand#isUpdatingCommand()
   * @see ConnectionProfile#getConfirmUpdates()
   * @see ConnectionProfile#isReadOnly()
   */
  public boolean isUpdatingCommand(String verb)
  {
    if (StringUtil.isEmpty(verb)) return false;
    return updatingCommands.contains(verb);
  }

  public static Map<String, String> getDBMSNames()
  {
    Map<String, String> dbmsNames = new HashMap<>();
    dbmsNames.put("h2", "H2");
    dbmsNames.put("oracle", "Oracle");
    dbmsNames.put("hsql_database_engine", "HSQLDB");
    dbmsNames.put("postgresql", "PostgreSQL");
    dbmsNames.put("db2", "DB2 (LUW)");
    dbmsNames.put("db2h", "DB2 Host");
    dbmsNames.put("db2i", "DB2 iSeries");
    dbmsNames.put("mysql", "MySQL");
    dbmsNames.put("firebird", "Firebird SQL");
    dbmsNames.put("informix_dynamic_server", "Informix");
    dbmsNames.put("sql_anywhere", "SQL Anywhere");
    dbmsNames.put("microsoft_sql_server", "Microsoft SQL Server");
    dbmsNames.put("apache_derby", "Apache Derby");
    return dbmsNames;
  }

  public boolean isPropertySet(String property)
  {
    String value = this.getProperty(property, null);
    return value != null;
  }

  public boolean supportsCreateArray()
  {
    return getBoolProperty("createarray.supported", true);
  }

  public boolean handleArrayDisplay()
  {
    return getBoolProperty("array.adjust.display", true);
  }

  public boolean showArrayType()
  {
    return getBoolProperty("array.show.type", false);
  }

  public boolean useGetStringForBit()
  {
    return getBoolProperty("bit.use.getstring", false);
  }

  public boolean useGetObjectForDates()
  {
    return getBoolProperty("date.use.getobject", false);
  }

  public boolean useLocalTimeForTime()
  {
    return getBoolProperty("time.use.localtime", false);
  }

  public boolean useGetObjectForTimestamps()
  {
    return getBoolProperty("timestamp.use.getobject", false);
  }

  public boolean useGetObjectForTimestampTZ()
  {
    return getBoolProperty("timestamptz.use.getobject", false);
  }

  public boolean useZonedDateTimeForTimestampTZ()
  {
    return getBoolProperty("timestamptz.use.zoneddatetime", false);
  }

  public boolean useOffsetDateTimeForTimestampTZ()
  {
    return getBoolProperty("timestamptz.use.offsetdatetime", false);
  }

  public boolean useGetXML()
  {
    return getBoolProperty("xml.use.getsqlxml", false);
  }

  public ClobAccessType getClobReadMethod()
  {
    String value = getProperty("clob.use.getstring", null);
    if ("true".equals(value))
    {
      return ClobAccessType.string;
    }
    String method = getProperty("clob.read.method", ClobAccessType.string.name());
    return Settings.getInstance().getEnumValue(method, ClobAccessType.string);
  }

  public boolean useSetStringForClobs()
  {
    return getBoolProperty("clob.use.setstring", false);
  }

  public BlobAccessType getBlobReadMethod()
  {
    String useGetBytes = getProperty("blob.use.getbytes", null);
    if (useGetBytes == null)
    {
      String method = getProperty("blob.read.method", BlobAccessType.binaryStream.name());
      return Settings.getInstance().getEnumValue(method, BlobAccessType.binaryStream);
    }

    if (StringUtil.stringToBool(useGetBytes))
    {
      return BlobAccessType.byteArray;
    }
    else
    {
      return BlobAccessType.binaryStream;
    }
  }

  public boolean useSetBytesForBlobs()
  {
    return getBoolProperty("blob.use.setbytes", false);
  }

  public boolean padCharColumns()
  {
    return getBoolProperty("dml.char.pad", false);
  }

  public boolean longVarcharIsClob()
  {
    return getBoolProperty("clob.longvarchar", true);
  }

  public boolean supportsResultSetsWithDML()
  {
    return getBoolProperty("dml.supports.results", true);
  }

  public boolean truncateReturnsRowCount()
  {
    return getBoolProperty("dml.truncate.returns.rows", true);
  }

  public boolean supportsBatchedStatements()
  {
    return getBoolProperty("batchedstatements", false);
  }

  public boolean allowsExtendedCreateStatement()
  {
    return getBoolProperty("extended.createstmt", true);
  }
  public void setUseExtendedCreateStatement(boolean flag)
  {
    setProperty("extended.createstmt", flag);
  }

  public boolean allowsMultipleGetUpdateCounts()
  {
    return getBoolProperty("multipleupdatecounts", true);
  }

  public boolean reportsRealSizeAsDisplaySize()
  {
    return getBoolProperty("charsize.usedisplaysize", false);
  }

  public int getMaxWarnings()
  {
    int defaultMax = 5000;
    int max = getIntProperty("maxwarnings", defaultMax);
    if (max <= 0)
    {
      max = defaultMax;
    }
    return max;
  }

  /**
   * Return the maximum number of result sets that should be considered.
   *
   * @see SqlCommand#processResults
   */
  public int getMaxResults()
  {
    int defaultMax = 50000;
    int max = getIntProperty("maxresults", defaultMax);
    if (max <= 0)
    {
      max = defaultMax;
    }
    return max;
  }

  /**
   * Returns true if the DBMS supports transactional DDL and thus needs a COMMIT after any DDL statement.
   * <br/>
   * The related property is: <tt>workbench.db.[dbid].ddlneedscommit</tt>
   */
  public boolean ddlNeedsCommit()
  {
    return getBoolProperty("ddlneedscommit", false);
  }

  /**
   * Returns when COMMIT statements should be generated in DDL scripts for DBMS that support transactional DDL.
   *
   * The default is <tt>whenNeeded</tt>
   *
   * <br/>
   * The related property is: <tt>workbench.db.[dbid].ddl.commit.type</tt>
   *
   * @see WbConnection#generateCommitForDDL()
   */
  public GenerateDDLCommit getDDLScriptCommitType()
  {
    String type = getProperty("ddl.commit.type", GenerateDDLCommit.whenNeeded.name());
    try
    {
      return GenerateDDLCommit.valueOf(type);
    }
    catch (Exception ex)
    {
      return GenerateDDLCommit.whenNeeded;
    }
  }

  public boolean ignoreCommitInAutocommitMode()
  {
    return getBoolProperty("transaction.control.ignore.autocommit", false);
  }

  public boolean showFeedbackForIgnoredCommit()
  {
    return getBoolProperty("transaction.control.ignore.feedback", false);
  }

  /**
   * Returns true if object names should never be quoted.
   *
   */
  public boolean neverQuoteObjects()
  {
    return getBoolProperty("neverquote", false);
  }

  /**
   * Returns true if default values in the table definition should be trimmed
   * before displaying them to the user.
   * The default is true
   * <br/>
   * The related property is: <tt>workbench.db.[dbid].trimdefaults</tt>
   */
  public boolean trimDefaults()
  {
    return getBoolProperty("trimdefaults", true);
  }

  /**
   * Returns true if the DataImporter should use setNull() to send NULL values
   * instead of setObject(int, null).
   * <br/>
   * This is also used from within the DataStore when updating data.
   * <br/>
   * The related property is workbench.db.[dbid].import.use.setnull
   *
   * @see DmlStatement#execute(workbench.db.WbConnection, boolean)
   */
  public boolean useSetNull()
  {
    return getBoolProperty("import.use.setnull", false);
  }

  /**
   * Returns true if the DataImporter should use setString() for CLOB values
   * of an unknown type.
   * <br/>
   * The related property is workbench.db.[dbid].import.clob.as.string
   */
  public boolean sendClobsAsStrings()
  {
    return getBoolProperty("import.clob.as.string", false);
  }

  /**
   * Returns true if the DataImporter should use setClob() for CLOB values.
   * <br/>
   * The related property is workbench.db.[dbid].import.clob.as.clob
   */
  public boolean sendClobAsClob()
  {
    return getBoolProperty("import.clob.as.clob", false);
  }

  /**
   * Returns true if the DataImporter should use setBlob() for BLOB values.
   * <br/>
   * The related property is workbench.db.[dbid].import.clob.as.blob
   */
  public boolean sendBlobAsBlob()
  {
    return getBoolProperty("import.blob.as.blob", false);
  }

  /**
   * Returns true if the DataImporter should use setBytes() for BLOB values.
   * <br/>
   * The related property is workbench.db.[dbid].import.clob.as.bytes
   */
  public boolean sendBlobAsBytes()
  {
    return getBoolProperty("import.blob.as.bytes", false);
  }

  /**
   * Some JDBC driver do not allow to run a SQL statement that contains COMMIT or ROLLBACK
   * as a String. They required to use Connection.commit() or Conneciton.rollback() instead.
   * <br/>
   *
   * The related property is: workbench.db.[dbid].usejdbccommit
   *
   * @see TransactionEndCommand#execute(java.lang.String)
   */
  public boolean useJdbcCommit()
  {
    return getBoolProperty("usejdbccommit", false);
  }

  /**
   * Check if string comparisons are case-sensitive by default for the current DBMS.
   * <br/>
   * The related property is: <tt>workbench.db.[dbid].casesensitive</tt>
   *
   * @return true if the current DBMS is case sensitive
   * @see TableSearchPanel#searchData()
   */
  public boolean isStringComparisonCaseSensitive()
  {
    return getBoolProperty("casesensitive", false);
  }

  public boolean getDefaultBeforeNull()
  {
    return getBoolProperty("defaultbeforenull", false);
  }

  public String getCascadeConstraintsVerb(String aType)
  {
    if (aType == null) return null;
    String verb = getProperty("drop." + getKeyValue(aType) + ".cascade", null);
    return verb;
  }

  public boolean useFQConstraintName()
  {
    return getBoolProperty("constraints.use_fqname", false);
  }

  public boolean useCatalogInDML()
  {
    return getBoolProperty("catalog.dml", true);
  }

  public boolean alwaysUseSchema()
  {
    return getBoolProperty("schema.always", false);
  }

  public boolean alwaysUseCatalog()
  {
    return getBoolProperty("catalog.always", false);
  }

  public boolean needsCatalogIfNoCurrent()
  {
    return getBoolProperty("catalog.neededwhenempty", false);
  }

  public String getInsertForImport()
  {
    return getProperty("import.insert", null);
  }

  public Collection<String> getRefCursorTypeNames()
  {
    return CollectionUtil.caseInsensitiveSet(getListProperty("refcursor.typename", null));
  }

  public int getRefCursorDataType()
  {
    return getIntProperty("refcursor.typevalue", Integer.MIN_VALUE);
  }

  public boolean useWbProcedureCall()
  {
    return getBoolProperty("procs.use.wbcall", false);
  }

  public String getCreateIndexSQL()
  {
    String globalDefault = Settings.getInstance().getProperty("workbench.db.sql.create.index", null);
    return getProperty("create.index", globalDefault);
  }

  public String getCreateUniqeConstraintSQL()
  {
    String globalDefault = Settings.getInstance().getProperty("workbench.db.sql.create.uniqueconstraint", null);
    return getProperty("create.uniqueconstraint", globalDefault);
  }

  public String getSelectForFunctionSQL()
  {
    return getProperty("function.select", null);
  }

  public static String getKeyValue(String value)
  {
    if (value == null) return null;
    return value.toLowerCase().trim().replaceAll("\\s+", "_");
  }

  public String getCheckConstraintTemplate()
  {
    return getProperty("sql.constraint.check", null);
  }

  /**
   * Return the complete DDL to drop the given type of DB-Object.
   * <br/>
   * If includeCascade is true and the DBMS supports dropping this type cascaded,
   * then the returned DDL will include the necessary CASCADE keyword
   * <br/>
   * The cascade keyword will only be used when the SQL template (defined in
   * default.properties or workbench.settings actually includes the %cascade%
   * placeholder. If that placeholder is not present in the SQL template,
   * passing true as includeCascade will not have an effect.
   * <br/>
   *
   * @param type the database object type to drop (TABLE, VIEW etc)
   * @return the DDL Statement to drop an object of that type. The placeholder %name% must
   * be replaced with the correct object name
   */
  public String getDropDDL(String type, boolean includeCascade)
  {
    if (StringUtil.isBlank(type)) return null;
    String cascade = getCascadeConstraintsVerb(type);

    String ddl = getProperty("drop." + getKeyValue(type), null);
    if (ddl == null)
    {
      ddl = "DROP " + type.toUpperCase() + " "+ MetaDataSqlManager.NAME_PLACEHOLDER;
      if (cascade != null && includeCascade)
      {
        ddl += " " + cascade;
      }
    }
    else
    {
      if (includeCascade)
      {
        ddl = TemplateHandler.replacePlaceholder(ddl, MetaDataSqlManager.CASCADE_PLACEHOLDER, cascade, true);
      }
      else
      {
        ddl = TemplateHandler.removePlaceholder(ddl, MetaDataSqlManager.CASCADE_PLACEHOLDER, true);
      }
    }
    return ddl.trim();
  }

  public boolean useSpecificNameForDropFunction()
  {
    return getBoolProperty("drop.procedure.use.specificname", false);
  }

  public boolean useSpecificNameForProcedureColumns()
  {
    return getBoolProperty("procedures.use.specificname", true);
  }

  public String getSpecificNameColumn()
  {
    return getProperty("procedures.specificname.colname", "SPECIFIC_NAME");
  }

  public boolean needParametersToDropFunction()
  {
    return getBoolProperty("drop.function.includeparameters", false);
  }

  public boolean includeOutParameterForDropFunction()
  {
    return getBoolProperty("drop.function.include.out.parameters", true);
  }

  /**
   * Returns if the DataImporter should use savepoints for each statement.
   * <br/>
   * The related property is: <tt>workbench.db.[dbid].import.usesavepoint</tt>
   */
  public boolean useSavepointForImport()
  {
    return getBoolProperty("import.usesavepoint", false);
  }

  /**
   * Returns if the DataImporter should use savepoints for the pre and post tables statements.
   * <br/>
   * The related property is: <tt>workbench.db.[dbid].import.tablestmt.usesavepoint</tt>
   */
  public boolean useSavepointForTableStatements()
  {
    return getBoolProperty("import.tablestmt.usesavepoint", false);
  }

  /**
   * Returns if DML statements should be guarded by savepoints.
   * <br/>
   * This affects SQL statements entered by the user and generated when
   * updating a DataStore
   * <br/>
   * The related property is: <tt>workbench.db.[dbid].sql.usesavepoint</tt>
   */
  public boolean useSavePointForDML()
  {
    return getBoolProperty("sql.usesavepoint", false);
  }

  /**
   * Returns if DDL statements should be guarded by savepoints.
   * This affects SQL statements entered by the user and generated when
   * updating a DataStore
   * <br/>
   * The related property is: <tt>workbench.db.[dbid].ddl.usesavepoint</tt>
   */
  public boolean useSavePointForDDL()
  {
    return getBoolProperty("ddl.usesavepoint", false);
  }

  /**
   * Returns the default type for the Blob formatter
   * @return hex, octal, char
   * @see BlobLiteralType
   */
  public String getBlobLiteralType()
  {
    return getProperty("blob.literal.type", "hex");
  }

  public String getBlobLiteralPrefix()
  {
    return getProperty("blob.literal.prefix", null);
  }

  public String getBlobLiteralSuffix()
  {
    return getProperty("blob.literal.suffix", null);
  }

  public boolean getBlobLiteralUpperCase()
  {
    return getBoolProperty("blob.literal.upcase", false);
  }

  public boolean getUseIdioticQuotes()
  {
    return getBoolProperty("bracket.quoting", false);
  }

  public boolean selectStartsTransaction()
  {
    return getBoolProperty("select.startstransaction", false);
  }

  public boolean getUseMySQLShowCreate(String type)
  {
    if (type == null) return false;
    return Settings.getInstance().getBoolProperty("workbench.db.mysql.use.showcreate." + type.trim().toLowerCase(), false);
  }

  /**
   * Returns the string that is used for line comments if the DBMS does not use
   * the ANSI comment character (such as MySQL)
   */
  public String getLineComment()
  {
    return getProperty("linecomment", null);
  }

  public boolean supportsQueryTimeout()
  {
    return getBoolProperty("supportquerytimeout", true);
  }

  public boolean supportsIndexedViews()
  {
    return getBoolProperty("indexedviews", false);
  }

  /**
   * Returns true if {@link DatabaseMetaData#getIndexInfo(String, String, String, boolean, boolean)} is supported.
   */
  public boolean indexInfoSupported()
  {
    return getBoolProperty("metadata.indexinfo.supported", true);
  }

  public boolean supportsGetPrimaryKeys()
  {
    return getBoolProperty("supportgetpk", true);
  }

  public boolean supportsTransactions()
  {
    return getBoolProperty("supports.transactions", true);
  }

  public boolean getStripProcGroupNumber()
  {
    return getBoolProperty("strip.procgroup", false);
  }

  public String getProcGroupDelimiter()
  {
    return getProperty("procversiondelimiter", null);
  }

  public boolean supportsCascadedTruncate()
  {
    String sql = getProperty("sql.truncate.cascade", null);
    return sql != null;
  }

  public String getTruncateCommand(boolean cascade)
  {
    String truncate = getProperty("sql.truncate", null);
    if (cascade)
    {
      truncate = getProperty("sql.truncate.cascade", truncate);
    }
    return truncate;
  }

  public boolean truncateNeedsCommit()
  {
    return getBoolProperty("truncate.commit", false);
  }

  public boolean supportsTruncate()
  {
    return getTruncateCommand(false) != null;
  }

  public boolean supportsGetFunctions()
  {
    return getBoolProperty("metadata.getfunctions.supported", true);
  }

  public boolean isViewType(String type)
  {
    if (type == null) return false;
    return getViewTypes().contains(type);
  }

  public boolean isSynonymType(String type)
  {
    if (type == null) return false;
    Set<String> synTypes = CollectionUtil.caseInsensitiveSet(getListProperty("synonymtypes", "synonym"));
    return synTypes.contains(type.toLowerCase());
  }

  public boolean isMview(String type)
  {
    if (type == null) return false;
    String mviewname = getProperty("mviewname", "materialized view").toLowerCase();
    return type.equalsIgnoreCase(mviewname);
  }

  String mapIndexType(int type)
  {
    switch (type)
    {
      case DatabaseMetaData.tableIndexHashed:
        return "HASH";
      case DatabaseMetaData.tableIndexClustered:
        return "CLUSTERED";
    }
    return IDX_TYPE_NORMAL;
  }

  public IdentifierCase getSchemaNameCase()
  {
    // This allows overriding the default value returned by the JDBC driver
    String nameCase = getProperty("schemaname.case", null);
    return Settings.getInstance().getEnumValue(nameCase, IdentifierCase.unknown);
  }

  public void setObjectNameCase(String oCase)
  {
    Settings.getInstance().setProperty(prefix + "objectname.case", oCase);
  }

  public IdentifierCase getObjectNameCase()
  {
    // This allows overriding the default value returned by the JDBC driver
    String nameCase = getProperty("objectname.case", null);
    return Settings.getInstance().getEnumValue(nameCase, IdentifierCase.unknown);
  }

  /**
   *  Translates the numberic constants of DatabaseMetaData for trigger rules
   *  into text (e.g DatabaseMetaData.importedKeyNoAction --> NO ACTION)
   *
   *  @param code the numeric value for a rule as defined by DatabaseMetaData.importedKeyXXXX constants
   *  @return String
   */
  public String getRuleDisplay(int code)
  {
    StringBuilder key = new StringBuilder(40);
    switch (code)
    {
      case DatabaseMetaData.importedKeyNoAction:
        key.append("workbench.sql.fkrule.noaction");
        break;
      case DatabaseMetaData.importedKeyRestrict:
        key.append("workbench.sql.fkrule.restrict");
        break;
      case DatabaseMetaData.importedKeySetNull:
        key.append("workbench.sql.fkrule.setnull");
        break;
      case DatabaseMetaData.importedKeyCascade:
        key.append("workbench.sql.fkrule.cascade");
        break;
      case DatabaseMetaData.importedKeySetDefault:
        key.append("workbench.sql.fkrule.setdefault");
        break;
      case DatabaseMetaData.importedKeyInitiallyDeferred:
        key.append("workbench.sql.fkrule.initiallydeferred");
        break;
      case DatabaseMetaData.importedKeyInitiallyImmediate:
        key.append("workbench.sql.fkrule.initiallyimmediate");
        break;
      case DatabaseMetaData.importedKeyNotDeferrable:
        key.append("workbench.sql.fkrule.notdeferrable");
        break;
      default:
        key = null;
    }

    if (key != null)
    {
      key.append('.');
      key.append(this.getDbId());
      String display = Settings.getInstance().getProperty(key.toString(), null);
      if (display != null) return display;
    }

    switch (code)
    {
      case DatabaseMetaData.importedKeyNoAction:
        return "NO ACTION";
      case DatabaseMetaData.importedKeyRestrict:
        return "RESTRICT";
      case DatabaseMetaData.importedKeySetNull:
        return "SET NULL";
      case DatabaseMetaData.importedKeyCascade:
        return "CASCADE";
      case DatabaseMetaData.importedKeySetDefault:
        return "SET DEFAULT";
      case DatabaseMetaData.importedKeyInitiallyDeferred:
        return "INITIALLY DEFERRED";
      case DatabaseMetaData.importedKeyInitiallyImmediate:
        return "INITIALLY IMMEDIATE";
      case DatabaseMetaData.importedKeyNotDeferrable:
        return "NOT DEFERRABLE";
      default:
        return StringUtil.EMPTY_STRING;
    }
  }

  public boolean useSetCatalog()
  {
    return getBoolProperty("usesetcatalog", true);
  }

  public boolean isNotDeferrable(String deferrable)
  {
    if (StringUtil.isEmpty(deferrable)) return true;
    return deferrable.equals(getRuleDisplay(DatabaseMetaData.importedKeyNotDeferrable));
  }

  /**
   * Retrieve the list of datatypes that should be ignored when mapping datatypes
   * from one DBMS to another (e.g. when creating a table on the fly using DataCopier
   * <br/>
   * The names in that list must match the names returned by DatabaseMetaData.getTypeInfo()
   */
  public List<String> getDataTypesToIgnore()
  {
    String types = Settings.getInstance().getProperty("workbench.ignoretypes." + getDbId(), null);
    List<String> ignored = StringUtil.stringToList(types, ",", true, true);
    return ignored;
  }

  /**
   * Return the query to retrieve the current catalog
   * <br/>
   * The related property is: <tt>workbench.db.[dbid].currentcatalog.query
   *
   * @return null if no query is configured
   */
  public String getQueryForCurrentCatalog()
  {
    return getProperty("currentcatalog.query", null);
  }

  /**
   * Returns if the RowDataConverter should format instances of java.util.Date using
   * the supplied timestamp format (to preserve the time information).
   * <br/>
   * The related property is: <tt>workbench.db.[dbid].export.convert.date2ts
   * <br/>
   * This property defaults to true for Oracle.
   *
   * @return true if java.util.Date should be formated with the Timestamp format
   * @see RowDataConverter#getValueAsFormattedString(workbench.storage.RowData, int)
   */
  public boolean getConvertDateInExport()
  {
    return getBoolProperty("export.convert.date2ts", false);
  }

  public boolean useColumnListInExport()
  {
    return getBoolProperty("export.select.use.columns", true);
  }

  public boolean exportXMLAsClob()
  {
    return getBoolProperty("export.xml.clob", true);
  }

  public boolean getUseStreamsForBlobExport()
  {
    return getBoolProperty("export.blob.use.streams", true);
  }

  public boolean getUseStreamsForClobExport()
  {
    return getBoolProperty("export.clob.use.streams", false);
  }

  public boolean needsExactClobLength()
  {
    return getBoolProperty("exactcloblength", false);
  }

  /**
   * Check if the source of views (in the DbExplorer) should be formatted after
   * it is retrieved from the server.
   *
   * The related property is: <tt>workbench.db.[dbid].source.view.doformat
   *
   * @return true if the source should be formatted (using the SQLFormatter)
   *
   * @see workbench.db.ViewReader#getViewSource(workbench.db.TableIdentifier)
   */
  public boolean getFormatViewSource()
  {
    return getBoolProperty("source.view.doformat", false);
  }

  /**
   * Return the DDL to drop a single column from a table.
   * The statement must contain placeholders for table and column names.
   *
   * The related property is: <tt>workbench.db.[dbid].drop.column
   *
   * @return null if no statement is configured.
   * @see workbench.db.MetaDataSqlManager#TABLE_NAME_PLACEHOLDER
   * @see workbench.db.MetaDataSqlManager#COLUMN_NAME_PLACEHOLDER
   */
  public String getDropSingleColumnSql()
  {
    return getProperty("drop.column", null);
  }

  /**
   * Return the DDL to drop multiple columns from a table.
   * The statement must contain placeholders for the table name and the column list.
   *
   * The related property is: <tt>workbench.db.[dbid].drop.column.multi
   *
   * @return null if no statement is configured.
   * @see workbench.db.MetaDataSqlManager#TABLE_NAME_PLACEHOLDER
   * @see workbench.db.MetaDataSqlManager#COLUMN_LIST_PLACEHOLDER
   */
  public String getDropMultipleColumnSql()
  {
    return getProperty("drop.column.multi", null);
  }


  /**
   * Return the DDL to add a single column to a table.
   *
   * The related property is: <tt>workbench.db.[dbid].add.column
   *
   * @return null if no statement is configured.
   * @see workbench.db.MetaDataSqlManager#TABLE_NAME_PLACEHOLDER
   * @see workbench.db.MetaDataSqlManager#COLUMN_NAME_PLACEHOLDER
   */
  public String getAddColumnSql()
  {
    return getProperty("add.column", null);
  }

  public boolean useQuotedColumnsForComments()
  {
    return getBoolProperty("comments.column.quote", true);
  }

  public boolean supportsMultiRowInsert()
  {
    return getBoolProperty("dml.insert.multirow.supported", true);
  }

  public boolean ignoreIndexColumnWithOrdinalZero()
  {
    return getBoolProperty("index.ignore.ordinal_zero", true);
  }

  public final boolean includeSystemTablesInSelectable()
  {
    return getBoolProperty("systemtables.selectable", false);
  }

  public boolean removeNewLinesInSQL()
  {
    return getBoolProperty("removenewlines", false);
  }

  public boolean canDropInTransaction(String type)
  {
    if (ddlNeedsCommit())
    {
      return getBoolProperty("drop." + type + ".in.transaction", true);
    }
    return true;
  }

  public boolean canDropType(String type)
  {
    if (StringUtil.isEmpty(type)) return false;
    if (type.equalsIgnoreCase("column"))
    {
      return getDropSingleColumnSql() != null;
    }
    return true;
  }

  public void setDataTypeExpression(String cleanType, String expr)
  {
    Settings.getInstance().setProperty(prefix + "selectexpression." + cleanType, expr);
  }

  /**
   * Retrieves an expression to be used inside a select statement for the given datatype.
   *
   * The DbExplorer will use this expression instead of the "plain" column
   * name to retrieve data for this data type. This can be used to make
   * data readable in the DbExplorer for data types that are not natively supported
   * by the JDBC driver.
   *
   * The expression must contain the placeholder <tt>${column}</tt> for the column name.
   *
   * @param dbmsType
   * @return null if nothing is configured
   *
   * @see workbench.db.TableSelectBuilder#getSelectForTable(workbench.db.TableIdentifier)
   * @see workbench.db.TableSelectBuilder#COLUMN_PLACEHOLDER
   */
  public String getDataTypeSelectExpression(String dbmsType)
  {
    if (dbmsType == null) return null;
    return getProperty("selectexpression." + dbmsType.toLowerCase(), null);
  }

  /**
   * Return an expression to be used in a PreparedStatement as the value placeholder.
   *
   * For e.g. Postgres to be able to update an <tt>XML</tt> column, the expression
   * <tt>cast(? as xml)</tt> is required.
   *
   * @param dbmsType  the DBMS data type of the column
   * @return a DBMS specific expression or null if nothing was defined (or the datatype is null)
   *
   * @see #isDmlExpressionDefined(java.lang.String)
   * @see DmlExpressionBuilder
   */
  public String getDmlExpressionValue(String dbmsType, DmlExpressionType expressionType)
  {
    if (dbmsType == null) return null;
    String cleanType = SqlUtil.getBaseTypeName(dbmsType);
    String expression = getProperty("dmlexpression." + cleanType.toLowerCase(), null);
    if (expressionType != DmlExpressionType.Any)
    {
      expression = getProperty("dmlexpression." + cleanType.toLowerCase() + "." + expressionType.toString().toLowerCase(), expression);
    }
    return expression;
  }

  public boolean isDmlExpressionDefined(String dbmsType, DmlExpressionType expressionType)
  {
    return getDmlExpressionValue(dbmsType, expressionType) != null;
  }

  /**
   * Return a customized mapping of JDBC types to native datatypes.
   * @see TypeMapper
   */
  public String getJDBCTypeMapping()
  {
    return getProperty("typemap", null);
  }

  public boolean cleanupTypeMappingNames()
  {
    return getBoolProperty("typemap.cleanup", false);
  }

  /**
   * Returns if setObject() should be used with the target JDBC datatype or
   * without
   * <br/>e.g. <tt>setObject(1, "42", Types.OTHER)</tt> which will define
   * the datatype, or using <tt>setObject(1, "42")</tt> which will pass the
   * conversion and type detection to the driver.
   * <br/>
   * Some drivers to not work properly when dealing with non JDBC Types here
   * (e.g. Postgres and UUID columns)
   */
  public SetObjectStrategy getUseTypeWithSetObject()
  {
    String value = getProperty("import.setobject.usetype", "false");
    if ("true".equals(value))
    {
      return SetObjectStrategy.Always;
    }
    if ("false".equals(value))
    {
      return SetObjectStrategy.Never;
    }
    try
    {
      return SetObjectStrategy.valueOf(value);
    }
    catch (Throwable th)
    {
      return SetObjectStrategy.Never;
    }
  }

  public boolean getFunctionsAlwaysNeedSchema()
  {
    return getBoolProperty("functioncall.schema.required", false);
  }

  public boolean getTableFunctionNeedsTableKeyword()
  {
    return getBoolProperty("srf.call.needs.table", false);
  }

  public boolean getTableFunctionAlwaysNeedsParens()
  {
    return getBoolProperty("srf.call.needs.parentheses", true);
  }

  public boolean getIncludeTableFunctionsForTableCompletion()
  {
    return getBoolProperty("completion.tables.include.functions", true);
  }

  public boolean getRetrieveProcParmsForAutoCompletion()
  {
    return getBoolProperty("completion.procs.showparms", true);
  }

  public Map<Integer, Integer> getTypeMappingForPreparedStatement()
  {
    Map<Integer, Integer> result = new HashMap<>();
    try
    {
      Field[] fields = SqlUtil.getSqlTypeFields();
      for (Field field : fields)
      {
        int type = field.getInt(null);
        int mappedType = getIntProperty("types.pstmt.send." + type, type);
        result.put(type, mappedType);
      }
    }
    catch (Exception ex)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not retrieve type mapping", ex);
    }
    return result;
  }

  /**
   * Controls if the index source should be generated together with the table source.
   *
   * If customize table source retrieval is configured, this controls if that
   * query includes the create index statements.
   *
   * Otherwise it controls if SQL Workbench/J should generate the index source
   * when generating the table source. If false, the generated CREATE TABLE
   * will not contain any index DDL.
   *
   * @see #getUseCustomizedCreateTableRetrieval()
   * @return true if index source should be generated even if the table source is retrieved by a customized statement
   */
  public boolean getGenerateTableIndexSource()
  {
    if (isTableSourceRetrievalCustomized())
    {
      boolean included = getBoolProperty("retrieve.create.table.index_included", false);
      return !included;
    }
    return getBoolProperty("generate.tablesource.include.indexes", true);
  }

  /**
   * If a customized table source retrieval is enabled, this method
   * controls if the foreign key source should be generated by SQL Workbench/J or not
   *
   * @see #getUseCustomizedCreateTableRetrieval()
   * @return true if index source should be generated even if the table source is retrieved by a customized statement
   */
  public boolean getGenerateTableFKSource()
  {
    if (isTableSourceRetrievalCustomized())
    {
      boolean included = getBoolProperty("retrieve.create.table.fk_included", false);
      return !included;
    }
    return getBoolProperty("generate.tablesource.include.fk", true);
  }

  public GenerateOwnerType getGenerateTableOwner()
  {
    String value = getProperty("generate.tablesource.include.owner", null);
    return Settings.getInstance().getEnumValue(value, GenerateOwnerType.whenNeeded);
  }

  /**
   * If customized SQL is configured to retrieve the source of a table, this setting identifies if table and column
   * comments are included in the generated SQL.
   *
   * <br/>
   * If no SQL is configured, this method always returns true. Otherwise the value of the config property <br/>
   * <tt>workbench.db.[dbid].retrieve.create.table.comments_included</tt> is checked. If that is true to indicate that
   * comments <i>are</i> returned by the custom SQL, this method returns false.
   * <br/>
   *
   * @see #getUseCustomizedCreateTableRetrieval()
   *
   * @return true if table comments (including columns) should be generated
   */
  public boolean getGenerateTableComments()
  {
    if (isTableSourceRetrievalCustomized())
    {
      boolean included = getBoolProperty("retrieve.create.table.comments_included", false);
      return !included;
    }
    boolean defaultFlag = Settings.getInstance().getBoolProperty("workbench.db.generate.tablesource.generate.comments", true);
    return getBoolProperty("generate.tablesource.include.comments", defaultFlag);
  }


  public boolean getUseInlineColumnComments()
  {
    return getBoolProperty("column.comment.inline", false);
  }

  public String getInlineCommentKeyword()
  {
    return getProperty("column.comment.inline.keyword", "COMMENT");
  }

  public boolean getUseInlineTableComments()
  {
    return getBoolProperty("table.comment.inline", false);
  }

  public String getInlineTableCommentKeyword()
  {
    return getProperty("table.comment.inline.keyword", null);
  }

  /**
   * Returns true if the table grants should be generated for the table source.
   *
   * If table source retrieval is customized, the property retrieve.create.table.grants_included decides
   * if the grants need to be retrieve seperately.
   *
   * @see workbench.db.TableSourceBuilder#getTableSource(workbench.db.TableIdentifier, java.util.List)
   * @see #isTableSourceRetrievalCustomized()
   * @see DbExplorerSettings#getGenerateTableGrants()
   *
   * @return true if table grants should be generated even if the table source is retrieved by a customized statement
   */
  public boolean getGenerateTableGrants()
  {
    if (isTableSourceRetrievalCustomized())
    {
      boolean included = getBoolProperty("retrieve.create.table.grants_included", false);
      return !included;
    }
    boolean defaultFlag = DbExplorerSettings.getGenerateTableGrants();
    return getBoolProperty("generate.tablesource.include.grants", defaultFlag);
  }

  public boolean needsPKIndexForPKDefinition()
  {
    return getBoolProperty("pksource.needs.pkindex", false);
  }

  public boolean needTableDefinitionForTableSource()
  {
    boolean useCustomQuery = isTableSourceRetrievalCustomized();
    return getBoolProperty("retrieve.create.table.needs_definition", useCustomQuery == false);
  }

  public boolean isTableSourceRetrievalCustomized()
  {
    return isObjectSourceRetrievalCustomized("table");
  }

  public boolean isViewSourceRetrievalCustomized()
  {
    return isObjectSourceRetrievalCustomized("view");
  }

  public boolean isObjectSourceRetrievalCustomized(String type)
  {
    if (DBID.Oracle.isDB(getDbId()))
    {
      if (OracleUtils.getUseOracleDBMSMeta(OracleUtils.DbmsMetadataTypes.table)) return true;
    }
    return (getUseCustomizedCreateObjectRetrieval(type) && StringUtil.isNotEmpty(getRetrieveObjectSourceSql(type)));
  }

  /**
   * Checks if a customized SQL query to retrieve the source of object types is enabled.
   *
   * The use of a custom SQL to retrieve the source of an object type can be enabled/disabled
   * independently of the actual SQL configuration. This allows to keep the configured
   * SQL in the .settings file but still turn its usage on or off.
   *
   *
   * @see #getRetrieveObjectSourceSql(String)
   *
   */
  public boolean getUseCustomizedCreateObjectRetrieval(String type)
  {
    if (type == null) return false;
    return getBoolProperty("retrieve.create." + type.trim().toLowerCase() + ".enabled", true);
  }

  /**
   * Returns the SQL that retrieves the CREATE SQL for a given table directly from the DBMS.
   * In the returned SQL, the placeholders %table_name%, %schema% and %catalog% must be
   * replaced with the real values.
   *
   * If the table source is not returned in the first column of the result set,
   * getRetrieveTableSourceCol() will indicate the column index that contains the
   * actual source.
   *
   * @return null if not configured, a SQL to be run to retrieve a CREATE TABLE otherwise
   * @see #getRetrieveTableSourceCol()
   * @see #getRetrieveTableSourceNeedsQuotes()
   * @see #getGenerateTableComments()
   * @see #getGenerateTableGrants()
   * @see #getGenerateTableIndexSource()
   * @see #getUseCustomizedCreateObjectRetrieval(String)
   */
  public String getRetrieveObjectSourceSql(String type)
  {
    if (!getUseCustomizedCreateObjectRetrieval(type)) return null;
    type = StringUtil.coalesce(cleanUpObjectType(type), "table");
    return getProperty("retrieve.create." + type + ".query", null);
  }

  /**
   * Returns the result set column in which the table source from getRetrieveTableSourceSql()
   * is returned (if configured)
   *
   * @return the approriate result set column index if configured, 1 otherwise
   * @see #getRetrieveTableSourceSql()
   */
  public int getRetrieveTableSourceCol(String type)
  {
    type = StringUtil.coalesce(cleanUpObjectType(type), "table");
    return getIntProperty("retrieve.create." + type + ".sourcecol", 1);
  }

  /**
   * Returns the result set column in which the index source from getRetrieveIndexSourceSql()
   * is returned (if configured)
   *
   * @return the approriate result set column index if configured, 1 otherwise
   * @see #getRetrieveIndexSourceSql()
   */
  public int getRetrieveIndexSourceCol()
  {
    return getIntProperty("retrieve.create.index.sourcecol", 1);
  }

  protected boolean getUseCustomizedCreateIndexRetrieval()
  {
    return getBoolProperty("retrieve.create.index.enabled", true);
  }

  public String getRetrieveIndexSourceSql()
  {
    if (!getUseCustomizedCreateIndexRetrieval()) return null;
    return getProperty("retrieve.create.index.query", null);
  }

  /**
   * Returns true if the placeholders for retrieving the index source need to be checked
   * for quoting. This is necessary if the SQL is a SELECT statement, but might not
   * be necessary if the SQL (defined by getRetrieveIndexSourceSql()) is a procedure call
   *
   * @return true if quotes might be needed.
   * @see #getRetrieveIndexSourceSql()
   */
  public boolean getRetrieveIndexSourceNeedsQuotes()
  {
    return getBoolProperty("retrieve.create.index.checkquotes", true);
  }

  /**
   * Return all configured "CREATE TABLE" types.
   *
   * @see #getCreateTableTemplate(java.lang.String)
   */
  public static List<CreateTableTypeDefinition> getCreateTableTypes()
  {
    return getCreateTableTypes(null);
  }

  /**
   * Return configured "CREATE TABLE" types for the specified DBID.
   *
   * @see #getCreateTableTemplate(java.lang.String)
   */
  public static List<CreateTableTypeDefinition> getCreateTableTypes(String dbid)
  {
    List<String> types = Settings.getInstance().getKeysWithPrefix(".create.table.");
    List<CreateTableTypeDefinition> result = new ArrayList<>(types.size());
    for (String type : types)
    {
      // don't use the internal DDL templates
      if (type.contains(".create.table.sql.")) continue;
      if (type.contains(".retrieve.create.table.")) continue;
      if (type.endsWith(".commit")) continue;

      CreateTableTypeDefinition createType = new CreateTableTypeDefinition(type);
      if (dbid == null || dbid.equals(createType.getDbId()))
      {
        result.add(createType);
      }
    }
    Collections.sort(result);
    return result;
  }

  /**
   * Checks if a CREATE TABLE for the specific type should be committed.
   *
   * @param createType
   * @return true if this type can/should be committed
   * @see #getCreateTableTemplate(java.lang.String)
   */
  public boolean commitCreateTable(String createType)
  {
    if (createType == null) return true;
    String key = prefix + ".create.table."+ createType.toLowerCase() + ".commit";
    return Settings.getInstance().getBoolProperty(key, true);
  }

  public static final String DEFAULT_CREATE_TABLE_TEMPLATE =
    "CREATE TABLE " + MetaDataSqlManager.FQ_TABLE_NAME_PLACEHOLDER +
    "\n(\n" +
    MetaDataSqlManager.COLUMN_LIST_PLACEHOLDER +
    "\n)";

  /**
   * The SQL template that is used to create a table of the specified type
   *
   * @return the temp table keyword or null
   */
  public String getCreateTableTemplate(String type)
  {
    if (StringUtil.isBlank(type)) type = DEFAULT_CREATE_TABLE_TYPE;

    return getProperty("create.table." + getKeyValue(type), DEFAULT_CREATE_TABLE_TEMPLATE);
  }

  public Set<String> getViewTypes()
  {
    List<String> types = Settings.getInstance().getListProperty("workbench.db.viewtypes", false, "VIEW");
    List<String> dbTypes = getListProperty("additional.viewtypes", null);
    Set<String> allTypes = CollectionUtil.caseInsensitiveSet();
    allTypes.addAll(types);
    allTypes.addAll(dbTypes);
    return allTypes;
  }

  /**
   * For testing purposes.
   */
  public void setCreateTableTemplate(String type, String template)
  {
    Settings.getInstance().setProperty(prefix + "create.table." + type.toLowerCase(), template);
  }

  /**
   * Returns true if the placeholders for retrieving the table source need to be checked
   * for quoting. This is necessary if the SQL is a SELECT statement, but might not
   * be necessary if the SQL (defined by getRetrieveTableSourceSql()) is a procedure call
   *
   * @return true if quotes might be needed.
   * @see #getRetrieveTableSourceSql()
   */
  public boolean getRetrieveObjectSourceNeedsQuotes(String type)
  {
    type = StringUtil.coalesce(cleanUpObjectType(type), "TABLE");
    return getBoolProperty("retrieve.create." + type + ".checkquotes", true);
  }

  public boolean applyFormatForNativeTableSource()
  {
    return applyFormatForNativeSource("table");
  }

  public boolean applyFormatForNativeSource(String type)
  {
    return getBoolProperty("retrieve.create." + type + ".reformat", false);
  }

  public boolean isSearchable(String dbmsType)
  {
    if (StringUtil.isBlank(dbmsType)) return false;
    List<String> types = getListProperty("datatypes.searchable", null, true);
    return types.contains(dbmsType.toLowerCase());
  }

  public String getAlterColumnDataTypeSql()
  {
    return getProperty("alter.column.type", null);
  }

  public String getRenameColumnSql()
  {
    return getProperty("alter.column.rename", null);
  }

  public String getAlterColumnSetNotNull()
  {
    return getProperty("alter.column.notnull.set", null);
  }

  public String getAlterColumnDropNotNull()
  {
    return getProperty("alter.column.notnull.drop", null);
  }

  /**
   * The SQL to alter a column's default. If this returns null, getSetColumnDefault()
   * and getDropColumnDefaultSql() should also be checked because some DBMS only
   * allow setting or removing the column default
   */
  public String getAlterColumnDefaultSql()
  {
    return getProperty("alter.column.default", null);
  }

  /**
   * The SQL to set a column's default.
   */
  public String getSetColumnDefaultSql()
  {
    return getProperty("alter.column.default.set", null);
  }

  public String getDropColumnDefaultSql()
  {
    return getProperty("alter.column.default.drop", null);
  }

  /**
   * Returns the ALTER ... template to rename the given object type
   * (e.g. TABLE, VIEW)
   *
   * @param type
   * @return null if no template was configured for this dbms
   */
  public String getRenameObjectSql(String type)
  {
    if (StringUtil.isBlank(type)) return null;
    return getProperty("alter." + getKeyValue(type) + ".rename", null);
  }

  public Collection<String> getExportTypesNeedingQuotes()
  {
    return getListProperty("export.quoting.needed");
  }

  /**
   * Returns the ALTER ... template to change the schema for a given object type
   *
   * @param type
   * @return null if no template was configured for this dbms
   */
  public String getChangeSchemaSql(String type)
  {
    if (StringUtil.isBlank(type)) return null;
    return getProperty("alter." + getKeyValue(type) + ".change.schema", null);
  }

  /**
   * Returns the ALTER ... template to change the schema for a given object type
   *
   * @param type
   * @return null if no template was configured for this dbms
   */
  public String getChangeCatalogSql(String type)
  {
    if (StringUtil.isBlank(type)) return null;
    return getProperty("alter." + getKeyValue(type) + ".change.catalog", null);
  }

  /**
   * Returns the SQL to drop a primary key of a database object
   * @param type the type of the object. e.g. table, materialized view
   */
  public String getDropPrimaryKeySql(String type)
  {
    if (StringUtil.isBlank(type)) return null;
    return getProperty("alter." + getKeyValue(type) + ".drop.pk", null);
  }

  /**
   * Returns the SQL to drop a constraint from a data object
   * @param type the type of the object. e.g. table, materialized view
   */
  public String getDropConstraint(String type)
  {
    if (StringUtil.isBlank(type)) return null;
    return getProperty("alter." + getKeyValue(type) + ".drop.constraint", null);
  }

  public Set<String> getTypesSupportingFKS()
  {
    List<String> dbTypes = getListProperty("types.with.foreignkeys", "TABLE");
    return CollectionUtil.caseInsensitiveSet(dbTypes);
  }

  /**
   * Returns the SQL to drop a foreign key constraint from a data object
   *
   * @param type the type of the object. e.g. table, materialized view
   */
  public String getDropFKConstraint(String type)
  {
    if (StringUtil.isBlank(type)) return null;
    String dropConstraint = getDropConstraint(type);
    return getProperty("alter." + getKeyValue(type) + ".drop.fk_constraint", dropConstraint);
  }

  public String getAddTableConstraint()
  {
    String globalDefault = Settings.getInstance().getProperty("workbench.db.sql.alter.table.add.constraint", null);
    return getProperty("alter.table.add.constraint", globalDefault);
  }

  /**
   * Returns the SQL to add a primary key to an object
   *
   * @param type the type of the object. e.g. table, materialized view
   */
  public String getAddPK(String type)
  {
    return getAddPK(type, false);
  }

  public String getAddPK(String type, boolean checkDefault)
  {
    if (StringUtil.isBlank(type)) return null;
    String sql = getProperty("alter." + getKeyValue(type) + ".add.pk", null);
    if (StringUtil.isEmpty(sql) && checkDefault)
    {
      sql = Settings.getInstance().getProperty("workbench.db.sql.alter." + getKeyValue(type) + ".add.pk", null);
    }
    return sql;
  }

  /**
   * Cleanup an object type name to be useable as part of a property key.
   */
  public String cleanUpObjectType(String type)
  {
    if (type == null) return "";
    return type.toLowerCase().trim().replace(' ', '_');
  }

  /**
   * Checks if the current DBMS supports comments for the given DB object type
   * @param objectType the type to be checked (e.g. TABLE, COLUMN)
   * @return true if the DBMS supports comments for this type
   */
  public boolean columnCommentAllowed(String objectType)
  {
    if (StringUtil.isBlank(objectType)) return false;
    String type = cleanUpObjectType(objectType);
    List<String> types = getListProperty("columncomment.types", "table");
    return types.contains(type);
  }

  /**
   * Setting to control the display of the auto-generated SELECT rules for views.
   *
   * @return true if the auto-generated SELECT rules should be excluded
   */
  public static boolean getExcludePostgresDefaultRules()
  {
    return Settings.getInstance().getBoolProperty("workbench.db.postgresql.exclude.defaultselectrule", true);
  }

  public boolean useXmlAPI()
  {
    return getBoolProperty("use.xmlapi", false);
  }

  public boolean isClobType(String dbmsType)
  {
    if (dbmsType == null) return false;
    return getBoolProperty("isclob." + dbmsType, false);
  }

  public boolean pkIndexHasTableName()
  {
    return getBoolProperty("pkconstraint.is_table_name", false);
  }

  public boolean createTriggerNeedsAlternateDelimiter()
  {
    return getBoolProperty("alternate.delim.create.trigger", true);
  }

  public boolean getSearchAllSchemas()
  {
    return getBoolProperty("search.all.schemas", true);
  }

  public boolean getSearchPreferCurrentSchema()
  {
    return getBoolProperty("search.prefer.current_schema", true);
  }

  public String getInlinePKKeyword()
  {
    return getProperty("sql.pk.inline", "PRIMARY KEY");
  }

  /**
   * Returns a flag if the driver returns "ready-made" expressions for the DEFAULT value of a column.
   */
  public boolean returnsValidDefaultExpressions()
  {
    return getBoolProperty("defaultvalue.isexpression", true);
  }

  /**
   * Returns true if the JDBC driver returns the correct ResultSetMetadata only by preparing a statement.
   *
   * If this is false, the ResultSetMetadata is only returned after actually retrieving data through the
   * statement.
   *
   */
  public boolean usePreparedStatementForQueryInfo()
  {
    return getBoolProperty("queryinfo.preparedstatement", false);
  }

  public void setUsePreparedStatementForQueryInfo(boolean flag)
  {
    Settings.getInstance().setProperty(prefix + "queryinfo.preparedstatement", flag);
  }

  public Set<String> getIgnoreSchemaForCompletionPaste()
  {
    return CollectionUtil.caseInsensitiveSet(getListProperty("completion.paste.ignore_schema", null));
  }

  public Set<String> getIgnoreCatalogForCompletionPaste()
  {
    return CollectionUtil.caseInsensitiveSet(getListProperty("completion.paste.ignore_catalog", null));
  }

  public boolean alwaysUseSchemaForCompletion()
  {
    return getBoolProperty("completion.always_use.schema", false);
  }

  public boolean alwaysUseCatalogForCompletion()
  {
    return getBoolProperty("completion.always_use.catalog", false);
  }

  /**
   * Returns the sqlstate for a unique (primary) key violation error for the DBMS.
   *
   * This value can be compared against SQLException.getSQLState() to test for such an error.
   *
   * Some DBMS only return exact information on pk violations through the sqlstate, some through
   * the error code.
   *
   * @return the sql state that is defined for this DBMS or null if none is defined
   * @see #getUniqueKeyViolationErrorCode()
   */
  public String getUniqueKeyViolationErrorState()
  {
    return getProperty("errorstate.unique", null);
  }

  /**
   * Returns the error code for a unique (primary) key violation error for the DBMS.
   * This value can be compared against SQLException.getErrorCode() to test for such an error.
   *
   * Some DBMS only return exact information on pk violations through the sqlstate, some through
   * the error code.
   *
   * @return the numeric value of the error code or -1 if none is defined
   * @see #getUniqueKeyViolationErrorState()
   */
  public int getUniqueKeyViolationErrorCode()
  {
    return getIntProperty("errorcode.unique", -1);
  }

  public boolean supportsResultMetaGetTable()
  {
    return getBoolProperty("resultmetadata.gettablename.supported", false);
  }

  public void setSupportsResultMetaGetTable(boolean flag)
  {
    setPropertyTemporary("resultmetadata.gettablename.supported", false);
  }

  /**
   * Checks if this DBMS supports triggers on views.
   * It is better to call TriggerReader#supportsTriggersOnViews() instead as that also
   * checks for the current DBMS version.
   *
   * @see TriggerReader#supportsTriggersOnViews()
   */
  public boolean supportsTriggersOnViews()
  {
    return getBoolProperty("view.trigger.supported", false);
  }

  /**
   * Checks if this DBMS supports triggers on views.
   *
   */
  public boolean supportsTriggers()
  {
    return getBoolProperty("trigger.supported", true);
  }

  public boolean changeCatalogToRetrieveSchemas()
  {
    return getBoolProperty("schema.retrieve.change.catalog", false);
  }

  public boolean supportsCatalogForGetSchemas()
  {
    return getBoolProperty("getschemas.per.catalog.supported", false);
  }

  public void setSupportsCatalogForGetSchemas(boolean flag)
  {
    setProperty("getschemas.per.catalog.supported", false);
  }

  public boolean supportsCatalogs()
  {
    return getBoolProperty("catalogs.supported", true);
  }

  public boolean supportsSchemas()
  {
    return getBoolProperty("schemas.supported", true);
  }

  public boolean schemaIsCatalog()
  {
    return getBoolProperty("schema.is.catalog", false);
  }

  /**
   * Return true if the driver for this DBMS is known to support PreparedStatement.getParameterMetaData()
   */
  public boolean supportsParameterMetaData()
  {
    return getBoolProperty("parameter.metadata.supported", true);
  }

  /**
   * Return true if the driver for this DBMS is known to support CallableStatement.getParameterMetaData()
   */
  public boolean supportsParameterMetaDataForCallableStatement()
  {
    return getBoolProperty("parameter.metadata.callablestatement.supported", true);
  }

  public boolean doEscapeSearchString()
  {
    return getBoolProperty("escape.searchstrings", true);
  }

  public String getSearchStringEscape()
  {
    return getProperty("searchstringescape", null);
  }

  public boolean fixFKRetrieval()
  {
    return getBoolProperty("fixfkretrieval", true);
  }

  public List<String> getObjectTypeNameMap()
  {
    return getListProperty("typenames.mapping", null, false, false);
  }

  public Set<String> getTableTypeSynonyms()
  {
    return CollectionUtil.caseInsensitiveSet(getListProperty("table.type.alternate.names"));
  }

  public String getIdentifierQuoteString()
  {
    String propName = "identifier.quote";
    String quote = getProperty("quote.escape", null);
    if (quote != null)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Deprecated property \"" + prefix + ".quote.escape\" used. Renaming to: " + prefix + propName);
      Settings.getInstance().removeProperty(prefix + "quote.escape");
      Settings.getInstance().setProperty(prefix + propName, quote);
    }
    else
    {
      quote = getProperty(propName, null);
    }
    return quote;
  }

  public boolean clearCacheOnReconnect()
  {
    boolean global = Settings.getInstance().getBoolProperty("workbench.db.objectcache.disconnect.clear", false);
    return getBoolProperty("objectcache.disconnect.clear", global);
  }

  public boolean populateCacheInBackground()
  {
    boolean global = Settings.getInstance().getBoolProperty("workbench.db.objectcache.retrieve.background", true);
    return getBoolProperty("objectcache.retrieve.background", global);
  }

  public boolean useCacheForObjectInfo()
  {
    boolean global = Settings.getInstance().getBoolProperty("workbench.db.objectinfo.usecache", false);
    return getBoolProperty("objectinfo.usecache", global);
  }

  public boolean objectInfoWithFK()
  {
    boolean global = Settings.getInstance().getBoolProperty("workbench.db.objectinfo.includefk", false);
    return getBoolProperty("objectinfo.includefk", global);
  }

  public boolean objectInfoWithDependencies()
  {
    boolean global = Settings.getInstance().getBoolProperty("workbench.db.objectinfo.includedeps", false);
    return getBoolProperty("objectinfo.includedeps", global);
  }

  public boolean useSavePointForTransactionCheck()
  {
    return getBoolProperty("opentransaction.usesavepoint", false);
  }

  public String checkOpenTransactionsQuery()
  {
    return getProperty("opentransaction.query", null);
  }

  public String getCatalogSeparator()
  {
    return getProperty("separator.catalog", null);
  }

  public boolean useCatalogSeparatorForSchema()
  {
    return getBoolProperty("separator.catalog.forschema", false);
  }

  public String getSchemaSeparator()
  {
    return getProperty("separator.schema", ".");
  }

  public boolean createInlinePKConstraints()
  {
    return getBoolProperty("pk.inline", false);
  }

  public boolean createInlineFKConstraints()
  {
    return getBoolProperty("fk.inline", false);
  }

  public boolean supportsFkOption(String action, String type)
  {
    String toUse = type.toLowerCase().replace(' ', '_');
    return getBoolProperty("fk." + action.toLowerCase() + "." + toUse +".supported", true);
  }

  public boolean supportsMetaDataWildcards()
  {
    return getBoolProperty("metadata.retrieval.wildcards", true);
  }

  public boolean supportsMetaDataSchemaWildcards()
  {
    return supportsMetaDataWildcards("schema");
  }

  public boolean supportsMetaDataCatalogWildcards()
  {
    return supportsMetaDataWildcards("catalog");
  }

  public boolean supportsMetaDataNullPattern()
  {
    return getBoolProperty("metadata.pattern.tablename.null.supported", true);
  }

  private boolean supportsMetaDataWildcards(String type)
  {
    return getBoolProperty("metadata.retrieval.wildcards." + type, supportsMetaDataWildcards());
  }

  public int getLockTimoutForSqlServer()
  {
    return getIntProperty("dbexplorer.locktimeout", 2500);
  }

  public EndReadOnlyTrans endTransactionAfterConnect()
  {
    String value = getProperty("afterconnect.finishtrans", null);
    if (StringUtil.isBlank(value)) return EndReadOnlyTrans.never;

    // Old behaviour where afterconnect.finishtrans was a true/false property
    if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false"))
    {
      boolean flag = Boolean.parseBoolean(value);
      return flag ? EndReadOnlyTrans.rollback : EndReadOnlyTrans.never;
    }
    return Settings.getInstance().getEnumValue(value, EndReadOnlyTrans.never);
  }

  public String getTableSelectTemplate(String keyname)
  {
    String globalDefault = Settings.getInstance().getProperty("workbench.db.sql." + keyname + ".select", null);
    return getProperty(keyname + ".select", globalDefault);
  }

  public String getSwitchCatalogStatement()
  {
    return getProperty("switchcatalog.sql", "USE " + TableSourceBuilder.CATALOG_PLACEHOLDER);
  }

  public boolean getSwitchCatalogInExplorer()
  {
    return getBoolProperty("dbexplorer.switchcatalog", DbExplorerSettings.getSwitchCatalogInExplorer());
  }

  public boolean fixSqlServerAutoincrement()
  {
    return Settings.getInstance().getBoolProperty("workbench.db.microsoft_sql_server.autoincrement.fix", true);
  }

  public String getLowerFunctionTemplate()
  {
    return getProperty("sql.function.lower", null);
  }

  public String getDisabledConstraintKeyword()
  {
    return getProperty("sql.constraint.disabled", null);
  }

  public String getNoValidateConstraintKeyword()
  {
    return getProperty("sql.constraint.notvalid", null);
  }

  public boolean getUseGenericExecuteForSelect()
  {
    boolean global = Settings.getInstance().getUseGenericExecuteForSelect();
    return getBoolProperty("select.executegeneric", global);
  }

  public boolean useCleanSQLForPreparedStatements()
  {
    return getBoolProperty("preparedstaments.cleansql", false);
  }

  public boolean supportsAutomaticFkIndexes()
  {
    return getBoolProperty("fk.index.automatic", false);
  }

  public boolean useReadUncommittedForDbExplorer()
  {
    return getBoolProperty("dbexplorer.use.read_uncommitted", false);
  }

  public boolean useFullSearchPathForCompletion()
  {
    return getBoolProperty("completion.full.searchpath", false);
  }

  public boolean useCurrentNamespaceForCompletion()
  {
    return getBoolProperty("completion.current.namespace", Settings.getInstance().getAutoCompletionUseCurrentNameSpace());
  }

  public boolean cleanupTypeList()
  {
    return getBoolProperty("metadata.cleanup.types", false);
  }

  public boolean useMaxRows(String verb)
  {
    if (alwaysUseMaxRows) return true;
    if (verb == null) return false;
    return useMaxRowsVerbs.contains(verb);
  }

  public Set<String> verbsWithoutUpdateCount()
  {
    return Collections.unmodifiableSet(noUpdateCountVerbs);
  }

  private void readUpdatingCommands()
  {
    String global = Settings.getInstance().getProperty("workbench.db.updatingcommands", null);
    updatingCommands.addAll(StringUtil.stringToList(global, ",", true, true));
    String dbCommands = getProperty("updatingcommands", null);
    updatingCommands.addAll(StringUtil.stringToList(dbCommands, ",", true, true));
  }

  private void readMaxRowVerbs()
  {
    String global = Settings.getInstance().getProperty("workbench.db.maxrows.verbs", null);
    useMaxRowsVerbs.addAll(StringUtil.stringToList(global, ",", true, true));
    String dbCommands = getProperty("maxrows.verbs", null);

    List<String> dbVerbs = StringUtil.stringToList(dbCommands, ",", true, true);
    for (String verb : dbVerbs)
    {
      if (StringUtil.isEmpty(verb)) continue;
      if (verb.startsWith("-"))
      {
        useMaxRowsVerbs.remove(verb.substring(1));
      }
      else
      {
        useMaxRowsVerbs.add(verb);
      }
    }
    alwaysUseMaxRows = useMaxRowsVerbs.contains("*");
  }

  private void readNoUpdateCountVerbs()
  {
    List<String> verbs = getListProperty("no.updatecount.default");
    noUpdateCountVerbs.addAll(verbs);

    List<String> userVerbs = getListProperty("no.updatecount");
    for (String verb : userVerbs)
    {
      if (StringUtil.isEmpty(verb)) continue;

      if (verb.charAt(0) == '-')
      {
        noUpdateCountVerbs.remove(verb.substring(1));
      }
      else
      {
        noUpdateCountVerbs.add(verb);
      }
    }
  }

  public boolean disableEscapesForDDL()
  {
    return getBoolProperty("ddl.disable.escapeprocessing", true);
  }

  public boolean hideOracleIdentitySequences()
  {
    return getBoolProperty("sequence.identity.hide", false);
  }

  public boolean useColumnNamesForProcedureResultColumns()
  {
    return getBoolProperty("procedurecolumns.retrieval.use.columnnames", true);
  }

  public boolean useColumnNameForMetadata()
  {
    return getBoolProperty("metadata.retrieval.columnnames", true);
  }

  // Currently only used for Postgres
  public boolean returnAccessibleProceduresOnly()
  {
    return getBoolProperty("procedurelist.only.accessible", true);
  }

  public boolean returnAccessibleTablesOnly()
  {
    return getBoolProperty("tablelist.only.accessible", false);
  }

  /**
   * Return true if IndexReader should check the table an index belongs to.
   *
   * This is needed to workaround a bug in the Informix driver that
   * returns indexes for tables other than just the specified one.
   */
  public boolean checkIndexTable()
  {
    return getBoolProperty("metadata.index.check.table", false);
  }

  public String getDDLIfNoExistsOption(String type)
  {
    if (getUseConditionalDDL())
    {
      return getProperty("ddl.create." + cleanUpObjectType(type) + ".ifnotexists", null);
    }
    return null;
  }

  public boolean getUseConditionalDDL()
  {
    return getBoolProperty("ddl.use.conditional", true);
  }

  public boolean getBoolProperty(String prop, boolean defaultValue)
  {
    String value = getProperty(prop, null);
    if (value == null) return defaultValue;
    return Boolean.parseBoolean(value);
  }

  public int getIntProperty(String prop, int defaultValue)
  {
    String value = getProperty(prop, null);
    if (value == null) return defaultValue;
    try
    {
      return Integer.parseInt(value);
    }
    catch (Throwable th)
    {
      return defaultValue;
    }
  }

  public boolean generateColumnListInViews()
  {
    boolean all = DbExplorerSettings.getGenerateColumnListInViews();
    return getBoolProperty("create.view.columnlist", all);
  }

  public String getErrorColumnInfoRegex()
  {
    return getProperty("errorinfo.regex.column", null);
  }

  public String getErrorLineInfoRegex()
  {
    return getProperty("errorinfo.regex.line", null);
  }

  public String getErrorPosInfoRegex()
  {
    return getProperty("errorinfo.regex.position", null);
  }

  public boolean getErrorPosIsZeroBased()
  {
    return getBoolProperty("errorinfo.zerobased", true);
  }

  public boolean getErrorPosIncludesLeadingComments()
  {
    return getBoolProperty("errorinfo.leading.comment.included", false);
  }

  public boolean getIncludeErrorCodeInMessage()
  {
    boolean globalDefault = Settings.getInstance().getBoolProperty("workbench.errorinfo.include.sqlcode", false);
    return getBoolProperty("errorinfo.include.sqlcode", globalDefault);
  }

  public boolean getCheckResultSetReadOnlyCols()
  {
    return getBoolProperty("resultset.columns.check.readonly", true);
  }

  public boolean getRetrieveGeneratedKeys()
  {
    return getBoolProperty("insert.retrieve.keys", true);
  }

  public Collection<String> getIgnoreCompletionSchemas()
  {
    return getListProperty("completion.ignore.schema", null);
  }

  public Collection<String> getIgnoreCompletionCatalogs()
  {
    return getListProperty("completion.ignore.catalog", null);
  }

  public Set<String> getGrantorsToIgnore()
  {
    List<String> names = getListProperty("ignore.grantor");
    Set<String> result = CollectionUtil.caseInsensitiveSet();
    result.addAll(names);
    return result;
  }

  public Set<String> getGranteesToIgnore()
  {
    List<String> names = getListProperty("ignore.grantee");
    Set<String> result = CollectionUtil.caseInsensitiveSet();
    result.addAll(names);
    return result;
  }

  public boolean supportsSetSchema()
  {
    return getBoolProperty("supports.schema_change", false);
  }

  public Set<String> getAdditionalTransactionCommands()
  {
    List<String> commands = getListProperty("transactional.commands", null);
    return CollectionUtil.caseInsensitiveSet(commands);
  }

  public Set<String> getNeverEndTransactionCommands()
  {
    List<String> commands = getListProperty("transaction.readonly.end.never");
    return CollectionUtil.caseInsensitiveSet(commands);
  }

  public EndReadOnlyTrans getAutoCloseReadOnlyTransactions()
  {
    String defaultSetting = Settings.getInstance().getProperty("workbench.sql.transaction.readonly.end", EndReadOnlyTrans.never.name());
    String value = getProperty("transaction.readonly.end", defaultSetting);
    return Settings.getInstance().getEnumValue(value, EndReadOnlyTrans.never);
  }

  public Set<Integer> getInformationalWarningCodes()
  {
    List<String> ids = getListProperty("warning.ignore.codes");
    if (ids.isEmpty()) return Collections.emptySet();
    Set<Integer> result = new HashSet<>(ids.size());
    for (String id :ids)
    {
      result.add(StringUtil.getIntValue(id, Integer.MIN_VALUE));
    }
    return result;
  }

  public Set<String> getInformationalWarningStates()
  {
    List<String> ids = getListProperty("warning.ignore.sqlstate");
    if (ids.isEmpty()) return Collections.emptySet();
    return new HashSet<>(ids);
  }

  public List<String> getSchemasToAdd()
  {
    return getListProperty("schemas.additional", null);
  }

  public boolean checkUniqueIndexesForPK()
  {
    boolean global = Settings.getInstance().getBoolProperty("workbench.db.pk.retrieval.checkunique", true);
    return getBoolProperty("pk.retrieval.checkunique", global);
  }

  public boolean getUpdateTableCheckPkOnly()
  {
    String propName = "updatetable.check.pkonly";
    boolean global = Settings.getInstance().getBoolProperty("workbench.db." + propName, false);
    return getBoolProperty(propName, global);
  }

  public boolean useCompletionCacheForUpdateTableCheck()
  {
    String propName = "updatetable.check.use.cache";
    boolean global = Settings.getInstance().getBoolProperty("workbench.db." + propName, false);
    return getBoolProperty(propName, global);
  }

  public String getLimitClause()
  {
    return getVersionedString("select.limit", null);
  }

  public boolean fixStupidMySQLZeroDate()
  {
    return getBoolProperty("timestamp.ignore.read.errors", false);
  }

  public boolean addWarningsOnError()
  {
    return getBoolProperty("error.include.warning", true);
  }

  public boolean useNumberFormatterForLiterals()
  {
    return getBoolProperty("sql.literal.number.use.formatter", false);
  }

  public boolean supportsFunctionOverloading()
  {
    return getBoolProperty("function.overloading.supported", false);
  }

  public boolean showProcedureParameters()
  {
    return getBoolProperty("procedurelist.showparameters", false);
  }

  public boolean retrieveWarningsForEachResult()
  {
    return getBoolProperty("warning.retrieve.eachresult", false);
  }

  public boolean supportsReplace(String type)
  {
    return getBoolProperty("ddl" + getKeyValue(type) + ".replace.supported", false);
  }

  public boolean useGetFunctions()
  {
    return getBoolProperty("procedurereader.use.getfunctions", false);
  }

  public boolean syncConnectionReadOnlyState()
  {
    return getBoolProperty("change.connection.readonly.state", true);
  }

  public String getSetReadOnlySQL()
  {
    return getProperty("change.connection.readonly.sql", null);
  }

  public String getSetReadWriteSQL()
  {
    return getProperty("change.connection.readwrite.sql", null);
  }

  public boolean enableDynamicDelimiter()
  {
    return getBoolProperty("delimiter.dynamic.enabled", true);
  }

  public Set<String> getTypesRequiringAlternateDelimiter()
  {
    Set<String> types = CollectionUtil.caseInsensitiveSet();
    String typeList = getProperty("types.alternatedelimiter", "procedure,function,trigger");
    types.addAll(StringUtil.stringToList(typeList,",", true, true));
    return types;
  }

  public boolean trimObjectNames(String type)
  {
    boolean globalDefault = getBoolProperty("trim.names", true);
    return getBoolProperty(type + ".trim.names", globalDefault);
  }

  public String getUnboundedVarcharType()
  {
    return getProperty("varchar.type.unlimited", null);
  }

  public int getMaxVarcharLength()
  {
    return getIntProperty("varchar.max.length", 32762);
  }

  public boolean quoteIndexColumnNames()
  {
    return getBoolProperty("index.columns.quote", true);
  }

  public boolean databaseProductVersionReturnsRealVersion()
  {
    return getBoolProperty("databaseproductversion.realversion", false);
  }

  public boolean supportsEmbeddedResults()
  {
    return getBoolProperty("supports.embedded.results", false);
  }

  public boolean refcursorIsEmbeddedResult()
  {
    return getBoolProperty("embedded.results.refcursor", false);
  }

  public boolean supportsUseDBStatement()
  {
    return getBoolProperty("sql.supports.usedb", false);
  }

  public void setSupportsGetMoreResults(boolean flag)
  {
    setProperty("supports.getmoreresults", true);
  }

  public boolean supportsGetMoreResults()
  {
    return getBoolProperty("supports.getmoreresults", true);
  }

  public boolean ignoreSQLErrorsForGetMoreResults()
  {
    return getBoolProperty("getmoreresults.ignore.sqlerrors", false);
  }

  public Set<String> getScriptOnlyObjects()
  {
    List<String> types = getListProperty("scriptonly.objects", "");
    return CollectionUtil.caseInsensitiveSet(types);
  }

  public Set<String> getGlobalObjectTypes()
  {
    List<String> types = getListProperty("objects.global", "");
    return CollectionUtil.caseInsensitiveSet(types);
  }

  public Set<String> getCatalogLevelTypes()
  {
    List<String> types = getListProperty("objects.catalog.global", "");
    return CollectionUtil.caseInsensitiveSet(types);
  }

  public boolean enableDatabaseSwitcher()
  {
    return getBoolProperty("gui.enable.dbswitcher", false);
  }

  public boolean autoDisableDriverBuffering()
  {
    return getBoolProperty("export.driver.buffering.disable", false);
  }

  public int getFetchSizeForDisabledBuffering()
  {
    return getIntProperty("export.driver.disabled.buffering.fetchsize", 100);
  }

  public boolean treatSchemaChangeErrorAsWarning()
  {
    return getBoolProperty("setschema.exception.is.warning", false);
  }

  public boolean useSQLiteDataReader()
  {
    return getBoolProperty("use.specific.rowdatareader", true);
  }

  public String getCastToString(String dbmsType)
  {
    String globalCast = Settings.getInstance().getProperty("workbench.db.cast.as.string", null);
    String cast = getProperty("cast.as.string", globalCast);
    if (StringUtil.isNotBlank(dbmsType))
    {
      cast = getProperty("cast." + dbmsType.trim().toLowerCase() + ".as.string", cast);
    }
    return cast;
  }

  public boolean showSuccessMessageForVerb(String verb)
  {
    if (verb == null) return false;
    if (!Settings.getInstance().getBoolProperty("workbench.db.sql.show.success", true)) return false;
    if (!getBoolProperty("show.success", true)) return false;

    String key = "show.success." + verb.trim().toLowerCase();
    boolean globalVerb = Settings.getInstance().getBoolProperty("workbench.db." + key, true);
    return getBoolProperty(key, globalVerb);
  }

  public Collection<String> getTypesNeedingQuotes()
  {
    // built in types
    List<String> types = getListProperty("literals.quoted.types.default", null, false, true);

    // User provided types
    types.addAll(getListProperty("literals.quoted.types", null, false, true));

    return types;
  }

  public boolean structLiteralNeedsQuotes()
  {
    return getBoolProperty("literals.struct.quotes", false);
  }

  public String getTableSearchLIKEOperator()
  {
    return getProperty("tablesearch.like.operator", null);
  }

  public boolean isTableSearchLIKEOperatorCaseSensitive()
  {
    return getBoolProperty("tablesearch.like.operator.case.sensitive", true);
  }

  public boolean validateSetSchema()
  {
    return getBoolProperty("setschema.validate", false);
  }

  /**
   * Returns a map of method names to property names to
   * retrieve additional information about a connection
   *
   * @return a map where the key is a method to be called on the Connection instance.
   */
  public Map<String, String> getDynamicInfoPropertiesMapping()
  {
    Map<String, String> result = new HashMap<>();
    List<String> defs = getListProperty("connectioninfo.properties.methods");
    if (CollectionUtil.isEmpty(defs)) return result;

    for (String def : defs)
    {
      String[] elements = def.split(";");
      if (elements.length == 2 && StringUtil.allNonEmpty(elements))
      {
        result.put(elements[0].trim(), elements[1].trim());
      }
    }
    return result;
  }

  public boolean isGetSchemaImplemented()
  {
    return getBoolProperty("getschema.implemented", true);
  }

  public boolean isGetCatalogImplemented()
  {
    return getBoolProperty("getcatalog.implemented", true);
  }
}
