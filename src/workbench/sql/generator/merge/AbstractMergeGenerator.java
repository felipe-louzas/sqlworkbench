/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2021 Thomas Kellerer.
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
package workbench.sql.generator.merge;

import java.util.ArrayList;
import java.util.List;

import workbench.db.ColumnIdentifier;

/**
 *
 * @author Thomas Kellerer
 */
public abstract class AbstractMergeGenerator
  implements MergeGenerator
{
  protected final List<ColumnIdentifier> columns = new ArrayList<>();

  @Override
  public void setColumns(List<ColumnIdentifier> columns)
  {
    this.columns.clear();
    if (columns != null)
    {
      this.columns.addAll(columns);
    }
  }

  protected boolean includeColumn(ColumnIdentifier column)
  {
    if (this.columns.isEmpty()) return true;
    return this.columns.contains(column);
  }

}
