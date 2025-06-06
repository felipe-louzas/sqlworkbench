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
package workbench.db.oracle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class OraclePartitionDefinition
{
  private final String type;

  /**
   * The name of the partition.
   */
  private final String name;

  /**
   * Stores the value for LIST partitions.
   */
  private String partitionValue;

  /**
   * The position of this partition.
   */
  private final int position;

  private String compressOption;

  private String subPartitionType;
  private List<OraclePartitionDefinition> subPartitions;
  private boolean isSubpartition;

  public OraclePartitionDefinition(String partitionName, String partitionType, int partitionPosition)
  {
    name = partitionName;
    position = partitionPosition;
    type = partitionType;
  }

  public boolean isSubpartition()
  {
    return isSubpartition;
  }

  public void setIsSubpartition(boolean isSubpartition)
  {
    this.isSubpartition = isSubpartition;
  }

  public List<OraclePartitionDefinition> getSubPartitions()
  {
    if (subPartitions == null) return Collections.emptyList();
    return Collections.unmodifiableList(subPartitions);
  }

  public void addSubPartition(OraclePartitionDefinition subPartition)
  {
    if (this.subPartitions == null)
    {
      this.subPartitions = new ArrayList<>();
    }
    subPartitions.add(subPartition);
  }

  public String getSubPartitionType()
  {
    return subPartitionType;
  }

  public void setSubPartitionType(String type)
  {
    this.subPartitionType = type;
  }

  /**
   * Return the partitioning strategy.
   *
   * e.g. LIST, RANGE, HASH
   */
  public String getType()
  {
    return type;
  }

  public String getCompressOption()
  {
    return compressOption;
  }

  public void setCompressOption(String compressOption)
  {
    this.compressOption = compressOption;
  }

  public String getName()
  {
    return name;
  }

  public void setPartitionValue(String partitionValue)
  {
    this.partitionValue = partitionValue;
  }

  /**
   * Return the (high) value of this partition.
   * Only applicable for LIST partitions
   */
  public String getPartitionValue()
  {
    return partitionValue;
  }

  public int getPosition()
  {
    return position;
  }

  public CharSequence getSource(boolean forTable, int nameLength, String indent)
  {
    StringBuilder result = new StringBuilder((partitionValue == null ? 15 : partitionValue.length()) + 20);
    result.append(indent);
    if (isSubpartition)
    {
      result.append("  SUBPARTITION ");
    }
    else
    {
      result.append("  PARTITION ");
    }

    result.append(StringUtil.padRight(SqlUtil.quoteObjectname(name), nameLength));
    if (partitionValue != null && forTable)
    {
      if ("RANGE".equals(type))
      {
        result.append(" VALUES LESS THAN (");
        result.append(partitionValue);
        result.append(')');
      }
      else
      {
        result.append(" VALUES (");
        result.append(partitionValue);
        result.append(')');
      }
    }

    if (compressOption != null && partitionValue != null && partitionValue.indexOf('\'') > -1)
    {
      if ("DISABLED".equals(compressOption))
      {
        result.append(" NOCOMPRESS");
      }
      if ("ENABLED".equals(compressOption))
      {
        result.append(" COMPRESS");
      }
    }

    boolean shouldAppendSubPartitions = CollectionUtil.isNonEmpty(subPartitions) && !subPartitionType.equals("HASH");
    if (shouldAppendSubPartitions)
    {
      int maxLength = getMaxPartitionNameLength(subPartitions);
      result.append("\n  ");
      result.append(indent);
      result.append("(\n");
      for (int i=0; i < subPartitions.size(); i++)
      {
        if (i > 0)
        {
          result.append(",\n");
        }
        result.append("  ");
        result.append(indent);
        result.append(subPartitions.get(i).getSource(forTable, maxLength, indent + "  "));
      }
      result.append("\n  ");
      result.append(indent);
      result.append(")");
    }
    return result;
  }

  static int getMaxPartitionNameLength(List<OraclePartitionDefinition> parts)
  {
    int maxLength = 0;
    for (OraclePartitionDefinition def : parts)
    {
      String name = SqlUtil.quoteObjectname(def.getName());
      if (name.length() > maxLength)
      {
        maxLength = name.length();
      }
    }
    return maxLength;
  }
}
