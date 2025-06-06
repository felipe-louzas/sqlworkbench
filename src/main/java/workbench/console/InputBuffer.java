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
package workbench.console;

import workbench.db.DBID;
import workbench.db.WbConnection;

import workbench.sql.ScriptCommandDefinition;
import workbench.sql.parser.ScriptParser;

import workbench.util.StringUtil;


/**
 * A buffer that collects pieces of text entered by the user until
 * it is terminated with a {@link workbench.sql.DelimiterDefinition}
 *
 * @author Thomas Kellerer
 */
public class InputBuffer
{
  private StringBuilder script;
  private ScriptParser parser;

  public InputBuffer()
  {
    script = new StringBuilder(1000);
    parser = new ScriptParser();
  }

  public void setConnection(WbConnection conn)
  {
    if (conn == null) return;
    setDbId(conn.getDbId());
    parser.setAlternateDelimiter(conn.getAlternateDelimiter());
  }

  public void setDbId(String dbid)
  {
    if (StringUtil.isBlank(dbid)) return;
    parser = new ScriptParser(DBID.fromID(dbid));
  }

  public String getScript()
  {
    return script.toString();
  }

  public int getLength()
  {
    return script.length();
  }

  public void clear()
  {
    script.setLength(0);
  }

  public boolean addLine(String line)
  {
    script.append('\n');
    script.append(line);
    return isComplete();
  }

  public boolean isComplete()
  {
    String sql = script.toString();
    parser.setScript(sql);
    parser.startIterator();

    ScriptCommandDefinition cmd = parser.getNextCommandDefinition();
    if (cmd == null) return false;

    if (cmd.getDelimiterNeeded())
    {
      return cmd.getDelimiterUsed() != null;
    }
    return true;
  }
}
