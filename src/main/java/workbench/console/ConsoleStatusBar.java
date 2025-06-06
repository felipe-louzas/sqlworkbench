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

import java.io.PrintStream;

import workbench.interfaces.StatusBar;

/**
 * An implementation of the {@link workbench.interfaces.StatusBar} interface
 * to display information in Console mode
 *
 * @author Thomas Kellerer
 */
public class ConsoleStatusBar
  implements StatusBar
{
  private PrintStream output;
  private String lastMessage;

  public ConsoleStatusBar()
  {
    output = System.out;
  }

  private String createDeleteString(String original)
  {
    if (original == null) return "\r";
    StringBuilder result = new StringBuilder(original.length()+2);
    result.append('\r');
    for (int i = 0; i < original.length(); i++)
    {
      result.append(' ');
    }
    result.append('\r');
    return result.toString();
  }

  @Override
  public void setStatusMessage(String message, int duration)
  {
    setStatusMessage(message);
  }

  @Override
  public void setStatusMessage(String message)
  {
    if (lastMessage != null)
    {
      output.print(createDeleteString(lastMessage));
    }
    output.print('\r');
    output.print(message);
    this.lastMessage = message;
  }

  @Override
  public void clearStatusMessage()
  {
    if (lastMessage != null)
    {
      output.print(createDeleteString(lastMessage));
      lastMessage = null;
    }
    else
    {
      output.print('\r');
    }
  }

  @Override
  public void doRepaint()
  {
  }

  @Override
  public String getText()
  {
    return lastMessage;
  }

}
