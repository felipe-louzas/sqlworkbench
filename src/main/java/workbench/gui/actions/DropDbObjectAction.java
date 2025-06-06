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

import java.awt.EventQueue;
import java.awt.event.ActionEvent;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

import workbench.interfaces.ObjectDropListener;
import workbench.interfaces.ObjectDropper;
import workbench.interfaces.Reloadable;
import workbench.interfaces.WbSelectionListener;
import workbench.interfaces.WbSelectionModel;

import workbench.db.ColumnDropper;
import workbench.db.ColumnIdentifier;
import workbench.db.DbObject;
import workbench.db.DbSettings;
import workbench.db.GenericObjectDropper;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.gui.WbSwingUtilities;
import workbench.gui.dbobjects.DbObjectList;
import workbench.gui.dbobjects.ObjectDropperUI;

import workbench.util.CollectionUtil;

/**
 * @author Thomas Kellerer
 */
public class DropDbObjectAction
  extends WbAction
  implements WbSelectionListener
{
  private DbObjectList source;
  private ObjectDropper dropper;
  private ObjectDropListener dropListener;
  private Reloadable data;
  private boolean available = true;
  private WbSelectionModel selection;

  public DropDbObjectAction(String labelKey, DbObjectList client, ListSelectionModel list, Reloadable r)
  {
    this("MnuTxtDropDbObject", client, WbSelectionModel.Factory.createFacade(list), r);
  }

  public DropDbObjectAction(DbObjectList client, WbSelectionModel list, Reloadable r)
  {
    this("MnuTxtDropDbObject", client, list, r);
  }

  public DropDbObjectAction(DbObjectList client, WbSelectionModel list)
  {
    this("MnuTxtDropDbObject", client, list, null);
  }

  public DropDbObjectAction(String labelKey, DbObjectList client, WbSelectionModel list, Reloadable r)
  {
    super();
    this.initMenuDefinition(labelKey);
    this.source = client;
    this.data = r;
    selection = list;
    selectionChanged(list);
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

  public void addDropListener(ObjectDropListener listener)
  {
    dropListener = listener;
  }

  @Override
  public void executeAction(ActionEvent e)
  {
    dropObjects();
  }

  public void setAvailable(boolean flag)
  {
    this.available = flag;
    if (!available) this.setEnabled(false);
  }

  public void setDropper(ObjectDropper dropperToUse)
  {
    this.dropper = dropperToUse;
  }

  private boolean needAutoCommit(List<DbObject> objects)
  {
    DbSettings dbs = source.getConnection().getDbSettings();
    for (DbObject dbo : objects)
    {
      if (!dbs.canDropInTransaction(dbo.getObjectType()))
      {
        return true;
      }
    }
    return false;
  }

  private void dropObjects()
  {
    if (!WbSwingUtilities.isConnectionIdle(source.getComponent(), source.getConnection())) return;

    final List<DbObject> objects = source.getSelectedObjects();
    if (objects == null || objects.isEmpty()) return;

    boolean autoCommitChanged = false;
    boolean autoCommit = source.getConnection().getAutoCommit();

    ObjectDropperUI dropperUI = null;
    try
    {
      // this is essentially here for the DbTree, because the DbTree sets its own connection
      // to autocommit regardless of the profile to reduce locking when retrieving data.
      // If the profile was not set to autocommit the dropping of the objects should be done in a transaction.
      if (autoCommit && !source.getConnection().getProfile().getAutocommit())
      {
        source.getConnection().changeAutoCommit(false);
        autoCommitChanged = true;
      }

      if (!autoCommit && needAutoCommit(objects))
      {
        source.getConnection().changeAutoCommit(true);
        autoCommitChanged = true;
      }

      ObjectDropper dropperToUse = this.dropper;
      if (dropperToUse == null)
      {
        if (objects.get(0) instanceof ColumnIdentifier)
        {
          dropperToUse = new ColumnDropper();
        }
        else
        {
          dropperToUse = new GenericObjectDropper();
        }
      }
      dropperToUse.setObjects(objects);
      dropperToUse.setConnection(source.getConnection());
      dropperToUse.setObjectTable(source.getObjectTable());

      dropperUI = new ObjectDropperUI(dropperToUse);

      JFrame f = (JFrame)SwingUtilities.getWindowAncestor(source.getComponent());
      dropperUI.showDialog(f);
    }
    finally
    {
      if (autoCommitChanged)
      {
        source.getConnection().changeAutoCommit(autoCommit);
      }
    }

    if (dropperUI.success() && !dropperUI.dialogWasCancelled())
    {
      EventQueue.invokeLater(() ->
      {
        if (data != null)
        {
          data.reload();
        }
        if (dropListener != null)
        {
          dropListener.objectsDropped(objects);
        }
      });
    }
  }

  @Override
  public void selectionChanged(WbSelectionModel list)
  {
    WbConnection conn = this.source.getConnection();

    if (conn == null || conn.isSessionReadOnly())
    {
      setEnabled(false);
    }
    else
    {
      List<DbObject> objects = source.getSelectedObjects();
      if (CollectionUtil.isEmpty(objects))
      {
        setEnabled(false);
        return;
      }

      int colCount = 0;
      int selCount = objects.size();

      for (DbObject dbo : objects)
      {
        if (dbo instanceof ColumnIdentifier)
        {
          colCount++;
        }
      }

      if (colCount > 0 && colCount == selCount)
      {
        TableIdentifier tbl = source.getObjectTable();
        setEnabled(tbl != null);
      }
      else
      {
        setEnabled(this.available && (colCount == 0 && selCount > 0));
      }
    }
  }

  @Override
  public boolean useInToolbar()
  {
    return false;
  }
}
