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
package workbench.db.derby;

import java.io.Serializable;
import java.sql.SQLException;

import workbench.db.DbObject;
import workbench.db.WbConnection;

/**
 *
 * @author Thomas Kellerer
 */
public class DerbyTypeDefinition
  implements DbObject, Serializable
{

  private String typeName;
  private String typeSchema;
  private String javaClassname;
  private String aliasInfo;

  public DerbyTypeDefinition(String schema, String name, String className, String info)
  {
    typeSchema = schema;
    typeName = name;
    javaClassname = className;
    aliasInfo = info;
  }

  @Override
  public String getCatalog()
  {
    return null;
  }

  @Override
  public String getSchema()
  {
    return typeSchema;
  }

  @Override
  public String getObjectType()
  {
    return "TYPE";
  }

  @Override
  public void setName(String name)
  {
    this.typeName = name;
  }

  @Override
  public String getObjectName()
  {
    return typeName;
  }

  @Override
  public String getObjectName(WbConnection conn)
  {
    return typeName;
  }

  @Override
  public String getObjectExpression(WbConnection conn)
  {
    return typeSchema + "." + typeName;
  }

  @Override
  public String getFullyQualifiedName(WbConnection conn)
  {
    return typeSchema + "." + typeName;
  }

  @Override
  public CharSequence getSource(WbConnection con)
    throws SQLException
  {
    StringBuilder sql = new StringBuilder(100);
    sql.append("CREATE TYPE ");
    sql.append(typeSchema);
    sql.append('.');
    sql.append(typeName);
    sql.append("\n  EXTERNAL NAME '");
    sql.append(javaClassname);
    sql.append("' \n  ");
    sql.append(aliasInfo);
    sql.append(";\n");
    return sql;
  }

  @Override
  public String getObjectNameForDrop(WbConnection con)
  {
    return getFullyQualifiedName(con);
  }

  @Override
  public String getComment()
  {
    return null;
  }

  @Override
  public void setComment(String cmt)
  {
  }

  @Override
  public String getDropStatement(WbConnection con, boolean cascade)
  {
    return null;
  }

  @Override
  public boolean supportsGetSource()
  {
    return true;
  }

}
