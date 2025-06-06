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
package workbench.gui.sql;

import java.awt.Container;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JTabbedPane;

import workbench.interfaces.TextChangeListener;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.IconMgr;
import workbench.resource.Settings;

import workbench.gui.WbSwingUtilities;

/**
 *
 * @author Thomas Kellerer
 */
public class IconHandler
  implements PropertyChangeListener, TextChangeListener
{
  public static final String PROP_LOADING_IMAGE = "workbench.gui.busyicon.name";
  public static final String PROP_CANCEL_IMAGE = "workbench.gui.cancelicon.name";
  public static final String DEFAULT_BUSY_IMAGE = "loading-static";
  public static final String DEFAULT_CANCEL_IMAGE = "cancelling-static";

  private final SqlPanel client;

  private ImageIcon fileIcon;
  private ImageIcon fileModifiedIcon;
  private ImageIcon cancelIcon;
  private ImageIcon loadingIcon;
  private boolean textModified;

  public IconHandler(SqlPanel panel)
  {
    client = panel;
    client.getEditor().addTextChangeListener(this);
    Settings.getInstance().addPropertyChangeListener(this, PROP_LOADING_IMAGE, PROP_CANCEL_IMAGE);
  }

  protected void dispose()
  {
    Settings.getInstance().removePropertyChangeListener(this);
    flush();
  }

  protected void flushBusyIcons()
  {
    if (cancelIcon != null) cancelIcon.getImage().flush();
    if (loadingIcon != null) loadingIcon.getImage().flush();
  }

  protected void flush()
  {
    flushBusyIcons();
    if (!Settings.getInstance().getCacheIcons())
    {
      if (fileIcon != null) fileIcon.getImage().flush();
      if (fileModifiedIcon != null) fileModifiedIcon.getImage().flush();
    }
  }

  public void removeIcon()
  {
    if (client.isBusy()) return;
    showIconForTab(null);
    flush();
  }

  public void showFileIcon()
  {
    if (client.isBusy()) return;
    showIconForTab(getFileIcon());
  }

  public void showCancelIcon()
  {
    showIconForTab(this.getCancelIndicator());
  }

  private ImageIcon getLoadingIndicator()
  {
    if (this.loadingIcon == null)
    {
      String name = Settings.getInstance().getProperty(PROP_LOADING_IMAGE, DEFAULT_BUSY_IMAGE);
      this.loadingIcon = IconMgr.getInstance().getLoadingImage(name);
      if (loadingIcon == null)
      {
        this.loadingIcon = IconMgr.getInstance().getLoadingImage(DEFAULT_BUSY_IMAGE);
      }
    }
    return this.loadingIcon;
  }

  protected ImageIcon getCancelIndicator()
  {
    if (this.cancelIcon == null)
    {
      String name = Settings.getInstance().getProperty(PROP_CANCEL_IMAGE, DEFAULT_CANCEL_IMAGE);
      cancelIcon = IconMgr.getInstance().getLoadingImage(name);
      if (cancelIcon == null)
      {
        cancelIcon = IconMgr.getInstance().getLoadingImage(DEFAULT_CANCEL_IMAGE);
      }
    }
    return this.cancelIcon;
  }

  protected ImageIcon getFileIcon()
  {
    ImageIcon icon = null;
    if (textModified)
    {
      if (this.fileModifiedIcon == null)
      {
        this.fileModifiedIcon = IconMgr.getInstance().getLabelIcon("file-modified");
      }
      icon = this.fileModifiedIcon;
    }
    else
    {
      if (this.fileIcon == null)
      {
        this.fileIcon = IconMgr.getInstance().getLabelIcon("file-icon");
      }
      icon = this.fileIcon;
    }

    return icon;
  }

  protected void showIconForTab(final ImageIcon icon)
  {
    Container parent = client.getParent();
    if (parent instanceof JTabbedPane)
    {
      final JTabbedPane tab = (JTabbedPane)parent;
      final int index = tab.indexOfComponent(client);
      if (index < 0) return;

      final Icon oldIcon = tab.getIconAt(index);
      if (icon == null && oldIcon == null) return;
      if (icon != oldIcon)
      {
        WbSwingUtilities.invoke(() ->
        {
          tab.setIconAt(index, icon);
        });
      }
    }
  }

  private void hideBusy()
  {
    _showBusyIcon(false);
  };

  private void showBusy()
  {
    _showBusyIcon(true);
  };

  public void showBusyIcon(boolean show)
  {
    if (show)
    {
      WbSwingUtilities.invoke(this::showBusy);
    }
    else
    {
      WbSwingUtilities.invoke(this::hideBusy);
    }
  }

  private void _showBusyIcon(boolean show)
  {
    Container parent = client.getParent();
    if (parent instanceof JTabbedPane)
    {
      final JTabbedPane tab = (JTabbedPane)parent;
      int index = tab.indexOfComponent(client);
      if (index >= 0 && index < tab.getTabCount())
      {
        try
        {
          if (show)
          {
            tab.setIconAt(index, getLoadingIndicator());
          }
          else
          {
            if (client.hasFileLoaded())
            {
              tab.setIconAt(index, getFileIcon());
            }
            else
            {
              tab.setIconAt(index, null);
            }
            // Under Linux, not flushing the icon seems to keep it running in the background
            // which causes a substantial CPU load
            flushBusyIcons();
          }
        }
        catch (Throwable th)
        {
          LogMgr.logWarning(new CallerInfo(){}, "Error when setting busy icon!", th);
        }
        tab.validate();
        tab.repaint();
      }
    }
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt)
  {
    String prop = evt.getPropertyName();
    if (prop == null) return;

    if (prop.equals(PROP_LOADING_IMAGE))
    {
      if (this.loadingIcon != null)
      {
        this.loadingIcon.getImage().flush();
        this.loadingIcon = null;
      }
    }
    if (prop.equals(PROP_CANCEL_IMAGE))
    {
      if (this.cancelIcon != null)
      {
        this.cancelIcon.getImage().flush();
        this.cancelIcon = null;
      }
    }
  }

  @Override
  public void textStatusChanged(boolean modified)
  {
    this.textModified = modified;
    // Make sure the icon for the file is updated to reflect
    // the modidified status
    if (client.hasFileLoaded()) showFileIcon();
  }

}
