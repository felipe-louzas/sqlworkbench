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
package workbench.gui.settings;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JTabbedPane;

import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

/**
 *
 * @author Thomas Kellerer
 */
public class PlacementChooser
  extends JComboBox
{
  public static final String DBEXPLORER_LOCATION_PROPERTY = "workbench.gui.dbobjects.tabletabs";
  public static final String MAINWIN_TAB_PLACEMENT_PROPERTY = "workbench.gui.mainwindow.tablocation";
  public static final String RESULT_TAB_PLACEMENT_PROPERTY = "workbench.gui.results.tablocation";

  private final String propertyName;

  public PlacementChooser()
  {
    this(DBEXPLORER_LOCATION_PROPERTY);
  }

  public PlacementChooser(String propName)
  {
    super();
    this.propertyName = propName;
    String[] locations = new String[] {
      ResourceMgr.getString("TxtTabTop"),
      ResourceMgr.getString("TxtTabBottom"),
      ResourceMgr.getString("TxtTabLeft"),
      ResourceMgr.getString("TxtTabRight"),
    };
    setModel(new DefaultComboBoxModel(locations));
  }

  public void showPlacement()
  {
    String placement = getPlacementSettingValue(this.propertyName);
    if ("top".equals(placement))
    {
      setSelectedIndex(0);
    }
    else if ("bottom".equals(placement))
    {
      setSelectedIndex(1);
    }
    if ("left".equals(placement))
    {
      setSelectedIndex(2);
    }
    if ("right".equals(placement))
    {
      setSelectedIndex(3);
    }
  }

  private static String getPlacementSettingValue(String property)
  {
    return Settings.getInstance().getProperty(property, "top");
  }

  public static int getDBExplorerTabLocation()
  {
    return getPlacementLocation(DBEXPLORER_LOCATION_PROPERTY);
  }

  public static int getMainWindowTabsLocation()
  {
    return getPlacementLocation(MAINWIN_TAB_PLACEMENT_PROPERTY);
  }

  public static int getResultTabLocation()
  {
    return getPlacementLocation(RESULT_TAB_PLACEMENT_PROPERTY);
  }

  public static int getPlacementLocation(String property)
  {
    String tabLocation = getPlacementSettingValue(property);
    int location = JTabbedPane.TOP;
    if (tabLocation.equalsIgnoreCase("top"))
    {
      location = JTabbedPane.TOP;
    }
    else if (tabLocation.equalsIgnoreCase("left"))
    {
      location = JTabbedPane.LEFT;
    }
    else if (tabLocation.equalsIgnoreCase("right"))
    {
      location = JTabbedPane.RIGHT;
    }
    else if (tabLocation.equalsIgnoreCase("bottom"))
    {
      location = JTabbedPane.BOTTOM;
    }
    return location;
  }

  public void saveSelection()
  {
    String placement = getPlacement();
    Settings.getInstance().setProperty(this.propertyName, placement);
  }

  private String getPlacement()
  {
    int placement = getSelectedIndex();
    switch (placement)
    {
      case 0:
        return "top";
      case 1:
        return "bottom";
      case 2:
        return "left";
      case 3:
        return "right";
    }
    return "top";
  }
}
