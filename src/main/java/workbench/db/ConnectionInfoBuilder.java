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

import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.ssh.SshConfig;

import workbench.db.report.TagWriter;

import workbench.util.StringUtil;

/**
 * A class to generate a summary display of a WbConnection.
 *
 * The information can be presented as HTML or plain text.
 *
 * @author Thomas Kellerer
 */
public class ConnectionInfoBuilder
{

  public ConnectionInfoBuilder()
  {
  }

  public String getHtmlDisplay(WbConnection conn)
  {
    if (conn == null) return "";
    if (conn.isClosed()) return "";
    return getDisplay(conn, true, 0);
  }

  public String getPlainTextDisplay(WbConnection conn, int indent)
  {
    if (conn == null) return "";
    if (conn.isClosed()) return "";
    return getDisplay(conn, false, indent);
  }

  private String getDisplay(WbConnection conn, boolean useHtml, int indent)
  {
    try
    {
      StringBuilder content = new StringBuilder(500);
      if (useHtml) content.append("<html>");

      String space = StringUtil.padRight("", indent);

      String lineStart = useHtml ? "<div style=\"white-space:nowrap;\">" : space;
      String lineEnd = useHtml ? "</div>\n" : "\n";
      String boldStart = useHtml ? "<b>" : "";
      String boldEnd = useHtml ? "</b> " : " ";
      String newLine = useHtml ? "<br>\n" : "\n";

      boolean busy = conn.isBusy();
      DbMetadata wbmeta = conn.getMetadata();

      String username;
      String isolationlevel;
      String productVersion;
      String driverName;
      String autocommit;
      String fetchsize;
      String url = conn.getUrl();
      if (busy)
      {
        username = conn.getDisplayUser();
        isolationlevel = "n/a";
        productVersion = "n/a";
        driverName = "n/a";
        autocommit = "n/a";
        fetchsize = "n/a";
      }
      else
      {
        username = conn.getCurrentUser();
        isolationlevel = conn.getIsolationLevelName();
        productVersion = conn.getDatabaseProductVersion();
        driverName = conn.getSqlConnection().getMetaData().getDriverName();
        autocommit = Boolean.toString(conn.getAutoCommit());
        fetchsize = Integer.toString(conn.getFetchSize());
      }
      String dbVersion = conn.getDatabaseVersion().toString();

      content.append(lineStart + boldStart + ResourceMgr.getString("LblDbProductName") + ":" + boldEnd + wbmeta.getProductName() + lineEnd);
      content.append(lineStart + boldStart + ResourceMgr.getString("LblDbProductVersion") + ":" + boldEnd + dbVersion + lineEnd);
      content.append(lineStart + boldStart + ResourceMgr.getString("LblDbProductInfo") + ":" + boldEnd + productVersion + lineEnd);
      content.append(lineStart + boldStart + ResourceMgr.getString("LblDriverInfoName") + ":" + boldEnd + driverName + lineEnd);
      content.append(lineStart + boldStart + ResourceMgr.getString("LblDriverInfoClass") + ":" + boldEnd + conn.getProfile().getDriverclass() + lineEnd);
      content.append(lineStart + boldStart + ResourceMgr.getString("LblDriverInfoVersion") + ":" + boldEnd + conn.getDriverVersion() + lineEnd);
      content.append(lineStart + boldStart + ResourceMgr.getString("LblDbURL") + ":" + boldEnd + conn.getUrl() + lineEnd);
      SshConfig ssh = conn.getProfile().getSshConfig();
      if (ssh != null)
      {
        content.append(lineStart + boldStart + "SSH:" + boldEnd + ssh.getInfoString() + lineEnd);
      }
      ConnectionPropertiesReader reader = ConnectionPropertiesReader.Factory.getReader(conn);
      Map<String, String> info = reader.getConnectionProperties(conn);
      for (Map.Entry<String, String> entry : info.entrySet())
      {
        content.append(lineStart + boldStart + entry.getKey() + ":" + boldEnd + entry.getValue() + lineEnd);
      }

      content.append(space + boldStart + "Isolation Level:" + boldEnd + isolationlevel + newLine);
      content.append(space + boldStart + "Autocommit:" + boldEnd + autocommit + newLine);
      content.append(space + boldStart + "Fetch size:" + boldEnd + fetchsize + newLine);
      content.append(space + boldStart + ResourceMgr.getString("LblUsername") + ":" + boldEnd + username + newLine);

      String term = wbmeta.getSchemaTerm();
      String s = StringUtil.capitalize(term);
      if (!"schema".equalsIgnoreCase(term))
      {
        s += " (" + ResourceMgr.getString("LblSchema") + ")";
      }
      content.append(space + boldStart + s + ":" + boldEnd + nvl(busy ? "n/a" : conn.getCurrentSchema()) + newLine);

      term = wbmeta.getCatalogTerm();
      s = StringUtil.capitalize(term);
      if (!"catalog".equalsIgnoreCase(term))
      {
        s += " (" +  ResourceMgr.getString("LblCatalog") + ")";
      }
      content.append(space + boldStart + s + ":" + boldEnd + nvl(busy ? "n/a" : conn.getCurrentCatalog()) + newLine);
      content.append(space + boldStart + "Workbench DBID:" + boldEnd + wbmeta.getDbId() + newLine);
      content.append(space + boldStart + "Workbench connection: " + boldEnd + conn.getId());

      JdbcClientInfoBuilder builder = new JdbcClientInfoBuilder(username, "");
      Properties definedProps = builder.getClientInfo(conn.getUrl());
      if (definedProps.size() > 0)
      {
        Properties clientInfo = conn.getSqlConnection().getClientInfo();
        content.append(newLine + newLine + space + boldStart + "JDBC ClientInfo" + boldEnd + newLine);
        content.append("---------------" + newLine);
        for (String key : definedProps.stringPropertyNames())
        {
          content.append(space + boldStart + key + ":" + boldEnd + clientInfo.getProperty(key) + newLine);
        }
      }

      if (useHtml) content.append("</html>");
      return content.toString();
    }
    catch (SQLException e)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not retrieve display", e);
      return e.getMessage();
    }
  }

  private String nvl(String value)
  {
    if (value == null) return "";
    return value;
  }

  /**
   *  Returns information about the DBMS and the JDBC driver
   *  in the XML format used for the XML export
   */
  public StringBuilder getDatabaseInfoAsXml(WbConnection conn, StringBuilder indent)
  {
    StringBuilder dbInfo = new StringBuilder(200);
    DatabaseMetaData db;

    try
    {
      db = conn.getSqlConnection().getMetaData();
    }
    catch (Exception e)
    {
      return StringUtil.emptyBuilder();
    }

    TagWriter tagWriter = new TagWriter();
    String value;

    try { value = db.getDriverName(); } catch (Throwable th) { value = "n/a"; }
    tagWriter.appendTag(dbInfo, indent, "jdbc-driver", cleanValue(value));

    try { value = db.getDriverVersion(); } catch (Throwable th) { value = "n/a"; }
    tagWriter.appendTag(dbInfo, indent, "jdbc-driver-version", cleanValue(value));

    tagWriter.appendTag(dbInfo, indent, "jdbc-url", conn.getUrl());
    tagWriter.appendTag(dbInfo, indent, "database-user", conn.getDisplayUser());

    try { value = db.getDatabaseProductName(); } catch (Throwable th) { value = "n/a"; }
    tagWriter.appendTag(dbInfo, indent, "database-product-name", cleanValue(value));

    try { value = db.getDatabaseProductVersion(); } catch (Throwable th) { value = "n/a"; }
    tagWriter.appendTag(dbInfo, indent, "database-product-version", cleanValue(value));

    return dbInfo;
  }

  /**
   *  Some DBMS have strange characters when reporting their name
   *  This method ensures that an XML "compatible" value is returned in
   *  getDatabaseInfoAsXml
   */
  private String cleanValue(String value)
  {
    if (value == null) return null;
    int len = value.length();
    StringBuilder result = new StringBuilder(len);
    for (int i=0; i < len; i++)
    {
      char c = value.charAt(i);
      if ( (c > 32 && c != 127) || c == 9 || c == 10 || c == 13)
      {
        result.append(c);
      }
      else
      {
        result.append(' ');
      }
    }
    return result.toString();
  }

}
