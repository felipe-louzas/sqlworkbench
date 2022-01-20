/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2022, Thomas Kellerer
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

import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import javax.swing.plaf.TabbedPaneUI;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

/**
 *
 * @author  Thomas Kellerer
 */
public class TabbedPaneUIFactory
{
  public static String getTabbedPaneUIClass()
  {
    if (!Settings.getInstance().getBoolProperty("workbench.gui.replacetabbedpane", true))
    {
      return null;
    }

    LookAndFeel lnf = UIManager.getLookAndFeel();
    String lnfClass = lnf.getClass().getName();

    if (lnfClass.startsWith("com.sun.java.swing.plaf.windows.Windows"))
    {
      return "workbench.gui.components.BorderLessWindowsTabbedPaneUI";
    }
    else if (lnfClass.contains("WebLookAndFeel"))
    {
      return "com.alee.laf.tabbedpane.WebTabbedPaneUI";
    }
    return null;
  }

  public static TabbedPaneUI getBorderLessUI()
  {
    String uiClass = getTabbedPaneUIClass();
    if (uiClass == null) return null;
    return getClassInstance(uiClass);
  }

  private static TabbedPaneUI getClassInstance(String className)
  {
    TabbedPaneUI ui = null;
    try
    {
      Class cls = Class.forName(className);
      ui = (TabbedPaneUI)cls.getDeclaredConstructor().newInstance();
    }
    catch (Throwable th)
    {
      LogMgr.logError(new CallerInfo(){}, "Cannot create custom TabbedPaneUI: " + className, th);
    }
    return ui;
  }
}
