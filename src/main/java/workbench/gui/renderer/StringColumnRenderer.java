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


/**
 * A renderer to display character data.
 * <br/>
 * This is basically a ToolTipRenderer, but for performance
 * reasons we are assuming the values are all of type string.
 * So we can use a type cast in the getDisplay() method
 * instead of toString() which is much faster when no exceptions
 * are thrown.
 *
 * @author  Thomas Kellerer
 */
public class StringColumnRenderer
  extends ToolTipRenderer
{

  @Override
  public void prepareDisplay(Object aValue)
  {
    try
    {
      this.displayValue = (String)aValue;
    }
    catch (Throwable e)
    {
      displayValue = (aValue == null ? null : aValue.toString());
    }
    setTooltip(displayValue);
  }

}
