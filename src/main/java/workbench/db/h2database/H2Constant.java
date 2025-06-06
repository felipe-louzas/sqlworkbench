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
package workbench.db.h2database;

import java.io.Serializable;
import java.sql.SQLException;

import workbench.db.DbObject;
import workbench.db.WbConnection;

import workbench.util.SqlUtil;

/**
 * A class representing a CONSTANT in H2 Database
 *
 * @author Thomas Kellerer
 */
public class H2Constant
  implements DbObject, Serializable
{
  private final String catalog;
  private final String schema;
  private final String constantName;
  private final String objectType = "CONSTANT";
  private String remarks;
  private String value;
  private String dataType;

  public H2Constant(String dcatalog, String dschema, String name)
  {
    catalog = dcatalog;
    schema = dschema;
    constantName = name;
  }

  public void setDataType(String dbmsType)
  {
    dataType = dbmsType;
  }

  public String getDataType()
  {
    return dataType;
  }

  public void setValue(String constantValue)
  {
    value = constantValue;
  }

  public String getValue()
  {
    return value;
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
    return objectType;
  }

  @Override
  public String getObjectName()
  {
    return constantName;
  }

  @Override
  public String getObjectName(WbConnection conn)
  {
    return constantName;
  }

  @Override
  public String getFullyQualifiedName(WbConnection conn)
  {
    return SqlUtil.buildExpression(conn, catalog, schema, constantName);
  }

  @Override
  public String getObjectExpression(WbConnection conn)
  {
    return constantName;
  }

  @Override
  public String toString()
  {
    return getObjectName();
  }

  @Override
  public CharSequence getSource(WbConnection con)
    throws SQLException
  {
    return con.getMetadata().getObjectSource(this);
  }

  @Override
  public String getDropStatement(WbConnection con, boolean cascade)
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
    return remarks;
  }

  @Override
  public void setComment(String cmt)
  {
    remarks = cmt;
  }

  @Override
  public boolean supportsGetSource()
  {
    return true;
  }

}
