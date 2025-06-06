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

import java.io.Serializable;
import java.sql.SQLException;
import java.util.List;

import workbench.resource.ResourceMgr;

import workbench.db.objectcache.DbObjectCacheFactory;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * A class that represents a table (or view) in the database.
 *
 * @author  Thomas Kellerer
 */
public class TableIdentifier
  implements DbObject, Comparable<TableIdentifier>, Serializable
{
  private static final long serialVersionUID = DbObjectCacheFactory.CACHE_VERSION_UID;

  private String tablename;
  private String systemTablename;
  private String schema;
  private String catalog;
  private String server; // for SQL Server syntax
  private String owner;
  private boolean isNewTable;
  private boolean tableWasQuoted;
  private boolean serverWasQuoted;
  private boolean catalogWasQuoted;
  private boolean schemaWasQuoted;
  private PkDefinition primaryKey;
  private String type;
  private boolean neverAdjustCase;
  private boolean preserveQuotes;
  private boolean showOnlyTableName;
  private String tableComment;
  private boolean commentWasInitialized;
  private boolean retrieveFkSource;
  private boolean useInlinePK;
  private boolean useInlineFK;
  private boolean useTableNameOnlyInExpression;
  private boolean pkInitialized;
  private boolean isPartitioned;
  private String quoteChar = QuoteHandler.STANDARD_QUOTE_CHARACTER;

  private boolean cacheSource = false;
  private CharSequence cachedSourceCode;

  // for Synonyms
  private TableIdentifier realTable;

  private ObjectSourceOptions sourceOptions = new ObjectSourceOptions();

  /**
   * DBMS specific tablespace options
   */
  private String tableSpace;

  public TableIdentifier(String aName)
  {
    this.isNewTable = false;
    this.parseTableIdentifier(aName);
  }

  public TableIdentifier(String aName, char catalogSeparator, char schemaSeparator)
  {
    this.isNewTable = false;
    this.parseTableIdentifier(aName, catalogSeparator, schemaSeparator, true, true);
  }

  public TableIdentifier(String aName, char catalogSeparator, char schemaSeparator, boolean supportsCatalogs, boolean supportsSchemas)
  {
    this.isNewTable = false;
    this.parseTableIdentifier(aName, catalogSeparator, schemaSeparator, supportsCatalogs, supportsSchemas);
  }

  public TableIdentifier(String aName, WbConnection conn)
  {
    this.isNewTable = false;
    DbSettings settings = (conn == null ? null : conn.getDbSettings());
    boolean supportsCatalogs = settings == null ? true : settings.supportsCatalogs();
    boolean supportsSchemas = settings == null ? true : settings.supportsSchemas();
    this.parseTableIdentifier(aName, SqlUtil.getCatalogSeparator(conn), SqlUtil.getSchemaSeparator(conn), supportsCatalogs, supportsSchemas);
    this.adjustCase(conn);
  }

  /**
   * Initialize a TableIdentifier for a new (to be defined) table
   * This is mainly used by the {@link workbench.db.datacopy.DataCopier}
   * to flag the target table to be created on the fly
   */
  public TableIdentifier()
  {
    this.schema = null;
    this.catalog = null;
    this.tablename = null;
    this.isNewTable = true;
  }

  public TableIdentifier(String aSchema, String aTable)
  {
    this(null, aSchema, aTable);
  }

  public TableIdentifier(String aCatalog, String aSchema, String aTable)
  {
    this.setTablename(aTable);
    this.setCatalog(aCatalog);
    this.setSchema(aSchema);
  }

  public boolean isPartitioned()
  {
    return isPartitioned;
  }

  public void setIsPartitioned(boolean flag)
  {
    this.isPartitioned = flag;
  }

  /**
   * Returns the internal system name of the table.
   * Mainly used for DB2
   * @return the internal name
   * @see #setSystemTablename(java.lang.String)
   */
  public String getSystemTablename()
  {
    return systemTablename;
  }

  public void setSystemTablename(String systemTablename)
  {
    this.systemTablename = systemTablename;
  }

  public boolean getUseNameOnly()
  {
    return useTableNameOnlyInExpression;
  }

  public void setUseNameOnly(boolean flag)
  {
    this.useTableNameOnlyInExpression = flag;
  }

  public TableIdentifier getRealTable()
  {
    return realTable;
  }

  public void setRealTable(TableIdentifier targetTable)
  {
    this.realTable = targetTable != null ? targetTable.createCopy() : null;
  }

  public String getOwner()
  {
    return owner;
  }

  public void setOwner(String owner)
  {
    this.owner = owner;
  }

  /**
   * Return the tablespace used for this table (if applicable)
   */
  public String getTablespace()
  {
    return tableSpace;
  }

  public void setTablespace(String tableSpaceName)
  {
    this.tableSpace = tableSpaceName;
  }

  /**
   * Define the source options to be used.
   *
   * @param options  the new options. If null, the call is ignored
   */
  public void setSourceOptions(ObjectSourceOptions options)
  {
    if (options != null)
    {
      this.sourceOptions = options;
    }
  }

  /**
   * Returns the source options to build the table's SQL
   *
   * @return the options. Never null
   */
  public ObjectSourceOptions getSourceOptions()
  {
    return this.sourceOptions;
  }

  public boolean getUseInlineFK()
  {
    return useInlineFK;
  }

  public void setUseInlineFK(boolean flag)
  {
    this.useInlineFK = flag;
  }

  public boolean getUseInlinePK()
  {
    return useInlinePK;
  }

  public void setUseInlinePK(boolean flag)
  {
    this.useInlinePK = flag;
  }

  public boolean isPkInitialized()
  {
    return pkInitialized;
  }

  public void setPkInitialized(boolean flag)
  {
    pkInitialized = flag;
  }

  public void setCacheSource(boolean flag)
  {
    cacheSource = flag;
  }

  @Override
  public void setComment(String comment)
  {
    this.commentWasInitialized = true;
    this.tableComment = comment;
  }

  @Override
  public String getComment()
  {
    return tableComment;
  }

  public boolean wasQuoted()
  {
    return tableWasQuoted || schemaWasQuoted || catalogWasQuoted;
  }

  public boolean commentIsDefined()
  {
    return commentWasInitialized;
  }

  @Override
  public String getDropStatement(WbConnection con, boolean cascade)
  {
    return null;
  }

  @Override
  public String getObjectNameForDrop(WbConnection con)
  {
    return getTableExpression(con);
  }

  @Override
  public String getObjectName(WbConnection conn)
  {
    if (conn == null) return SqlUtil.quoteObjectname(tablename, tableWasQuoted);
    return conn.getMetadata().quoteObjectname(this.tablename);
  }

  @Override
  public String getFullyQualifiedName(WbConnection con)
  {
    return SqlUtil.fullyQualifiedName(con, this);
  }

  @Override
  public String getObjectExpression(WbConnection conn)
  {
    return getTableExpression(conn);
  }

  @Override
  public String getObjectType()
  {
    if (type == null) return "TABLE";
    return type.toUpperCase();
  }

  @Override
  public String getObjectName()
  {
    return getTableName();
  }

  public void setPreserveQuotes(boolean flag)
  {
    this.preserveQuotes = flag;
  }

  public boolean getNeverAdjustCase()
  {
    return this.neverAdjustCase;
  }

  public void setNeverAdjustCase(boolean flag)
  {
    this.neverAdjustCase = flag;
  }

  public void checkQuotesNeeded(WbConnection con)
  {
    if (con == null) return;
    DbMetadata meta = con.getMetadata();
    if (meta == null) return;
    this.quoteChar = meta.getIdentifierQuoteCharacter();
    if (this.quoteChar == null)
    {
      this.quoteChar = QuoteHandler.STANDARD_HANDLER.getIdentifierQuoteCharacter();
    }
    this.schemaWasQuoted = !meta.isDefaultCase(this.schema);
    this.catalogWasQuoted = !meta.isDefaultCase(this.catalog);
    this.tableWasQuoted = !meta.isDefaultCase(this.tablename);
    this.preserveQuotes = (this.schemaWasQuoted || this.catalogWasQuoted || this.tableWasQuoted );
  }

  public TableIdentifier createCopy()
  {
    TableIdentifier copy = new TableIdentifier();
    copy.isNewTable = this.isNewTable;
    copy.pkInitialized = this.pkInitialized;
    copy.primaryKey = this.primaryKey == null ? null : this.primaryKey.createCopy();
    copy.schema = this.schema;
    copy.tablename = this.tablename;
    copy.catalog = this.catalog;
    copy.server = this.server;
    copy.neverAdjustCase = this.neverAdjustCase;
    copy.serverWasQuoted = this.serverWasQuoted;
    copy.tableWasQuoted = this.tableWasQuoted;
    copy.catalogWasQuoted = this.catalogWasQuoted;
    copy.schemaWasQuoted = this.schemaWasQuoted;
    copy.showOnlyTableName = this.showOnlyTableName;
    copy.preserveQuotes = this.preserveQuotes;
    copy.type = this.type;
    copy.retrieveFkSource = this.retrieveFkSource;
    copy.commentWasInitialized = this.commentWasInitialized;
    copy.tableComment = this.tableComment;
    copy.sourceOptions = this.sourceOptions == null ? null : sourceOptions.createCopy();
    copy.useInlinePK = this.useInlinePK;
    copy.useInlineFK = this.useInlineFK;
    copy.owner = this.owner;
    copy.realTable = this.realTable == null ? null : realTable.createCopy();
    copy.useTableNameOnlyInExpression = this.useTableNameOnlyInExpression;
    copy.quoteChar = this.quoteChar;
    return copy;
  }

  public String getTableExpression()
  {
    return buildTableExpression(QuoteHandler.STANDARD_HANDLER, null, '.', '.');
  }

  public String getTableExpression(char catalogSeparator, char schemaSeparator)
  {
    return this.buildTableExpression(QuoteHandler.STANDARD_HANDLER, null, catalogSeparator, schemaSeparator);
  }

  @Override
  public int hashCode()
  {
    return getTableExpression().hashCode();
  }

  public String getTableExpression(WbConnection conn)
  {
    char catalogSeparator = SqlUtil.getCatalogSeparator(conn);
    char schemaSeparator = SqlUtil.getSchemaSeparator(conn);
    QuoteHandler quoter = conn == null ? QuoteHandler.STANDARD_HANDLER : conn.getMetadata();
    return this.buildTableExpression(quoter, conn, catalogSeparator, schemaSeparator);
  }

  protected String buildTableExpression(QuoteHandler quoter, WbConnection conn, char catalogSeparator, char schemaSeparator)
  {
    if (this.isNewTable && this.tablename == null)
    {
      return ResourceMgr.getString("TxtNewTableIdentifier");
    }

    StringBuilder result = new StringBuilder(30);
    if (this.server != null)
    {
      result.append(SqlUtil.quoteObjectname(this.server, false));
      result.append('.');
    }
    if (conn == null)
    {
      if (this.catalog != null && !useTableNameOnlyInExpression)
      {
        result.append(quoter.quoteObjectname(this.catalog, preserveQuotes && catalogWasQuoted));
        result.append(catalogSeparator);
      }
      if (this.schema != null && !useTableNameOnlyInExpression)
      {
        result.append(quoter.quoteObjectname(this.schema, preserveQuotes && schemaWasQuoted));
        result.append(schemaSeparator);
      }
      result.append(quoter.quoteObjectname(this.tablename, preserveQuotes && tableWasQuoted));
    }
    else
    {
      DbMetadata meta = conn.getMetadata();
      this.adjustCase(conn);
      String catalogToUse = getCatalogToUse(conn);
      boolean hasCatalog = false;
      if (StringUtil.isNotBlank(catalogToUse))
      {
        hasCatalog = true;
        result.append(meta.quoteObjectname(catalogToUse, preserveQuotes && catalogWasQuoted));
        result.append(catalogSeparator);
      }

      String schemaToUse = getSchemaToUse(conn);

      // if a catalog is present we always need the schema as the combination catalog.tablename is not valid
      // (this is mainly needed for SQL Server)
      if (hasCatalog && StringUtil.isBlank(schemaToUse))
      {
        schemaToUse = conn.getCurrentSchema();
      }

      if (StringUtil.isNotBlank(schemaToUse))
      {
        result.append(meta.quoteObjectname(schemaToUse, preserveQuotes && schemaWasQuoted));
        result.append(schemaSeparator);
      }
      result.append(meta.quoteObjectname(this.tablename, preserveQuotes && tableWasQuoted));
    }
    return result.toString();
  }

  public String getSchemaToUse(WbConnection conn)
  {
    if (useTableNameOnlyInExpression) return null;
    if (conn == null) return null;

    DbMetadata meta = conn.getMetadata();
    if (meta.needSchemaInDML(this))
    {
      return meta.getSchemaToUse(this);
    }
    return null;
  }

  public String getCatalogToUse(WbConnection conn)
  {
    if (useTableNameOnlyInExpression) return null;
    if (conn == null) return null;

    DbMetadata meta = conn.getMetadata();
    if (meta.needCatalogInDML(this))
    {
      return meta.getCatalogToUse(this);
    }
    return null;
  }

  public void adjustCatalogAndSchema(WbConnection conn)
  {
    if (conn == null) return;

    if (catalog == null && conn.getDbSettings().supportsCatalogs())
    {
      setCatalog(conn.getCurrentCatalog());
    }
    if (schema == null && conn.getDbSettings().supportsSchemas())
    {
      setSchema(conn.getCurrentSchema());
    }
  }

  public final void adjustCase(WbConnection conn)
  {
    if (this.neverAdjustCase) return;
    if (conn == null) return;
    DbMetadata meta = conn.getMetadata();

    if (this.tablename != null && !tableWasQuoted) this.tablename = meta.adjustObjectnameCase(this.tablename);
    if (this.schema != null && !schemaWasQuoted) this.schema = meta.adjustSchemaNameCase(this.schema);
    if (this.catalog != null && !catalogWasQuoted) this.catalog = meta.adjustObjectnameCase(this.catalog);
  }

  /**
   * Return the fully qualified name of the table
   * (including catalog and schema) but not quoted
   * even if it needed quotes
   */
  public String getQualifiedName()
  {
    StringBuilder result = new StringBuilder(32);
    if (this.server != null)
    {
      result.append(server);
      result.append('.');
    }
    if (catalog != null)
    {
      result.append(catalog);
      result.append('.');
    }
    if (schema != null)
    {
      result.append(schema);
      result.append('.');
    }
    result.append(this.tablename);
    return result.toString();
  }

  public String getRawCatalog()
  {
    return this.catalog;
  }

  public String getRawTableName()
  {
    return this.tablename;
  }

  public String getRawSchema()
  {
    return this.schema;
  }

  public String getTableName()
  {
    if (tablename == null) return null;
    if (!tableWasQuoted || !preserveQuotes) return this.tablename;

    StringBuilder result = new StringBuilder(tablename.length() + 2);
    result.append(this.quoteChar);
    result.append(tablename);
    result.append(this.quoteChar);
    return result.toString();
  }

  /**
   * Check for non-standard quote characters in the table, schema or catalog name
   * in order to support DBMS who don't even care about the most simple standards.
   *
   * This will only re-add the correct quotes later, if getTableExpression(WbConnection) is used!
   *
   * @param meta the DbMetadata object used for checking if a name component is quoted
   * @see workbench.db.DbMetadata#isQuoted(java.lang.String)
   */
  public void checkIsQuoted(DbMetadata meta)
  {
    if (meta.isQuoted(server))
    {
      this.serverWasQuoted = true;
      this.server = server.substring(1, server.length() - 1);
    }
    if (meta.isQuoted(this.tablename))
    {
      this.tableWasQuoted = true;
      this.tablename = this.tablename.substring(1, tablename.length() - 1);
    }
    if (meta.isQuoted(schema))
    {
      this.schemaWasQuoted = true;
      this.schema = this.schema.substring(1, schema.length() - 1);
    }
    if (meta.isQuoted(catalog))
    {
      this.catalogWasQuoted = true;
      this.catalog= this.catalog.substring(1, catalog.length() - 1);
    }
  }

  public final void parseTableIdentifier(String aTable)
  {
    parseTableIdentifier(aTable, '.', '.', true, true);
  }

  /**
   * Parse a table identifier into the different parts.
   *
   * If a name with two elements (e.g. foo.bar) is passed, the flags for schema/catalogs determine what the
   * first part is used for. If both are supported or only schemas, the first part is a schema. If only catalogs
   * are supported the first part is a catalog.
   *
   * @param tableId  the identifier (name) e.g. used by the user
   * @param catalogSeparator  the catalog separator
   * @param schemaSeparator   the schema separator
   * @param supportsCatalogs  flag if catalogs are supported
   * @param supportsSchemas   flag if schemas are supported
   */
  public final void parseTableIdentifier(String tableId, char catalogSeparator, char schemaSeparator, boolean supportsCatalogs, boolean supportsSchemas)
  {
    if (!this.isNewTable && (StringUtil.isBlank(tableId)))
    {
      throw new IllegalArgumentException("Table name may not be empty");
    }

    if (tableId == null)
    {
      // this is a "new table"
      this.tablename = null;
      this.schema = null;
      this.catalog = null;
      return;
    }

    ObjectExpressionParser expression = new ObjectExpressionParser(tableId, catalogSeparator, schemaSeparator, supportsCatalogs, supportsSchemas);
    setServerPart(expression.getServer());
    setCatalog(expression.getCatalog());
    setSchema(expression.getSchema());
    setTablename(expression.getName());
  }

  /**
   * For a table specified using SQL Server's extended syntax
   * that allows 4 elements, return the first element. Usually this is the
   * name of the linked server.
   *
   * @return the name of the server part if specified, null otherwise
   */
  public String getServerPart()
  {
    return server;
  }

  public void setServerPart(String name)
  {
    if (StringUtil.isBlank(name))
    {
      server = null;
    }
    else
    {
      server = name.trim();
    }
  }

  @Override
  public void setName(String name)
  {
    setTablename(name);
  }

  private void setTablename(String name)
  {
    if (name == null) return;
    tableWasQuoted = SqlUtil.isQuotedIdentifier(name.trim());
    tablename = SqlUtil.removeObjectQuotes(name).trim();
  }

  @Override
  public String getSchema()
  {
    if (schema == null) return null;
    if (!schemaWasQuoted || !preserveQuotes) return schema;

    StringBuilder result = new StringBuilder(schema.length() + 2);
    result.append(this.quoteChar);
    result.append(schema);
    result.append(this.quoteChar);
    return result.toString();
  }

  @Override
  public final void setSchema(String aSchema)
  {
    if (StringUtil.isBlank(aSchema))
    {
      this.schema = null;
    }
    else
    {
      schemaWasQuoted = SqlUtil.isQuotedIdentifier(aSchema);
      schema = SqlUtil.removeObjectQuotes(aSchema);
    }
  }

  @Override
  public String getCatalog()
  {
    if (catalog == null) return null;
    if (!catalogWasQuoted || !preserveQuotes) return this.catalog;

    StringBuilder result = new StringBuilder(catalog.length() + 2);
    result.append(this.quoteChar);
    result.append(catalog);
    result.append(this.quoteChar);
    return result.toString();
  }

  @Override
  public final void setCatalog(String aCatalog)
  {
    if (this.isNewTable) return;

    if (StringUtil.isBlank(aCatalog))
    {
      this.catalog = null;
    }
    else
    {
      catalogWasQuoted = SqlUtil.isQuotedIdentifier(aCatalog);
      catalog = SqlUtil.removeObjectQuotes(aCatalog);
    }
  }

  @Override
  public String toString()
  {
    if (this.isNewTable)
    {
      if (this.tablename == null)
      {
        return this.getTableExpression();
      }
      else
      {
        return "(+) " + this.tablename;
      }
    }
    else if (this.showOnlyTableName)
    {
      return this.getTableName();
    }
    else
    {
      return this.getTableExpression();
    }
  }

  public boolean isNewTable()
  {
    return this.isNewTable;
  }

  public void setNewTable(boolean flag)
  {
    this.isNewTable = flag;
  }

  public void setShowTablenameOnly(boolean flag)
  {
    this.showOnlyTableName = flag;
  }

  @Override
  public int compareTo(TableIdentifier other)
  {
    return this.getTableExpression().compareTo(other.getTableExpression());
  }

  @Override
  public boolean equals(Object other)
  {
    if (other instanceof TableIdentifier)
    {
      boolean result;
      TableIdentifier t = (TableIdentifier)other;
      if (this.isNewTable && t.isNewTable)
      {
        result = true;
      }
      else if (this.isNewTable || t.isNewTable)
      {
        result = false;
      }
      else
      {
        result = this.getTableExpression().equals(t.getTableExpression());
      }
      return result;
    }
    return false;
  }

  /**
   * Compare this TableIdentifier to another.
   *
   * The schema and catalog fields are only compared if both identifiers have them.
   *
   * This is different to the equals() method, which returns false if one TableIdentifier has
   * a schema and/or catalog and the other doesn't
   *
   * @return true if both tables have the same name and/or schema/catalog
   */
  public boolean compareNames(TableIdentifier other)
  {
    // if both identifiers have neverAdjustCase set, this means
    // the names were retrieved directly through the JDBC driver.
    // In that case the comparison should be done in case-senstive
    // to deal with situations where there is e.g. a table "PERSON" and a table "Person".
    boolean ignoreCase = !(this.neverAdjustCase && other.neverAdjustCase);

    return compareNames(this, other, ignoreCase);
  }

  public static boolean compareNames(TableIdentifier one, TableIdentifier other, boolean ignoreCase)
  {
    boolean result;
    if (one == null || other == null)
    {
      return false;
    }

    if (one.isNewTable() && other.isNewTable())
    {
      return true;
    }

    if (one.isNewTable() || other.isNewTable())
    {
      return false;
    }

    result = StringUtil.equalStringOrEmpty(one.tablename, other.tablename, ignoreCase);

    if (result && one.schema != null && other.schema != null)
    {
      result = StringUtil.equalStringOrEmpty(one.schema, other.schema, ignoreCase);
    }

    if (result && one.catalog != null && other.catalog != null)
    {
      result = StringUtil.equalStringOrEmpty(one.catalog, other.catalog, ignoreCase);
    }

    return result;
  }

  public String getPrimaryKeyName()
  {
    if (this.primaryKey == null) return null;
    return this.primaryKey.getPkName();
  }

  public PkDefinition getPrimaryKey()
  {
    return this.primaryKey;
  }

  public void setPrimaryKey(PkDefinition pk)
  {
    this.primaryKey = pk;
    this.pkInitialized = true;
  }

  public String getType()
  {
    return type;
  }

  public void setType(String type)
  {
    this.type = type;
  }

  @Override
  public CharSequence getSource(WbConnection con)
    throws SQLException
  {
    return getSource(con, retrieveFkSource, con.getDbSettings().getGenerateTableGrants());
  }

  public CharSequence getSource(WbConnection con, boolean includeFk, boolean includeGrants)
    throws SQLException
  {
    if (cacheSource && cachedSourceCode != null)
    {
      return cachedSourceCode;
    }
    CharSequence source = null;
    DbMetadata meta = con.getMetadata();
    if (meta.isExtendedObject(this))
    {
      return meta.getObjectSource(this);
    }
    else if (DbMetadata.MVIEW_NAME.equalsIgnoreCase(type))
    {
      source = meta.getViewReader().getExtendedViewSource(new TableDefinition(this, null), DropType.none, false);
    }
    else if (meta.isSynonym(this))
    {
      SynonymDDLHandler synHandler = new SynonymDDLHandler();
      source = synHandler.getSynonymSource(con, this, false, DropType.none);
    }
    else if ("VIEW".equalsIgnoreCase(type))
    {
      source = meta.getViewReader().getExtendedViewSource(new TableDefinition(this, null), DropType.none, false);
    }
    else if (con.getMetadata().isSequenceType(type))
    {
      SequenceReader reader = meta.getSequenceReader();
      if (reader != null)
      {
        source = reader.getSequenceSource(getCatalog(), getSchema(), getTableName(), new GenerationOptions(true, includeGrants));
      }
    }
    else
    {
      TableSourceBuilder builder = TableSourceBuilderFactory.getBuilder(con);
      source = builder.getTableSource(this, DropType.none, includeFk, includeGrants);
    }
    if (cacheSource)
    {
      cachedSourceCode = source;
    }
    return source;
  }

  /**
   * Controls if getSource() will return the source for the FK constraints as well.
   *
   * @param flag true, getSource() will include FK source, false: FK source will not be included
   */
  public void setRetrieveFkSource(boolean flag)
  {
    retrieveFkSource = flag;
  }

  public static TableIdentifier findTableByName(List<TableIdentifier> tables, String toFind)
  {
    return findTableByName(tables, new TableIdentifier(toFind));
  }

  public static TableIdentifier findTableByName(List<TableIdentifier> tables, TableIdentifier toFind)
  {
    if (tables == null) return null;

    for (TableIdentifier table : tables)
    {
      if (table.getTableName().equalsIgnoreCase(toFind.getTableName())) return table;
    }
    return null;
  }

  public static TableIdentifier findTableByNameAndSchema(List<TableIdentifier> tables, TableIdentifier toFind)
  {
    if (tables == null) return null;
    String nameToFind = getQualifiedName(toFind);

    for (TableIdentifier table : tables)
    {
      String name = getQualifiedName(table);
      if (name.equalsIgnoreCase(nameToFind)) return table;
    }
    return null;
  }

  private static String getQualifiedName(TableIdentifier table)
  {
    String name = table.getTableName();
    String schema = table.getSchema();
    if (StringUtil.isEmpty(schema) && StringUtil.isNotEmpty(table.getCatalog()))
    {
      schema = table.getCatalog();
    }
    if (StringUtil.isEmpty(schema)) return name;
    return schema + "." + name;
  }

  public static boolean tablesAreEqual(TableIdentifier one, TableIdentifier other, WbConnection con)
  {
    if (one == null || other == null) return false;
    if (con == null)
    {
      return one.equals(other);
    }

    TableIdentifier tbl1 = one.createCopy();
    tbl1.adjustCatalogAndSchema(con);

    TableIdentifier tbl2 = other.createCopy();
    tbl2.adjustCatalogAndSchema(con);

    String expr1 = tbl1.getTableExpression(con);
    String expr2 = tbl2.getTableExpression(con);
    return expr1.equalsIgnoreCase(expr2);
  }

  @Override
  public boolean supportsGetSource()
  {
    return true;
  }

}
