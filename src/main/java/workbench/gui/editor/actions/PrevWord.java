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

import workbench.resource.PlatformShortcuts;

import workbench.gui.editor.JEditTextArea;
import workbench.gui.editor.TextUtilities;

/**
 *
 * @author Thomas Kellerer
 */
public class PrevWord
  extends EditorAction
{
  protected boolean select;

  public PrevWord()
  {
    super("TxtEdPrvWord", PlatformShortcuts.getDefaultPrevWord(false));
    select = false;
  }

  public PrevWord(String resourceKey, KeyStroke key)
  {
    super(resourceKey, key);
  }

  @Override
  public void actionPerformed(ActionEvent evt)
  {
    JEditTextArea textArea = getTextArea(evt);
    int caret = textArea.getCaretPosition();
    int line = textArea.getCaretLine();
    int lineStart = textArea.getLineStartOffset(line);
    caret -= lineStart;

    String lineText = textArea.getLineText(textArea.getCaretLine());

    if (caret == 0)
    {
      if (lineStart == 0)
      {
        textArea.getToolkit().beep();
        return;
      }
      caret--;
    }
    else
    {
      caret = TextUtilities.findWordStart(lineText, caret);
    }

    if (select)
    {
      textArea.select(textArea.getMarkPosition(), lineStart + caret);
    }
    else
    {
      textArea.setCaretPosition(lineStart + caret);
    }
  }
}
