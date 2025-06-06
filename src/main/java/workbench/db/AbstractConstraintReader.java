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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.sqltemplates.ColumnDefinitionTemplate;
import workbench.db.sqltemplates.ConstraintNameTester;

import workbench.util.CollectionUtil;
import workbench.util.StringUtil;

/**
 * A class to read table level check constraints from the database.
 *
 * @author Thomas Kellerer
 */
public abstract class AbstractConstraintReader
  implements ConstraintReader
{
  private final ConstraintNameTester nameTester;

  public AbstractConstraintReader(String dbId)
  {
    nameTester = new ConstraintNameTester(dbId);
  }

  public abstract String getColumnConstraintSql(TableIdentifier tbl);
  public abstract String getTableConstraintSql(TableIdentifier tbl);

  public int getIndexForSchemaParameter()
  {
    return -1;
  }

  public int getIndexForCatalogParameter()
  {
    return -1;
  }

  public int getIndexForTableNameParameter()
  {
    return 1;
  }

  public boolean isSystemConstraintName(String name)
  {
    if (nameTester == null) return false;
    return nameTester.isSystemConstraintName(name);
  }

  /**
   * Returns the column constraints for the given table.
   *
   * The key to the Map is the column name, the value is the full expression which can be appended
   * to the column definition inside a CREATE TABLE statement.
   *
   * For SQL Server this can also return "default constraints" (default value definitions with a name).
   * This must be taken into consideration when (re-)building the source code based on these constraints.
   *
   * @see ColumnDefinitionTemplate#getColumnDefinitionSQL(ColumnIdentifier, String, int)
   */
  @Override
  public void retrieveColumnConstraints(WbConnection dbConnection, TableDefinition def)
  {
    String sql = this.getColumnConstraintSql(def.getTable());
    if (sql == null) return;

    LogMgr.logMetadataSql(new CallerInfo(){}, "column constraints", sql);

    ResultSet rs = null;
    PreparedStatement stmt = null;
    Savepoint sp = null;

    TableIdentifier table = def.getTable();
    try
    {
      if (dbConnection.getDbSettings().useSavePointForDML())
      {
        sp = dbConnection.setSavepoint();
      }
      stmt = dbConnection.getSqlConnection().prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
      int index = this.getIndexForSchemaParameter();
      if (index > 0) stmt.setString(index, table.getSchema());

      index = this.getIndexForCatalogParameter();
      if (index > 0) stmt.setString(index, table.getCatalog());

      index = this.getIndexForTableNameParameter();
      if (index > 0) stmt.setString(index, table.getTableName());

      rs = stmt.executeQuery();
      while (rs.next())
      {
        String column = rs.getString(1);
        String constraint = rs.getString(2);
        ColumnIdentifier colId = ColumnIdentifier.findColumnInList(def.getColumns(), column);

        if (colId != null && StringUtil.isNotEmpty(constraint))
        {
          colId.setConstraint(constraint.trim());
        }
      }
      dbConnection.releaseSavepoint(sp);
    }
    catch (Exception e)
    {
      dbConnection.rollback(sp);
      LogMgr.logMetadataError(new CallerInfo(){}, e, "column constraints", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
  }

  @Override
  public String getConstraintSource(List<TableConstraint> constraints, String indent)
  {
    if (CollectionUtil.isEmpty(constraints)) return StringUtil.EMPTY_STRING;
    StringBuilder result = new StringBuilder(constraints.size() * 10);

    int nr = 0;
    for (TableConstraint cons : constraints)
    {
      if (cons == null) continue;
      if (StringUtil.isBlank(cons.getExpression())) continue;
      if (nr > 0)
      {
        result.append(",\n");
        result.append(indent);
      }
      result.append(cons.getSql());
      nr++;
    }
    return result.toString();
  }

  /**
   * Returns the table level constraints for the table
   *
   * @param dbConnection  the connection to use
   * @param table         the table to check
   *
   * @return a list of table constraints or an empty list if nothing was found
   */
  @Override
  public List<TableConstraint> getTableConstraints(WbConnection dbConnection, TableDefinition table)
  {
    String sql = this.getTableConstraintSql(table.getTable());
    if (sql == null) return null;

    LogMgr.logMetadataSql(new CallerInfo(){}, "table constraints",  sql);

    List<TableConstraint> result = CollectionUtil.arrayList();
    PreparedStatement stmt = null;

    Savepoint sp = null;

    TableIdentifier baseTable = table.getTable();
    ResultSet rs = null;
    try
    {
      if (dbConnection.getDbSettings().useSavePointForDML())
      {
        sp = dbConnection.setSavepoint();
      }

      stmt = dbConnection.getSqlConnection().prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);

      int index = this.getIndexForSchemaParameter();
      if (index > 0)
      {
        String schema = baseTable.getRawSchema();
        if (StringUtil.isBlank(schema))
        {
          schema = dbConnection.getCurrentSchema();
        }
        stmt.setString(index, schema);
      }

      index = this.getIndexForCatalogParameter();
      if (index > 0) stmt.setString(index, baseTable.getRawCatalog());

      index = this.getIndexForTableNameParameter();
      if (index > 0) stmt.setString(index, baseTable.getRawTableName());

      Pattern p = Pattern.compile("^check\\s+.*", Pattern.CASE_INSENSITIVE);
      rs = stmt.executeQuery();

      boolean hasComment = rs.getMetaData().getColumnCount() > 2;

      while (rs.next())
      {
        String name = rs.getString(1);
        String constraint = StringUtil.trim(rs.getString(2));
        String comment = null;
        if (hasComment)
        {
          comment = rs.getString(3);
        }

        if (shouldIncludeTableConstraint(name, constraint, table))
        {
          Matcher m = p.matcher(constraint);
          if (constraint.charAt(0) != '(' && !m.matches() && !constraint.startsWith("EXCLUDE"))
          {
            constraint = "(" + constraint + ")";
          }
          String template = dbConnection.getDbSettings().getCheckConstraintTemplate();
          TableConstraint c = new TableConstraint(baseTable, name, constraint, template);
          c.setIsSystemName(isSystemConstraintName(name));
          c.setComment(comment);
          result.add(c);
        }
      }
      dbConnection.releaseSavepoint(sp);
    }
    catch (SQLException e)
    {
      dbConnection.rollback(sp);
      LogMgr.logMetadataError(new CallerInfo(){}, e, "table constraints", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return result;
  }

  protected boolean shouldIncludeTableConstraint(String constraintName, String constraint, TableDefinition table)
  {
    return StringUtil.isNotEmpty(constraint);
  }

}
