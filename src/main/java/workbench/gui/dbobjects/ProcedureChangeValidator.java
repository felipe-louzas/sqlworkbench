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
package workbench.gui.dbobjects;

import workbench.db.DbObjectChanger;
import workbench.db.ProcedureDefinition;
import workbench.db.ProcedureReader;
import workbench.db.WbConnection;

import workbench.gui.components.DataStoreTableModel;

import workbench.storage.InputValidator;

/**
 *
 * @author Thomas Kellerer
 */
public class ProcedureChangeValidator
  implements InputValidator
{
  private DbObjectChanger changer;

  public ProcedureChangeValidator()
  {
  }

  @Override
  public boolean isValid(Object newValue, int row, int col, DataStoreTableModel source)
  {
    if (changer == null) return false;

    String type = null;
    Object userObject = source.getDataStore().getRow(row).getUserObject();
    if (userObject instanceof ProcedureDefinition)
    {
      type = ((ProcedureDefinition)userObject).getObjectType();
    }
    else
    {
      return false;
    }

    if (col == ProcedureReader.COLUMN_IDX_PROC_LIST_REMARKS)
    {
      return changer.getCommentSql(type, null) != null;
    }
    else if (col == ProcedureReader.COLUMN_IDX_PROC_LIST_SCHEMA)
    {
      return changer.getChangeSchemaSql(type) != null;
    }
    else if (col == ProcedureReader.COLUMN_IDX_PROC_LIST_CATALOG)
    {
      return changer.getChangeCatalogSql(type) != null;
    }
    else if (col == ProcedureReader.COLUMN_IDX_PROC_LIST_NAME)
    {
      return changer.getRenameObjectSql(type) != null;
    }
    return false;
  }

  public void setConnection(WbConnection con)
  {
    if (con != null)
    {
      changer = new DbObjectChanger(con);
    }
    else
    {
      changer = null;
    }
  }
}
