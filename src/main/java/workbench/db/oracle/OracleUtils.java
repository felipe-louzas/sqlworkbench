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

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.ConnectionProfile;
import workbench.db.DBID;
import workbench.db.JdbcUtils;
import workbench.db.WbConnection;

import workbench.storage.DataStore;

import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

import static workbench.db.DbMetadata.*;

/**
 * Utility methods for Oracle
 *
 * @author Thomas Kellerer
 */
public class OracleUtils
{
  public static final String PROP_KEY_TBLSPACE = "oracle_default_tablespace";
  public static final String PROP_FIX_TIMESTAMPTZ = "workbench.db.oracle.fix.timstamptz";
  public static final String PROP_FIX_PLSQL_RESULTSET = "workbench.db.oracle.fix.plsql.resultset";

  public static final String KEYWORD_EDITIONABLE = "EDITIONABLE";

  public static final Set<String> STANDARD_TYPES = CollectionUtil.caseInsensitiveSet("INTERVALDS", "INTERVALYM", "TIMESTAMP WITH LOCAL TIME ZONE", "TIMESTAMP WITH TIME ZONE",
    "NUMBER", "NUMBER", "NUMBER", "LONG RAW", "RAW", "LONG", "CHAR", "NUMBER", "NUMBER", "NUMBER",
    "FLOAT", "REAL", "VARCHAR2", "DATE", "DATE", "TIMESTAMP", "STRUCT", "ARRAY", "BLOB", "CLOB", "ROWID",
    "XMLType", "SDO_GEOMETRY", "SDO_TOPO_GEOMETRY", "SDO_GEORASTER", "ANYTYPE", "ANYDATA");

  public static enum DbmsMetadataTypes
  {
    procedure,
    trigger,
    index,
    table,
    mview,
    view,
    sequence,
    synonym,
    grant,
    constraint;
  };

  private OracleUtils()
  {
  }

  static boolean getRemarksReporting(WbConnection conn)
  {
    // The old "remarksReporting" property should not be taken from the
    // System properties as a fall-back
    String value = getDriverProperty(conn, "remarksReporting", false);
    if (value == null)
    {
      // Only the new oracle.jdbc.remarksReporting should also be
      // checked in the system properties
      value = getDriverProperty(conn, "oracle.jdbc.remarksReporting", true);
    }
    return StringUtil.stringToBool(value);
  }

  static boolean getMapDateToTimestamp(WbConnection conn)
  {
    if (Settings.getInstance().fixOracleDateType()) return true;
    // if the mapping hasn't been enabled globally, then check the driver property

    // Newer Oracle drivers support a connection property to automatically
    // return DATE columns as Types.TIMESTAMP. We have to mimic that
    // when using our own statement to retrieve column definitions
    String value = getDriverProperty(conn, "oracle.jdbc.mapDateToTimestamp", true);

    // this is what the driver does: it assumes true if nothing was specified
    if (value == null) return true;

    return StringUtil.stringToBool(value);
  }

  static String getDriverProperty(WbConnection con, String property, boolean includeSystemProperty)
  {
    if (con == null) return "false";
    String value = null;
    ConnectionProfile profile = con.getProfile();
    if (profile != null)
    {
      Properties props = profile.getConnectionProperties();
      value = (props != null ? props.getProperty(property, null) : null);
      if (value == null && includeSystemProperty)
      {
        value = System.getProperty(property, null);
      }
    }
    return value;
  }

  /**
   * Checks if the property "remarksReporting" is enabled for the given connection.
   *
   * @param con the connection to test
   *
   * @return true if the driver returns comments for tables and columns
   */
  public static boolean remarksEnabled(WbConnection con)
  {
    if (con == null) return false;
    ConnectionProfile prof = con.getProfile();
    Properties props = prof.getConnectionProperties();
    String value = "false";
    if (props != null)
    {
      value = props.getProperty("remarksReporting", "false");
    }
    return StringUtil.stringToBool(value);
  }

  /**
   * Returns true if the current connection is an Oracle connection that supports partitioning.
   *
   * If the connection is to an Express Edition, this returns false.
   */
  public static boolean supportsPartitioning(WbConnection conn)
  {
    if (conn == null) return false;
    if (!DBID.Oracle.isDB(conn)) return false;
    String info = conn.getDatabaseProductVersion();
    if (StringUtil.isBlank(info)) return false;
    return !info.toLowerCase().contains("express edition");
  }

  /**
   * Checks if the given connection enables the reporting of table comments in MySQL
   *
   * @param con the connection to test
   *
   * @return true if the driver returns comments for tables
   */
  public static boolean remarksEnabledMySQL(WbConnection con)
  {
    if (con == null) return false;
    ConnectionProfile prof = con.getProfile();
    Properties props = prof.getConnectionProperties();
    String value = "false";
    if (props != null)
    {
      value = props.getProperty("useInformationSchema", "false");
    }
    return StringUtil.stringToBool(value);
  }

  public static boolean isCommonUser(WbConnection conn)
  {
    if (conn == null) return false;
    String username = conn.getCurrentUser();
    if (StringUtil.isBlank(username)) return false;
    boolean isSysDba = conn.getProfile().getOracleSysDBA();
    return username.toLowerCase().equals("sys") || isSysDba || username.toLowerCase().startsWith("c##");
  }

  public static boolean hasMultipleContainers(WbConnection conn)
  {
    if (JdbcUtils.hasMinimumServerVersion(conn, "12.1") == false) return false;

    int cdbCount = 0;
    Statement stmt = null;
    ResultSet rs = null;
    try
    {
      stmt = conn.createStatementForQuery();
      rs = stmt.executeQuery("select count(*) from v$pdbs");
      if (rs.next())
      {
        cdbCount = rs.getInt(1);
      }
    }
    catch (Exception ex)
    {
      // ignore
      cdbCount = 0;
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    return cdbCount > 0;
  }

  public static String getCurrentContainer(WbConnection conn)
  {
    String sql =
      "-- SQL Workbench/J \n" +
      "select sys_context('userenv', 'CON_NAME') from dual";

    LogMgr.logMetadataSql(new CallerInfo(){}, "current container", sql);

    return getSingleResult(sql, conn);
  }

  public static String getDefaultTablespace(WbConnection conn)
  {
    if (conn == null) return "";
    readDefaultTableSpace(conn);
    return conn.getSessionProperty(PROP_KEY_TBLSPACE);
  }

  private static synchronized void readDefaultTableSpace(final WbConnection conn)
  {
    if (conn.getSessionProperty(PROP_KEY_TBLSPACE) != null) return;

    String sql =
      "-- SQL Workbench/J \n" +
      "select default_tablespace \n" +
      "from user_users";

    LogMgr.logMetadataSql(new CallerInfo(){}, "default tablespace", sql);

    String tableSpace = getSingleResult(sql, conn);
    conn.setSessionProperty(PROP_KEY_TBLSPACE, tableSpace);
  }

  private static String getSingleResult(String query, WbConnection conn)
  {
    Statement stmt = null;
    ResultSet rs = null;
    String result = null;

    try
    {
      stmt = conn.createStatementForQuery();
      rs = stmt.executeQuery(query);
      if (rs.next())
      {
        result = rs.getString(1);
      }
    }
    catch (SQLException e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error running query:\n" + query, e);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }

    return result;
  }

  public static String getCacheHint()
  {
    boolean useResultCache = Settings.getInstance().getBoolProperty("workbench.db.oracle.metadata.result_cache", false);
    return useResultCache ? "/*+ result_cache */ " : StringUtil.EMPTY_STRING;
  }

  public static boolean checkDefaultTablespace()
  {
    return Settings.getInstance().getBoolProperty("workbench.db.oracle.check_default_tablespace", false);
  }

  public static boolean retrieveTablespaceInfo()
  {
    return Settings.getInstance().getBoolProperty("workbench.db.oracle.retrieve_tablespace", true);
  }

  public static boolean showSystemGeneratedExtendedStats()
  {
    return Settings.getInstance().getBoolProperty("workbench.db.oracle.extended_stats.include.system", false);
  }

  public static boolean shouldAppendTablespace(String tablespace, String defaultTablespace, String objectOwner, String currentUser)
  {
    // no tablespace given --> nothing to append
    if (StringUtil.isBlank(tablespace)) return false;

    // different owner than the current user --> always append
    if (!StringUtil.equalStringIgnoreCase(StringUtil.trimQuotes(objectOwner), currentUser)) return true;

    // current user's table --> dependent on the system setting
    if (!retrieveTablespaceInfo()) return false;

    if (StringUtil.isBlank(defaultTablespace)) return true;
    return (!tablespace.equals(defaultTablespace));
  }

  public static String trimSQLPlusLineContinuation(String input)
  {
    if (StringUtil.isEmpty(input)) return input;
    List<String> lines = StringUtil.getLines(input);
    StringBuilder result = new StringBuilder(input.length());
    for (String line : lines)
    {
      String clean = StringUtil.rtrim(line);
      if (clean.endsWith("-"))
      {
        result.append(clean.substring(0, clean.length() - 1));
      }
      else
      {
        result.append(line);
      }
      result.append('\n');
    }
    return result.toString();
  }

  public static boolean shouldTrimContinuationCharacter(WbConnection conn)
  {
    if (DBID.Oracle.isDB(conn))
    {
      return Settings.getInstance().getBoolProperty("workbench.db.oracle.trim.sqlplus.continuation", false);
    }
    return false;
  }

  public static boolean useUserSpecificCatalogs(WbConnection conn, String requestedSchema)
  {
    if (conn == null) return false;
    return useUserSpecificCatalogs(conn.getCurrentUser(), requestedSchema);
  }

  public static boolean useUserSpecificCatalogs(String currentUser, String requestedSchema)
  {
    return optimizeCatalogQueries() &&
           (StringUtil.isEmpty(requestedSchema) || StringUtil.equalStringIgnoreCase(requestedSchema, currentUser));
  }

  public static boolean optimizeCatalogQueries()
  {
    return Settings.getInstance().getBoolProperty("workbench.db.oracle.prefer_user_catalog_tables", true);
  }

  public static boolean showSetServeroutputFeedback()
  {
    return Settings.getInstance().getBoolProperty("workbench.db.oracle.set_serveroutput.feedback", false);
  }

  public static boolean is12_1_0_2(WbConnection conn)
  {
    if (conn == null) return false;
    if (!JdbcUtils.hasMinimumServerVersion(conn, "12.1")) return false;

    try
    {
      String release = conn.getSqlConnection().getMetaData().getDatabaseProductVersion();
      return is12_1_0_2(release);
    }
    catch (Throwable th)
    {
      return false;
    }
  }

  public static boolean is12_1_0_2(String release)
  {
    int pos = release.indexOf("Release ");
    if (pos < 0) return false;
    int pos2 = release.indexOf(" - ", pos);
    String version = release.substring(pos + "Release".length() + 1, pos2);
    if (!version.startsWith("12")) return false;
    // "12.1.0.2.0"
    String[] elements = version.split("\\.");
    if (elements == null || elements.length < 5) return false;
    try
    {
      int major = Integer.parseInt(elements[0]);
      int minor = Integer.parseInt(elements[1]);
      int first = Integer.parseInt(elements[2]);
      int second = Integer.parseInt(elements[3]);
      if (major < 12) return false;
      if (minor > 1) return true;

      return (first >= 0 && second >= 2);
    }
    catch (Throwable th)
    {
      return false;
    }
  }

  public static boolean cleanupDDLQuotedIdentifiers()
  {
    return Settings.getInstance().getBoolProperty("workbench.db.oracle.dbmsmeta.cleanup.quotes", false);
  }

  public static boolean getUseOracleDBMSMeta(DbmsMetadataTypes type)
  {
    boolean global = Settings.getInstance().getBoolProperty("workbench.db.oracle.use.dbmsmeta", false);
    return Settings.getInstance().getBoolProperty("workbench.db.oracle.use.dbmsmeta." + type.name(), global);
  }

  public static void setUseOracleDBMSMeta(DbmsMetadataTypes type, boolean flag)
  {
    Settings.getInstance().setProperty("workbench.db.oracle.use.dbmsmeta." + type.name(), flag);
  }

  public static boolean showContainerInfo()
  {
    return Settings.getInstance().getBoolProperty("workbench.db.oracle.conninfo.include.container", false);
  }

  public static boolean fixPLSQLResultSetBug()
  {
    return Settings.getInstance().getBoolProperty(PROP_FIX_PLSQL_RESULTSET, true);
  }

  public static int getInternalFetchSize()
  {
    return Settings.getInstance().getIntProperty("workbench.db.oracle.internal.fetchsize", 250);
  }

  public static String[] adjustTableTypes(WbConnection dbConnection, String[] types)
  {
    // When TABLE and MATERIALIZED VIEW is specified for getTables() the Oracle driver returns
    // materialized views twice, so we need to get rid of them.
    // As mviews are automatically returned when TABLE is specified we can remove the mview type
    if (typeIncluded("TABLE", types) && typeIncluded(MVIEW_NAME, types))
    {
      types = CollectionUtil.removeElement(types, MVIEW_NAME);
    }
    else if (typeIncluded(MVIEW_NAME, types) && JdbcUtils.hasMiniumDriverVersion(dbConnection, "12.1"))
    {
      // the 12.x driver does not accept MATERIALIZED VIEW as a type any more
      CollectionUtil.replaceElement(types, MVIEW_NAME, "TABLE");
    }
    return types;
  }

  public static DataStore getPDBs(WbConnection conn)
    throws SQLException
  {
    return SqlUtil.getResultData(conn,
      "select con_id, name, open_mode, restricted \n" +
      "from gv$pdbs", false);
  }

  public static Statement createStatement(WbConnection conn)
    throws SQLException
  {
    Statement stmt = conn.createStatement();
    setFetchSize(stmt);
    return stmt;
  }

  public static PreparedStatement prepareQuery(WbConnection conn, String sql)
    throws SQLException
  {
    PreparedStatement stmt = conn.getSqlConnection().prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
    setFetchSize(stmt);
    return stmt;
  }

  private static void setFetchSize(Statement stmt)
    throws SQLException
  {
    if (stmt == null) return;

    int fetchSize = getInternalFetchSize();
    if (fetchSize > 0)
    {
      stmt.setFetchSize(fetchSize);
    }
  }
}
