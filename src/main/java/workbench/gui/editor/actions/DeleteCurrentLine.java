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

import javax.swing.text.BadLocationException;

import workbench.resource.Settings;

import workbench.gui.editor.JEditTextArea;

/**
 *
 * @author Thomas Kellerer
 */
public class DeleteCurrentLine
  extends EditorAction
{
  public DeleteCurrentLine()
  {
    super("TxtEdDelLine", null);
  }

  @Override
  public void actionPerformed(ActionEvent evt)
  {
    JEditTextArea textArea = getTextArea(evt);
    if (textArea == null) return;

    int line = textArea.getCaretLine();

    int lineStart = textArea.getLineStartOffset(line);
    int len = textArea.getLineLength(line);
    if (line < textArea.getLineCount() - 1)
    {
      len += Settings.getInstance().getInternalEditorLineEnding().length();
    }
    try
    {
      textArea.getDocument().remove(lineStart, len);
    }
    catch (BadLocationException bl)
    {
      bl.printStackTrace();
    }
  }
}
