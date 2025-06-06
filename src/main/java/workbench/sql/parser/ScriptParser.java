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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import workbench.resource.Settings;

import workbench.db.DBID;
import workbench.db.DbSettings;
import workbench.db.WbConnection;

import workbench.sql.DelimiterDefinition;
import workbench.sql.ScriptCommandDefinition;

import workbench.util.FileUtil;
import workbench.util.StringUtil;
import workbench.util.WbFile;

/**
 * A class to parse a SQL script and return the individual commands in the script.
 *
 * The actual parsing is done by using an instance of {@link LexerBasedParser}
 *
 * @see LexerBasedParser
 * @see ScriptIterator
 *
 * @author  Thomas Kellerer
 */
public class ScriptParser
{
  private String originalScript;
  private List<ScriptCommandDefinition> commands;
  private DelimiterDefinition delimiter = DelimiterDefinition.STANDARD_DELIMITER;
  private DelimiterDefinition alternateDelimiter;
  private int currentIteratorIndex = -42;
  private boolean checkEscapedQuotes;
  private ScriptIterator scriptIterator;
  private boolean emptyLineIsSeparator;
  private boolean returnLeadingWhitespace;
  private boolean useAlternateDelimiter;
  private ParserType parserType = ParserType.Standard;
  private File source;
  private boolean useDynamicDelimiterTester;

  public ScriptParser()
  {
    this(ParserType.Standard);
  }

  public ScriptParser(ParserType type)
  {
    parserType = type;
  }

  public ScriptParser(DBID dbms)
  {
    parserType = ParserType.getTypeFromDBID(dbms);
    DbSettings dbs = new DbSettings(dbms.getId());
    useDynamicDelimiterTester = dbs.enableDynamicDelimiter();
  }

  public ScriptParser(WbConnection conn)
  {
    parserType = ParserType.getTypeFromConnection(conn);
    if (conn != null && conn.getDbSettings() != null)
    {
      useDynamicDelimiterTester = conn.getDbSettings().enableDynamicDelimiter();
    }
  }

  public ScriptParser(String aScript, DBID dbms)
  {
    this(dbms);
    this.setScript(aScript);
  }

  public ScriptParser(String aScript, ParserType type)
  {
    this(type);
    this.setScript(aScript);
  }

  public ScriptParser(String aScript)
  {
    this.setScript(aScript);
  }

  public boolean isDynamicDelimiterEnabled()
  {
    return this.useDynamicDelimiterTester;
  }

  public void setDynamicDelimiterEnabled(boolean flag)
  {
    if (flag)
    {
      this.useAlternateDelimiter = false;
    }
    this.commands = null;
    this.useDynamicDelimiterTester = flag;
    if (scriptIterator != null)
    {
      scriptIterator = getParserInstance();
    }
  }

  public void setParserType(ParserType type)
  {
    parserType = type;
    this.commands = null;
    if (scriptIterator != null)
    {
      scriptIterator = getParserInstance();
    }
  }

  private boolean doLoadFile(File f)
  {
    return f.length() <= Settings.getInstance().getInMemoryScriptSizeThreshold();
  }

  public void setFile(File f)
    throws IOException
  {
    setFile(f, null, doLoadFile(f));
  }

  /**
   * Define the source file for this ScriptParser.
   * Depending on the size the file might be read into memory or not
   */
  public final void setFile(File f, String encoding)
    throws IOException
  {
    this.setFile(f, encoding, doLoadFile(f));
  }

  public final void setFile(File f, String encoding, boolean loadScriptToMemory)
    throws IOException
  {
    if (!f.exists()) throw new FileNotFoundException(f.getName() + " not found");

    // Load small scripts into memory to be able to properly detect the alternate delimiter if necesssary
    // For Oracle this is not necessary because we support mixing standard and alternate delimiter in the script.
    if (parserType != ParserType.Oracle && loadScriptToMemory)
    {
      String script = FileUtil.readFile(f, encoding);
      if (script != null)
      {
        setScript(script);
        source = f;
        return;
      }
    }
    useAlternateDelimiter = this.alternateDelimiter != null;
    scriptIterator = getParserInstance();
    scriptIterator.setFile(f, encoding);
    source = f;
  }

  /**
   * Returns the file that was parsed if available.
   * May be null (if the parser has been initialized from a String)
   */
  public WbFile getScriptFile()
  {
    if (source == null) return null;
    return new WbFile(this.source);
  }

  public int getScriptLength()
  {
    if (this.scriptIterator != null)
    {
      return this.scriptIterator.getScriptLength();
    }
    if (this.originalScript != null)
    {
      return this.originalScript.length();
    }
    return 0;
  }

  /**
   * Reads the script from the given file using the provided encoding.
   *
   * @param f the file to be read
   * @param encoding the encoding to be use. May be null
   * @throws IOException
   */
  public void readScriptFromFile(File f, String encoding)
    throws IOException
  {
    String content = FileUtil.readFile(f, encoding);
    this.setScript(content == null ? "" : content);
  }

  public void setEmptyLineIsDelimiter(boolean flag)
  {
    this.emptyLineIsSeparator = flag;
  }

  public void setReturnStartingWhitespace(boolean flag)
  {
    this.returnLeadingWhitespace = flag;
  }

  /**
   *  Define the script to be parsed.
   */
  public final void setScript(String script)
  {
    if (script == null) throw new NullPointerException("SQL script may not be null");
    if (script.equals(this.originalScript)) return;
    this.originalScript = script;
    this.commands = null;
    this.scriptIterator = null;
    this.source = null;
    this.currentIteratorIndex = 0;
  }

  public void setDelimiter(DelimiterDefinition delim)
  {
    this.setDelimiters(delim, null);
  }

  /**
   * Sets the alternate delimiter. This implies that
   * by default the semicolon is used, and only if
   * the alternate delimiter is detected, that will be used.
   *
   * If only one delimiter should be used (and no automatic checking
   * for an alternate delimiter), use {@link #setDelimiter(DelimiterDefinition)}
   */
  public void setAlternateDelimiter(DelimiterDefinition alt)
  {
    setDelimiters(DelimiterDefinition.STANDARD_DELIMITER, alt);
  }

  /**
   * Define the delimiters to be used. If the (in-memory) script ends with
   * the defined alternate delimiter, then the alternate is used, otherwise
   * the default
   */
  public void setDelimiters(DelimiterDefinition defaultDelim, DelimiterDefinition alternateDelim)
  {
    this.delimiter = defaultDelim;
    if (alternateDelimiterChanged(alternateDelim))
    {
      this.commands = null;
      this.scriptIterator = null;
    }
    this.alternateDelimiter = alternateDelim;
  }

  private boolean alternateDelimiterChanged(DelimiterDefinition newDelim)
  {
    if (this.alternateDelimiter == null && newDelim == null) return false;
    if (this.alternateDelimiter == null && newDelim != null) return true;
    if (this.alternateDelimiter != null && newDelim == null) return true;
    return !alternateDelimiter.equals(newDelim);
  }

  /**
   * Checks if the current script ends with the defined alternate delimiter.
   *
   * <p>
   * If no {@link #alternateDelimiter} was defined, this function does nothing.
   * </p>
   *
   * <p>
   * If the script ends with the {@link #alternateDelimiter} the flag {@link #useAlternateDelimiter}
   * is set to true
   * </p>
   */
  private void checkAlternateDelimiter()
  {
    this.useAlternateDelimiter = false;

    if (this.alternateDelimiter == null) return;
    if (this.originalScript == null) return;

    this.useAlternateDelimiter = alternateDelimiter.terminatesScript(originalScript, this.parserType == ParserType.MySQL, '\"');
    this.commands = null;
  }

  /**
   * Return the index from the overall script mapped to the
   * index inside the specified command. For a single command
   * script scriptCursorLocation will be the same as
   * the location inside the dedicated command.
   * @param commandIndex the index for the command to check
   * @param cursorPos the index in the overall script
   * @return the relative index inside the command
   */
  public int getIndexInCommand(int commandIndex, int cursorPos)
  {
    if (commandIndex < 0) return -1;

    if (this.commands == null) this.parseCommands();

    if (commandIndex >= this.commands.size()) return -1;
    ScriptCommandDefinition b = this.commands.get(commandIndex);
    int start = b.getStartPositionInScript();
    int end = b.getEndPositionInScript();
    int relativePos = (cursorPos - start);
    int commandLength = (end - start);
    if (relativePos > commandLength)
    {
      // This can happen when trimming the statements.
      relativePos = commandLength;
    }
    return relativePos;
  }

  /**
   *  Return the command index for the command which is located at
   *  the given index of the current script.
   */
  public int getCommandIndexAtCursorPos(int cursorPos)
  {
    if (cursorPos < 0) return -1;

    if (this.commands == null) this.parseCommands();
    int count = this.commands.size();
    if (count == 1) return 0;
    if (count == 0) return -1;
    for (int i=0; i < count - 1; i++)
    {
      ScriptCommandDefinition b = this.commands.get(i);
      ScriptCommandDefinition next = this.commands.get(i + 1);
      if (cursorPos >= b.getWhitespaceStart() && cursorPos < next.getStartPositionInScript()) return i;
      if (cursorPos > b.getEndPositionInScript() && cursorPos < next.getEndPositionInScript()) return i + 1;
      if (b.getEndPositionInScript() > cursorPos && next.getWhitespaceStart() <= cursorPos) return i+1;
    }
    ScriptCommandDefinition b = this.commands.get(count - 1);
    if (b.getWhitespaceStart() <= cursorPos && b.getEndPositionInScript() >= cursorPos) return count - 1;
    return -1;
  }

  /**
   *  Get the starting offset in the original script for the command indicated by index
   */
  public int getStartPosForCommand(int index)
  {
    if (index < 0) return -1;

    if (this.commands == null) this.parseCommands();
    if (index >= this.commands.size()) return -1;
    ScriptCommandDefinition b = this.commands.get(index);
    int start = b.getStartPositionInScript();
    return start;
  }

  /**
   * Get the starting offset in the original script for the command indicated by index
   */
  public int getEndPosForCommand(int index)
  {
    if (index < 0) return -1;
    if (this.commands == null) this.parseCommands();
    if (index >= this.commands.size()) return -1;
    ScriptCommandDefinition b = this.commands.get(index);
    return b.getEndPositionInScript();
  }

  /**
   * Find the position in the original script for the next start of line
   */
  public int findNextLineStart(int pos)
  {
    if (this.originalScript == null) return -1;
    if (pos < 0) return pos;
    int len = this.originalScript.length();
    if (pos >= len) return pos;
    char c = this.originalScript.charAt(pos);
    while (pos < len && (c == '\n' || c == '\r'))
    {
      pos ++;
      c = this.originalScript.charAt(pos);
    }
    return pos;
  }

  /**
   * Return the command at the given index position.
   */
  public String getCommand(int index)
  {
    return getCommand(index, true);
  }

  public DelimiterDefinition getDelimiterUsed(int commandIndex)
  {
    ScriptCommandDefinition c = getCommandDefinition(commandIndex);
    if (c != null) return c.getDelimiterUsed();
    return getDelimiter();
  }

  /**
   * Return the command at the given index position.
   * <br/>
   * This will force a complete parsing of the script
   * and the script will be loaded into memory!
   */
  public String getCommand(int index, boolean rightTrimCommand)
  {
    ScriptCommandDefinition c = getCommandDefinition(index);
    if (c == null) return null;
    if (c.getSQL() != null) return c.getSQL();

    String s = originalScript.substring(c.getStartPositionInScript(), c.getEndPositionInScript());

    if (rightTrimCommand)
    {
      return StringUtil.rtrim(s);
    }
    else
    {
      return s;
    }
  }

  public ScriptCommandDefinition getCommandDefinition(int index)
  {
    if (index < 0) return null;
    if (this.commands == null) this.parseCommands();
    if (index >= this.commands.size()) return null;
    return this.commands.get(index);
  }

  /**
   * Returns the number of statements in this script.
   * <br/>
   * This will force a complete parsing of the script and the
   * script will be loaded into memory!
   *
   * @see #getStatementCount()
   */
  public int getSize()
  {
    if (this.commands == null) this.parseCommands();
    return this.commands.size();
  }

  public boolean isFullyLoaded()
  {
    return this.commands != null;
  }

  public void startIterator()
  {
    this.currentIteratorIndex = 0;
    if (this.scriptIterator == null && this.commands == null)
    {
      this.parseCommands();
    }
    else if (this.scriptIterator != null)
    {
      this.scriptIterator.reset();
    }
  }

  public void done()
  {
    if (this.scriptIterator != null)
    {
      this.scriptIterator.done();
    }
  }

  /**
   * Check for quote characters that are escaped using a
   * backslash. If turned on (flag == true) the following
   * SQL statement would be valid (different to the SQL standard):
   * <pre>INSERT INTO myTable (column1) VALUES ('Arthurs\'s house');</pre>
   * but the following Script would generate an error:
   * <pre>INSERT INTO myTable (file_path) VALUES ('c:\');</pre>
   * because the last quote would not bee seen as a closing quote
   */
  public void setCheckEscapedQuotes(boolean flag)
  {
    this.checkEscapedQuotes = flag;
  }

  public DelimiterDefinition getDelimiter()
  {
    if (this.useAlternateDelimiter) return this.alternateDelimiter;
    return this.delimiter;
  }

  public boolean supportsMixedDelimiter()
  {
    ScriptIterator parser = getParserInstance();
    return parser.supportsMixedDelimiter();
  }

  private ScriptIterator getParserInstance()
  {
    LexerBasedParser p = new LexerBasedParser(parserType);
    if (useDynamicDelimiterTester)
    {
      p.enableDynamicDelimiter();
    }
    p.setCheckEscapedQuotes(this.checkEscapedQuotes);

    checkAlternateDelimiter();

    if (p.supportsMixedDelimiter())
    {
      p.setDelimiter(delimiter);
      if (useAlternateDelimiter)
      {
        p.setAlternateDelimiter(alternateDelimiter);
      }
      else
      {
        p.setAlternateDelimiter(null);
      }
      p.setEmptyLineIsDelimiter(emptyLineIsSeparator);
    }
    else
    {
      // and disable the alternate delimiter alltogether
      // because only a single delimiter will be used
      p.setAlternateDelimiter(null);
      if (useAlternateDelimiter)
      {
        p.setDelimiter(alternateDelimiter);
        // we can't have empty lines as delimiter if using the alternate delimiter
        p.setEmptyLineIsDelimiter(false);
      }
      else
      {
        p.setDelimiter(delimiter);
        p.setEmptyLineIsDelimiter(emptyLineIsSeparator);
      }

    }
    p.setReturnStartingWhitespace(this.returnLeadingWhitespace);
    return p;
  }

  /**
   *  Parse the given SQL Script into a List of single SQL statements.
   */
  private void parseCommands()
  {
    ScriptIterator itr = null;
    boolean storeStatement = false;
    if (scriptIterator == null)
    {
      itr = getParserInstance();
      itr.setScript(this.originalScript);
      itr.setStoreStatementText(false); // no need to store the statements twice
    }
    else
    {
      itr = scriptIterator;
      itr.setStoreStatementText(true);
      storeStatement = true;
    }
    commands = new ArrayList<>();

    ScriptCommandDefinition c = null;
    int index = 0;

    while ((c = itr.getNextCommand()) != null)
    {
      if (storeStatement)
      {
        if (StringUtil.isBlank(c.getSQL())) continue;
      }
      else
      {
        if (isEmpty(c.getStartPositionInScript(), c.getEndPositionInScript())) continue;
      }

      c.setIndexInScript(index);
      index++;
      this.commands.add(c);
    }
    currentIteratorIndex = 0;
    if (scriptIterator != null)
    {
      scriptIterator.done();
    }
  }

  private boolean isEmpty(int startPos, int endPos)
  {
    if (startPos < 0) return true;
    if (endPos < 0 || endPos < startPos) return true;
    if (this.originalScript == null) return true;

    for (int i=startPos; i < endPos; i++)
    {
      char c = this.originalScript.charAt(i);
      if (!Character.isWhitespace(c)) return false;
    }
    return true;
  }

  /**
   * Returns the number of statements in the script in case the script was loaded into memory.
   *
   * @return  the number of statements or -1 if the script is not loaded into memory
   * @see #getSize()
   */
  public int getStatementCount()
  {
    if (this.scriptIterator != null)
    {
      return -1;
    }
    if (commands == null) parseCommands();
    return this.commands.size();
  }

  /**
   * Return the next {@link ScriptCommandDefinition} from the script.
   *
   */
  public String getNextCommand()
  {
    ScriptCommandDefinition command = getNextCommandDefinition();
    String result = null;
    if (command == null) return null;
    if (scriptIterator != null)
    {
      result = command.getSQL();
    }
    else
    {
      result = originalScript.substring(command.getStartPositionInScript(), command.getEndPositionInScript());
    }
    return result;
  }

  public int getLastCommandIndex()
  {
    if (this.scriptIterator == null)
    {
      return currentIteratorIndex - 1;
    }
    return -1;
  }

  /**
   * Return the next {@link ScriptCommandDefinition} from the script.
   *
   */
  public ScriptCommandDefinition getNextCommandDefinition()
  {
    ScriptCommandDefinition command = null;

    if (this.scriptIterator != null)
    {
      command = this.scriptIterator.getNextCommand();
    }
    else
    {
      if (commands == null) parseCommands();
      if (currentIteratorIndex < commands.size())
      {
        command = this.commands.get(currentIteratorIndex);
        currentIteratorIndex ++;
      }
    }
    return command;
  }

  /**
   * Creates a configured ScriptParser for the passed connection.
   *
   * The following properties will be configured:
   *
   * <ul>
   * <li>alternate delimiter</li>
   * <li>checking for escaped quotes</li>
   * <li>empty lines as delimiter</li>
   * </ul>
   *
   * @param dbConnection
   * @return a ScriptParser configured with the current settings.
   *
   * @see #setAlternateDelimiter(workbench.sql.DelimiterDefinition)
   * @see #setCheckEscapedQuotes(boolean)
   * @see #setEmptyLineIsDelimiter(boolean)
   * @see Settings#getAlternateDelimiter(workbench.db.WbConnection, workbench.sql.DelimiterDefinition)
   * @see Settings#useNonStandardQuoteEscaping(workbench.db.WbConnection)
   * @see Settings#getEmptyLineIsDelimiter()
   */
  public static ScriptParser createScriptParser(WbConnection dbConnection)
  {
    ScriptParser scriptParser = new ScriptParser(dbConnection);
    scriptParser.setAlternateDelimiter(Settings.getInstance().getAlternateDelimiter(dbConnection, null));
    scriptParser.setCheckEscapedQuotes(Settings.getInstance().useNonStandardQuoteEscaping(dbConnection));
    scriptParser.setEmptyLineIsDelimiter(Settings.getInstance().getEmptyLineIsDelimiter());
    return scriptParser;
  }

}
