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
package workbench.gui.profiles;

import java.awt.event.ActionEvent;

import workbench.interfaces.GroupTree;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.gui.actions.WbAction;

/**
 * @author Thomas Kellerer
 */
public class NewGroupAction
  extends WbAction
{
  private GroupTree client;

  public NewGroupAction(GroupTree panel, String resourceKey)
  {
    super();
    this.client = panel;
    this.setIcon("folder_new");
    this.initMenuDefinition(resourceKey);
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    try
    {
      this.client.addGroup(isCtrlPressed(e));
    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Error copying profile", ex);
    }
  }

  @Override
  public boolean useInToolbar()
  {
    return false;
  }

}
