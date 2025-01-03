/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer.
 *
 * Licensed under a modified Apache License, Version 2.0 (the "License")
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

import java.util.List;

import workbench.util.SqlUtil;
import workbench.util.WbStringTokenizer;

/**
 * A class to parse object expressions that can potentially contain multiple parts.
 *
 * @author Thomas Kellerer
 */
public class ObjectExpressionParser
{
  private String server;
  private String catalog;
  private String schema;
  private String name;

  public ObjectExpressionParser(String expression)
  {
    this(expression, '.', '.', true, true);
  }

  public ObjectExpressionParser(String expression, char catalogSeparator, char schemaSeparator)
  {
    this(expression, catalogSeparator, schemaSeparator, true, true);
  }

  public ObjectExpressionParser(String expression, char catalogSeparator, char schemaSeparator, boolean supportsCatalogs, boolean supportsSchemas)
  {
    parseExpression(expression, catalogSeparator, schemaSeparator, supportsCatalogs, supportsSchemas);
  }

  public ObjectExpressionParser(String expression, WbConnection conn)
  {
    DbSettings settings = (conn == null ? null : conn.getDbSettings());
    boolean supportsCatalogs = settings == null ? true : settings.supportsCatalogs();
    boolean supportsSchemas = settings == null ? true : settings.supportsSchemas();
    char catalogSeparator = SqlUtil.getCatalogSeparator(conn);
    char schemaSeparator = SqlUtil.getSchemaSeparator(conn);
    parseExpression(expression, catalogSeparator, schemaSeparator, supportsCatalogs, supportsSchemas);
  }

  public String getServer()
  {
    return server;
  }

  public String getCatalog()
  {
    return catalog;
  }

  public String getSchema()
  {
    return schema;
  }

  public String getName()
  {
    return name;
  }

  private void parseExpression(String expression, char catalogSeparator, char schemaSeparator, boolean supportsCatalogs, boolean supportsSchemas)
  {
    WbStringTokenizer tok = new WbStringTokenizer(schemaSeparator, "\"", true);
    tok.setSourceString(expression);
    List<String> elements = tok.getAllTokens();

    switch (elements.size())
    {
      case 1:
        // if only one element is found it could still be a two element identifier
        // in case the catalog separator is different from the schema separator (e.g. DB2 for iSeries)
        catalog = getCatalogPart(expression, catalogSeparator);
        name = getNamePart(expression, catalogSeparator);
        break;
      case 2:
        if (supportsSchemas && supportsCatalogs)
        {
          catalog = getCatalogPart(elements.get(0), catalogSeparator);
          schema = getNamePart(elements.get(0), catalogSeparator);
        }
        if (supportsSchemas && !supportsCatalogs)
        {
          // no catalog supported, the first element must be the schema
          schema = elements.get(0);
        }
        if (supportsCatalogs && !supportsSchemas)
        {
          // e.g. MySQL qualifier: database.tablename
          catalog = elements.get(0);
        }
        name = elements.get(1);
        break;
      case 3:
        // no ambiguity if three elements are used
        catalog = elements.get(0);
        schema = elements.get(1);
        name = elements.get(2);
        break;
      case 4:
        // support for SQL Server syntax with a linked server
        server = elements.get(0);
        catalog = elements.get(1);
        schema = elements.get(2);
        name = elements.get(3);
        break;
    }
  }

  private String getCatalogPart(String identifier, char catalogSeparator)
  {
    if (identifier == null) return identifier;
    WbStringTokenizer tok = new WbStringTokenizer(catalogSeparator, "\"", true);
    tok.setSourceString(identifier);
    List<String> tokens = tok.getAllTokens();
    if (tokens.size() == 2)
    {
      return tokens.get(0);
    }
    return null;
  }

  private String getNamePart(String identifier, char catalogSeparator)
  {
    if (identifier == null) return identifier;
    WbStringTokenizer tok = new WbStringTokenizer(catalogSeparator, "\"", true);
    tok.setSourceString(identifier);
    List<String> tokens = tok.getAllTokens();
    if (tokens.size() == 2)
    {
      return tokens.get(1);
    }
    return tokens.get(0);
  }


}
