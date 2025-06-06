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

import javax.swing.SwingConstants;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.util.WbDateFormatter;

/**
 * A class to render date and timestamp values.
 * <br/>
 * The values are formatted according to the global settings.
 *
 * @author  Thomas Kellerer
 */
public class DateColumnRenderer
  extends ToolTipRenderer
{
  private final WbDateFormatter dateFormatter;

  public static final String DEFAULT_FORMAT = "yyyy-MM-dd HH:mm:ss";

  public DateColumnRenderer()
  {
    super();
    this.dateFormatter = new WbDateFormatter(DEFAULT_FORMAT);
    this.setHorizontalAlignment(SwingConstants.RIGHT);
  }

  public DateColumnRenderer(String aDateFormat, boolean variableFractions)
  {
    this();
    this.setFormat(aDateFormat, variableFractions);
  }

  public final void setFormat(String aDateFormat, boolean variableFractions)
  {
    try
    {
      synchronized (this.dateFormatter)
      {
        this.dateFormatter.applyPattern(aDateFormat, variableFractions);
      }
    }
    catch (Exception e)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Error when setting date format [" + aDateFormat + "] default format [" + DEFAULT_FORMAT + "] will be used instead", e);
      this.dateFormatter.applyPattern(DEFAULT_FORMAT);
    }
  }

  @Override
  public void prepareDisplay(Object value)
  {
    try
    {
      this.displayValue = this.dateFormatter.formatDateTimeValue(value);

      if (showTooltip)
      {
        this.tooltip = displayValue;
      }
      else
      {
        this.tooltip = null;
      }
    }
    catch (Throwable cc)
    {
      this.displayValue = value.toString();
      setTooltip(displayValue);
    }
  }

}
