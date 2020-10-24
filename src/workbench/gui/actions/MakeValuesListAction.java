/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2020, Thomas Kellerer
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

import java.awt.Window;
import java.awt.event.ActionEvent;

import workbench.interfaces.TextSelectionListener;
import workbench.resource.GeneratedIdentifierCase;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.gui.WbSwingUtilities;
import workbench.gui.editor.ValuesListCreator;
import workbench.gui.sql.EditorPanel;

import workbench.util.StringUtil;

/**
 * Convert the current selection to a format approriate for a VALUES list.
 *
 * @see workbench.gui.editor.ValuesListCreator
 *
 * @author Thomas Kellerer
 */
public class MakeValuesListAction
  extends WbAction
  implements TextSelectionListener
{
  private EditorPanel client;

  public MakeValuesListAction(EditorPanel aClient)
  {
    super();
    this.client = aClient;
    this.client.addSelectionListener(this);
    this.initMenuDefinition("MnuTxtMakeValuesList");
    this.setMenuItemName(ResourceMgr.MNU_TXT_SQL);
    this.setEnabled(false);
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    String input = client.getSelectedText();
    // shouldn't happen
    if (StringUtil.isBlank(input)) return;

    ValuesCreatorParameter parmInput = new ValuesCreatorParameter(input);
    Window window = WbSwingUtilities.getWindowAncestor(client);
    boolean ok = WbSwingUtilities.getOKCancel(ResourceMgr.getString("MnuTxtMakeValuesList"),
                                              window, parmInput, () -> {parmInput.setFocusToInput();});

    if (!ok) return;

    parmInput.saveSettings();
    ValuesListCreator creator = new ValuesListCreator(input, parmInput.getDelimiter(), parmInput.isRegex());
    creator.setEmptyStringIsNull(parmInput.getEmptyStringIsNull());
    creator.setTrimDelimiter(parmInput.getTrimDelimiter());
    creator.setNullString(parmInput.getNullString());
    creator.setReplaceDoubleQuotes(parmInput.getReplaceDoubleQuotes());
    String end = Settings.getInstance().getInternalEditorLineEnding();
    creator.setLineEnding(end);
    String list = creator.createValuesList();


    if (parmInput.getAddValuesClause())
    {
      String valuesClause = getValuesClause();
      // indent the generated list by two spaces
      list = list.replaceAll(StringUtil.REGEX_CRLF, end + "  ");
      list = valuesClause + end + "  " + list;
    }

    list += end;
    if (StringUtil.isNonBlank(list))
    {
      client.setSelectedText(list);
    }
  }

  private String getValuesClause()
  {
    GeneratedIdentifierCase kwCase = Settings.getInstance().getFormatterKeywordsCase();
    if (kwCase != GeneratedIdentifierCase.lower)
    {
      return "values";
    }
    return "VALUES";
  }
  
  @Override
  public void selectionChanged(int newStart, int newEnd)
  {
    this.setEnabled(this.client.getSelectionLength() > 0);
  }

}
