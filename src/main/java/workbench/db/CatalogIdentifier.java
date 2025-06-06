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
package workbench.db;

import java.sql.SQLException;
import java.util.Objects;

import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class CatalogIdentifier
  implements DbObject
{
  private String catalogName;
  private String typeName = "CATALOG";

  public CatalogIdentifier(String name)
  {
    catalogName = name;
  }

  @Override
  public void setCatalog(String catalog)
  {
    this.catalogName = catalog;
  }

  public void setTypeName(String type)
  {
    if (StringUtil.isNotBlank(type))
    {
      typeName = type.trim().toUpperCase();
    }
  }

  @Override
  public String getCatalog()
  {
    return catalogName;
  }

  @Override
  public String getSchema()
  {
    return null;
  }

  @Override
  public String getObjectType()
  {
    return typeName;
  }

  @Override
  public String getObjectName()
  {
    return catalogName;
  }

  @Override
  public void setName(String name)
  {
    catalogName = name;
  }

  @Override
  public String getObjectName(WbConnection conn)
  {
    return conn.getMetadata().quoteObjectname(catalogName);
  }

  @Override
  public String getObjectExpression(WbConnection conn)
  {
    return getObjectName(conn);
  }

  @Override
  public String getFullyQualifiedName(WbConnection conn)
  {
    return getObjectName(conn);
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
    return getObjectName(con);
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
    if (con == null) return null;
    if (DBID.SQL_Server.isDB(con) && this.catalogName != null)
    {
      return
        "use master;\n" +
        "drop schema " + con.getMetadata().quoteObjectname(catalogName) + ";";
    }
    return null;
  }

  @Override
  public int hashCode()
  {
    int hash = 3;
    hash = 53 * hash + Objects.hashCode(this.catalogName);
    return hash;
  }

  @Override
  public boolean equals(Object obj)
  {
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    final CatalogIdentifier other = (CatalogIdentifier)obj;
    if (!Objects.equals(this.catalogName, other.catalogName)) return false;
    return true;
  }

  @Override
  public String toString()
  {
    return catalogName;
  }

  @Override
  public boolean supportsGetSource()
  {
    return false;
  }

}
