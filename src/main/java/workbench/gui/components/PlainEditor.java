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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.Reader;

import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.PlainDocument;

import workbench.interfaces.Restoreable;
import workbench.interfaces.TextContainer;
import workbench.resource.GuiSettings;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.gui.WbSwingUtilities;
import workbench.gui.editor.SearchAndReplace;

/**
 * A simple text editor based on a JTextArea.
 * The panel displays also a checkbox to turn word wrapping on and off
 * and optionally an information label.
 *
 * @author Thomas Kellerer
 */
public class PlainEditor
  extends JPanel
  implements ActionListener, TextContainer, Restoreable, PropertyChangeListener
{
  private JTextArea editor;
  private JCheckBox wordWrap;
  private Color enabledBackground;
  private JLabel infoText;
  private JPanel toolPanel;
  private JScrollPane scroll;
  private String wrapSettingsKey;
  private TextComponentMouseListener editMenu;

  public PlainEditor()
  {
    this(GuiSettings.PROP_PLAIN_EDITOR_WRAP, true, true);
  }

  public PlainEditor(String settingsKey, boolean allowEdit, boolean enableWordWrap)
  {
    super();
    editor = new JTextArea();
    enabledBackground = editor.getBackground();
    editor.putClientProperty("JTextArea.infoBackground", Boolean.TRUE);
    editor.addPropertyChangeListener("document", this);
    editMenu = new TextComponentMouseListener(editor);

    scroll = new WbScrollPane(editor, WbSwingUtilities.EMPTY_BORDER);
    editor.setFont(Settings.getInstance().getEditorFont());
    this.setLayout(new BorderLayout());

    wrapSettingsKey = settingsKey;
    if (enableWordWrap)
    {
      editor.setLineWrap(true);
      editor.setWrapStyleWord(true);
      toolPanel = new JPanel();
      toolPanel.setLayout(new FlowLayout(FlowLayout.LEFT,0,0));
      toolPanel.setBorder(DividerBorder.BOTTOM_DIVIDER);
      wordWrap = new JCheckBox(ResourceMgr.getString("LblWordWrap"));
      wordWrap.setSelected(true);
      wordWrap.setFocusable(false);
      wordWrap.addActionListener(this);
      toolPanel.add(wordWrap);
      this.add(toolPanel, BorderLayout.NORTH);
    }

    this.add(scroll, BorderLayout.CENTER);
    this.setFocusable(false);
    setTabSize();
    if (allowEdit)
    {
      SearchAndReplace replacer = new SearchAndReplace(this, this);
      editMenu.addAction(replacer.getFindAction());
      editMenu.addAction(replacer.getFindNextAction());
      editMenu.addAction(replacer.getReplaceAction());
    }
    setEditable(allowEdit);
  }

  private void setTabSize()
  {
    Document d = editor.getDocument();
    if (d != null)
    {
      int tabSize = Settings.getInstance().getEditorTabWidth();
      d.putProperty(PlainDocument.tabSizeAttribute, Integer.valueOf(tabSize));
    }
  }

  @Override
  public void removeNotify()
  {
    super.removeNotify();
    editMenu.dispose();
  }

  @Override
  public int getCaretPosition()
  {
    return this.editor.getCaretPosition();
  }

  @Override
  public int getSelectionEnd()
  {
    return this.editor.getSelectionEnd();
  }

  @Override
  public int getSelectionStart()
  {
    return this.editor.getSelectionStart();
  }

  @Override
  public void select(int start, int end)
  {
    this.editor.select(start, end);
  }

  public void setInfoText(String text)
  {
    if (this.infoText == null && toolPanel != null)
    {
      this.infoText = new JLabel();
      this.toolPanel.add(Box.createHorizontalStrut(10));
      this.toolPanel.add(infoText);
    }
    this.infoText.setText(text);
  }

  public int getScrollbarWidth()
  {
    int width = 0;
    JScrollBar bar = scroll.getVerticalScrollBar();
    if (bar != null)
    {
      Dimension prefSize = bar.getPreferredSize();
      if (prefSize != null)
      {
        width = (int)prefSize.getHeight();
      }
      else
      {
        width = bar.getHeight();
      }
    }
    return width;
  }

  public int getScrollbarHeight()
  {
    int height = 0;
    JScrollBar bar = scroll.getHorizontalScrollBar();
    if (bar != null)
    {
      Dimension prefSize = bar.getPreferredSize();
      if (prefSize != null)
      {
        height = (int)prefSize.getHeight();
      }
      else
      {
        height = bar.getHeight();
      }
    }
    return height;
  }

  @Override
  public void requestFocus()
  {
    this.editor.requestFocus();
  }

  @Override
  public boolean requestFocusInWindow()
  {
    return this.editor.requestFocusInWindow();
  }

  @Override
  public void restoreSettings()
  {
    if (wordWrap != null && wrapSettingsKey != null)
    {
      boolean wrap = Settings.getInstance().getBoolProperty(wrapSettingsKey);
      wordWrap.setSelected(wrap);
      editor.setLineWrap(wrap);
    }
  }

  public void append(String text)
  {
    editor.append(text);
  }

  public void clear()
  {
    editor.setText("");
  }

  @Override
  public void saveSettings()
  {
    if (wordWrap != null)
    {
      Settings.getInstance().setProperty(wrapSettingsKey, wordWrap.isSelected());
    }
  }

  @Override
  public void setSelectedText(String aText)
  {
    this.editor.replaceSelection(aText);
  }

  @Override
  public String getText()
  {
    return this.editor.getText();
  }

  @Override
  public String getSelectedText()
  {
    return this.editor.getSelectedText();
  }

  public void readText(Reader in)
    throws IOException
  {
    this.editor.read(in, null);
  }

  @Override
  public void setText(String aText)
  {
    this.editor.setText(aText);
  }

  @Override
  public void setCaretPosition(int pos)
  {
    this.editor.setCaretPosition(pos);
  }

  @Override
  public void actionPerformed(ActionEvent e)
  {
    this.editor.setLineWrap(this.wordWrap.isSelected());
    if (wrapSettingsKey != null)
    {
      Settings.getInstance().setProperty(wrapSettingsKey, wordWrap.isSelected());
    }
  }

  @Override
  public void setEditable(boolean flag)
  {
    this.editor.setEditable(flag);
    this.editor.setBackground(enabledBackground);
  }

  @Override
  public boolean isEditable()
  {
    return this.editor.isEditable();
  }

  @Override
  public boolean isTextSelected()
  {
    return (getSelectionStart() < getSelectionEnd());
  }

  @Override
  public String getWordAtCursor(String wordChars)
  {
    return null;
  }

  @Override
  public int getLineCount()
  {
    return editor.getLineCount();
  }

  @Override
  public int getStartInLine(int offset)
  {
    try
    {
      int line = getLineOfOffset(offset);
      int start = editor.getLineStartOffset(line);
      return offset - start;
    }
    catch (BadLocationException ex)
    {
      return -1;
    }
  }

  @Override
  public int getLineOfOffset(int offset)
  {
    try
    {
      return editor.getLineOfOffset(offset);
    }
    catch (BadLocationException ex)
    {
      return -1;
    }
  }

  @Override
  public String getLineText(int line)
  {
    try
    {
      int start = editor.getLineStartOffset(line);
      int end = editor.getLineEndOffset(line);

      return editor.getText(start, end - start);
    }
    catch (BadLocationException ex)
    {
      return null;
    }
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt)
  {
    if (evt.getSource() == editor && "document".equals(evt.getPropertyName()))
    {
      setTabSize();
    }
  }

}
