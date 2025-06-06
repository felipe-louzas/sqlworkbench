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
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import workbench.WbManager;
import workbench.interfaces.WbSelectionListener;
import workbench.interfaces.WbSelectionModel;

import workbench.db.DbMetadata;
import workbench.db.DbObject;
import workbench.db.DropScriptGenerator;
import workbench.db.TableIdentifier;

import workbench.gui.WbSwingUtilities;
import workbench.gui.dbobjects.DbObjectList;
import workbench.gui.dbobjects.ObjectScripterUI;

import workbench.util.CollectionUtil;

import static workbench.gui.actions.WbAction.*;

/**
 * @author Thomas Kellerer
 */
public class CreateDropScriptAction
  extends WbAction
  implements WbSelectionListener
{
  private DbObjectList source;
  private WbSelectionModel selection;

  public CreateDropScriptAction(DbObjectList client, WbSelectionModel list)
  {
    super();
    this.initMenuDefinition("MnuTxtGenerateDrop");
    this.source = client;
    selectionChanged(list);
    selection = list;
    selection.addSelectionListener(this);
  }

  @Override
  public void dispose()
  {
    super.dispose();
    if (selection != null)
    {
      selection.removeSelectionListener(this);
    }
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    if (source == null) return;
    if (!WbSwingUtilities.isConnectionIdle(source.getComponent(), source.getConnection())) return;

    List<? extends DbObject> objects = source.getSelectedObjects();
    List<TableIdentifier> tables = new ArrayList<>(objects.size());
    DbMetadata meta = source.getConnection().getMetadata();
    Set<String> types = CollectionUtil.caseInsensitiveSet();
    types.addAll(meta.getTableTypes());

    for (DbObject dbo : objects)
    {
      if (types.contains(dbo.getObjectType()))
      {
        tables.add((TableIdentifier)dbo);
      }
    }
    DropScriptGenerator generator = new DropScriptGenerator(source.getConnection());
    generator.setEndTransaction(true);
    if (invokedByMouse(e) && isCtrlPressed(e))
    {
      generator.setIncludeRecreateStatements(false);
    }

    if (invokedByMouse(e) && isAltPressed(e))
    {
      generator.setIncludeDropTable(false);
    }

    generator.setTables(tables);
    ObjectScripterUI ui = new ObjectScripterUI(generator);
    ui.show(WbManager.getInstance().getCurrentWindow());
  }

  @Override
  public void selectionChanged(WbSelectionModel selection)
  {
    if (source == null) return;
    if (selection == null) return;
    
    if (selection.hasSelection())
    {
      List<? extends DbObject> objects = source.getSelectedObjects();
      if (CollectionUtil.isEmpty(objects)) return;

      DbMetadata meta = source.getConnection().getMetadata();
      if (meta == null) return;
      Set<String> types = CollectionUtil.caseInsensitiveSet();
      types.addAll(meta.getTableTypes());
      for (DbObject dbo : objects)
      {
        if (!types.contains(dbo.getObjectType()))
        {
          setEnabled(false);
          return;
        }
        setEnabled(true);
      }
    }
    else
    {
      setEnabled(false);
    }
  }

  @Override
  public boolean useInToolbar()
  {
    return false;
  }
}
