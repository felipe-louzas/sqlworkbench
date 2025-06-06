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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

/**
 * Action to toggle the detection of prepared statements during SQL execution
 *
 * @see workbench.resource.Settings#setCheckPreparedStatements(boolean)
 *
 * @author Thomas Kellerer
 */
public class ConsolidateLogAction
  extends CheckBoxAction
  implements PropertyChangeListener
{
  public ConsolidateLogAction()
  {
    super("LblConsolidateLog", Settings.PROPERTY_CONSOLIDATE_LOG_MESSAGES);
    this.setMenuItemName(ResourceMgr.MNU_TXT_SQL);
    Settings.getInstance().addPropertyChangeListener(this, Settings.PROPERTY_CONSOLIDATE_LOG_MESSAGES);
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt)
  {
    setSwitchedOn(Settings.getInstance().getConsolidateLogMsg());
  }

  @Override
  public void dispose()
  {
    super.dispose();
    Settings.getInstance().removePropertyChangeListener(this);
  }

}
