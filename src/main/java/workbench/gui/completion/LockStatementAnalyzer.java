/*
 * This file is part of SQL Workbench/J, http://www.sql-workbench.net
 *
 * Copyright 2002-2013, Thomas Kellerer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at.
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.net
 *
 */
package workbench.gui.completion;

import java.util.Set;

import workbench.db.WbConnection;

/**
 *
 * @author Thomas Kellerer
 */
public class LockStatementAnalyzer
  extends BaseAnalyzer
{
  public LockStatementAnalyzer(WbConnection conn, String statement, int cursorPos)
  {
    super(conn, statement, cursorPos);
  }

  @Override
  protected void checkContext()
  {
    int tablePos = -1;
    int inPos = -1;
    int modePos = -1;
    // Postgres, Oracle
    if (verb.equalsIgnoreCase("LOCK"))
    {
      tablePos = parsingUtil.getKeywordPosition(Set.of("TABLE"), sql, 0);
      inPos = parsingUtil.getKeywordPosition(Set.of("IN"), sql, 0);
      modePos = parsingUtil.getKeywordPosition(Set.of("MODE"), sql, 0);
      if (inPos > 0 && between(cursorPos, inPos, modePos))
      {
        context = CONTEXT_KW_LIST;
        keywordFile = "lock_modes.txt";
      }
      else if (between(cursorPos, verb.length(), tablePos) || between(cursorPos, tablePos, inPos))
      {
        context = CONTEXT_TABLE_LIST;
      }
    }
    // MySQL
    else if (verb.equalsIgnoreCase("LOCK TABLES"))
    {
      if (cursorPos > verb.length())
      {
        context = CONTEXT_TABLE_LIST;
      }
    }
  }

}
