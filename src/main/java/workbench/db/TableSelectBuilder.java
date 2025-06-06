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

import java.sql.SQLException;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.sqltemplates.TemplateHandler;

import workbench.sql.annotations.ResultNameAnnotation;

import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class TableSelectBuilder
{
  public static final String COLUMN_PLACEHOLDER = "${column}";
  public static final String TABLEDATA_TEMPLATE_NAME = "tabledata";
  public static final String ROWCOUNT_TEMPLATE_NAME = "tablerowcount";

  public static final String LIMIT_EXPRESSION_PLACEHOLDER = "%limit_expression%";
  public static final String ORDER_BY_PLACEHOLDER = "%order_by%";
  public static final String MAX_ROWS_PLACEHOLDER = "%max_rows%";

  private WbConnection dbConnection;
  private boolean includeBLOBColumns = true;
  private boolean includeCLOBColumns = true;
  private boolean useColumnAlias;
  private boolean sortPksFirst;
  private String sqlTemplate;
  private String limitClause;
  private boolean neverUseFQN = false;

  public TableSelectBuilder(WbConnection source)
  {
    this(source, null, null);
  }

  public TableSelectBuilder(WbConnection source, String templateKey)
  {
    this(source, templateKey, null);
  }

  public TableSelectBuilder(WbConnection source, String templateKey, String fallbackTemplate)
  {
    this.dbConnection = source;
    if (this.dbConnection != null)
    {
      if (StringUtil.isNotBlank(templateKey))
      {
        sqlTemplate = dbConnection.getDbSettings().getTableSelectTemplate(templateKey.toLowerCase());
      }
      if (StringUtil.isBlank(sqlTemplate) && StringUtil.isNotBlank(fallbackTemplate))
      {
        sqlTemplate = dbConnection.getDbSettings().getTableSelectTemplate(fallbackTemplate.toLowerCase());
      }
      limitClause = dbConnection.getDbSettings().getLimitClause();
    }

    if (StringUtil.isBlank(sqlTemplate))
    {
      sqlTemplate = "SELECT " + MetaDataSqlManager.COLUMN_LIST_PLACEHOLDER + "\nFROM " + MetaDataSqlManager.TABLE_EXPRESSION_PLACEHOLDER;
    }
  }

  public void setNeverUseFQN(boolean flag)
  {
    this.neverUseFQN = flag;
  }

  public void setSortPksFirst(boolean flag)
  {
    this.sortPksFirst = flag;
  }

  /**
   * For testing purposes only.
   * @param template
   */
  void setSqlTemplate(String template)
  {
    this.sqlTemplate = template;
  }

  public void setIncludeBLOBColumns(boolean flag)
  {
    includeBLOBColumns = flag;
  }

  public void setIncludeCLOBColumns(boolean flag)
  {
    includeCLOBColumns = flag;
  }

  public void setUseColumnAlias(boolean flag)
  {
    this.useColumnAlias = flag;
  }

  public String getSelectForCount(TableIdentifier table)
  {
    if (table == null)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Not table supplied!");
      return null;
    }
    String select = replacePlaceholders(table, "count(*)", -1);
    select = TemplateHandler.removePlaceholder(select, TableSelectBuilder.ORDER_BY_PLACEHOLDER, false);
    return select;
  }

  /**
   * Returns a SELECT statement that selects all columns from the table.
   *
   * The table columns will be retrieved to build the SELECT statement.
   *
   * @param table   the table for which the SELECT should be created
   * @return a SQL to retrieve all columns and rows from the table
   *
   * @throws SQLException
   * @see DbMetadata#getTableDefinition(workbench.db.TableIdentifier)
   */
  public String getSelectForTable(TableIdentifier table, int maxRows)
    throws SQLException
  {
    TableIdentifier tbl = null;

    if (!table.getNeverAdjustCase() || table.getType() == null || table.getSchema() == null)
    {
      // If neverAdjustCase() has been set, the TableIdentifier was system generated.
      // If no type or schema is present, it is most probably a user supplied value and thus we need to resolve synonyms
      // This is not done by default because resolving the synonym is quite costly especially on Oracle!
      tbl = dbConnection.getMetadata().getSynonymTable(table);
    }
    TableDefinition def = dbConnection.getMetadata().getTableDefinition(tbl == null ? table : tbl, false);
    return getSelectForColumns(def.getTable(), def.getColumns(), maxRows);
  }

  public String getSelectForTableData(TableIdentifier table, List<ColumnIdentifier> columns, boolean withAnnotation)
  {
    String sql = getSelectForColumns(table, columns, -1);
    StringBuilder select = new StringBuilder(sql.length() + 40);
    if (withAnnotation)
    {
      select.append("-- @");
      select.append(ResultNameAnnotation.ANNOTATION);
      select.append(' ');
      select.append(table.getTableName());
      select.append('\n');
    }
    select.append(sql.trim());
    select.append(';');
    return select.toString();
  }

  /**
   * Create a SELECT statement for the columns of the the table.
   *
   * If replacements for certain datatypes are configured, an expression to convert the column
   * will be used instead of the column directly. The expression will be given the column name as an alias.
   *
   * @param table    the table to retrieve
   * @param columns  the columns to use
   * @return a SELECT for all rows in the table
   * @see #getColumnExpression(workbench.db.ColumnIdentifier)
   */
  public String getSelectForColumns(TableIdentifier table, List<ColumnIdentifier> columns, int maxRows)
  {
    return getSelectForColumns(table, columns, null, maxRows);
  }

  public String getSelectForColumns(TableIdentifier table, List<ColumnIdentifier> columns, String sortCols, int maxRows)
  {
    if (table == null)
    {
      LogMgr.logWarning(new CallerInfo(){}, "No table supplied!");
      return null;
    }

    StringBuilder selectCols = new StringBuilder(columns == null ? 5 : columns.size() * 15);

    if (CollectionUtil.isEmpty(columns))
    {
      selectCols.append("*");
    }
    else
    {
      int colsInList = 0;

      if (sortPksFirst)
      {
        columns = ColumnIdentifier.sortPksFirst(columns);
      }

      for (ColumnIdentifier column : columns)
      {
        String expr = null;
        int type  = column.getDataType();
        String dbmsType = column.getDbmsType();
        if (dbmsType == null)
        {
          dbmsType = SqlUtil.getTypeName(type);
        }

        boolean isBlob = SqlUtil.isBlobType(type);
        boolean isClob = SqlUtil.isClobType(type);

        if (isClob)
        {
          if (includeCLOBColumns) expr = getColumnExpression(column);
        }
        else if (isBlob)
        {
          if (includeBLOBColumns) expr = getColumnExpression(column);
        }
        else
        {
          expr = getColumnExpression(column);
        }

        if (expr != null)
        {
          if (colsInList > 0) selectCols.append(",\n");
          if (colsInList > 0) selectCols.append("       ");
          selectCols.append(expr);
          colsInList ++;
        }
      }
    }

    String select = replacePlaceholders(table, selectCols, maxRows);
    select = applyOrderBy(select, sortCols);

    return select;
  }

  private String applyOrderBy(String sql, String sortCols)
  {
    if (StringUtil.isNotBlank(sortCols))
    {
      String orderBy = " \nORDER BY " + sortCols;
      if (sql.contains(ORDER_BY_PLACEHOLDER))
      {
        sql = TemplateHandler.replacePlaceholder(sql, TableSelectBuilder.ORDER_BY_PLACEHOLDER, orderBy, true);
      }
      else
      {
        sql += orderBy;
      }
    }
    else
    {
      sql = TemplateHandler.removePlaceholder(sql, TableSelectBuilder.ORDER_BY_PLACEHOLDER, false);
    }
    return sql;
  }

  private String replacePlaceholders(TableIdentifier table, CharSequence selectCols, int maxRows)
  {
    String select = sqlTemplate.replace(MetaDataSqlManager.COLUMN_LIST_PLACEHOLDER, selectCols);

    if (neverUseFQN)
    {
      TableIdentifier tbl = new TableIdentifier(table.getCatalog(), table.getSchema(), table.getTableName())
      {
        @Override
        public String getTableExpression(WbConnection conn)
        {
          return super.buildTableExpression(dbConnection.getMetadata(), null, SqlUtil.getCatalogSeparator(conn), SqlUtil.getSchemaSeparator(conn));
        }

        @Override
        public String getTableExpression(char catalogSeparator, char schemaSeparator)
        {
          return getTableExpression(dbConnection);
        }

        @Override
        public String getTableExpression()
        {
          return getTableExpression(dbConnection);
        }

        @Override
        public String getFullyQualifiedName(WbConnection con)
        {
          return getTableExpression(dbConnection);
        }
      };

      select = TemplateHandler.replaceTablePlaceholder(select, tbl, null);
    }
    else
    {
      select = TemplateHandler.replaceTablePlaceholder(select, table, dbConnection);
    }

    select = applyLimit(select, maxRows);

    return select;
  }

  public String getColumnExpression(ColumnIdentifier col)
  {
    String colname = dbConnection.getMetadata().quoteObjectname(col.getColumnName());
    DbSettings db = dbConnection.getDbSettings();
    if (db == null) return colname;

    String type = SqlUtil.getPlainTypeName(col.getDbmsType());
    String expr = db.getDataTypeSelectExpression(type);
    if (expr == null)
    {
      return colname;
    }

    // If an expression without the placeholder was defined ignore the definition
    if (!expr.contains(COLUMN_PLACEHOLDER))
    {
      LogMgr.logError(new CallerInfo(){}, "Expression without " + COLUMN_PLACEHOLDER + " specified for datatype '" + type + "': " + expr, null);
      return colname;
    }

    expr = expr.replace(COLUMN_PLACEHOLDER, colname);
    if (useColumnAlias)
    {
      expr = expr + " AS " + colname;
    }
    return expr;
  }

  /**
   * Return the SQL expression that limits the returned rows.
   *
   * The result of this function is used as a replacement for the {@link #LIMIT_EXPRESSION_PLACEHOLDER} parameter
   * in the final SELECT statement
   * @param maxRows   the max number of rows. if <= 0 an empty string is returned.
   * @return The expression to be used
   */
  public String getLimitExpression(int maxRows)
  {
    if (maxRows <= 0) return StringUtil.EMPTY_STRING;
    if (StringUtil.isEmpty(limitClause)) return StringUtil.EMPTY_STRING;
    if (!limitClause.contains(MAX_ROWS_PLACEHOLDER)) return StringUtil.EMPTY_STRING;
    return limitClause.replace(MAX_ROWS_PLACEHOLDER, Integer.toString(maxRows));
  }

  public String applyLimit(String baseSelect, int maxRows)
  {
    String expression = getLimitExpression(maxRows);
    return TemplateHandler.replacePlaceholder(baseSelect, LIMIT_EXPRESSION_PLACEHOLDER, expression, false);
  }

  // =========================================

  void setTemplate(String template)
  {
    this.sqlTemplate = template;
  }

  void setLimitClause(String limit)
  {
    this.limitClause = limit;
  }
}
