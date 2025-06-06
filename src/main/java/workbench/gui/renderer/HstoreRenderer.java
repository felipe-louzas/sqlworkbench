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

import java.util.Map;

import workbench.db.postgres.HstoreSupport;


/**
 * A class to render and edit BLOB columns in a result set.
 * <br/>
 * It uses a BlobColumnPanel to display the information to the user. The renderer
 * is registered with the BlobColumnPanel's button as an actionlistener and will then
 * display a dialog with details about the blob.
 * <br/>
 *
 * @see workbench.gui.components.BlobHandler#showBlobInfoDialog(java.awt.Frame, Object, boolean)
 *
 * @author  Thomas Kellerer
 */
public class HstoreRenderer
  extends ToolTipRenderer
{
  public HstoreRenderer()
  {
    super();
  }

  @Override
  public void prepareDisplay(Object value)
  {
    if (value instanceof Map)
    {
      displayValue = HstoreSupport.getDisplay((Map)value);
    }
    else
    {
      displayValue = value.toString();
    }
    setTooltip(displayValue);
  }

}
