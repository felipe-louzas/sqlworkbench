/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
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

import java.io.Serializable;

import workbench.db.BaseObjectType;
import workbench.db.DbObject;
import workbench.db.WbConnection;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class PgRangeType
  extends BaseObjectType
  implements Serializable
{
  public static final String RANGE_TYPE_NAME = "RANGE TYPE";
  private String dataType;
  private String multirangeType;

  public PgRangeType(String schema, String typeName)
  {
    super(schema, typeName);
  }

  public String getMultirangeType()
  {
    return multirangeType;
  }

  public void setMultirangeType(String multirangeType)
  {
    this.multirangeType = multirangeType;
  }

  public String getDataType()
  {
    return dataType;
  }

  public void setDataType(String dataType)
  {
    this.dataType = dataType;
  }

  @Override
  public String getObjectType()
  {
    return RANGE_TYPE_NAME;
  }

  @Override
  public String getObjectExpression(WbConnection conn)
  {
    return SqlUtil.buildExpression(conn, this);
  }

  @Override
  public String toString()
  {
    return getObjectName();
  }

  @Override
  public String getDropStatement(WbConnection con, boolean cascade)
  {
    // range types aren't really called "RANGE TYPE" in Postgres,
    // so we need to construct a drop statement that uses "TYPE" instead
    String ddl = "DROP TYPE %name%";
    if (con != null)
    {
      // if a specific template was configured, use that
      ddl = con.getDbSettings().getDropDDL("TYPE", cascade);
    }
    else if (cascade)
    {
      ddl += " CASCADE";
    }
    ddl = ddl.replace("%name%", getFullyQualifiedName(con));
    return ddl;
  }

  @Override
  public String getObjectNameForDrop(WbConnection con)
  {
    return getFullyQualifiedName(con);
  }

  @Override
  public boolean isComparableWith(DbObject other)
  {
    return (other instanceof PgRangeType);
  }

  @Override
  public boolean isEqualTo(DbObject other)
  {
    if (other instanceof PgRangeType)
    {
      String otherName = other.getObjectName();
      String otherSchema = other.getSchema();
      String otherType = ((PgRangeType)other).dataType;

      return StringUtil.equalString(getObjectName(), otherName)
        && StringUtil.equalString(getSchema(), otherSchema)
        && StringUtil.equalString(dataType, otherType);
    }
    return false;
  }

}
