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

import java.awt.BorderLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.Optional;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import workbench.interfaces.ToolWindow;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.GuiSettings;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.WbConnection;

import workbench.gui.MainWindow;
import workbench.gui.WbSwingUtilities;
import workbench.gui.dbobjects.TableDataPanel;

import workbench.util.NumberStringCache;
import workbench.util.StringUtil;


/**
 *
 * @author Thomas Kellerer
 */
public class DetachedResultWindow
  extends JPanel
  implements WindowListener, ToolWindow
{
  private static int instanceCount;
  private final TableDataPanel data;
  private final int id;
  private JFrame window;
  private final SqlPanel sourcePanel;
  private final JButton attachButton;
  private JButton closeButton;

  public DetachedResultWindow(DwPanel result, SqlPanel source)
  {
    super(new BorderLayout(0,0));
    id = ++instanceCount;

    this.sourcePanel = source;

    data = new TableDataPanel();
    data.showRefreshButton(true);
    attachButton = new JButton(ResourceMgr.getString("LblAttachResult"));
    attachButton.addActionListener((ActionEvent e) -> {reAttach();});

    if (GuiSettings.showCloseButtonForDetachedResults())
    {
      closeButton = new JButton(ResourceMgr.getString("LblClose"));
      attachButton.addActionListener((ActionEvent e) -> {close();});
    }
    data.addButtons(attachButton, closeButton);

    add(data, BorderLayout.CENTER);
    data.displayData(result.getDataStore(), result.getLastExecutionTime());

    String title = result.getDataStore().getResultName();
    if (StringUtil.isBlank(title))
    {
      title = ResourceMgr.getString("LblTabResult") + " " + NumberStringCache.getNumberString(id);
    }

    WbConnection conn = result.getDataStore().getOriginalConnection();
    if (conn != null)
    {
      title += " (" + conn.getCurrentUser() + "@" + conn.getUrl() + ")";
    }

    this.window = new JFrame(title);
    this.window.getContentPane().setLayout(new BorderLayout());
    this.window.getContentPane().add(this, BorderLayout.CENTER);

    ResourceMgr.setWindowIcons(window, "data");

    Window parent = SwingUtilities.getWindowAncestor(source);
    int width = (int)(parent.getWidth() * 0.7);
    int height = (int)(parent.getHeight() * 0.7);

    if (!Settings.getInstance().restoreWindowSize(window, getClass().getName()))
    {
      this.window.setSize(width, height);
    }

    WbSwingUtilities.center(this.window, parent);

    window.addWindowListener(this);
    sourcePanel.registerToolWindow(this);
  }

  private void close()
  {
    sourcePanel.unregisterToolWindow(this);
    doClose();
  }

  private void reAttach()
  {
    final CallerInfo ci = new CallerInfo(){};
    try
    {
      WbSwingUtilities.invoke(() ->
      {
        try
        {
          MainWindow main = WbSwingUtilities.getMainWindow(sourcePanel);
          int index = main.getIndexForPanel(Optional.of(sourcePanel));
          if (index > -1)
          {
            main.selectTab(index);
          }
        }
        catch (Exception ex)
        {
          LogMgr.logError(ci, "Could not re-attach result", ex);
        }
      });
      sourcePanel.showData(this.data.getData().getDataStore());
      sourcePanel.unregisterToolWindow(this);
      window.removeWindowListener(this);
      doClose();
    }
    catch (Exception ex)
    {
      LogMgr.logError(ci, "Could not re-attach result", ex);
    }
  }

  public void refreshAutomatically(int interval)
  {
    data.refreshAutomatically(interval);
  }

  public void showWindow()
  {
    if (this.window != null)
    {
      this.window.setVisible(true);
    }
  }

  protected void saveSettings()
  {
    Settings.getInstance().storeWindowSize(this.window, getClass().getName());
  }

  private void doClose()
  {
    saveSettings();
    window.setVisible(false);
    window.dispose();
    window = null;
  }

  @Override
  public void windowOpened(WindowEvent e)
  {
  }

  @Override
  public void windowClosing(WindowEvent e)
  {
    sourcePanel.unregisterToolWindow(this);
    doClose();
  }


  @Override
  public void windowClosed(WindowEvent e)
  {
    if (data != null)
    {
      data.dispose();
    }
  }

  @Override
  public void windowIconified(WindowEvent e)
  {

  }

  @Override
  public void windowDeiconified(WindowEvent e)
  {

  }

  @Override
  public void windowActivated(WindowEvent e)
  {

  }

  @Override
  public void windowDeactivated(WindowEvent e)
  {
  }

  @Override
  public void closeWindow()
  {
    doClose();
  }

  @Override
  public void activate()
  {
    if (window != null)
    {
      window.requestFocus();
    }
  }

  @Override
  public JFrame getWindow()
  {
    return window;
  }

  @Override
  public void disconnect()
  {
    if (this.data != null)
    {
      this.data.detachConnection();
    }
    if (window != null)
    {
      String title = window.getTitle();
      int pos = title.indexOf(" (");
      if (pos > -1)
      {
        title = title.substring(0, pos);
        window.setTitle(title);
      }
    }
  }

  @Override
  public WbConnection getConnection()
  {
    // as the connection original comes from the SqlPanel
    // we do not need to return it here
    // (this is only used by WbManager to properly close all connections during shutdown)
    return null;
  }

}
