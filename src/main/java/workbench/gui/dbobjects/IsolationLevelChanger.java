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
package workbench.gui.dbobjects;

import java.sql.Connection;

import workbench.db.WbConnection;

/**
 *
 * @author Thomas Kellerer
 */
public class IsolationLevelChanger
{
  private int oldLevel = Connection.TRANSACTION_NONE;

  public void restoreIsolationLevel(WbConnection dbConnection)
  {
    if (oldLevel == Connection.TRANSACTION_READ_COMMITTED ||
        oldLevel == Connection.TRANSACTION_REPEATABLE_READ ||
        oldLevel == Connection.TRANSACTION_SERIALIZABLE)
    {
      dbConnection.setIsolationLevel(oldLevel);
    }
  }

  public void changeIsolationLevel(WbConnection dbConnection)
  {
    oldLevel = Connection.TRANSACTION_NONE;
    if (dbConnection == null) return;

    if (dbConnection.getDbSettings().useReadUncommittedForDbExplorer())
    {
      oldLevel = dbConnection.getIsolationLevel();
      dbConnection.setIsolationLevel(Connection.TRANSACTION_READ_UNCOMMITTED);
    }
  }

}
