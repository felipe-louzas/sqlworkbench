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
package workbench.gui.editor.actions;

import java.awt.event.ActionEvent;

import javax.swing.KeyStroke;

import workbench.resource.ResourceMgr;

import workbench.gui.actions.WbAction;
import workbench.gui.editor.InputHandler;
import workbench.gui.editor.JEditTextArea;

/**
 *
 * @author Thomas Kellerer
 */
public class EditorAction
  extends WbAction
{
  private JEditTextArea editor;

  protected EditorAction(String resourceKey, int key, int modifier)
  {
    super();
    init(resourceKey, KeyStroke.getKeyStroke(key, modifier));
  }

  protected EditorAction(String resourceKey, KeyStroke keyStroke)
  {
    super();
    init(resourceKey, keyStroke);
  }

  protected void init(String resourceKey, KeyStroke key)
  {
    // initMenuDefinition cannot be used because the editor actions don't have a description/tooltip
    // therefor a "resource not found exception" would be thrown
    setMenuText(ResourceMgr.getString(resourceKey));
    setDefaultAccelerator(key);
    initializeShortcut();
  }

  protected JEditTextArea getTextArea(ActionEvent evt)
  {
    if (editor == null)
    {
      editor = InputHandler.getTextArea(evt);
    }
    return editor;
  }
}
