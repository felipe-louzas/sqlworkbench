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

import javax.swing.KeyStroke;

import workbench.interfaces.TextSelectionListener;
import workbench.resource.ResourceMgr;

import workbench.gui.actions.WbAction;
import workbench.gui.editor.JEditTextArea;
import workbench.gui.editor.TextIndenter;

/**
 *
 * @author Thomas Kellerer
 */
public class IndentSelection
  extends WbAction
  implements TextSelectionListener
{
  private final JEditTextArea area;

  public IndentSelection(JEditTextArea edit)
  {
    super();
    initMenuDefinition("MnuTxtIndent", KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0));
    setMenuItemName(ResourceMgr.MNU_TXT_EDIT);
    setEnabled(false);
    area = edit;
    area.addSelectionListener(this);
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    TextIndenter indenter = new TextIndenter(area);
    indenter.indentSelection();
  }

  @Override
  public void selectionChanged(int newStart, int newEnd)
  {
    this.setEnabled(newStart < newEnd);
  }

}
