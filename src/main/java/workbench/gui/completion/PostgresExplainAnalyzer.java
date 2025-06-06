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
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import workbench.db.JdbcUtils;
import workbench.db.WbConnection;

import workbench.sql.lexer.SQLLexer;
import workbench.sql.lexer.SQLLexerFactory;
import workbench.sql.lexer.SQLToken;

import workbench.util.CaseInsensitiveComparator;
import workbench.util.CollectionUtil;

/**
 * A statement analyzer for Postgres' EXPLAIN statement.
 *
 * It will not handle the statement that is explained. ExplainAnalyzerFactory will
 * take care of creating the correct analyzer depending on the cursor position.
 *
 * @author Thomas Kellerer
 */
public class PostgresExplainAnalyzer
  extends ExplainAnalyzer
{
  public PostgresExplainAnalyzer(WbConnection conn, String statement, int cursorPos)
  {
    super(conn, statement, cursorPos);
  }

  @Override
  protected void checkContext()
  {
    String explain = getExplainSql();
    Set<String> usedOptions = CollectionUtil.caseInsensitiveSet();
    Map<String, List<String>> allOptions = getOptions(dbConnection);

    int analyzePosition = -1;
    int verbosePosition = -1;

    int bracketOpen = explain.indexOf('(');
    boolean use90Options = bracketOpen > -1;
    SQLLexer lexer = SQLLexerFactory.createLexer(dbConnection, explain);
    SQLToken t = lexer.getNextToken(false, false);
    SQLToken last = null;
    String currentWord = null;

    while (t != null)
    {
      if (use90Options)
      {
        if (allOptions.containsKey(t.getContents()))
        {
          usedOptions.add(t.getContents());
        }
      }
      else
      {
        if ("ANALYZE".equalsIgnoreCase(t.getContents()))
        {
          analyzePosition = t.getCharBegin();
        }
        if ("VERBOSE".equalsIgnoreCase(t.getContents()))
        {
          verbosePosition = t.getCharBegin();
        }
      }
      last = t;
      t = lexer.getNextToken(false, false);
      if (last != null && t != null)
      {
        if (cursorPos >= last.getCharEnd() && cursorPos <= t.getCharBegin())
        {
          currentWord = last.getContents();
        }
      }
    }

    if (use90Options)
    {
      if (usedOptions.isEmpty())
      {
        elements = new ArrayList<>(allOptions.keySet());
        context = CONTEXT_SYNTAX_COMPLETION;
      }
      else
      {
        String word = currentWord;
        if (allOptions.containsKey(word))
        {
          elements = allOptions.get(word);
          context = CONTEXT_STATEMENT_PARAMETER;
        }
        else
        {
          elements = CollectionUtil.arrayList();
          for (String option : allOptions.keySet())
          {
            if (!usedOptions.contains(option))
            {
              elements.add(option);
              context = CONTEXT_SYNTAX_COMPLETION;
            }
          }
        }
      }
    }
    else
    {
      if ( (analyzePosition == -1 && verbosePosition == -1)
          || (verbosePosition > -1 && cursorPos <= verbosePosition))
      {
        // no option given yet, the first one must be analyze
        this.elements = CollectionUtil.arrayList("analyze");
        context = CONTEXT_SYNTAX_COMPLETION;
      }
      else if (analyzePosition > -1 && cursorPos >= analyzePosition)
      {
        // ANALYZE is already specified, only option left is verbose
        this.elements = CollectionUtil.arrayList("verbose");
        context = CONTEXT_SYNTAX_COMPLETION;
      }
      else
      {
        context = NO_CONTEXT;
      }
    }
  }

  private Map<String, List<String>> getOptions(WbConnection conn)
  {
    Map<String, List<String>> options = new TreeMap<>(CaseInsensitiveComparator.INSTANCE);
    List<String> booleanValues = CollectionUtil.arrayList("true", "false");
    options.put("analyze", booleanValues);
    options.put("verbose", booleanValues);
    options.put("costs", booleanValues);
    options.put("buffers", booleanValues);
    options.put("format", CollectionUtil.arrayList("text", "xml", "json", "yaml"));
    options.put("timing", booleanValues);
    if (JdbcUtils.hasMinimumServerVersion(conn, "10"))
    {
      options.put("summary", booleanValues);
    }
    if (JdbcUtils.hasMinimumServerVersion(conn, "11"))
    {
      options.put("settings", booleanValues);
    }
    if (JdbcUtils.hasMinimumServerVersion(conn, "13"))
    {
      options.put("wal", booleanValues);
    }
    if (JdbcUtils.hasMinimumServerVersion(conn, "16"))
    {
      options.put("generic_plan", booleanValues);
    }
    if (JdbcUtils.hasMinimumServerVersion(conn, "17"))
    {
      options.put("serialize", List.of("none", "text", "binary"));
      options.put("memory", booleanValues);
    }
    return options;
  }

  @Override
  protected int getStatementStart(String sql)
  {
    Set<String> explainable = CollectionUtil.caseInsensitiveSet(
      "SELECT", "UPDATE", "INSERT", "DELETE", "VALUES", "EXECUTE", "DECLARE", "CREATE", "WITH");

    try
    {
      SQLLexer lexer = SQLLexerFactory.createLexer(dbConnection, sql);
      SQLToken t = lexer.getNextToken(false, false);
      while (t != null)
      {
        if (explainable.contains(t.getContents()))
        {
          return t.getCharBegin();
        }
        t = lexer.getNextToken(false, false);
      }
      return Integer.MAX_VALUE;
    }
    catch (Exception e)
    {
      return Integer.MAX_VALUE;
    }
  }
}
