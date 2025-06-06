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

import java.io.IOException;
import java.util.List;

import workbench.db.WbConnection;

import workbench.sql.TableNameCompletor;

import workbench.util.CollectionUtil;
import workbench.util.FileUtil;

import jline.ArgumentCompletor;
import jline.Completor;
import jline.ConsoleReader;
import jline.History;
import jline.NullCompletor;
import jline.Terminal;

/**
 *
 * @author Thomas Kellerer
 */
public class JLineWrapper
  implements WbConsole
{
  private ConsoleReader reader;
  private TableNameCompletor currentCompletor;

  public JLineWrapper()
    throws IOException
  {
    reader = new ConsoleReader();
    reader.setUseHistory(true);
    reader.setUsePagination(false);
    reader.setBellEnabled(false);
    List<Completor> completors = CollectionUtil.arrayList(new ClipCompletor(), new NullCompletor());
    reader.addCompletor(new ArgumentCompletor(completors));
  }

  @Override
  public void clearScreen()
  {
    try
    {
      reader.clearScreen();
    }
    catch (IOException ex)
    {
    }
  }

  @Override
  public void reset()
  {
    try
    {
      reader.printString("\u001b[2K");
      reader.killLine();
      reader.printNewline();
      reader.getInput().reset();
    }
    catch (IOException ex)
    {
    }
  }

  @Override
  public char readCharacter()
  {
    try
    {
      return (char)reader.readVirtualKey();
    }
    catch (IOException ex)
    {
    }
    return 0;
  }

  @Override
  public String readLineWithoutHistory(String prompt)
  {
    boolean old = reader.getUseHistory();
    try
    {
      reader.setUseHistory(false);
      return readLine(prompt);
    }
    finally
    {
      reader.setUseHistory(old);
    }
  }

  @Override
  public int getColumns()
  {
    Terminal t = Terminal.getTerminal();
    if (t != null)
    {
      return t.getTerminalWidth();
    }
    return -1;
  }

  @Override
  public String readPassword(String prompt)
  {
    try
    {
      return reader.readLine(prompt, Character.valueOf('*'));
    }
    catch (IOException e)
    {
      return null;
    }
  }

  @Override
  public String readLine(String prompt)
  {
    try
    {
      return reader.readLine(prompt);
    }
    catch (IOException e)
    {
      return null;
    }
  }

  @Override
  public void shutdown()
  {
    History h = reader.getHistory();
    if (h != null)
    {
      FileUtil.closeQuietely(h.getOutput());
    }
  }

  @Override
  public void clearHistory()
  {
    History h = reader.getHistory();
    if (h != null)
    {
      h.clear();
    }
  }

  @Override
  public void addToHistory(List<String> lines)
  {
    if (CollectionUtil.isEmpty(lines)) return;

    History h = reader.getHistory();
    if (h != null)
    {
      for (String line : lines)
      {
        h.addToHistory(line);
      }
    }
  }

  @Override
  public void setConnection(WbConnection connection)
  {
    if (currentCompletor != null && currentCompletor.isSameConnection(connection)) return;

    if (currentCompletor != null)
    {
      reader.removeCompletor(currentCompletor);
      currentCompletor = null;
    }

    if (connection != null)
    {
      currentCompletor = new TableNameCompletor(connection);
      reader.addCompletor(currentCompletor);
    }
  }

}
