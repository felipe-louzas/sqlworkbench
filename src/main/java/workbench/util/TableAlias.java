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
package workbench.util;

import workbench.db.TableIdentifier;

/**
 * @author Thomas Kellerer
 */
public class TableAlias
  extends Alias
{
  private TableIdentifier table;

  public TableAlias(String value)
  {
    super(value);
    checkTable('.', '.');
  }

  public TableAlias(String objectName, char catalogSeparator, char schemaSeparator)
  {
    super(objectName);
    checkTable(catalogSeparator, schemaSeparator);
  }

  public TableAlias(String objectName, String alias, char catalogSeparator, char schemaSeparator)
  {
    super(objectName, alias);
    checkTable(catalogSeparator, schemaSeparator);
  }

  public void setTableIdentifier(TableIdentifier tbl)
  {
    this.table = tbl;
  }

  private void checkTable(char catalogSeparator, char schemaSeparator)
  {
    if (getObjectName() != null)
    {
      this.table = new TableIdentifier(getObjectName(), catalogSeparator, schemaSeparator);
    }
  }

  public final TableIdentifier getTable()
  {
    return this.table;
  }

  /**
   * Compares the given name to this TableAlias checking
   * if the name either references this table or its alias.
   *
   * @see TableIdentifier#compareNames(TableIdentifier, TableIdentifier, boolean)
   */
  public boolean isTableOrAlias(String name, char catalogSeparator, char schemaSeparator, boolean exactMatch)
  {
    if (StringUtil.isEmpty(name)) return false;
    if (name.trim().equalsIgnoreCase(getNameToUse())) return true;
    if (this.table == null) return false;

    TableIdentifier tbl = new TableIdentifier(name, catalogSeparator, schemaSeparator);
    if (exactMatch)
    {
      return table.getTableExpression().equalsIgnoreCase(tbl.getTableExpression());
    }
    return TableIdentifier.compareNames(tbl, table, true);
  }

  public static TableAlias createFrom(Alias a)
  {
    TableAlias tbl = new TableAlias(a.getObjectName());
    tbl.setAlias(a.getAlias());
    tbl.setStartPositionInQuery(a.getStartPositionInQuery());
    tbl.setEndPositionInQuery(a.getEndPositionInQuery());
    return tbl;
  }
}
