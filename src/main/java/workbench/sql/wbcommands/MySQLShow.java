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
package workbench.sql.wbcommands;

import java.sql.SQLException;
import java.util.List;

import workbench.storage.DataStore;

import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;
import workbench.sql.lexer.SQLLexer;
import workbench.sql.lexer.SQLLexerFactory;
import workbench.sql.lexer.SQLToken;
import workbench.sql.parser.ParserType;

/**
 * A class to display the output of MySQL's "show engine innodb status" as a message
 * rather than a result set.
 *
 * Every other option to the show command is handled as is.
 *
 * @author Thomas Kellerer
 */
public class MySQLShow
  extends SqlCommand
{
  public static final String VERB = "SHOW";

  @Override
  public StatementRunnerResult execute(String sql)
    throws SQLException, Exception
  {
    StatementRunnerResult result =  super.execute(sql);
    if (!isInnoDBStatus(sql))
    {
      return result;
    }

    List<DataStore> status = result.getDataStores();
    if (status != null && status.size() == 1)
    {
      DataStore ds = status.get(0);
      StringBuilder msg = new StringBuilder(500);
      int col = ds.getColumnIndex("Status");
      if (col > -1)
      {
        for (int row = 0; row < ds.getRowCount(); row ++)
        {
          String value = ds.getValueAsString(row, col);
          msg.append(value);
          msg.append('\n');
        }
      }
      result.clearResultData();
      result.addMessage(msg);
    }
    return result;
  }

  /**
   * Package visible for testing purposes.
   */
  boolean isInnoDBStatus(String sql)
  {
    String[] words = new String[] { "show", "engine", "innodb", "status"};
    SQLLexer lexer = SQLLexerFactory.createLexer(ParserType.MySQL, sql);
    SQLToken token = lexer.getNextToken(false, false);
    int index = 0;
    while (token != null)
    {
      if (!token.getText().equalsIgnoreCase(words[index])) return false;
      index ++;
      token = lexer.getNextToken(false, false);
    }
    return index == 4;
  }

  @Override
  public String getVerb()
  {
    return VERB;
  }

}
