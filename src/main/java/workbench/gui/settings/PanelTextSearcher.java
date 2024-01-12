/*
 * This file is part of SQL Workbench/J, http://www.sql-workbench.net
 *
 * Copyright 2002-2024 Thomas Kellerer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at.
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.net
 *
 */
package workbench.gui.settings;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import workbench.resource.GuiSettings;
import workbench.resource.Settings;

import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class PanelTextSearcher
{
  private static final String ORIGINAL_COLOR_KEY = "originalColor";
  private static final String OPAQUE_KEY = "wasOpaque";

  public boolean containsText(JPanel panel, String search)
  {
    if (StringUtil.isBlank(search)) return false;
    search = search.toLowerCase();
    List<JComponent> labels = getMatchingLabels(panel, search);
    return (labels.size() > 0);
  }

  public void clearHighlight(JPanel panel)
  {
    List<JComponent> controls = getLabels(panel);
    for (JComponent comp : controls)
    {
      Object prop = comp.getClientProperty(ORIGINAL_COLOR_KEY);
      if (prop instanceof Color)
      {
        comp.setBackground((Color)prop);
      }
      Object opaqueFlag = comp.getClientProperty(OPAQUE_KEY);
      if (opaqueFlag instanceof Boolean)
      {
        comp.setOpaque((Boolean)opaqueFlag);
      }
    }
  }

  public void highlighSearch(JPanel panel, String search)
  {
    List<JComponent> controls = getMatchingLabels(panel, search);
    for (JComponent comp : controls)
    {
      Color bkg = comp.getBackground();
      comp.putClientProperty(ORIGINAL_COLOR_KEY, bkg);
      Color hilite = Settings.getInstance().geSelectionHighlightColor();
      comp.setBackground(hilite);
      Boolean opaque = comp.isOpaque();
      comp.putClientProperty(OPAQUE_KEY, opaque);
      comp.setOpaque(true);
    }
  }

  private List<JComponent> getMatchingLabels(JPanel panel, String search)
  {
    ArrayList<JComponent> result = new ArrayList<>();
    List<JComponent> labels = getLabels(panel);

    for (JComponent label : labels)
    {
      if (containsText(label, search))
      {
        result.add(label);
      }
    }

    return result;
  }

  private boolean containsText(JComponent label, String search)
  {
    if (label == null) return false;
    String text = getText(label);
    if (isMatch(text, search)) return true;

    if (GuiSettings.includeTooltipsInSettingsSearch())
    {
      String tip =  StringUtil.trimToNull(label.getToolTipText());
      if (isMatch(tip, search)) return true;
    }
    return false;
  }

  private boolean isMatch(String label, String search)
  {
    if (StringUtil.isAnyBlank(label, search)) return false;
    if (GuiSettings.useRegexForSettingsSearch())
    {
      try
      {
        Pattern p = Pattern.compile(search, Pattern.CASE_INSENSITIVE + Pattern.UNICODE_CASE);
        Matcher m = p.matcher(label);
        return m.find();
      }
      catch (Exception ex)
      {
        // Ignore, fallback to simple "contains"
      }
    }
    return label.toLowerCase().contains(search.toLowerCase());
  }

  private String getText(JComponent comp)
  {
    if (comp instanceof JLabel)
    {
      return StringUtil.trimToNull(((JLabel)comp).getText());
    }
    if (comp instanceof JCheckBox)
    {
      return StringUtil.trimToNull(((JCheckBox)comp).getText());
    }
    return null;
  }

  private List<JComponent> getLabels(JPanel panel)
  {
    return getContainerLabels(panel);
  }

  private List<JComponent> getContainerLabels(Container component)
  {
    ArrayList<JComponent> result = new ArrayList<>();
    if (component == null) return result;

    Component[] children = component.getComponents();
    for (Component child : children)
    {
      if (child instanceof JLabel || child instanceof JCheckBox)
      {
        result.add((JComponent)child);
      }
      else if (child instanceof Container)
      {
        List<JComponent> labels = getContainerLabels((Container)child);
        result.addAll(labels);
      }
    }
    return result;
  }
}
