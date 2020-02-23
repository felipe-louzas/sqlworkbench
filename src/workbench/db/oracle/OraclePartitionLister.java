/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2020 Thomas Kellerer.
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
package workbench.db.oracle;

import java.util.Collections;
import java.util.List;

import workbench.db.DbObject;
import workbench.db.PartitionLister;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

/**
 *
 * @author Thomas Kellerer
 */
public class OraclePartitionLister
  implements PartitionLister
{
  private final WbConnection conn;

  public OraclePartitionLister(WbConnection conn)
  {
    this.conn = conn;
  }

  @Override
  public List<? extends DbObject> getPartitions(TableIdentifier table)
  {
    return null;
  }

  @Override
  public List<? extends DbObject> getSubPartitions(TableIdentifier baseTable, DbObject partition)
  {
    return Collections.emptyList();
  }

  @Override
  public boolean supportsSubPartitions()
  {
    return true;
  }

}
