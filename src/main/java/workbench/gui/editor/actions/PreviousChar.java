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
import java.awt.event.KeyEvent;

import workbench.gui.editor.JEditTextArea;

/**
 *
 * @author Thomas Kellerer
 */
public class PreviousChar
  extends EditorAction
{
  protected boolean select;

  public PreviousChar()
  {
    super("TxtEdPrevChar", KeyEvent.VK_LEFT, 0);
    select = false;
  }

  public PreviousChar(String resourceKey, int key, int modifier)
  {
    super(resourceKey, key, modifier);
  }

  @Override
  public void actionPerformed(ActionEvent evt)
  {
    JEditTextArea textArea = getTextArea(evt);
    int caret = textArea.getCaretPosition();
    if (caret == 0)
    {
      textArea.getToolkit().beep();
      if (!select)
      {
        textArea.selectNone();
      }
      return;
    }

    if (select)
    {
      textArea.select(textArea.getMarkPosition(), caret - 1);
    }
    else
    {
      textArea.setCaretPosition(caret - 1);
    }
  }
}
