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
package workbench.gui.dialogs;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.File;
import java.sql.Blob;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.WindowConstants;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.gui.WbSwingUtilities;
import workbench.gui.actions.EscAction;

/**
 * A dialog to display a {@link ImagePanel}
 *
 * @author Thomas Kellerer
 */
public class ImageViewer
  extends JDialog
  implements ActionListener, WindowListener
{
  private ImagePanel panel;
  private JButton closeButton = new JButton(ResourceMgr.getString("LblClose"));
  private final String settingsId = "workbench.gui.imageviewer";

  public ImageViewer(Frame parent, String title)
  {
    super(parent, title, true);
    init();
    WbSwingUtilities.center(this, parent);
  }

  public ImageViewer(Dialog parent, String title)
  {
    super(parent, title, true);
    init();
    WbSwingUtilities.center(this, parent.getParent());
  }

  private void init()
  {
    this.getContentPane().setLayout(new BorderLayout());
    this.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    panel = new ImagePanel();
    this.getContentPane().add(panel, BorderLayout.CENTER);
    JPanel buttonPanel = new JPanel();
    buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 10));
    buttonPanel.add(closeButton);
    closeButton.addActionListener(this);
    this.getContentPane().add(buttonPanel, BorderLayout.SOUTH);
    this.addWindowListener(this);
    if (!Settings.getInstance().restoreWindowSize(this, settingsId))
    {
      setSize(640,480);
    }

    getRootPane().setDefaultButton(closeButton);
    new EscAction(this, this);
  }

  public void setData(Object data)
  {
    try
    {
      if (data instanceof Blob)
      {
        this.panel.setImage((Blob)data);
      }
      else if (data instanceof byte[])
      {
        this.panel.setImage((byte[])data);
      }
      else if (data instanceof File)
      {
        this.panel.setImage((File)data);
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error reading image", e);
    }
  }

  @Override
  public void actionPerformed(ActionEvent e)
  {
    this.setVisible(false);
    this.dispose();
  }

  @Override
  public void windowOpened(WindowEvent e)
  {
  }

  @Override
  public void windowClosing(WindowEvent e)
  {
  }

  @Override
  public void windowClosed(WindowEvent e)
  {
    Settings.getInstance().storeWindowSize(this, settingsId);
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

}
