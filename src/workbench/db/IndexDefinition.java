/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2021, Thomas Kellerer
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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * A class to store the defintion of a database index.
 * @author  Thomas Kellerer
 */
public class IndexDefinition
  implements DbObject, Serializable
{
  private boolean isPK;
  private boolean isUnique;
  private String schema;
  private String catalog;
  private String indexName;
  private String indexType;
  private TableIdentifier baseTable;
  private List<IndexColumn> columns = new ArrayList<>();
  private String comment;
  private ConstraintDefinition uniqueConstraint;
  private String indexExpression;
  private String displayName;
  private String status;
  private String filterExpression;
  private ObjectSourceOptions sourceOptions = new ObjectSourceOptions();

  // For Oracle, Postgres
  private String tableSpace;

  private boolean autoGenerated;

  private boolean partitioned;

  // for Firebird which only supports a "global" index direction, not per column
  private String direction;

  // this is for Oracle
  private Boolean enabled;
  private Boolean validated;

  public IndexDefinition(TableIdentifier table, String name)
  {
    this.indexName = name;
    this.baseTable = table;
  }

  /**
   * Define the source options to be used.
   *
   * @param options  the new options. If null, the call is ignored
   */
  public void setSourceOptions(ObjectSourceOptions options)
  {
    if (options != null)
    {
      this.sourceOptions = options;
    }
  }

  /**
   * Returns the source options to build the table's SQL
   *
   * @return the options. Never null
   */
  public ObjectSourceOptions getSourceOptions()
  {
    return this.sourceOptions;
  }

  public boolean isPartitioned()
  {
    return partitioned;
  }

  public void setPartitioned(boolean flag)
  {
    this.partitioned = flag;
  }

  public boolean isAutoGenerated()
  {
    return autoGenerated;
  }

  public void setAutoGenerated(boolean flag)
  {
    this.autoGenerated = flag;
  }

  public String getStatus()
  {
    return status;
  }

  public void setStatus(String indexStatus)
  {
    this.status = indexStatus;
  }

  /**
   * Return the tablespace used for this table (if applicable)
   */
  public String getTablespace()
  {
    return tableSpace;
  }

  public void setTablespace(String tableSpaceName)
  {
    this.tableSpace = tableSpaceName;
  }

  public String getDirection()
  {
    return direction;
  }

  public void setDirection(String dir)
  {
    this.direction = dir;
  }

  public Boolean isEnabled()
  {
    return enabled;
  }

  public void setEnabled(Boolean flag)
  {
    this.enabled = flag;
  }

  public Boolean isValidated()
  {
    return validated;
  }

  public void setValid(Boolean status)
  {
    this.validated = status;
  }

  public String getFilterExpression()
  {
    return filterExpression;
  }

  public void setFilterExpression(String expression)
  {
    this.filterExpression = expression;
  }

  @Override
  public String getComment()
  {
    return comment;
  }

  @Override
  public void setComment(String c)
  {
    comment = c;
  }

  @Override
  public void setName(String name)
  {
    this.indexName = name;
  }

  @Override
  public void setCatalog(String idxCatalog)
  {
    this.catalog = idxCatalog;
  }

  @Override
  public void setSchema(String idxSchema)
  {
    this.schema = idxSchema;
  }

  @Override
  public String getSchema()
  {
    if (schema != null) return schema;
    return baseTable.getSchema();
  }

  @Override
  public String getCatalog()
  {
    if (catalog != null) return catalog;
    return baseTable.getCatalog();
  }

  public boolean isNonStandardExpression()
  {
    return indexExpression != null;
  }

  public String getIndexExpression()
  {
    return indexExpression;
  }

  public void setIndexExpression(String expression)
  {
    this.indexExpression = expression;
  }

  public boolean isUniqueConstraint()
  {
    return uniqueConstraint != null;
  }

  public void setUniqueConstraint(ConstraintDefinition constraint)
  {
    if (constraint != null && constraint.getConstraintType() != ConstraintType.Unique)
    {
      LogMgr.logError(new CallerInfo(){}, "setUniqueConstraint() called with a different constraint type", new IllegalArgumentException("Invalid type: " + constraint.getConstraintType()));
    }
    this.uniqueConstraint = constraint;
  }

  public String getUniqueConstraintName()
  {
    return uniqueConstraint == null ? null : uniqueConstraint.getConstraintName();
  }

  public ConstraintDefinition getUniqueConstraint()
  {
    return uniqueConstraint;
  }

  public void addColumn(String column, String direction)
  {
    this.columns.add(new IndexColumn(column, direction));
  }

  public void setIndexType(String type)
  {
    if (type == null)
    {
      this.indexType = "NORMAL";
    }
    else
    {
      this.indexType = type;
    }
  }

  @Override
  public String getFullyQualifiedName(WbConnection conn)
  {
    return SqlUtil.fullyQualifiedName(conn, this);
  }

  @Override
  public String getObjectExpression(WbConnection conn)
  {
    return SqlUtil.buildExpression(conn, null, getSchema(), indexName);
  }

  @Override
  public String getDropStatement(WbConnection con, boolean cascade)
  {
    return null;
  }

  @Override
  public String getObjectNameForDrop(WbConnection con)
  {
    if (con != null && con.getMetadata().isSqlServer())
    {
      // SQL Server does not support fully qualified names when dropping an index
      return this.indexName;
    }
    return getFullyQualifiedName(con);
  }

  @Override
  public String getObjectName(WbConnection conn)
  {
    return SqlUtil.getQuoteHandler(conn).quoteObjectname(indexName);
  }

  @Override
  public String getObjectType()
  {
    return "INDEX";
  }

  @Override
  public String getObjectName()
  {
    return getName();
  }

  public List<IndexColumn> getColumns()
  {
    if (columns == null) return Collections.emptyList();
    return columns;
  }

  public String getIndexType()
  {
    return this.indexType;
  }

  @Override
  public DbObject getOwnerObject()
  {
    return getBaseTable();
  }

  public TableIdentifier getBaseTable()
  {
    return baseTable;
  }

  public void setDisplayName(String name)
  {
    this.displayName = name;
  }

  @Override
  public String toString()
  {
    if (displayName != null) return displayName;
    return indexName;
  }

  public String getColumnList()
  {
    StringBuilder result = new StringBuilder(this.columns.size() * 10);
    for (int i=0; i < this.columns.size(); i++)
    {
      if (i > 0) result.append(", ");
      result.append(columns.get(i).getColumn());
    }
    return result.toString();
  }

  public String getExpression()
  {
    return getExpression(null);
  }

  public String getExpression(WbConnection conn)
  {
    if (indexExpression != null)
    {
      return indexExpression;
    }

    StringBuilder result = new StringBuilder(this.columns.size() * 10);
    for (int i=0; i < this.columns.size(); i++)
    {
      if (i > 0) result.append(", ");
      result.append(columns.get(i).getExpression());
    }
    return result.toString();
  }

  public String getName()
  {
    return this.indexName;
  }

  public void setPrimaryKeyIndex(boolean flag)
  {
    this.isPK = flag;
  }

  public boolean isPrimaryKeyIndex()
  {
    return this.isPK;
  }

  public void setUnique(boolean flag)
  {
    this.isUnique = flag;
  }

  public boolean isUnique()
  {
    return this.isUnique;
  }

  @Override
  public int hashCode()
  {
    int hash = 71 * 7 + (this.indexName != null ? this.indexName.hashCode() : 0);
    return hash;
  }

  @Override
  public boolean equals(Object o)
  {
    if (o instanceof IndexDefinition)
    {
      IndexDefinition other = (IndexDefinition)o;
      boolean equals = false;
      if (this.isPK && other.isPK || this.isUnique && other.isUnique)
      {
        equals = true;
        // for PK indexes the order of the columns in the index does not matter
        // so we consider the same list of columns equal even if they have a different order
        for (IndexColumn col : columns)
        {
          if (!other.columns.contains(col))
          {
            equals = false;
            break;
          }
        }
      }
      else
      {
        equals = this.columns.equals(other.columns);
        if (equals)
        {
          equals = StringUtil.equalStringOrEmpty(this.getFilterExpression(), other.getFilterExpression(), false);
        }
      }

      if (equals)
      {
        equals = (this.isPK == other.isPK) && (this.isUnique == other.isUnique);
      }
      return equals;
    }
    else if (o instanceof String)
    {
      return this.getExpression().equals((String)o);
    }
    return false;
  }

  @Override
  public CharSequence getSource(WbConnection con)
  {
    if (con == null) return null;
    IndexReader reader = con.getMetadata().getIndexReader();
    return reader.getIndexSource(baseTable, this);
  }

  public boolean isNameEqual(String schema, String indexName)
  {
    if (indexName == null) return false;
    if (SqlUtil.objectNamesAreEqual(this.getName(), indexName))
    {
      if (getSchema() != null && schema != null)
      {
        return SqlUtil.objectNamesAreEqual(getSchema(), schema);
      }
      return true;
    }
    return false;
  }

  public static IndexDefinition findIndex(Collection<IndexDefinition> indexList, String indexName, String indexSchema)
  {
    for (IndexDefinition idx : indexList)
    {
      if (idx.isNameEqual(indexSchema, indexName))
      {
        return idx;
      }
    }
    return null;
  }

  public IndexDefinition createCopy()
  {
    IndexDefinition idx = new IndexDefinition(this.baseTable.createCopy(), this.indexName);
    idx.autoGenerated = this.autoGenerated;
    idx.enabled = this.enabled;
    idx.columns = new ArrayList<>(this.columns);
    idx.comment = this.comment;
    idx.catalog = this.catalog;
    idx.direction = this.direction;
    idx.displayName = this.displayName;
    idx.indexExpression = this.indexExpression;
    idx.indexType = this.indexType;
    idx.isPK = this.isPK;
    idx.isUnique = this.isUnique;
    idx.schema = this.schema;
    idx.tableSpace = this.tableSpace;
    idx.uniqueConstraint = this.uniqueConstraint;
    idx.validated = this.validated;
    idx.partitioned = this.partitioned;
    return idx;
  }

  public static Comparator<IndexDefinition> getNameSorter()
  {
    Comparator<IndexDefinition> comp = (IndexDefinition o1, IndexDefinition o2) ->
    {
      if (o1 == null) return 1;
      if (o2 == null) return -1;
      String name1 = o1.getName();
      String name2 = o2.getName();
      return StringUtil.compareStrings(name1, name2, true);
    };
    return comp;
  }

  public boolean isEmpty()
  {
    if (columns == null) return true;
    for (IndexColumn col : columns)
    {
      if (col != null && StringUtil.isNonEmpty(col.getColumn())) return false;
    }
    return true;
  }

  @Override
  public boolean supportsGetSource()
  {
    return true;
  }

}
