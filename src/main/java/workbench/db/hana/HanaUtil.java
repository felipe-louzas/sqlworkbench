/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at.
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
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
package workbench.db.hana;

import java.sql.CallableStatement;
import java.sql.ResultSet;

import workbench.log.CallerInfo;

import workbench.db.DbMetadata;
import workbench.db.DbObject;
import workbench.db.WbConnection;

import workbench.log.LogMgr;

import workbench.db.JdbcUtils;

/**
 *
 * @author Thomas Kellerer
 */
public class HanaUtil
{
  public static String getObjectDefinition(WbConnection connection, DbObject dbo)
  {
    if (dbo == null) return null;
    if (connection == null) return null;

    CallableStatement cstmt = null;
    ResultSet rs = null;
    String source = null;
    try
    {
      cstmt = connection.getSqlConnection().prepareCall("{call get_object_definition(?, ?)} ");

      DbMetadata meta = connection.getMetadata();
      cstmt.setString(1, meta.quoteObjectname(dbo.getSchema()));
      cstmt.setString(2, meta.quoteObjectname(dbo.getObjectName()));
      rs = cstmt.executeQuery();
      if (rs.next())
      {
        source = rs.getString("OBJECT_CREATION_STATEMENT");
        if (source != null)
        {
          source += ";";
        }
      }
    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not read object source for " + dbo.getObjectExpression(connection), ex);
    }
    finally
    {
      JdbcUtils.closeAll(rs, cstmt);
    }
    return source;
  }
}
