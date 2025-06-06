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

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.io.StringReader;
import java.util.Collections;
import java.util.EventObject;
import java.util.Set;

import javax.swing.AbstractCellEditor;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.text.JTextComponent;

import workbench.interfaces.NullableEditor;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.GuiSettings;

import workbench.gui.WbSwingUtilities;
import workbench.gui.actions.MultilineWrapAction;
import workbench.gui.actions.RestoreDataAction;
import workbench.gui.actions.SelectFkValueAction;
import workbench.gui.actions.SetNullAction;
import workbench.gui.actions.WbAction;
import workbench.gui.renderer.WbRenderer;
import workbench.gui.renderer.WrapEnabledEditor;

import workbench.util.WbDateFormatter;

/**
 * A TableCellEditor that displays multiple lines.
 *
 * @author Thomas Kellerer
 */
public class WbCellEditor
  extends AbstractCellEditor
  implements TableCellEditor, MouseListener, NullableEditor, DocumentListener, WrapEnabledEditor
{
  private final TextAreaEditor editor;
  private final WbTable parentTable;
  private final JScrollPane scroll;
  private final Color defaultBackground;
  private boolean isNull;
  private final RestoreDataAction restoreValue;
  private final SetNullAction setNull;
  private final MultilineWrapAction multilineWrapAction;
  private final TextComponentMouseListener contextMenu;
  private final SelectFkValueAction selectFk;

  public WbCellEditor(WbTable parent)
  {
    super();
    parentTable = parent;
    editor = new TextAreaEditor();
    defaultBackground = editor.getBackground();
    setFont(parent.getFont());
    scroll = new TextAreaScrollPane(editor);
    scroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
    scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    boolean wrap = GuiSettings.getWrapMultilineEditor();
    editor.setLineWrap(wrap);
    editor.setWrapStyleWord(wrap);
    int spacing = (int)parent.getIntercellSpacing().getHeight();
    EmptyBorder b = new EmptyBorder(spacing,0,0,0);
    scroll.setBorder(b);
    editor.setBorder(WbSwingUtilities.EMPTY_BORDER);
    restoreValue = new RestoreDataAction(this);
    contextMenu = new TextComponentMouseListener(editor);
    setNull = new SetNullAction(this);
    multilineWrapAction = new MultilineWrapAction(this, this.getEditor(), GuiSettings.PROP_WRAP_MULTILINE_EDITOR);

    selectFk = new SelectFkValueAction(parent);
    selectFk.addToInputMap(editor);

    contextMenu.addAction(setNull);
    contextMenu.addAction(selectFk);
    contextMenu.addAction(restoreValue);
    contextMenu.addAction(multilineWrapAction);
    editor.addMouseListener(this);
  }

  public void dispose()
  {
    WbAction.dispose(multilineWrapAction, setNull, restoreValue);
    if (contextMenu != null) contextMenu.dispose();
  }

  @Override
  public void setWordwrap(boolean flag)
  {
    editor.setLineWrap(flag);
    editor.setWrapStyleWord(flag);
    GuiSettings.setWrapMultilineEditor(flag);
  }

  @Override
  public void restoreOriginal()
  {
    int row = parentTable.getEditingRow();
    int col = parentTable.getEditingColumn();
    if (row >= 0 && col >= 0)
    {
      Object oldValue = parentTable.restoreColumnValue(row, col);
      if (oldValue != null)
      {
        editor.setText(oldValue.toString());
        isNull = false;
      }
    }
  }

  @Override
  public void setNull(boolean setToNull)
  {
    if (setToNull)
    {
      editor.setText("");
    }
    isNull = setToNull;
  }

  @Override
  public JTextComponent getEditor()
  {
    return editor;
  }

  @Override
  public void cancelCellEditing()
  {
    super.cancelCellEditing();
    // For some reason Swing resets the row height for all rows when
    // stopping the editing. If automatic row height calculation is turned on
    // we need to re-calculate the row heights
    parentTable.adjustRowHeight();
  }

  @Override
  public boolean stopCellEditing()
  {
    boolean result = super.stopCellEditing();
    // For some reason Swing resets the row height for all rows when
    // stopping the editing. If automatic row height calculation is turned on
    // we need to re-calculate the row heights
    parentTable.adjustRowHeight();
    return result;
  }

  public void setText(String newText)
  {
    editor.setText(newText);
    editor.selectAll();
    isNull = false;
  }

  public final void setFont(Font aFont)
  {
    this.editor.setFont(aFont);
  }

  public Component getComponent()
  {
    return scroll;
  }

  @Override
  public Object getCellEditorValue()
  {
    if (isNull)
    {
      return null;
    }
    return editor.getText();
  }

  @Override
  public boolean isCellEditable(EventObject anEvent)
  {
    boolean result = true;
    if (anEvent instanceof MouseEvent)
    {
      result = ((MouseEvent) anEvent).getClickCount() >= 2;
    }
    if (result)
    {
      WbSwingUtilities.requestFocus(editor);
    }
    return result;
  }

  private String getRendererDisplay(JTable table, int row, int column)
  {
    String displayValue = null;
    TableCellRenderer renderer = table.getCellRenderer(row, column);

    if (renderer instanceof WbRenderer)
    {
      WbRenderer wb = (WbRenderer)renderer;
      displayValue = wb.getDisplayValue();
    }
    else if (renderer instanceof JTextComponent)
    {
      JTextComponent text = (JTextComponent)renderer;
      displayValue = text.getText();
    }
    else if (renderer instanceof JLabel)
    {
      displayValue = ((JLabel)renderer).getText();
    }
    else if (table instanceof WbTable)
    {
      displayValue = ((WbTable)table).getValueAsString(row, column);
    }
    return displayValue;
  }

  @Override
  public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column)
  {
    String displayValue = value == null ? "" : value.toString();

    if (WbDateFormatter.isDateTimeValue(value))
    {
      displayValue = getRendererDisplay(table, row, column);
    }

    if (GuiSettings.getUseReaderForMultilineRenderer())
    {
      StringReader r = new StringReader(displayValue);
      try
      {
        editor.read(r, null);
      }
      catch (Throwable th)
      {
        LogMgr.logWarning(new CallerInfo(){}, "Could not set value using StringReader", th);
        editor.setText(displayValue);
      }
    }
    else
    {
      editor.setText(displayValue);
    }
    // this method is called when the user edits a cell
    // in that case we want to select all text
    editor.selectAll();
    setNull(false);
    setEditable(!parentTable.isReadOnly());
    restoreValue.setEnabled(parentTable.getDataStoreTableModel().isColumnModified(row, column));
    return scroll;
  }

  @Override
  public boolean shouldSelectCell(EventObject anEvent)
  {
    return true;
  }

  @Override
  public void mouseClicked(MouseEvent evt)
  {
    if (evt.getClickCount() == 2 && evt.getButton() == MouseEvent.BUTTON1 && this.parentTable != null)
    {
      CellWindowEdit edit = new CellWindowEdit(parentTable);
      edit.openEditWindow();
    }
  }

  @Override
  public void mousePressed(MouseEvent evt)
  {
  }

  @Override
  public void mouseReleased(MouseEvent evt)
  {
  }

  @Override
  public void mouseEntered(MouseEvent evt)
  {
  }

  @Override
  public void mouseExited(MouseEvent evt)
  {
  }

  public void setEditable(boolean flag)
  {
    editor.setEditable(flag);
    if (!flag)
    {
      editor.setBackground(defaultBackground);
    }
    editor.getCaret().setVisible(true);
  }

  @Override
  public void insertUpdate(DocumentEvent e)
  {
    setNull(false);
  }

  @Override
  public void removeUpdate(DocumentEvent e)
  {
    setNull(false);
  }

  @Override
  public void changedUpdate(DocumentEvent e)
  {
    setNull(false);
  }

  private static class TextAreaEditor
    extends JTextArea
  {
    TextAreaEditor()
    {
      super();
      this.setFocusCycleRoot(false);
      Set<? extends java.awt.AWTKeyStroke> empty = Collections.emptySet();
      this.setFocusTraversalKeys(WHEN_FOCUSED, empty);

      Object tabAction = this.getInputMap().get(WbSwingUtilities.TAB);

      this.getInputMap().put(WbSwingUtilities.TAB, "wb-do-nothing-at-all");

      if (tabAction != null)
      {
        this.getInputMap().put(WbSwingUtilities.CTRL_TAB, tabAction);
      }

      // Remove the default action for the Enter key and replace it with
      // the "stop-editing" action which is the default for all other editors.
      Object enterAction = this.getInputMap().get(WbSwingUtilities.ENTER);

      this.getInputMap().put(WbSwingUtilities.ENTER, "wb-stop-editing");

      if (enterAction != null)
      {
        // This will map the original Action for Enter (basically: create a new line)
        // to Alt-Enter and Ctrl-Enter
        this.getInputMap().put(WbSwingUtilities.CTRL_ENTER, enterAction);
        this.getInputMap().put(WbSwingUtilities.ALT_ENTER, enterAction);
      }
    }

    @Override
    public Insets getInsets()
    {
      return new Insets(0, 0, 0, 0);
    }

    @Override
    public Insets getMargin()
    {
      return new Insets(0, 0, 0, 0);
    }

    @Override
    public boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed)
    {
      return super.processKeyBinding(ks, e, condition, pressed);
    }

  }

  public class TextAreaScrollPane
    extends JScrollPane
    implements NullableEditor
  {

    TextAreaScrollPane(TextAreaEditor content)
    {
      super(content);
      setFocusCycleRoot(false);
      Set<? extends java.awt.AWTKeyStroke> empty = Collections.emptySet();
      this.setFocusTraversalKeys(WHEN_FOCUSED, empty);
    }

    @Override
    public Insets getInsets()
    {
      return new Insets(0, 0, 0, 0);
    }

    @Override
    public boolean requestFocus(boolean temp)
    {
      return editor.requestFocus(temp);
    }

    @Override
    public void requestFocus()
    {
      editor.requestFocus();
    }

    @Override
    public boolean requestFocusInWindow()
    {
      return editor.requestFocusInWindow();
    }

    @Override
    public boolean requestFocusInWindow(boolean temp)
    {
      return editor.requestFocusInWindow();
    }

    /**
     * Overriding processKeyBinding is needed in order to forward the initial keystrokes
     * from the JTable to the editor (because the scroll component is registered as the "editor component" with the table)
     */
    @Override
    protected boolean processKeyBinding(KeyStroke ks, KeyEvent e, int condition, boolean pressed)
    {
      return editor.processKeyBinding(ks, e, condition, pressed);
    }

    @Override
    public void setNull(boolean setToNull)
    {
      WbCellEditor.this.setNull(setToNull);
    }

    @Override
    public JTextComponent getEditor()
    {
      return editor;
    }

    @Override
    public void restoreOriginal()
    {
      WbCellEditor.this.restoreOriginal();
    }


  }
}
