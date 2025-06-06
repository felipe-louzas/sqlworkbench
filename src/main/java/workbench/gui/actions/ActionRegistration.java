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

import workbench.gui.actions.clipboard.CopySelectedAsSqlDeleteInsertAction;
import workbench.gui.actions.clipboard.CopySelectedAsSqlInsertAction;
import workbench.gui.actions.clipboard.CopySelectedAsSqlUpdateAction;
import workbench.gui.dbobjects.objecttree.FindObjectAction;

/**
 * Register actions with the ShortcutManager that are not created upon startup.
 * For this, a dummy action is created (with no client) which will
 * kick off the registration with the ShortcutManager
 *
 * @author Thomas Kellerer
 */
public class ActionRegistration
{
  public static void registerActions()
  {
    new ToggleTableSourceAction(null);
    new ReloadAction(null);
    new CopySelectedAsSqlDeleteInsertAction(null);
    new CopySelectedAsSqlInsertAction(null);
    new CopySelectedAsSqlUpdateAction(null);
    new SetNullAction(null);
    new SelectFkValueAction(null);
    new FindObjectAction(null);
    new SearchDbExplorerProcedure(null, null);
    new SearchDbExplorerTable(null, null);
  }
}
