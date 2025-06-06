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
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.GuiSettings;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.derby.DerbyTypeReader;
import workbench.db.duckdb.DuckDbMacroReader;
import workbench.db.firebird.FirebirdDomainReader;
import workbench.db.firebird.FirebirdTypeResolver;
import workbench.db.greenplum.GreenplumObjectListCleaner;
import workbench.db.greenplum.GreenplumObjectListEnhancer;
import workbench.db.greenplum.GreenplumUtil;
import workbench.db.h2database.H2ConstantReader;
import workbench.db.h2database.H2DomainReader;
import workbench.db.hana.HanaTableDefinitionReader;
import workbench.db.hsqldb.HsqlDataTypeResolver;
import workbench.db.hsqldb.HsqlTypeReader;
import workbench.db.ibm.DB2TempTableReader;
import workbench.db.ibm.DB2TypeReader;
import workbench.db.ibm.Db2iObjectListEnhancer;
import workbench.db.ibm.Db2iVariableReader;
import workbench.db.ibm.InformixDataTypeResolver;
import workbench.db.mssql.SqlServerDataTypeResolver;
import workbench.db.mssql.SqlServerObjectListEnhancer;
import workbench.db.mssql.SqlServerRuleReader;
import workbench.db.mssql.SqlServerSchemaInfoReader;
import workbench.db.mssql.SqlServerTableDefinitionReader;
import workbench.db.mssql.SqlServerTypeReader;
import workbench.db.mssql.SqlServerUtil;
import workbench.db.mysql.MySQLTableCommentReader;
import workbench.db.nuodb.NuoDBDomainReader;
import workbench.db.objectcache.Namespace;
import workbench.db.oracle.DbmsOutput;
import workbench.db.oracle.OracleDataTypeResolver;
import workbench.db.oracle.OracleDomainReader;
import workbench.db.oracle.OracleObjectListEnhancer;
import workbench.db.oracle.OracleTableDefinitionReader;
import workbench.db.oracle.OracleTypeReader;
import workbench.db.oracle.OracleUtils;
import workbench.db.postgres.PostgresCollationReader;
import workbench.db.postgres.PostgresDataTypeResolver;
import workbench.db.postgres.PostgresDomainReader;
import workbench.db.postgres.PostgresEnumReader;
import workbench.db.postgres.PostgresEventTriggerReader;
import workbench.db.postgres.PostgresExtensionReader;
import workbench.db.postgres.PostgresForeignServerReader;
import workbench.db.postgres.PostgresObjectListCleaner;
import workbench.db.postgres.PostgresPublicationReader;
import workbench.db.postgres.PostgresRangeTypeReader;
import workbench.db.postgres.PostgresRuleReader;
import workbench.db.postgres.PostgresSubscriptionReader;
import workbench.db.postgres.PostgresTypeReader;
import workbench.db.postgres.PostgresUtil;
import workbench.db.progress.OpenEdgeObjectListEnhancer;
import workbench.db.progress.OpenEdgeSchemaInformationReader;
import workbench.db.sqlite.SQLiteDataTypeResolver;
import workbench.db.vertica.VerticaTableDefinitionReader;
import workbench.db.vertica.VerticaTableReader;

import workbench.storage.DataStore;
import workbench.storage.DatastoreTransposer;

import workbench.sql.syntax.SqlKeywordHelper;

import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;
import workbench.util.VersionNumber;

import static workbench.db.ObjectListDataStore.*;

/**
 * Retrieve meta data information from the database.
 * This class returns more information than the generic JDBC DatabaseMetadata.
 *
 * @author Thomas Kellerer
 */
public class DbMetadata
  implements QuoteHandler
{
  public static final String MVIEW_NAME = "MATERIALIZED VIEW";

  private final String[] EMPTY_STRING_ARRAY = new String[]{};

  private final Object readerLock = new Object();

  private String schemaTerm;
  private String catalogTerm;
  private String productName;
  private String dbId;

  private MetaDataSqlManager metaSqlMgr;
  private DatabaseMetaData metaData;
  private WbConnection dbConnection;

  private ObjectListEnhancer objectListEnhancer;
  private TableDefinitionReader definitionReader;
  private DataTypeResolver dataTypeResolver;
  private SchemaInformationReader schemaInfoReader;
  private final CatalogInformationReader catalogInfoReader;
  private IndexReader indexReader;
  private List<ObjectListExtender> extenders = new ArrayList<>();
  private List<ObjectListAppender> appenders = new ArrayList<>();
  private List<ObjectListCleaner> cleaners = new ArrayList<>(1);

  private DbmsOutput oraOutput;

  private boolean isExcel;
  private boolean isAccess;

  private String quoteCharacter;
  private final Set<String> keywords = CollectionUtil.caseInsensitiveSet();
  private final Set<String> reservedWords = CollectionUtil.caseInsensitiveSet();

  private String baseTableTypeName;
  private String mviewTypeName;

  private Set<String> tableTypesList;
  private String[] tableTypesArray;
  private String[] selectableTypes;
  private Set<String> schemasToIgnore;
  private Set<String> catalogsToIgnore;

  private DbSettings dbSettings;
  private final char catalogSeparator;
  private SelectIntoVerifier selectIntoVerifier;
  private Set<String> objectTypesFromDriver;
  private int maxTableNameLength;

  private boolean supportsGetSchema = true;
  private Pattern identifierPattern;

  public DbMetadata(WbConnection aConnection)
    throws SQLException
  {
    this.dbConnection = aConnection;
    this.metaData = aConnection.getSqlConnection().getMetaData();

    String connectionId = dbConnection.getId();
    final CallerInfo ci = new CallerInfo(){};

    try
    {
      this.schemaTerm = this.metaData.getSchemaTerm();
      LogMgr.logDebug(ci, connectionId + ": Schema term: " + schemaTerm);
    }
    catch (Throwable e)
    {
      LogMgr.logWarning(ci, connectionId + ": Could not retrieve Schema term: " + e.getMessage());
      this.schemaTerm = "Schema";
    }

    try
    {
      this.catalogTerm = this.metaData.getCatalogTerm();
      LogMgr.logDebug(ci, connectionId + ": Catalog term: " + catalogTerm);
    }
    catch (Throwable e)
    {
      LogMgr.logWarning(ci, connectionId + ": Could not retrieve Catalog term: " + e.getMessage());
      this.catalogTerm = "Catalog";
    }

    // Some JDBC drivers do not return a value for getCatalogTerm() or getSchemaTerm()
    // and don't throw an Exception. This is to ensure that our getCatalogTerm() will
    // always return something usable.
    if (StringUtil.isBlank(this.schemaTerm)) this.schemaTerm = "Schema";
    if (StringUtil.isBlank(this.catalogTerm)) this.catalogTerm = "Catalog";

    try
    {
      this.productName = this.metaData.getDatabaseProductName();
    }
    catch (Throwable e)
    {
      this.productName = JdbcUtils.getDBMSName(aConnection.getProfile().getUrl());
      LogMgr.logWarning(ci, connectionId + ": Could not retrieve database product name. Using name from JDBC URL: " + productName, e);
    }

    String productLower = this.productName.toLowerCase();
    VersionNumber dbVersion = null;

    if (productLower.contains("greenplum") || (productLower.contains("postgres") && PostgresUtil.isGreenplum(dbConnection.getSqlConnection())))
    {
      this.dbId = DBID.Greenplum.getId();

      this.dataTypeResolver = new PostgresDataTypeResolver();
      extenders.add(new PostgresRuleReader());
      extenders.add(new PostgresDomainReader());
      extenders.add(new PostgresTypeReader());

      objectListEnhancer = new GreenplumObjectListEnhancer();
      dbVersion = GreenplumUtil.getDatabaseVersion(dbConnection);
      if (JdbcUtils.hasMinimumServerVersion(dbVersion, "5.0"))
      {
        extenders.add(new PostgresEnumReader());
        extenders.add(new PostgresExtensionReader());
      }
      cleaners.add(new GreenplumObjectListCleaner());
    }
    else if (productLower.contains("redshift") || (productLower.contains("postgres") && PostgresUtil.isRedshift(dbConnection)))
    {
      PostgresDataTypeResolver resolver = new PostgresDataTypeResolver();
      resolver.setFixTimestampTZ(JdbcUtils.hasMiniumDriverVersion(dbConnection, "1.0"));
      this.dataTypeResolver = resolver;

      mviewTypeName = MVIEW_NAME;
      PostgresTypeReader typeReader = new PostgresTypeReader();
      objectListEnhancer = typeReader;
      extenders.add(typeReader);
      this.dbId = DBID.Redshift.getId();
    }
    else if (productLower.contains("postgres")
             && Settings.getInstance().checkCockroachDBConnection()
             && PostgresUtil.isCockroachDB(dbConnection.getSqlConnection()))
    {
      this.dbId = DBID.CockroachDB.getId();
    }
    else if (productLower.contains("postgres"))
    {
      if (PostgresUtil.isYugabyte(aConnection.getSqlConnection()))
      {
        this.dbId = DBID.Yugabyte.getId();
      }
      else
      {
        this.dbId = DBID.Postgres.getId();
      }

      PostgresDataTypeResolver resolver = new PostgresDataTypeResolver();
      resolver.setFixTimestampTZ(JdbcUtils.hasMiniumDriverVersion(dbConnection, "42.0"));
      this.dataTypeResolver = resolver;

      mviewTypeName = MVIEW_NAME;
      extenders.add(new PostgresDomainReader());
      if (JdbcUtils.hasMinimumServerVersion(dbConnection, "8.3"))
      {
        extenders.add(new PostgresEnumReader());
      }
      if (JdbcUtils.hasMinimumServerVersion(dbConnection, "8.4"))
      {
        extenders.add(new PostgresForeignServerReader());
      }
      if (JdbcUtils.hasMinimumServerVersion(dbConnection, "9.1"))
      {
        extenders.add(new PostgresExtensionReader());
        extenders.add(new PostgresEventTriggerReader());
        extenders.add(new PostgresCollationReader());
      }
      if (JdbcUtils.hasMinimumServerVersion(dbConnection, "10"))
      {
        extenders.add(new PostgresPublicationReader());
        extenders.add(new PostgresSubscriptionReader());
      }
      extenders.add(new PostgresRuleReader());
      PostgresTypeReader typeReader = new PostgresTypeReader();
      objectListEnhancer = typeReader;
      extenders.add(typeReader);
      if (JdbcUtils.hasMinimumServerVersion(dbConnection, "9.2") && PostgresRangeTypeReader.retrieveRangeTypes())
      {
        extenders.add(new PostgresRangeTypeReader());
      }
      if (JdbcUtils.hasMinimumServerVersion(dbConnection, "10.0"))
      {
        cleaners.add(new PostgresObjectListCleaner());
      }
    }
    else if (productLower.contains("oracle") && !productLower.contains("lite ordbms"))
    {
      dbId = DBID.Oracle.getId();
      mviewTypeName = MVIEW_NAME;
      dataTypeResolver = new OracleDataTypeResolver(aConnection);
      definitionReader = new OracleTableDefinitionReader(aConnection, (OracleDataTypeResolver)dataTypeResolver);
      extenders.add(new OracleTypeReader());
      if (JdbcUtils.hasMinimumServerVersion(dbConnection, "23.0"))
      {
        extenders.add(new OracleDomainReader());
      }
      objectListEnhancer = new OracleObjectListEnhancer(); // to cleanup MVIEW type information
    }
    else if (productLower.contains("hsql") && !productLower.startsWith("ucanaccess"))
    {
      this.dbId = DBID.HSQLDB.getId();
      this.dataTypeResolver = new HsqlDataTypeResolver();
      if (JdbcUtils.hasMinimumServerVersion(dbConnection, "2.2"))
      {
        extenders.add(new HsqlTypeReader());
      }
    }
    else if (productLower.contains("firebird"))
    {
      this.dbId = DBID.Firebird.getId();
      dataTypeResolver = new FirebirdTypeResolver();
      extenders.add(new FirebirdDomainReader());
    }
    else if (productLower.contains("microsoft") && productLower.contains("sql server"))
    {
      // checking for "microsoft" is important, because apparently the jTDS driver
      // confusingly identifies a Sybase server as "SQL Server"
      dbId = DBID.SQL_Server.getId();

      if (SqlServerTypeReader.versionSupportsTypes(dbConnection))
      {
        extenders.add(new SqlServerTypeReader());
      }

      if (SqlServerUtil.isSqlServer2000(dbConnection))
      {
        extenders.add(new SqlServerRuleReader());
      }
      definitionReader = new SqlServerTableDefinitionReader(dbConnection);
      objectListEnhancer = new SqlServerObjectListEnhancer();
      dataTypeResolver = new SqlServerDataTypeResolver();
      if (Settings.getInstance().getBoolProperty("workbench.db.microsoft_sql_server.use.schemareader", true)
          && SqlServerUtil.isSqlServer2005(dbConnection))
      {
        // SqlServerSchemaInfoReader will cache the user's default schema
        schemaInfoReader = new SqlServerSchemaInfoReader(dbConnection);
      }
    }
    else if (productLower.contains("db2"))
    {
      dbId = DBID.generateId(productName);
      // Generated columns are not available on the host version...
      if (dbId.equals(DBID.DB2_LUW.getId()))
      {
        extenders.add(new DB2TypeReader());
        appenders.add(new DB2TempTableReader());
      }

      if (dbId.equals(DBID.DB2_ISERIES.getId()))
      {
        objectListEnhancer = new Db2iObjectListEnhancer();
        extenders.add(new Db2iVariableReader());
      }
    }
    else if (productLower.contains("mysql"))
    {
      this.objectListEnhancer = new MySQLTableCommentReader();
      this.dbId = DBID.MySQL.getId();
      String versionString = dbConnection.getDatabaseProductVersion();
      if (versionString.toLowerCase().contains("mariadb"))
      {
        this.dbId = DBID.MariaDB.getId();
      }
    }
    else if (productLower.contains("mariadb"))
    {
      this.objectListEnhancer = new MySQLTableCommentReader();
      this.dbId = DBID.MariaDB.getId();
    }
    else if (productLower.contains("derby"))
    {
      dbId = DBID.Derby.getId();
      if (JdbcUtils.hasMinimumServerVersion(dbConnection, "10.6"))
      {
        extenders.add(new DerbyTypeReader());
      }
    }
    else if (productLower.equals("duckdb"))
    {
      dbId = DBID.DuckDB.getId();
      extenders.add(new DuckDbMacroReader());
    }
    else if (productLower.equals("nuodb"))
    {
      dbId = DBID.NuoDB.getId();
      extenders.add(new NuoDBDomainReader());
    }
    else if (productLower.contains("sqlite"))
    {
      dbId = DBID.SQLite.getId();
      dataTypeResolver = new SQLiteDataTypeResolver();
    }
    else if (productLower.contains("excel"))
    {
      isExcel = true;
    }
    else if (productLower.contains("access"))
    {
      isAccess = true;
    }
    else if (productLower.equals("h2"))
    {
      dbId = DBID.H2.getId();
      extenders.add(new H2DomainReader());
      extenders.add(new H2ConstantReader());
    }
    else if (productLower.contains("informix") || Settings.getInstance().getInformixProductNames().contains(productName))
    {
      // use the same DBID regardless of the product name reported by the server
      dbId = DBID.Informix.getId();
      dataTypeResolver = new InformixDataTypeResolver();
    }
    else if (productLower.equals("vertica database"))
    {
      dbId = DBID.Vertica.getId();
      definitionReader = new VerticaTableDefinitionReader(aConnection);
      extenders.add(new VerticaTableReader());
    }
    else if (productLower.contains("openedge"))
    {
      // Progress returns a different name through JDBC and ODBC
      dbId = DBID.OPENEDGE.getId();
      objectListEnhancer = new OpenEdgeObjectListEnhancer();

      if (Settings.getInstance().getBoolProperty("workbench.db.openedge.check.defaultschema", true))
      {
        schemaInfoReader = new OpenEdgeSchemaInformationReader(dbConnection);
      }
    }
    else if (productLower.equals("hdb"))
    {
      dbId = DBID.HANA.getId();
      definitionReader = new HanaTableDefinitionReader(aConnection);
    }

    if (this.dataTypeResolver == null)
    {
      this.dataTypeResolver = new DefaultDataTypeResolver();
    }

    if (definitionReader == null)
    {
      definitionReader = new JdbcTableDefinitionReader(dbConnection);
    }

    try
    {
      this.quoteCharacter = this.metaData.getIdentifierQuoteString();
      LogMgr.logDebug(ci, connectionId + ": Identifier quote character obtained from driver: " + quoteCharacter);
    }
    catch (Throwable e)
    {
      this.quoteCharacter = null;
      LogMgr.logError(ci, connectionId + ": Error when retrieving identifier quote character", e);
    }

    if (this.dbId == null)
    {
      this.dbId = DBID.generateId(productName);
    }

    LogMgr.logInfo(ci, connectionId + ": Using DBID=" + this.dbId);

    if (dbVersion == null)
    {
      dbVersion = aConnection.getDatabaseVersion(this.dbId);
    }
    this.dbSettings = new DbSettings(dbId, dbVersion.getMajorVersion(), dbVersion.getMinorVersion());

    String quote = dbSettings.getIdentifierQuoteString();
    if (quote != null)
    {
      if ("<none>".equals(quote))
      {
        this.quoteCharacter = "";
      }
      else
      {
        this.quoteCharacter = quote;
      }
      LogMgr.logDebug(ci, connectionId + ": Using configured identifier quote character: >" + quoteCharacter + "<");
    }

    if (StringUtil.isBlank(quoteCharacter))
    {
      this.quoteCharacter = "\"";
    }
    LogMgr.logInfo(ci, connectionId + ": Using identifier quote character: " + quoteCharacter);
    LogMgr.logInfo(ci, connectionId + ": Using search string escape character: " + getSearchStringEscape());

    baseTableTypeName = dbSettings.getProperty("basetype.table", "TABLE");

    Collection<String> ttypes = new ArrayList<>(retrieveTableTypes());
    Iterator<String> titr = ttypes.iterator();
    while (titr.hasNext())
    {
      String type = titr.next().toLowerCase();
      // we don't want regular views in this list
      if (type.contains("view") && !type.equalsIgnoreCase("materialized view"))
      {
        titr.remove();
      }
    }

    tableTypesList = CollectionUtil.caseInsensitiveSet(ttypes);

    // make sure synonyms are not treated as tables
    tableTypesList.remove(SynonymReader.SYN_TYPE_NAME);

    tableTypesArray = StringUtil.toArray(tableTypesList, true);

    // The selectableTypes array will be used to fill the completion cache.
    // In that case we do not want system tables included
    // (which is done for the objectsWithData as that drives the "Data" tab in the DbExplorer)
    Set<String> types = getObjectsWithData();

    if (!dbSettings.includeSystemTablesInSelectable())
    {
      Iterator<String> itr = types.iterator();
      while (itr.hasNext())
      {
        String s = itr.next();
        if (s.toUpperCase().contains("SYSTEM"))
        {
          itr.remove();
        }
      }
    }

    selectableTypes = StringUtil.toArray(types, true);
    selectIntoVerifier = new SelectIntoVerifier(dbId);

    String sep = getDbSettings().getCatalogSeparator();
    if (sep == null)
    {
      try
      {
        sep = metaData.getCatalogSeparator();
      }
      catch (Exception e)
      {
        LogMgr.logError(ci, connectionId + ": Could not retrieve catalog separator", e);
      }
    }

    if (StringUtil.isBlank(sep))
    {
      catalogSeparator = '.';
    }
    else
    {
      catalogSeparator = sep.charAt(0);
    }

    LogMgr.logInfo(ci, connectionId + ": Using catalog separator: " + catalogSeparator);

    try
    {
      this.maxTableNameLength = metaData.getMaxTableNameLength();
    }
    catch (Throwable sql)
    {
      LogMgr.logWarning(ci, connectionId + ": Driver does not support getMaxTableNameLength()", sql);
      this.maxTableNameLength = 0;
    }

    initIdentifierPattern();

    if (schemaInfoReader == null)
    {
      this.schemaInfoReader = new GenericSchemaInfoReader(this.dbConnection, dbSettings);
    }

    this.catalogInfoReader = new GenericCatalogInformationReader(this.dbConnection, dbSettings);
    this.supportsGetSchema = dbSettings.isGetSchemaImplemented();
    this.dataTypeResolver.configure(dbSettings);
    logConfiguredTableTypes();
  }

  private void logConfiguredTableTypes()
  {
    Collection<String> configuredTypes = dbSettings.getListProperty("tabletypes");

    if (configuredTypes.size() > 0)
    {
      LogMgr.logDebug(new CallerInfo(){}, getConnId() + ": Using configured table types: " + configuredTypes);
    }
  }

  private void initIdentifierPattern()
  {
    String pattern = getDbSettings().getProperty("identifier.pattern", null);
    if (pattern != null)
    {
      try
      {
        identifierPattern = Pattern.compile(pattern);
        LogMgr.logInfo(new CallerInfo(){}, getConnId() + ": Using regular expression for valid identifiers: " + pattern);
      }
      catch (Exception ex)
      {
        LogMgr.logWarning(new CallerInfo(){}, getConnId() + ": Could not compile pattern: " + pattern, ex);
      }
    }
  }

  public int getMaxTableNameLength()
  {
    return maxTableNameLength;
  }

  /**
   * Wrapper around DatabaseMetadata.getSearchStringEscape() that does not throw an exception.
   *
   * It also allows overwriting the driver's escape character through DbSettings in case
   * the driver returns the wrong information (like Oracle's driver did for some time).
   *
   * @return the escape characters to mask wildcards in a string literal
   */
  public final String getSearchStringEscape()
  {
    // using a config property to override the driver's behaviour
    // is necessary because some Oracle drivers return the wrong escape character
    String escape = getDbSettings().getSearchStringEscape();
    if (escape != null)
    {
      return escape;
    }

    try
    {
      return metaData.getSearchStringEscape();
    }
    catch (Throwable e)
    {
      // Should not happen
      return null;
    }
  }

  public char getCatalogSeparator()
  {
    return catalogSeparator;
  }

  public char getSchemaSeparator()
  {
    if (dbSettings.useCatalogSeparatorForSchema()) return getCatalogSeparator();
    String sep = dbSettings.getSchemaSeparator();
    if (sep == null) return '.';
    return sep.charAt(0);
  }

  public MetaDataSqlManager getMetaDataSQLMgr()
  {
    if (this.metaSqlMgr == null)
    {
      this.metaSqlMgr = new MetaDataSqlManager(productName, dbId, this.dbConnection.getDatabaseVersion());
    }
    return this.metaSqlMgr;
  }

  public ProcedureReader getProcedureReader()
  {
    return ReaderFactory.getProcedureReader(this);
  }

  public ViewReader getViewReader()
  {
    return ReaderFactory.createViewReader(this.dbConnection);
  }

  @Override
  public String getIdentifierQuoteCharacter()
  {
    return this.quoteCharacter;
  }

  public char getIdentifierQuoteChar()
  {
    if (StringUtil.isBlank(quoteCharacter))
    {
      return '\"';
    }
    return this.quoteCharacter.charAt(0);
  }

  public String getBaseTableTypeName()
  {
    return baseTableTypeName;
  }

  public String[] getTableTypesArray()
  {
    return Arrays.copyOf(tableTypesArray, tableTypesArray.length);
  }

  public String[] getTablesAndViewTypes()
  {
    List<String> types = new ArrayList<>(tableTypesList);
    types.addAll(getDbSettings().getViewTypes());
    return types.toArray(EMPTY_STRING_ARRAY);
  }

  public String[] getSelectableTypes()
  {
    return Arrays.copyOf(selectableTypes, selectableTypes.length);
  }

  public List<String> getTableTypes()
  {
    return new ArrayList<>(tableTypesList);
  }

  public boolean supportsMaterializedViews()
  {
    return mviewTypeName != null;
  }

  public String getMViewTypeName()
  {
    if (mviewTypeName == null) return "";
    return mviewTypeName;
  }

  public String getViewTypeName()
  {
    return "VIEW";
  }

  public DataTypeResolver getDataTypeResolver()
  {
    return this.dataTypeResolver;
  }

  public DatabaseMetaData getJdbcMetaData()
  {
    return this.metaData;
  }

  public WbConnection getWbConnection()
  {
    return this.dbConnection;
  }

  public Connection getSqlConnection()
  {
    return this.dbConnection.getSqlConnection();
  }

  public IndexReader getIndexReader()
  {
    synchronized (readerLock)
    {
      // Some IndexReaders cache information, so make sure only one instance is created.
      if (indexReader == null)
      {
        indexReader = ReaderFactory.getIndexReader(this);
      }
      return this.indexReader;
    }
  }

  public TableDefinitionReader getTableDefinitionReader()
  {
    return definitionReader;
  }

  /**
   * Check if the given DB object type can contain data. i.e. if
   * a SELECT FROM can be run against this type.
   * <br/>
   * By default these are objects of type
   * <ul>
   *  <li>table</li>
   *  <li>view</li>
   *  <li>synonym</li>
   *  <li>system view</li>
   *  <li>system table</li>
   * </ul>
   * <br/>
   * The list of types can be defined per DBMS using the property
   * <literal>workbench.db.objecttype.selectable.[dbid]</literal>.
   * <br/>
   * If that property is empty, the above defaults are used.
   *
   * @see #getObjectsWithData()
   * @see #retrieveTableTypes()
   * @see #getTableTypes()
   */
  public boolean objectTypeCanContainData(String type)
  {
    if (type == null) return false;
    return getObjectsWithData().contains(type);
  }

  public final Set<String> getObjectsWithData()
  {
    Set<String> objectsWithData = CollectionUtil.caseInsensitiveSet();
    objectsWithData.addAll(retrieveTableTypes());
    objectsWithData.addAll(getTableTypes());
    objectsWithData.addAll(getDbSettings().getViewTypes());

    String keyPrefix = "workbench.db.objecttype.selectable.";
    String defValue = Settings.getInstance().getProperty(keyPrefix + "default", null);
    String types = Settings.getInstance().getProperty(keyPrefix + dbId, defValue);

    if (types != null)
    {
      List<String> l = StringUtil.stringToList(types.toLowerCase(), ",", true, true);
      objectsWithData.addAll(l);
    }

    if (objectsWithData.isEmpty())
    {
      // make sure we have some basic information in here
      objectsWithData.add("table");
      objectsWithData.add("view");
      objectsWithData.add("system view");
      objectsWithData.add("system table");
    }

    List<String> notSelectable = Settings.getInstance().getListProperty("workbench.db.objecttype.not.selectable." + dbId, false);
    objectsWithData.removeAll(notSelectable);

    return objectsWithData;
  }

  /**
   * Return the name of the DBMS as reported by the JDBC driver.
   * <br/>
   *
   * For configuration purposes the DBID should be used as that can be part of a key in a properties file.
   *
   * @see #getDbId()
   */
  public final String getProductName()
  {
    return this.productName;
  }

  String getConnId()
  {
    if (dbConnection == null) return "";
    return dbConnection.getId();
  }

  /**
   * Return a clean version of the productname that can be used as the part of a properties key.
   *
   * @see #getProductName()
   */
  public final String getDbId()
  {
    return this.dbId;
  }

  public final DbSettings getDbSettings()
  {
    return this.dbSettings;
  }

  public boolean isSelectIntoNewTable(String sql)
  {
    return selectIntoVerifier.isSelectIntoNewTable(sql);
  }

  public boolean supportsSelectIntoNewTable()
  {
    return selectIntoVerifier != null && selectIntoVerifier.supportsSelectIntoNewTable();
  }

  private boolean isOracle() { return DBID.Oracle.isDB(dbId); }

  /**
   * Clears the cached list of catalogs to ignore.
   * Intended for testing purposes.
   * The list is re-read from the global settings if needed
   */
  public void clearIgnoredCatalogs()
  {
    catalogsToIgnore = null;
  }

  /**
   * Clears the cached list of schemas to ignore.
   * Intended for testing purposes.
   * The list is re-read from the global settings if needed
   */
  public void clearIgnoredSchemas()
  {
    schemasToIgnore = null;
  }

  /**
   * Returns true if the given schema name can be ignored for the current DBMS.
   * <br/>
   * The information which schema names can be ignored for the current DBMS is
   * retrieved from the settings file through the property
   * <literal>workbench.sql.ignoreschema.[dbid]</literal>
   *
   * @param schema
   * @return true if the supplied schema name should not be used
   */
  public boolean ignoreSchema(String schema)
  {
    return ignoreSchema(schema, null);
  }

  public boolean ignoreSchema(String schema, String currentSchema)
  {
    if (StringUtil.isEmpty(schema)) return true;
    if (dbSettings.alwaysUseSchema()) return false;

    if (schemasToIgnore == null)
    {
      schemasToIgnore = readIgnored("schema", null);
    }

    if (schemasToIgnore.contains("$current"))
    {
      String current = (currentSchema == null ? getCurrentSchema() : currentSchema);
      if (current != null)
      {
        return SqlUtil.objectNamesAreEqual(schema, current);
      }
    }
    return schemasToIgnore.contains("*") || schemasToIgnore.contains(schema);
  }

  private Set<String> readIgnored(String type, String defaultList)
  {
    Set<String> result;
    String ids = Settings.getInstance().getProperty("workbench.sql.ignore" + type + "." + dbId, defaultList);
    if (ids != null)
    {
      result = new TreeSet<>(StringUtil.stringToList(ids, ","));
    }
    else
    {
       result = Collections.emptySet();
    }
    return result;
  }

  /**
   * For testing purposes only
   */
  public void resetSchemasToIgnores()
  {
    schemasToIgnore = null;
  }

  /**
   * Check if the given {@link TableIdentifier} requires
   * the usage of the schema for a DML or DDL statement
   * statement.
   * <br/>
   * If the current DBMS does not support schemas, it returns false.
   * <br/>
   * If the current  schema is different to the table's schema, it returns true.
   * <br/>
   * If either no current schema is available, or if is the same as the table's schema
   * the result of ignoreSchema() is checked to leave out e.g. the PUBLIC schema in Postgres or H2
   *
   * @see #ignoreSchema(java.lang.String)
   * @see DbSettings#supportsSchemas()
   */
  public boolean needSchemaInDML(TableIdentifier table)
  {
    try
    {
      String tblSchema = table.getSchema();

      // Object names may never be prefixed with PUBLIC in Oracle
      if (this.isOracle() && "PUBLIC".equalsIgnoreCase(tblSchema)) return false;

      if (!dbSettings.supportsSchemas()) return false;
      if (dbSettings.alwaysUseSchema()) return true;

      String currentSchema = getCurrentSchema();

      // If the current schema is not the one of the table, the schema is needed in DML statements
      // to avoid wrong implicit schema resolution
      if (currentSchema != null && !currentSchema.equalsIgnoreCase(tblSchema)) return true;

      // otherwise check if the schema should be ignored
      return (!ignoreSchema(tblSchema, currentSchema));
    }
    catch (Throwable th)
    {
      return true;
    }
  }

  /**
   * Check if the given {@link TableIdentifier} requires the usage of a catalog name for a DML statement.
   * <br/>
   * If the current DB engine is Microsoft Access, this method always returns true.
   * <br/>
   *
   * @see #ignoreCatalog(java.lang.String)
   * @see DbSettings#alwaysUseCatalog()
   * @see DbSettings#useCatalogInDML()
   * @see DbSettings#needsCatalogIfNoCurrent()
   */
  public boolean needCatalogInDML(TableIdentifier table)
  {
    if (this.isAccess) return true;

    String cat = table.getCatalog();
    if (StringUtil.isEmpty(cat)) return false;

    if (this.isExcel)
    {
      String currentCat = getCurrentCatalog();
      if (StringUtil.isEmpty(currentCat)) return true;

      // Excel puts the directory into the catalog
      // so we need to normalize the directory name
      File c1 = new File(cat);
      File c2 = new File(currentCat);
      return !c1.equals(c2);
    }

    if (!dbSettings.supportsCatalogs()) return false;
    if (!dbSettings.useCatalogInDML()) return false;
    if (dbSettings.alwaysUseCatalog()) return true;

    if (this.dbSettings.needsCatalogIfNoCurrent())
    {
      String currentCat = getCurrentCatalog();
      if (StringUtil.isEmpty(currentCat))
      {
        return true;
      }
    }
    return !ignoreCatalog(cat);
  }

  /**
   * Checks if the given catalog name should be ignored
   * in SQL statements.
   *
   * @param catalog the catalog name to check
   * @return true if the catalog is not needed in SQL statements
   */
  public boolean ignoreCatalog(String catalog)
  {
    return ignoreCatalog(catalog, null);
  }

  public boolean ignoreCatalog(String catalog, String currentCatalog)
  {
    if (catalog == null) return true;
    if (dbSettings.alwaysUseCatalog()) return false;
    if (!dbSettings.supportsCatalogs()) return true;

    if (catalogsToIgnore == null)
    {
      catalogsToIgnore = readIgnored("catalog", "$current");
    }
    if (catalogsToIgnore.contains("$current"))
    {
      String current = currentCatalog == null ? getCurrentCatalog() : currentCatalog;
      if (current != null)
      {
        return SqlUtil.objectNamesAreEqual(current, catalog);
      }
    }
    return catalogsToIgnore.contains("*") || catalogsToIgnore.contains(catalog);
  }

  /**
   * Wrapper for DatabaseMetaData.supportsBatchUpdates() that throws
   * no exception. If any error occurs, false will be returned
   */
  public boolean supportsBatchUpdates()
  {
    try
    {
      return this.metaData.supportsBatchUpdates();
    }
    catch (SQLException e)
    {
      return false;
    }
  }

  /**
   * Returns the type of the passed TableIdentifier.
   * <br/>
   * This could be VIEW, TABLE, SYNONYM, ...
   * <br/>
   * If the JDBC driver does not return the object through the getObjects()
   * method, null is returned, otherwise the value reported in TABLE_TYPE
   * <br/>
   * If there is more than object with the same name but different types
   * (is there a DB that supports that???) than the type of the first object found
   * will be returned.
   * @see #getObjects(String, String, String, String[])
   */
  public String getObjectType(TableIdentifier table)
  {
    String type = null;
    try
    {
      DbObjectFinder finder = new DbObjectFinder(this);
      TableIdentifier tbl = table.createCopy();
      tbl.adjustCase(this.dbConnection);
      tbl.checkIsQuoted(this);
      TableIdentifier target = finder.findObject(tbl);
      if (target != null)
      {
        type = target.getType();
      }
    }
    catch (Exception e)
    {
      type = null;
    }
    return type;
  }

  public boolean isReservedWord(String name)
  {
    assert dbId != null;
    synchronized (reservedWords)
    {
      if (reservedWords.isEmpty())
      {
        SqlKeywordHelper helper = new SqlKeywordHelper(dbId);
        reservedWords.addAll(helper.getReservedWords());
        reservedWords.addAll(helper.getOperators());
        if (dbSettings.getAliasId() != null)
        {
          SqlKeywordHelper aliasHelper = new SqlKeywordHelper(dbSettings.getAliasId());
          reservedWords.addAll(aliasHelper.getReservedWords());
          reservedWords.addAll(aliasHelper.getOperators());
        }
      }
      return this.reservedWords.contains(name);
    }
  }

  public boolean isKeyword(String name)
  {
    assert dbId != null;
    synchronized (keywords)
    {
      if (keywords.isEmpty())
      {
        SqlKeywordHelper helper = new SqlKeywordHelper(dbId);
        keywords.addAll(helper.getKeywords());
        keywords.addAll(helper.getOperators());

        if (dbSettings.getAliasId() != null)
        {
          SqlKeywordHelper aliasHelper = new SqlKeywordHelper(dbSettings.getAliasId());
          keywords.addAll(aliasHelper.getKeywords());
          keywords.addAll(aliasHelper.getOperators());
        }

        try
        {
          String words = metaData.getSQLKeywords();
          if (StringUtil.isNotBlank(words))
          {
            List<String> driverKeywords = StringUtil.stringToList(words, ",");
            keywords.addAll(driverKeywords);
          }
        }
        catch (Throwable sql)
        {
          LogMgr.logDebug(new CallerInfo(){}, getConnId() + ": Could not read SQL keywords from driver", sql);
        }
      }
      return this.keywords.contains(name);
    }
  }

  /**
   * Checks if the given name is already quoted according to the SQL rules
   * for the current DBMS. This takes non-standard DBMS into account.
   *
   * @param name
   * @return true if the values is already quoted.
   */
  @Override
  public boolean isQuoted(String name)
  {
    if (name == null) return false;
    name = name.trim();
    if (name.length() < 2) return false;

    if (name.startsWith(quoteCharacter) && name.endsWith(quoteCharacter)) return true;

    // SQL Server driver claims that a " is the quote character but still
    // accepts those iditotic brackets as quote characters...
    if (DBID.SQL_Server.isDB(dbId))
    {
      if (name.charAt(0) == '[' && name.charAt(name.length() - 1) == ']') return true;
    }
    return false;
  }

  @Override
  public String removeQuotes(String name)
  {
    if (StringUtil.isEmpty(name)) return name;

    name = name.trim();

    if (DBID.SQL_Server.isDB(dbId) && name.startsWith("[") && name.endsWith("]"))
    {
      return name.substring(1, name.length() - 1);
    }
    return StringUtil.removeQuotes(name, quoteCharacter);
  }

  @Override
  public String quoteObjectname(String aName)
  {
    return quoteObjectname(aName, false);
  }


  /**
   * Quotes the given object name if necessary.
   *
   * Quoting of names is necessary if the name is a reserved word in the
   * database or if the names contains invalid characters or if the name is not
   * in default case as the database uses.
   *
   * @see #needsQuotes(java.lang.String)
   * @see #getIdentifierQuoteCharacter()
   */
  @Override
  public String quoteObjectname(String name, boolean quoteAlways)
  {
    if (StringUtil.isEmpty(name)) return null;

    // already quoted?
    if (isQuoted(name)) return name;

    if (this.dbSettings.neverQuoteObjects()) return removeQuotes(name);

    boolean needQuote = quoteAlways || needsQuotes(name);

    if (needQuote)
    {
      StringBuilder result = new StringBuilder(name.length() + quoteCharacter.length() * 2);
      result.append(this.quoteCharacter);
      result.append(name.trim());
      result.append(this.quoteCharacter);
      return result.toString();
    }
    return name;
  }

  @Override
  public boolean isLegalIdentifier(String name)
  {
    Matcher matcher;

    if (identifierPattern == null)
    {
      matcher = SqlUtil.SQL_IDENTIFIER.matcher(name);
    }
    else
    {
      matcher = identifierPattern.matcher(name);
    }
    return matcher.matches();
  }


  /**
   * Check if the given object name needs quoting for this DBMS.
   *
   * @param name the name to check
   * @return true if the name needs quotes (or if quoted names should always be used for this DBMS)
   *
   * @see #isReservedWord(java.lang.String)
   * @see #storesLowerCaseIdentifiers()
   * @see #storesMixedCaseIdentifiers()
   * @see #storesUpperCaseIdentifiers()
   */
  @Override
  public boolean needsQuotes(String name)
  {
    if (name == null) return false;
    if (name.length() == 0) return false;

    // already quoted?
    if (isQuoted(name)) return false;

    // avoid using a regex for this simple case
    if (name.indexOf(' ') > -1) return true;

    if (!isLegalIdentifier(name)) return true;

    if (this.storesMixedCaseIdentifiers() && StringUtil.isMixedCase(name))
    {
      return true;
    }

    if (this.storesUpperCaseIdentifiers() && !StringUtil.isUpperCase(name))
    {
      return true;
    }

    if (this.storesLowerCaseIdentifiers() && !StringUtil.isLowerCase(name))
    {
      return true;
    }

    return isReservedWord(name);
  }

  /**
   * Adjusts the case of the given schema name to the
   * case in which the server stores schema names.
   *
   * This is needed e.g. when the user types a
   * table name, and that value is used to retrieve
   * the table definition.
   *
   * If the schema name is quoted, then the case will not be adjusted.
   *
   * @param schema the schema name to adjust
   * @return the adjusted schema name
   */
  public String adjustSchemaNameCase(String schema)
  {
    if (StringUtil.isBlank(schema)) return null;
    if (isQuoted(schema)) return schema.trim();

    schema = SqlUtil.removeObjectQuotes(schema).trim();

    if (this.storesUpperCaseSchemas())
    {
      return schema.toUpperCase();
    }
    else if (this.storesLowerCaseSchemas())
    {
      return schema.toLowerCase();
    }
    return schema;
  }

  /**
   * Returns true if the given object name needs quoting due
   * to mixed case writing or because the case of the name
   * does not match the case in which the database stores its objects
   */
  public boolean isDefaultCase(String name)
  {
    if (name == null) return true;

    if (storesMixedCaseIdentifiers()) return true;

    boolean isUpper = StringUtil.isUpperCase(name);
    boolean isLower = StringUtil.isLowerCase(name);

    if (isUpper && storesUpperCaseIdentifiers()) return true;
    if (isLower && storesLowerCaseIdentifiers()) return true;

    return false;
  }


  /**
   * Adjusts the case of the given object to the
   * case in which the server stores objects
   * This is needed e.g. when the user types a
   * table name, and that value is used to retrieve
   * the table definition. Usually the getColumns()
   * method is case sensitiv.
   *
   * @param name the object name to adjust
   * @return the adjusted object name
   */
  public String adjustObjectnameCase(String name)
  {
    if (name == null) return null;
    // if we have quotes, keep them...
    if (isQuoted(name)) return name.trim();

    try
    {
      if (this.storesMixedCaseIdentifiers())
      {
        return name;
      }
      else if (this.storesUpperCaseIdentifiers())
      {
        return name.toUpperCase();
      }
      else if (this.storesLowerCaseIdentifiers())
      {
        return name.toLowerCase();
      }
    }
    catch (Exception e)
    {
    }
    return name.trim();
  }

  /**
   * Returns the current schema.
   *
   * @see SchemaInformationReader#getCurrentSchema()
   */
  public String getCurrentSchema()
  {
    if (dbSettings.supportsSchemas() && schemaInfoReader.isSupported())
    {
      return this.schemaInfoReader.getCurrentSchema();
    }

    String schema = null;
    if (supportsGetSchema)
    {
      try
      {
        schema = this.dbConnection.getSqlConnection().getSchema();
      }
      catch (Throwable ex)
      {
        LogMgr.logWarning(new CallerInfo(){}, "Could not get current schema from driver. Assuming schemas are not supported", ex);
        supportsGetSchema = false;
      }
    }
    return schema;
  }

  public void clearCachedSchemaInformation()
  {
    this.schemaInfoReader.clearCache();
    this.catalogInfoReader.clearCache();
  }

  /**
   * Returns the schema that should be used for the current user
   * This essentially calls {@link #getCurrentSchema()}. The method
   * then checks if the schema should be ignored for the current
   * dbms by calling {@link #ignoreSchema(String, String)}. If the
   * Schema should not be ignored, then it's returned, otherwise
   * the method will return null
   */
  public String getSchemaToUse()
  {
    String schema = this.getCurrentSchema();
    if (schema == null) return null;
    if (this.ignoreSchema(schema, schema)) return null;
    return schema;
  }

  public ObjectListDataStore getObjects(String aCatalog, String aSchema, String[] types)
    throws SQLException
  {
    return getObjects(aCatalog, aSchema, null, types);
  }

  public static String cleanupWildcards(String pattern)
  {
    if (StringUtil.isEmpty(pattern)) return null;
    if ("*".equals(pattern) || "%".equals(pattern)) return null;
    return SqlUtil.removeObjectQuotes(StringUtil.replace(pattern, "*", "%"));
  }

  private Set<String> getExtenderTypes()
  {
    Set<String> types = CollectionUtil.caseInsensitiveSet();
    for (ObjectListExtender extender : extenders)
    {
      types.addAll(extender.supportedTypes());
    }
    return types;
  }

  /**
   * Remove any type from the array that is not a native object type as reported by the JDBC driver
   *
   * @param types  the types to cleanup
   * @return an array containing only the native types of the JDBC driver
   * @see #retrieveTableTypes()
   */
  private String[] cleanupTypes(String[] types)
  {
    if (types == null || types.length == 0) return types;

    List<String> typesToUse = new ArrayList<>(types.length);
    Set<String> extenderTypes = getExtenderTypes();

    Collection<String> nativeTypes = retrieveTableTypes();
    for (String type : types)
    {
      if (StringUtil.isBlank(type)) continue;

      // don't include types from registered ObjectListExtenders
      if (extenderTypes.contains(type)) continue;

      if (nativeTypes.contains(type))
      {
        typesToUse.add(type);
      }
    }

    String[] cleanTypes = new String[typesToUse.size()];
    cleanTypes = typesToUse.toArray(cleanTypes);
    return cleanTypes;
  }

  public ObjectListDataStore createObjectListDataStore()
  {
    boolean sortMViewAsTable = isOracle() && Settings.getInstance().getBoolProperty("workbench.db.oracle.sortmviewsastable", true);

    ObjectListDataStore result = new ObjectListDataStore(catalogTerm.toUpperCase(), schemaTerm.toUpperCase());
    result.setSortMviewsAsTables(sortMViewAsTable);

    return result;
  }

  public ObjectListDataStore getObjects(String catalogPattern, String schemaPattern, String namePattern, String[] types)
    throws SQLException
  {
    catalogPattern = cleanupWildcards(catalogPattern);
    schemaPattern = cleanupWildcards(schemaPattern);
    namePattern = cleanupWildcards(namePattern);

    ObjectListDataStore result = createObjectListDataStore();

    boolean sequencesReturned = false;
    boolean synRetrieved = false;
    boolean synonymsRequested = typeIncluded(SynonymReader.SYN_TYPE_NAME, types);

    ObjectListFilter filter = new ObjectListFilter(dbId);

    if (isOracle())
    {
      types = OracleUtils.adjustTableTypes(dbConnection, types);
    }

    String escape = dbConnection.getSearchStringEscape();

    String escapedNamePattern = namePattern;
    String escapedSchema = schemaPattern;
    String escapedCatalog = SqlUtil.removeObjectQuotes(catalogPattern);

    if (getDbSettings().supportsMetaDataWildcards())
    {
      escapedNamePattern = SqlUtil.escapeUnderscore(namePattern, escape);
    }

    if (getDbSettings().supportsMetaDataSchemaWildcards())
    {
      escapedSchema = SqlUtil.escapeUnderscore(schemaPattern, escape);
    }

    if (getDbSettings().supportsMetaDataCatalogWildcards())
    {
      escapedCatalog = SqlUtil.escapeUnderscore(catalogPattern, escape);
    }

    SequenceReader seqReader = getSequenceReader();
    String sequenceType =  seqReader != null ? seqReader.getSequenceTypeName() : null;
    final SynonymReader synReader = this.getSynonymReader();
    String synTypeName = SynonymReader.SYN_TYPE_NAME;

    if (synReader != null)
    {
      synTypeName = synReader.getSynonymTypeName();
    }

    if (!getDbSettings().supportsMetaDataNullPattern() && escapedNamePattern == null)
    {
      escapedNamePattern = "%";
    }

    final CallerInfo ci = new CallerInfo(){};
    ResultSet tableRs = null;
    try
    {
      String[] typesToUse = StringUtil.toUpperCase(types);
      // Some DBMS don't like it when invalid types are passed to getTables()
      // so cleanupTypeList() will remove those that are handled by the extenders
      if (getDbSettings().cleanupTypeList())
      {
        typesToUse = cleanupTypes(types);
      }

      if (Settings.getInstance().getDebugMetadataSql())
      {
        LogMgr.logInfo(ci, getConnId() + ": Calling getTables() using: catalog="+ escapedCatalog +
          ", schema=" + escapedSchema +
          ", name=" + escapedNamePattern +
          ", types=" + (typesToUse == null ? "null" : Arrays.asList(typesToUse).toString()));
      }

      long start = System.currentTimeMillis();

      // getTables() only needs to be called if the types array is not empty after cleaning it up
      // If it is empty, only non-native types are requested and they are handled by the extenders
      if (typesToUse == null || typesToUse.length > 0)
      {
        tableRs = metaData.getTables(escapedCatalog, escapedSchema, escapedNamePattern, typesToUse);
        if (tableRs == null)
        {
          LogMgr.logError(ci, getConnId() + ": Driver returned a NULL ResultSet from getTables()",null);
        }
      }

      long duration = System.currentTimeMillis() - start;
      LogMgr.logDebug(ci, getConnId() + ": getTables() took: " + duration + "ms");

      if (tableRs != null && Settings.getInstance().getDebugMetadataSql())
      {
        SqlUtil.dumpResultSetInfo("DatabaseMetaData.getTables()", tableRs.getMetaData());
      }

      boolean useColumnNames = dbSettings.useColumnNameForMetadata();

      ObjectTypeNameMapper mapper = new ObjectTypeNameMapper(dbSettings);

      start = System.currentTimeMillis();

      while (tableRs != null && tableRs.next())
      {
        String cat = useColumnNames ? tableRs.getString("TABLE_CAT") : tableRs.getString(1);
        String schema = useColumnNames ? tableRs.getString("TABLE_SCHEM") : tableRs.getString(2);
        String name = useColumnNames ? tableRs.getString("TABLE_NAME") : tableRs.getString(3);
        String ttype = useColumnNames ? tableRs.getString("TABLE_TYPE") : tableRs.getString(4);
        if (name == null) continue;

        ttype = mapper.mapInternalNameToSQL(ttype);

        if (filter.isExcluded(ttype, name)) continue;

        String remarks = useColumnNames ? tableRs.getString(ObjectListDataStore.RESULT_COL_REMARKS) : tableRs.getString(5);

        boolean isSynoym = synRetrieved || synTypeName.equals(ttype);

        // prevent duplicate retrieval of SYNONYMs if the driver
        // returns them already, but the Settings have enabled
        // Synonym retrieval as well
        // (e.g. because an upgraded Driver now returns the synonyms)
        if (isSynoym)
        {
          synRetrieved = true;
        }

        if (isIndexType(ttype)) continue;

        int row = result.addRow();
        result.setObjectName(row, name);
        result.setType(row, ttype);
        result.setCatalog(row, cat);
        result.setSchema(row, schema);
        result.setRemarks(row, remarks);
        if (!sequencesReturned && StringUtil.equalString(sequenceType, ttype)) sequencesReturned = true;
        TableIdentifier tbl = result.getTableIdentifier(row);
        result.getRow(row).setUserObject(tbl);

        // if retrieval takes longer than 2 seconds
        // then write a trace message every 1000 rows
        long sofar = System.currentTimeMillis() - start;
        if (sofar >= 2000 && row % 1000 == 0)
        {
          LogMgr.logTrace(ci, getConnId() + ": Processed " + row + " objects");
        }
      }

      duration = System.currentTimeMillis() - start;
      LogMgr.logDebug(new CallerInfo(){}, getConnId() + ": Processing " + result.getRowCount() + " tables took: " + duration + "ms");
    }
    finally
    {
      JdbcUtils.closeResult(tableRs);
    }


    // Synonym and Sequence retrieval is handled differently to "regular" ObjectListExtenders
    // because some JDBC driver versions do retrieve this information automatically and some don't
    if (seqReader != null && typeIncluded(seqReader.getSequenceTypeName(), types) &&
        dbSettings.getBoolProperty("retrieve_sequences", true)
        && !sequencesReturned)
    {
      List<SequenceDefinition> sequences = seqReader.getSequences(catalogPattern, schemaPattern, namePattern);
      for (SequenceDefinition sequence : sequences)
      {
        int row = result.addRow();

        result.setObjectName(row, sequence.getSequenceName());
        result.setType(row, sequence.getObjectType());
        result.setCatalog(row, sequence.getCatalog());
        result.setSchema(row, sequence.getSchema());
        result.setRemarks(row, sequence.getComment());
        result.getRow(row).setUserObject(sequence);
      }
    }

    long start = System.currentTimeMillis();

    if (synReader != null && synonymsRequested && dbSettings.getBoolProperty("retrieve_synonyms", false) && !synRetrieved)
    {
      List<TableIdentifier> syns = synReader.getSynonymList(dbConnection, catalogPattern, schemaPattern, namePattern);
      for (TableIdentifier synonym : syns)
      {
        int row = result.addRow();

        result.setObjectName(row, synonym.getTableName());
        result.setType(row, synonym.getType());
        result.setCatalog(row, synonym.getCatalog());
        result.setSchema(row, synonym.getSchema());
        result.setRemarks(row, synonym.getComment());
        result.getRow(row).setUserObject(synonym);
      }
    }

    for (ObjectListAppender appender : appenders)
    {
      appender.extendObjectList(dbConnection, result, catalogPattern, schemaPattern, namePattern, types);
    }

    for (ObjectListExtender extender : extenders)
    {
      if (extender.handlesType(types))
      {
        extender.extendObjectList(dbConnection, result, catalogPattern, schemaPattern, namePattern, types);
      }
    }

    if (objectListEnhancer != null)
    {
      objectListEnhancer.updateObjectList(dbConnection, result, catalogPattern, schemaPattern, namePattern, types);
    }

    for (ObjectListCleaner cleaner : cleaners)
    {
      cleaner.cleanupObjectList(dbConnection, result, catalogPattern, schemaPattern, namePattern, types);
    }

    long duration = System.currentTimeMillis() - start;
    LogMgr.logDebug(new CallerInfo(){}, getConnId() + ": Processing appenders and extenders took: " + duration + "ms");

    start = System.currentTimeMillis();
    applyRetrievalFilters(result);
    duration = System.currentTimeMillis() - start;
    LogMgr.logDebug(new CallerInfo(){}, getConnId() + ": Applying retrieval filters: " + duration + "ms");

    result.resetStatus();

    return result;
  }

  private void applyRetrievalFilters(ObjectListDataStore result)
  {
    if (!Settings.getInstance().getBoolProperty("workbench.db.objectlist.applyfilter", true)) return;
    if (dbConnection == null) return;

    ObjectNameFilter schemaFilter = dbConnection.getSchemaFilter();
    ObjectNameFilter catalogFilter = dbConnection.getCatalogFilter();
    result.applyRetrievalFilters(catalogFilter, schemaFilter);
  }

  /**
   * Check if any of the type in <tt>toCheck</tt> match the requested types.
   *
   * @param toCheck  the types to check
   * @param requestedTypes the requested object types
   *
   * @see #typeIncluded(String, String[])
   */
  public static boolean typesIncluded(String[] toCheck, String[] requestedTypes)
  {
    if (requestedTypes == null) return true;
    if (toCheck == null) return false;
    for (String type : toCheck)
    {
      if (typeIncluded(type, requestedTypes)) return true;
    }
    return false;
  }

  /**
   * Checks if the given type is contained in the passed array of "requested types".
   *
   * <p>If at least one entry in the types array is * then this
   * method will always return true</p>
   *
   * @param type the type to check for
   * @param requestedTypes the list of types to be checked
   * @return true, if type is contained in requestedTypes
   *
   * @see #typesIncluded(String[], String[])
   * @see #getObjectList(String, String[])
   */
  public static boolean typeIncluded(String type, String[] requestedTypes)
  {
    if (requestedTypes == null) return true;
    if (type == null) return false;
    for (String rtype : requestedTypes)
    {
      if (rtype == null) continue;
      if (rtype.equals("*")) return true;
      if (rtype.equals("%")) return true;
      if (type.equalsIgnoreCase(rtype)) return true;
    }
    return false;
  }

  public boolean storesMixedCaseSchemas()
  {
    IdentifierCase ocase = this.dbSettings.getSchemaNameCase();
    if (ocase == IdentifierCase.unknown)
    {
      return storesMixedCaseIdentifiers();
    }
    return ocase == IdentifierCase.mixed;
  }

  public boolean storesUpperCaseSchemas()
  {
    IdentifierCase ocase = this.dbSettings.getSchemaNameCase();
    if (ocase == IdentifierCase.unknown)
    {
      return storesUpperCaseIdentifiers();
    }
    return ocase == IdentifierCase.upper;
  }

  public boolean storesLowerCaseSchemas()
  {
    IdentifierCase ocase = this.dbSettings.getSchemaNameCase();
    if (ocase == IdentifierCase.unknown)
    {
      return storesLowerCaseIdentifiers();
    }
    return ocase == IdentifierCase.lower;
  }


  /**
   * Returns true if the server stores identifiers in mixed case.
   *
   * <p>
   * Usually this is delegated to the JDBC driver, but as some drivers
   * implement this incorrectly, this can be overriden in workbench.settings
   * with the property:<br/>
   * <tt>workbench.db.[dbid].objectname.case</tt>
   * </p>
   */
  public boolean storesMixedCaseIdentifiers()
  {
    IdentifierCase ocase = this.dbSettings.getObjectNameCase();
    if (ocase != IdentifierCase.unknown)
    {
      return ocase == IdentifierCase.mixed;
    }
    try
    {
      boolean upper = this.metaData.storesUpperCaseIdentifiers();
      boolean lower = this.metaData.storesLowerCaseIdentifiers();
      boolean mixed = this.metaData.storesMixedCaseIdentifiers();

      return mixed || (upper && lower);
    }
    catch (SQLException e)
    {
      return false;
    }
  }

  /**
   * Returns true if the server stores identifiers in lower case.
   * Usually this is delegated to the JDBC driver, but as some drivers
   * (e.g. Frontbase) implement this incorrectly, this can be overriden
   * in workbench.settings with the property:
   * workbench.db.objectname.case.<dbid>
   */
  public boolean storesLowerCaseIdentifiers()
  {
    IdentifierCase ocase = this.dbSettings.getObjectNameCase();
    if (ocase != IdentifierCase.unknown)
    {
      return ocase == IdentifierCase.lower;
    }
    try
    {
      boolean lower = this.metaData.storesLowerCaseIdentifiers();
      return lower;
    }
    catch (SQLException e)
    {
      return false;
    }
  }

  /**
   * Returns true if the server stores identifiers in upper case.
   * Usually this is delegated to the JDBC driver, but as some drivers
   * (e.g. Frontbase) implement this incorrectly, this can be overriden
   * in workbench.settings
   */
  public boolean storesUpperCaseIdentifiers()
  {
    IdentifierCase ocase = this.dbSettings.getObjectNameCase();
    if (ocase != IdentifierCase.unknown)
    {
      return ocase == IdentifierCase.upper;
    }
    try
    {
      boolean upper = this.metaData.storesUpperCaseIdentifiers();
      return upper;
    }
    catch (SQLException e)
    {
      return false;
    }
  }

  /**
   * Enable Oracle's DBMS_OUTPUT package with a default buffer size
   * @see #enableOutput(long)
   */
  public void enableOutput()
  {
    this.enableOutput(-1);
  }

  /**
   * Enable Oracle's DBMS_OUTPUT package.
   * @see workbench.db.oracle.DbmsOutput#enable(long)
   */
  public void enableOutput(long aLimit)
  {
    if (!this.isOracle())
    {
      return;
    }

    if (this.oraOutput == null)
    {
      try
      {
        this.oraOutput = new DbmsOutput(this.dbConnection.getSqlConnection());
      }
      catch (Exception e)
      {
        LogMgr.logError(new CallerInfo(){}, getConnId() + ": Could not create DbmsOutput", e);
        this.oraOutput = null;
      }
    }

    if (this.oraOutput != null)
    {
      try
      {
        this.oraOutput.enable(aLimit);
      }
      catch (Throwable e)
      {
        LogMgr.logError(new CallerInfo(){}, getConnId() + ": Error when enabling DbmsOutput", e);
      }
    }
  }

  public boolean isDbmsOutputEnabled()
  {
    if (!this.isOracle()) return false;
    if (oraOutput == null) return false;
    return oraOutput.isEnabled();
  }

  /**
   * Disable Oracle's DBMS_OUTPUT package
   * @see workbench.db.oracle.DbmsOutput#disable()
   */
  public void disableOutput()
  {
    if (!this.isOracle()) return;

    if (this.oraOutput != null)
    {
      try
      {
        this.oraOutput.disable();
        this.oraOutput = null;
      }
      catch (Throwable e)
      {
        LogMgr.logError(new CallerInfo(){}, getConnId() + ": Error when disabling DbmsOutput", e);
      }
    }
  }

  /**
   * Return any server side messages. Currently this is only implemented
   * for Oracle (and is returning messages that were "printed" using
   * the DBMS_OUTPUT package
   */
  public String getOutputMessages()
  {
    String result = StringUtil.EMPTY_STRING;

    if (this.oraOutput != null)
    {
      try
      {
        result = this.oraOutput.getResult();
      }
      catch (Throwable th)
      {
        LogMgr.logError(new CallerInfo(){}, getConnId() + ": Error when retrieving Output Messages", th);
        result = StringUtil.EMPTY_STRING;
      }
    }
    return result;
  }

  /**
   * Release any resources for this object.
   *
   * After a call to close(), this instance should not be used any longer.
   * @see DbmsOutput#close()
   * @see SchemaInformationReader#dispose()
   */
  public void close()
  {
    if  (this.dbConnection != null && !this.dbConnection.isBusy())
    {
      if (this.oraOutput != null)   this.oraOutput.close();
      if (this.schemaInfoReader != null) this.schemaInfoReader.dispose();
    }
  }

  public boolean isExtendedObject(DbObject o)
  {
    for (ObjectListExtender extender : extenders)
    {
      if (extender.handlesType(o.getObjectType())) return true;
    }
    return false;
  }

  /**
   * Retrieves the object source for the given object
   * using a registered ObjectListExtender.
   *
   * @param o the object to retrieve
   * @return the source of the object or null, if this object is not handled by an extender
   * @see #isExtendedObject(workbench.db.DbObject)
   */
  public String getObjectSource(DbObject o)
  {
    if (o == null) return null;
    for (ObjectListExtender extender : extenders)
    {
      if (extender.handlesType(o.getObjectType()))
      {
        return extender.getObjectSource(dbConnection, o);
      }
    }
    return null;
  }

  public List<ColumnIdentifier> getTableColumns(TableIdentifier table)
    throws SQLException
  {
    return getTableColumns(table, true);
  }

  /**
   * Return the column list for the given table.
   *
   * @param table             the table for which to retrieve the column definition
   * @param readPkDefinition  if true, the PK definition of the table will also be retrieved
   *
   * @see #getTableDefinition(workbench.db.TableIdentifier)
   */
  public List<ColumnIdentifier> getTableColumns(TableIdentifier table, boolean readPkDefinition)
    throws SQLException
  {
    TableDefinition def = definitionReader.getTableDefinition(table, readPkDefinition);
    if (def == null) return Collections.emptyList();
    if (!table.isPkInitialized() && readPkDefinition)
    {
      table.setPrimaryKey(def.getTable().getPrimaryKey());
    }
    return def.getColumns();
  }

  public DbObject getObjectDefinition(TableIdentifier table)
  {
    for (ObjectListExtender extender : extenders)
    {
      if (extender.handlesType(table.getObjectType()))
      {
        return extender.getObjectDefinition(dbConnection, table);
      }
    }
    return null;
  }

  public boolean isSynonym(TableIdentifier table)
  {
    if (table == null) return false;
    SynonymReader reader = getSynonymReader();
    if (reader == null) return false;
    return reader.getSynonymTypeName().equalsIgnoreCase(table.getType());
  }

  public boolean isSequenceType(String type)
  {
    if (type == null) return false;
    SequenceReader reader = getSequenceReader();
    if (reader == null) return false;
    return StringUtil.equalStringIgnoreCase(type, reader.getSequenceTypeName());
  }

  public List<ColumnIdentifier> getObjectColumns(DbObject object)
    throws SQLException
  {
    if (object == null) return null;

    if (isViewOrTable(object.getObjectType()) && (object instanceof TableIdentifier))
    {
      return getTableColumns((TableIdentifier)object);
    }

    for (ObjectListExtender extender : extenders)
    {
      if (extender.handlesType(object.getObjectType()))
      {
        return extender.getColumns(dbConnection, object);
      }
    }
    return null;
  }

  public boolean hasColumns(DbObject object)
  {
    if (object == null) return false;
    return hasColumns(object.getObjectType());
  }

  public boolean hasColumns(String objectType)
  {
    if (objectType == null) return false;
    if (isExtendedTableType(objectType)) return true;
    if (isViewType(objectType)) return true;
    if (objectType.equals(MVIEW_NAME)) return true;

    for (ObjectListExtender extender : extenders)
    {
      if (extender.handlesType(objectType))
      {
        return extender.hasColumns();
      }
    }
    return false;
  }

  public DataStore getObjectDetails(TableIdentifier table)
    throws SQLException
  {
    DataStore def = null;
    for (ObjectListExtender extender : extenders)
    {
      if (extender.handlesType(table.getObjectType()))
      {
        def = extender.getObjectDetails(dbConnection, table);
        break;
      }
    }
    if (def == null && isSequenceType(table.getObjectType()))
    {
      TableIdentifier tbl = table.createCopy();
      tbl.adjustCase(this.dbConnection);
      String schema = SqlUtil.removeObjectQuotes(table.getSchema());
      String seqname = SqlUtil.removeObjectQuotes(table.getObjectName());
      String catalog = SqlUtil.removeObjectQuotes(table.getCatalog());
      DataStore seqDef = getSequenceReader().getRawSequenceDefinition(catalog, schema, seqname);
      if (GuiSettings.getTransformSequenceDisplay() && seqDef != null && seqDef.getRowCount() == 1)
      {
        DatastoreTransposer transpose = new DatastoreTransposer(seqDef);

        // No need to show the remarks as a row in the sequence details
        transpose.setColumnsToExclude(CollectionUtil.caseInsensitiveSet(RESULT_COL_REMARKS));

        def = transpose.transposeRows(new int[]{0});
        def.getColumns()[0].setColumnName(ResourceMgr.getString("TxtAttribute"));
        def.getColumns()[1].setColumnName(ResourceMgr.getString("TxtValue"));
      }
      else
      {
        def = seqDef;
      }
    }
    else if (def == null)
    {
      TableDefinition tdef = definitionReader.getTableDefinition(table, true);
      def = new TableColumnsDatastore(tdef);
    }
    if (def != null) def.resetStatus();
    return def;
  }

  /**
   * Return the definition of the given table.
   * <br/>
   * To display the columns for a table in a DataStore create an
   * instance of {@link TableColumnsDatastore}.
   *
   * @param toRead The table for which the definition should be retrieved
   *
   * @throws SQLException
   * @return the definition of the table.
   * @see TableColumnsDatastore
   * @see TableDefinitionReader#getTableDefinition(workbench.db.TableIdentifier)
   */
  public TableDefinition getTableDefinition(TableIdentifier toRead)
    throws SQLException
  {
    return getTableDefinition(toRead, true);
  }

  public TableDefinition getTableDefinition(TableIdentifier toRead, boolean includePkInformation)
    throws SQLException
  {
    if (toRead == null) return null;
    return definitionReader.getTableDefinition(toRead, includePkInformation);
  }

  /**
   * If the passed TableIdentifier is a Synonym and the current
   * DBMS supports synonyms, a TableIdentifier for the "real"
   * table is returned.
   *
   * Otherwise the passed TableIdentifier is returned
   */
  public TableIdentifier resolveSynonym(TableIdentifier tbl)
  {
    if (tbl == null) return null;
    if (!supportsSynonyms()) return tbl;
    String type = tbl.getType();
    if (type != null && !dbSettings.isSynonymType(type)) return tbl;
    TableIdentifier syn = getSynonymTable(tbl);
    if (syn == null) return tbl;
    return syn;
  }

  /**
   * Returns a list of all tables in the current schema.
   * <br/>
   * The types used are those returned by {@link #getTableTypes()}
   * @throws SQLException
   */
  public List<TableIdentifier> getTableList()
    throws SQLException
  {
    return getObjectList(null, getCurrentSchema(), tableTypesArray);
  }

  public List<TableIdentifier> getObjectList(String schema, String[] types)
    throws SQLException
  {
    if (schema == null) schema = this.getCurrentSchema();
    return getObjectList(null, schema, types);
  }

  public List<TableIdentifier> getTableList(String table, String schema)
    throws SQLException
  {
    return getObjectList(table, schema, tableTypesArray);
  }

  /**
   * Returns a list of objects from which a SELECT can be run.
   * <br/>
   * Typically these are tables, views and materialized views.
   *
   * @param namePattern      a pattern to search for object names
   * @param schemaOrCatalog  the schema or catalog pattern (if the DBMS does not support schemas)
   * @see #getObjects(java.lang.String, java.lang.String, java.lang.String, java.lang.String[])
   * @throws SQLException
   */
  public List<TableIdentifier> getSelectableObjectsList(String namePattern, String schemaOrCatalog)
    throws SQLException
  {
    return getSelectableObjectsList(namePattern, schemaOrCatalog, selectableTypes);
  }

  public List<TableIdentifier> getSelectableObjectsList(String namePattern, String schemaOrCatalog, String[] types)
    throws SQLException
  {
    if (getDbSettings().supportsSchemas())
    {
      return getObjectList(namePattern, null, schemaOrCatalog, types);
    }
    else if (getDbSettings().supportsCatalogs())
    {
      return getObjectList(namePattern, schemaOrCatalog, null, types);
    }
    return getObjectList(namePattern, null, null, types);
  }

  /**
   * Return a list of tables for the given schema
   * if the table name is null, all tables will be returned
   *
   * @param namePattern  the search pattern for the objects to be retrieved
   * @param schema       the schema or catalog pattern (if the DBMS does not support schemas)
   * @see #getObjects(java.lang.String, java.lang.String, java.lang.String, java.lang.String[])
   */
  public List<TableIdentifier> getObjectList(String namePattern, String schema, String[] types)
    throws SQLException
  {
    if (getDbSettings().supportsSchemas())
    {
      return getObjectList(namePattern, null, schema, types);
    }
    else if (getDbSettings().supportsCatalogs())
    {
      return getObjectList(namePattern, schema, null, types);
    }
    return getObjectList(namePattern, null, null, types);
  }

  public List<TableIdentifier> getObjectList(String namePattern, Namespace namespace, String[] types)
    throws SQLException
  {
    if (namespace == null)
    {
      namespace = Namespace.NULL_NSP;
    }
    return getObjectList(namePattern, namespace.getCatalog(), namespace.getSchema(), types);
  }

  /**
   * Return a list of tables for the given schema
   * if the table name is null, all tables will be returned
   *
   * @param namePattern     the (wildcard) name of a table
   * @param catalogPattern  the catalog for which to retrieve the objects (may be null)
   * @param schemaPattern   the schema for which to retrieve the objects (may be null)
   *
   * @see #getObjects(java.lang.String, java.lang.String, java.lang.String, java.lang.String[])
   */
  public List<TableIdentifier> getObjectList(String namePattern, String catalogPattern, String schemaPattern, String[] types)
    throws SQLException
  {
    ObjectListDataStore ds = getObjects(catalogPattern, schemaPattern, namePattern, types);
    int count = ds.getRowCount();
    List<TableIdentifier> tables = new ArrayList<>(count);
    for (int i=0; i < count; i++)
    {
      TableIdentifier tbl = ds.getTableIdentifier(i);
      tables.add(tbl);
    }
    return tables;
  }

  public List<DbObject> getDbObjectList(String namePattern, String catalogPattern, String schemaPattern, String[] types)
    throws SQLException
  {
    ObjectListDataStore ds = getObjects(catalogPattern, schemaPattern, namePattern, types);
    int count = ds.getRowCount();
    List<DbObject> objects = new ArrayList<>(count);
    for (int i=0; i < count; i++)
    {
      DbObject dbo = ds.getUserObject(i, DbObject.class);
      objects.add(dbo);
    }
    return objects;
  }

  public String getSchemaToUse(TableIdentifier tbl)
  {
    if (!getDbSettings().supportsSchemas()) return null;

    String schemaToUse = tbl.getRawSchema();
    String currentSchema = null;

    if (schemaToUse == null)
    {
      currentSchema = getCurrentSchema();
      schemaToUse = currentSchema;
    }

    if (ignoreSchema(schemaToUse, currentSchema)) return null;

    return StringUtil.trim(schemaToUse);
  }

  public String getCatalogToUse(TableIdentifier tbl)
  {
    if (!getDbSettings().supportsCatalogs()) return null;

    String catalogToUse = tbl.getRawCatalog();
    if (catalogToUse == null)
    {
      catalogToUse = definitionReader.getCatalogToUse(tbl);
    }

    if (ignoreCatalog(catalogToUse)) return null;

    return StringUtil.trim(catalogToUse);
  }

  /**
   * Return the current catalog for this connection.
   *
   * Wrapper function for CatalogInformationReader.getCurrentCatalog()
   *
   * @return The name of the current catalog or null if there is no current catalog
   * @see CatalogInformationReader#getCurrentCatalog()
   */
  public String getCurrentCatalog()
  {
    return catalogInfoReader.getCurrentCatalog();
  }

  public void clearCachedCatalog()
  {
    catalogInfoReader.clearCache();
  }

  /**
   *  Returns a list of all catalogs in the database.
   *  Some DBMS's do not support catalogs, in this case the method
   *  will return an empty List.
   * <br/>
   * The list of catalogs will not be filtered.
   */
  public List<String> getCatalogs()
  {
    return getCatalogInformation(null);
  }

  public List<String> getAllCatalogs()
  {
    if (DBID.Postgres.isDB(dbConnection))
    {
      return PostgresUtil.getAllDatabases(dbConnection);
    }
    return getCatalogInformation(null);
  }

  public boolean supportsCatalogLevelObjects()
  {
    return DBID.SQL_Server.isDB(dbConnection) && SqlServerUtil.supportsPartitioning(dbConnection);
  }

  /**
   * Return a filtered list of catalogs in the database.
   * <br/>
   * Some DBMS's do not support catalogs, in this case the method
   * will return an empty List.
   * The list is obtained by calling DatabaseMetaData.getCatalogs().
   * <br/>
   * If the filter is not null, all entries that are matched by the
   * filter are removed from the result.
   *
   * @param filter the ObjectNameFilter to apply
   * @return a list of available catalogs if supported by the database
   * @see ObjectNameFilter#isExcluded(java.lang.String)
   */
  public List<String> getCatalogInformation(ObjectNameFilter filter)
  {
    List<String> result = CollectionUtil.arrayList();

    boolean useColumnNames = dbSettings.useColumnNameForMetadata();
    ResultSet rs = null;
    try
    {
      long start = System.currentTimeMillis();
      rs = this.metaData.getCatalogs();
      while (rs.next())
      {
        String cat = useColumnNames ? rs.getString("TABLE_CAT") : rs.getString(1);
        if (StringUtil.isNotEmpty(cat))
        {
          result.add(cat);
        }
      }
      long duration = System.currentTimeMillis() - start;
      LogMgr.logDebug(new CallerInfo(){}, getConnId() + ": Retrieving " + result.size() +  " catalogs using getCatalogs() took: " + duration + "ms");
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, getConnId() + ": Error retrieving catalog information", e);
    }
    finally
    {
      JdbcUtils.closeResult(rs);
    }

    if (filter != null)
    {
      filter.applyFilter(result);
    }

    Collections.sort(result);
    return result;
  }

  /**
   * Returns the list of schemas as returned by DatabaseMetadata.getSchemas()
   * @return a list of schema names
   */
  public List<String> getSchemas()
  {
    return getSchemas(null);
  }

  public boolean supportsCatalogForGetSchemas()
  {
    if (DBID.SQL_Server.isDB(dbConnection))
    {
      // only the Microsoft driver supports this, but not the jTDS driver
      return SqlServerUtil.isMicrosoftDriver(dbConnection);
    }
    return getDbSettings().supportsCatalogForGetSchemas();
  }

  /**
   * Return a filtered list of schemas in the database.
   * <br/>
   * The list is obtained by calling DatabaseMetadata.getSchemas().
   * <br/>
   * If the filter is not null, all entries that are matched by the
   * filter are removed from the result.
   *
   * @param filter    the ObjectNameFilter to apply. May be null
   * @param catalog   the catalog for which the schemas should be retrieved. May be null
   *
   * @return a list of available schemas if supported by the database
   * @see ObjectNameFilter#isExcluded(java.lang.String)
   * @see ObjectNameFilter#applyFilter(java.util.Collection)
   * @see #supportsCatalogForGetSchemas()
   */
  public List<String> getSchemas(ObjectNameFilter filter)
  {
    return getSchemas(filter, null);
  }

  public List<String> getSchemas(ObjectNameFilter filter, String catalog)
  {
    List<String> result = new ArrayList<>();
    boolean applyFilter = filter != null;

    long start = System.currentTimeMillis();

    final CallerInfo ci = new CallerInfo(){};

    try
    {
      if (filter != null && filter.isRetrievalFilter() && CollectionUtil.isNonEmpty(filter.getFilterExpressions()))
      {
        for (String expression : filter.getFilterExpressions())
        {
          long filterStart = System.currentTimeMillis();
          expression = adjustSchemaNameCase(cleanupWildcards(expression));
          ResultSet rs = metaData.getSchemas(catalog, expression);
          int count = addSchemaResult(result, rs);
          long filterDuration = System.currentTimeMillis() - filterStart;
          LogMgr.logDebug(ci, getConnId() + ": Using schema filter expression " + expression + " as a retrieval parameter returned " + count + " schemas (" + filterDuration + "ms)");
        }
        applyFilter = false;
      }
      else
      {
        boolean retrieved = false;
        ResultSet rs = null;
        if (getDbSettings().supportsCatalogForGetSchemas())
        {
          try
          {
            rs = this.metaData.getSchemas(catalog, null);
            retrieved = true;
          }
          catch (Throwable th)
          {
            LogMgr.logError(ci, "Error calling getSchema(String, String) with catalog: " + catalog, th);
            getDbSettings().setSupportsCatalogForGetSchemas(false);
            retrieved = false;
          }
        }

        if (!retrieved)
        {
          rs = this.metaData.getSchemas();
        }
        addSchemaResult(result, rs);
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(ci, getConnId() + ": Error retrieving schemas: " + e.getMessage(), e);
    }

    long duration = System.currentTimeMillis() - start;
    LogMgr.logDebug(ci, getConnId() + ": Retrieving " + result.size() + " schemas" + (catalog == null ? "" : " for catalog: " + catalog) + " took " + duration + "ms");

    // This is mainly for Oracle because the Oracle driver does not return the "PUBLIC" schema
    // which is - strictly speaking - correct as there is no user PUBLIC in the database.
    // but to view public synonyms we need the entry for the DbExplorer
    List<String> additionalSchemas = dbSettings.getSchemasToAdd();
    if (additionalSchemas != null)
    {
      result.addAll(additionalSchemas);
    }

    if (filter != null && applyFilter)
    {
      filter.applyFilter(result);
    }

    Collections.sort(result);
    return result;
  }

  private int addSchemaResult(List<String> result, ResultSet rs)
    throws SQLException
  {
    boolean useColumnNames = dbSettings.useColumnNameForMetadata();
    int count = 0;
    try
    {
      while (rs.next())
      {
        count++;
        String schema = useColumnNames ? rs.getString("TABLE_SCHEM") : rs.getString(1);
        if (StringUtil.isNotEmpty(schema))
        {
          result.add(schema);
        }
      }
    }
    finally
    {
      JdbcUtils.closeResult(rs);
    }
    return count;
  }

  private boolean isIndexType(String type)
  {
    if (type == null) return false;
    return (type.contains("INDEX"));
  }

  private synchronized Collection<String> retrieveTableTypes()
  {
    Collection<String> configuredTypes = dbSettings.getListProperty("tabletypes");

    if (configuredTypes.size() > 0)
    {
      return configuredTypes;
    }
    return retrieveObjectTypes();
  }

  /**
   * Retrieve the "native" object types supported by the JDBC driver.
   *
   * @see DatabaseMetaData#getTableTypes()
   */
  private synchronized Collection<String> retrieveObjectTypes()
  {
    if (objectTypesFromDriver != null) return objectTypesFromDriver;

    boolean ignoreIndexTypes = getDbSettings().getBoolProperty("metadata.tabletypes.ignore.index", true);
    boolean useDefaults = getDbSettings().getBoolProperty("metadata.tabletypes.use.defaults", true);

    ObjectTypeNameMapper mapper = new ObjectTypeNameMapper(dbSettings);

    Set<String> types = CollectionUtil.caseInsensitiveSet();
    ResultSet rs = null;
    String ignoredTypes = "";
    try
    {
      rs = this.metaData.getTableTypes();
      while (rs != null && rs.next())
      {
        String type = rs.getString(1);
        if (type == null) continue;

        // for some reason oracle sometimes returns the types padded to a fixed length.
        // I'm assuming it doesn't hurt for other DBMS to trim the returned value always
        type = type.trim();


        // Some drivers return index "types" as well when calling getTableTypes()
        if (ignoreIndexTypes && isIndexType(type))
        {
          if (!ignoredTypes.isEmpty()) ignoredTypes += ",";
          ignoredTypes += type;
          continue;
        }
        types.add(type.toUpperCase());
        String sqlTypeName = mapper.mapInternalNameToSQL(type);
        if (StringUtil.stringsAreNotEqual(type.toUpperCase(), sqlTypeName.toUpperCase()))
        {
          types.add(sqlTypeName.toUpperCase());
        }
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, getConnId() + ": Error retrieving table types.", e);
    }
    finally
    {
      JdbcUtils.closeResult(rs);
    }

    if (!ignoredTypes.isEmpty())
    {
      LogMgr.logDebug(new CallerInfo(){}, getConnId() + ": Ignoring \"table\" types: " + ignoredTypes);
    }

    if (types.isEmpty() && useDefaults)
    {
      types = CollectionUtil.caseInsensitiveSet("TABLE", "VIEW");
      LogMgr.logWarning(new CallerInfo(){}, getConnId() + ": The driver did not return any table types using getTableTypes(). Using default values: " + types);
    }
    else
    {
      LogMgr.logInfo(new CallerInfo(){}, getConnId() + ": Table types returned by the JDBC driver: " + types);
    }

    objectTypesFromDriver = Collections.unmodifiableSet(types);

    return objectTypesFromDriver;
  }

  /**
   * Return a list of object types available in the database.
   *
   * This includes the native types as reported by the JDBC driver and all
   * types handled by a registered extender.
   *
   * e.g. TABLE, SYSTEM TABLE, ...
   *
   * @see #retrieveTableTypes()
   * @see ObjectListExtender#supportedTypes()
   * @see SynonymReader#getSynonymTypeName()
   * @see SequenceReader#getSequenceTypeName()
   */
  public Collection<String> getObjectTypes()
  {
    Set<String> result = CollectionUtil.caseInsensitiveSet();
    result.addAll(retrieveObjectTypes());

    List<String> addTypes = dbSettings.getListProperty("additional.objecttypes");
    result.addAll(addTypes);

    if (supportsSynonyms())
    {
      result.add(getSynonymReader().getSynonymTypeName());
    }

    SequenceReader reader = getSequenceReader();
    if (reader != null)
    {
      result.add(reader.getSequenceTypeName());
    }

    for (ObjectListExtender extender : extenders)
    {
      if (!extender.isDerivedType())
      {
        result.addAll(extender.supportedTypes());
      }
    }
    return result;
  }


  public String getSchemaTerm()
  {
    return this.schemaTerm;
  }

  public String getCatalogTerm()
  {
    return this.catalogTerm;
  }

  public SequenceReader getSequenceReader()
  {
    return ReaderFactory.getSequenceReader(this.dbConnection);
  }

  /**
   * Checks if the given type is a regular table type or an extended table type.
   *
   * @param type the type to check
   * @return true if it's a table of some kind.
   * @see #isTableType(java.lang.String)
   */
  public boolean isExtendedTableType(String type)
  {
    if (isTableType(type)) return true;
    List<String> types = getAdditionalTableTypes();
    return types.contains(type);
  }

  public List<String> getAdditionalTableTypes()
  {
    return dbSettings.getListProperty("additional.tabletypes");
  }

  public boolean isViewOrTable(String type)
  {
    return isTableType(type) || isExtendedTableType(type) || isViewType(type);
  }

  public boolean isViewType(String type)
  {
    return getDbSettings().isViewType(type);
  }

  public boolean isTableType(String type)
  {
    if (type == null) return false;
    return tableTypesList.contains(type.trim());
  }

  /**
   * Checks if the current DBMS supports synonyms.
   * @return true if the synonym support is available (basically if synonymReader != null)
   */
  public boolean supportsSynonyms()
  {
    return getSynonymReader() != null;
  }

  /**
   *  Return the underlying table of a synonym.
   * @param synonym the synonym definition
   *
   * @return the table to which the synonym points or null if the passed
   *         name does not reference a synonym or if the DBMS does not support synonyms
   * @see #getSynonymTable(java.lang.String, java.lang.String, java.lang.String)
   */
  public TableIdentifier getSynonymTable(TableIdentifier synonym)
  {
    TableIdentifier tbl = synonym.createCopy();
    tbl.adjustCase(this.dbConnection);
    return getSynonymTable(tbl.getCatalog(), tbl.getSchema(), tbl.getTableName());
  }

  /**
   * Return the underlying table of a synonym.
   *
   * @param schema the schema of the synonym
   * @param synonym the name of the synonym
   *
   * @return the table to which the synonym points or null if the passed
   *         name does not reference a synonym or if the DBMS does not support synonyms
   * @see #getSynonymTable(workbench.db.TableIdentifier)
   */
  public TableIdentifier getSynonymTable(String catalog, String schema, String synonym)
  {
    SynonymReader reader = getSynonymReader();
    if (reader == null) return null;
    TableIdentifier id = null;
    try
    {
      id = reader.getSynonymTable(this.dbConnection, catalog, schema, synonym);
      if (id != null && id.getType() == null)
      {
        String type = getObjectType(id);
        id.setType(type);
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not retrieve table for synonym", e);
    }
    return id;
  }

  public SynonymReader getSynonymReader()
  {
    return ReaderFactory.getSynonymReader(dbConnection);
  }
}
