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

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.sql.lexer.SQLLexer;
import workbench.sql.lexer.SQLLexerFactory;
import workbench.sql.lexer.SQLToken;

/**
 * Analyze an ALTER TABLE statement to provide completion for tables and columns
 * @author Thomas Kellerer
 */
public class AlterTableAnalyzer
  extends BaseAnalyzer
{

  public AlterTableAnalyzer(WbConnection conn, String statement, int cursorPos)
  {
    super(conn, statement, cursorPos);
  }

  @Override
  protected void checkContext()
  {
    int addPos = -1;
    int modifyPos = -1;
    int tablePos = -1;
    int tableEnd = -1;
    SQLLexer lexer = SQLLexerFactory.createLexer(dbConnection, this.sql);

    try
    {
      SQLToken token = lexer.getNextToken(false, false);
      while (token != null)
      {
        String v = token.getContents();
        if ("TABLE".equalsIgnoreCase(v))
        {
          tablePos = token.getCharEnd();
        }
        else if ("ADD".equalsIgnoreCase(v))
        {
          addPos = token.getCharEnd();
          tableEnd = token.getCharBegin() - 1;
        }
        else if ("MODIFY".equalsIgnoreCase(v) || "DROP".equalsIgnoreCase(v))
        {
          modifyPos = token.getCharEnd();
          tableEnd = token.getCharBegin() - 1;
        }
        token = lexer.getNextToken(false, false);
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error parsing SQL", e);
    }

    namespaceForTableList = getNamespaceFromCurrentWord();

    if (between(cursorPos, tablePos, modifyPos) || between(cursorPos, tablePos, addPos) ||
       (modifyPos == -1 && addPos == -1))
    {
      context = CONTEXT_TABLE_LIST;
      setTableTypeFilter(this.dbConnection.getMetadata().getTableTypes());
    }
    else if (modifyPos > -1 && tableEnd > -1)
    {
      String table = this.sql.substring(tablePos, tableEnd).trim();
      context = CONTEXT_COLUMN_LIST;
      tableForColumnList = new TableIdentifier(table);
    }

  }

}
