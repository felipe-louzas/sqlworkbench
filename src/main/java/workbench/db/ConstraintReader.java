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
package workbench.db;

import java.util.Collections;
import java.util.List;

import workbench.util.StringUtil;

/**
 * An interface to read column and table constraints from the database.
 *
 * @author Thomas Kellerer
 */
public interface ConstraintReader
{
  /**
   * Retrieve the column constraints for the given table and stores them in the
   * list of columns.
   *
   * The key to the returned Map is the column name, the value is the full expression which can be appended
   * to the column definition inside a CREATE TABLE statement.
   *
   * @param dbConnection the connection to use
   * @param table        the table to check
   */
  void retrieveColumnConstraints(WbConnection dbConnection, TableDefinition table);


  /**
   * Returns the table level constraints for the table (usually these are check constraints).
   *
   * @param dbConnection  the connection to use
   * @param table        the table to check
   *
   * @return a list of table constraints or an empty list if nothing was found
   */
  List<TableConstraint> getTableConstraints(WbConnection dbConnection, TableDefinition table);

  /**
   * Rebuild the source of the given constraints.
   *
   * @param constraints  the constraints for which to build the source
   * @param indent       a line indent to be used
   */
  String getConstraintSource(List<TableConstraint> constraints, String indent);

  default void updateIndexList(List<TableConstraint> constraints, List<IndexDefinition> tableIndex)
  {

  }

  /**
   * A ConstraintReader which does nothing.
   */
  ConstraintReader NULL_READER = new ConstraintReader()
  {
    @Override
    public void retrieveColumnConstraints(WbConnection dbConnection,TableDefinition table)
    {
    }

    @Override
    public List<TableConstraint> getTableConstraints(WbConnection dbConnection, TableDefinition table)
    {
      return Collections.emptyList();
    }

    @Override
    public String getConstraintSource(List<TableConstraint> constraints, String indent)
    {
      return StringUtil.EMPTY_STRING;
    }
  };
}
