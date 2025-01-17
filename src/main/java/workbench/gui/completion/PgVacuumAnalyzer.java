/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer.
 *
 * Licensed under a modified Apache License, Version 2.0 (the "License")
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
package workbench.gui.completion;

import java.util.ArrayList;
import java.util.Set;

import workbench.db.JdbcUtils;
import workbench.db.WbConnection;

import workbench.sql.lexer.SQLLexer;
import workbench.sql.lexer.SQLLexerFactory;
import workbench.sql.lexer.SQLToken;

import workbench.util.CollectionUtil;

import static workbench.gui.completion.BaseAnalyzer.*;

/**
 * A SQL analyzer for Postgres' VACUUM command.
 *
 * @author Thomas Kellerer
 */
public class PgVacuumAnalyzer
  extends BaseAnalyzer
{
  private final Set<String> options = CollectionUtil.caseInsensitiveSet("FULL", "FREEZE", "VERBOSE", "ANALYZE");

  public PgVacuumAnalyzer(WbConnection conn, String statement, int cursorPos)
  {
    super(conn, statement, cursorPos);
    if (JdbcUtils.hasMinimumServerVersion(conn, "11.0"))
    {
      options.add("DISABLE_PAGE_SKIPPING");
    }
    if (JdbcUtils.hasMinimumServerVersion(conn, "12.0"))
    {
      options.add("SKIP_LOCKED");
      options.add("INDEX_CLEANUP");
      options.add("TRUNCATE");
    }
    if (JdbcUtils.hasMinimumServerVersion(conn, "13.0"))
    {
      options.add("PARALLEL");
    }
    if (JdbcUtils.hasMinimumServerVersion(conn, "14.0"))
    {
      options.add("PROCESS_TOAST");
    }
    if (JdbcUtils.hasMinimumServerVersion(conn, "16.0"))
    {
      options.add("PROCESS_MAIN");
      options.add("SKIP_DATABASE_STATS");
      options.add("ONLY_DATABASE_STATS");
      options.add("BUFFER_USAGE_LIMIT");
    }
  }


  @Override
  protected void checkContext()
  {
      SQLLexer lexer = SQLLexerFactory.createLexer(dbConnection, this.sql);
      SQLToken token = lexer.getNextToken(false, false);
      if (token == null)
      {
        return;
      }
      int verbEnd = token.getCharEnd();
      int openBracketPos = -1;
      int closeBracketPos = -1;
      while (token != null)
      {
        if ("(".equals(token.getText()))
        {
          openBracketPos = token.getCharEnd();
        }
        else if (")".equals(token.getText()))
        {
          closeBracketPos = token.getCharBegin();
        }
        token = lexer.getNextToken(false, false);
      }

      if (between(cursorPos, openBracketPos, closeBracketPos))
      {
        context = CONTEXT_VALUE_LIST;
      }
      else if (cursorPos > verbEnd)
      {
        context = CONTEXT_TABLE_LIST;
      }

  }

  @Override
  protected void buildResult()
  {
    if (context == CONTEXT_VALUE_LIST)
    {
      this.elements = new ArrayList<>(options);
    }
    else
    {
      super.buildResult();
    }
  }



}
