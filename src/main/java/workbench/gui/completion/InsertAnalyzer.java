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

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.TableIdentifier;
import workbench.db.WbConnection;
import workbench.db.objectcache.Namespace;

import workbench.sql.lexer.SQLLexer;
import workbench.sql.lexer.SQLLexerFactory;
import workbench.sql.lexer.SQLToken;

import workbench.util.SqlUtil;

/**
 * Analyze an UPDATE statement regarding the context for the auto-completion
 * @author Thomas Kellerer
 */
public class InsertAnalyzer
  extends BaseAnalyzer
{

  public InsertAnalyzer(WbConnection conn, String statement, int cursorPos)
  {
    super(conn, statement, cursorPos);
  }

  @Override
  public void checkContext()
  {
    SQLLexer lexer = SQLLexerFactory.createLexer(dbConnection, this.sql);

    int intoEnd = Integer.MAX_VALUE;
    int intoStart = Integer.MAX_VALUE;
    int tableStart = Integer.MAX_VALUE;
    int columnBracketStart = Integer.MAX_VALUE;
    int columnBracketEnd = Integer.MAX_VALUE;
    int valuesPos = Integer.MAX_VALUE;
    boolean inColumnBracket = false;

    String tableName = null;
    try
    {
      int bracketCount = 0;
      boolean nextTokenIsTable = false;
      SQLToken t = lexer.getNextToken(false, false);

      while (t != null)
      {
        String value = t.getContents();
        if ("(".equals(value))
        {
          bracketCount ++;
          // if the INTO keyword was already read but not the VALUES
          // keyword, the opening bracket marks the end of the table
          // definition between INTO and the column list
          if (intoStart != Integer.MAX_VALUE && valuesPos == Integer.MAX_VALUE && columnBracketStart == Integer.MAX_VALUE)
          {
            intoEnd = t.getCharBegin();
            columnBracketStart = t.getCharEnd();
            inColumnBracket = true;
          }
        }
        else if (")".equals(value))
        {
          if (inColumnBracket)
          {
            columnBracketEnd = t.getCharBegin();
          }
          bracketCount --;
          inColumnBracket = false;
        }
        else if (bracketCount == 0)
        {
          if (nextTokenIsTable)
          {
            tableStart = t.getCharBegin();
            if (catalogSeparator != '.')
            {
              StringBuilder tname = new StringBuilder(t.getContents());
              t = SqlUtil.appendCurrentTablename(lexer, tname, catalogSeparator);
              tableName = tname.toString();
            }
            else
            {
              tableName = t.getContents();
            }

            nextTokenIsTable = false;
          }
          if ("INTO".equals(value))
          {
            intoStart = t.getCharEnd();
            nextTokenIsTable = true;
          }
          else if ("VALUES".equals(value))
          {
            valuesPos = t.getCharBegin();
            break;
          }
        }
        t = lexer.getNextToken(false, false);
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error parsing insert statement", e);
      this.context = NO_CONTEXT;
    }

    TableIdentifier table = null;
    if (tableName != null)
    {
      table = new TableIdentifier(tableName, catalogSeparator, SqlUtil.getSchemaSeparator(dbConnection));
      table.adjustCase(dbConnection);
    }

    if (cursorPos > intoStart && cursorPos < intoEnd)
    {
      if (cursorPos > tableStart)
      {
        if (table != null) namespaceForTableList = Namespace.fromTable(table, dbConnection);
      }

      if (namespaceForTableList == null)
      {
        namespaceForTableList = getNamespaceFromCurrentWord();
      }

      context = CONTEXT_TABLE_LIST;
    }
    else if (cursorPos >= columnBracketStart && cursorPos <= columnBracketEnd)
    {
      tableForColumnList = table;
      context = CONTEXT_COLUMN_LIST;
    }
    else if (cursorPos >= valuesPos)
    {
      context = CONTEXT_STATEMENT_PARAMETER;
      tableForColumnList = table;
      InsertColumnMatcher matcher = new InsertColumnMatcher(dbConnection, sql);
      String column = matcher.getInsertColumnName(cursorPos);
      if (column != null)
      {
        elements = new ArrayList();
        elements.add(new SelectFKValueMarker(column, table, false));
      }
    }
  }

}
