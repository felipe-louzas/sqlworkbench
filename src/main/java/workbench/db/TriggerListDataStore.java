/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2022 Thomas Kellerer.
 *
 * Licensed under a modified Apache License, Version 2.0 (the "License")
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
package workbench.db;

import java.sql.Types;
import java.util.Arrays;

import workbench.storage.DataStore;

import static workbench.db.TriggerReader.*;
/**
 *
 * @author Thomas Kellerer
 */
public class TriggerListDataStore
  extends DataStore
{
  private boolean showSchema = true;

  public TriggerListDataStore(boolean includeSchema)
  {
    this.showSchema = includeSchema;

    String[] columns;
    int[] sizes;
    if (showSchema)
    {
      sizes = new int[]{30, 30, 30, 20, 20, 20, 10, 10};
      columns = new String[]{TRIGGER_NAME_COLUMN,
                             TRIGGER_SCHEMA_COLUMN,
                             TRIGGER_TYPE_COLUMN,
                             TRIGGER_EVENT_COLUMN,
                             TRIGGER_TABLE_COLUMN,
                             TRIGGER_COMMENT_COLUMN,
                             TRIGGER_STATUS_COLUMN,
                             TRIGGER_LEVEL_COLUMN};
    }
    else
    {
      sizes = new int[]{30, 30, 20, 20, 20, 10, 10};
      columns = new String[]{TRIGGER_NAME_COLUMN,
                             TRIGGER_TYPE_COLUMN,
                             TRIGGER_EVENT_COLUMN,
                             TRIGGER_TABLE_COLUMN,
                             TRIGGER_COMMENT_COLUMN,
                             TRIGGER_STATUS_COLUMN,
                             TRIGGER_LEVEL_COLUMN};
    }
    int[] types = new int[columns.length];
    Arrays.fill(types, Types.VARCHAR);
    initializeStructure(columns, types, sizes);
  }

  public void setTriggerSchema(int row, String schema)
  {
    if (showSchema)
    {
      setValue(row, TRIGGER_SCHEMA_COLUMN, schema);
    }
  }

  public String getTriggerSchema(int row)
  {
    if (showSchema)
    {
      return getValueAsString(row, TRIGGER_SCHEMA_COLUMN);
    }
    return null;
  }

  public void setTriggerName(int row, String name)
  {
    setValue(row, TRIGGER_NAME_COLUMN, name);
  }

  public String getTriggerName(int row)
  {
    return getValueAsString(row, TRIGGER_NAME_COLUMN);
  }

  public void setTriggerTable(int row, String name)
  {
    setValue(row, TRIGGER_TABLE_COLUMN, name);
  }

  public String getTriggerTable(int row)
  {
    return getValueAsString(row, TRIGGER_TABLE_COLUMN);
  }

  public String getStatus(int row)
  {
    return getValueAsString(row, TRIGGER_STATUS_COLUMN);
  }
  public void setStatus(int row, String status)
  {
    setValue(row, TRIGGER_STATUS_COLUMN, status);
  }

  public String getLevel(int row)
  {
    return getValueAsString(row, TRIGGER_LEVEL_COLUMN);
  }
  public void setLevel(int row, String level)
  {
    setValue(row, TRIGGER_LEVEL_COLUMN, level);
  }

  public String getTriggerType(int row)
  {
    return getValueAsString(row, TRIGGER_TYPE_COLUMN);
  }
  public void setTriggerType(int row, String type)
  {
    setValue(row, TRIGGER_TYPE_COLUMN, type);
  }

  public String getRemarks(int row)
  {
    return getValueAsString(row, TRIGGER_COMMENT_COLUMN);
  }
  public void setRemarks(int row, String remarks)
  {
    setValue(row, TRIGGER_COMMENT_COLUMN, remarks);
  }

  public String getEvent(int row)
  {
    return getValueAsString(row, TRIGGER_EVENT_COLUMN);
  }
  public void setEvent(int row, String event)
  {
    setValue(row, TRIGGER_EVENT_COLUMN, event);
  }

}
