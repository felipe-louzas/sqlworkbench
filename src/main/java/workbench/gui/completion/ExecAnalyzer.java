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
package workbench.gui.completion;

import workbench.resource.ResourceMgr;

import workbench.db.WbConnection;
import workbench.db.objectcache.Namespace;

import workbench.sql.lexer.SQLLexer;
import workbench.sql.lexer.SQLLexerFactory;
import workbench.sql.lexer.SQLToken;

import workbench.util.StringUtil;

/**
 * Supply a list of stored procedures for EXEC or WbCall
 *
 */
public class ExecAnalyzer
  extends BaseAnalyzer
{
  private String qualifier;

  public ExecAnalyzer(WbConnection conn, String statement, int cursorPos)
  {
    super(conn, statement, cursorPos);
  }

  @Override
  protected void checkContext()
  {
    SQLLexer lexer = SQLLexerFactory.createLexer(dbConnection, this.sql);
    SQLToken verbToken = lexer.getNextToken(false, false);

    if (verbToken == null)
    {
      this.context = NO_CONTEXT;
      return;
    }

    context = CONTEXT_TABLE_LIST;
    qualifier = getQualifierLeftOfCursor();
  }

  @Override
  protected void buildResult()
  {
    if (context == NO_CONTEXT) return;

    title = ResourceMgr.getString("TxtDbExplorerProcs");
    String schema = null;

    if (StringUtil.isNotBlank(qualifier))
    {
      String[] parsed = qualifier.split("\\.");
      if (parsed.length == 1)
      {
        schema = parsed[0];
      }
      if (parsed.length == 2)
      {
        schema = parsed[1];
      }
    }

    if (schema == null)
    {
      schema = this.dbConnection.getCurrentSchema();
    }
    Namespace nsp = Namespace.fromCatalogAndSchema(dbConnection, this.dbConnection.getCurrentCatalog(), schema);
    elements = dbConnection.getObjectCache().getProcedures(nsp);
  }

}
