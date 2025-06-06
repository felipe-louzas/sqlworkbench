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
package workbench.db.firebird;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.DbMetadata;
import workbench.db.DbObject;
import workbench.db.DomainIdentifier;
import workbench.db.JdbcUtils;
import workbench.db.ObjectListDataStore;
import workbench.db.ObjectListExtender;
import workbench.db.WbConnection;

import workbench.storage.DataStore;

import workbench.util.CollectionUtil;
import workbench.util.StringUtil;

/**
 * A class to read information about defined DOMAINs in Firebird.
 *
 * @author Thomas Kellerer
 */
public class FirebirdDomainReader
  implements ObjectListExtender
{
  final String baseSql =
    "SELECT trim(rdb$field_name) AS domain_name, \n" +
    "       rdb$validation_source as constraint_definition, \n" +
    "       case rdb$field_type  \n" +
    "         when 261 then 'BLOB' \n" +
    "         when 14 then 'CHAR' \n" +
    "         when 40 then 'CSTRING' \n" +
    "         when 11 then 'D_FLOAT' \n" +
    "         when 27 then 'DOUBLE' \n" +
    "         when 10 then 'FLOAT' \n" +
    "         when 16 then 'BIGINT' \n" +
    "         when 8 then 'INTEGER' \n" +
    "         when 9 then 'QUAD' \n" +
    "         when 7 then 'SMALLINT' \n" +
    "         when 12 then 'DATE' \n" +
    "         when 35 then 'TIMESTAMP' \n" +
    "         when 3 then 'DATE' \n" +
    "         when 37 then 'VARCHAR' \n" +
    "         else 'UNKNOWN' \n" +
    "       end as data_type, \n" +
    "       rdb$default_source as default_value, \n" +
    "       case rdb$null_flag \n" +
    "         when 1 then 1 \n" +
    "         else 0 \n" +
    "       end as nullable \n" +
    "FROM rdb$fields \n" +
    "WHERE rdb$field_name NOT LIKE 'RDB$%' \n" +
    "  AND rdb$field_name NOT LIKE 'SEC$%' \n" +
    "  AND rdb$field_name NOT LIKE 'MON$%'"; // for Firebird 3.0+

  @Override
  public boolean isDerivedType()
  {
    return false;
  }

  private String getSql(WbConnection connection, String name)
  {
    StringBuilder sql = new StringBuilder(baseSql.length() + 40);

    sql.append(baseSql);

    if (StringUtil.isNotBlank(name))
    {
      sql.append(" AND trim(rdb$field_name) ");
      if (name.indexOf('%') == -1)
      {
        sql.append('=');
      }
      else
      {
        sql.append("LIKE");
      }
      sql.append(" '");
      sql.append(connection.getMetadata().quoteObjectname(name));
      sql.append("' ");
    }

    sql.append(" ORDER BY 1 ");

    LogMgr.logMetadataSql(new CallerInfo(){}, "domain list", sql);

    return sql.toString();
  }

  public List<DomainIdentifier> getDomainList(WbConnection connection, String schemaPattern, String namePattern)
  {
    Statement stmt = null;
    ResultSet rs = null;
    Savepoint sp = null;
    List<DomainIdentifier> result = new ArrayList<>();
    String sql = null;
    try
    {
      sp = connection.setSavepoint();
      stmt = connection.createStatementForQuery();
      sql = getSql(connection, namePattern);
      rs = stmt.executeQuery(sql);
      while (rs.next())
      {
        String name = rs.getString("domain_name");
        DomainIdentifier domain = new DomainIdentifier(null, null, name);
        String check = rs.getString("constraint_definition");
        if (check != null) check = check.trim();
        domain.setCheckConstraint(check);
        String type = rs.getString("data_type");
        if (type != null) type = type.trim();
        domain.setDataType(type);
        int nullFlag = rs.getInt("nullable");
        domain.setNullable(nullFlag == 0);
        domain.setDefaultValue(rs.getString("default_value"));
        result.add(domain);
      }
      connection.releaseSavepoint(sp);
    }
    catch (SQLException e)
    {
      connection.rollback(sp);
      LogMgr.logMetadataError(new CallerInfo(){}, e, "domain list", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return result;
  }

  @Override
  public DomainIdentifier getObjectDefinition(WbConnection connection, DbObject object)
  {
    List<DomainIdentifier> domains = getDomainList(connection, object.getSchema(), object.getObjectName());
    if (CollectionUtil.isEmpty(domains)) return null;
    return domains.get(0);
  }

  public String getDomainSource(DomainIdentifier domain)
  {
    if (domain == null) return null;
    StringBuilder result = new StringBuilder(50);
    result.append("CREATE DOMAIN ");
    result.append(domain.getObjectName());
    result.append(" AS ");
    result.append(domain.getDataType());
    if (domain.getDefaultValue() != null)
    {
      result.append("\n  ");
      result.append(domain.getDefaultValue());
    }
    if (StringUtil.isNotBlank(domain.getCheckConstraint()) || !domain.isNullable())
    {
      result.append("\n  ");
      if (!domain.isNullable()) result.append("NOT NULL");
      if (StringUtil.isNotBlank(domain.getCheckConstraint()))
      {
        if (!domain.isNullable()) result.append(' ');
        result.append(domain.getCheckConstraint());
      }
    }
    result.append(";\n");
    return result.toString();
  }

  @Override
  public boolean extendObjectList(WbConnection con, ObjectListDataStore result, String catalog, String schema, String objects, String[] requestedTypes)
  {
    if (!DbMetadata.typeIncluded("DOMAIN", requestedTypes)) return false;

    List<DomainIdentifier> domains = getDomainList(con, schema, objects);
    if (domains.isEmpty()) return false;

    for (DomainIdentifier domain : domains)
    {
      result.addDbObject(domain);
    }
    return true;
  }

  @Override
  public boolean handlesType(String type)
  {
    return StringUtil.equalStringIgnoreCase("DOMAIN", type);
  }

  @Override
  public DataStore getObjectDetails(WbConnection con, DbObject object)
  {
    if (object == null) return null;
    if (!handlesType(object.getObjectType())) return null;

    DomainIdentifier domain = getObjectDefinition(con, object);
    if (domain == null) return null;

    String[] columns = new String[] { "DOMAIN", "DATA_TYPE", "NULLABLE", "CONSTRAINT", ObjectListDataStore.RESULT_COL_REMARKS };
    int[] types = new int[] { Types.VARCHAR, Types.VARCHAR, Types.BOOLEAN, Types.VARCHAR, Types.VARCHAR };
    int[] sizes = new int[] { 20, 10, 5, 30, 30 };
    DataStore result = new DataStore(columns, types, sizes);
    result.addRow();
    result.setValue(0, 0, domain.getObjectName());
    result.setValue(0, 1, domain.getDataType().trim());
    result.setValue(0, 2, domain.isNullable());
    result.setValue(0, 3, domain.getCheckConstraint());
    result.setValue(0, 4, domain.getComment());

    return result;
  }

  @Override
  public List<String> supportedTypes()
  {
    return CollectionUtil.arrayList("DOMAIN");
  }

  @Override
  public String getObjectSource(WbConnection con, DbObject object)
  {
    return getDomainSource(getObjectDefinition(con, object));
  }

  @Override
  public List<ColumnIdentifier> getColumns(WbConnection con, DbObject object)
  {
    return null;
  }

  @Override
  public boolean hasColumns()
  {
    return false;
  }

}
