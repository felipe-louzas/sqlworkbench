/*
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
package workbench.gui.actions.clipboard;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;

import workbench.interfaces.TextContainer;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.DBID;
import workbench.db.WbConnection;

import workbench.gui.actions.WbAction;

import workbench.sql.DelimiterDefinition;
import workbench.sql.parser.ScriptParser;

import workbench.util.StringUtil;

/**
 * An action, to copy the statement where the text cursor is located into the clipboard
 *
 * @author Thomas Kellerer
 */
public class CopyCurrentStatementAction
  extends WbAction
{
  private TextContainer script;
  private String dbid;
  private DelimiterDefinition alternateDelimiter;

  public CopyCurrentStatementAction(TextContainer text)
  {
    super();
    this.initMenuDefinition("MnuTxtCopyCurrentStmt");
    this.setMenuItemName(ResourceMgr.MNU_TXT_SQL);
    this.script = text;
  }

  public void setConnection(WbConnection conn)
  {
    if (conn == null)
    {
      this.dbid = null;
      this.alternateDelimiter = null;
    }
    else
    {
      this.dbid = conn.getDbId();
      this.alternateDelimiter = conn.getAlternateDelimiter();
    }
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    String sql = script.getText();
    if (StringUtil.isEmpty(sql)) return;

    ScriptParser parser = new ScriptParser(sql, DBID.fromID(dbid));
    parser.setAlternateDelimiter(alternateDelimiter);
    parser.setCheckEscapedQuotes(Settings.getInstance().useNonStandardQuoteEscaping(dbid));
    parser.setEmptyLineIsDelimiter(Settings.getInstance().getEmptyLineIsDelimiter());

    int cursor = script.getCaretPosition();

    int index = parser.getCommandIndexAtCursorPos(cursor);
    String command = parser.getCommand(index);
    command += "\n;\n";

    boolean makeSnippet = false;
    if (invokedByMouse(e) && isCtrlPressed(e))
    {
      makeSnippet = true;
    }

    if (makeSnippet)
    {
      command = CreateSnippetAction.makeJavaString(command, true);
    }

    Clipboard clp = Toolkit.getDefaultToolkit().getSystemClipboard();
    StringSelection sel = new StringSelection(command);
    clp.setContents(sel, sel);
  }

}
