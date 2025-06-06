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

import java.awt.Component;

import javax.swing.JSplitPane;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.SplitPaneUI;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

import workbench.resource.IconMgr;

import workbench.gui.WbSwingUtilities;
import workbench.gui.lnf.LnFHelper;
import workbench.gui.sql.SplitPaneExpander;

/**
 * A JSplitPane which restores the divider size after a UI Change and an updateUI().
 *
 * @author  Thomas Kellerer
 */
public class WbSplitPane
  extends JSplitPane
{
  private SplitPaneExpander expander;

  public WbSplitPane()
  {
    super();
    this.initDefaults();
  }

  public WbSplitPane(int orientation)
  {
    super(orientation);
    this.initDefaults();
  }

  public WbSplitPane(int newOrientation, boolean newContinuousLayout)
  {
    super(newOrientation, newContinuousLayout);
    this.initDefaults();
  }

  public WbSplitPane(int newOrientation, boolean newContinuousLayout, Component newLeftComponent, Component newRightComponent)
  {
    super(newOrientation, newContinuousLayout, newLeftComponent, newRightComponent);
    this.initDefaults();
  }

  public WbSplitPane(int newOrientation, Component newLeftComponent, Component newRightComponent)
  {
    super(newOrientation, newLeftComponent, newRightComponent);
    this.initDefaults();
  }

  @Override
  public void updateUI()
  {
    int divider = this.getDividerSize();
    this.setDividerSize(divider);
    super.setUI(getSplitPaneUI());
    revalidate();
  }

  private SplitPaneUI getSplitPaneUI()
  {
    if (!LnFHelper.isWindowsLookAndFeel())
    {
      SplitPaneUI sui = createInstance((String)UIManager.getDefaults().get("SplitPaneUI"));
      if (sui != null) return sui;
    }
    return new WbSplitPaneUI();
  }

  private SplitPaneUI createInstance(String className)
  {
    try
    {
      Class clz = Class.forName(className);
      return (SplitPaneUI)clz.getDeclaredConstructor().newInstance();
    }
    catch (Throwable th)
    {
    }
    return null;
  }

  @Override
  public void setUI(ComponentUI newUI)
  {
    int divider = this.getDividerSize();
    super.setUI(newUI);
    this.setDividerSize(divider);
  }

  public void setOneTouchTooltip(String tip)
  {
    SplitPaneUI currentUI = getUI();
    if (currentUI instanceof WbSplitPaneUI)
    {
      ((WbSplitPaneUI)currentUI).setOneTouchTooltip(tip);
    }
  }

  private void initDefaults()
  {
    int divSize;
    int iconSize = IconMgr.getInstance().getSizeForLabel();
    switch (iconSize)
    {
      case 24:
        divSize = 12;
        break;
      case 32:
        divSize = 14;
        break;
      default:
        divSize = 10;
    }
    this.setDividerSize(divSize);
    this.setBorder(WbSwingUtilities.EMPTY_BORDER);
    this.setContinuousLayout(true);
    expander = new SplitPaneExpander(this);
  }

  public SplitPaneExpander getExpander()
  {
    return expander;
  }

  public void setDividerBorder(Border newBorder)
  {
    int divider = this.getDividerSize();
    if (LnFHelper.isWindowsLookAndFeel())
    {
      try
      {
        BasicSplitPaneUI currentUI = (BasicSplitPaneUI)this.getUI();
        BasicSplitPaneDivider div = currentUI.getDivider();
        div.setBorder(newBorder);
        this.setDividerSize(divider);
      }
      catch (Exception e)
      {
      }
    }
  }
}
