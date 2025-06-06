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

import javax.swing.text.BadLocationException;

import workbench.gui.editor.JEditTextArea;

/**
 *
 * @author Thomas Kellerer
 */
public class DeleteChar
  extends EditorAction
{
  public DeleteChar()
  {
    super("TxtEdDelChar", KeyEvent.VK_DELETE, 0);
  }

  @Override
  public void actionPerformed(ActionEvent evt)
  {
    JEditTextArea textArea = getTextArea(evt);

    if (!textArea.isEditable())
    {
      textArea.getToolkit().beep();
      return;
    }

    if (textArea.getSelectionStart() != textArea.getSelectionEnd())
    {
      if (textArea.isEmptyRectangleSelection())
      {
        textArea.doRectangleDeleteChar();
      }
      else
      {
        textArea.setSelectedText("");
      }
    }
    else
    {
      int caret = textArea.getCaretPosition();
      if (caret == textArea.getDocumentLength())
      {
        textArea.getToolkit().beep();
        return;
      }
      try
      {
        textArea.getDocument().remove(caret, 1);
      }
      catch (BadLocationException bl)
      {
        bl.printStackTrace();
      }
    }
  }
}
