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
package workbench.db;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import workbench.util.SqlUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class TablePartition
  implements DbObject
{
  private String catalog;
  private String schema;
  private String partitionName;
  private String comment;
  boolean isSubPartition;
  private List<TablePartition> subPartitions;

  public void setCatalog(String catalog)
  {
    this.catalog = catalog;
  }

  public void setSchema(String schema)
  {
    this.schema = schema;
  }

  public void setPartitionName(String partitionName)
  {
    this.partitionName = partitionName;
  }

  public void setComments(String comments)
  {
    this.comment = comments;
  }

  public void setIsSubPartition(boolean isSubPartition)
  {
    this.isSubPartition = isSubPartition;
  }

  public boolean isSubPartition()
  {
    return isSubPartition;
  }

  public void setSubPartitions(List<TablePartition> partitions)
  {
    if (partitions == null)
    {
      this.subPartitions = null;
    }
    else
    {
      this.subPartitions = new ArrayList<>(partitions);
    }
  }

  public List<TablePartition> getSubPartitions()
  {
    if (subPartitions == null) return null;
    return Collections.unmodifiableList(subPartitions);
  }


  @Override
  public String getCatalog()
  {
    return catalog;
  }

  @Override
  public String getSchema()
  {
    return schema;
  }

  @Override
  public String getObjectType()
  {
    return PartitionLister.PARTITION_TYPE_NAME;
  }

  @Override
  public String getObjectName()
  {
    return partitionName;
  }

  @Override
  public String getObjectName(WbConnection conn)
  {
    if (conn == null) return SqlUtil.quoteObjectname(this.partitionName);
    return conn.getMetadata().quoteObjectname(this.partitionName);
  }

  @Override
  public String getObjectExpression(WbConnection conn)
  {
    return new TableIdentifier(catalog, schema, partitionName).getObjectExpression(conn);
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
    return null;
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
  public void setComment(String cmt)
  {
    this.comment = cmt;
  }

  @Override
  public String getDropStatement(WbConnection con, boolean cascade)
  {
    return null;
  }

  @Override
  public boolean supportsGetSource()
  {
    return false;
  }

}
