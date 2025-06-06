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

import javax.swing.JMenuItem;

import workbench.gui.WbSwingUtilities;
import workbench.gui.actions.WbAction;

/**
 *
 * @author  Thomas Kellerer
 */
public class WbMenuItem
  extends JMenuItem
{
  public WbMenuItem()
  {
    super();
  }

  public WbMenuItem(String aText)
  {
    super(aText);
  }

  @Override
  public void setText(String aText)
  {
    if (aText == null)
    {
      return;
    }
    int pos = aText.indexOf('&');
    if (pos > -1)
    {
      char mnemonic = aText.charAt(pos + 1);
      if (mnemonic != ' ')
      {
        aText = aText.substring(0, pos) + aText.substring(pos + 1);
      }
      super.setText(aText);
      if (mnemonic != ' ' && mnemonic != '&')
      {
        this.setMnemonic((int) mnemonic);
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
      super.setText(aText);
    }
  }

  @Override
  public void removeAll()
  {
    super.removeAll();
    this.itemListener = null;
    this.changeListener = null;
    this.actionListener = null;
    this.setIcon(null);
    this.setAction(null);
  }

  public void dispose()
  {
    WbSwingUtilities.removeAllListeners(this);
    if (getAction() instanceof WbAction)
    {
      ((WbAction)getAction()).dispose();
    }
    removeAll();
  }
}
