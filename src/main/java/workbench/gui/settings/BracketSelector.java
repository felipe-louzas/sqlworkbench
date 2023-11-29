/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer.
 *
 * Licensed under a modified Apache License, Version 2.0 (the "License")
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
package workbench.gui.settings;

import java.awt.BorderLayout;
import java.util.List;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import workbench.resource.ResourceMgr;

import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class BracketSelector
  extends JPanel
{
  private final JComboBox<String> selector;
  private final JLabel label;

  public BracketSelector()
  {
    super(new BorderLayout(10, 0));
    List<String> items = List.of(ResourceMgr.getString("TxtNothingItem"), "( )", "{ }", "[ ]", "< >");

    this.selector = new JComboBox<>(items.toArray(String[]::new));

    this.label = new JLabel("Enclose with");
    this.add(label, BorderLayout.LINE_START);
    this.add(selector, BorderLayout.CENTER);
  }

  public JComboBox getSelector()
  {
    return selector;
  }

  public void setEnable(boolean flag)
  {
    selector.setEnabled(flag);
  }

  public void setLabelKey(String labelKey)
  {
    this.label.setText(ResourceMgr.getString(labelKey));
    this.label.setToolTipText(ResourceMgr.getDescription(labelKey));
    this.selector.setToolTipText(ResourceMgr.getDescription(labelKey));
  }

  public void setSelectedItem(String item)
  {
    if (StringUtil.isBlank(item))
    {
      selector.setSelectedIndex(0);
    }
    else
    {
      item = item.trim();
      int count = selector.getItemCount();
      for (int i = 1; i < count; i++)
      {
        String element = getValueAt(i);
        if (element.equals(item))
        {
          selector.setSelectedIndex(i);
          break;
        }
      }

    }
  }

  private String getValueAt(int index)
  {
    if (index <= 0) return null;
    String item = selector.getItemAt(index);
    return item.replace(" ", "");
  }

  public String getSelectedBrackets()
  {
    int index = selector.getSelectedIndex();
    return getValueAt(index);
  }

}
