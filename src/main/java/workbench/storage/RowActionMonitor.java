/*
 * RowActionMonitor.java
 *
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
package workbench.storage;

/**
 *
 * @author  Thomas Kellerer
 */
public interface RowActionMonitor
{
  /** The monitor type to show running INSERT statements */
  int MONITOR_INSERT = 0;

  /** The monitor type to show running UPDATE statements */
  int MONITOR_UPDATE = 1;

  /** The monitor type to show loading of data */
  int MONITOR_LOAD = 2;

  /** The monitor type to show exporting of data */
  int MONITOR_EXPORT = 3;

  /** The monitor type to show copying of data */
  int MONITOR_COPY = 4;

  /** The monitor type to show that one table (of several) is being processed */
  int MONITOR_PROCESS_TABLE = 5;

  /** A generic monitor type to show progress for a process that knows how many steps it will have */
  int MONITOR_PROCESS = 6;

  /** A generic monitor type to show progress for a process does not know the number of items to be processed */
  int MONITOR_PLAIN = 7;

  /** The monitor type to show deleting of data */
  int MONITOR_DELETE = 8;

  void setMonitorType(int aType);
  int getMonitorType();
  void saveCurrentType(String key);
  void restoreType(String key);
  void setCurrentObject(String object, long number, long totalObjects);
  void setCurrentRow(long currentRow, long totalRows);
  void jobFinished();
}
