/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
 * that restricts the use for certain governments.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.sql-workbench.eu/manual/license.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.eu
 */

package workbench.gui.dialogs;

import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;

import workbench.resource.ResourceMgr;

import workbench.util.QuoteEscapeType;

/**
 *
 * @author Thomas Kellerer
 */
public class QuoteEscapeSelector
  extends JComboBox
{

  public QuoteEscapeSelector()
  {
    super();
    String[] items = new String[]
    {
      ResourceMgr.getString("TxtQuote.none"),
      ResourceMgr.getString("TxtQuote.escape"),
      ResourceMgr.getString("TxtQuote.duplicate")
    };
    ComboBoxModel model = new DefaultComboBoxModel(items);
    setModel(model);
  }

  public void setEscapeType(QuoteEscapeType type)
  {
    switch (type)
    {
      case none:
        setSelectedIndex(0);
        break;
      case escape:
        setSelectedIndex(1);
        break;
      case duplicate:
        setSelectedIndex(2);
        break;
    }
  }

  public QuoteEscapeType getEscapeType()
  {
    int index = getSelectedIndex();
    if (index == 1)
    {
      return QuoteEscapeType.escape;
    }
    if (index == 2)
    {
      return QuoteEscapeType.duplicate;
    }
    return QuoteEscapeType.none;
  }

}
