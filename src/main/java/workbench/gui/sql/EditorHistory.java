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
package workbench.gui.sql;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.gui.actions.ClearStatementHistoryAction;
import workbench.gui.actions.FirstStatementAction;
import workbench.gui.actions.LastStatementAction;
import workbench.gui.actions.NextStatementAction;
import workbench.gui.actions.PrevStatementAction;
import workbench.gui.actions.WbAction;

import workbench.util.EncodingUtil;
import workbench.util.StringUtil;

/**
 * Stores the SQL scripts entered in the {@link SqlPanel} and manages
 * a history of statements.
 *
 * @author  Thomas Kellerer
 */
public class EditorHistory
{
  private static final String LIST_DELIMITER = "----------- WbStatement -----------";

  private final List<EditorHistoryEntry> history;
  private int currentEntry;
  private final int maxSize;
  private boolean changed;
  private EditorPanel editor;
  private NextStatementAction nextStmtAction;
  private PrevStatementAction prevStmtAction;
  private FirstStatementAction firstStmtAction;
  private LastStatementAction lastStmtAction;
  private ClearStatementHistoryAction clearAction;

  public EditorHistory(int size)
  {
    this.maxSize = size;
    this.history = new ArrayList<>(size + 2);
  }

  public EditorHistory(EditorPanel ed, int size)
  {
    this.maxSize = size;
    this.history = new ArrayList<>(size + 2);
    this.editor = ed;
    this.firstStmtAction = new FirstStatementAction(this);
    this.firstStmtAction.setEnabled(false);

    this.prevStmtAction = new PrevStatementAction(this);
    this.prevStmtAction.setEnabled(false);

    this.nextStmtAction = new NextStatementAction(this);
    this.nextStmtAction.setEnabled(false);

    this.lastStmtAction = new LastStatementAction(this);
    this.lastStmtAction.setEnabled(false);

    this.clearAction = new ClearStatementHistoryAction(this);
    this.clearAction.setEnabled(false);
  }

  public synchronized void setEnabled(boolean flag)
  {
    nextStmtAction.setEnabled(flag);
    prevStmtAction.setEnabled(flag);
    firstStmtAction.setEnabled(flag);
    lastStmtAction.setEnabled(flag);
  }

  public WbAction getShowFirstStatementAction() { return this.firstStmtAction; }
  public WbAction getShowLastStatementAction() { return this.lastStmtAction; }
  public WbAction getShowNextStatementAction() { return this.nextStmtAction; }
  public WbAction getShowPreviousStatementAction() { return this.prevStmtAction; }
  public WbAction getClearHistoryAction() { return this.clearAction; }

  public synchronized void replaceHistory(List<EditorHistoryEntry> newHistory)
  {
    clear();
    history.addAll(newHistory);
    showLastStatement();
  }

  public synchronized void addContent(EditorPanel edit)
  {
    boolean includeFiles = Settings.getInstance().getStoreFilesInHistory();
    if (!includeFiles && edit.hasFileLoaded()) return;

    int maxLength = Settings.getInstance().getIntProperty("workbench.sql.history.maxtextlength", 1024*1024*10);
    if (edit.getDocumentLength() > maxLength) return;

    String text = edit.getText();
    if (text == null || text.length() == 0) return;

    if (edit.currentSelectionIsTemporary())
    {
      addContent(text, edit.getCaretPosition(), 0, 0);
    }
    else
    {
      addContent(text, edit.getCaretPosition(), 0, 0);
    }
    checkActions();
  }

  public synchronized void addContent(String content, int caretPos, int selectionStart, int selectionEnd)
  {
    EditorHistoryEntry entry = new EditorHistoryEntry(content, caretPos, selectionStart, selectionEnd);
    try
    {
      EditorHistoryEntry top = this.getTopEntry();
      if (top != null && top.equals(entry)) return;
      this.addEntry(entry);
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not add entry", e);
    }
  }

  public void addEntry(EditorHistoryEntry entry)
  {
    this.history.add(entry);
    if (this.history.size() > this.maxSize)
    {
      this.history.remove(0);
    }
    this.currentEntry = this.history.size() - 1;
    this.changed = true;
  }

  public boolean hasNext()
  {
    return (this.currentEntry < (this.history.size() - 1));
  }

  public boolean hasPrevious()
  {
    return (this.currentEntry > 0);
  }

  public void clear()
  {
    this.currentEntry = 0;
    this.history.clear();
    this.changed = false;
    this.checkActions();
  }

  public void dispose()
  {
    clear();
    WbAction.dispose(nextStmtAction, prevStmtAction, firstStmtAction, lastStmtAction, clearAction);
  }

  public void showLastStatement()
  {
    if (this.history.isEmpty()) return;
    if (editor == null) return;
    if (!editor.isEditable()) return;
    this.currentEntry = this.history.size() - 1;
    EditorHistoryEntry entry = this.history.get(this.currentEntry);
    entry.applyTo(editor);
    checkActions();
  }

  public void showFirstStatement()
  {
    if (this.history.isEmpty()) return;
    if (editor == null) return;
    if (!editor.isEditable()) return;
    this.currentEntry = 0;
    EditorHistoryEntry entry = this.history.get(this.currentEntry);
    entry.applyTo(editor);
    checkActions();
  }

  public void showCurrent()
  {
    if (this.currentEntry >= this.history.size()) return;
    if (editor == null) return;
    if (!editor.isEditable()) return;
    EditorHistoryEntry entry = this.history.get(this.currentEntry);
    entry.applyTo(editor, true);
    checkActions();
  }

  public void showPreviousStatement()
  {
    if (!this.hasPrevious()) return;
    if (editor == null) return;
    if (!editor.isEditable()) return;
    EditorHistoryEntry entry = this.getPreviousEntry();
    entry.applyTo(editor);
    checkActions();
  }

  public void showNextStatement()
  {
    if (!this.hasNext()) return;
    if (editor == null) return;
    EditorHistoryEntry entry = this.getNextEntry();
    entry.applyTo(editor);
    checkActions();
  }

  public EditorHistoryEntry getTopEntry()
  {
    if (this.history.size() < 1) return null;
    EditorHistoryEntry entry = this.history.get(this.history.size() - 1);
    return entry;
  }

  private EditorHistoryEntry getPreviousEntry()
  {
    if (this.currentEntry <= 0) return null;
    this.currentEntry--;
    EditorHistoryEntry entry = this.history.get(this.currentEntry);
    return entry;
  }

  private EditorHistoryEntry getNextEntry()
  {
    if (this.currentEntry >= this.history.size() - 1) return null;
    this.currentEntry++;
    EditorHistoryEntry entry = this.history.get(this.currentEntry);
    return entry;
  }

  private static final String KEY_POS = "##sqlwb.pos=";
  private static final String KEY_START = "##sqlwb.selStart=";
  private static final String KEY_END = "##sqlwb.selEnd=";

  public void writeToStream(OutputStream out)
  {

    String lineEnding = "\n";
    try
    {
      Writer writer = EncodingUtil.createWriter(out, "UTF-8");

      int count = this.history.size();
      for (int i=0; i < count; i++)
      {
        EditorHistoryEntry entry = this.history.get(i);
        writer.write(KEY_POS);
        writer.write(Integer.toString(entry.getCursorPosition()));
        writer.write(lineEnding);

        writer.write(KEY_START);
        writer.write(Integer.toString(entry.getSelectionStart()));
        writer.write(lineEnding);

        writer.write(KEY_END);
        writer.write(Integer.toString(entry.getSelectionEnd()));
        writer.write(lineEnding);

        // Make sure the editor text is converted to the correct line ending
        BufferedReader reader = new BufferedReader(new StringReader(entry.getText()));
        String line = reader.readLine();
        while (line != null)
        {
          int len = StringUtil.getRealLineLength(line);
          if (len > 0)
          {
            writer.write(line.substring(0,len));
          }
          writer.write(lineEnding);
          line = reader.readLine();
        }

        //writer.write(lineEnding);
        writer.write(LIST_DELIMITER);
        writer.write(lineEnding);
      }
      writer.flush();
      this.changed = false;
    }
    catch (IOException e)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not write history!", e);
    }
  }

  public List<EditorHistoryEntry> getEntries()
  {
    return new ArrayList<>(history);
  }

  public boolean isChanged()
  {
    return this.changed;
  }

  public void readFromStream(InputStream in)
  {
    StringBuilder content = new StringBuilder(500);
    int pos = 0;
    int start = -1;
    int end = -1;

    String lineEnding = "\n";
    try (BufferedReader reader = new BufferedReader(EncodingUtil.createReader(in , "UTF-8"));)
    {
      String line = reader.readLine();
      while(line != null)
      {
        if (line.equals(LIST_DELIMITER))
        {
          try
          {
            EditorHistoryEntry entry = new EditorHistoryEntry(content.toString(), pos, start, end);
            this.addEntry(entry);
            pos = 0;
            start = -1;
            end = -1;
            content = new StringBuilder(500);
          }
          catch (Exception e)
          {
            LogMgr.logError(new CallerInfo(){}, "Error when creating SqlHistoryEntry", e);
          }
        }
        else if (line.startsWith(KEY_POS))
        {
          pos = StringUtil.getIntValue(line.substring(KEY_POS.length()), -1);
        }
        else if (line.startsWith(KEY_START))
        {
          start = StringUtil.getIntValue(line.substring(KEY_START.length()), -1);
        }
        else if (line.startsWith(KEY_END))
        {
          end = StringUtil.getIntValue(line.substring(KEY_END.length()), -1);
        }
        else
        {
          int len = StringUtil.getRealLineLength(line);
          if (len > 0)
          {
            content.append(line, 0, len);
          }
          content.append(lineEnding);
        }
        line = reader.readLine();
      }
      this.changed = false;
    }
    catch (IOException e)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not read history!", e);
    }

    if (content.length() > 0)
    {
      EditorHistoryEntry entry = new EditorHistoryEntry(content.toString(), pos, start, end);
      this.addEntry(entry);
    }
  }

  private void checkActions()
  {
    this.nextStmtAction.setEnabled(this.hasNext());
    this.lastStmtAction.setEnabled(this.hasNext());
    this.prevStmtAction.setEnabled(this.hasPrevious());
    this.firstStmtAction.setEnabled(this.hasPrevious());
    this.clearAction.setEnabled(this.history.size() > 0);
  }
}
