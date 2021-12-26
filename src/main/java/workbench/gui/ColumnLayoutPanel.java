/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2021 Thomas Kellerer.
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
package workbench.gui;

import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import workbench.gui.components.WbSplitPane;
import workbench.gui.components.WbTabbedPane;
import workbench.gui.dbobjects.objecttree.TreePosition;

import workbench.util.StringUtil;
import workbench.util.WbProperties;


/**
 * A panel that displays components in thre columns.
 *
 * The center column is always occupied by the main component.
 *
 * Any number of components can be added to the left or right
 * of the main component using a SplitPane to make them resizable.
 * If multiple components are shown on one side, they are displayed
 * using a TabbedPane.
 *
 * @author Thomas Kellerer
 */
public class ColumnLayoutPanel
  extends JPanel
{
  private static final String TITLE_PROP = "_wbLayoutTitle";
  private static final String INDEX_PROP = "_wbTargetIndex";
  private JComponent mainComponent;
  private final List<JComponent> leftComponents = new ArrayList<>();
  private final List<JComponent> rightComponents = new ArrayList<>();
  private WbSplitPane leftSplit;
  private WbSplitPane rightSplit;
  private WbTabbedPane leftTab;
  private WbTabbedPane rightTab;
  private int lastLeftDividerPosition = -1;
  private int lastRightDividerPosition = -1;

  public ColumnLayoutPanel(JComponent main)
  {
    super(new BorderLayout());
    this.mainComponent = main;
    this.add(mainComponent, BorderLayout.CENTER);
  }

  /**
   * Returns the number of additional components.
   * This excludes the main component.
   */
  public int getNumberOfComponents()
  {
    return leftComponents.size() + rightComponents.size();
  }

  public void showAdditionalComponents()
  {
    if (getNumberOfComponents() == 0) return;
    setupColumns();

    showLeftComponents();
    showRightComponents();
    restoreDividerLocation();

    refreshUI();
  }

  public void hideAdditionalComponents()
  {
    if (getNumberOfComponents() == 0) return;

    storeDividerLocations();
    if (leftTab != null)
    {
      leftTab.removeAll();
      leftTab = null;
    }
    if (rightTab != null)
    {
      rightTab.removeAll();
      rightTab = null;
    }
    if (leftSplit != null)
    {
      remove(leftSplit);
      leftSplit.removeAll();
      leftSplit = null;
    }
    if (rightSplit != null)
    {
      remove(rightSplit);
      rightSplit.removeAll();
      rightSplit = null;
    }
    add(mainComponent, BorderLayout.CENTER);
    refreshUI();
  }

  public void addComponentAt(TreePosition position, JComponent comp, String title, int index)
  {
    if (position == TreePosition.left)
    {
      addLeftComponent(comp, title, index);
    }
    else
    {
      addRightComponent(comp, title, index);
    }
  }
  public void addRightComponent(JComponent comp, String title, int index)
  {
    comp.putClientProperty(TITLE_PROP, title);
    comp.putClientProperty(INDEX_PROP, index);
    rightComponents.add(comp);
    storeDividerLocations();

    int width = mainComponent.getWidth();

    setupColumns();
    showRightComponents();
    if (rightComponents.size() == 1)
    {
      rightSplit.setDividerLocation((int)(width * 0.75));
    }
    restoreDividerLocation();
    refreshUI();
  }

  private void showRightComponents()
  {
    if (rightComponents.size() == 1)
    {
      rightSplit.setRightComponent(rightComponents.get(0));
    }
    else if (rightComponents.size() > 1)
    {
      if (rightTab == null)
      {
        rightTab = createTabPane();
        rightSplit.setRightComponent(rightTab);
      }
      insertTabs(rightTab, rightComponents);
    }
  }

  public void addLeftComponent(JComponent comp, String title, int index)
  {
    comp.putClientProperty(TITLE_PROP, title);
    comp.putClientProperty(INDEX_PROP, index);
    int width = mainComponent.getWidth();
    storeDividerLocations();

    leftComponents.add(comp);

    setupColumns();
    showLeftComponents();
    if (leftComponents.size() == 1)
    {
      leftSplit.setDividerLocation((int)(width * 0.25));
    }
    restoreDividerLocation();
    refreshUI();
  }

  private void refreshUI()
  {
    invalidate();
    EventQueue.invokeLater(this::validate);
  }

  private void showLeftComponents()
  {
    if (leftComponents.size() == 1)
    {
      leftSplit.setLeftComponent(leftComponents.get(0));
    }
    else if (leftComponents.size() > 1)
    {
      if (leftTab == null)
      {
        leftTab = createTabPane();
        leftSplit.setLeftComponent(leftTab);
      }
      insertTabs(leftTab, leftComponents);
    }
  }

  private void insertTabs(JTabbedPane tab, List<JComponent> toAdd)
  {
    List<JComponent> sorted = new ArrayList<>(toAdd);
    sorted.sort((JComponent o1, JComponent o2) ->
    {
      int i1 = getTargetIndex(o1);
      int i2 = getTargetIndex(o2);
      return i1 - i2;
    });
    for (JComponent comp : sorted)
    {
      tab.addTab((String)comp.getClientProperty(TITLE_PROP), comp);
    }
  }

  private int getTargetIndex(JComponent comp)
  {
    Integer index = (Integer)comp.getClientProperty(INDEX_PROP);
    if (index == null) return 0;
    return index.intValue();
  }

  private void setupColumns()
  {
    if (getNumberOfComponents() == 0)
    {
      if (rightSplit != null)
      {
        remove(rightSplit);
        rightSplit.removeAll();
      }
      if (leftSplit != null)
      {
        remove(leftSplit);
        leftSplit.removeAll();
      }
      rightTab = null;
      leftTab = null;
      add(mainComponent, BorderLayout.CENTER);
    }
    else if (leftComponents.size() > 0 && rightComponents.size() > 0)
    {
      if (leftSplit == null) leftSplit = createSplitPane();
      if (rightSplit == null) rightSplit = createSplitPane();
      leftSplit.setRightComponent(rightSplit);
      rightSplit.setLeftComponent(mainComponent);
      add(leftSplit, BorderLayout.CENTER);
    }
    else if (leftComponents.size() > 0 && rightComponents.size() == 0)
    {
      if (leftSplit == null) leftSplit = createSplitPane();
      if (rightSplit != null)
      {
        remove(rightSplit);
        rightSplit.removeAll();
        rightSplit = null;
      }
      leftSplit.setRightComponent(mainComponent);
      add(leftSplit, BorderLayout.CENTER);
    }
    else if (leftComponents.size() == 0 && rightComponents.size() > 0)
    {
      if (rightSplit == null)
      {
        rightSplit = createSplitPane();
      }
      if (leftSplit != null)
      {
        remove(leftSplit);
        leftSplit.removeAll();
        leftSplit = null;
      }
      rightSplit.setLeftComponent(mainComponent);
      add(rightSplit, BorderLayout.CENTER);
    }
  }

  private boolean isLeftComponent(JComponent comp)
  {
    for (JComponent c : leftComponents)
    {
      if (c == comp) return true;
    }
    return false;
  }

  private boolean isRightComponent(JComponent comp)
  {
    for (JComponent c : rightComponents)
    {
      if (c == comp) return true;
    }
    return false;
  }

  public JComponent findByName(String name)
  {
    if (StringUtil.isBlank(name)) return null;
    for (JComponent c : leftComponents)
    {
      if (name.equals(c.getName())) return c;
    }
    for (JComponent c : rightComponents)
    {
      if (name.equals(c.getName())) return c;
    }
    return null;
  }

  public void removeByName(String name)
  {
    JComponent toRemove = findByName(name);
    removeComponent(toRemove);
  }

  public void removeComponent(JComponent toRemove)
  {
    if (toRemove == null) return;
    storeDividerLocations();
    if (isLeftComponent(toRemove))
    {
      removeLeftComponent(toRemove);
    }
    if (isRightComponent(toRemove))
    {
      removeRightComponent(toRemove);
    }
    restoreDividerLocation();
    refreshUI();
  }

  private void removeRightComponent(JComponent toRemove)
  {
    int oldSize = rightComponents.size();
    if (oldSize == 0) return;

    if (rightTab != null)
    {
      int index = rightTab.indexOfComponent(toRemove);
      if (index > -1)
      {
        rightTab.removeTabAt(index);
      }
    }

    rightComponents.remove(toRemove);

    if (rightComponents.size() == 0)
    {
      remove(rightSplit);
      rightSplit.removeAll();
      rightSplit = null;
      if (leftSplit == null)
      {
        add(mainComponent, BorderLayout.CENTER);
      }
      else
      {
        leftSplit.setRightComponent(mainComponent);
      }
    }
    else if (rightComponents.size() == 1)
    {
      if (rightTab != null)
      {
        rightTab.removeAll();
        rightTab = null;
      }
      rightSplit.setRightComponent(leftComponents.get(0));
      if (leftSplit != null)
      {
        rightSplit.setLeftComponent(leftSplit);
      }
      else
      {
        rightSplit.setLeftComponent(mainComponent);
      }
    }
  }
  private void removeLeftComponent(JComponent toRemove)
  {
    int oldSize = leftComponents.size();
    if (oldSize == 0) return;

    if (leftTab != null)
    {
      int index = leftTab.indexOfComponent(toRemove);
      if (index > -1)
      {
        leftTab.removeTabAt(index);
      }
    }

    leftComponents.remove(toRemove);

    if (leftComponents.size() == 0)
    {
      remove(leftSplit);
      leftSplit.removeAll();
      leftSplit = null;
      if (rightSplit == null)
      {
        add(mainComponent, BorderLayout.CENTER);
      }
      else
      {
        rightSplit.setLeftComponent(mainComponent);
      }
    }
    else if (leftComponents.size() == 1)
    {
      if (leftTab != null)
      {
        leftTab.removeAll();
        leftTab = null;
      }
      leftSplit.setLeftComponent(leftComponents.get(0));
      if (rightSplit != null)
      {
        leftSplit.setRightComponent(rightSplit);
      }
      else
      {
        leftSplit.setRightComponent(mainComponent);
      }
    }
  }

  public <T extends Object> T getComponent(Class<T> what)
  {
    for (JComponent c : leftComponents)
    {
      if (what.isInstance(c))
      {
        return (T)c;
      }
    }
    for (JComponent c : rightComponents)
    {
      if (what.isInstance(c))
      {
        return (T)c;
      }
    }
    return null;
  }

  public void setLeftDividerLocation(int location)
  {
    if (leftSplit != null && location > -1)
    {
      leftSplit.setDividerLocation(location);
    }
  }

  public void setRightDividerLocation(int location)
  {
    if (rightSplit != null && location > -1)
    {
      rightSplit.setDividerLocation(location);
    }
  }

  public int getLeftDividerLocation()
  {
    if (leftSplit == null) return -1;
    return leftSplit.getDividerLocation();
  }

  public int getRightDividerLocation()
  {
    if (this.rightSplit == null) return -1;
    return rightSplit.getDividerLocation();
  }

  public void saveSettings(WbProperties props, String prefix)
  {
    int left = getLeftDividerLocation();
    if (left > -1)
    {
      props.setProperty(prefix + ".divider.left", -1);
    }
    int right = getRightDividerLocation();
    if (right > -1)
    {
      props.setProperty(prefix + ".divider.right", right);
    }
  }

  public void restoreSettings(WbProperties props, String prefix)
  {
    this.lastLeftDividerPosition = props.getIntProperty(prefix + ".divider.left", -1);
    this.lastRightDividerPosition = props.getIntProperty(prefix + ".divider.right", -1);
  }

  private WbSplitPane createSplitPane()
  {
    WbSplitPane split = new WbSplitPane();
    split.setDividerBorder(WbSwingUtilities.EMPTY_BORDER);
    split.setOneTouchExpandable(true);
    return split;
  }

  private WbTabbedPane createTabPane()
  {
    WbTabbedPane tab = new WbTabbedPane();
    return tab;
  }

  private void storeDividerLocations()
  {
    if (leftSplit != null)
    {
      this.lastLeftDividerPosition = leftSplit.getDividerLocation();
    }
    if (rightSplit != null)
    {
      this.lastRightDividerPosition = rightSplit.getDividerLocation();
    }
  }

  private void restoreDividerLocation()
  {
    setLeftDividerLocation(lastLeftDividerPosition);
    setRightDividerLocation(lastRightDividerPosition);
    lastRightDividerPosition = -1;
    lastLeftDividerPosition = -1;
  }
}
