/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0 (the "License")
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
package workbench.gui.tabhistory;

import java.io.File;
import java.util.List;

import workbench.gui.sql.EditorHistoryEntry;

/**
 *
 * @author Thomas Kellerer
 */
public class ClosedTabInfo
{
  private final String tabName;
  private final List<EditorHistoryEntry> sqlHistory;
  private final int tabIndex;
  private File externalFile;
  private String fileEncoding;

  public ClosedTabInfo(String name, List<EditorHistoryEntry> history, int index)
  {
    this.tabName = name;
    this.sqlHistory = history;
    this.tabIndex = index;
  }

  public void setExternalFile(File file, String encoding)
  {
    externalFile = file;
    fileEncoding = encoding;
  }

  public String getFileEncoding()
  {
    return fileEncoding;
  }
  
  public File getExternalFile()
  {
    return externalFile;
  }

  public int getTabIndex()
  {
    return tabIndex;
  }

  public String getTabName()
  {
    return tabName;
  }

  public List<EditorHistoryEntry> getHistory()
  {
    return sqlHistory;
  }

  @Override
  public String toString()
  {
    return "ClosedTabInfo[index: " + tabIndex + ", title: \"" + tabName + "\", history size: " + sqlHistory.size() + "]";
  }

  public void clear()
  {
    sqlHistory.clear();
  }
}
