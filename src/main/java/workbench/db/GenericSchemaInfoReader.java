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

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.Statement;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class GenericSchemaInfoReader
  implements SchemaInformationReader, PropertyChangeListener
{
  private WbConnection connection;
  private final String schemaQuery;
  private final boolean useSavepoint;
  private final boolean reuseStmt;
  private final boolean isCacheable;
  private boolean useJDBC;
  private PreparedStatement query;
  private String cachedSchema;

  private final String reuseProp = "currentschema.reuse.stmt";
  private final String queryProp = "currentschema.query";
  private final String cacheProp = "currentschema.cacheable";
  private final String timeoutProp = "currentschema.timeout";

  public GenericSchemaInfoReader(WbConnection conn, DbSettings settings)
  {
    connection = conn;
    useSavepoint = settings.getBoolProperty("currentschema.query.usesavepoint", false);
    schemaQuery = settings.getProperty(queryProp, null);
    useJDBC = StringUtil.isBlank(schemaQuery) && settings.isGetSchemaImplemented();
    reuseStmt = settings.getBoolProperty(reuseProp, false);
    isCacheable = settings.getBoolProperty(cacheProp, false);
    connection.addChangeListener(this);
    logSettings();
  }

  @Override
  public boolean isSupported()
  {
    return StringUtil.isNotEmpty(schemaQuery) || useJDBC;
  }

  private void logSettings()
  {
    if (useJDBC)
    {
      LogMgr.logDebug(new CallerInfo(){}, connection.getId() + ": Using JDBC API to retrieve current schema, cache current schema: "+ isCacheable);
    }
    else
    {
      LogMgr.logDebug(new CallerInfo(){}, connection.getId() + ": Re-Use statement: " + reuseStmt + ", cache current schema: "+ isCacheable + ", SQL: " + schemaQuery);
    }
  }

  @Override
  public void clearCache()
  {
    this.cachedSchema = null;
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt)
  {
    if (evt.getSource() == this.connection && evt.getPropertyName().equals(WbConnection.PROP_SCHEMA))
    {
      Object value = evt.getNewValue();
      if (value instanceof String)
      {
        this.cachedSchema = (String)value;
      }
      else
      {
        this.cachedSchema = null;
      }
    }
  }

  private int getQueryTimeout()
  {
    int timeout = connection.getDbSettings().getIntProperty(timeoutProp, 0);
    if (timeout < 0) return 0;
    return timeout;
  }

  /**
   * Retrieves the currently active schema from the server.
   *
   * This is done by running the query configured for the passed dbid.
   * If no query is configured or an error is thrown, this method returns null
   *
   * If a configured query throws an error, the query will be ignored for all subsequent calls.
   *
   * @see workbench.db.DbMetadata#getDbId()
   */
  @Override
  public String getCurrentSchema()
  {
    if (this.connection == null) return null;


    if (isCacheable && cachedSchema != null)
    {
      return cachedSchema;
    }
    if (StringUtil.isEmpty(this.schemaQuery) && !useJDBC) return null;

    String currentSchema = null;

    Savepoint sp = null;
    ResultSet rs = null;
    Statement stmt = null;

    final CallerInfo ci = new CallerInfo(){};
    try
    {
      if (useSavepoint)
      {
        sp = connection.setSavepoint(ci);
      }

      if (useJDBC)
      {
        currentSchema = connection.getSqlConnection().getSchema();
      }
      else
      {
        if (reuseStmt)
        {
          if (query == null)
          {
            query = connection.getSqlConnection().prepareStatement(schemaQuery);
          }
          setQueryTimeout(query);
          rs = query.executeQuery();
        }
        else
        {
          stmt = connection.createStatement();
          setQueryTimeout(stmt);
          rs = stmt.executeQuery(schemaQuery);
        }

        if (rs != null && rs.next())
        {
          currentSchema = rs.getString(1);
        }
      }
      currentSchema = StringUtil.trim(currentSchema);
      connection.releaseSavepoint(sp, ci);
    }
    catch (AbstractMethodError ame)
    {
      // Not all drivers actually implement getSchema()
      LogMgr.logError(ci, "Error calling getSchema()", ame);
      useJDBC = false;
      connection.getDbSettings().setProperty("getschema.implemented", false);
      currentSchema = null;
    }
    catch (Exception e)
    {
      connection.rollback(sp, ci);
      if (useJDBC)
      {
        LogMgr.logWarning(ci, "Error reading current schema using Connection.getSchema()", e);
      }
      else
      {
        LogMgr.logWarning(ci, "Error reading current schema using query: " + schemaQuery, e);
      }
      currentSchema = null;
    }
    finally
    {
      if (!useJDBC)
      {
        JdbcUtils.closeAll(rs, stmt);
      }
    }

    if (isCacheable)
    {
      cachedSchema = currentSchema;
      LogMgr.logDebug(ci, "Caching current schema: " + cachedSchema);
    }
    else
    {
      cachedSchema = null;
    }
    return currentSchema;
  }

  private void setQueryTimeout(Statement stmt)
  {
    int timeout = getQueryTimeout();
    try
    {
      if (timeout > 0)
      {
        stmt.setQueryTimeout(timeout);
      }
    }
    catch (Throwable sql)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not set query timeout to " + timeout + " Please adjust the value of the property: " + queryProp, sql);
    }
  }

  @Override
  public String getCachedSchema()
  {
    return cachedSchema;
  }

  @Override
  public void dispose()
  {
    JdbcUtils.closeStatement(query);
    cachedSchema = null;
    connection = null;
    Settings.getInstance().removePropertyChangeListener(this);
  }
}
