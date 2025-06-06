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

import workbench.db.TableIdentifier;
import workbench.db.WbConnection;
import workbench.db.objectcache.Namespace;

import workbench.sql.formatter.WbSqlFormatter;
import workbench.sql.lexer.SQLLexer;
import workbench.sql.lexer.SQLLexerFactory;
import workbench.sql.lexer.SQLToken;

import workbench.util.SqlUtil;
import workbench.util.TableAlias;

import static workbench.gui.completion.BaseAnalyzer.*;

/**
 * Analyze an UPDATE statement regarding the context for the auto-completion
 * @author Thomas Kellerer
 */
public class UpdateAnalyzer
  extends BaseAnalyzer
{
  public UpdateAnalyzer(WbConnection conn, String statement, int cursorPos)
  {
    super(conn, statement, cursorPos);
  }

  @Override
  protected void checkContext()
  {
    checkOverwrite();

    final int IN_SET = 1;
    final int IN_UPDATE = 2;
    final int IN_WHERE = 3;

    int state = -1;
    boolean collectTable = false;
    String table = null;

    SQLLexer lexer = SQLLexerFactory.createLexer(dbConnection, sql);
    SQLToken t = lexer.getNextToken(false, false);
    SQLToken last = null;

    while (t != null)
    {
      if (t.getContents().equals("SET"))
      {
        collectTable = false;
        if (cursorPos > t.getCharEnd())
        {
          state = IN_SET;
        }
      }
      else if (WbSqlFormatter.SET_TERMINAL.contains(t.getContents()))
      {
        if (cursorPos > t.getCharEnd())
        {
          state = IN_WHERE;
        }
      }
      else if (t.getContents().equals("UPDATE") || t.getContents().equals("ONLY"))
      {
        collectTable = true;
        if (cursorPos > t.getCharEnd())
        {
          state = IN_UPDATE;
        }
      }
      else if (t.getContents().equals("(") && last != null && last.getText().equalsIgnoreCase("ONLY"))
      {
        collectTable = true;
      }
      else if (collectTable && t.getContents().equals(")"))
      {
        collectTable = false;
      }
      else if (collectTable)
      {
        if (table == null)
        {
          table = t.getText();
        }
        else
        {
          table += t.getText();
        }
      }
      last = t;
      t = lexer.getNextToken(false, false);
    }

    if (state == IN_UPDATE)
    {
      context = CONTEXT_TABLE_LIST;
      this.namespaceForTableList = getNamespaceFromCurrentWord();
      if (isCurrentNameSpaceCatalog())
      {
        this.namespaceForTableList = new Namespace(null, namespaceForTableList.toString());
        context = CONTEXT_SCHEMA_LIST;
      }
    }
    else
    {
      // "inside" the SET and after the WHERE we always need the column list
      if (table != null)
      {
        context = CONTEXT_COLUMN_LIST;
        tableForColumnList = new TableIdentifier(table, dbConnection);
        fkMarker = checkFkLookup();
      }
    }
  }

  /**
   * Package visible for testing purposes.
   */
  String getCurrentColumn()
  {
    List<ColumnInfo> columns = getColumns();
    for (ColumnInfo col : columns)
    {
      if (cursorPos >= col.valueStartPos && cursorPos <= col.valueEndPos)
      {
        return col.name;
      }
    }
    return null;
  }

  /**
   * Package visible for testing purposes.
   */
  List<ColumnInfo> getColumns()
  {
    List<ColumnInfo> result = new ArrayList<>();

    SQLLexer lexer = SQLLexerFactory.createLexer(dbConnection, sql);
    SQLToken t = lexer.getNextToken(false, false);
    SQLToken lastToken = null;
    boolean inColumns = false;
    boolean nextIsColumn = false;

    while (t != null)
    {
      String text = t.getContents();

      if (inColumns)
      {
        if (WbSqlFormatter.SET_TERMINAL.contains(text))
        {
          nextIsColumn = true;
          ColumnInfo last = (result.size() > 0 ? result.get(result.size()-1) : null);
          if (last != null && last.valueEndPos == 0)
          {
            last.valueEndPos = t.getCharBegin();
          }
        }
        else if (nextIsColumn && !t.isReservedWord())
        {
          ColumnInfo col = new ColumnInfo();
          col.name = text;
          col.valueStartPos = 0;
          col.valueEndPos = 0;
          result.add(col);
          nextIsColumn = false;
        }
        else if (text.equals(","))
        {
          nextIsColumn = true;
          ColumnInfo last = (result.size() > 0 ? result.get(result.size()-1) : null);
          if (last != null)
          {
            last.valueEndPos = t.getCharBegin();
          }
        }
        else if (t.isLiteral() && (lastToken != null && lastToken.getText().equals("=")))
        {
          ColumnInfo last = (result.size() > 0 ? result.get(result.size()-1) : null);
          if (last != null)
          {
            last.valueEndPos = t.getCharEnd();
          }
        }
        else if (t.isOperator())
        {
          nextIsColumn = false;
          ColumnInfo last = (result.size() > 0 ? result.get(result.size()-1) : null);
          if (last != null)
          {
            last.valueStartPos = t.getCharEnd();
          }
        }
      }
      else if (text.equalsIgnoreCase("SET"))
      {
        inColumns = true;
        nextIsColumn = true;
      }

      lastToken = t;
      t = lexer.getNextToken(false, false);
    }

    // deal with a dangling operator at the end of the statement (e.g. "where foo = ")
    if (inColumns && lastToken != null && lastToken.isOperator())
    {
      ColumnInfo last = (result.size() > 0 ? result.get(result.size()-1) : null);
      // if the value for the last column does not have "an end", assume it ends with the statement
      if (last != null && last.valueEndPos == 0)
      {
        last.valueEndPos = sql.length();
      }
    }
    return result;
  }

  @Override
  public List<TableAlias> getTables()
  {
    char schemaSep = SqlUtil.getSchemaSeparator(dbConnection);
    String table = SqlUtil.getUpdateTable(this.sql, catalogSeparator, dbConnection);
    TableAlias a = new TableAlias(table, catalogSeparator, schemaSep);
    List<TableAlias> result = new ArrayList<>(1);
    result.add(a);
    return result;
  }

  class ColumnInfo
  {
    String name;
    int valueStartPos;
    int valueEndPos;

    @Override
    public String toString()
    {
      return "Column: " + name + ", value from: " + valueStartPos + " to: " + valueEndPos;
    }
  }

}
