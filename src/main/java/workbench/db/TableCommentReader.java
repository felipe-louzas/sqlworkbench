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

import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.sqltemplates.ColumnChanger;

import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * @author Thomas Kellerer
 */
public class TableCommentReader
{
  public TableCommentReader()
  {
  }

  /**
   * Return the SQL that is needed to re-create the comment on the given table.
   * The syntax to be used, can be configured in the workbench.settings file.
   */
  public String getTableCommentSql(WbConnection dbConnection, TableIdentifier table)
  {
    return getTableCommentSql(dbConnection.getDbSettings().getDbId(), dbConnection, table);
  }

  String getTableCommentSql(String dbId, WbConnection dbConnection, TableIdentifier table)
  {
    CommentSqlManager mgr = new CommentSqlManager(dbConnection.getMetadata().getDbId());

    String commentStatement = mgr.getCommentSqlTemplate(table.getType(), CommentSqlManager.COMMENT_ACTION_SET);
    boolean quoteIdentifier = dbConnection.getDbSettings().useQuotedColumnsForComments();

    if (StringUtil.isBlank(commentStatement))
    {
      return null;
    }

    String comment = null;

    if (table.commentIsDefined())
    {
      comment = table.getComment();
    }
    else
    {
      comment = getTableComment(dbConnection, table);
    }

    QuoteHandler quoter = quoteIdentifier ? dbConnection.getMetadata() : getDummyQuoteHandler();

    String result = null;
    if (Settings.getInstance().getIncludeEmptyComments() || StringUtil.isNotBlank(comment))
    {
      String fqn = table.getFullyQualifiedName(dbConnection);

      result = StringUtil.replace(commentStatement, CommentSqlManager.COMMENT_FQ_OBJECT_NAME_PLACEHOLDER, fqn);
      result = StringUtil.replace(result, MetaDataSqlManager.FQ_TABLE_NAME_PLACEHOLDER, fqn);

      // only call getObjectExpression() if necessary to avoid unnecessary calls to retrieve the current schema or catalog
      if (result.contains(CommentSqlManager.COMMENT_OBJECT_NAME_PLACEHOLDER))
      {
        result = StringUtil.replace(result, CommentSqlManager.COMMENT_OBJECT_NAME_PLACEHOLDER, table.getObjectExpression(dbConnection));
      }

      result = replaceObjectNamePlaceholder(result, MetaDataSqlManager.TABLE_NAME_PLACEHOLDER, quoter.quoteObjectname(table.getTableName()));
      result = replaceObjectNamePlaceholder(result, TableSourceBuilder.SCHEMA_PLACEHOLDER, quoter.quoteObjectname(table.getSchema()));
      result = replaceObjectNamePlaceholder(result, TableSourceBuilder.CATALOG_PLACEHOLDER, quoter.quoteObjectname(table.getCatalog()));
      result = StringUtil.replace(result, CommentSqlManager.COMMENT_PLACEHOLDER, comment == null ? "" : comment.replace("'", "''"));
      result += ";";
    }

    return result;
  }

  public String getTableComment(WbConnection dbConnection, TableIdentifier tbl)
  {
    TableIdentifier id = new DbObjectFinder(dbConnection).findObject(tbl);
    if (id == null) return null;
    return id.getComment();
  }

  /**
   * Return the SQL that is needed to re-create the comment on the given columns.
   *
   * The syntax to be used, can be configured in the workbench.settings file.
   *
   * @see CommentSqlManager#getCommentSqlTemplate(java.lang.String, java.lang.String)
   */
  public StringBuilder getTableColumnCommentsSql(WbConnection con, TableIdentifier table, List<ColumnIdentifier> columns)
  {
    return getTableColumnCommentsSql(con.getMetadata().getDbId(), con, table, columns);
  }

  /**
   * For Unit-Testing only
   */
  StringBuilder getTableColumnCommentsSql(String dbId, WbConnection con, TableIdentifier table, List<ColumnIdentifier> columns)
  {
    CommentSqlManager mgr = new CommentSqlManager(dbId);

    String columnStatement = mgr.getCommentSqlTemplate("column", CommentSqlManager.COMMENT_ACTION_SET);
    if (StringUtil.isBlank(columnStatement)) return null;
    if (CollectionUtil.isEmpty(columns)) return null;

    StringBuilder result = new StringBuilder(columns.size() * 25);
    ColumnChanger colChanger = new ColumnChanger(con);

    boolean quoteIdentifier = con.getDbSettings().useQuotedColumnsForComments();
    QuoteHandler quoter = quoteIdentifier ? con.getMetadata() : getDummyQuoteHandler();

    for (ColumnIdentifier col : columns)
    {
      String comment = col.getComment();
      if (Settings.getInstance().getIncludeEmptyComments() || StringUtil.isNotBlank(comment))
      {
        try
        {
          String commentSql = colChanger.getColumnCommentSql(table, col, quoter);
          result.append(commentSql);
          result.append(";\n");
        }
        catch (Exception e)
        {
          LogMgr.logError(new CallerInfo(){}, "Error creating comments SQL for remark=" + comment, e);
        }
      }
    }
    return result;
  }

  private String replaceObjectNamePlaceholder(String source, String placeHolder, String replacement)
  {
    if (StringUtil.isBlank(replacement))
    {
      return source.replace(placeHolder + ".", "");
    }
    return source.replace(placeHolder, replacement);
  }

  private QuoteHandler getDummyQuoteHandler() {
      return new QuoteHandler()
      {
        @Override
        public boolean isQuoted(String name)
        {
          return SqlUtil.isQuotedIdentifier(name);
        }

        @Override
        public String removeQuotes(String name)
        {
          return SqlUtil.removeObjectQuotes(name);
        }

        @Override
        public String quoteObjectname(String name)
        {
          return name;
        }

        @Override
        public String quoteObjectname(String name, boolean quoteAlways)
        {
          return name;
        }

        @Override
        public boolean needsQuotes(String name)
        {
          return false;
        }

        @Override
        public boolean isLegalIdentifier(String name)
        {
          return true;
        }
      };
  }
}
