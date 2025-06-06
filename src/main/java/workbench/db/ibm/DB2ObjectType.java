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
package workbench.db.ibm;

import java.io.Serializable;

import workbench.db.BaseObjectType;
import workbench.db.WbConnection;

/**
 *
 * @author Thomas Kellerer
 */
public class DB2ObjectType
  extends BaseObjectType
  implements Serializable
{
  private String baseType;
  private MetaType metaType;
  private int arrayLength;

  public DB2ObjectType(String schema, String objectName)
  {
    super(schema, objectName);
  }

  public MetaType getMetaType()
  {
    return metaType;
  }

  public int getArrayLength()
  {
    return arrayLength;
  }

  public void setArrayLength(int length)
  {
    this.arrayLength = length;
  }

  public void setMetaType(String meta)
  {
    // https://www-01.ibm.com/support/knowledgecenter/SSEPGG_9.7.0/com.ibm.db2.luw.sql.ref.doc/doc/r0001040.html?cp=SSEPGG_9.7.0%2F5-5-7-26&lang=en
    //    A = User-defined array type
    //    C = User-defined cursor type
    //    F = User-defined row type
    //    L = User-defined associative array type
    //    R = User-defined structured type
    //    S = System predefined type
    //    T = User-defined distinct type

    if (meta == null) return;
    if ("A".equals(meta))
    {
      this.metaType = MetaType.array;
    }
    else if ("T".equals(meta))
    {
      this.metaType = MetaType.distinct;
    }
    else if ("F".equals(meta))
    {
      this.metaType = MetaType.row;
    }
    else
    {
      this.metaType = MetaType.structured;
    }
  }

  public void setMetaType(MetaType type)
  {
    this.metaType = type;
  }

  public String getBaseType()
  {
    return baseType;
  }

  public void setBaseType(String baseType)
  {
    this.baseType = baseType;
  }

  @Override
  public String getDropStatement(WbConnection con, boolean cascade)
  {
    return "DROP TYPE " + getSchema() + "." + getObjectName();
  }

  public static enum MetaType
  {
    distinct,
    array,
    cursor,
    row,
    structured;
  }
}
