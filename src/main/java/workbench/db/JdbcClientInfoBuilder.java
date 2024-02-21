/*
 * This file is part of SQL Workbench/J, http://www.sql-workbench.net
 *
 * Copyright 2002-2013, Thomas Kellerer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at.
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.net
 *
 */
package workbench.db;

import java.net.InetAddress;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.util.StringUtil;

/**
 * A class to set the ClientInfo properties from workbench.settings.
 *
 * @see Connection#setClientInfo(Properties)
 * @author Thomas Kellerer
 */
public class JdbcClientInfoBuilder {

  private final String connectionId;
  private final String jdbcUser;

  public JdbcClientInfoBuilder(String jdbcUser, String connId)
  {
    this.jdbcUser = jdbcUser;
    this.connectionId = connId;
  }

  public void setClientInfo(Connection conn, String url)
  {
    if (conn == null || StringUtil.isBlank(url)) return;

    Properties clientProps = getClientInfo(url);
    if (clientProps.isEmpty()) return;

    try
    {
      LogMgr.logInfo(new CallerInfo(){}, "Setting JDBC client info: " + clientProps);
      conn.setClientInfo(clientProps);
    }
    catch (SQLException ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not call setClientInfo()", ex);
    }
  }

  public Properties getClientInfo(String url)
  {
    String dbms = JdbcUtils.getDBMSName(url);
    String prefix = "workbench.db.clientinfo.";
    String cleanupRegex = Settings.getInstance().getProperty(prefix + "cleanup." + dbms, null);
    Pattern cleanupPattern = getPattern(cleanupRegex);

    List<String> props = Settings.getInstance().getKeysWithPrefix(prefix + dbms);
    Properties clientProps = new Properties();
    for (String key : props)
    {
      String value = Settings.getInstance().getProperty(key, null);
      String infoKey = getLastElement(key);
      value = replaceClientInfoPlaceholders(value, cleanupPattern);
      if (StringUtil.isNotBlank(value) && infoKey != null)
      {
        clientProps.setProperty(infoKey, value);
      }
    }
    return clientProps;
  }

  private Pattern getPattern(String regex)
  {
    if (StringUtil.isEmpty(regex)) return null;
    try
    {
      return Pattern.compile(regex);
    }
    catch (Exception ex)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Invalid cleanup regex provided: " + regex);
    }
    return null;
  }

  private String getLastElement(String key)
  {
    if (key == null) return null;
    String[] elements = key.split("\\.");
    if (elements.length == 1) return key;
    return elements[elements.length - 1];
  }

  private String replaceClientInfoPlaceholders(String value, Pattern cleanup)
  {
    if (StringUtil.isBlank(value)) return null;
    value = value.replace("${application}", ResourceMgr.TXT_PRODUCT_NAME);
    value = value.replace("${version}", ResourceMgr.getBuildNumber().toString());
    value = value.replace("${osuser}", System.getProperty("user.name"));
    value = value.replace("${connectionid}", connectionId);
    value = value.replace("${jdbcuser}", jdbcUser);
    if (value.contains("${clienthost}"))
    {
      value = value.replace("${clienthost}", getHostname());
    }

    if (cleanup != null && value != null)
    {
      Matcher m = cleanup.matcher(value);
      value = m.replaceAll("");
    }
    return value;
  }

  private String getHostname()
  {
    String result = System.getenv("HOSTNAME");
    if (result != null) return result;
    result = System.getenv("COMPUTERNAME");
    if (result != null) return result;

    try
    {
      InetAddress addr = InetAddress.getLocalHost();
      result = addr.getHostName();
      if (StringUtil.isBlank(result))
      {
        result = addr.getHostAddress();
      }
    }
    catch (Exception ex)
    {
    }
    return result;
  }
}
