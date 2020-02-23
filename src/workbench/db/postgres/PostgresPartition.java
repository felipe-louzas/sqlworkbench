/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2017 Thomas Kellerer.
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
package workbench.db.postgres;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import workbench.db.DbObject;
import workbench.db.PartitionLister;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class PostgresPartition
  implements DbObject
{
  private final String name;
  private final String schema;
  private String definition;
  private String subPartitionDefinition;
  private String subPartitionStrategy;
  private String comment;

  // for sub-partitions
  private TableIdentifier parentPartition;

  // The table to which this partition belongs to
  private final TableIdentifier baseTable;

  private List<PostgresPartition> subPartitions;

  public PostgresPartition(TableIdentifier baseTable, String partitionSchema, String partitionName)
  {
    this.baseTable = baseTable;
    this.name = partitionName;
    this.schema = partitionSchema;
  }

  public String getSubPartitionStrategy()
  {
    return subPartitionStrategy;
  }

  public void setSubPartitionStrategy(String subPartitionStrategy)
  {
    this.subPartitionStrategy = subPartitionStrategy;
  }

  /**
   * Return the partition strategy and definition for a sub-partition.
   */
  public String getSubPartitionDefinition()
  {
    return subPartitionDefinition;
  }

  /**
   * Set the partition strategy and definition for a sub-partition.
   * @param subPartitionDefinition
   */
  public void setSubPartitionDefinition(String subPartitionDefinition)
  {
    this.subPartitionDefinition = subPartitionDefinition;
  }

  public void setSubPartitions(List<PostgresPartition> partitions)
  {
    if (CollectionUtil.isEmpty(partitions))
    {
      this.subPartitions = null;
    }
    else
    {
      this.subPartitions = new ArrayList<>(partitions);
    }
  }

  /**
   * The List of sub-partitions is used in the DbTree.
   *
   * @see PostgresPartitionLister#getPartitions(TableIdentifier)
   */
  public List<PostgresPartition> getSubPartitions()
  {
    if (this.subPartitions == null) return null;
    return Collections.unmodifiableList(subPartitions);
  }
  
  @Override
  public String getCatalog()
  {
    return null;
  }

  @Override
  public String getObjectType()
  {
    return PartitionLister.PARTITION_TYPE_NAME;
  }

  @Override
  public String getObjectName()
  {
    return getName();
  }

  @Override
  public String getObjectName(WbConnection conn)
  {
    return SqlUtil.quoteObjectname(name);
  }

  @Override
  public String getObjectExpression(WbConnection conn)
  {
    return new TableIdentifier(null, schema, name).getObjectExpression(conn);
  }

  @Override
  public String getFullyQualifiedName(WbConnection conn)
  {
    return SqlUtil.fullyQualifiedName(conn, this);
  }

  @Override
  public CharSequence getSource(WbConnection con)
    throws SQLException
  {
    return PostgresPartitionReader.generatePartitionDDL(this, this.baseTable.getTableExpression(con), con);
  }

  @Override
  public String getObjectNameForDrop(WbConnection con)
  {
    return getFullyQualifiedName(con);
  }

  @Override
  public String getComment()
  {
    return comment;
  }

  @Override
  public void setComment(String remarks)
  {
    this.comment = remarks;
  }

  @Override
  public String getDropStatement(WbConnection con, boolean cascade)
  {
    TableIdentifier tbl = new TableIdentifier(null, schema, name);
    return tbl.getDropStatement(con, cascade);
  }

  @Override
  public boolean supportsGetSource()
  {
    return true;
  }

  public boolean isSubPartition()
  {
    return parentPartition != null;
  }

  /**
   * If this is a sub-partition, this returns the table name of the parent partition.
   */
  public TableIdentifier getParentTable()
  {
    return parentPartition;
  }

  public void setParentTable(TableIdentifier parentTable)
  {
    this.parentPartition = parentTable;
  }

  public void setDefinition(String partitionDefinition)
  {
    this.definition = partitionDefinition;
  }

  public String getDefinition()
  {
    return definition;
  }

  public String getName()
  {
    return name;
  }

  @Override
  public String getSchema()
  {
    return schema;
  }

}
