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
import java.awt.Component;
import java.awt.Dialog;
import java.awt.EventQueue;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.border.Border;

import workbench.WbManager;
import workbench.interfaces.ValidatingComponent;
import workbench.resource.IconMgr;
import workbench.resource.ResourceMgr;

import workbench.gui.WbSwingUtilities;
import workbench.gui.actions.EscAction;
import workbench.gui.actions.WbAction;
import workbench.gui.editor.JEditTextArea;

/**
 * @author  Thomas Kellerer
 */
public class ValidatingDialog
  extends JDialog
  implements WindowListener, ActionListener, PropertyChangeListener
{
  public static final String PROPERTY_VALID_STATE = "editorValid";
  protected ValidatingComponent validator;
  protected JComponent editorComponent;
  private JButton[] optionButtons;

  private JButton cancelButton;
  private boolean isCancelled = true;
  private int selectedOption = -1;
  private int cancelOption = -1;
  private EscAction esc;
  private JPanel buttonPanel;

  public ValidatingDialog(Dialog owner, String title, JComponent editor)
  {
    super(owner, title, true);
    init(editor, new String[] { ResourceMgr.getString("LblOK") }, true);
  }

  public ValidatingDialog(Frame owner, String title, JComponent editor)
  {
    this(owner, title, editor, true);
  }

  public ValidatingDialog(Frame owner, String title, JComponent editor, boolean addCancelButton)
  {
    super(owner, title, true);
    init(editor, new String[] { ResourceMgr.getString("LblOK") }, addCancelButton);
  }

  public ValidatingDialog(Frame owner, String title, JComponent editor, String[] options, boolean modal)
  {
    super(owner, title, modal);
    init(editor, options, false);
  }

  public ValidatingDialog(Dialog owner, String title, JComponent editor, boolean addCancelButton)
  {
    super(owner, title, true);
    init(editor, new String[] { ResourceMgr.getString("LblOK") }, addCancelButton);
  }

  public ValidatingDialog(Dialog owner, String title, JComponent editor, String[] options)
  {
    this(owner, title, editor, options, true);
  }

  public ValidatingDialog(Dialog owner, String title, JComponent editor, String[] options, boolean addCancelButton)
  {
    super(owner, title, true);
    init(editor, options, addCancelButton);
  }

  public void setCancelOption(int index)
  {
    this.cancelOption = index;
  }

  public void setDefaultButton(int index)
  {
    JRootPane root = this.getRootPane();
    if (index >= optionButtons.length && cancelButton != null)
    {
      root.setDefaultButton(cancelButton);
    }
    else
    {
      root.setDefaultButton(optionButtons[index]);
    }
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt)
  {
    if (evt.getSource() == this.editorComponent && PROPERTY_VALID_STATE.equals(evt.getPropertyName()))
    {
      Boolean valid = (Boolean)evt.getNewValue();
      if (valid != null)
      {
        setButtonsEnabled(valid);
      }
    }
  }

  public void setButtonsEnabled(boolean flag)
  {
    if (this.validator == null) return;
    for (int i=0; i < optionButtons.length; i++)
    {
      if (cancelOption > -1 && i == cancelOption) continue;
      optionButtons[i].setEnabled(flag);
    }
  }

  private void init(JComponent editor, String[] options, boolean addCancelButton)
  {
    if (editor instanceof ValidatingComponent)
    {
      this.validator = (ValidatingComponent)editor;
    }
    this.editorComponent = editor;
    this.editorComponent.addPropertyChangeListener(PROPERTY_VALID_STATE, this);
    this.optionButtons = new JButton[options.length];
    for (int i = 0; i < options.length; i++)
    {
      this.optionButtons[i] = new WbButton(options[i]);
      String label = optionButtons[i].getText();
      if (label.equals("OK"))
      {
        optionButtons[i].setName("ok");
      }
      this.optionButtons[i].addActionListener(this);
    }

    if (addCancelButton)
    {
      this.cancelButton = new WbButton(ResourceMgr.getString("LblCancel"));
      this.cancelButton.setName("cancel");
      this.cancelButton.addActionListener(this);
    }

    JRootPane root = this.getRootPane();
    root.setDefaultButton(optionButtons[0]);

    esc = new EscAction(this, this);
    if (editor instanceof JEditTextArea)
    {
      JEditTextArea edit = (JEditTextArea)editor;
      edit.addKeyBinding(esc);
    }

    JPanel content = new JPanel();
    content.setLayout(new BorderLayout());
    int gap = (int)(IconMgr.getInstance().getSizeForLabel() * 0.70);
    Border b = BorderFactory.createEmptyBorder(gap,gap,gap,gap);
    content.setBorder(b);
    content.add(editor, BorderLayout.CENTER);
    buttonPanel = new JPanel();
    buttonPanel.setLayout(new FlowLayout(FlowLayout.RIGHT, 0, 0));

    int length = optionButtons.length + (cancelButton == null ? 0 : 1);
    JComponent[] allButtons = new JComponent[length];
    for (int i=0; i < optionButtons.length; i++)
    {
      if (i > 0)
      {
        buttonPanel.add(Box.createHorizontalStrut(gap));
      }
      buttonPanel.add(optionButtons[i]);
      allButtons[i] = optionButtons[i];
    }

    if (cancelButton != null)
    {
      buttonPanel.add(Box.createHorizontalStrut(gap));
      buttonPanel.add(cancelButton);
      allButtons[allButtons.length - 1] = cancelButton;
    }

    b = BorderFactory.createEmptyBorder(gap, 0, 0, 0);

    WbSwingUtilities.makeEqualWidth(allButtons);
    buttonPanel.setBorder(b);
    content.add(buttonPanel, BorderLayout.SOUTH);
    this.getContentPane().add(content);
    this.doLayout();
    this.pack();
    this.addWindowListener(this);
  }

  public void setButtonEnabled(int index, boolean flag)
  {
    if (index >= 0 && index < optionButtons.length)
    {
      optionButtons[index].setEnabled(flag);
    }
  }

  public WbAction getESCAction()
  {
    return esc;
  }

  public int getSelectedOption()
  {
    return this.selectedOption;
  }

  public boolean isCancelled()
  {
    return this.isCancelled;
  }

  public static boolean showConfirmDialog(Window parent, JComponent editor, String title)
  {
    return showConfirmDialog(parent, editor, title, null, 0, false);
  }

  public static boolean showOKCancelDialog(Dialog parent, JComponent editor, String title)
  {
    ValidatingDialog dialog = new ValidatingDialog(parent, title, editor, true);
    dialog.pack();
    WbSwingUtilities.center(dialog, parent);
    dialog.setDefaultButton(0);
    dialog.setVisible(true);
    return !dialog.isCancelled();
  }

  public static boolean showConfirmDialog(Window parent, JComponent editor, String title, int defaultButton)
  {
    return showConfirmDialog(parent, editor, title, null, defaultButton, false);
  }

  public static ValidatingDialog createDialog(Window parent, JComponent editor, String title, Component reference, int defaultButton, boolean centeredButtons)
  {
    ValidatingDialog dialog = null;
    if (parent == null)
    {
      dialog = new ValidatingDialog(WbManager.getInstance().getCurrentWindow(), title, editor);
    }
    else
    {
      if (parent instanceof Frame)
      {
        dialog = new ValidatingDialog((Frame) parent, title, editor);
      }
      else if (parent instanceof Dialog)
      {
        dialog = new ValidatingDialog((Dialog) parent, title, editor);
      }
      else
      {
        throw new IllegalArgumentException("Parent component must be Dialog or Frame");
      }
    }
    dialog.pack();
    if (reference != null)
    {
      WbSwingUtilities.center(dialog, reference);
    }
    else
    {
      WbSwingUtilities.center(dialog, parent);
    }

    dialog.setDefaultButton(defaultButton);
    if (centeredButtons)
    {
      dialog.buttonPanel.setLayout(new FlowLayout(FlowLayout.CENTER));
    }
    return dialog;
  }

  public static boolean showConfirmDialog(Window parent, JComponent editor, String title, Component reference, int defaultButton, boolean centeredButtons)
  {
    ValidatingDialog dialog = createDialog(parent, editor, title, reference, defaultButton, centeredButtons);
    dialog.setVisible(true);

    return !dialog.isCancelled();
  }

  public void close()
  {
    if (validator != null)
    {
      validator.componentWillBeClosed();
    }
    this.setVisible(false);
    this.dispose();
  }

  @Override
  public void windowActivated(WindowEvent e)
  {
    if (validator == null) editorComponent.requestFocusInWindow();
  }

  @Override
  public void windowClosed(WindowEvent e)
  {
  }

  @Override
  public void windowClosing(WindowEvent e)
  {
    this.close();
  }

  @Override
  public void windowDeactivated(WindowEvent e)
  {
  }

  @Override
  public void windowDeiconified(WindowEvent e)
  {
  }

  @Override
  public void windowIconified(WindowEvent e)
  {
  }

  @Override
  public void windowOpened(WindowEvent e)
  {
    EventQueue.invokeLater(() ->
    {
      if (validator != null)
      {
        validator.componentDisplayed();
      }
      else
      {
        editorComponent.requestFocus();
      }
    });
  }

  public void approveAndClose()
  {
    this.selectedOption = 0;
    this.isCancelled = false;
    this.close();
  }

  private boolean isCancelButton(Object source)
  {
    if (this.cancelOption > -1)
    {
      return this.optionButtons[cancelOption] == source;
    }
    return source == null || source == this.esc || source == cancelButton;
  }

  @Override
  public void actionPerformed(ActionEvent e)
  {
    if (isCancelButton(e.getSource()))
    {
      this.selectedOption = -1;
      this.isCancelled = true;
      this.close();
    }
    else
    {
      for (int i = 0; i < optionButtons.length; i++)
      {
        if (e.getSource() == optionButtons[i])
        {
          this.selectedOption = i;
          break;
        }
      }
      if (validator == null || this.validator.validateInput())
      {
        this.isCancelled = false;
        this.close();
      }
    }
  }

}
