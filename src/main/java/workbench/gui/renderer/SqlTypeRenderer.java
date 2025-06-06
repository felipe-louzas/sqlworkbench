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

import workbench.util.NumberStringCache;
import workbench.util.SqlUtil;

/**
 * Displays the integer values from java.sql.Types as readable names.
 *
 * @see workbench.util.SqlUtil#getTypeName(int)
 *
 * @author Thomas Kellerer
 */
public class SqlTypeRenderer
  extends ToolTipRenderer
{
  private boolean showNumericValue;

  public SqlTypeRenderer()
  {
    this(false);
  }

  public SqlTypeRenderer(boolean showValue)
  {
    super();
    this.setHorizontalAlignment(JLabel.LEFT);
    showNumericValue = showValue;
  }

  @Override
  public void prepareDisplay(Object value)
  {
    if (value != null)
    {
      int type = -1;
      try
      {
        type = ((Integer)value).intValue();
        displayValue = SqlUtil.getTypeName(type);
        StringBuilder tip = new StringBuilder(displayValue.length() + 8);
        tip.append(displayValue);
        tip.append(" (");
        tip.append(NumberStringCache.getNumberString(type));
        tip.append(')');
        String toolTip = tip.toString();
        setTooltip(toolTip);
        if (showNumericValue)
        {
          displayValue = toolTip;
        }
      }
      catch (Exception e)
      {
        displayValue = value.toString();
        setTooltip(displayValue);
      }
    }
    else
    {
      displayValue = "";
      setTooltip(null);
    }
  }

}
