/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2021, Thomas Kellerer
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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingConstants;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.LineBorder;

import workbench.resource.IconMgr;
import workbench.resource.ResourceMgr;

import workbench.gui.WbSwingUtilities;
import workbench.gui.renderer.ColorUtils;

import workbench.util.StringUtil;
import workbench.util.WbThread;

/**
 *
 * @author Thomas Kellerer
 */
public class FeedbackWindow
  extends JDialog
  implements ActionListener
{
  private JLabel connectLabel;
  private ActionListener cancelAction;
  private JButton cancelButton;

  public FeedbackWindow(Frame owner, String msg)
  {
    super(owner, false);
    initComponents(msg, null, null);
  }

  public FeedbackWindow(Frame owner, String message, ActionListener action, String buttonTextKey)
  {
    this(owner, message, action, buttonTextKey, false);
  }
  
  public FeedbackWindow(Frame owner, String message, ActionListener action, String buttonTextKey, boolean modal)
  {
    super(owner, modal);
    initComponents(message, action, buttonTextKey);
  }

  public FeedbackWindow(Dialog owner, String message)
  {
    super(owner, true);
    initComponents(message, null, null);
  }

  public FeedbackWindow(Dialog owner, String message, ActionListener action, String buttonTextKey)
  {
    super(owner, true);
    initComponents(message, action, buttonTextKey);
  }

  private void initComponents(String msg, ActionListener action, String buttonTextKey)
  {
    cancelAction = action;
    JPanel p = new JPanel();
    Color background = p.getBackground();
    Color borderColor;
    if (ColorUtils.isDark(background))
    {
      borderColor = background.brighter();
    }
    else
    {
      borderColor = background.darker();
    }

    int hgap = (int)(IconMgr.getInstance().getToolbarIconSize() * 1.25);
    int vgap = (int)(IconMgr.getInstance().getToolbarIconSize());
    p.setBorder(new CompoundBorder(new LineBorder(borderColor, 1), new EmptyBorder(vgap, hgap, vgap, hgap)));
    p.setLayout(new BorderLayout(0, 0));

    connectLabel = new JLabel(msg);
    FontMetrics fm = connectLabel.getFontMetrics(connectLabel.getFont());

    int width = fm.stringWidth(msg);
    int height = (int)(fm.getHeight());
    Dimension labelSize = new Dimension((int)(width * 1.5), (int)(height * 1.2));
    connectLabel.setMinimumSize(labelSize);
    connectLabel.setHorizontalAlignment(SwingConstants.CENTER);
    p.add(connectLabel, BorderLayout.CENTER);

    if (cancelAction != null && buttonTextKey != null)
    {
      cancelButton = new JButton(ResourceMgr.getString(buttonTextKey));
      cancelButton.addActionListener(this);
      JPanel p2 = new JPanel();
      p2.setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
      p2.setBorder(new EmptyBorder((int)(vgap * 1.25), hgap, vgap / 4, hgap));
      p2.add(cancelButton);
      p.add(p2, BorderLayout.SOUTH);
    }

    setUndecorated(true);
    getRootPane().setWindowDecorationStyle(JRootPane.NONE);
    setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    getContentPane().setLayout(new BorderLayout());
    getContentPane().add(p, BorderLayout.CENTER);
    pack();
  }

  public void showAndStart(final Runnable task)
  {
    EventQueue.invokeLater(() ->
    {
      WbThread t = new WbThread(task, "FeedbackWindow");
      t.start();
      setVisible(true);
    });
  }

  public String getMessage()
  {
    return connectLabel.getText();
  }

  public void setMessage(String msg)
  {
    if (StringUtil.isBlank(msg))
    {
      connectLabel.setText("");
    }
    else
    {
      connectLabel.setText(msg);
    }
    pack();
  }

  public void forceRepaint()
  {
    WbSwingUtilities.invoke(() ->
    {
      doLayout();
      invalidate();
      validate();
      repaint();
    });
  }

  @Override
  public void actionPerformed(ActionEvent evt)
  {
    if (cancelAction != null)
    {
      evt.setSource(this);
      cancelAction.actionPerformed(evt);
    }
  }

  @Override
  public void dispose()
  {
    if (this.cancelButton != null && this.cancelAction != null)
    {
      cancelButton.removeActionListener(cancelAction);
    }
    super.dispose();
  }

}
