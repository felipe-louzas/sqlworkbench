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
package workbench.gui.components;

import javax.swing.JLabel;

import workbench.resource.ResourceMgr;

/**
 *
 * @author Thomas Kellerer
 */
public class WbLabel
  extends JLabel
{

  public void setTextByKey(String resourceKey)
  {
    setTextByKey(resourceKey, true);
  }

  public void setTextByKey(String resourceKey, boolean includeTooltip)
  {
    setText(ResourceMgr.getString(resourceKey));
    setToolTipText(ResourceMgr.getDescription(resourceKey, false));
  }

  @Override
  public void setText(String text)
  {
    if (text == null) return;

    int pos = text.indexOf('&');
    if (pos > -1)
    {
      char mnemonic = text.charAt(pos + 1);
      if (mnemonic != ' ')
      {
        text = text.substring(0, pos) + text.substring(pos + 1);
      }

      super.setText(text);

      if (mnemonic != ' ' && mnemonic != '&')
      {
        setDisplayedMnemonic(mnemonic);
        try
        {
          this.setDisplayedMnemonicIndex(pos);
        }
        catch (Exception e)
        {
        }
      }
    }
    else
    {
      super.setText(text);
    }
  }
}
