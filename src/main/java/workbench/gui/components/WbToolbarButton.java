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

import java.awt.Insets;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JToolTip;

import workbench.resource.Settings;

import workbench.gui.WbSwingUtilities;
import workbench.gui.actions.WbAction;
import workbench.gui.lnf.LnFHelper;

/**
 *
 * @author Thomas Kellerer
 */
public class WbToolbarButton
  extends WbButton
  implements PropertyChangeListener
{
  public static final Insets SMALL_MARGIN = new Insets(1,1,1,1);
  public static final Insets WIDE_MARGIN = new Insets(5,5,5,5);

  private static final boolean USE_WIDE_MARGIN = LnFHelper.isFlatLaf();

  public WbToolbarButton()
  {
    super();
    init();
  }

  public WbToolbarButton(String aText)
  {
    super(aText);
    init();
  }

  public WbToolbarButton(Action a)
  {
    super(a);
    this.setText(null);
    init();
  }

  public WbToolbarButton(Icon icon)
  {
    super(icon);
    this.setText(null);
    init();
  }

  public void setAction(WbAction a)
  {
    super.setAction(a);
    // only remove the text if we do not have an icon
    if (a.getIconKey() != null || a.hasCustomIcon())
    {
      this.setText(null);
    }
    init();
  }

  private void removeListener()
  {
    Settings.getInstance().removePropertyChangeListener(this);
  }

  private void init()
  {
    this.setMargin(USE_WIDE_MARGIN ? WIDE_MARGIN : SMALL_MARGIN);
    this.setFocusable(false);
    if (getAction() instanceof WbAction)
    {
      Settings.getInstance().addPropertyChangeListener(this, ((WbAction)getAction()).getCustomIconProperty());
    }
    else
    {
      removeListener();
    }
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt)
  {
    if (getAction() instanceof WbAction)
    {
      WbAction wb = (WbAction)getAction();
      configurePropertiesFromAction(wb);
      if (wb.getIconKey() != null || wb.hasCustomIcon())
      {
        this.setText(null);
      }
      invalidate();
      WbSwingUtilities.repaintLater(getParent());
    }
  }

  @Override
  public void removeNotify()
  {
    super.removeNotify();
    removeListener();
  }

  @Override
  public JToolTip createToolTip()
  {
    JToolTip tip = new MultiLineToolTip();
    tip.setComponent(this);
    return tip;
  }

}
