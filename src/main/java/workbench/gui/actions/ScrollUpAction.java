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
package workbench.gui.actions;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import javax.swing.KeyStroke;

import workbench.gui.editor.LineScroller;

/**
 *
 * @author Thomas Kellerer
 */
public class ScrollUpAction
  extends WbAction
{
  private final LineScroller client;

  public ScrollUpAction(LineScroller scroller)
  {
    super();
    client = scroller;
    initMenuDefinition("MnuTxtScrollUp", KeyStroke.getKeyStroke(KeyEvent.VK_UP, KeyEvent.CTRL_DOWN_MASK));
  }

  @Override
  public boolean isEnabled()
  {
    return client != null && client.canScrollUp();
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    client.scrollUp();
  }

  @Override
  public boolean useInToolbar()
  {
    return false;
  }
}
