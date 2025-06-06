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

package workbench.sql.wbcommands;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import workbench.console.ConsoleSettings;
import workbench.console.RowDisplay;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.DbMetadata;
import workbench.db.JdbcUtils;
import workbench.db.TableIdentifier;
import workbench.db.TableSelectBuilder;
import workbench.db.WbConnection;

import workbench.storage.DataStore;
import workbench.storage.NamedSortDefinition;
import workbench.storage.RowActionMonitor;
import workbench.storage.SortDefinition;

import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;
import workbench.sql.annotations.ResultNameAnnotation;

import workbench.util.ArgumentParser;
import workbench.util.ArgumentType;
import workbench.util.CollectionUtil;
import workbench.util.StringUtil;

/**
 * A class to count rows from the specified tables.
 *
 * It's the commandline version of the DbExplorer's  "Count rows" feature.
 *
 * @author Thomas Kellerer
 */
public class WbRowCount
  extends SqlCommand
{
  public static final String VERB = "WbRowCount";
  public static final String ARG_ORDER_BY = "orderBy";
  public static final String ARG_EXCL_COLS = "excludeColumns";
  public static final String ARG_REMOVE_EMPTY = "removeEmpty";

  public WbRowCount()
  {
    cmdLine = new ArgumentParser();
    cmdLine.addArgument(CommonArgs.ARG_OBJECTS, ArgumentType.TableArgument);
    cmdLine.addArgument(CommonArgs.ARG_TYPES, ArgumentType.ObjectTypeArgument);
    cmdLine.addDeprecatedArgument(CommonArgs.ARG_SCHEMA, ArgumentType.SchemaArgument);
    cmdLine.addDeprecatedArgument(CommonArgs.ARG_CATALOG, ArgumentType.CatalogArgument);
    cmdLine.addArgument(CommonArgs.ARG_EXCLUDE_TABLES, ArgumentType.TableArgument);
    cmdLine.addArgument(ARG_REMOVE_EMPTY, ArgumentType.BoolSwitch);
    cmdLine.addArgument(ARG_ORDER_BY, CollectionUtil.arrayList("rowcount", "type", "schema", "catalog", "name"));
    cmdLine.addArgument(ARG_EXCL_COLS, CollectionUtil.arrayList("schema","catalog","database","type"));
  }

  @Override
  public String getVerb()
  {
    return VERB;
  }

  public static DataStore buildResultDataStore(WbConnection connection)
  {
    return buildResultDataStore(connection, true, true, true);
  }

  public static DataStore buildResultDataStore(WbConnection connection, boolean includeCatalog, boolean includeSchema, boolean includeType)
  {
    List<String> colNames = new ArrayList<>(5);

    colNames.add(ResourceMgr.getString("TxtRowCnt").toUpperCase());
    colNames.add("NAME");
    if (includeType)
    {
      colNames.add("TYPE");
    }
    if (includeCatalog)
    {
      colNames.add(connection.getMetadata().getCatalogTerm().toUpperCase());
    }
    if (includeSchema)
    {
      colNames.add(connection.getMetadata().getSchemaTerm().toUpperCase());
    }

    int[] types = new int[colNames.size()];
    types[0] = Types.BIGINT;

    String[]  columns = new String[colNames.size()];

    for (int i=0; i< colNames.size(); i++)
    {
      if (i > 0) types[i] = Types.VARCHAR;
      columns[i] = colNames.get(i);
    }
    DataStore ds = new DataStore(columns, types);
    return ds;
  }

  @Override
  public StatementRunnerResult execute(String sql)
    throws SQLException
  {
    String options = getCommandLine(sql);

    StatementRunnerResult result = new StatementRunnerResult();
    ConsoleSettings.getInstance().setNextRowDisplay(RowDisplay.SingleLine);

    cmdLine.parse(options);
    if (displayHelp(result))
    {
      return result;
    }

    String defaultSort = getDefaultSortConfig();
    String sort = cmdLine.getValue(ARG_ORDER_BY, defaultSort);
    boolean includeEmpty = cmdLine.getBoolean(ARG_REMOVE_EMPTY) == false;

    if (this.rowMonitor != null)
    {
      rowMonitor.setMonitorType(RowActionMonitor.MONITOR_PLAIN);
      rowMonitor.setCurrentObject(ResourceMgr.getString("MsgDiffRetrieveDbInfo"), -1, -1);
    }

    String include = null;
    String exclude = null;
    if (cmdLine.hasArguments())
    {
      include = cmdLine.getValue(CommonArgs.ARG_OBJECTS);
      exclude = cmdLine.getValue(CommonArgs.ARG_EXCLUDE_TABLES);

      // Support the old way of specifying the catalog or schema
      String schema = cmdLine.getValue(CommonArgs.ARG_SCHEMA);
      String catalog = cmdLine.getValue(CommonArgs.ARG_CATALOG);
      if (StringUtil.isBlank(include) && StringUtil.isNotBlank(schema))
      {
        include = schema + ".%";
      }
      else if (StringUtil.isBlank(include) && StringUtil.isNotBlank(catalog))
      {
        include = catalog + ".%";
      }
    }
    else
    {
      include = cmdLine.getNonArguments();
    }

    if (StringUtil.isEmpty(include))
    {
      include = "%";
    }

    String[] types = null;
    List<String> typeList = cmdLine.getListValue(CommonArgs.ARG_TYPES);
    if (CollectionUtil.isEmpty(typeList))
    {
      types =  currentConnection.getMetadata().getTableTypesArray();
    }
    else
    {
      types = StringUtil.toArray(typeList, true, true);
    }

    SourceTableArgument tables = new SourceTableArgument(include, exclude, null, types, currentConnection);

    List<TableIdentifier> resultList = tables.getTables();
    if (CollectionUtil.isEmpty(resultList))
    {
      result.setFailure();
      result.addMessageByKey("ErrNoTablesFound");
      return result;
    }

    boolean useSavepoint = currentConnection.getDbSettings().useSavePointForDML();

    Set<String> excludeCols = CollectionUtil.caseInsensitiveSet();
    excludeCols.addAll(cmdLine.getListValue(ARG_EXCL_COLS));

    String schemaTerm = currentConnection.getMetadata().getSchemaTerm().toLowerCase();
    String catalogTerm =  currentConnection.getMetadata().getCatalogTerm().toLowerCase();

    boolean includeType = true;
    boolean includeSchema = currentConnection.getDbSettings().supportsSchemas();
    boolean includeCatalog = currentConnection.getDbSettings().supportsCatalogs();

    if (excludeCols.contains("type"))
    {
      includeType = false;
    }

    if (excludeCols.contains("schema") || excludeCols.contains(schemaTerm))
    {
      includeSchema = false;
    }

    if (excludeCols.contains("catalog") || excludeCols.contains("database") || excludeCols.contains(catalogTerm))
    {
      includeCatalog = false;
    }

    DataStore rowCounts = buildResultDataStore(currentConnection, includeCatalog, includeSchema, includeType);

    TableSelectBuilder builder = new TableSelectBuilder(currentConnection, TableSelectBuilder.ROWCOUNT_TEMPLATE_NAME, TableSelectBuilder.TABLEDATA_TEMPLATE_NAME);
    currentStatement = currentConnection.createStatementForQuery();

    int typeIndex = rowCounts.getColumnIndex("TYPE");
    int catalogIndex = rowCounts.getColumnIndex(currentConnection.getMetadata().getCatalogTerm());
    int schemaIndex = rowCounts.getColumnIndex(currentConnection.getMetadata().getSchemaTerm());

    int tableCount = resultList.size();
    Set<TableIdentifier> processed = new HashSet<>(tableCount);

    final CallerInfo ci = new CallerInfo(){};

    for (int row=0; row < tableCount; row++)
    {
      ResultSet rs = null;
      TableIdentifier table = resultList.get(row);
      try
      {
        if (processed.contains(table)) continue;

        String countQuery = builder.getSelectForCount(table);
        String msg = ResourceMgr.getFormattedString("MsgCalculatingRowCount", table.getTableExpression(), row + 1, tableCount);

        if (rowMonitor != null)
        {
          rowMonitor.setCurrentObject(msg, row + 1, tableCount);
        }

        if (Settings.getInstance().getLogAllStatements())
        {
          LogMgr.logInfo(ci, "Retrieving rowcount using:\n" + countQuery);
        }
        else
        {
          LogMgr.logDebug(ci, "Retrieving rowcount using:\n" + countQuery);
        }

        rs = JdbcUtils.runQuery(currentConnection, currentStatement, countQuery, useSavepoint);

        if (isCancelled) break;

        long rowCount = -1;
        if (rs != null && rs.next())
        {
          rowCount = rs.getLong(1);
        }

        if (includeEmpty || rowCount > 0)
        {
          int dsRow = rowCounts.addRow();
          rowCounts.setValue(dsRow, 0, rowCount);
          rowCounts.setValue(dsRow, 1, table.getTableName());
          if (includeType) rowCounts.setValue(dsRow, typeIndex, table.getObjectType());
          if (includeCatalog) rowCounts.setValue(dsRow, catalogIndex, table.getCatalog());
          if (includeSchema) rowCounts.setValue(dsRow, schemaIndex, table.getSchema());
        }
      }
      finally
      {
        JdbcUtils.closeResult(rs);
        processed.add(table);
      }
    }

    if (rowMonitor != null)
    {
      rowMonitor.jobFinished();
    }

    SortDefinition sortDef = getRowCountSort(sort, rowCounts, currentConnection);
    if (sortDef.hasColumns())
    {
      rowCounts.sort(sortDef);
    }
    rowCounts.setResultName(VERB);
    ResultNameAnnotation.setResultName(rowCounts, sql);
    rowCounts.setGeneratingSql(sql);
    rowCounts.resetStatus();
    result.addDataStore(rowCounts);

    return result;
  }

  public static String getDefaultSortConfig()
  {
    return Settings.getInstance().getProperty("workbench.sql.wbrowcount.sortdef", "name;a").toLowerCase();
  }

  public static SortDefinition getDefaultRowCountSort(DataStore rowCounts, WbConnection connection)
  {
    return getRowCountSort(getDefaultSortConfig(), rowCounts, connection);
  }

  public static SortDefinition getRowCountSort(String namedSort, DataStore rowCounts, WbConnection connection)
  {
    if (namedSort == null) return new SortDefinition();

    DbMetadata meta = connection.getMetadata();
    // the column name for rowcount used in the DataStore is localized (see buildResultDataStore(), so we need to
    // replace the name with the real one before creating the SortDefinition
    String sort = namedSort.replace("rowcount", rowCounts.getColumnName(0).toLowerCase());

    // The schema and catalog columns might not be called that depending on the JDBC driver
    String schema = meta.getSchemaTerm().toLowerCase();
    sort = sort.replace("schema", schema);

    String catalog = meta.getCatalogTerm().toLowerCase();
    sort = sort.replace("catalog", catalog);

    NamedSortDefinition namedSortDef = NamedSortDefinition.parseDefinitionString(sort);
    return namedSortDef.getSortDefinition(rowCounts);
  }

  @Override
  public boolean isWbCommand()
  {
    return true;
  }

  @Override
  public boolean shouldEndTransaction()
  {
    return true;
  }

}
