/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2022, Thomas Kellerer
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
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.storage.DataStore;
import workbench.storage.ResultInfo;

import workbench.sql.DelimiterDefinition;

import workbench.util.CollectionUtil;
import workbench.util.ExceptionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * A class to read triggers from the database.
 * The reading is done by Statements configured in XML files.
 *
 * @author Thomas Kellerer
 * @see MetaDataSqlManager
 */
public class DefaultTriggerReader
  implements TriggerReader
{
  protected WbConnection dbConnection;
  protected DbMetadata dbMeta;

  public DefaultTriggerReader(WbConnection conn)
  {
    this.dbMeta = conn.getMetadata();
    this.dbConnection = conn;
  }

  /**
   * Return a list of triggers available in the given schema.
   */
  @Override
  public TriggerListDataStore getTriggers(String catalog, String schema)
    throws SQLException
  {
    return getTriggers(catalog, schema, null);
  }

  @Override
  public List<TriggerDefinition> getTriggerList(String catalog, String schema, String baseTable)
    throws SQLException
  {
    TriggerListDataStore triggers = getTriggers(catalog, schema, baseTable);
    List<TriggerDefinition> result = new ArrayList<>(triggers.getRowCount());
    for (int row = 0; row < triggers.getRowCount(); row ++)
    {
      TriggerDefinition trg = triggers.getUserObject(row, TriggerDefinition.class);
      result.add(trg);
    }
    return result;
  }

  public TriggerDefinition createTriggerDefinition(TriggerListDataStore triggers, int row, String catalog, String schema)
  {
    String trgName = triggers.getTriggerName(row);
    String trgSchema = triggers.getTriggerSchema(row);
    String trgType = triggers.getTriggerType(row);
    String trgEvent = triggers.getEvent(row);
    String tableName = triggers.getTriggerTable(row);
    String comment = triggers.getRemarks(row);
    String status = triggers.getStatus(row);
    String level = triggers.getLevel(row);

    TriggerDefinition trg = new TriggerDefinition(catalog, StringUtil.coalesce(trgSchema, schema), trgName);
    trg.setTriggerType(trgType);
    trg.setTriggerEvent(trgEvent);
    trg.setComment(comment);
    trg.setStatus(status);
    trg.setLevel(TriggerLevel.parseLevel(level));

    if (tableName != null)
    {
      String tableSchema = triggers.getTriggerTableSchema(row);
      TableIdentifier tbl;
      if (StringUtil.isNoneBlank(tableName, tableSchema))
      {
        tbl = new TableIdentifier(tableSchema, tableName);
      }
      else
      {
        tbl = new TableIdentifier(tableName);
      }
      trg.setRelatedTable(tbl);
    }
    return trg;
  }

  /**
   *  Return the list of defined triggers for the given table.
   */
  @Override
  public DataStore getTableTriggers(TableIdentifier table)
    throws SQLException
  {
    TableIdentifier tbl = table.createCopy();
    tbl.adjustCase(this.dbConnection);
    return getTriggers(tbl.getCatalog(), tbl.getSchema(), tbl.getTableName());
  }

  protected String getListTriggerSQL(String catalog, String schema, String tableName)
  {
    GetMetaDataSql sql = dbMeta.getMetaDataSQLMgr().getListTriggerSql();
    if (sql == null)
    {
      LogMgr.logInfo(new CallerInfo(){}, "No SQL query configured to list triggers.");
      return null;
    }

    if ("*".equals(schema))
    {
      schema = null;
    }
    if ("*".equals(catalog))
    {
      catalog = null;
    }

    sql.setSchema(schema);
    sql.setCatalog(catalog);
    sql.setObjectName(tableName);

    return sql.getSql();
  }

  protected TriggerListDataStore getTriggers(String catalog, String schema, String tableName)
    throws SQLException
  {
    final TriggerListDataStore result = new TriggerListDataStore();
    String query = getListTriggerSQL(catalog, schema, tableName);
    if (query == null) return result;

    Statement stmt = this.dbConnection.createStatementForQuery();

    LogMgr.logMetadataSql(new CallerInfo(){}, "table triggers", query);

    boolean trimNames = dbMeta.getDbSettings().trimObjectNames("trigger");
    boolean useSavepoint = dbConnection.getDbSettings().useSavePointForDML();
    Savepoint sp = null;
    ResultSet rs = null;
    try
    {
      if (useSavepoint)
      {
        sp = dbConnection.setSavepoint();
      }
      rs = stmt.executeQuery(query);
      ResultSetMetaData rsMeta = rs.getMetaData();
      ResultInfo info = new ResultInfo(rsMeta, dbConnection);
      boolean hasTableName = info.findColumn("TRIGGER_TABLE") > -1;
      boolean hasComment = info.findColumn("REMARKS") > -1;
      boolean hasStatus = info.findColumn("STATUS") > -1;
      boolean hasLevel = info.findColumn("TRIGGER_LEVEL") > -1;
      boolean hasSchema = info.findColumn("TRIGGER_SCHEMA") > -1;
      boolean hasTableSchema = info.findColumn("TRIGGER_TABLE_SCHEMA") > -1;
      if (!hasSchema)
      {
        result.removeColumn(TriggerReader.TRIGGER_SCHEMA_COLUMN);
      }
      if (!hasTableSchema)
      {
        result.removeColumn(TriggerReader.TRIGGER_TABLE_SCHEMA_COLUMN);
      }

      while (rs.next())
      {
        int row = result.addRow();
        String value = rs.getString("TRIGGER_NAME");
        if (trimNames) value = StringUtil.trim(value);
        result.setTriggerName(row, value);

        value = rs.getString("TRIGGER_TYPE");
        if (trimNames) value = StringUtil.trim(value);
        result.setTriggerType(row, value);

        value = rs.getString("TRIGGER_EVENT");
        if (trimNames) value = StringUtil.trim(value);
        result.setEvent(row, value);

        if (hasTableName)
        {
          value = rs.getString("TRIGGER_TABLE");
          if (trimNames) value = StringUtil.trim(value);
          result.setTriggerTable(row, value);
        }

        if (hasTableSchema)
        {
          value = rs.getString("TRIGGER_TABLE_SCHEMA");
          if (trimNames) value = StringUtil.trim(value);
          result.setTriggerTableSchema(row, value);
        }

        if (hasComment)
        {
          value = rs.getString("REMARKS");
          result.setRemarks(row, StringUtil.trim(value));
        }

        if (hasStatus)
        {
          value = rs.getString("STATUS");
          result.setStatus(row, StringUtil.trim(value));
        }

        if (hasLevel)
        {
          value = rs.getString("TRIGGER_LEVEL");
          result.setLevel(row, StringUtil.trim(value));
        }

        if (hasSchema)
        {
          value = rs.getString("TRIGGER_SCHEMA");
          result.setTriggerSchema(row, StringUtil.trim(value));
        }
        TriggerDefinition trg = createTriggerDefinition(result, row, catalog, schema);
        result.getRow(row).setUserObject(trg);
      }
      result.resetStatus();
      dbConnection.releaseSavepoint(sp);
    }
    catch (SQLException ex)
    {
      dbConnection.rollback(sp);
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "table triggers", query);
      throw ex;
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return result;
  }

  @Override
  public TriggerDefinition findTrigger(String catalog, String schema, String name)
    throws SQLException
  {
    List<TriggerDefinition> triggers = getTriggerList(catalog, schema, null);
    if (CollectionUtil.isEmpty(triggers)) return null;
    for (TriggerDefinition trg : triggers)
    {
      if (trg.getObjectName().equalsIgnoreCase(name))
      {
        return trg;
      }
    }
    return null;
  }

  @Override
  public String getTriggerSource(TriggerDefinition trigger, boolean includeDependencies)
    throws SQLException
  {
    return getTriggerSource(trigger.getCatalog(), trigger.getSchema(), trigger.getObjectName(), trigger.getRelatedTable(), trigger.getComment(), includeDependencies);
  }

  /**
   * Retrieve the SQL Source of the given trigger.
   *
   * @param triggerCatalog The catalog in which the trigger is defined. This should be null if the DBMS does not support catalogs
   * @param triggerSchema The schema in which the trigger is defined. This should be null if the DBMS does not support schemas
   * @param triggerName
   * @param triggerTable the table for which the trigger is defined
   * @throws SQLException
   * @return the trigger source
   */
  @Override
  public String getTriggerSource(String triggerCatalog, String triggerSchema, String triggerName, TableIdentifier triggerTable, String trgComment, boolean includeDependencies)
    throws SQLException
  {
    StringBuilder result = new StringBuilder(500);

    if ("*".equals(triggerCatalog)) triggerCatalog = null;
    if ("*".equals(triggerSchema)) triggerSchema = null;

    GetMetaDataSql sql = dbMeta.getMetaDataSQLMgr().getTriggerSourceSql();
    if (sql == null) return StringUtil.EMPTY_STRING;

    sql.setSchema(triggerSchema);
    sql.setCatalog(triggerCatalog);
    sql.setObjectName(triggerName);

    if (triggerTable != null)
    {
      sql.setBaseObjectName(triggerTable.getRawTableName());
      sql.setBaseObjectSchema(triggerTable.getRawSchema());
      sql.setBaseObjectCatalog(triggerTable.getRawCatalog());
    }
    Statement stmt = this.dbConnection.createStatementForQuery();
    String query = null;

    ResultSet rs = null;

    String nl = Settings.getInstance().getInternalEditorLineEnding();

    boolean useSavepoint = dbConnection.getDbSettings().useSavePointForDML();
    Savepoint sp = null;

    try
    {
      if (useSavepoint)
      {
        sp = dbConnection.setSavepoint();
      }

      if (sql.isPreparedStatement())
      {
        query = sql.getBaseSql();
        LogMgr.logMetadataSql(new CallerInfo(){}, "trigger source", query);
        PreparedStatement pstmt = sql.prepareStatement(dbConnection, triggerCatalog, triggerSchema, triggerName);
        stmt = pstmt;
        rs = pstmt.executeQuery();
      }
      else
      {
        query = sql.getSql();
        LogMgr.logMetadataSql(new CallerInfo(){}, "trigger source", query);
        // I am not using executeQuery() because the configured SQL could also be a stored procedure
        stmt.execute(query);
        rs = stmt.getResultSet();
      }

      boolean replaceNL = Settings.getInstance().getBoolProperty("workbench.db." + dbMeta.getDbId() + ".replacenl.triggersource", false);
      boolean addNL = Settings.getInstance().getBoolProperty("workbench.db." + dbMeta.getDbId() + ".triggersource.addnl", false);

      if (rs != null)
      {
        int colCount = rs.getMetaData().getColumnCount();
        while (rs.next())
        {
          for (int i=1; i <= colCount; i++)
          {
            String line = rs.getString(i);
            if (line != null)
            {
              if (replaceNL)
              {
                line = StringUtil.replace(line, "\\n", nl);
              }
              result.append(line);
            }
          }
          if (addNL)
          {
            result.append(nl);
          }
        }
      }

      CharSequence warn = SqlUtil.getWarnings(this.dbConnection, stmt);
      if (warn != null)
      {
        if (result.length() > 0)
        {
          result.append(nl);
          result.append(nl);
        }
        result.append(warn);
      }

      if (includeDependencies)
      {
        if (dbConnection.getDbSettings().createTriggerNeedsAlternateDelimiter())
        {
          DelimiterDefinition delim = dbConnection.getAlternateDelimiter();

          if (result.length() > 0 && delim != null && !delim.isStandard())
          {
            result.append(nl);
            result.append(delim.getDelimiter());
          }
        }

        CommentSqlManager mgr = new CommentSqlManager(this.dbConnection.getMetadata().getDbId());
        String ddl = mgr.getCommentSqlTemplate("trigger", CommentSqlManager.COMMENT_ACTION_SET);
        if (result.length() > 0 && StringUtil.isNonBlank(ddl) && StringUtil.isNonBlank(trgComment))
        {
          result.append(nl);
          String commentSql = ddl.replace(TriggerDefinition.PLACEHOLDER_TRIGGER_NAME, triggerName);
          commentSql = commentSql.replace(TriggerDefinition.PLACEHOLDER_TRIGGER_SCHEMA, triggerSchema);
          if (triggerTable != null)
          {
            commentSql = commentSql.replace(TriggerDefinition.PLACEHOLDER_TRIGGER_TABLE, triggerTable.getTableExpression(dbConnection));
          }
          commentSql = commentSql.replace(CommentSqlManager.COMMENT_PLACEHOLDER, SqlUtil.escapeQuotes(trgComment));
          result.append(nl);
          result.append(commentSql);
          result.append(';');
          result.append(nl);
        }

        CharSequence dependent = getDependentSource(triggerCatalog, triggerSchema, triggerName, triggerTable);
        if (dependent != null)
        {
          result.append(nl);
          result.append(dependent);
        }
      }
      dbConnection.releaseSavepoint(sp);
    }
    catch (SQLException e)
    {
      dbConnection.rollback(sp);
      LogMgr.logMetadataError(new CallerInfo(){}, e, "trigger source", query);
      if (this.dbMeta.isPostgres()) try { this.dbConnection.rollback(); } catch (Throwable th) {}
      result.append(ExceptionUtil.getDisplay(e));
      JdbcUtils.closeAll(rs, stmt);
      return result.toString();
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }

    return result.toString();
  }

  @Override
  public CharSequence getDependentSource(String triggerCatalog, String triggerSchema, String triggerName, TableIdentifier triggerTable)
    throws SQLException
  {
    return null;
  }

  @Override
  public boolean supportsTriggersOnViews()
  {
    if (dbConnection == null) return false;
    return dbConnection.getDbSettings().supportsTriggersOnViews();
  }

}
