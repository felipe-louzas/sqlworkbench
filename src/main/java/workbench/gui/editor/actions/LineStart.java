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
package workbench.gui.editor.actions;

import java.awt.event.ActionEvent;
import javax.swing.KeyStroke;
import workbench.gui.editor.InputHandler;
import workbench.gui.editor.JEditTextArea;
import workbench.resource.PlatformShortcuts;

/**
 *
 * @author Thomas Kellerer
 */
public class LineStart
  extends EditorAction
{
  protected boolean select;

  public LineStart()
  {
    super("TxtEdLineStart", PlatformShortcuts.getDefaultStartOfLine(false));
    select = false;
  }

  protected LineStart(String resourceKey, KeyStroke key)
  {
    super(resourceKey, key);
  }

  @Override
  public void actionPerformed(ActionEvent evt)
  {
    JEditTextArea textArea = getTextArea(evt);

    int caret = textArea.getCaretPosition();

    int firstLine = textArea.getFirstLine();

    int firstOfLine = textArea.getLineStartOffset(textArea.getCaretLine());
    int firstVisibleLine = (firstLine == 0 ? 0 : firstLine + textArea.getElectricScroll());
    int firstVisible = textArea.getLineStartOffset(firstVisibleLine);

    if (caret == 0)
    {
      textArea.getToolkit().beep();
      if (!select)
      {
        textArea.selectNone();
      }
      return;
    }
    else if (!Boolean.TRUE.equals(textArea.getClientProperty(InputHandler.SMART_HOME_END_PROPERTY)))
    {
      caret = firstOfLine;
    }
    else if (caret == firstVisible)
    {
      caret = 0;
    }
    else if (caret == firstOfLine)
    {
      caret = firstVisible;
    }
    else
    {
      caret = firstOfLine;
    }

    if (select)
    {
      textArea.select(textArea.getMarkPosition(), caret);
    }
    else
    {
      textArea.selectNone();
      textArea.setCaretPosition(caret);
    }
  }
}
