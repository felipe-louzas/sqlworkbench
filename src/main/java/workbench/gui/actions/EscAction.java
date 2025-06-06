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
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;

import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.KeyStroke;

/**
 * An action mapped to the ESC key.
 *
 * @author Thomas Kellerer
 */
public class EscAction
  extends WbAction
{
  private ActionListener client;

  public EscAction(JDialog d, ActionListener aClient)
  {
    super();
    client = aClient;
    isConfigurable = false;
    setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
    addToInputMap(d.getRootPane());
  }

  @Override
  public void actionPerformed(ActionEvent e)
  {
    e.setSource(this);
    this.client.actionPerformed(e);
  }

  @Override
  public void addToInputMap(JComponent c)
  {
    super.addToInputMap(c, JComponent.WHEN_IN_FOCUSED_WINDOW);
  }

  @Override
  public boolean useInToolbar()
  {
    return false;
  }
}
