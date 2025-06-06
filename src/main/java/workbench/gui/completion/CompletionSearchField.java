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
package workbench.gui.completion;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.BorderFactory;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * A quick search field for the auto completion popup
 *
 * @author Thomas Kellerer
 */
public class CompletionSearchField
  extends JTextField
  implements KeyListener, DocumentListener
{
  private final QuickSearchList parent;

  public CompletionSearchField(QuickSearchList popup, String text)
  {
    super(text);
    this.parent = popup;
    this.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
    this.addKeyListener(this);
    this.getDocument().addDocumentListener(this);
  }

  @Override
  public void keyTyped(KeyEvent e)
  {
  }

  @Override
  public void keyPressed(KeyEvent e)
  {
    if (e.getKeyCode() == KeyEvent.VK_ENTER)
    {
      e.consume();
      parent.quickSearchValueSelected();
    }
    else if (e.getKeyCode() == KeyEvent.VK_ESCAPE)
    {
      e.consume();
      this.parent.closeQuickSearch();
    }
    else if (e.getKeyCode() == KeyEvent.VK_DOWN || e.getKeyCode() == KeyEvent.VK_UP)
    {
      parent.keyPressed(e);
    }
  }

  @Override
  public void keyReleased(KeyEvent e)
  {
  }

  @Override
  public void insertUpdate(DocumentEvent e)
  {
    this.parent.selectMatchingEntry(this.getText());
  }

  @Override
  public void removeUpdate(DocumentEvent e)
  {
    this.parent.selectMatchingEntry(this.getText());
  }

  @Override
  public void changedUpdate(DocumentEvent e)
  {
    this.parent.selectMatchingEntry(this.getText());
  }

}

