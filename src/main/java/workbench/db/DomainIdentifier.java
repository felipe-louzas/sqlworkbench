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

import java.io.Serializable;
import java.sql.SQLException;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class DomainIdentifier
  implements ComparableDbObject, Serializable
{
  private String catalog;
  private String schema;
  private final String domainName;
  private String objectType = "DOMAIN";
  private String remarks;
  private String dataType;
  private boolean nullable;
  private String constraintDefinition;
  private String constraintName;
  private String defaultValue;
  private String source;

  public DomainIdentifier(String dcatalog, String dschema, String name)
  {
    catalog = dcatalog;
    schema = dschema;
    domainName = name;
  }

  public void setDefaultValue(String def)
  {
    defaultValue = def;
  }

  public String getDefaultValue()
  {
    return defaultValue;
  }

  public void setDataType(String dbmsType)
  {
    dataType = dbmsType;
  }

  public String getDataType()
  {
    return dataType;
  }

  public void setNullable(boolean flag)
  {
    nullable = flag;
  }

  public boolean isNullable()
  {
    return nullable;
  }

  public void setConstraintName(String name)
  {
    this.constraintName = name;
  }

  public String getConstraintName()
  {
    return constraintName;
  }

  public void setCheckConstraint(String check)
  {
    this.constraintDefinition = check;
  }

  public String getCheckConstraint()
  {
    return constraintDefinition;
  }

  @Override
  public String getCatalog()
  {
    return catalog;
  }

  @Override
  public void setCatalog(String catalog)
  {
    this.catalog = catalog;
  }

  @Override
  public void setSchema(String schema)
  {
    this.schema = schema;
  }

  @Override
  public String getSchema()
  {
    return schema;
  }

  public void setObjectType(String type)
  {
    objectType = type;
  }

  @Override
  public String getObjectType()
  {
    return objectType;
  }

  @Override
  public String getObjectName()
  {
    return domainName;
  }

  @Override
  public String getObjectName(WbConnection conn)
  {
    return domainName;
  }

  @Override
  public String getFullyQualifiedName(WbConnection conn)
  {
    return SqlUtil.fullyQualifiedName(conn, this);
  }

  @Override
  public String getObjectExpression(WbConnection conn)
  {
    return SqlUtil.buildExpression(conn, this);
  }

  @Override
  public String toString()
  {
    return getSummary();
  }

  public String getSummary()
  {
    StringBuilder result = new StringBuilder(25);
    result.append(this.dataType);
    if (!nullable) result.append(" NOT NULL");
    if (StringUtil.isNotBlank(defaultValue)) result.append(" DEFAULT " + defaultValue);
    if (StringUtil.isNotBlank(constraintDefinition))
    {
      result.append(' ');
      result.append(constraintDefinition);
    }
    result.append(';');
    return result.toString();
  }

  public String getSource()
  {
    return source;
  }

  public void setSource(String sql)
  {
    source = sql;
  }

  @Override
  public CharSequence getSource(WbConnection con)
    throws SQLException
  {
    if (StringUtil.isBlank(source))
    {
      source = con.getMetadata().getObjectSource(this);
    }
    return source;
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
  public boolean isComparableWith(DbObject other)
  {
    return (other instanceof DomainIdentifier);
  }

  @Override
  public boolean isEqualTo(DbObject other)
  {
    if (other instanceof DomainIdentifier)
    {
      DomainIdentifier dom = (DomainIdentifier)other;
      if (!StringUtil.equalStringOrEmpty(dataType, dom.dataType)) return false;
      if (nullable != dom.nullable) return false;
      if (!StringUtil.equalStringOrEmpty(defaultValue, dom.defaultValue)) return false;
      if (!StringUtil.equalStringOrEmpty(constraintDefinition, dom.constraintDefinition)) return false;
      if (!StringUtil.equalStringOrEmpty(constraintName, dom.constraintName)) return false;
      if (!StringUtil.equalStringOrEmpty(remarks, dom.remarks)) return false;
      return true;
    }
    return false;
  }

  @Override
  public boolean supportsGetSource()
  {
    return true;
  }

}
