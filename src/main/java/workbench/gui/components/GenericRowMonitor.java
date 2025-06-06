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
package workbench.gui.components;

import java.util.HashMap;
import java.util.Map;

import workbench.interfaces.StatusBar;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;

import workbench.storage.RowActionMonitor;

import workbench.util.NumberStringCache;

/**
 *
 * @author Thomas Kellerer
 */
public class GenericRowMonitor
  implements RowActionMonitor
{
  private final StatusBar statusBar;
  private String updateMsg;
  private String currentMonitorObject;
  private int monitorType;
  private final String objectMsg = ResourceMgr.getString("MsgProcessObject") + " ";
  private final Map<String, TypeEntry> typeStack = new HashMap<>();

  public GenericRowMonitor(StatusBar status)
  {
    this.statusBar = status;
  }

  @Override
  public int getMonitorType() { return this.monitorType; }

  @Override
  public void setMonitorType(int type)
  {
    this.monitorType = type;
    switch (type)
    {
      case RowActionMonitor.MONITOR_INSERT:
        this.updateMsg = ResourceMgr.getString("MsgImportingRow") + " ";
        break;
      case RowActionMonitor.MONITOR_UPDATE:
        this.updateMsg = ResourceMgr.getString("MsgUpdatingRow") + " ";
        break;
      case RowActionMonitor.MONITOR_LOAD:
        this.updateMsg = ResourceMgr.getString("MsgLoadingRow") + " ";
        break;
      case RowActionMonitor.MONITOR_EXPORT:
        this.updateMsg = ResourceMgr.getString("MsgWritingRow") + " ";
        break;
      case RowActionMonitor.MONITOR_COPY:
        this.updateMsg = ResourceMgr.getString("MsgCopyingRow") + " ";
        break;
      case RowActionMonitor.MONITOR_PROCESS_TABLE:
        this.updateMsg = ResourceMgr.getString("MsgProcessTable") + " ";
        break;
      case RowActionMonitor.MONITOR_PROCESS:
        this.updateMsg = ResourceMgr.getString("MsgProcessObject") + " ";
        break;
      case RowActionMonitor.MONITOR_DELETE:
        this.updateMsg = ResourceMgr.getString("MsgProcessingDeletes") + " ";
        break;
      case RowActionMonitor.MONITOR_PLAIN:
        this.updateMsg = null;
        break;
      default:
        LogMgr.logWarning(new CallerInfo(){}, "Invalid monitor type " + type + " specified!");
        this.monitorType = RowActionMonitor.MONITOR_PLAIN;
        this.updateMsg = null;
    }
  }

  @Override
  public void setCurrentObject(String name, long number, long total)
  {
    this.currentMonitorObject = name;
    if (this.monitorType == RowActionMonitor.MONITOR_PLAIN)
    {
      statusBar.setStatusMessage(name);
    }
    else
    {
      StringBuilder msg = new StringBuilder(40);
      if (objectMsg != null) msg.append(objectMsg);
      msg.append(name);
      if (number > 0)
      {
        msg.append(" (");
        msg.append(number);
        if (total > 0)
        {
          msg.append('/');
          msg.append(total);
        }
        msg.append(')');
      }
      statusBar.setStatusMessage(msg.toString());
    }
  }

  @Override
  public void setCurrentRow(long currentRow, long totalRows)
  {
    if (this.updateMsg == null && this.currentMonitorObject == null) return;
    StringBuilder msg = new StringBuilder(40);
    if (this.updateMsg == null)
    {
      msg.append(objectMsg);
      msg.append(this.currentMonitorObject);
      msg.append(" (");
    }
    else
    {
      msg.append(this.updateMsg);
    }
    msg.append(NumberStringCache.getNumberString(currentRow));
    if (totalRows > 0)
    {
      msg.append('/');
      msg.append(NumberStringCache.getNumberString(totalRows));
    }
    if (this.updateMsg == null) msg.append(')');
    statusBar.setStatusMessage(msg.toString());
  }

  @Override
  public void jobFinished()
  {
    statusBar.clearStatusMessage();
    updateMsg = null;
    currentMonitorObject = null;
    typeStack.clear();
  }

  @Override
  public void saveCurrentType(String key)
  {
    TypeEntry entry = new TypeEntry();
    entry.msg = this.updateMsg;
    entry.type = this.monitorType;
    entry.obj = this.currentMonitorObject;
    this.typeStack.put(key, entry);
  }

  @Override
  public void restoreType(String type)
  {
    statusBar.clearStatusMessage();
    TypeEntry entry = typeStack.get(type);
    if (entry == null) return;
    this.updateMsg = entry.msg;
    this.currentMonitorObject = entry.obj;
    this.monitorType = entry.type;
  }
}

class TypeEntry
{
  int type;
  String msg;
  String obj;
}
