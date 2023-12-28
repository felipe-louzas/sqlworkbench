/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer.
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
import java.util.Collections;
import java.util.List;
import java.util.Set;

import workbench.db.ColumnIdentifier;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

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
public class MergeAnalyzer
  extends BaseAnalyzer
{
  private Alias sourceAlias;
  private TableIdentifier sourceTable;
  private String sourceQuery;

  private TableAlias targetAlias;
  private TableIdentifier targetTable;

  private int joinPosition = -1;
  private int actionStart = -1;
  private int targetStart;

  public MergeAnalyzer(WbConnection conn, String statement, int cursorPos)
  {
    super(conn, statement, cursorPos);
  }

  @Override
  protected void checkContext()
  {
    SQLLexer lexer = SQLLexerFactory.createLexer(dbConnection, sql);

    SQLToken token = findFirstTargetToken(lexer);

    if (token == null)
    {
      // assuming code completion was invoked with only MERGE INTO
      context = CONTEXT_TABLE_LIST;
      namespaceForTableList = getNamespaceFromCurrentWord();
      return;
    }

    SQLToken lastToken = null;
    boolean nextIsTarget = true;
    boolean nextIsTargetAlias = false;
    int whenMatchedStart = -1;
    int whenNotMatchedStart = -1;
    int insertPos = -1;
    int updatePos = -1;
    int updateSetPos = -1;
    int insertColStart = -1;
    int insertColEnd = -1;
    int valuesListStart = -1;
    int valuesListEnd = -1;

    int usingStart = -1;
    LexerState state = new LexerState();
    while (token != null)
    {
      state.visit(token);
      if (token.isIdentifier() && nextIsTarget)
      {
        targetTable = new TableIdentifier(token.getText(), dbConnection);
        nextIsTargetAlias = true;
        nextIsTarget = false;
      }
      else if (token.isIdentifier() && nextIsTargetAlias)
      {
        nextIsTargetAlias = false;
        targetAlias = new TableAlias(token.getText(), catalogSeparator, schemaSeparator);
        if (targetTable != null)
        {
          targetAlias.setTableIdentifier(targetTable);
        }
      }
      else if ("USING".equalsIgnoreCase(token.getText()))
      {
        usingStart = token.getCharEnd();
        nextIsTargetAlias = false;
        lastToken = token;
        token = processUsingClause(lexer);
        continue;
      }
      else if ("MATCHED".equalsIgnoreCase(token.getContents()) && token.getCharBegin() > usingStart)
      {
        whenMatchedStart = token.getCharEnd();
      }
      else if ("NOT MATCHED".equalsIgnoreCase(token.getContents()) && token.getCharBegin() > usingStart)
      {
        whenNotMatchedStart = token.getCharEnd();
      }
      else if ("INSERT".equalsIgnoreCase(token.getContents()) && whenNotMatchedStart > -1)
      {
        insertPos = token.getCharEnd();
      }
      else if ("UPDATE".equalsIgnoreCase(token.getContents()) && whenMatchedStart > -1)
      {
        updatePos = token.getCharEnd();
      }
      else if ("SET".equalsIgnoreCase(token.getContents()) && token.getCharBegin() > updatePos)
      {
        updateSetPos = token.getCharEnd();
      }
      else if ("(".equals(token.getText()) && lastToken != null && "INSERT".equals(lastToken.getContents()) && token.getCharEnd() >= insertPos)
      {
        insertColStart = token.getCharEnd();
        lastToken = token;
        token = skipToClosingParens(lexer);
        if (token != null)
        {
          insertColEnd = token.getCharBegin();
        }
        continue;
      }
      else if ("(".equals(token.getText()) && lastToken != null && "VALUES".equals(lastToken.getContents()) && token.getCharEnd() >= insertPos)
      {
        valuesListStart = token.getCharEnd();
        lastToken = token;
        token = skipToClosingParens(lexer);
        if (token != null)
        {
          valuesListEnd = token.getCharBegin();
        }
        continue;
      }
      lastToken = token;
      token = lexer.getNextToken(false, false);
    }

    int endOfUpdate = - 1;
    if (insertPos > -1)
    {
      endOfUpdate = insertPos;
    }

    String word = getQualifierLeftOfCursor();
    if (between(cursorPos, targetStart, usingStart))
    {
      context = CONTEXT_TABLE_LIST;
      namespaceForTableList = getNamespaceFromCurrentWord();
    }
    else if (between(cursorPos, joinPosition, actionStart))
    {
      // somewhere in the ON clause that joins the source with the target
      if (word == null)
      {
        context = CONTEXT_TABLE_LIST;
        elements = new ArrayList<>();
        if (sourceAlias != null)
        {
          elements.add(sourceAlias);
        }
        if (targetAlias != null)
        {
          elements.add(targetAlias);
        }
        this.addAllMarker = false;
      }
      else if (isTargetAlias(word))
      {
        context = CONTEXT_COLUMN_LIST;
        tableForColumnList = targetTable;
      }
      else if (isSourceAlias(word))
      {
        context = CONTEXT_COLUMN_LIST;
        if (sourceTable != null)
        {
          tableForColumnList = sourceTable;
        }
        else if (this.sourceQuery != null)
        {
          elements = SqlUtil.getSelectColumns(sourceQuery, false, dbConnection);
        }
      }
    }
    else if (updatePos > -1 && between(cursorPos, updateSetPos, endOfUpdate))
    {
      // somewhere in the THEN UPDATE part
      context = CONTEXT_COLUMN_LIST;

      String alias = getQualifierLeftOfCursor();
      String text = StringUtil.findWordLeftOfCursor(sql, cursorPos);
      if (isTargetAlias(alias) || "SET".equalsIgnoreCase(text) || text.endsWith(","))
      {
        tableForColumnList = targetTable;
      }
      else if (isSourceAlias(alias) || "=".equals(text))
      {
        if (sourceTable != null)
        {
          tableForColumnList = sourceTable;
        }
        else if (this.sourceQuery != null)
        {
          elements = SqlUtil.getSelectColumns(sourceQuery, false, dbConnection);
        }
      }
    }
    else if (between(cursorPos, insertColStart, insertColEnd))
    {
      // inside the target columns of the INSERT part
      context = CONTEXT_COLUMN_LIST;
      tableForColumnList = targetTable;
    }
    else if (between(cursorPos, valuesListStart, valuesListEnd))
    {
      // inside the VALUES of the INSERT part
      context = CONTEXT_COLUMN_LIST;
      if (sourceTable != null)
      {
        tableForColumnList = sourceTable;
      }
      else if (this.sourceQuery != null)
      {
        elements = SqlUtil.getSelectColumns(sourceQuery, false, dbConnection);
      }
    }
  }

  private SQLToken findFirstTargetToken(SQLLexer lexer)
  {
    SQLToken token = lexer.getNextToken(false, false);

    Set<String> noiseTokenAfterMerge = CollectionUtil.caseInsensitiveSet("INTO", "ONLY");
    if (token == null) return null;
    boolean inTOP = false;
    boolean nextIsTarget = false;
    while (token != null)
    {
      String text = token.getText();
      if (nextIsTarget)
      {
        return token;
      }

      if (noiseTokenAfterMerge.contains(text))
      {
        nextIsTarget = true;
        targetStart = token.getCharEnd();
      }
      else if ("TOP".equalsIgnoreCase(text))
      {
        inTOP = true;
      }
      else if (inTOP && "(".equalsIgnoreCase(text))
      {
        inTOP = false;
        token = skipToClosingParens(lexer);
        continue;
      }
      token = lexer.getNextToken(false, false);
    }
    return token;
  }


  private boolean isTargetAlias(String word)
  {
    return this.targetAlias != null && targetAlias.isTableOrAlias(word, catalogSeparator, schemaSeparator);
  }

  private boolean isSourceAlias(String word)
  {
    return (this.sourceAlias != null && sourceAlias.getNameToUse().equalsIgnoreCase(word));
  }

  private SQLToken processUsingClause(SQLLexer lexer)
  {
    Set<String> actionVerbs = CollectionUtil.caseInsensitiveSet("MATCHED", "NOT MATCHED");
    boolean nextIsAlias = false;
    boolean firstIdentifier = true;

    SQLToken token = lexer.getNextToken(false, false);
    SQLToken lastToken = null;
    while (token != null)
    {
      String text = token.getText();
      if (nextIsAlias && token.isIdentifier())
      {
        sourceAlias = new Alias(text);
        nextIsAlias = false;
        firstIdentifier = false;
      }
      else if ("ON".equalsIgnoreCase(text))
      {
        nextIsAlias = false;
        joinPosition = token.getCharEnd();
      }
      else if ("(".equals(text))
      {
        token = skipToClosingParens(lexer);
        nextIsAlias = firstIdentifier;
      }
      else if (token.isIdentifier() && firstIdentifier)
      {
        sourceTable = new TableIdentifier(token.getText());
        firstIdentifier = false;
        nextIsAlias = true;
      }
      else if (actionVerbs.contains(text) && actionStart == -1 && lastToken != null && "WHEN".equalsIgnoreCase(lastToken.getText()))
      {
        actionStart = token.getCharBegin();
        return token;
      }
      lastToken = token;
      token = lexer.getNextToken(false, false);
    }
    return null;
  }

  public TableAlias getTargetAlias()
  {
    return targetAlias;
  }

  public TableIdentifier getTargetTable()
  {
    return targetTable;
  }

  public List<ColumnIdentifier> getSourceColumns()
  {
    return Collections.emptyList();
  }

  public TableIdentifier getSourceTable()
  {
    return sourceTable;
  }

  /**
   * Returns the alias for the source (which can be either a table or a query)
   */
  public Alias getSourceAlias()
  {
    return sourceAlias;
  }


  public static BaseAnalyzer createAnalyzer(WbConnection dbConnection, String sql, int cursorPos)
  {
    // find the source based on the USING keyword
    // this can be a direct table reference of a sub-query
    SQLLexer lexer = SQLLexerFactory.createLexer(dbConnection, sql);
    SQLToken token = lexer.getNextToken(false, false);
    LexerState state = new LexerState(0);
    boolean lastWasUSING = false;
    int subQueryStart = -1;
    int subQueryEnd = -1;
    Set<String> usingTerminals = CollectionUtil.caseInsensitiveSet("ON", "AS");
    String sourceQuery = null;

    while (token != null)
    {
      state.visit(token);
      if (lastWasUSING && state.inParentheses())
      {
        subQueryStart = token.getCharEnd();
        token = skipToClosingParens(lexer);
        if (token != null)
        {
          lastWasUSING = false;
          state.visit(token);
          subQueryEnd = token.getCharBegin();
          break;
        }
      }
      else if ("USING".equalsIgnoreCase(token.getText()))
      {
        lastWasUSING = true;
      }
      else if (usingTerminals.contains(token.getText()))
      {
        lastWasUSING = false;
      }
      token = lexer.getNextToken(false, false);
    }

    if (subQueryStart > 0 && subQueryEnd > subQueryStart)
    {
      sourceQuery = sql.substring(subQueryStart, subQueryEnd);
      if (cursorPos >= subQueryStart && cursorPos < subQueryEnd)
      {
        return new SelectAnalyzer(dbConnection, sourceQuery, cursorPos - subQueryStart);
      }
    }
    MergeAnalyzer merge = new MergeAnalyzer(dbConnection, sql, cursorPos);
    merge.sourceQuery = sourceQuery;
    return merge;
  }

  private static SQLToken skipToClosingParens(SQLLexer lexer)
  {
    LexerState state = new LexerState(1);
    SQLToken token = lexer.getNextToken(false, false);
    while (token != null)
    {
      state.visit(token);
      if (state.getParenthesesCount() == 0) return token;
      token = lexer.getNextToken(false, false);
    }
    return null;
  }

  protected boolean inUsingClause()
  {
    return false;
  }

}
