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
package workbench.db.sqltemplates;

import java.io.IOException;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.DbObject;
import workbench.db.MetaDataSqlManager;
import workbench.db.QuoteHandler;
import workbench.db.TableSourceBuilder;
import workbench.db.WbConnection;

import workbench.util.FileUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;
import workbench.util.WbFile;

/**
 *
 * @author Thomas Kellerer
 */
public abstract class TemplateHandler
{

  protected String getStringProperty(String property, String defaultValue)
  {
    String value= Settings.getInstance().getProperty(property, defaultValue);
    if (value != null && value.startsWith("@file:"))
    {
      value = readFile(value);
    }
    return value;
  }

  private String readFile(String propValue)
  {
    String fname = propValue.replace("@file:", "");
    WbFile f = new WbFile(fname);
    if (!f.isAbsolute())
    {
      f = new WbFile(Settings.getInstance().getConfigDir(), fname);
    }

    String sql = null;
    try
    {
      LogMgr.logDebug(new CallerInfo(){}, "Reading SQL template from: " + f.getAbsolutePath());
      sql = FileUtil.readFile(f, "UTF-8");
    }
    catch (IOException io)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not read file: " + fname, io);
      sql = null;
    }
    return sql;
  }

  /**
   * Remove the placeholder completely from the template SQL.
   *
   * @param sql          the sql template
   * @param placeholder  the placeholder
   * @param withNL       controls replacing of newlines after the placeholder.
   *                     if true, newlines after and whitespace before the placeholder are removed as well.
   *
   * @return the template with the placeholder removed
   */
  public static String removePlaceholder(String sql, String placeholder, boolean withNL)
  {
    String s;
    if (withNL)
    {
      StringBuilder b = new StringBuilder(placeholder.length() + 10);
      b.append("[ \\t]*");
      b.append(StringUtil.quoteRegexMeta(placeholder));
      b.append("[\n|\r\n]?");
      s = b.toString();
    }
    else
    {
      s = StringUtil.quoteRegexMeta(placeholder);
    }
    return sql.replaceAll(s, StringUtil.EMPTY_STRING);
  }

  public static String replaceNamespaces(String sql, String catalog, String schema, WbConnection conn)
  {
    sql = replaceSchemaPlaceholder(sql, schema, conn);
    return replaceCatalogPlaceholder(sql, catalog, conn);
  }

  public static String replaceSchemaPlaceholder(String sql, String schemaName, WbConnection conn)
  {
    char sep = conn.getMetadata().getSchemaSeparator();
    if (StringUtil.isBlank(schemaName))
    {
      sql = removeNamespacePlaceholder(sql, MetaDataSqlManager.SCHEMA_NAME_PLACEHOLDER, sep);
      sql = removeNamespacePlaceholder(sql, TableSourceBuilder.SCHEMA_PLACEHOLDER, sep);
    }
    if (sql.contains(MetaDataSqlManager.SCHEMA_NAME_PLACEHOLDER))
    {
      return replacePlaceholder(sql, MetaDataSqlManager.SCHEMA_NAME_PLACEHOLDER, schemaName, false);
    }
    return replacePlaceholder(sql, TableSourceBuilder.SCHEMA_PLACEHOLDER, schemaName, false);
  }

  public static String replaceCatalogPlaceholder(String sql, String catalogName, WbConnection conn)
  {
    char sep = conn.getMetadata().getCatalogSeparator();
    if (StringUtil.isBlank(catalogName))
    {
      return removeNamespacePlaceholder(sql, MetaDataSqlManager.CATALOG_NAME_PLACEHOLDER, sep);
    }
    return replacePlaceholder(sql, MetaDataSqlManager.CATALOG_NAME_PLACEHOLDER, conn.getMetadata().quoteObjectname(catalogName), false);
  }

  public static String replaceTablePlaceholder(String sql, DbObject table, WbConnection connection)
  {
    return replaceTablePlaceholder(sql, table, connection, false);
  }

  public static String replaceTablePlaceholder(String sql, DbObject table, WbConnection connection, boolean addWhitespace)
  {
    return replaceTablePlaceholder(sql, table, connection, addWhitespace, SqlUtil.getQuoteHandler(connection));
  }

  public static String replaceTablePlaceholder(String sql, DbObject table, WbConnection connection, boolean addWhitespace, QuoteHandler handler)
  {
    if (sql == null) return sql;
    if (table == null) return sql;
    if (handler == null)
    {
      handler = QuoteHandler.STANDARD_HANDLER;
    }

    if (table.getSchema() == null)
    {
      sql = removeSchemaPlaceholder(sql, SqlUtil.getSchemaSeparator(connection));
    }
    else
    {
      sql = replacePlaceholder(sql, MetaDataSqlManager.SCHEMA_NAME_PLACEHOLDER, handler.quoteObjectname(table.getSchema()), false);
    }

    if (table.getCatalog() == null)
    {
      sql = TemplateHandler.removeCatalogPlaceholder(sql, SqlUtil.getCatalogSeparator(connection));
    }
    else
    {
      sql = replacePlaceholder(sql, MetaDataSqlManager.CATALOG_NAME_PLACEHOLDER, handler.quoteObjectname(table.getCatalog()), false);
    }

    if (sql.contains(MetaDataSqlManager.TABLE_NAME_PLACEHOLDER))
    {
      sql = replacePlaceholder(sql, MetaDataSqlManager.TABLE_NAME_PLACEHOLDER, handler.quoteObjectname(table.getObjectName()), false);
    }

    if (sql.contains(MetaDataSqlManager.OBJECT_NAME_PLACEHOLDER))
    {
      sql = replacePlaceholder(sql, MetaDataSqlManager.OBJECT_NAME_PLACEHOLDER, handler.quoteObjectname(table.getObjectName()), false);
    }

    // do not call getObjectExpression() or getFullyQualifiedName() if not necessary.
    // this might trigger a SELECT to the database to get the current schema and/or catalog
    // to avoid unnecessary calls, this is only done if really needed

    if (sql.contains(MetaDataSqlManager.TABLE_EXPRESSION_PLACEHOLDER))
    {
      sql = replacePlaceholder(sql, MetaDataSqlManager.TABLE_EXPRESSION_PLACEHOLDER, table.getObjectExpression(connection), addWhitespace);
    }
    if (sql.contains(MetaDataSqlManager.FQ_TABLE_NAME_PLACEHOLDER))
    {
      sql = replacePlaceholder(sql, MetaDataSqlManager.FQ_TABLE_NAME_PLACEHOLDER, table.getFullyQualifiedName(connection), addWhitespace);
    }

    if (sql.contains(MetaDataSqlManager.FQ_NAME_PLACEHOLDER))
    {
      sql = replacePlaceholder(sql, MetaDataSqlManager.FQ_NAME_PLACEHOLDER, table.getFullyQualifiedName(connection), addWhitespace);
    }
    return sql;
  }

  public static String removeSchemaPlaceholder(String sql, char schemaSeparator)
  {
    return removeNamespacePlaceholder(sql, MetaDataSqlManager.SCHEMA_NAME_PLACEHOLDER, schemaSeparator);
  }

  public static String removeCatalogPlaceholder(String sql, char schemaSeparator)
  {
    return removeNamespacePlaceholder(sql, MetaDataSqlManager.CATALOG_NAME_PLACEHOLDER, schemaSeparator);
  }

  /**
   * Remove the a schema or catalog placeholder completely from the template SQL.
   *
   * @param sql                the sql template
   * @param placeholder        the placeholder
   * @param namespaceSeparator if this character follows the placeholder, it is removed as well
   *
   * @return the template with the placeholder removed
   *
   * @see #removeSchemaPlaceholder(java.lang.String, java.lang.String)
   * @see #removeCatalogPlaceholder(java.lang.String, java.lang.String)
   */
  public static String removeNamespacePlaceholder(String sql, String placeholder, char namespaceSeparator)
  {
    String clean = removePlaceholder(sql, placeholder, false);
    clean = removePlaceholder(sql, placeholder + namespaceSeparator, false);
    return clean;
  }

  /**
   * Replace the placeholder in the given SQL template.
   *
   * If the template does not have whitespace before or after the placeholder a whitespace will be inserted.
   *
   * If replacement is null or an empty string, the placeholder will be removed.
   *
   * @param sql           the SQL template
   * @param placeholder   the placeholder
   * @param replacement   the replacement
   * @param addWhitespace if true a space will be added after the replacement
   *
   * @return the template with the placeholder replaced
   * @see #removePlaceholder(String, String, boolean)
   */
  public static String replacePlaceholder(String sql, String placeholder, String replacement, boolean addWhitespace)
  {
    if (StringUtil.isEmpty(replacement)) return removePlaceholder(sql, placeholder, false);
    int pos = sql.indexOf(placeholder);
    if (pos < 0) return sql;

    String realReplacement = replacement;

    if (pos > 1)
    {
      String opening = "([\"'`";
      char prev = sql.charAt(pos - 1);
      if (addWhitespace && !Character.isWhitespace(prev) && opening.indexOf(prev) == -1)
      {
        realReplacement = " " + realReplacement;
      }
    }

    if (pos + placeholder.length() < sql.length())
    {
      String closing = ")]\"'`";
      char next = sql.charAt(pos + placeholder.length());
      if (addWhitespace && !Character.isWhitespace(next) && closing.indexOf(next) == -1)
      {
        realReplacement = realReplacement + " ";
      }
    }
    return StringUtil.replace(sql, placeholder, realReplacement);
  }

}
