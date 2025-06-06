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
package workbench.gui.actions;

import java.awt.EventQueue;
import java.awt.event.ActionEvent;

import workbench.resource.ResourceMgr;

import workbench.gui.sql.SqlPanel;

import workbench.sql.parser.ScriptParser;


/**
 *
 * @author Thomas Kellerer
 */
public class JumpToNextStatement
  extends WbAction
{
  private SqlPanel client;

  public JumpToNextStatement(SqlPanel panel)
  {
    super();
    initMenuDefinition("MnuTxtJumpToNextStmt");
    setMenuItemName(ResourceMgr.MNU_TXT_SQL);
    client = panel;
  }

  @Override
  public void actionPerformed(ActionEvent evt)
  {
    ScriptParser p = ScriptParser.createScriptParser(client.getConnection());
    p.setScript(client.getText());
    int count = p.getSize();
    if (count <= 0) return;

    int current = p.getCommandIndexAtCursorPos(client.getEditor().getCaretPosition());
    if (current == count - 1) return;

    final int pos = p.getStartPosForCommand(current + 1);
    if (pos > -1)
    {
      EventQueue.invokeLater(() -> { client.getEditor().setCaretPosition(pos); });
    }
  }

}
