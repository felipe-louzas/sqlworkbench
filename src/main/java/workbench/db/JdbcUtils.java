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

import java.io.IOException;
import java.io.Reader;
import java.sql.Array;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.SQLXML;
import java.sql.Savepoint;
import java.sql.Statement;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.util.FileUtil;
import workbench.util.StringUtil;
import workbench.util.VersionNumber;

/**
 *
 * @author Thomas Kellerer
 */
public class JdbcUtils
{

  /**
   * Read a JDBC array from a ResultSet casting it to a "native" Java array.
   *
   * @param <T>      the target type
   * @param rs       the result set
   * @param column   the column name to read
   * @param type     the target type
   *
   * @return the array value
   */
  public static <T> T getArray(ResultSet rs, String column, Class<T> type)
  {
    try
    {
      Array array = rs.getArray(column);
      if (rs.wasNull() || array == null) return null;

      return type.cast(array.getArray());
    }
    catch (Throwable th)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not read array for column " + column, th);
      return null;
    }
  }

  /**
   * Check if the server has the minimum specified version.
   *
   * @param con  the connection to the check
   * @param targetVersion the minimum version in the format major.minor (e.g. 8.4)
   *
   * @return true if the server's version is at least the one requested or higher.
   * @see VersionNumber
   * @see WbConnection#getDatabaseVersion()
   */
  public static boolean hasMinimumServerVersion(WbConnection con, String targetVersion)
  {
    if (con == null) return false;
    VersionNumber server = con.getDatabaseVersion();
    VersionNumber target = new VersionNumber(targetVersion);
    return server.isNewerOrEqual(target);
  }

  public static boolean hasMinimumServerVersion(VersionNumber serverVersion, String targetVersion)
  {
    VersionNumber target = new VersionNumber(targetVersion);
    return serverVersion.isNewerOrEqual(target);
  }

  /**
   * Check if the server has the minimum specified version.
   *
   * @param con  the connection to the check
   * @param targetVersion the minimum version in the format major.minor (e.g. 8.4)
   *
   * @return true if the server's version is at least the one requested or higher.
   * @see VersionNumber
   */
  public static boolean hasMinimumServerVersion(Connection con, String targetVersion)
  {
    if (con == null) return false;

    VersionNumber target = new VersionNumber(targetVersion);
    try
    {
      int serverMajor = con.getMetaData().getDatabaseMajorVersion();
      int serverMinor = con.getMetaData().getDatabaseMinorVersion();
      VersionNumber server = new VersionNumber(serverMajor, serverMinor);
      return server.isNewerOrEqual(target);
    }
    catch (Throwable th)
    {
      return false;
    }
  }

  public static boolean hasMiniumDriverVersion(WbConnection con, String targetVersion)
  {
    return hasMiniumDriverVersion(con.getSqlConnection(), targetVersion);
  }

  /**
   * Check if the driver used for the connection has the minimum specified version.
   *
   * @param con  the connection to the check
   * @param targetVersion the minimum version in the format major.minor (e.g. 8.4)
   *
   * @return true if the driver's version is at least the one requested or higher.
   * @see VersionNumber
   */
  public static boolean hasMiniumDriverVersion(Connection con, String targetVersion)
  {
    if (con == null) return false;
    if (targetVersion == null) return false;

    VersionNumber target = new VersionNumber(targetVersion);
    try
    {
      int driverMajor = con.getMetaData().getDriverMajorVersion();
      int driverMinor = con.getMetaData().getDriverMinorVersion();
      VersionNumber driver = new VersionNumber(driverMajor, driverMinor);
      return driver.isNewerOrEqual(target);
    }
    catch (Throwable th)
    {
      return false;
    }
  }

  /**
   * Return the index of the column identified by it's name.
   *
   * @param rs the result set to check
   * @param colname the column name to find
   *
   * @return  he index of the column or -1 if the column was not found
   */
  public static int getColumnIndex(ResultSet rs, String colname)
  {
    try
    {
      if (rs == null) return -1;
      if (StringUtil.isEmpty(colname)) return -1;
      ResultSetMetaData meta = rs.getMetaData();
      return getColumnIndex(meta, colname);
    }
    catch (Exception e)
    {
      // ignore
    }
    return -1;

  }
  public static int getColumnIndex(ResultSetMetaData meta, String colname)
  {
    try
    {
      if (meta == null) return -1;
      if (StringUtil.isEmpty(colname)) return -1;

      int colcount = meta.getColumnCount();
      for (int i=1; i <= colcount; i++)
      {
        String name = meta.getColumnName(i);
        if (colname.equalsIgnoreCase(name)) return i;
        String alias = meta.getColumnLabel(i);
        if (colname.equalsIgnoreCase(alias)) return i;
      }
    }
    catch (Exception e)
    {
      // ignore
    }
    return -1;
  }

  /**
   * Check if the driver of the given connection might buffer
   * results completely before returning from an executeQuery() call
   * <br/>
   * Currently only connections to Postgres and SQL Server are tested.
   * For all others, <tt>false</tt> is returned.
   *
   * @param con the connection to test
   * @return true, if the driver might buffer the results.
   */
  public static boolean driverMightBufferResults(WbConnection con)
  {
    if (con == null) return false;
    switch (DBID.fromConnection(con))
    {
      case Postgres:
        return checkPostgresBuffering(con);
      case SQL_Server:
        return checkSqlServerBuffering(con);
    }
    return false;
  }

  public static boolean checkPostgresBuffering(WbConnection con)
  {
    if (!DBID.Postgres.isDB(con)) return false;

    // Postgres driver always buffers in Autocommit mode
    if (con.getAutoCommit()) return true;
    if (con.getProfile() == null) return true;
    int fetchSize = con.getProfile().getFetchSize();
    return fetchSize <= 0;
  }

  private static boolean checkSqlServerBuffering(WbConnection con)
  {
    String url = con.getUrl();
    if (url.startsWith("jdbc:jtds"))
    {
      // jTDS driver
      return !url.contains("useCursors=false");
    }
    else if (url.startsWith("jdbc:sqlserver"))
    {
      // Newer versions automatically adjust the buffering
      if (hasMiniumDriverVersion(con, "6.0"))
      {
        return false;
      }
      return !url.contains("selectMethod=cursor");
    }
    return false;
  }


  public static SQLXML createXML(String content, WbConnection con)
    throws SQLException
  {
    SQLXML xml = con.getSqlConnection().createSQLXML();
    xml.setString(content);
    return xml;
  }

  public static SQLXML createXML(Clob content, WbConnection con)
    throws SQLException
  {
    return createXML(content.getCharacterStream(), con);
  }

  public static SQLXML createXML(Reader in, WbConnection con)
    throws SQLException
  {
    try
    {
      String xml = FileUtil.readCharacters(in);
      return createXML(xml, con);
    }
    catch (IOException io)
    {
      throw new SQLException("Can not read input data", io);
    }
  }

  public static ResultSet runQuery(WbConnection dbConnection, Statement statement, String sql, boolean useSavepoint)
  {
    ResultSet rs = null;
    Savepoint sp = null;
    try
    {
      if (useSavepoint && !dbConnection.getAutoCommit())
      {
        sp = dbConnection.setSavepoint();
      }
      rs = statement.executeQuery(sql);
      dbConnection.releaseSavepoint(sp);
    }
    catch (Throwable ex)
    {
      dbConnection.rollback(sp);
      LogMgr.logError(new CallerInfo(){}, "Error running query", ex);
    }
    return rs;
  }

  public static void runQuery(WbConnection dbConnection, String sql, boolean useSavepoint, ResultSetProcessor processor)
    throws SQLException
  {
    ResultSet rs = null;
    Savepoint sp = null;
    Statement stmt = null;
    try
    {
      stmt = dbConnection.createStatementForQuery();
      if (useSavepoint && !dbConnection.getAutoCommit())
      {
        sp = dbConnection.setSavepoint();
      }
      rs = stmt.executeQuery(sql);
      processor.process(rs);
      dbConnection.releaseSavepoint(sp);
    }
    finally
    {
      closeAll(rs, stmt);
    }
  }

  public static boolean runStatement(WbConnection dbConnection, String sql)
  {
    Statement stmt = null;
    try
    {
      stmt = dbConnection.getSqlConnection().createStatement();
      stmt.execute(sql);
      return true;
    }
    catch (Throwable ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Error running statement", ex);
    }
    finally
    {
      close(stmt);
    }
    return false;
  }

  public static String getDbIdFromUrl(String url)
  {
    if (StringUtil.isBlank(url)) return null;

    if (url.startsWith("jdbc:postgresql")) return DBID.Postgres.getId();
    if (url.startsWith("jdbc:pgsql")) return DBID.Postgres.getId();
    if (url.startsWith("jdbc:oracle")) return DBID.Oracle.getId();
    if (url.startsWith("jdbc:sqlserver")) return DBID.SQL_Server.getId();
    if (url.startsWith("jdbc:jtds:sqlserver")) return DBID.SQL_Server.getId();
    if (url.startsWith("jdbc:microsoft:sqlserver")) return DBID.SQL_Server.getId();
    if (url.startsWith("jdbc:firebirdsql")) return DBID.Firebird.getId();
    if (url.startsWith("jdbc:h2")) return DBID.H2.getId();
    if (url.startsWith("jdbc:derby")) return DBID.Derby.getId();
    if (url.startsWith("jdbc:hsqldb")) return DBID.HSQLDB.getId();
    if (url.startsWith("jdbc:sapdb:")) return DBID.SAP_DB.getId();
    if (url.startsWith("jdbc:sap:")) return DBID.HANA.getId();
    if (url.startsWith("jdbc:datadirect:openedge")) return DBID.OPENEDGE.getId();
    if (url.startsWith("jdbc:db2")) return DBID.DB2_LUW.getId();
    if (url.startsWith("jdbc:informix")) return DBID.Informix.getId();
    if (url.startsWith("jdbc:cubrid")) return DBID.Cubrid.getId();
    if (url.startsWith("jdbc:sqlite")) return DBID.SQLite.getId();
    if (url.startsWith("jdbc:vertica")) return DBID.Vertica.getId();
    if (url.startsWith("jdbc:mysql")) return DBID.MySQL.getId();
    if (url.startsWith("jdbc:mariadb")) return DBID.MariaDB.getId();

    // take anything between the first and second colon
    String db = url.replaceFirst("[^:]+:([^:]+):[^:]+", "$1");
    return db;
  }

  public static String getDBMSName(String jdbcUrl)
  {
    String prefix = extractPrefix(jdbcUrl);
    return prefix.substring("jdbc:".length(), prefix.length() - 1);
  }

  public static String extractPrefix(String jdbcUrl)
  {
    if (StringUtil.isBlank(jdbcUrl)) return null;
    int pos = jdbcUrl.indexOf(':', "jdbc:".length());
    if (pos < 0) return jdbcUrl;
    return jdbcUrl.substring(0, pos + 1);
  }

  /**
   *  Convenience method to close a ResultSet without a possible
   *  SQLException
   */
  public static void closeResult(ResultSet rs)
  {
    if (rs == null) return;
    clearWarnings(rs);
    try { rs.close(); } catch (Throwable th) {}
  }

  /**
   *  Convenience method to close a Statement without a possible
   *  SQLException
   */
  public static void closeStatement(Statement stmt)
  {
    if (stmt == null) return;
    try { stmt.close(); } catch (Throwable th) {}
  }

  /**
   *  Convenience method to close a ResultSet and a Statement without
   *  a possible SQLException
   */
  public static void closeAll(ResultSet rs, Statement stmt)
  {
    closeResult(rs);
    closeStatement(stmt);
  }

  public static void close(AutoCloseable... toClose)
  {
    if (toClose == null) return;
    for (AutoCloseable cl : toClose)
    {
      close(cl);
    }
  }

  public static void close(AutoCloseable toClose)
  {
    if (toClose == null) return;
    if (toClose instanceof ResultSet)
    {
      clearWarnings((ResultSet)toClose);
    }
    try { toClose.close(); } catch (Throwable th) {}
  }

  public static void clearWarnings(ResultSet rs)
  {
    try
    {
      if (rs != null) rs.clearWarnings();
    }
    catch (Throwable th)
    {
    }
  }

  public static void clearWarnings(WbConnection con, Statement stmt)
  {
    clearWarnings(con);
    clearWarnings(stmt);
  }

  public static void clearWarnings(WbConnection con)
  {
    try
    {
      if (con != null) con.clearWarnings();
    }
    catch (Throwable th)
    {
    }
  }

  public static void clearWarnings(Statement stmt)
  {
    try
    {
      if (stmt != null) stmt.clearWarnings();
    }
    catch (Throwable th)
    {
    }
  }



}
