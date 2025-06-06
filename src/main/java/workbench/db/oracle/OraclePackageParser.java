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
package workbench.db.oracle;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ProcedureDefinition;

import workbench.sql.lexer.SQLLexer;
import workbench.sql.lexer.SQLLexerFactory;
import workbench.sql.lexer.SQLToken;
import workbench.sql.parser.ParserType;

import workbench.util.CollectionUtil;
import workbench.util.StringUtil;

/**
 * @author Thomas Kellerer
 */
public class OraclePackageParser
{
  private String packageDeclaration;
  private String packageBody;
  private String packageName;

  public OraclePackageParser(String sql)
  {
    try
    {
      parse(sql);
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not parse SQL", e);
    }
  }

  public String getPackageDeclaration()
  {
    return this.packageDeclaration;
  }

  public String getPackageBody()
  {
    return this.packageBody;
  }

  public String getPackageName()
  {
    return this.packageName;
  }

  /**
   * Get the source of the specified procedure from the package source.
   *
   * @param source      the complete package source
   * @param def         the procedure to extract
   * @param parameters  the parameters of the procedure
   *                    if null parameters are not checked at all
   *                    to search for an overloaded procedure without parameters, pass an empty List
   *
   * @return the code of the procedure
   */
  public static CharSequence getProcedureSource(CharSequence source, ProcedureDefinition def, List<String> parameters)
  {
    CodeArea bounds = findProcedureArea(source, def, parameters);
    if (bounds.startPos > -1 && bounds.endPos > -1)
    {
      int lineStart = StringUtil.getLineStart(source, bounds.startPos);
      return source.subSequence(lineStart > 0 ? lineStart : bounds.startPos, bounds.endPos);
    }
    return null;
  }

  private void parse(String sql)
    throws IOException
  {
    SQLLexer lexer = SQLLexerFactory.createLexer(ParserType.Oracle, sql);
    SQLToken t = lexer.getNextToken(false, false);

    int defBegin = -1;
    int defEnd = -1;
    int bodyBegin = -1;
    int bodyEnd = -1;
    int lastCreateStart = -1;

    while (t != null)
    {
      String text = t.getContents();

      if (isCreate(text))
      {
        lastCreateStart = t.getCharBegin();
      }
      else if (text.equals("PACKAGE"))
      {
        defBegin = lastCreateStart;
        t = lexer.getNextToken(false, false);
        if (t == null) continue;

        if (t.isIdentifier())
        {
          this.packageName = t.getContents();
          t = findEnd(lexer, this.packageName);
          if (t != null)
          {
            defEnd = t.getCharEnd();
          }
        }
      }
      else if (text.equals("PACKAGE BODY"))
      {
        bodyBegin = lastCreateStart;

        t = lexer.getNextToken(false, false);
        if (t == null) continue;

        String name = t.getContents();
        t = findEnd(lexer, name);
        if (t != null)
        {
          bodyEnd = t.getCharEnd();
          break;
        }
      }
      t = lexer.getNextToken(false, false);
    }
    if (defBegin > -1 && defEnd > defBegin)
    {
      this.packageDeclaration = sql.substring(defBegin, defEnd);
    }
    if (bodyBegin > -1 && bodyEnd > bodyBegin)
    {
      this.packageBody = sql.substring(bodyBegin, bodyEnd);
    }
  }

  private static boolean isCreate(String text)
  {
    return text.equals("CREATE") || text.equals("CREATE OR REPLACE");
  }

  private SQLToken findEnd(SQLLexer lexer, String name)
    throws IOException
  {
    SQLToken t = lexer.getNextToken(false, false);
    boolean lastWasEnd = false;

    while (t != null)
    {
      String v = t.getContents();
      if (v.equalsIgnoreCase("END"))
      {
        lastWasEnd = true;
      }
      else if (lastWasEnd && name.equalsIgnoreCase(v))
      {
        SQLToken t2 = lexer.getNextToken(false, false);
        if (t2 != null) return t2;
        else return t;
      }
      else
      {
        lastWasEnd = false;
      }
      t = lexer.getNextToken(false, false);
    }
    return null;
  }

  public static int findProcedurePosition(CharSequence source, ProcedureDefinition def, List<String> parameters)
  {
    CodeArea area = findProcedureArea(source, def, parameters);
    return area.startPos;
  }

  private static CodeArea findProcedureArea(CharSequence source, ProcedureDefinition def, List<String> parameters)
  {
    int procPos = -1;

    SQLLexer lexer = SQLLexerFactory.createLexer(ParserType.Oracle, source);
    SQLToken t = lexer.getNextToken(false, false);
    CodeArea result = new CodeArea();

    boolean packageHeaderFound = false;
    String procType = def.isFunction() ? "FUNCTION" : "PROCEDURE";

    // Find the start of the package body
    while (t != null)
    {
      if (t.getContents().equals("PACKAGE") || t.getContents().equals("TYPE"))
      {
        packageHeaderFound = true;
      }
      if (t.getContents().equals("PACKAGE BODY")) break;
      if (t.getContents().equals("TYPE BODY")) break;
      t = lexer.getNextToken(false, false);
    }

    if (t == null && !packageHeaderFound) return result;
    if (packageHeaderFound && t == null)
    {
      // apparently only the defintion but not the body is available
      // so try to find the procedure in the header
      lexer = SQLLexerFactory.createLexer(ParserType.Oracle, source);
      t = lexer.getNextToken(false, false);
    }

    // Now we have reached the package or type body, let's find the the actual procedure or function
    int lastKeywordPos = -1;

    while (t != null)
    {
      String text = t.getContents();
      if (lastKeywordPos > -1 && text.equalsIgnoreCase(def.getProcedureName()))
      {
        procPos = lastKeywordPos;
        if (parameters == null)
        {
          break;
        }
        else
        {
          // need to check the parameters of the current procedure/function
          // to support overloaded functions/procedures in Oracle
          t = lexer.getNextToken(false, false);
          if (t != null && t.getContents().equals("("))
          {
            List<String> params = getParameters(lexer);
            if (compareArguments(params, parameters))
            {
              break;
            }
          }
          else if (CollectionUtil.isEmpty(parameters))
          {
            break;
          }
          else
          {
            lastKeywordPos = -1;
            continue;
          }
        }
      }

      if (text.equals(procType))
      {
        lastKeywordPos = t.getCharBegin();
      }
      else
      {
        lastKeywordPos = -1;
      }
      t = lexer.getNextToken(false, false);
    }
    result.startPos = procPos;

    // now find the end of the procedure/function
    SQLToken lastToken = t;
    SQLToken lastEnd = null;

    while (t != null)
    {
      String text = t.getContents();
      if (text.equals("PROCEDURE") || text.equals("FUNCTION"))
      {
        result.endPos = lastToken == null ? t.getCharBegin() - 1 : lastToken.getCharEnd();
        break;
      }
      if (text.equals("END"))
      {
        lastEnd = t;
      }
      lastToken = t;
      t = lexer.getNextToken(false, false);
    }
    if (result.endPos == -1 && lastEnd != null)
    {
      result.endPos = lastEnd.getCharBegin() - 1;
    }
    return result;
  }

  private static List<String> getParameters(SQLLexer lexer)
  {
    List<String> params = new ArrayList<>();
    SQLToken t = lexer.getNextToken(false, false);
    boolean nextIsName = true;
    int bracketCount = 0;
    while (t != null)
    {
      String text = t.getText();
      if (text.equals("("))
      {
        bracketCount ++;
      }

      if (text.equals(")"))
      {
        if (bracketCount == 0)
        {
          break;
        }
        bracketCount--;
      }

      if (nextIsName)
      {
        params.add(text);
        nextIsName = false;
      }
      else
      {
        nextIsName = text.equals(",") && bracketCount == 0;
      }
      t = lexer.getNextToken(false, false);
    }
    return params;
  }

  private static boolean compareArguments(List<String> list1, List<String> list2)
  {
    if (CollectionUtil.isEmpty(list1) && CollectionUtil.isEmpty(list2)) return true;
    if (CollectionUtil.isEmpty(list1) && CollectionUtil.isNonEmpty(list2)) return false;
    if (CollectionUtil.isNonEmpty(list1) && CollectionUtil.isEmpty(list2)) return false;
    if (list1.size() != list2.size()) return false;
    for (int i=0; i < list1.size(); i++)
    {
      if (!StringUtil.equalStringIgnoreCase(list1.get(i), list2.get(i))) return false;
    }
    return true;
  }

  private static class CodeArea
  {
    int startPos = -1;
    int endPos = -1;
  }

}
