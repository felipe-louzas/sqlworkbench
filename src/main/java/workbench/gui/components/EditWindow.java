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

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowListener;
import java.io.StringReader;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.text.JTextComponent;

import workbench.WbManager;
import workbench.interfaces.Restoreable;
import workbench.interfaces.TextContainer;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.GuiSettings;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.gui.WbSwingUtilities;
import workbench.gui.actions.EscAction;
import workbench.gui.sql.EditorPanel;

/**
 *
 * @author Thomas Kellerer
 */
public class EditWindow
  extends JDialog
  implements ActionListener, WindowListener
{
  private TextContainer textContainer;
  private JComponent editor;
  private Restoreable componentSettings;
  private final JButton okButton = new WbButton(ResourceMgr.getString("LblOK"));
  private final JButton cancelButton = new WbButton(ResourceMgr.getString("LblCancel"));
  private final JButton copyButton = new WbButton(ResourceMgr.getString("MnuTxtCopy"));
  private boolean isCancelled = true;
  private String settingsId;

  public EditWindow(Frame owner, String title, String text)
  {
    this(owner, title, text, "workbench.data.edit.window");
  }

  public EditWindow(Frame owner, String title, String text, boolean createSqlEditor, boolean showCloseButtonOnly)
  {
    this(owner, title, text, "workbench.data.edit.window", createSqlEditor, true, showCloseButtonOnly);
  }

  public EditWindow(Frame owner, String title, String text, String id)
  {
    this(owner, title, text, id, false);
  }

  public EditWindow(Frame owner, String title, String text, String id, boolean createSqlEditor)
  {
    this(owner, title, text, id, createSqlEditor, true, false);
  }

  public EditWindow(Frame owner, String title, String text, String id, boolean createSqlEditor, boolean modal)
  {
    this(owner, title, text, id, createSqlEditor, modal, false);
  }

  public EditWindow(final Frame owner, final String title, final String text, final String id, final boolean createSqlEditor, final boolean modal, final boolean showCloseButtonOnly)
  {
    super(owner, title, modal);
    init(text, id, createSqlEditor, showCloseButtonOnly);
    WbSwingUtilities.center(this, owner);
  }

  public EditWindow(final Dialog owner, final String title, final String text, final String id, final boolean createSqlEditor)
  {
    super(owner, title, true);
    init(text, id, createSqlEditor, true);
    WbSwingUtilities.center(this, WbManager.getInstance().getCurrentWindow());
  }

  public EditWindow(final Dialog owner, final String title, final String text, final boolean createSqlEditor, final boolean showCloseButtonOnly)
  {
    super(owner, title, true);
    init(text, "workbench.data.edit.window", createSqlEditor, showCloseButtonOnly);
    WbSwingUtilities.center(this, WbManager.getInstance().getCurrentWindow());
  }

  public void setReadOnly()
  {
    this.textContainer.setEditable(false);
  }

  private void init(String text, String id, boolean createSqlEditor, boolean showCloseButtonOnly)
  {
    this.settingsId = id;
    setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    this.getContentPane().setLayout(new BorderLayout());
    if (createSqlEditor)
    {
      EditorPanel panel = EditorPanel.createSqlEditor();
      panel.showFindOnPopupMenu();
      panel.showFormatSql();
      this.editor = panel;
      this.textContainer = panel;
    }
    else
    {
      if (Settings.getInstance().getUsePlainEditorForData())
      {
        PlainEditor ed = new PlainEditor();
        this.componentSettings = ed;
        this.textContainer = ed;
        this.editor = ed;
      }
      else
      {
        EditorPanel panel = EditorPanel.createTextEditor();
        panel.showFindOnPopupMenu();
        this.editor = panel;
        this.textContainer = panel;
      }
    }

    this.getContentPane().add(editor, BorderLayout.CENTER);
    JPanel buttonPanel = new JPanel(new BorderLayout());

    JPanel closePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    JPanel copyPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));

    copyPanel.add(this.copyButton);
    if (!showCloseButtonOnly)
    {
      closePanel.add(this.okButton);
    }
    else
    {
      this.cancelButton.setText(ResourceMgr.getString("LblClose"));
    }
    closePanel.add(this.cancelButton);
    buttonPanel.add(copyPanel, BorderLayout.LINE_START);
    buttonPanel.add(closePanel, BorderLayout.CENTER);

    this.getContentPane().add(buttonPanel, BorderLayout.SOUTH);

    if (GuiSettings.getUseReaderForMultilineRenderer() && editor instanceof PlainEditor)
    {
      StringReader r = new StringReader(text);
      try
      {
        ((PlainEditor)editor).readText(r);
      }
      catch (Throwable th)
      {
        LogMgr.logWarning(new CallerInfo(){}, "Could not set value using StringReader", th);
        this.textContainer.setText(text);
      }
    }
    else
    {
      this.textContainer.setText(text);
    }

    this.textContainer.setCaretPosition(0);

    this.okButton.addActionListener(this);
    this.cancelButton.addActionListener(this);
    this.copyButton.addActionListener(this);

    WbTraversalPolicy pol = new WbTraversalPolicy();
    pol.setDefaultComponent(editor);
    pol.addComponent(editor);
    pol.addComponent(this.copyButton);
    pol.addComponent(this.okButton);
    pol.addComponent(this.cancelButton);
    this.setFocusTraversalPolicy(pol);
    this.setFocusCycleRoot(false);

    // creating the action will add it to the input map of the dialog
    // which will enable the key
    new EscAction(this, this);

    if (!Settings.getInstance().restoreWindowSize(this, settingsId))
    {
      this.setSize(500,400);
    }

    this.addWindowListener(this);
  }

  public void setInfoText(String text)
  {
    if (this.editor instanceof PlainEditor)
    {
      ((PlainEditor)editor).setInfoText(text);
    }
  }

  private void copyToClipboard()
  {
    String text = null;
    if (editor instanceof JTextComponent)
    {
      text = ((JTextComponent)editor).getText();
    }
    else if (editor instanceof TextContainer)
    {
      text = ((TextContainer)editor).getText();
    }
    if (text != null)
    {
      Clipboard clipboard = getToolkit().getSystemClipboard();
      clipboard.setContents(new StringSelection(text), null);
    }
  }

  public void hideCancelButton()
  {
    this.cancelButton.removeActionListener(this);
    this.cancelButton.setVisible(false);
  }

  @Override
  public void actionPerformed(ActionEvent e)
  {
    if (e.getSource() == this.copyButton)
    {
      copyToClipboard();
      return;
    }

    if (e.getSource() == this.okButton)
    {
      this.isCancelled = false;
    }
    else if (e.getSource() == this.cancelButton)
    {
      this.isCancelled = true;
    }
    closeWindow();
  }

  private void closeWindow()
  {
    setVisible(false);
    dispose();
  }
  public boolean isCancelled()
  {
    return this.isCancelled;
  }

  public String getText()
  {
    return this.textContainer.getText();
  }

  @Override
  public void windowActivated(java.awt.event.WindowEvent e)
  {
  }

  @Override
  public void windowClosed(java.awt.event.WindowEvent e)
  {
    Settings.getInstance().storeWindowSize(this, this.settingsId);
    if (componentSettings != null) componentSettings.saveSettings();
  }

  @Override
  public void windowClosing(java.awt.event.WindowEvent e)
  {
  }

  @Override
  public void windowDeactivated(java.awt.event.WindowEvent e)
  {
  }

  @Override
  public void windowDeiconified(java.awt.event.WindowEvent e)
  {
  }

  @Override
  public void windowIconified(java.awt.event.WindowEvent e)
  {
  }

  @Override
  public void windowOpened(java.awt.event.WindowEvent e)
  {
    validate();
    editor.validate();
    editor.requestFocusInWindow();
    WbSwingUtilities.repaintLater(this);
  }

}
