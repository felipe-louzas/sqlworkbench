/*
 * This file is part of SQL Workbench/J, http://www.sql-workbench.net
 *
 * Copyright 2002-2013, Thomas Kellerer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at.
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.net
 *
 */
package workbench.storage.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import workbench.resource.Settings;

import workbench.db.ColumnIdentifier;
import workbench.db.DbSettings;
import workbench.db.MetaDataSqlManager;
import workbench.db.WbConnection;
import workbench.db.sqltemplates.TemplateHandler;

import workbench.storage.ColumnData;
import workbench.storage.SqlLiteralFormatter;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class SQLFilterGenerator
{
  private final SqlLiteralFormatter literalFormatter;
  private final DbSettings dbSettings;

  public SQLFilterGenerator(WbConnection conn)
  {
    this.dbSettings = conn.getDbSettings();
    this.literalFormatter = new SqlLiteralFormatter(conn);
  }

  public SQLFilterGenerator(SqlLiteralFormatter formatter, DbSettings dbSettings)
  {
    this.dbSettings = dbSettings;
    this.literalFormatter = formatter;
  }

  public String getSQLConditionForAll(ColumnComparator comp, List<ColumnIdentifier> columns, Object comparisonValue, boolean ignoreCase)
  {
    List<String> conditions = new ArrayList<>();
    for (ColumnIdentifier column : columns)
    {
      String template = getSQLTemplate(comp, ignoreCase);
      if (template == null) continue;

      String columnName = SqlUtil.quoteObjectname(column.getColumnName());
      if (!SqlUtil.isCharacterType(column.getDataType()))
      {
        columnName = getCastExpression(column.getDbmsType(), columnName);
      }
      String expression = getSQLCondition(comp, column, columnName, comparisonValue, ignoreCase);
      if (StringUtil.isNotBlank(expression))
      {
        conditions.add(expression);
      }
    }
    String sql = conditions.stream().collect(Collectors.joining(" OR\n  "));
    return "(\n  " + sql + "\n)";
  }

  public String getSQLCondition(ColumnComparator comp, ColumnIdentifier column, Object comparisonValue, boolean ignoreCase)
  {
    if (column == null || comp == null) return null;
    String columnName = SqlUtil.quoteObjectname(column.getColumnName());
    return getSQLCondition(comp, column, columnName, comparisonValue, ignoreCase);
  }

  public String getSQLCondition(ColumnComparator comp, ColumnIdentifier column, String columnExpression, Object comparisonValue, boolean ignoreCase)
  {
    if (column == null || comp == null) return null;

    String template = getSQLTemplate(comp, comp.supportsIgnoreCase() ? ignoreCase : false);
    if (StringUtil.isBlank(template))
    {
      return null;
    }
    String sql = TemplateHandler.replacePlaceholder(template, MetaDataSqlManager.COLUMN_NAME_PLACEHOLDER, columnExpression, false);
    ColumnData data = new ColumnData(comparisonValue, column);
    String value = comp.getSQLLiteral(data, literalFormatter);
    sql = TemplateHandler.replacePlaceholder(sql, "%column_value%", value, false);
    return sql;
  }


  private String getSQLTemplate(ColumnComparator comp, boolean ignoreCase)
  {
    String dbKey = "filterexpressions.template." + comp.getClass().getSimpleName();

    String baseName = "workbench.db." + dbKey;
    if (ignoreCase)
    {
      baseName += ".ignoreCase";
    }
    String defaultValue = Settings.getInstance().getProperty(baseName, null);

    String template = dbSettings.getProperty(dbKey, defaultValue);
    if (ignoreCase)
    {
      template = dbSettings.getProperty(dbKey + ".ignoreCase", template);
    }
    return template;
  }

  private String getCastExpression(String dbmsType, String expression)
  {
    String cast = dbSettings.getCastToString(dbmsType);
    return TemplateHandler.replacePlaceholder(cast, MetaDataSqlManager.COLUMN_NAME_PLACEHOLDER, expression, false);
  }
}
