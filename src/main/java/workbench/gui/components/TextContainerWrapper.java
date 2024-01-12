/*
 * This file is part of SQL Workbench/J, http://www.sql-workbench.net
 *
 * Copyright 2002-2024 Thomas Kellerer
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
package workbench.gui.components;

import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

import workbench.interfaces.TextContainer;

/**
 *
 * @author Thomas Kellerer
 */
public class TextContainerWrapper
  extends JTextArea
  implements TextContainer {
  
  public TextContainerWrapper()
  {
  }

  public TextContainerWrapper(Document doc)
  {
    super(doc);
  }


  @Override
  public int getLineOfOffset(int offset)
  {
    try
    {
      return super.getLineOfOffset(offset);
    }
    catch (BadLocationException ex)
    {
      return -1;
    }
  }

  @Override
  public void setSelectedText(String text)
  {
    super.replaceSelection(text);
  }

  @Override
  public boolean isTextSelected()
  {
    return getSelectionEnd() > getSelectionStart();
  }

  @Override
  public String getWordAtCursor(String wordChars)
  {
    return null;
  }

  @Override
  public int getStartInLine(int offset)
  {
    try
    {
      int line = getLineOfOffset(offset);
      int start = getLineStartOffset(line);
      return offset - start;
    }
    catch (BadLocationException ex)
    {
      return -1;
    }
  }

  @Override
  public String getLineText(int line)
  {
    try
    {
      int start = getLineStartOffset(line);
      int end = getLineEndOffset(line);
      return getText(end, start - end);
    }
    catch (BadLocationException ble)
    {
      return null;
    }
  }

}
