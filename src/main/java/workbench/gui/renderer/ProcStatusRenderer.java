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
package workbench.gui.renderer;

import javax.swing.JLabel;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.JdbcProcedureReader;

/**
 * Displays the return type of a stored procedure as a readable text.
 * <br/>
 * @see workbench.db.JdbcProcedureReader#convertProcTypeToSQL(int)
 *
 * * @author Thomas Kellerer
 */
public class ProcStatusRenderer
  extends ToolTipRenderer
{

  public ProcStatusRenderer()
  {
    super();
    this.setHorizontalAlignment(JLabel.LEFT);
  }

  @Override
  public void prepareDisplay(Object value)
  {
    try
    {
      Integer status = (Integer)value;
      this.displayValue = JdbcProcedureReader.convertProcTypeToSQL(status.intValue());
    }
    catch (ClassCastException cce)
    {
      LogMgr.logWarning(new CallerInfo(){}, "The current value (" + value + ") is not an Integer!", cce);
      this.displayValue = (value == null ? "" : value.toString());
    }
  }

}
