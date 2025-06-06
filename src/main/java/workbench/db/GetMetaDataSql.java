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

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Collections;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.util.SqlParsingUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class GetMetaDataSql
{
  private String baseSql;
  private String schema;
  private String schemaField;
  private String catalog;
  private String catalogField;
  private String objectName;
  private String objectNameField;
  private String orderBy;
  private boolean useUpperCase;
  private boolean useLowerCase;
  private boolean isProcedureCall;
  private int schemaArgumentPos;
  private int catalogArgumentPos;
  private int objectNameArgumentPos;

  private String baseObjectName;
  private String baseObjectCatalog;
  private String baseObjectSchema;

  private String baseObjectNameField;
  private String baseObjectCatalogField;
  private String baseObjectSchemaField;

  // for stored procedure retrieval
  private String specificNameColumn;
  private String specificName;

  // for stored procedure retrieval
  private String internalIdColumn;
  private Object internalId;

  private String metaDataType;
  private boolean isPreparedStatement;

  public void setMetaDataType(String type)
  {
    this.metaDataType = type;
  }

  public String getSql()
  {
    if (this.isProcedureCall) return this.getProcedureCallSql();
    else return this.getSelectSql();
  }

  private String getSelectSql()
  {
    boolean containsWhere = containsWhere(baseSql);
    boolean needsAnd = containsWhere;
    boolean needsWhere = !containsWhere;
    StringBuilder sql = new StringBuilder(baseSql);

    if (schema != null && schemaField != null)
    {
      if (needsWhere)
      {
        sql.append(" WHERE ");
        needsWhere = false;
      }

      if (needsAnd) sql.append(" AND ");
      sql.append(schemaField + getOperator(schema) + "'" + getNameValue(schema) + "'");
      needsAnd = true;
    }

    if (catalog != null && catalogField != null)
    {
      if (needsWhere)
      {
        sql.append(" WHERE ");
        needsWhere = false;
      }
      if (needsAnd) sql.append(" AND ");
      sql.append(catalogField + getOperator(catalog) + "'" + getNameValue(catalog) + "'");
      needsAnd = true;
    }

    if (objectName != null && objectNameField != null)
    {
      if (needsWhere)
      {
        sql.append(" WHERE ");
        needsWhere = false;
      }
      if (needsAnd) sql.append(" AND ");
      sql.append(objectNameField +  getOperator(objectName) + "'" + getNameValue(objectName) + "'");
      needsAnd = true;
    }

    if (specificName != null && specificNameColumn != null)
    {
      if (needsWhere)
      {
        sql.append(" WHERE ");
        needsWhere = false;
      }
      if (needsAnd) sql.append(" AND ");
      sql.append(specificNameColumn +  getOperator(specificName) + "'" + getNameValue(specificName) + "'");
      needsAnd = true;
    }

    if (internalId != null && internalIdColumn != null)
    {
      if (needsWhere)
      {
        sql.append(" WHERE ");
        needsWhere = false;
      }
      if (needsAnd) sql.append(" AND ");
      String expr = null;
      if (internalId instanceof String)
      {
        expr = "'" + internalId + "'";
      }
      else
      {
        expr = internalId.toString();
      }
      sql.append(internalIdColumn +  " = " + expr);
      needsAnd = true;
    }

    if (baseObjectName != null && baseObjectNameField != null)
    {
      sql.append(" AND ");
      sql.append(baseObjectNameField +  getOperator(baseObjectName) + "'" + getNameValue(baseObjectName ) + "'");
    }

    if (baseObjectCatalog != null && baseObjectCatalogField != null)
    {
      sql.append(" AND ");
      sql.append(baseObjectCatalogField + getOperator(baseObjectCatalog) + "'" + getNameValue(baseObjectCatalog) + "'");
    }

    if (baseObjectSchema != null && baseObjectSchemaField != null)
    {
      sql.append(" AND ");
      sql.append(baseObjectSchemaField + getOperator(baseObjectSchema) + "'" + getNameValue(baseObjectSchema) + "'");
    }

    if (this.orderBy != null)
    {
      sql.append(" " + this.orderBy);
    }
    return sql.toString();
  }

  private String getOperator(String inputValue)
  {
    if (inputValue == null) return "";
    if (inputValue.indexOf('%') > -1)
    {
      return " LIKE ";
    }
    return " = ";
  }


  private String getNameValue(String value)
  {
    if (value == null) return null;
    if (useLowerCase) return value.toLowerCase();
    if (useUpperCase) return value.toUpperCase();
    return value;
  }

  private String getProcedureCallSql()
  {
    StringBuilder sql = new StringBuilder(this.baseSql);
    sql.append(' ');
    for (int i = 1; i < 4; i++)
    {
      if (schemaArgumentPos == i && this.schema != null)
      {
        if (i > 1) sql.append(',');
        sql.append(this.schema);
      }
      else if (catalogArgumentPos == i && this.catalog != null)
      {
        if (i > 1) sql.append(',');
        sql.append(this.catalog);
      }
      else if (this.objectNameArgumentPos == i && this.objectName != null)
      {
        if (i > 1) sql.append(',');
        sql.append(this.objectName);
      }
    }
    return sql.toString();
  }

  public String getInternalIdColumn()
  {
    return internalIdColumn;
  }

  public void setInternalIdColumn(String columnName)
  {
    this.internalIdColumn = StringUtil.trimToNull(columnName);
  }

  public Object getInternalId()
  {
    return internalId;
  }

  public void setInternalId(Object id)
  {
    this.internalId = id;
  }


  public String getBaseSql()
  {
    return baseSql;
  }

  public void setBaseSql(String sql)
  {
    this.baseSql = StringUtil.trimToNull(sql);
  }

  public String getSchema()
  {
    return schema;
  }

  public void setSchema(String schema)
  {
    this.schema = StringUtil.trimToNull(schema);
  }

  public String getCatalog()
  {
    return catalog;
  }

  public void setCatalog(String cat)
  {
    this.catalog = StringUtil.trimToNull(cat);
  }

  public String getObjectName()
  {
    return objectName;
  }

  public void setObjectName(String name)
  {
    this.objectName = name;
  }

  @Override
  public String toString()
  {
    return getSql();
  }

  public String getSchemaField()
  {
    return schemaField;
  }

  public void setSchemaField(String field)
  {
    this.schemaField = StringUtil.trimToNull(field);
  }

  public String getCatalogField()
  {
    return catalogField;
  }

  public void setCatalogField(String field)
  {
    this.catalogField = StringUtil.trimToNull(field);
  }

  public String getObjectNameField()
  {
    return objectNameField;
  }

  public void setObjectNameField(String field)
  {
    this.objectNameField = StringUtil.trimToNull(field);
  }

  public String getOrderBy()
  {
    return orderBy;
  }

  public void setOrderBy(String order)
  {
    this.orderBy = order;
  }

  public boolean getUseUpperCase()
  {
    return useUpperCase;
  }

  public void setUseUpperCase(boolean upperCase)
  {
    this.useUpperCase = upperCase;
  }

  public boolean getUseLowerCase()
  {
    return useLowerCase;
  }

  public void setUseLowerCase(boolean lowerCase)
  {
    this.useLowerCase = lowerCase;
  }

  public boolean isIsProcedureCall()
  {
    return isProcedureCall;
  }

  public void setIsProcedureCall(boolean isCall)
  {
    this.isProcedureCall = isCall;
  }

  public int getSchemaArgumentPos()
  {
    return schemaArgumentPos;
  }

  public void setSchemaArgumentPos(int pos)
  {
    this.schemaArgumentPos = pos;
  }

  public int getCatalogArgumentPos()
  {
    return catalogArgumentPos;
  }

  public void setCatalogArgumentPos(int pos)
  {
    this.catalogArgumentPos = pos;
  }

  public int getObjectNameArgumentPos()
  {
    return objectNameArgumentPos;
  }

  public void setObjectNameArgumentPos(int pos)
  {
    this.objectNameArgumentPos = pos;
  }

  public int getBaseObjectNameArgumentPos()
  {
    return objectNameArgumentPos;
  }

  public void setBaseObjectNameArgumentPos(int pos)
  {
    this.objectNameArgumentPos = pos;
  }

  public String getBaseObjectCatalog()
  {
    return baseObjectCatalog;
  }

  public void setBaseObjectCatalog(String baseObjectCatalog)
  {
    this.baseObjectCatalog = StringUtil.trimToNull(baseObjectCatalog);
  }

  public String getBaseObjectCatalogField()
  {
    return baseObjectCatalogField;
  }

  public void setBaseObjectCatalogField(String baseObjectCatalogField)
  {
    this.baseObjectCatalogField = StringUtil.trimToNull(baseObjectCatalogField);
  }

  public String getBaseObjectName()
  {
    return baseObjectName;
  }

  public void setBaseObjectName(String baseObjectName)
  {
    this.baseObjectName = StringUtil.trimToNull(baseObjectName);
  }

  public String getBaseObjectNameField()
  {
    return baseObjectNameField;
  }

  public void setBaseObjectNameField(String baseObjectNameField)
  {
    this.baseObjectNameField = StringUtil.trimToNull(baseObjectNameField);
  }

  public String getBaseObjectSchema()
  {
    return baseObjectSchema;
  }

  public void setBaseObjectSchema(String baseObjectSchema)
  {
    this.baseObjectSchema = StringUtil.trimToNull(baseObjectSchema);
  }

  public String getBaseObjectSchemaField()
  {
    return baseObjectSchemaField;
  }

  public void setBaseObjectSchemaField(String baseObjectSchemaField)
  {
    this.baseObjectSchemaField = StringUtil.trimToNull(baseObjectSchemaField);
  }

  public String getSpecificNameColumn()
  {
    return specificNameColumn;
  }

  public void setSpecificNameColumn(String column)
  {
    this.specificNameColumn = column;
  }

  public String getSpecificName()
  {
    return specificName;
  }

  public void setSpecificName(String name)
  {
    this.specificName = name;
  }

  public boolean isPreparedStatement()
  {
    return isPreparedStatement;
  }

  public void setIsPreparedStatement(boolean flag)
  {
    this.isPreparedStatement = flag;
  }

  public PreparedStatement prepareStatement(WbConnection conn, String catalog, String schema, String name)
    throws SQLException
  {
    if (!isPreparedStatement) return null;

    PreparedStatement pstmt = conn.getSqlConnection().prepareStatement(baseSql);
    int schemaPos = getSchemaArgumentPos();
    int catalogPos = getCatalogArgumentPos();
    int namePos = getObjectNameArgumentPos();
    String params = "";
    if (namePos > 0)
    {
      pstmt.setString(namePos, name);
      params = "Parameter " + namePos + ": '" + name + "'";
    }
    if (schemaPos > 0 && StringUtil.isNotEmpty(schema))
    {
      pstmt.setString(schemaPos, schema);
      params += ", Parameter " + schemaPos + ": '" + schema + "'";
    }
    if (catalogPos > 0 && StringUtil.isNotEmpty(catalog))
    {
      pstmt.setString(catalogPos, catalog);
      params += ", Parameter " + catalogPos + ": '" + catalog + "'";
    }
    if (Settings.getInstance().getDebugMetadataSql())
    {
      LogMgr.logInfo(new CallerInfo(){}, "Retrieving " + metaDataType + " using query=\n" + baseSql + "\n(" + params + ")");
    }
    return pstmt;
  }

  boolean containsWhere(String sql)
  {
    if (sql == null) return false;
    sql = sql.toLowerCase();
    if (!sql.contains("where")) return false;
    SqlParsingUtil util = SqlParsingUtil.getInstance(null);
    int fromPos = util.getFromPosition(sql);
    if (fromPos == -1) return false;
    int wherePos = util.getKeywordPosition(Collections.singleton("WHERE"), sql, fromPos);
    return wherePos > -1;
  }
}
