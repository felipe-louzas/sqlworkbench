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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.DbExplorerSettings;
import workbench.resource.Settings;

import workbench.db.sqltemplates.ColumnDefinitionTemplate;
import workbench.db.sqltemplates.ConstraintNameTester;
import workbench.db.sqltemplates.FkTemplate;
import workbench.db.sqltemplates.PkTemplate;
import workbench.db.sqltemplates.TemplateHandler;

import workbench.sql.formatter.SqlFormatter;
import workbench.sql.formatter.SqlFormatterFactory;

import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * Re-create the source SQL for a given TableIdentifier.
 *
 * This class should not be instantiated directly. Use
 * TableSourceBuilderFactory.getBuilder() instead
 *
 * @author Thomas Kellerer
 * @see TableSourceBuilderFactory#getBuilder(workbench.db.WbConnection)
 */
public class TableSourceBuilder
{
  public static final String COL_INDENT = "   ";
  public static final String SCHEMA_PLACEHOLDER = "%schema%";
  public static final String CATALOG_PLACEHOLDER = "%catalog%";
  public static final String NAME_PLACEHOLDER = "%name%";

  protected WbConnection dbConnection;
  private final ConstraintNameTester nameTester;
  protected boolean includePartitions = true;
  protected Boolean createInlineFK;

  /**
   * This class should not be instantiated directly.
   *
   * Use TableSourceBuilderFactory.getBuilder() instead.
   *
   * @param con the connection to be used
   * @see TableSourceBuilderFactory#getBuilder(workbench.db.WbConnection)
   */
  protected TableSourceBuilder(WbConnection con)
  {
    dbConnection = con;
    nameTester = new ConstraintNameTester(con.getDbId());
  }

  protected ViewReader getViewReader()
  {
    return dbConnection.getMetadata().getViewReader();
  }

  protected IndexReader getIndexReader()
  {
    return dbConnection.getMetadata().getIndexReader();
  }

  public void setIncludePartitions(boolean flag)
  {
    includePartitions = flag;
  }

  /**
   * Return the SQL statement to re-create the given table. (in the dialect for the
   * current DBMS)
   *
   * @return the SQL statement to create the given table.
   * @param table the table for which the source should be retrieved
   * @param includeDrop If true, a DROP TABLE statement will be included in the generated SQL script.
   * @param includeFk if true, the foreign key constraints will be added after the CREATE TABLE
   * @throws SQLException
   */
  public String getTableSource(TableIdentifier table,  DropType dropType, boolean includeFk)
    throws SQLException
  {
    return getTableSource(table, dropType, includeFk, dbConnection.getDbSettings().getGenerateTableGrants());
  }

  public String getTableSource(TableIdentifier table, DropType dropType, boolean includeFk, boolean includeGrants)
    throws SQLException
  {
    if (dbConnection.getDbSettings().isViewType(table.getType()))
    {
      CharSequence s = getViewReader().getExtendedViewSource(table, dropType);
      if (s == null) return null;
      return s.toString();
    }
    DbMetadata meta = dbConnection.getMetadata();

    TableIdentifier tbl = table.createCopy();
    tbl.adjustCase(this.dbConnection);

    List<ColumnIdentifier> cols = null;
    List<IndexDefinition> indexDef = null;
    TableIdentifier toShow = tbl;
    List<DependencyNode> fkDef = null;

    if (needTableDefinitionForTableSource())
    {
      TableDefinition def = meta.getTableDefinition(tbl);
      cols = def.getColumns();
      toShow = def.getTable();
    }

    if (shouldIncludeIndexInTableSource())
    {
      indexDef = getIndexReader().getTableIndexList(toShow, true);
    }

    if (includeFk && dbConnection.getDbSettings().getGenerateTableFKSource())
    {
      fkDef = getForeignKeys(toShow);
    }

    String source = this.getTableSource(toShow, cols, indexDef, fkDef, dropType, includeFk, includeGrants);

    // copy the source to the original table so that they are available later as well
    // and don't need to be re-retrieved
    if (toShow.getSourceOptions().isInitialized())
    {
      table.setSourceOptions(toShow.getSourceOptions());
    }
    return source;
  }

  protected boolean needTableDefinitionForTableSource()
  {
    return dbConnection.getDbSettings().needTableDefinitionForTableSource();
  }

  public String getTableSource(TableIdentifier table, List<ColumnIdentifier> columns)
  {
    List<IndexDefinition> indexInfo = getIndexReader().getTableIndexList(table, true);
    return getTableSource(table, columns, indexInfo, null, DropType.none, true);
  }

  private boolean isFKName(String name, List<DependencyNode> foreignKeys)
  {
    if (StringUtil.isEmpty(name)) return false;
    for (DependencyNode node : foreignKeys)
    {
      String fkname = node.getFkName();
      if (name.equalsIgnoreCase(fkname)) return true;
    }
    return false;
  }

  /**
   * Returns the indexes that should really be re-created.
   * <p>
   * If the DBMS automatically creates an index when a FK constraint is defined (e.g. MySQL)
   * the corresponding CREATE INDEX should not be part of the generated table source.
   *
   * @see DbSettings#supportsAutomaticFkIndexes()
   */
  private List<IndexDefinition> getIndexesToCreate(List<IndexDefinition> indexList, List<DependencyNode> foreignKeys)
  {
    if (CollectionUtil.isEmpty(indexList)) return indexList;
    if (CollectionUtil.isEmpty(foreignKeys)) return indexList;

    List<IndexDefinition> result = new ArrayList<>(indexList.size());
    for (IndexDefinition idx : indexList)
    {
      if (idx.isAutoGenerated()) continue;

      if (dbConnection.getDbSettings().supportsAutomaticFkIndexes() && isFKName(idx.getName(), foreignKeys))
      {
        continue;
      }

      result.add(idx);
    }
    return result;
  }

  public void readTableOptions(TableIdentifier table, List<ColumnIdentifier> columns)
  {
  }

  public String getTableSource(TableIdentifier table, List<ColumnIdentifier> columns, List<IndexDefinition> indexList, List<DependencyNode> fkList, DropType dropType, boolean includeFk)
  {
    return getTableSource(table, columns, indexList, fkList, dropType, includeFk, dbConnection.getDbSettings().getGenerateTableGrants());
  }

  protected boolean shouldIncludeFKInTableSource()
  {
    return dbConnection.getDbSettings().getGenerateTableFKSource();
  }

  protected boolean shouldIncludeIndexInTableSource()
  {
    return dbConnection.getDbSettings().getGenerateTableIndexSource();
  }

  protected boolean shouldIncludeGrantsInTableSource()
  {
    return true;
  }

  protected boolean shouldIncludeCommentInTableSource()
  {
    return true;
  }

  public String getTableSource(TableIdentifier table, List<ColumnIdentifier> columns, List<IndexDefinition> indexList, List<DependencyNode> fkList, DropType dropType, boolean includeFk, boolean includeGrants)
  {
    CharSequence createSql = getCreateTable(table, columns, indexList, fkList, dropType, includeFk);

    StringBuilder result = new StringBuilder(createSql.length() + 50);
    result.append(createSql);

    String lineEnding = Settings.getInstance().getInternalEditorLineEnding();

    boolean inlineFK = getCreateInlineFKConstraints();

    StringUtil.trimTrailingWhitespace(result);
    result.append(lineEnding);

    if (includeFk && shouldIncludeFKInTableSource())
    {
      if (inlineFK)
      {
        appendFKComments(table, result, fkList);
      }
      else
      {
        CharSequence fk = getFkSource(table, fkList, false);
        if (StringUtil.isNotBlank(fk))
        {
          result.append(lineEnding);
          result.append(fk);
        }
        StringUtil.trimTrailingWhitespace(result);
        result.append(lineEnding);
      }
    }


    if (shouldIncludeIndexInTableSource())
    {
      List<IndexDefinition> toCreate = getIndexesToCreate(indexList, fkList);
      StringBuilder indexSource = getIndexReader().getIndexSource(table, toCreate);
      if (StringUtil.isNotBlank(indexSource))
      {
        result.append(lineEnding);
        result.append(indexSource);
      }
      StringUtil.trimTrailingWhitespace(result);
      result.append(lineEnding);
    }

    if (shouldIncludeCommentInTableSource())
    {
      appendTableComments(result, table, columns, lineEnding);
    }

    if (includeGrants && shouldIncludeGrantsInTableSource())
    {
      TableGrantReader grantReader = TableGrantReader.createReader(dbConnection);
      StringBuilder grants = grantReader.getTableGrantSource(this.dbConnection, table);
      if (StringUtil.isNotBlank(grants))
      {
        result.append(lineEnding);
        result.append(grants);
      }
      StringUtil.trimTrailingWhitespace(result);
      result.append(lineEnding);
    }

    CharSequence extendedSQL = table.getSourceOptions().getAdditionalSql();
    if (extendedSQL != null)
    {
      result.append(lineEnding);
      result.append(extendedSQL);
    }

    StringUtil.trimTrailingWhitespace(result);
    result.append(lineEnding);

    return result.toString();
  }

  protected void appendTableComments(StringBuilder result, TableIdentifier table, List<ColumnIdentifier> columns, String lineEnding)
  {
    appendComments(result, dbConnection, table, columns, lineEnding, !dbConnection.getDbSettings().getUseInlineColumnComments());
  }

  public static void appendComments(StringBuilder result, WbConnection connection, TableDefinition table)
  {
    if (table != null)
    {
      appendComments(result, connection, table.getTable(), table.getColumns(), "\n", true);
    }
  }

  public static void appendComments(StringBuilder result, WbConnection connection, TableIdentifier table, List<ColumnIdentifier> columns, String lineEnding, boolean generateColumnComments)
  {
    if (connection.getDbSettings().getGenerateTableComments())
    {
      TableCommentReader commentReader = new TableCommentReader();

      if (connection.getDbSettings().getUseInlineTableComments() == false)
      {
        String tableComment = commentReader.getTableCommentSql(connection, table);
        if (StringUtil.isNotBlank(tableComment))
        {
          result.append(lineEnding);
          result.append(tableComment);
        }
      }

      if (generateColumnComments && CollectionUtil.isNonEmpty(columns))
      {
        StringBuilder colComments = commentReader.getTableColumnCommentsSql(connection, table, columns);
        if (StringUtil.isNotBlank(colComments))
        {
          result.append(lineEnding);
          result.append(colComments);
        }
      }
    }
  }

  /**
   * Generate the pure CREATE TABLE statement for the passed table definition.
   *
   * Any table constraints will be retrieved if needed.
   *
   * @param table the table name
   * @param columns the columns of the table
   * @param indexList defined indexes for the table (may be null)
   * @param fkDefinitions defined foreign keys for the table (may be null)
   * @param includeDrop if true, a DROP TABLE will be added before the CREATE TABLE
   * @param includeFk if true, foreign key definitions (if present) will be included
   *
   * @return the CREATE TABLE statement for the table
   */
  public CharSequence getCreateTable(TableIdentifier table, List<ColumnIdentifier> columns, List<IndexDefinition> indexList, List<DependencyNode> fkDefinitions, DropType dropType, boolean includeFk)
  {
    return getCreateTable(table, columns, indexList, fkDefinitions, dropType, includeFk, true, false);
  }

  public CharSequence getCreateTable(TableIdentifier table, List<ColumnIdentifier> columns, List<IndexDefinition> indexList, List<DependencyNode> fkDefinitions, DropType dropType, boolean includeFk, boolean includePK, boolean useFQN)
  {
    if (table == null) return StringUtil.EMPTY_STRING;

    String nativeSql = getNativeTableSource(table, dropType);
    if (nativeSql != null) return nativeSql;

    readTableOptions(table, columns);
    if (table.getUseInlineFK() && fkDefinitions == null)
    {
      fkDefinitions = getForeignKeys(table);
    }

    if (CollectionUtil.isEmpty(columns)) return StringUtil.EMPTY_STRING;

    StringBuilder result = new StringBuilder(250);
    DbMetadata meta = dbConnection.getMetadata();

    TableDefinition def = new TableDefinition(table, columns);
    ConstraintReader consReader = ReaderFactory.getConstraintReader(meta);

    consReader.retrieveColumnConstraints(dbConnection, def);

    // getTableConstraints() is allowed to change the column definitions to
    // move table constraints that are actually column constraints into the column definition
    // therefor this must be called bevore calling appendColumnDefinition()
    List<TableConstraint> tableConstraints = consReader.getTableConstraints(dbConnection, def);

    consReader.updateIndexList(tableConstraints, indexList);

    // this should have been populated previously!
    ObjectSourceOptions sourceOptions = table.getSourceOptions();
    String typeOption = sourceOptions.getTypeModifier();

    result.append(generateCreateObject(dropType, table, typeOption, useFQN));
    result.append("\n(\n");

    appendColumnDefinitions(result, columns, meta, COL_INDENT);

    String cons = consReader.getConstraintSource(tableConstraints, COL_INDENT);
    if (StringUtil.isNotEmpty(cons))
    {
      result.append(",\n").append(COL_INDENT);
      result.append(cons);
    }

    String pkIndexName = getPKName(indexList);
    String pkname = table.getPrimaryKeyName() != null ? table.getPrimaryKeyName() : pkIndexName;

    List<String> pkCols = findPkColumns(columns);

    if (pkname != null && pkCols.isEmpty())
    {
      // this can happen in DB2 iSeries. Apparently the columns are not always marked as PK
      // but the PK index is detected by SQL Workbench
      pkCols = getPKColsFromIndex(indexList);
    }

    PkDefinition pk = table.getPrimaryKey();
    if (includePK && pk == null)
    {
      pk = getIndexReader().getPrimaryKey(table);
      if (pk == null && pkCols.size() > 0)
      {
        pk = new PkDefinition(pkCols);
      }
      else
      {
        table.setPrimaryKey(pk);
      }
    }
    syncPkIndexType(pk, indexList);

    boolean inlinePK = table.getUseInlinePK() || getCreateInlinePKConstraints();
    if (includePK && inlinePK && pk != null)
    {
      result.append(",\n").append(COL_INDENT);
      CharSequence pkSql = getPkSource(table, pk, true, false);
      result.append(pkSql);
    }

    if (table.getUseInlineFK() || (includeFk  && getCreateInlineFKConstraints()))
    {
      StringBuilder fk = getFkSource(table, fkDefinitions, true);
      if (fk.length() > 0)
      {
        result.append(",\n");
        result.append(fk);
      }
    }

    String tblOptions = sourceOptions.getInlineOption();
    if (tblOptions != null)
    {
      result.append(",\n").append(COL_INDENT);
      result.append(tblOptions);
    }

    result.append('\n');
    result.append(")");
    String options = sourceOptions.getTableOption();
    if (StringUtil.isNotEmpty(options))
    {
      result.append('\n');
      result.append(options);
      result.append('\n');
    }

    if (dbConnection.getDbSettings().getUseInlineTableComments()
        && dbConnection.getDbSettings().getInlineTableCommentKeyword() != null
        && StringUtil.isNotEmpty(table.getComment()))
    {
      result.append('\n');
      result.append(dbConnection.getDbSettings().getInlineTableCommentKeyword());
      result.append(" '");
      result.append(SqlUtil.escapeQuotes(table.getComment()));
      result.append('\'');
    }

    StringUtil.trimTrailingWhitespace(result);
    result.append(";\n");
    // end of CREATE TABLE

    // Add additional information provided by any specialized descendant class
    String info = getAdditionalTableInfo(table, columns, indexList);
    if (StringUtil.isNotBlank(info))
    {
      result.append(info);
      result.append('\n');
    }

    if (includePK && !inlinePK && pk != null)
    {
      CharSequence pkSource = getPkSource(table, pk, false, useFQN);
      result.append('\n');
      result.append(pkSource);
    }

    return result;
  }

  private void syncPkIndexType(PkDefinition pk, List<IndexDefinition> indexList)
  {
    if (pk == null) return;
    if (CollectionUtil.isEmpty(indexList)) return;

    for (IndexDefinition index : indexList)
    {
      if (index.isPrimaryKeyIndex())
      {
        pk.setPkIndexDefinition(index);
      }
    }
  }

  public CharSequence generateDrop(DbObject toDrop, DropType dropType)
  {
    String type = toDrop.getObjectType();
    type = type.replace("SYSTEM ", "");
    String objectName = toDrop.getObjectNameForDrop(dbConnection);
    StringBuilder result = new StringBuilder(type.length() + objectName.length() + 15);

    String drop = dbConnection.getDbSettings().getDropDDL(type, dropType == DropType.cascaded);
    if (drop == null)
    {
      // Fallback, just in case no DROP statement was configured
      result.append("DROP ");
      result.append(type.toUpperCase());
      result.append(' ');
      result.append(objectName);
      String cascade = dbConnection.getDbSettings().getCascadeConstraintsVerb(type);
      if (cascade != null)
      {
        result.append(' ');
        result.append(cascade);
      }
      result.append(";\n");
    }
    else
    {
      drop = TemplateHandler.replaceTablePlaceholder(drop, toDrop, dbConnection, false);
      drop = TemplateHandler.replacePlaceholder(drop, NAME_PLACEHOLDER, objectName, false);
      result.append(SqlUtil.addSemicolon(drop));
    }
    return result;
  }

  public String getReplaceDDL(String objectType)
  {
    return getDDL(objectType, "replace");
  }

  protected String getCreateDDL(String objectType, TableIdentifier tbl)
  {
    String create = getDDL(objectType, "create");
    if (create != null) return create;

    return "CREATE " + objectType.toUpperCase() + " " + MetaDataSqlManager.DDL_IF_NOT_EXISTS + " " + NAME_PLACEHOLDER;
  }

  protected String getDDL(String objectType, String operation)
  {
    String prefix = "workbench.db.";
    String suffix = "." + DbSettings.getKeyValue(objectType) + ".sql." + dbConnection.getDbId();
    return Settings.getInstance().getProperty(prefix + operation + suffix, null);
  }

  /**
   * Generate a CREATE statement for the given object type
   * @param includeDrop  if true, a DROP ... will be included in the SQL
   * @param toCreate     the definition of the object to create
   * @param typeOption   an option for the CREATE statement. This is only
   * @return an approriate CREATE xxxx statement
   */
  public StringBuilder generateCreateObject(DropType dropType, TableIdentifier toCreate, String typeOption)
  {
    return generateCreateObject(dropType, toCreate, typeOption, false);
  }

  public StringBuilder generateCreateObject(DropType dropType, TableIdentifier toCreate, String typeOption, boolean useFQN)
  {
    StringBuilder result = new StringBuilder();
    boolean addDrop = dropType != DropType.none;

    String objectType = toCreate.getObjectType();
    objectType = objectType.replace("SYSTEM ", "");

    String ddl = getReplaceDDL(objectType);

    if (ddl == null)
    {
      ddl = getCreateDDL(objectType, toCreate);
    }
    else
    {
      String cascadeVerb = dbConnection.getDbSettings().getCascadeConstraintsVerb(objectType);
      boolean cascadeSupported = StringUtil.isNotEmpty(cascadeVerb);

      // if a cascaded drop was requested add it, even when a REPLACE is available
      // because a cascaded drop might do more than a create or replace
      // when "only" a regular drop was requested this should be the same as a CREATE OR REPLACE
      // so there is no need to add the drop statement
      addDrop = (dropType == DropType.cascaded) && cascadeSupported;
    }

    if (addDrop)
    {
      result.append(generateDrop(toCreate, dropType));
      result.append('\n');
      result.append('\n');
    }

    String name = toCreate.getObjectExpression(dbConnection);
    String fqName = toCreate.getFullyQualifiedName(dbConnection);

    ddl = StringUtil.replace(ddl, MetaDataSqlManager.NAME_PLACEHOLDER, useFQN ? fqName : name);
    ddl = StringUtil.replace(ddl, MetaDataSqlManager.FQ_NAME_PLACEHOLDER, fqName);

    if (StringUtil.isEmpty(typeOption))
    {
      ddl = TemplateHandler.removePlaceholder(ddl, MetaDataSqlManager.DDL_TYPEOPTION, true);
    }
    else
    {
      ddl = TemplateHandler.replacePlaceholder(ddl, MetaDataSqlManager.DDL_TYPEOPTION, typeOption, true);
    }

    String ifNotExists = dbConnection.getDbSettings().getDDLIfNoExistsOption(objectType);
    if (dropType == DropType.none && StringUtil.isNotEmpty(ifNotExists))
    {
      ddl = TemplateHandler.replacePlaceholder(ddl, MetaDataSqlManager.DDL_IF_NOT_EXISTS, ifNotExists, true);
    }
    else
    {
      ddl = TemplateHandler.removePlaceholder(ddl, MetaDataSqlManager.DDL_IF_NOT_EXISTS, true);
    }

    result.append(ddl);

    return result;
  }

  private List<String> findPkColumns(List<ColumnIdentifier> columns)
  {
    List<String> result = new ArrayList<>(2);
    for (ColumnIdentifier column : columns)
    {
      if (column.isPkColumn())
      {
        result.add(column.getColumnName());
      }
    }
    return result;
  }

  public void appendColumnDefinitions(StringBuilder result, List<ColumnIdentifier> columns, DbMetadata meta)
  {
    appendColumnDefinitions(result, columns, meta, COL_INDENT);
  }

  public void appendColumnDefinitions(StringBuilder result, List<ColumnIdentifier> columns, DbMetadata meta, String indent)
  {
    int maxColLength = 0;
    int maxTypeLength = 0;

    // Make sure the columns are sorted correctly
    List<ColumnIdentifier> cols = new ArrayList<>(columns);
    ColumnIdentifier.sortByPosition(cols);

    // calculate the longest column name, so that the display can be formatted
    for (ColumnIdentifier column : cols)
    {
      String colName = meta.quoteObjectname(column.getColumnName());
      String type = column.getDbmsType();
      maxColLength = Math.max(maxColLength, colName.length());
      maxTypeLength = Math.max(maxTypeLength, (type != null ? type.length() : 0));
    }
    maxColLength += 2;
    maxTypeLength += 2;

    Iterator<ColumnIdentifier> itr = cols.iterator();
    while (itr.hasNext())
    {
      ColumnIdentifier column = itr.next();
      String colName = column.getColumnName();
      String quotedColName = meta.quoteObjectname(colName);
      String type = column.getDbmsType();
      if (type == null) type = "";

      result.append(indent);
      if (column.isInherited())
      {
        result.append("-- inherited: ");
      }
      result.append(quotedColName);

      for (int k=0; k < maxColLength - quotedColName.length(); k++)
      {
        result.append(' ');
      }
      String coldef = getColumnSQL(column, maxTypeLength, column.getConstraint());

      result.append(coldef);
      if (itr.hasNext())
      {
        if (!column.isInherited()) result.append(',');
        result.append('\n');
      }
    }
  }

  private List<String> getPKColsFromIndex(List<IndexDefinition> indexList)
  {
    List<String> columns = new ArrayList<>();
    for (IndexDefinition index : indexList)
    {
      if (index != null && index.isPrimaryKeyIndex())
      {
        for (IndexColumn col : index.getColumns())
        {
          columns.add(col.getColumn());
        }
      }
    }
    return columns;
  }

  protected String getColumnSQL(ColumnIdentifier column, int maxTypeLength, String columnConstraint)
  {
    DbMetadata meta = dbConnection.getMetadata();
    boolean inlineColumnComments = dbConnection.getDbSettings().getUseInlineColumnComments();

    StringBuilder result = new StringBuilder(50);

    ColumnIdentifier toUse = column;

    ColumnDefinitionTemplate tmpl = new ColumnDefinitionTemplate(meta.getDbId());
    tmpl.setFixDefaultValues(!dbConnection.getDbSettings().returnsValidDefaultExpressions());
    result.append(tmpl.getColumnDefinitionSQL(toUse, columnConstraint, maxTypeLength));

    if (inlineColumnComments && StringUtil.isNotBlank(column.getComment()))
    {
      String keyword = " " + dbConnection.getDbSettings().getInlineCommentKeyword() + " ";
      result.append(keyword);
      result.append('\'');
      result.append(SqlUtil.escapeQuotes(column.getComment()));
      result.append('\'');
    }
    return result.toString();
  }

  protected String getAdditionalTableInfo(TableIdentifier table, List<ColumnIdentifier> columns, List<IndexDefinition> indexList)
  {
    return null;
  }

  private String getPKName(List<IndexDefinition> indexList)
  {
    if (indexList == null) return null;
    for (IndexDefinition index : indexList)
    {
      if (index.isPrimaryKeyIndex())
      {
        return index.getName();
      }
    }
    return null;
  }

  public static String replacePlaceHolder(String sql, String placeHolder, String value, boolean needQuotes, QuoteHandler quoter)
  {
    value = (needQuotes ? quoter.quoteObjectname(value) : value);
    return TemplateHandler.replacePlaceholder(sql, placeHolder, value, false);
  }

  public String getNativeTableSource(TableIdentifier table, DropType dropType)
  {
    String sql = dbConnection.getDbSettings().getRetrieveObjectSourceSql(table.getType());
    if (sql == null) return null;

    StringBuilder result = new StringBuilder(250);

    int colIndex = dbConnection.getDbSettings().getRetrieveTableSourceCol(table.getType());

    if (dropType != DropType.none)
    {
      CharSequence drop = generateDrop(table, dropType);
      result.append(drop);
      result.append("\n\n");
    }

    boolean needQuotes = dbConnection.getDbSettings().getRetrieveObjectSourceNeedsQuotes(table.getType());
    DbMetadata metaData = dbConnection.getMetadata();

    sql = replacePlaceHolder(sql, MetaDataSqlManager.TABLE_NAME_PLACEHOLDER, table.getTableName(), needQuotes, metaData);
    sql = replacePlaceHolder(sql, NAME_PLACEHOLDER, table.getTableName(), needQuotes, metaData);
    if (sql.contains(MetaDataSqlManager.FQ_TABLE_NAME_PLACEHOLDER))
    {
      sql = replacePlaceHolder(sql, MetaDataSqlManager.FQ_TABLE_NAME_PLACEHOLDER, table.getFullyQualifiedName(dbConnection), false, metaData);
    }

    sql = replacePlaceHolder(sql, SCHEMA_PLACEHOLDER, table.getSchema(), needQuotes, metaData);
    sql = replacePlaceHolder(sql, CATALOG_PLACEHOLDER, table.getCatalog(), needQuotes, metaData);

    LogMgr.logMetadataSql(new CallerInfo(){}, "table source", sql);
    Statement stmt = null;
    ResultSet rs = null;
    try
    {
      stmt = dbConnection.createStatementForQuery();
      rs = stmt.executeQuery(sql);
      while (rs.next())
      {
        result.append(rs.getString(colIndex));
      }
      result.append('\n');
    }
    catch (Exception se)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, se, "table source", sql);
      return null;
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    StringUtil.trimTrailingWhitespace(result);
    if (result.charAt(result.length() -1 ) != ';')
    {
      result.append(";\n");
    }

    if (dbConnection.getDbSettings().applyFormatForNativeTableSource())
    {
      SqlFormatter formatter = SqlFormatterFactory.createFormatter(dbConnection.getDbId());
      return formatter.getFormattedSql(result.toString());
    }

    return result.toString();
  }

  /**
   * Builds an ALTER TABLE to add a primary key definition for the given tablename.
   *
   * @param table         the table for which the PK statement should be created.
   * @param pk            the PK definition, if null the PK from the table is used
   * @param forInlineUse  if true, the SQL is useable "inline" for a CREATE TABLE statement.
   *
   * @return an SQL statement to add a PK constraint on the given table.
   */
  public CharSequence getPkSource(TableIdentifier table, PkDefinition pk, boolean forInlineUse, boolean useFQN)
  {
    if (pk == null) return StringUtil.EMPTY_STRING;

    DbMetadata meta = dbConnection.getMetadata();

    PkTemplate pkTmpl = new PkTemplate(dbConnection, forInlineUse);
    String template = pkTmpl.getSQLTemplate();

    if (StringUtil.isEmpty(template)) return StringUtil.EMPTY_STRING;

    StringBuilder result = new StringBuilder(100);
    String fqName = table.getFullyQualifiedName(dbConnection);
    String tablename = table.getTableExpression(this.dbConnection);

    List<String> pkCols = pk.getColumns();
    String pkName = pk.getPkName();

    template = StringUtil.replace(template, MetaDataSqlManager.TABLE_EXPRESSION_PLACEHOLDER, useFQN ? fqName : tablename);
    template = StringUtil.replace(template, MetaDataSqlManager.TABLE_NAME_PLACEHOLDER, useFQN ? fqName : table.getTableName());
    template = StringUtil.replace(template, MetaDataSqlManager.FQ_TABLE_NAME_PLACEHOLDER, fqName);
    template = StringUtil.replace(template, MetaDataSqlManager.COLUMN_LIST_PLACEHOLDER, getColumnList(pkCols));

    if (nameTester.isSystemConstraintName(pkName))
    {
      pkName = null;
    }

    if (pkName == null && DbExplorerSettings.getAutoGeneratePKName())
    {
      int maxLen = this.dbConnection.getMetadata().getMaxTableNameLength();
      pkName = nameTester.generatePKName(table, maxLen);
    }
    else if (pkName != null && dbConnection.getDbSettings().useFQConstraintName())
    {
      pkName = SqlUtil.buildExpression(dbConnection, table.getCatalog(), table.getSchema(), pkName);
    }
    else if (pkName != null)
    {
      pkName = meta.quoteObjectname(pkName);
    }

    if (StringUtil.isEmpty(pkName))
    {
      template = TemplateHandler.removePlaceholder(template, "CONSTRAINT " + MetaDataSqlManager.CONSTRAINT_NAME_PLACEHOLDER, true);
      template = TemplateHandler.removePlaceholder(template, MetaDataSqlManager.CONSTRAINT_NAME_PLACEHOLDER, true);
      template = StringUtil.replace(template, " CONSTRAINT ", ""); // remove CONSTRAINT KEYWORD if no name is available
    }
    else
    {
      template = StringUtil.replace(template, MetaDataSqlManager.PK_NAME_PLACEHOLDER, pkName);  // old templates
      template = TemplateHandler.replacePlaceholder(template, MetaDataSqlManager.CONSTRAINT_NAME_PLACEHOLDER, pkName, true);  // new templates through DbSettings.getAddPk()
    }

    template = template.replaceAll("ADD\\s+PRIMARY", "ADD PRIMARY"); // removing the constraint name leaves two spaces which I find ugly :)
    result.append(template);
    if (!forInlineUse)
    {
      result.append(";\n");
    }

    return result;
  }

  private String getColumnList(List<String> pkCols)
  {
    StringBuilder result = new StringBuilder(pkCols.size() * 30);
    for (int i=0; i < pkCols.size(); i++)
    {
      if (i > 0) result.append(", ");
      result.append(dbConnection.getMetadata().quoteObjectname(pkCols.get(i)));
    }
    return result.toString();
  }

  protected boolean getCreateInlinePKConstraints()
  {
    if (dbConnection == null) return false;
    return dbConnection.getDbSettings().createInlinePKConstraints();
  }

  public void setCreateInlineFKConstrants(boolean flag)
  {
    this.createInlineFK = flag;
  }
  protected boolean getCreateInlineFKConstraints()
  {
    if (createInlineFK != null) return createInlineFK;
    if (dbConnection == null) return false;
    return dbConnection.getDbSettings().createInlineFKConstraints();
  }

  protected List<DependencyNode> getForeignKeys(TableIdentifier table)
  {
    TableDependency deps = new TableDependency(dbConnection, table);
    deps.setRetrieveDirectChildrenOnly(true);
    deps.readTreeForParents();
    return deps.getLeafs();
  }

  public StringBuilder getFkSource(TableIdentifier table)
  {
    return getFkSource(table, getForeignKeys(table), getCreateInlineFKConstraints());
  }

  protected void appendFKComments(TableIdentifier table, StringBuilder fkSource, List<DependencyNode> fkList)
  {
  }

  /**
   *  Return a SQL script to re-create the Foreign key definition for the given table.
   *
   *  @param table the tablename for which the foreign keys should be created
   *  @param fkList a DataStore with the FK definition as returned by #getForeignKeys()
   *
   *  @return a SQL statement to add the foreign key definitions to the given table, never null
   */
  public StringBuilder getFkSource(TableIdentifier table, List<DependencyNode> fkList, boolean forInlineUse)
  {
    if (CollectionUtil.isEmpty(fkList)) return StringUtil.emptyBuilder();

    FkTemplate tmpl = new FkTemplate(dbConnection.getDbId(), forInlineUse);
    String template = tmpl.getSQLTemplate();
    List<String> fkStatements = new ArrayList<>(fkList.size());
    FKHandler fkHandler = FKHandler.createInstance(dbConnection);

    for (DependencyNode node : fkList)
    {
      String fkname = node.getFkName();
      String stmt = TemplateHandler.replaceTablePlaceholder(template, table, dbConnection, false);

      if (nameTester.isSystemConstraintName(fkname))
      {
        stmt = StringUtil.replace(stmt, MetaDataSqlManager.CONSTRAINT_NAME_PLACEHOLDER, "");
        stmt = StringUtil.replace(stmt, " CONSTRAINT ", "");
      }
      else
      {
        if (dbConnection.getDbSettings().useFQConstraintName())
        {
          String fqName = SqlUtil.buildExpression(dbConnection, table.getCatalog(), table.getSchema(), fkname);
          stmt = StringUtil.replace(stmt, MetaDataSqlManager.CONSTRAINT_NAME_PLACEHOLDER, fqName);
        }
        else
        {
          stmt = StringUtil.replace(stmt, MetaDataSqlManager.CONSTRAINT_NAME_PLACEHOLDER, dbConnection.getMetadata().quoteObjectname(fkname));
        }
      }

      stmt = TemplateHandler.replacePlaceholder(stmt, MetaDataSqlManager.COLUMN_LIST_PLACEHOLDER, getColumnList(node.getTargetColumns()), true);

      String rule = node.getUpdateAction();
      if (dbConnection.getDbSettings().supportsFkOption("update", rule))
      {
        stmt = TemplateHandler.replacePlaceholder(stmt, MetaDataSqlManager.FK_UPDATE_RULE, "ON UPDATE " + rule, true);
      }
      else
      {
        stmt = TemplateHandler.removePlaceholder(stmt, MetaDataSqlManager.FK_UPDATE_RULE, true);
      }

      rule = node.getDeleteAction();
      if (dbConnection.getDbSettings().supportsFkOption("delete", rule))
      {
        stmt = TemplateHandler.replacePlaceholder(stmt, MetaDataSqlManager.FK_DELETE_RULE, "ON DELETE " + rule, true);
      }
      else
      {
        stmt = TemplateHandler.removePlaceholder(stmt, MetaDataSqlManager.FK_DELETE_RULE, true);
      }

      rule = getDeferrableVerb(node.getDeferrableType());
      if (StringUtil.isEmpty(rule))
      {
        stmt = TemplateHandler.removePlaceholder(stmt, MetaDataSqlManager.DEFERRABLE, true);
      }
      else
      {
        stmt = TemplateHandler.replacePlaceholder(stmt, MetaDataSqlManager.DEFERRABLE, rule.trim(), true);
      }

      FKMatchType matchType = node.getMatchType();
      if (!fkHandler.shouldGenerate(matchType))
      {
        stmt = TemplateHandler.removePlaceholder(stmt, MetaDataSqlManager.FK_MATCH_TYPE, false);
      }
      else
      {
        stmt = TemplateHandler.replacePlaceholder(stmt, MetaDataSqlManager.FK_MATCH_TYPE, "MATCH " + matchType.toString(), true);
      }

      stmt = TemplateHandler.replacePlaceholder(stmt, MetaDataSqlManager.FK_TARGET_TABLE_PLACEHOLDER, node.getTable().getTableExpression(dbConnection), true);
      stmt = TemplateHandler.replacePlaceholder(stmt, MetaDataSqlManager.FK_TARGET_COLUMNS_PLACEHOLDER, getColumnList(node.getSourceColumns()), true);

      String add = getAdditionalFkSql(table, node, stmt);
      if (add != null)
      {
        stmt = add;
      }
      fkStatements.add(stmt.trim());
    }

    StringBuilder fk = new StringBuilder();

    String nl = Settings.getInstance().getInternalEditorLineEnding();

    Iterator<String> values = fkStatements.iterator();
    while (values.hasNext())
    {
      if (forInlineUse)
      {
        fk.append(COL_INDENT);
        fk.append(values.next());
        if (values.hasNext())
        {
          fk.append(',');
        }
      }
      else
      {
        fk.append(values.next());
        fk.append(';');
        fk.append(nl);
      }
      if (values.hasNext()) fk.append(nl);
    }

    return fk;
  }

  protected String getAdditionalFkSql(TableIdentifier table, DependencyNode fk, String template)
  {
    return null;
  }

  private String getDeferrableVerb(String type)
  {
    if (dbConnection.getDbSettings().isNotDeferrable(type)) return StringUtil.EMPTY_STRING;
    return "DEFERRABLE " + type;
  }

}
