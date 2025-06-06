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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.sql.formatter.WbSqlFormatter;

import workbench.util.CollectionUtil;
import workbench.util.ExceptionUtil;
import workbench.util.StringUtil;

/**
 * A class to read the source of a database view.
 * <br/>
 * The source is retrieved by using SQL statements defined in the file
 * <literal>ViewSourceStatements.xml</literal>.
 * <br/>
 *
 * @author Thomas Kellerer
 * @see MetaDataSqlManager#getViewSourceSql()
 */
public class DefaultViewReader
  implements ViewReader
{
  protected WbConnection connection;

  public DefaultViewReader(WbConnection con)
  {
    this.connection = con;
  }

  @Override
  public CharSequence getExtendedViewSource(TableIdentifier tbl)
    throws SQLException
  {
    return getExtendedViewSource(new TableDefinition(tbl), DropType.none, false);
  }

  @Override
  public CharSequence getExtendedViewSource(TableIdentifier tbl, DropType dropType)
    throws SQLException
  {
    return getExtendedViewSource(new TableDefinition(tbl), dropType, false);
  }

  @Override
  public CharSequence getFullViewSource(TableDefinition view)
    throws SQLException, NoConfigException
  {
    return createFullViewSource(view, DropType.none, false);
  }

  protected CharSequence createFullViewSource(TableDefinition view, DropType dropType, boolean includeCommit)
    throws SQLException, NoConfigException
  {
    TableIdentifier viewTable = view.getTable();
    CharSequence source = this.getViewSource(viewTable);

    List<ColumnIdentifier> columns = view.getColumns();

    if (CollectionUtil.isEmpty(columns))
    {
      view = this.connection.getMetadata().getTableDefinition(view.getTable());
      columns = view.getColumns();
    }

    if (StringUtil.isEmpty(source)) return StringUtil.EMPTY_STRING;

    StringBuilder result = new StringBuilder(source.length() + 100);

    String lineEnding = Settings.getInstance().getInternalEditorLineEnding();
    String verb = connection.getParsingUtil().getSqlVerb(source.toString());

    TableSourceBuilder builder = TableSourceBuilderFactory.getBuilder(connection);


    // a drop should be added if
    //
    // * the database does not support create or replace
    // * a cascaded drop was requested (because "create or replace" works differently than a drop ... cascade)
    boolean addDrop = dropType != DropType.none;

    if (dropType == DropType.regular && !verb.equals("CREATE OR REPLACE") && !verb.equalsIgnoreCase("REPLACE"))
    {
      addDrop = true;
    }

    // SQL Server and DB2 return the full CREATE VIEW statement
    // DB2 even returns the CREATE OR REPLACE if the view was created that way.
    // Teradata returns a complete REPLACE VIEW ... statement
    // therefor the verb is compared with startsWith() rather than equals()
    if (verb.startsWith("CREATE") || verb.equals("REPLACE"))
    {
      if (addDrop)
      {
        result.append(builder.generateDrop(viewTable, dropType));
        result.append(lineEnding);
        result.append(lineEnding);
      }

      // use the source as it is
      result.append(source);

      if (this.connection.getDbSettings().ddlNeedsCommit() && includeCommit)
      {
        result.append(lineEnding);
        result.append("COMMIT;");
        result.append(lineEnding);
      }
    }
    else
    {
      // apprently only the SELECT statement was returned by the DBMS
      // re-construct a valid CREATE VIEW statement
      result.append(builder.generateCreateObject(dropType, viewTable, null));

      if (connection.getDbSettings().generateColumnListInViews())
      {
        result.append(lineEnding);
        result.append('(');
        result.append(lineEnding);

        int colCount = columns.size();
        for (int i=0; i < colCount; i++)
        {

          String colName = columns.get(i).getColumnName();
          result.append("  ");
          result.append(connection.getMetadata().quoteObjectname(colName));
          if (i < colCount - 1)
          {
            result.append(',');
            result.append(lineEnding);
          }
        }
        result.append(lineEnding);
        result.append(')');
      }
      result.append(lineEnding);
      result.append("AS ");
      result.append(lineEnding);
      result.append(source);
      result.append(lineEnding);
    }
    return result;
  }

  /**
   * Returns a complete SQL statement to (re)create the given view.
   *
   * This method will extend the stored source to a valid CREATE VIEW.
   *
   * @param view The view for which thee source should be created
   * @param includeCommit if true, terminate the whole statement with a COMMIT
   * @param includeDrop if true, add a DROP statement before the CREATE statement
   *
   * @see #getViewSource(workbench.db.TableIdentifier)
   */
  @Override
  public CharSequence getExtendedViewSource(TableDefinition view, DropType dropType, boolean includeCommit)
    throws SQLException
  {
    TableIdentifier viewTable = view.getTable();

    CharSequence source = null;
    try
    {
      source = createFullViewSource(view, dropType, includeCommit);
    }
    catch (NoConfigException no)
    {
      SourceStatementsHelp help = new SourceStatementsHelp(this.connection.getMetadata().getMetaDataSQLMgr());
      return help.explainMissingViewSourceSql();
    }

    if (StringUtil.isEmpty(source)) return StringUtil.EMPTY_STRING;

    StringBuilder result = new StringBuilder(source.length() + 100);
    result.append(source);

    String lineEnding = Settings.getInstance().getInternalEditorLineEnding();

    ViewGrantReader grantReader = ViewGrantReader.createViewGrantReader(connection);
    if (grantReader != null)
    {
      CharSequence grants = grantReader.getViewGrantSource(connection, view.getTable());
      if (grants != null && grants.length() > 0)
      {
        result.append(Settings.getInstance().getInternalEditorLineEnding());
        result.append(Settings.getInstance().getInternalEditorLineEnding());
        result.append(grants);
        result.append(Settings.getInstance().getInternalEditorLineEnding());
      }
    }

    TableCommentReader commentReader = new TableCommentReader();
    String viewComment = commentReader.getTableCommentSql(this.connection, view.getTable());
    if (StringUtil.isNotBlank(viewComment))
    {
      result.append(viewComment);
      if (!viewComment.endsWith(";")) result.append(';');
    }

    StringBuilder colComments = commentReader.getTableColumnCommentsSql(this.connection, view.getTable(), view.getColumns());
    if (StringUtil.isNotBlank(colComments))
    {
      result.append(lineEnding);
      result.append(colComments);
      result.append(lineEnding);
    }

    if (supportsIndexesOnView(viewTable))
    {
      List<IndexDefinition> indexInfo = connection.getMetadata().getIndexReader().getTableIndexList(viewTable, true);
      if (indexInfo.size() > 0)
      {
        StringBuilder idx = this.connection.getMetadata().getIndexReader().getIndexSource(viewTable, indexInfo);
        if (idx != null && idx.length() > 0)
        {
          result.append(lineEnding);
          result.append(lineEnding);
          result.append(idx);
          result.append(lineEnding);
        }
      }
    }

    if (this.connection.getDbSettings().ddlNeedsCommit() && includeCommit)
    {
      result.append("COMMIT;");
    }
    return result;
  }

  protected boolean supportsIndexesOnView(TableIdentifier view)
  {
    return connection.getDbSettings().supportsIndexedViews();
  }

  /**
   * Return the source of a view definition as it is stored in the database.
   * <br/>
   * Usually (depending on how the meta data is stored in the database) the DBMS
   * only stores the underlying SELECT statement (but not a full CREATE VIEW),
   * and that will be returned by this method.
   * <br/>
   * To create a complete SQL to re-create a view, use {@link #getExtendedViewSource(workbench.db.TableIdentifier) }
   *
   * @return the view source as stored in the database.
   *
   * @throws NoConfigException if no SQL was configured in ViewSourceStatements.xml
   * @see DbSettings#getFormatViewSource()
   */
  @Override
  public CharSequence getViewSource(TableIdentifier viewId)
    throws NoConfigException
  {
    if (viewId == null) return null;

    if (connection.getDbSettings().isObjectSourceRetrievalCustomized(viewId.getType()))
    {
      TableSourceBuilder builder = TableSourceBuilderFactory.getBuilder(connection);
      return builder.getNativeTableSource(viewId, DropType.none);
    }

    GetMetaDataSql sql = connection.getMetadata().getMetaDataSQLMgr().getViewSourceSql();
    if (sql == null) throw new NoConfigException("No SQL to retrieve the VIEW source");

    StringBuilder source = new StringBuilder(500);
    Statement stmt = null;
    ResultSet rs = null;
    Savepoint sp = null;
    String query = null;

    try
    {
      if (connection.getDbSettings().useSavePointForDML())
      {
        sp = connection.setSavepoint();
      }
      TableIdentifier tbl = viewId.createCopy();
      tbl.adjustCase(connection);
      sql.setSchema(tbl.getRawSchema());
      sql.setObjectName(tbl.getRawTableName());
      sql.setCatalog(tbl.getRawCatalog());

      if (sql.isPreparedStatement())
      {
        query = sql.getBaseSql();
        LogMgr.logMetadataSql(new CallerInfo(){}, "view source", query, tbl.getRawCatalog(), tbl.getRawSchema(), tbl.getRawTableName());
        PreparedStatement pstmt = sql.prepareStatement(connection, tbl.getRawCatalog(), tbl.getRawSchema(), tbl.getRawTableName());
        rs = pstmt.executeQuery();
      }
      else
      {
        stmt = connection.createStatementForQuery();
        query = sql.getSql();
        LogMgr.logMetadataSql(new CallerInfo(){}, "view source", query);
        rs = stmt.executeQuery(query);
      }

      while (rs.next())
      {
        String line = rs.getString(1);
        if (line != null)
        {
          source.append(line);
        }
      }

      if (source.length() > 0)
      {
        StringUtil.trimTrailingWhitespace(source);
        if (this.connection.getDbSettings().getFormatViewSource())
        {
          WbSqlFormatter f = new WbSqlFormatter(source, connection.getDbId());
          source = new StringBuilder(f.getFormattedSql());
        }

        if (!StringUtil.endsWith(source, ';'))
        {
          source.append(';');
          source.append(Settings.getInstance().getInternalEditorLineEnding());
        }
      }
      connection.releaseSavepoint(sp);
    }
    catch (Exception e)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, e, "view source", query);
      source = new StringBuilder(ExceptionUtil.getDisplay(e));
      connection.rollback(sp);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }

    return source;
  }

}
