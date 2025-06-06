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
package workbench.sql.parser;


import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import workbench.WbTestCase;

import workbench.sql.DelimiterDefinition;
import workbench.sql.ScriptCommandDefinition;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class LexerBasedParserTest
  extends WbTestCase
{

  public LexerBasedParserTest()
  {
    super("LexerBasedParserTest");
  }

  @Test
  public void testBackslash()
  {
    String sql =
      "select '\\'\n" +
      ";";
    LexerBasedParser p = new LexerBasedParser(ParserType.Oracle);
    p.setStoreStatementText(true);
    p.setScript(sql);
    ScriptCommandDefinition cmd = p.getNextCommand();
    assertEquals("select '\\'", cmd.toString());
  }

  @Test
  public void testSingleEmptyLine()
  {
    String sql = "\n\nselect now();\n";
    LexerBasedParser p = new LexerBasedParser(ParserType.Standard);
    p.setEmptyLineIsDelimiter(true);
    p.setAlternateDelimiter(null);
    p.setScript(sql);
    ScriptCommandDefinition cmd = p.getNextCommand();
    assertNotNull(cmd);
    assertEquals("select now()", cmd.getSQL());
    cmd = p.getNextCommand();
    assertNull(cmd);
  }

  @Test
  public void testGetNextCommand()
    throws Exception
  {
    String sql = "select * from test;\n" + "select * from person;";
    LexerBasedParser parser = new LexerBasedParser(sql);
    ScriptCommandDefinition cmd = parser.getNextCommand();
    assertEquals("select * from test", cmd.getSQL());
    cmd = parser.getNextCommand();
    assertEquals("select * from person", cmd.getSQL());

    sql = "delete from test;commit;";
    parser.setScript(sql);
    cmd = parser.getNextCommand();
    assertEquals("delete from test", cmd.getSQL());
    cmd = parser.getNextCommand();
    assertEquals("commit", cmd.getSQL());
  }

  @Test
  public void testStoreIndexOnly()
    throws Exception
  {
    String sql = "select * from test;\n" + "select * from person;";
    LexerBasedParser parser = new LexerBasedParser(sql);
    parser.setStoreStatementText(false);
    ScriptCommandDefinition cmd = null;
    while ((cmd = parser.getNextCommand()) != null)
    {
      assertNull(cmd.getSQL());
      int index = cmd.getIndexInScript();
      if (index == 0)
      {
        String cmdSql = sql.substring(cmd.getStartPositionInScript(), cmd.getEndPositionInScript());
        assertEquals("select * from test", cmdSql.trim());
      }
      else if (index == 1)
      {
        String cmdSql = sql.substring(cmd.getStartPositionInScript(), cmd.getEndPositionInScript());
        assertEquals("select * from person", cmdSql.trim());
      }
      else
      {
        fail("Wrong command index: " + index);
      }
    }
  }

  @Test
  public void testTrimLeadingWhiteSpace()
    throws Exception
  {
    String sql = "select * from test;\nselect * from person;\n";
    LexerBasedParser parser = new LexerBasedParser(sql);
    parser.setReturnStartingWhitespace(false);
    ScriptCommandDefinition cmd = null;
    while ((cmd = parser.getNextCommand()) != null)
    {
      int index = cmd.getIndexInScript();
      if (index == 0)
      {
        assertEquals("select * from test", cmd.getSQL());
      }
      else if (index == 1)
      {
        assertEquals("select * from person", cmd.getSQL());
      }
    }

    sql = "COMMENT ON COLUMN COMMENT_TEST.ID IS 'Primary key column';\r\nCOMMENT ON COLUMN COMMENT_TEST.FIRST_NAME IS 'Firstname';\r\n";
    parser = new LexerBasedParser(sql);
    parser.setReturnStartingWhitespace(false);
    parser.setStoreStatementText(false);
    while ((cmd = parser.getNextCommand()) != null)
    {
      int index = cmd.getIndexInScript();
      if (index == 0)
      {
        int start = cmd.getStartPositionInScript();
        int end = cmd.getEndPositionInScript();
        String cmdSql = sql.substring(start, end);
        assertEquals("COMMENT ON COLUMN COMMENT_TEST.ID IS 'Primary key column'", cmdSql);
      }
      else if (index == 1)
      {
        int start = cmd.getStartPositionInScript();
        int end = cmd.getEndPositionInScript();
        String cmdSql = sql.substring(start, end);
        assertEquals("COMMENT ON COLUMN COMMENT_TEST.FIRST_NAME IS 'Firstname'", cmdSql);
      }
    }
  }

  @Test
  public void testReturnLeadingWhiteSpace()
    throws Exception
  {
    String sql = " select * from test;\n" + " select * from person;";
    LexerBasedParser parser = new LexerBasedParser(sql);
    parser.setReturnStartingWhitespace(true);
    ScriptCommandDefinition cmd = null;
    while ((cmd = parser.getNextCommand()) != null)
    {
      int index = cmd.getIndexInScript();
      if (index == 0)
      {
        assertEquals(" select * from test", cmd.getSQL());
      }
      else if (index == 1)
      {
        assertEquals("\n select * from person", cmd.getSQL());
      }
      else
      {
        fail("Wrong command index: " + index);
      }
    }
  }

  @Test
  public void testPatterns()
  {
    LexerBasedParser parser = new LexerBasedParser();
    assertTrue(parser.isMultiLine("\n\n"));
    assertTrue(parser.isMultiLine("\r\n\r\n"));
    assertFalse(parser.isMultiLine(" \r\n "));
    assertFalse(parser.isMultiLine("\r\n"));
    assertTrue(parser.isMultiLine(" \r\n\t\r\n "));
    assertTrue(parser.isMultiLine(" \r\n\t  \r\n\t"));

    assertTrue(parser.isLineBreak("\n"));
    assertTrue(parser.isLineBreak(" \n "));
    assertTrue(parser.isLineBreak("\r\n"));
    assertTrue(parser.isLineBreak(" \r\n "));
    assertTrue(parser.isLineBreak("\r\n  "));
    assertTrue(parser.isLineBreak("           \t\r\n\t"));
  }

  @Test
  public void testCursorInEmptyLine()
    throws Exception
  {
    String sql = "\nselect 42\nfrom dual;\nselect * \nfrom table\n;";
    LexerBasedParser p = new LexerBasedParser();
    p.setEmptyLineIsDelimiter(false);
    p.setScript(sql);
    ScriptCommandDefinition cmd = p.getNextCommand();
    assertNotNull(cmd);
//    System.out.println("*** start: " + cmd.getWhitespaceStart());
  }

  @Test
  public void testPgParser()
    throws Exception
  {
    String sql =
      "select * from foo;\n" +
      "commit;\n" +
      "delete from bar;";

    LexerBasedParser parser = new LexerBasedParser(sql);
    parser.setCheckPgQuoting(true);
    List<String> script = getStatements(parser);
    assertEquals(3, script.size());
    assertEquals("commit", script.get(1));

    sql =
      "create or replace function foo()\n" +
      "  returns integer \n" +
      "as \n" +
      "$body$\n" +
      "  declare l_value integer;\n" +
      "begin \n" +
      "   l_value := 42; \n" +
      "   return l_value; \n" +
      "end;\n" +
      "$body$\n"+
      "language plpgsql;";

    parser.setScript(sql);
    script = getStatements(parser);
    assertEquals(1, script.size());

    sql =
      "drop function foo()\n" +
      "/\n" +
      "create or replace function foo()\n" +
      "  returns integer \n" +
      "as \n" +
      "$body$\n" +
      "  declare l_value integer;\n" +
      "begin \n" +
      "   l_value := 42; \n" +
      "   return l_value; \n" +
      "end;\n" +
      "$body$\n"+
      "language plpgsql" +
      "/\n";

    parser.setScript(sql);
    parser.setDelimiter(DelimiterDefinition.DEFAULT_ORA_DELIMITER);
    script = getStatements(parser);
    assertEquals(2, script.size());

    sql =
      "delete from foo where descr = 'arthur''s house';\n" +
      "commit;";

    parser.setScript(sql);
    parser.setDelimiter(DelimiterDefinition.STANDARD_DELIMITER);
    script = getStatements(parser);
    assertEquals(2, script.size());
    assertEquals("delete from foo where descr = 'arthur''s house'", script.get(0));
    assertEquals("commit", script.get(1));

    parser.setScript(sql);
    parser.setCheckPgQuoting(false);
    script = getStatements(parser);
    assertEquals(2, script.size());
    assertEquals("delete from foo where descr = 'arthur''s house'", script.get(0));
    assertEquals("commit", script.get(1));

    parser.setScript(
      "wbImport -fileColumns=one,two,$wb_skip$,three -table=x -file=x.txt;\n" +
      "select count(*) from x;\n");
    script = getStatements(parser);
    assertEquals(2, script.size());
    assertEquals("wbImport -fileColumns=one,two,$wb_skip$,three -table=x -file=x.txt", script.get(0));
    assertEquals("select count(*) from x", script.get(1));


    sql =
      "drop function foo();\n" +
      "\n" +
      "create or replace function foo()\n" +
      "  returns integer \n" +
      "as \n" +
      "$body$\n" +
      "  declare l_value varchar;\n" +
      "begin \n" +
      "   select \"$body$\" into l_value where some_column <> '$body$'; \n" +
      "   return l_value; \n" +
      "end;\n" +
      "$body$\n"+
      "language plpgsql;";

    parser.setScript(sql);
    parser.setCheckPgQuoting(true);
    script = getStatements(parser);
    assertEquals(2, script.size());
    assertEquals("drop function foo()", script.get(0));
    assertTrue(script.get(1).startsWith("create or replace"));
    assertTrue(script.get(1).endsWith("language plpgsql"));


    sql =
      "drop function foo()\n" +
      "/?\n" +
      "create or replace function foo()\n" +
      "  returns integer \n" +
      "as \n" +
      "$body$\n" +
      "  declare l_value varchar;\n" +
      "begin \n" +
      "   select \"$body$\" into l_value where some_column <> '$body$'; \n" +
      "   return l_value; \n" +
      "end;\n" +
      "$body$\n"+
      "language plpgsql\n" +
      "/?\n";

    parser = new LexerBasedParser(sql);
    parser.setDelimiter(new DelimiterDefinition("/?"));
    parser.setCheckPgQuoting(true);
    parser.setStoreStatementText(true);

    ScriptCommandDefinition cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("drop function foo()", cmd.getSQL().trim());

//    System.out.println(script.get(1));
    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertTrue(cmd.getSQL().startsWith("create or replace"));
    assertTrue(cmd.getSQL().endsWith("language plpgsql"));
  }

  private List<String> getStatements(LexerBasedParser parser)
  {
    List<String> result = new ArrayList<>();
    ScriptCommandDefinition cmd = parser.getNextCommand();
    while (cmd != null)
    {
      result.add(cmd.getSQL());
      cmd = parser.getNextCommand();
    }
    return result;
  }

  @Test
  public void testMsGO()
    throws Exception
  {
    String sql = "select * from test\n GO \n" + "select * from person\nGO";
    LexerBasedParser parser = new LexerBasedParser(ParserType.SqlServer);
    parser.setScript(sql);
    parser.setDelimiter(DelimiterDefinition.DEFAULT_MS_DELIMITER);
    parser.setStoreStatementText(true);

    ScriptCommandDefinition cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("select * from test", cmd.getSQL().trim());

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("select * from person", cmd.getSQL().trim());

    sql =
      "declare @x;\n" +
      "set @x = 42;" +
      "select * " +
      "from foo \n" +
      "where id = @x;\n" +
      "GO\n";
    parser.setScript(sql);

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertTrue(cmd.getSQL().startsWith("declare @x;"));
    assertTrue(cmd.getSQL().endsWith("where id = @x;"));

    cmd = parser.getNextCommand();
    assertNull(cmd);

    parser = new LexerBasedParser(ParserType.Standard);
    parser.setScript(sql);
    parser.setDelimiter(DelimiterDefinition.DEFAULT_MS_DELIMITER);
    parser.setStoreStatementText(true);

    sql =
      "declare @x;\n" +
      "set @x = 42;" +
      "select * " +
      "from foo \n" +
      "where id = @x;\n" +
      "GO\n";

    parser.setScript(sql);
    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertTrue(cmd.getSQL().startsWith("declare @x;"));
    assertTrue(cmd.getSQL().endsWith("where id = @x;"));

    cmd = parser.getNextCommand();
    assertNull(cmd);

    sql =
      "SELECT id \n" +
      "FROM person GO\n" +
      "  GO  \n" +
      " \n" +
      " \n" +
      "select * \n" +
      "from country \n" +
      "  GO";
    parser.setScript(sql);
    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("SELECT id \nFROM person GO", cmd.getSQL());

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("select * \nfrom country", cmd.getSQL().trim());
  }

  @Test
  public void testDanglingQuotes()
    throws Exception
  {
    String sql = "delete from foo;\n" +
      "\n" +
      "insert into foo (col1, col2s) values ('one, two);";
    LexerBasedParser parser = new LexerBasedParser(ParserType.Standard);
    parser.setStoreStatementText(true);
    parser.setScript(sql);

    ScriptCommandDefinition cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("delete from foo", cmd.getSQL());

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("insert into foo (col1, col2s) values ('one, two)", cmd.getSQL());
  }

  @Test
  public void testAlternateDelimiter()
    throws Exception
  {
    String sql =
      "create table one (id integer)\n" +
      "/?\n" +
      "create table two (id integer)\n" +
      "/?\n";

    LexerBasedParser parser = new LexerBasedParser(sql);
    parser.setDelimiter(new DelimiterDefinition("/?"));
    parser.setStoreStatementText(false);

    ScriptCommandDefinition cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertNotNull(cmd.getDelimiterUsed());
    String stmt = sql.substring(cmd.getStartPositionInScript(), cmd.getEndPositionInScript());
    assertEquals("create table one (id integer)", stmt.trim());

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertNotNull(cmd.getDelimiterUsed());
    stmt = sql.substring(cmd.getStartPositionInScript(), cmd.getEndPositionInScript());
    assertEquals("create table two (id integer)", stmt.trim());
  }

  @Test
  public void quotedQuotes()
    throws Exception
  {
    for (ParserType type : ParserType.values())
    {
      doTestQuotedQuotes(new LexerBasedParser(type));
    }
  }

  public void doTestQuotedQuotes(LexerBasedParser parser)
    throws Exception
  {
    String sql = "select \";\" from foobar;\ncommit;";
    parser.setScript(sql);
    parser.setStoreStatementText(true);
    ScriptCommandDefinition cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("select \";\" from foobar", cmd.getSQL());

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("commit", cmd.getSQL());

    sql = "select ';', \"'\" from foobar;\ncommit;";
    parser.setScript(sql);
    parser.setStoreStatementText(true);
    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("select ';', \"'\" from foobar", cmd.getSQL());

    sql =
      "wbexport  -type=text -delimiter=';' -quoteChar=\"'\" -file=test.txt; \n" +
      "wbimport  -type=text;";

    parser.setScript(sql);
    parser.setStoreStatementText(true);
    cmd = parser.getNextCommand();
    assertNotNull(cmd);
//    System.out.println(cmd.getSQL());
    assertEquals("wbexport  -type=text -delimiter=';' -quoteChar=\"'\" -file=test.txt", cmd.getSQL());

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("wbimport  -type=text", cmd.getSQL());
  }

  @Test
  public void testOracleInclude()
    throws Exception
  {
    String sql = "select \n@i = 1,id from test;\n" + "select * from person;delete from test2;commit;";
    LexerBasedParser parser = new LexerBasedParser(ParserType.Standard);
    parser.setScript(sql);
    ScriptCommandDefinition cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("select \n@i = 1,id from test", cmd.getSQL().trim());

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("select * from person", cmd.getSQL().trim());

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("delete from test2", cmd.getSQL().trim());

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("commit", cmd.getSQL().trim());

    parser = new LexerBasedParser(ParserType.Oracle);
    sql = "delete from person;\n  @insert_person.sql\ncommit;";
    parser.setScript(sql);
    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("delete from person", cmd.getSQL());

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("@insert_person.sql", cmd.getSQL());

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("commit", cmd.getSQL());

    cmd = parser.getNextCommand();
    assertNull(cmd);

    // For SQL Server, the short include using @ should not be used
    parser = new LexerBasedParser(ParserType.SqlServer);
    sql = "delete from person;\n  @insert_person.sql\ncommit;";
    parser.setScript(sql);
    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("delete from person", cmd.getSQL());

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("@insert_person.sql\ncommit", cmd.getSQL());

    cmd = parser.getNextCommand();
    assertNull(cmd);

    parser = new LexerBasedParser(ParserType.Standard);
    sql = "delete from person;\n  @insert_person.sql\ncommit;";
    parser.setScript(sql);
    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("delete from person", cmd.getSQL());

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("@insert_person.sql", cmd.getSQL());

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("commit", cmd.getSQL());

    cmd = parser.getNextCommand();
    assertNull(cmd);
  }

  @Test
  public void testOracleSingleLine()
  {
    String sql =
      "whenever sql error continue\n" +
      "\n" +
      "drop table foo;\n";
    LexerBasedParser parser = new LexerBasedParser(ParserType.Oracle);
    parser.setStoreStatementText(true);
    parser.setScript(sql);
    ScriptCommandDefinition cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("whenever sql error continue", cmd.getSQL());

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("drop table foo", cmd.getSQL());

    sql =
      "select 1 from dual; \n" +
      "whenever sql error continue\n" +
      "\n" +
      "drop table foo;\n";
    parser.setScript(sql);

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("select 1 from dual", cmd.getSQL());

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("whenever sql error continue", cmd.getSQL());

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("drop table foo", cmd.getSQL());

    sql =
      "select 1 from dual; \n" +
      "describe foo\n" +
      "select 2 from foo;\n";
    parser.setScript(sql);

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("select 1 from dual", cmd.getSQL());

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("describe foo", cmd.getSQL());

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("select 2 from foo", cmd.getSQL());

    sql = "select id from person order by id \ndesc";
    parser.setScript(sql);
    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("select id from person order by id \ndesc", cmd.getSQL());

    cmd = parser.getNextCommand();
    assertNull(cmd);

    parser.setScript(
      "set serveroutput on\n" +
      "BEGIN\n" +
      "  some_proc;\n" +
      "end;\n" +
      "/");

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("set serveroutput on", cmd.getSQL());

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertTrue(cmd.getSQL().trim().startsWith("BEGIN"));
    assertTrue(cmd.getSQL().trim().endsWith("end;"));
  }

  @Test
  public void testOracleDDL()
  {
    String script =
      "/* this function \n" +
      "   does something useful \n" +
      "*/\n" +
      "create or replace function foo \n" +
      "  (\n" +
      "     p_one integer, -- some value \n" +
      "     p_two integer -- other value \n" +
      "  )\n" +
      "  RETURN integer\n" +
      "IS\n" +
      "BEGIN\n" +
      "  IF p_one IS NULL then \n" +
      "    return 41;" +
      "  end if;\n" +
      "  return 42;\n" +
      "END foo;\n" +
      "/";
    LexerBasedParser parser = new LexerBasedParser(ParserType.Oracle);
    parser.setStoreStatementText(true);
    parser.setScript(script);
    parser.setAlternateDelimiter(DelimiterDefinition.DEFAULT_ORA_DELIMITER);
    parser.setEmptyLineIsDelimiter(false);
    ScriptCommandDefinition cmd = parser.getNextCommand();
    assertTrue(cmd.getSQL().trim().endsWith("END foo;"));
    cmd = parser.getNextCommand();
    assertNull(cmd);
  }

  @Test
  public void testDynamicDelimiter()
  {
    String script =
      "drop procedure foo;\n" +
      "\n" +
      "create procedure foo\n" +
      "is\n" +
      "   l_value integer;\n" +
      "begin\n" +
      "\n" +
      "   l_value := 42;\n" +
      "\n" +
      "end;\n" +
      "/\n";

    LexerBasedParser parser = new LexerBasedParser(ParserType.Oracle);
    parser.setScript(script);
    parser.setAlternateDelimiter(DelimiterDefinition.DEFAULT_ORA_DELIMITER);
    parser.setEmptyLineIsDelimiter(true);
    ScriptCommandDefinition cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("drop procedure foo", cmd.getSQL());

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertTrue(cmd.getSQL().startsWith("create procedure"));
    assertTrue(cmd.getSQL().endsWith("end;"));

    cmd = parser.getNextCommand();
    assertNull("unexpected statement: " + cmd, cmd);

    parser.setScript("WbVardef outfile=/temp/foo.txt;");
    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("WbVardef outfile=/temp/foo.txt", cmd.getSQL().trim());


    script = "select a/b from some_table;" +
      "\n" +
      "select c/d from other_table\n" +
      "/\n";
    parser.setScript(script);
    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("select a/b from some_table", cmd.getSQL().trim());

    script = "select * from foo\n" +
      "/\n" +
      "select * from bar\n" +
      "/\n";
    parser.setScript(script);
    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("select * from foo", cmd.getSQL().trim());

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("select * from bar", cmd.getSQL().trim());

    script =
        "select 1 \n" +
        "/ 2 from some_table;";
    parser.setScript(script);
    cmd = parser.getNextCommand();

    assertEquals(
      "select 1 \n" +
      "/ 2 from some_table", cmd.getSQL().trim());

    cmd = parser.getNextCommand();
    assertNull(cmd);
  }

  @Test
  public void testMixedEmptyLinesWithTerminator()
    throws Exception
  {
    for (ParserType type : ParserType.values())
    {
      doTestMixedEmptyLinesWithTerminator(new LexerBasedParser(type));
    }
  }

  private void doTestMixedEmptyLinesWithTerminator(ScriptIterator parser)
    throws Exception
  {
    String sql = "select * from foo;\n\n" + "select * from bar;\n";
    parser.setEmptyLineIsDelimiter(true);
    parser.setScript(sql);
    parser.setStoreStatementText(true);

    ScriptCommandDefinition cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("select * from foo", cmd.getSQL());

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("select * from bar", cmd.getSQL());

    sql =
      "select * from foo;\n" +
      "select * from bar;\n" +
      "select * from foobar;\n" +
      "\n" +
      "select * from foo;";
    parser.setScript(sql);
    parser.setStoreStatementText(true);

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("select * from foo", cmd.getSQL());

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("select * from bar", cmd.getSQL());

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("select * from foobar", cmd.getSQL());

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("select * from foo", cmd.getSQL());

    cmd = parser.getNextCommand();
    assertNull(cmd);
  }

  @Test
  public void testEmptyLineDelimiter()
    throws Exception
  {
    for (ParserType type : ParserType.values())
    {
      doTestEmptyLineDelimiter(new LexerBasedParser(type));
    }
  }

  private void doTestEmptyLineDelimiter(final ScriptIterator parser)
    throws Exception
  {
    String sql = "select * from test\n\n" + "select * from person\n";
    parser.setScript(sql);
    parser.setEmptyLineIsDelimiter(true);
    parser.setStoreStatementText(true);

    ScriptCommandDefinition cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("select * from test", cmd.getSQL().trim());

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("select * from person", cmd.getSQL().trim());

    sql = "select a,b,c\r\nfrom test\r\nwhere x = 1";
    parser.setScript(sql);
    parser.setEmptyLineIsDelimiter(true);
    parser.setStoreStatementText(true);

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("select a,b,c\r\nfrom test\r\nwhere x = 1", cmd.getSQL());

    sql = "select *\nfrom foo\n\nselect * from bar";
    parser.setScript(sql);
    parser.setStoreStatementText(true);

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("select *\nfrom foo", cmd.getSQL());

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("select * from bar", cmd.getSQL());

    cmd = parser.getNextCommand();
    assertNull(cmd);
  }


  @Test
  public void testQuotedDelimiter()
    throws Exception
  {
    for (ParserType type : ParserType.values())
    {
      doTestQuotedDelimiter(new LexerBasedParser(type));
    }
  }

  private void doTestQuotedDelimiter(ScriptIterator parser)
    throws Exception
  {
    String sql = "select 'test\n;lines' from test;";
    parser.setScript(sql);
    parser.setStoreStatementText(true);

    ScriptCommandDefinition cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertEquals("select 'test\n;lines' from test", cmd.getSQL());

    cmd = parser.getNextCommand();
    assertNull(cmd);
  }

  @Test
  public void testWhiteSpaceAtEnd()
    throws Exception
  {
    for (ParserType type : ParserType.values())
    {
      doTestWhiteSpaceAtEnd(new LexerBasedParser(type));
    }
  }

  public void doTestWhiteSpaceAtEnd(ScriptIterator parser)
    throws IOException
  {
    String sql = "create table target_table (id integer);\n" +
      "wbcopy \n";

    parser.setScript(sql);
    parser.setCheckEscapedQuotes(false);
    parser.setEmptyLineIsDelimiter(false);
    parser.setStoreStatementText(false);

    ScriptCommandDefinition cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertNull(cmd.getSQL());

    cmd = parser.getNextCommand();
    assertNotNull(cmd);
    assertNull(cmd.getSQL());
    assertEquals(sql.length(), cmd.getEndPositionInScript());
  }

  @Test
  public void testEscapedQuotes()
  {
    for (ParserType type : ParserType.values())
    {
      doTestEscapedQuotes(new LexerBasedParser(type), type.toString());
    }
  }

  public void doTestEscapedQuotes(ScriptIterator parser, String parserType)
  {
    parser.setCheckEscapedQuotes(true);
    parser.setScript(
      "insert into foo (data) values ('foo\\'s data1');\n" +
      "insert into foo (data) values ('foo\\'s data2');" +
      "commit;\n");
    parser.setStoreStatementText(true);

    ScriptCommandDefinition c = parser.getNextCommand();
    assertNotNull("First statement is null for type: " + parserType, c);
    assertTrue("First statement not an insert for type: " + parserType, c.getSQL().startsWith("insert"));

    c = parser.getNextCommand();
    assertNotNull("Second statement is null for type: " + parserType, c);
    assertNotNull(c);
    assertTrue("Second statement not an insert for type: " + parserType, c.getSQL().startsWith("insert"));

    c = parser.getNextCommand();
    assertNotNull("Third statement is null for type: " + parserType, c);
    assertEquals("Third statement not a commit for type: " + parserType, "commit", c.getSQL());
  }

  @Test
  public void testCreateProcGrant()
    throws Exception
  {
    for (ParserType type : ParserType.values())
    {
      doTestCreateProcGrant(new LexerBasedParser(type));
    }
  }

  public void doTestCreateProcGrant(LexerBasedParser parser)
    throws Exception
  {
    String sql =
      "grant create table to arthur;\n" +
      "grant create procedure to arthur;\n" +
      "grant create view to arthur;";

    parser.setStoreStatementText(true);
    parser.setScript(sql);

    ScriptCommandDefinition cmd = parser.getNextCommand();
    assertEquals("Wrong grant for table (" + parser.getParserType() + ")", "grant create table to arthur", cmd.getSQL().trim());

    cmd = parser.getNextCommand();
    assertEquals("Wrong grant for procedure (" + parser.getParserType() + ")", "grant create procedure to arthur", cmd.getSQL().trim());

    cmd = parser.getNextCommand();
    assertEquals("Wrong grant for view (" + parser.getParserType() + ")", "grant create view to arthur", cmd.getSQL().trim());

    cmd = parser.getNextCommand();
    assertNull(cmd);
  }
}
