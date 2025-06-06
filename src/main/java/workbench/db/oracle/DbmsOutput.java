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
package workbench.db.oracle;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.JdbcUtils;

import workbench.util.StringUtil;

/**
 * A class to control the dbms_output package in Oracle through JDBC
 *
 * @author Thomas Kellerer
 */
public class DbmsOutput
{
  private final Connection conn;
  private boolean enabled = false;
  private long lastSize;
  private final boolean useGetLines;

  public DbmsOutput(Connection aConn)
    throws SQLException
  {
    this.conn = aConn;
    useGetLines = JdbcUtils.hasMinimumServerVersion(conn, "11.0");
  }

  /**
   * Enable Oracle's dbms_output with the specified buffer size
   * This essentially calls dbms_output.enable().
   *
   * @param size the buffer size, if &lt; 0 no limit will be passed, otherwise the specified number
   * @throws SQLException
   */
  public void enable(long size)
    throws SQLException
  {
    if (this.enabled && size == this.lastSize) return;

    CallableStatement enableStatement = null;
    try
    {
      enableStatement = conn.prepareCall( "{call dbms_output.enable(?) }" );
      if (size <= 0)
      {
        enableStatement.setNull(1, Types.BIGINT);
      }
      else
      {
        enableStatement.setLong(1, size);
      }
      enableStatement.executeUpdate();
      this.enabled = true;
      this.lastSize = size;
      LogMgr.logDebug(new CallerInfo(){}, "Support for DBMS_OUTPUT package enabled (max size=" + (size > 0 ? Long.toString(size) : "unlimited") + ")");
    }
    finally
    {
      JdbcUtils.closeStatement(enableStatement);
    }
  }

  /**
   * Disable dbms_output.
   * This simply calls dbms_output.disable();
   */
  public void disable()
    throws SQLException
  {
    CallableStatement disableStatement = null;
    try
    {
      disableStatement = conn.prepareCall( "{call dbms_output.disable}" );
      disableStatement.executeUpdate();
      this.enabled = false;
    }
    finally
    {
      JdbcUtils.closeStatement(disableStatement);
    }
  }

  public boolean isEnabled()
  {
    return enabled;
  }

  /**
   * Retrieve all server messages written with dbms_output.
   *
   * Nothing will be retrieved if enable() was never called or if disable() was called since then.
   *
   * @return all messages written with dbms_output.put_line()
   */
  public String getResult()
    throws SQLException
  {
    if (!this.enabled) return "";
    return retrieveOutput();
  }

  /**
   * Retrieve all server messages written with dbms_output regardless if enable() has been called before.
   *
   * @return all messages written with dbms_output.put_line()
   */
  public String retrieveOutput()
    throws SQLException
  {
    // using dbms_output.get_lines() is substantially faster then dbms_output.get_line()
    // I can't test get_lines on anything before 11.0.
    // So for obsolete versions I'm sticking to the old implementation
    if (useGetLines)
    {
      return retrieveUsingGetLines();
    }
    return retrieveUsingGetLine();
  }

  private String retrieveUsingGetLines()
    throws SQLException
  {
    CallableStatement cstmt = null;
    StringBuilder result = new StringBuilder(1024);
    final int arraySize = 100;
    Array array = null;

    try
    {
      // using dbms_output.get_lines() is substantially faster then using get_line
      cstmt = conn.prepareCall("{call dbms_output.get_lines(?,?)}");
      cstmt.registerOutParameter(1, Types.ARRAY, "DBMSOUTPUT_LINESARRAY");
      cstmt.registerOutParameter(2, Types.INTEGER);
      cstmt.setInt(2, arraySize);
      cstmt.execute();

      int realSize = cstmt.getInt(2);
      while (realSize > 0)
      {
        array = cstmt.getArray(1);
        Object[] lines = (Object[])array.getArray();
        int numLines = Math.min(realSize, lines.length);
        for (int i=0; i < numLines; i++)
        {
          String line = (String)lines[i];
          result.append(StringUtil.rtrim(StringUtil.coalesce((String)line, "")));
          result.append('\n');
        }
        cstmt.execute();
        realSize = cstmt.getInt(2);
      }
    }
    finally
    {
      try
      {
        if (array != null) array.free();
      }
      catch (Throwable th)
      {
        // ignore
      }
      JdbcUtils.closeStatement(cstmt);
    }
    return result.toString();
  }

  private String retrieveUsingGetLine()
    throws SQLException
  {
    CallableStatement cstmt = null;
    StringBuilder result = new StringBuilder(1024);
    try
    {
      cstmt = conn.prepareCall("{call dbms_output.get_line(?,?)}");
      cstmt.registerOutParameter(1, java.sql.Types.VARCHAR);
      cstmt.registerOutParameter(2, java.sql.Types.NUMERIC);

      int status = 0;
      while (status == 0)
      {
        cstmt.execute();
        String line = cstmt.getString(1);
        if (line == null) line = "";
        status = cstmt.getInt(2);
        if (status == 0)
        {
          result.append(StringUtil.rtrim(line));
          result.append('\n');
        }
      }
    }
    finally
    {
      JdbcUtils.closeStatement(cstmt);
    }
    return result.toString();
  }

  public void close()
  {
    try
    {
      this.disable();
    }
    catch (Throwable th)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Error when disabling dbms_output", th);
    }
  }

}
