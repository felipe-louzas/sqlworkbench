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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.swing.AbstractListModel;
import javax.swing.JPanel;

import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class PageListModel
  extends AbstractListModel<OptionPanelPage>
{
  private final List<OptionPanelPage> pages = new ArrayList<>();
  private final List<OptionPanelPage> filteredPages = new ArrayList<>();
  private final PanelTextSearcher searcher = new PanelTextSearcher();

  public PageListModel(List<OptionPanelPage> pages)
  {
    this.pages.addAll(pages);
  }

  public int getIndexOf(String title)
  {
    if (StringUtil.isBlank(title)) return -1;
    for (int i = 0; i < pages.size(); i++)
    {
      OptionPanelPage page = pages.get(i);
      if (page.getLabel().equals(title)) return i;
    }
    return -1;
  }

  public void applyFilter(String search)
  {
    if (StringUtil.isBlank(search)) return;

    pages.addAll(filteredPages);
    filteredPages.clear();

    Iterator<OptionPanelPage> itr = pages.iterator();
    while (itr.hasNext())
    {
      OptionPanelPage page = itr.next();
      JPanel panel = page.getOptionsPanel();
      if (!searcher.containsText(panel, search) && !searcher.isMatch(page.getLabel(), search))
      {
        itr.remove();
        searcher.clearHighlight(panel);
        filteredPages.add(page);
      }
      else
      {
        searcher.highlighSearch(panel, search);
      }
    }
    pages.sort(null);
    fireContentsChanged(this, 0, pages.size());
  }

  public void resetFilter()
  {
    if (filteredPages.isEmpty()) return;
    pages.addAll(filteredPages);
    filteredPages.clear();
    pages.sort(null);
    for (OptionPanelPage page : pages)
    {
      searcher.clearHighlight(page.getOptionsPanel());
    }
    fireContentsChanged(this, 0, pages.size());
  }

  @Override
  public int getSize()
  {
    return pages.size();
  }

  @Override
  public OptionPanelPage getElementAt(int index)
  {
    return pages.get(index);
  }

  public List<OptionPanelPage> getAllPages()
  {
    List<OptionPanelPage> result = new ArrayList<>(pages.size());
    result.addAll(pages);
    if (filteredPages.size() > 0)
    {
      result.addAll(filteredPages);
      result.sort(null);
    }
    return result;
  }

}
