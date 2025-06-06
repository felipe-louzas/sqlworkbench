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
package workbench.gui.completion;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;
import workbench.db.objectcache.Namespace;

import workbench.sql.formatter.WbSqlFormatter;
import workbench.sql.lexer.LexerState;
import workbench.sql.lexer.SQLLexer;
import workbench.sql.lexer.SQLLexerFactory;
import workbench.sql.lexer.SQLToken;

import workbench.util.Alias;
import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;
import workbench.util.TableAlias;

/**
 *
 * @author Thomas Kellerer
 */
public class SelectAnalyzer
  extends BaseAnalyzer
{
  private final int NO_JOIN_ON = 0;

  /** Display tables from the database for the JOIN keyword */
  private final int JOIN_ON_TABLE_LIST = 1;

  /** The cursor position is such that the completion should display the tables in the from clause */
  private final int JOIN_FROM_TABLE_LIST = 2;

  /** display columns for a join condition. */
  private final int JOIN_ON_COLUMN_LIST = 3;

  public SelectAnalyzer(WbConnection conn, String statement, int cursorPos)
  {
    super(conn, statement, cursorPos);
  }

  @SuppressWarnings("unchecked")
  @Override
  protected void checkContext()
  {
    this.context = NO_CONTEXT;

    if ("TABLE".equalsIgnoreCase(this.verb))
    {
      this.context = CONTEXT_TABLE_LIST;
      this.namespaceForTableList = getNamespaceFromCurrentWord();
      return;
    }

    this.appendDot = false;
    setColumnPrefix(null);

    int fromPos = parsingUtil.getFromPosition(this.sql);
    int wherePos = -1;
    int joinPos = -1;
    if (fromPos > 0)
    {
      wherePos = parsingUtil.getWherePosition(sql);
      joinPos = parsingUtil.getJoinPosition(sql);
    }

    int groupPos = parsingUtil.getKeywordPosition("GROUP BY", sql);
    int havingPos = parsingUtil.getKeywordPosition("HAVING", sql);
    int orderPos = parsingUtil.getKeywordPosition("ORDER BY", sql);

    int connectPos = -1;
    int connectByPos = parsingUtil.getKeywordPosition("CONNECT BY", sql);
    int startWithPos = parsingUtil.getKeywordPosition("START WITH", sql);

    if (connectByPos > -1 && startWithPos > -1)
    {
      // use the first position as the position for checking if the cursor is located inside
      // the connect by part of the query
      connectPos = Math.min(connectByPos, startWithPos);
    }
    else
    {
      // at least one position was not found, so take the bigger value
      connectPos = Math.max(connectByPos, startWithPos);
    }

    // find the tables from the FROM clause
    List<Alias> tables = SqlUtil.getTables(sql, true, dbConnection);

    boolean afterWhere = (wherePos > 0 && cursorPos > wherePos);
    boolean afterGroup = (groupPos > 0 && cursorPos > groupPos);
    boolean afterOrder = (orderPos > 0 && cursorPos > orderPos);

    if (havingPos > -1 && afterGroup)
    {
      afterGroup = (cursorPos < havingPos);
    }

    if (orderPos > -1 && afterGroup)
    {
      afterGroup = (cursorPos < orderPos);
    }

    boolean afterHaving = (havingPos > 0 && cursorPos > havingPos);
    if (orderPos > -1 && afterHaving)
    {
      afterHaving = (cursorPos < orderPos);
    }

    boolean inSelectList = cursorPos < fromPos;
    boolean inTableList = between(cursorPos, fromPos, joinPos) || between(cursorPos, fromPos, wherePos);
    boolean inWhere =  between(cursorPos, wherePos, orderPos) ||
                       between(cursorPos, wherePos, groupPos) ||
                       between(cursorPos, wherePos, havingPos) ||
                       between(cursorPos, connectPos, groupPos);

    if (inTableList)
    {
      if (inWhere || afterGroup || afterOrder) inTableList = false;
    }

    int joinState = NO_JOIN_ON;
    if (joinPos > 0 && inTableList)
    {
      joinState = inJoinONPart(tables);
      if (joinState == JOIN_ON_COLUMN_LIST)
      {
        inTableList = false;
      }
    }

    if (inTableList && joinState == JOIN_FROM_TABLE_LIST)
    {
      this.context = CONTEXT_FROM_LIST;
      this.appendDot = true;

      this.elements = new ArrayList<>();
      // As we are in the middle of a JOIN ON condition
      // we should only display tables that have already been mentioned
      for (Alias a : tables)
      {
        if (a.getStartPositionInQuery() < 0 || a.getStartPositionInQuery() <= cursorPos)
        {
          elements.add(TableAlias.createFrom(a));
        }
      }

      return;
    }
    else if (inTableList)
    {
      String q = getQualifierLeftOfCursor();
      if (q != null)
      {
        setOverwriteCurrentWord(true);//!this.dbConnection.getMetadata().isKeyword(q));
      }

      // If no FROM is present but there is a word with a dot
      // at the cursor position we will first try to use that
      // as a table name (because usually you type the table name
      // first in the SELECT list. If no columns for that
      // name are found, BaseAnalyzer will try to use that as a
      // schema name.
      if (fromPos < 0 && q != null)
      {
        context = CONTEXT_TABLE_OR_COLUMN_LIST;
        this.tableForColumnList = new TableIdentifier(q, dbConnection);
      }
      else
      {
        context = CONTEXT_TABLE_LIST;
      }

      this.namespaceForTableList = getNamespaceFromCurrentWord();
      if (isCurrentNameSpaceCatalog())
      {
        this.namespaceForTableList = new Namespace(null, namespaceForTableList.toString());
        context = CONTEXT_SCHEMA_LIST;
      }
    }
    else
    {
      context = CONTEXT_COLUMN_LIST;
      // current cursor position is after the WHERE
      // statement or before the FROM statement, so
      // we'll try to find a proper column list

      int tableCount = tables.size();
      this.tableForColumnList = null;

      if (afterGroup)
      {
        this.elements = getColumnsForGroupBy();
        this.addAllMarker = true;
        this.title = ResourceMgr.getString("TxtTitleColumns");
        return;
      }

      if (afterHaving)
      {
        this.elements = getColumnsForHaving();
        this.addAllMarker = false;
        this.title = ResourceMgr.getString("TxtTitleGroupFuncs");
        return;
      }

      this.addAllMarker = !afterWhere;

      // check if the current qualifier is either one of the
      // tables in the table list or one of the aliases used
      // in the table list.
      TableAlias currentAlias = getTableOrAliasLeftOfCursor(tables);
      if (currentAlias == null && parentAnalyzer != null)
      {
        List<TableAlias> outerTables = this.parentAnalyzer.getTables();
        currentAlias = getTableOrAliasLeftOfCursor(outerTables);
      }

      if (currentAlias != null)
      {
        tableForColumnList = currentAlias.getTable();
      }
      else if ((inWhere || inSelectList) && tableCount > 1)
      {
        this.elements = getColumnsForAllTables(tables);
        return;
      }
      else if (tableCount == 1)
      {
        TableAlias tbl = new TableAlias(tables.get(0).getObjectName(), null, catalogSeparator, schemaSeparator);
        tableForColumnList = tbl.getTable();
      }

      // after an ORDER BY but without an alias at the current cursor position:
      // --> display the columns from the select list
      if (afterOrder && currentAlias == null)
      {
        List<String> columns = getColumnsForOrderBy();
        // if the select list only has a single *
        // don't display the columns, from the select, but display the regular list of choices
        if (!isSelectStar(columns))
        {
          this.elements = columns;
          this.addAllMarker = true;
          this.title = ResourceMgr.getString("TxtTitleColumns");
          return;
        }
      }


      if (tableForColumnList == null && currentAlias == null)
      {
        this.context = CONTEXT_FROM_LIST;
        this.addAllMarker = false;
        this.elements = new ArrayList();
        for (Alias entry : tables)
        {
          TableAlias tbl = new TableAlias(entry.getObjectName(), entry.getAlias(), catalogSeparator, schemaSeparator);
          this.elements.add(tbl);
          this.appendDot = true;
        }
      }
      else if (currentAlias != null)
      {
        setColumnPrefix(currentAlias.getNameToUse());
      }
    }

    if (inWhere)
    {
      fkMarker = checkFkLookup();
      if (fkMarker != null && elements != null)
      {
        elements.add(fkMarker);
      }
    }
  }

  private boolean isSelectStar(List<String> columns)
  {
    if (columns.size() != 1) return false;
    return columns.get(0).equals("*");
  }

  private int inJoinONPart(List<Alias> tablesInSelect)
  {
    int result = NO_JOIN_ON;
    final Set<String> joinKeywords = SqlUtil.getJoinKeyWords();

    try
    {
      boolean afterFrom = false;

      SQLLexer lexer = SQLLexerFactory.createLexer(dbConnection, this.sql);
      SQLToken token = lexer.getNextToken(false, false);
      SQLToken lastToken = null;
      LexerState state = new LexerState();
      TableAlias currentAlias = getTableOrAliasLeftOfCursor(tablesInSelect);

      while (token != null)
      {
        state.visit(token);

        String t = token.getContents();

        if (afterFrom)
        {
          if ("ON".equals(t))
          {
            if (cursorPos >= token.getCharEnd())
            {
              if (currentAlias == null)
              {
                // right after the ON
                result = JOIN_FROM_TABLE_LIST;
              }
              else
              {
                // if there is a qualifier, we assume it's a table name or table alias
                result = JOIN_ON_COLUMN_LIST;
              }
            }
          }
          else if ("USING".equals(t))
          {
            if (cursorPos >= token.getCharEnd()) result = JOIN_ON_COLUMN_LIST;
          }
          else if (result == JOIN_ON_COLUMN_LIST && cursorPos <= token.getCharBegin())
          {
            if (currentAlias == null)
            {
              // no alias found, assume the current word is a schema name
              result = JOIN_ON_TABLE_LIST;
            }
            else
            {
              tableForColumnList = currentAlias.getTable();
              break;
            }
            break;
          }
          else if (joinKeywords.contains(t))
          {
            if (lastToken != null && cursorPos > lastToken.getCharEnd() && cursorPos <= token.getCharBegin() && lastToken.getContents().equals("ON"))
            {
              // we are between an ON keyword and the next JOIN keyword
              // --> show all tables that have been listed so far
              return JOIN_FROM_TABLE_LIST;
            }
            else if (lastToken != null && cursorPos > lastToken.getCharEnd() && cursorPos <= token.getCharBegin()
                       && SqlUtil.getJoinKeyWords().contains(lastToken.getContents())  )
            {
              // we are between two JOIN statements without anything else
              result = JOIN_ON_TABLE_LIST;
            }
            else if (cursorPos > token.getCharEnd())
            {
              result = JOIN_ON_TABLE_LIST;
            }
            else
            {
              result = NO_JOIN_ON;
            }
          }
          else if (!state.inParentheses() && WbSqlFormatter.FROM_TERMINAL.contains(t))
          {
            return result;
          }
        }
        else
        {
          if (!state.inParentheses() && WbSqlFormatter.FROM_TERMINAL.contains(t)) break;

          if (t.equals("FROM"))
          {
            if (cursorPos < token.getCharBegin()) return NO_JOIN_ON;
            afterFrom = true;
            result = JOIN_ON_TABLE_LIST;
          }
        }
        lastToken = token;
        token = lexer.getNextToken(false, false);
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error parsing SQL Statement!", e);
    }
    return result;
  }

  private List<ColumnIdentifier> getColumnsForAllTables(List<Alias> tables)
  {
    if (this.dbConnection == null) return null;
    if (CollectionUtil.isEmpty(tables)) return null;

    List<ColumnIdentifier> allColumns = new ArrayList<>();
    for (Alias alias : tables)
    {
      TableAlias tbl = new TableAlias(alias.getObjectName(), null, catalogSeparator, schemaSeparator);
      TableIdentifier table = tbl.getTable();
      List<ColumnIdentifier> tableColumns = retrieveColumnsForTable(table);
      if (CollectionUtil.isNonEmpty(tableColumns))
      {
        for (ColumnIdentifier col : tableColumns)
        {
          if (tables.size() > 1)
          {
            String tableName = alias.getNameToUse();
            ColumnIdentifier c = new ColumnIdentifier(col.getColumnName(), col.getDataType())
            {
              @Override
              public String getColumnName()
              {
                return tableName + "." + super.getColumnName();
              }

              @Override
              public String toString()
              {
                return getColumnName();
              }
            };
            c.setDbmsType(col.getDbmsType());
            c.setColumnTypeName(col.getColumnTypeName());
            c.setIsNullable(col.isNullable());
            c.setGeneratedExpression(col.getGenerationExpression(), col.getGeneratedColumnType());
            allColumns.add(c);
          }
          else
          {
            allColumns.add(col);
          }
        }
      }
    }

    if (allColumns.isEmpty())
    {
      return null;
    }
    return allColumns;
  }

  private List<String> getColumnsForOrderBy()
  {
    return SqlUtil.getSelectColumns(this.sql, false, dbConnection);
  }

  private List getColumnsForHaving()
  {
    List<String> cols = SqlUtil.getSelectColumns(this.sql, false, dbConnection);
    List<String> validCols = new ArrayList<>();
    for (String col : cols)
    {
      if (col.indexOf('(') > -1 && col.indexOf(')') > -1)
      {
        validCols.add(col);
      }
    }
    return validCols;
  }

  private List getColumnsForGroupBy()
  {
    List<String> cols = SqlUtil.getSelectColumns(this.sql, false, dbConnection);
    List<String> validCols = new ArrayList<>();
    String[] funcs = new String[]{"sum", "count", "avg", "min", "max" };
    StringBuilder regex = new StringBuilder(50);
    for (int i = 0; i < funcs.length; i++)
    {
      if (i > 0) regex.append('|');
      regex.append("\\s*");
      regex.append(funcs[i]);
      regex.append("\\s*\\(");
    }
    Pattern aggregate = Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
    for (String col : cols)
    {
      if (StringUtil.findPattern(aggregate, col, 0) == -1)
      {
        validCols.add(col);
      }
    }
    return validCols;
  }

  /**
   * This will only return tables in the FROM clause to
   * support correlated sub-queries
   */
  @Override
  public List<TableAlias> getTables()
  {
    List<Alias> tables = SqlUtil.getTables(sql, true, dbConnection);
    List<TableAlias> result = new ArrayList<>(tables.size());
    for (Alias s : tables)
    {
      if (s.getObjectName() != null)
      {
        result.add(TableAlias.createFrom(s));
      }
    }
    return result;
  }
}
