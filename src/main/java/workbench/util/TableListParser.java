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
package workbench.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.sql.formatter.WbSqlFormatter;
import workbench.sql.lexer.SQLLexer;
import workbench.sql.lexer.SQLLexerFactory;
import workbench.sql.lexer.SQLToken;
import workbench.sql.parser.ParserType;

import static workbench.util.SqlParsingUtil.*;


/**
 *
 * @author Thomas Kellerer
 */
public class TableListParser
{
  private char catalogSeparator = '.';
  private char schemaSeparator = '.';
  private ParserType parserType = ParserType.Standard;

  public TableListParser()
  {
  }

  public TableListParser(ParserType type)
  {
    this('.', '.', type);
  }

  public TableListParser(char catSep, char schemaSep, ParserType type)
  {
    parserType = type;
    catalogSeparator = catSep;
    schemaSeparator = schemaSep;
  }

  public List<Alias> getTables(String sql, boolean includeAlias)
  {
    if (StringUtil.isBlank(sql)) Collections.emptyList();
    SQLLexer lexer = SQLLexerFactory.createLexer(parserType, "");

    lexer.setInput(sql);
    SQLToken firstToken = lexer.getNextToken(false, false);
    if (firstToken != null && "TABLE".equalsIgnoreCase(firstToken.getContents()))
    {
      // special case "TABLE foobar;" (currently only supported by Postgres
      // the next token has to be the table name
      SQLToken tableToken = lexer.getNextToken(false, false);
      if (tableToken != null)
      {
        return CollectionUtil.arrayList(new TableAlias(tableToken.getContents()));
      }
      Collections.emptyList();
    }

    sql = SqlUtil.trimSemicolon(sql);

    int fromPos = getKeywordPosition(Collections.singleton("FROM"), sql, 0, lexer);
    if (fromPos < 0) return Collections.emptyList();
    //String fromPart = SqlParsingUtil.getFromPart(sql, lexer);

    //if (StringUtil.isBlank(fromPart)) return Collections.emptyList();
    List<Alias> result = new ArrayList<>();

    try
    {
      //lexer.setInput(fromPart);
      boolean lastTokenWasAlias = false;
      SQLToken t = lexer.getNextToken(false, false);

      boolean collectTable = true;
      int bracketCount = 0;
      boolean subSelect = false;
      int subSelectBracketCount = -1;

      while (t != null)
      {
        String s = t.getContents();
        if (WbSqlFormatter.FROM_TERMINAL.contains(s) && bracketCount == 0) break;

        if (s.equals("SELECT") && bracketCount > 0)
        {
          subSelect = true;
          subSelectBracketCount = bracketCount;
        }

        if ("(".equals(s))
        {
          if (lastTokenWasAlias)
          {
            t = skipAliasColumns(lexer, t);
            lastTokenWasAlias = false;
            if (t != null && t.getContents().equals("AS"))
            {
              collectTable = true;
            }
            else
            {
              collectTable = bracketCount == 0;
            }
            continue;
          }
          else
          {
            bracketCount ++;
          }
        }
        else if (")".equals(s))
        {
          if (subSelect && bracketCount == subSelectBracketCount)
          {
            subSelect = false;
          }
          bracketCount --;
          t = lexer.getNextToken(false, false);

          // An AS keyword right after a closing ) means this introduces the
          // alias for the derived table. We can skip this token
          if (t != null && t.getContents().equals("AS"))
          {
            collectTable = true;
          }
          else
          {
            collectTable = bracketCount == 0;
          }
          continue;
        }

        if (!subSelect)
        {
          if (SqlUtil.getJoinKeyWords().contains(s))
          {
            collectTable = true;
          }
          else if (",".equals(s))
          {
            collectTable = true;
          }
          else if ("ON".equals(s) || "USING".equals(s))
          {
            collectTable = false;
          }
          else if (collectTable && !s.equals("(") && !s.equalsIgnoreCase("LATERAL"))
          {
            collectTable = false;
            lastTokenWasAlias = true;
            Alias table = new Alias();
            table.setStartPositionInQuery(t.getCharBegin());

            if (!t.getContents().equals("AS"))
            {
              SQLToken lt = t;
              t = collectToWhiteSpace(lexer, t, table);
              if (t != null && "(".equals(t.getContents()))
              {
                table.setEndPositionInQuery(lt.getCharEnd());
                continue;
              }
            }

            if (t != null && t.isWhiteSpace())
            {
              t = lexer.getNextToken(false, false);
            }

            if (t != null && t.getContents().equals("AS"))
            {
              table.setAsKeyword(t.getText());
              // the next item must be the alias
              t = lexer.getNextToken(false, false);
              table.setAlias(t != null ? t.getText() : null);
              if (t != null)
              {
                table.setEndPositionInQuery(t.getCharEnd());
              }
              result.add(table);
            }
            else if (t != null && t.isIdentifier())
            {
              table.setAlias(t.getText());
              table.setEndPositionInQuery(t.getCharEnd());
              result.add(table);
            }
            else
            {
              if (t != null) table.setEndPositionInQuery(t.getCharEnd());
              result.add(table);
              continue;
            }
          }
        }
        t = lexer.getNextToken(false, false);
      }

      if (!includeAlias)
      {
        for (Alias a : result)
        {
          a.setAlias(null);
        }
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error parsing sql", e);
    }
    return result;
  }

  private SQLToken skipAliasColumns(SQLLexer lexer, SQLToken current)
  {
    if (current == null) return current;
    if (!current.getText().equals("(")) return current;
    SQLToken next = lexer.getNextToken(false, false);
    int bracketCount = 1;
    while (next != null)
    {
      String t = next.getText();
      if ("(".equals(t))
      {
        bracketCount++;
      }
      else if (t.equals(")"))
      {
        bracketCount--;
        if (bracketCount == 0)
        {
          return lexer.getNextToken(false, false);
        }
      }
      next = lexer.getNextToken(false, false);
    }
    return null;
  }

  private SQLToken collectToWhiteSpace(SQLLexer lexer, SQLToken current, Alias table)
  {
    if (!current.isWhiteSpace())
    {
      table.appendObjectName(current.getText());
    }
    SQLToken token = lexer.getNextToken(false, true);
    while (token != null)
    {
      String text = token.getContents();
      if (!isSeparator(text) && (token.isWhiteSpace() ||
                                 token.isOperator() ||
                                 token.isReservedWord() ||
                                 text.equals(",") ||
                                 text.equals("(")))
      {
        break;
      }
      table.appendObjectName(token.getText());
      table.setEndPositionInQuery(token.getCharEnd());
      token = lexer.getNextToken(false, true);
    }
    return token;
  }

  private boolean isSeparator(String text)
  {
    if (text == null || text.isEmpty()) return false;
    return text.charAt(0) == schemaSeparator || text.charAt(0) == catalogSeparator;
  }
}
