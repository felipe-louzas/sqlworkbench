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
package workbench.sql.parser;



import workbench.WbTestCase;

import workbench.sql.lexer.SQLToken;

import org.junit.Test;

import workbench.sql.DelimiterDefinition;
import workbench.sql.lexer.SQLLexer;
import workbench.sql.lexer.SQLLexerFactory;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class OracleDelimiterTesterTest
  extends WbTestCase
{

  public OracleDelimiterTesterTest()
  {
    super("OracleDelimiterTesterTest");
  }

  @Test
  public void testCheckHint()
  {
    OracleDelimiterTester tester = new OracleDelimiterTester();
    String sql = "UPDATE /*+ NO_NL WITH_PLSQL */ t1 a\n";
    SQLLexer lexer = SQLLexerFactory.createLexer(ParserType.Oracle, sql);
    SQLToken t = lexer.getNextToken(true, false);
    t = lexer.getNextToken(true, false);
    assertTrue(tester.isPLSQLHint(t));

    sql = "--+ WITH_PLSQL USE_NL\nUPDATE t1 SET \n";
    lexer = SQLLexerFactory.createLexer(ParserType.Oracle, sql);
    t = lexer.getNextToken(true, false);
    assertTrue(tester.isPLSQLHint(t));
  }

  @Test
  public void testWithProcedure()
  {
    OracleDelimiterTester tester = new OracleDelimiterTester();
    tester.setAlternateDelimiter(DelimiterDefinition.DEFAULT_ORA_DELIMITER);
    SQLToken create = new SQLToken(SQLToken.RESERVED_WORD, "WITH", 0, 0);
    tester.currentToken(create, true);
    DelimiterDefinition delim = tester.getCurrentDelimiter();
    assertEquals(DelimiterDefinition.STANDARD_DELIMITER, delim);

    SQLToken proc = new SQLToken(SQLToken.RESERVED_WORD, "FUNCTION", 0, 0);
    tester.currentToken(proc, false);
    delim = tester.getCurrentDelimiter();
    assertEquals(tester.getAlternateDelimiter(), delim);
  }

  @Test
  public void testGetCurrentDelimiter()
  {
    OracleDelimiterTester tester = new OracleDelimiterTester();
    tester.setAlternateDelimiter(DelimiterDefinition.DEFAULT_ORA_DELIMITER);

    SQLToken create = new SQLToken(SQLToken.RESERVED_WORD, "CREATE", 0, 0);
    tester.currentToken(create, true);
    DelimiterDefinition delim = tester.getCurrentDelimiter();
    assertEquals(DelimiterDefinition.STANDARD_DELIMITER, delim);

    SQLToken proc = new SQLToken(SQLToken.RESERVED_WORD, "PROCEDURE", 0, 0);
    tester.currentToken(proc, false);
    delim = tester.getCurrentDelimiter();
    assertEquals(tester.getAlternateDelimiter(), delim);

    SQLToken name = new SQLToken(SQLToken.IDENTIFIER, "foobar", 0, 0);
    tester.currentToken(name, false);
    delim = tester.getCurrentDelimiter();
    assertEquals(tester.getAlternateDelimiter(), delim);

    SQLToken t = new SQLToken(SQLToken.IDENTIFIER, "as", 0, 0);
    tester.currentToken(t, false);
    delim = tester.getCurrentDelimiter();
    assertEquals(tester.getAlternateDelimiter(), delim);

    tester.statementFinished();
    delim = tester.getCurrentDelimiter();
    assertEquals(DelimiterDefinition.STANDARD_DELIMITER, delim);
  }

  @Test
  public void testBlock()
  {
    OracleDelimiterTester tester = new OracleDelimiterTester();
    tester.setAlternateDelimiter(DelimiterDefinition.DEFAULT_ORA_DELIMITER);

    SQLToken begin = new SQLToken(SQLToken.RESERVED_WORD, "BEGIN", 0, 0);
    tester.currentToken(begin, true);
    DelimiterDefinition delim = tester.getCurrentDelimiter();
    assertEquals(tester.getAlternateDelimiter(), delim);

    tester.statementFinished();

    SQLToken declare = new SQLToken(SQLToken.RESERVED_WORD, "DECLARE", 0, 0);
    tester.currentToken(declare, false);
    delim = tester.getCurrentDelimiter();
    assertEquals(tester.getAlternateDelimiter(), delim);

    tester.statementFinished();

    tester.currentToken(begin, false);
    delim = tester.getCurrentDelimiter();
    assertEquals(tester.getAlternateDelimiter(), delim);

    SQLToken select = new SQLToken(SQLToken.RESERVED_WORD, "SELECT", 0, 0);
    tester.currentToken(select, false);
    delim = tester.getCurrentDelimiter();
    assertEquals(tester.getAlternateDelimiter(), delim);
  }

}
