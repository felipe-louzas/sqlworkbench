/*
 * WbLabelField.java
 *
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
import java.awt.Font;

import javax.swing.JTextField;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.plaf.basic.BasicTextFieldUI;
import workbench.gui.WbSwingUtilities;

import workbench.gui.actions.WbAction;

/**
 * A label that is built from a JTextField so that the text can
 * be selected and copied into the clipboard
 *
 * @author Thomas Kellerer
 */
public class WbLabelField
  extends JTextField
{
  private static final Border DEFAULT_BORDER = new EmptyBorder(2, 5, 2, 2);
  private TextComponentMouseListener mouseListener;

  public WbLabelField()
  {
    super();
    init();
  }

  public WbLabelField(Border border)
  {
    super();
    init(border);
  }

  public WbLabelField(String text)
  {
    super(text);
    init();
  }

  private void init()
  {
    init(DEFAULT_BORDER);
  }
  
  private void init(Border border)
  {
    setUI(new BasicTextFieldUI());
    setEditable(false);
    setOpaque(false);
    mouseListener = new TextComponentMouseListener();
    addMouseListener(mouseListener);
    setBorder(border);

    Font f = UIManager.getFont("Label.font");
    if (f != null)
    {
      setFont(f);
    }

    Color bg = UIManager.getColor("Label.background");
    if (bg != null)
    {
      setBackground(bg);
    }

    Color fg = UIManager.getColor("Label.foreground");
    if (fg != null)
    {
      setForeground(fg);
    }
  }

  @Override
  public void setText(String t)
  {
    if (getFont() == null) return;
    if (getFontMetrics(getFont()) == null) return;
    super.setText(t);
    setCaretPosition(0);
  }

  public void useBoldFont()
  {
    WbSwingUtilities.makeBold(this);
  }

  public void addPopupAction(WbAction a)
  {
    mouseListener.addAction(a);
  }

  public void dispose()
  {
    if (mouseListener != null)
    {
      mouseListener.dispose();
    }
  }
}
