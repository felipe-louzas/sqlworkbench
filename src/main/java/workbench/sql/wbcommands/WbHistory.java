/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
 * that restricts the use for certain governments.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.sql-workbench.eu/manual/license.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.eu
 */

package workbench.sql.wbcommands;

import java.sql.SQLException;
import java.sql.Types;
import java.util.Collections;
import java.util.List;

import workbench.WbManager;
import workbench.resource.GuiSettings;

import workbench.storage.DataStore;

import workbench.sql.SqlCommand;
import workbench.sql.StatementHistory;
import workbench.sql.StatementRunnerResult;
import workbench.sql.annotations.ResultNameAnnotation;
import workbench.sql.annotations.ScrollAnnotation;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class WbHistory
  extends SqlCommand
{
  public static final String VERB = "WbHistory";
  public static final String SHORT_VERB = "WbHist";

  private int maxLength = -1;

  public WbHistory()
  {
    super();
    this.isUpdatingCommand = false;
  }

  @Override
  public String getVerb()
  {
    return VERB;
  }

  @Override
  protected boolean isConnectionRequired()
  {
    return false;
  }

  public void setMaxDisplayLength(int length)
  {
    maxLength = length;
  }

  @Override
  public StatementRunnerResult execute(String sql)
    throws SQLException
  {
    StatementRunnerResult result = new StatementRunnerResult();

    StatementHistory sqlHistory = this.runner.getSqlHistory();
    List<String> history = Collections.emptyList();
    if (sqlHistory != null)
    {
      history = sqlHistory.getHistoryEntries();
    }

    String parameter = this.getCommandLine(sql);
    if (StringUtil.isNotBlank(parameter)) return result;

    DataStore ds = new DataStore(
        new String[] {"NR", "SQL"},
        new int[] {Types.INTEGER, Types.VARCHAR},
        new int[] {5, GuiSettings.getMultiLineThreshold()} );

    int index = 1;
    for (String entry : history)
    {
      int row = ds.addRow();
      ds.setValue(row, 0, index);
      ds.setValue(row, 1, getDisplayString(entry));
      index ++;
    }
    ds.resetStatus();
    ds.setResultName(VERB);
    ResultNameAnnotation.setResultName(ds, sql);
    ds.setGeneratingSql(ScrollAnnotation.getScrollToEndAnnotation() + "\n" + VERB);
    result.addDataStore(ds);
    return result;
  }

  private String getDisplayString(String sql)
  {
    if (WbManager.getInstance().isGUIMode())
    {
      return StringUtil.trim(sql);
    }

    String display = SqlUtil.makeCleanSql(sql, false, false, true, currentConnection);
    if (maxLength > -1)
    {
      display = StringUtil.getMaxSubstring(display, maxLength - 10);
    }
    return display;
  }

  @Override
  public boolean isWbCommand()
  {
    return true;
  }
}
