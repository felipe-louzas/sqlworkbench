/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
 * that restricts the use for certain governments.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.sql-workbench.eu/manual/license.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.eu
 */
package workbench.gui.dbobjects.objecttree.vertica;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;

import workbench.db.WbConnection;
import workbench.db.vertica.VerticaProjectionReader;

import workbench.gui.dbobjects.objecttree.ObjectTreeNode;
import workbench.gui.dbobjects.objecttree.TreeLoader;

import workbench.storage.DataStore;


/**
 *
 * @author Thomas Kellerer
 */
public class ProjectionColumnsNode
  extends ObjectTreeNode
{
  public ProjectionColumnsNode()
  {
    super(ResourceMgr.getString("TxtDbExplorerTableDefinition"), TreeLoader.TYPE_PROJECTION_COLUMNS);
    setAllowsChildren(true);
  }

  @Override
  public boolean loadChildren(WbConnection connection, TreeLoader loader)
  {
    if (getParent() == null) return false;
    ProjectionNode projection = (ProjectionNode)getParent();
    loadProjectionColumns(connection, projection);
    return true;
  }

  private void loadProjectionColumns(WbConnection conn, ProjectionNode projection)
  {
    VerticaProjectionReader reader = new VerticaProjectionReader();
    reader.setConnection(conn);
    try
    {
      DataStore columns = reader.getProjectionColumns(projection.getName(), projection.getBaseTable().getSchema());
      for (int row = 0; row < columns.getRowCount(); row ++)
      {
        String colName = columns.getValueAsString(row, "projection_column_name");
        String type = columns.getValueAsString(row, "data_type");
        add(new ProjectionColumn(colName, type));
      }
      setChildrenLoaded(true);
    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not load projection columns", ex);
    }
  }

}
