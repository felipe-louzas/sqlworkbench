/*
 * MacroRunner.java
 *
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
package workbench.gui.macros;

import java.util.Map;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.exporter.TextRowDataConverter;

import workbench.gui.WbSwingUtilities;

import workbench.storage.ResultInfo;
import workbench.storage.RowData;

import workbench.sql.VariablePool;
import workbench.sql.macros.MacroDefinition;

import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class MacroRunner
{

  public void runMacro(MacroDefinition macro, MacroClient client, boolean replaceEditorText)
  {
    if (macro == null) return;

    String sql = handleReplacement(macro.getText(), client, true);
    if (sql != null)
    {
      client.executeMacroSql(sql, replaceEditorText, macro.isAppendResult(), false);
    }
  }

  public String handleReplacement(String macroText, MacroClient client, boolean showError)
  {
    if (client == null || StringUtil.isBlank(macroText)) return macroText;

    if (hasSelectedKey(macroText))
    {
      String selected = client.getSelectedText();
      if (selected == null && showError)
      {
        WbSwingUtilities.showErrorMessageKey(client.getPanel(), "ErrNoSelection4Macro");
        return null;
      }
      macroText = replaceSelected(macroText, StringUtil.coalesce(selected, ""));
    }

    if (hasCurrentKey(macroText))
    {
      String current = client.getStatementAtCursor();
      if (current == null && showError)
      {
        WbSwingUtilities.showErrorMessageKey(client.getPanel(), "ErrNoCurrent4Macro");
        return null;
      }
      macroText = replaceCurrent(macroText, StringUtil.coalesce(current, ""));
    }

    if (hasTextKey(macroText))
    {
      macroText = replaceEditorText(macroText, client.getText());
    }
    return macroText;
  }

  protected boolean hasTextKey(String sql)
  {
    if (sql == null) return false;
    return (sql.contains(getEditorTextKey()));
  }

  protected boolean hasSelectedKey(String sql)
  {
    if (sql == null) return false;
    return (sql.contains(getSelectedTextKey())) || (sql.contains(getSelectedStatementKey()));
  }

  protected boolean hasCurrentKey(String sql)
  {
    if (sql == null) return false;
    return (sql.contains(getCurrentStatementKey()));
  }

  protected String replaceCurrent(String sql, String statementAtCursor)
  {
    if (statementAtCursor == null || sql == null) return sql;
    return StringUtil.replace(sql, getCurrentStatementKey(), statementAtCursor);
  }

  protected String replaceEditorText(String sql, String text)
  {
    if (text == null || sql == null) return sql;
    return StringUtil.replace(sql, getEditorTextKey(), text);
  }

  protected String replaceSelected(String sql, String selectedText)
  {
    if (selectedText == null || sql == null) return sql;

    if (sql.contains(getSelectedTextKey()))
    {
      return StringUtil.replace(sql, getSelectedTextKey(), selectedText);
    }
    else if (sql.contains(getSelectedStatementKey()))
    {
      String stmt = selectedText.trim();
      if (stmt.endsWith(";"))
      {
        stmt = stmt.substring(0, stmt.length() - 1);
      }
      return StringUtil.replace(sql, getSelectedStatementKey(), stmt);
    }
    return sql;
  }

  public void runDataMacro(MacroDefinition macro, ResultInfo info, RowData row, MacroClient client, String poolID,
                           Map<String, String> columnMap, boolean refreshCurrentResult)
  {
    if (macro == null) return;
    if (info == null) return;
    if (row == null) return;
    if (client == null) return;

    TextRowDataConverter converter = new TextRowDataConverter();
    converter.setResultInfo(info);
    VariablePool pool = VariablePool.getInstance(poolID);

    for (int i = 0; i < info.getColumnCount(); i++)
    {
      String col = info.getColumnName(i);
      String varName = StringUtil.coalesce(columnMap.get(col), col);
      if (pool.isValidVariableName(varName))
      {
        String data = converter.getValueAsFormattedString(row, i);
        if (StringUtil.isNotEmpty(data))
        {
          pool.setParameterValue(varName, data);
        }
      }
      else
      {
        LogMgr.logWarning(new CallerInfo(){}, "Column name: " + col + " is not a valid SQL Workbench variable name. Column will be ignored.");
      }
    }
    String sql = macro.getText();
    client.executeMacroSql(sql, false, true, refreshCurrentResult);
  }

  private String getSelectedTextKey()
  {
    return Settings.getInstance().getProperty("workbench.macro.key.selection", "${selection}$");
  }

  private String getSelectedStatementKey()
  {
    return Settings.getInstance().getProperty("workbench.macro.key.selectedstmt", "${selected_statement}$");
  }

  private String getCurrentStatementKey()
  {
    return Settings.getInstance().getProperty("workbench.macro.key.currentstatement", "${current_statement}$");
  }

  private String getEditorTextKey()
  {
    return Settings.getInstance().getProperty("workbench.macro.key.editortext", "${text}$");
  }

}
