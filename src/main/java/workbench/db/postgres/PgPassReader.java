/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
 * that restricts the use for certain governments.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.sql-workbench.eu/manual/license.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.eu
 */
package workbench.db.postgres;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.util.FileUtil;
import workbench.util.PlatformHelper;
import workbench.util.StringUtil;
import workbench.util.WbFile;

/**
 *
 * @author Thomas Kellerer
 */
public class PgPassReader
{
  private String host;
  private String port;
  private String database;
  private String pwd;
  private String username;
  private final Pattern linePattern = Pattern.compile("([\\w-/.*]*):([\\d]{1,6}|[*]):([\\*\\w-/.]*):([\\*\\w-/.]*):(.*)");

  public PgPassReader(String url, String user)
  {
    username = user;
    parseUrl(url);
  }

  public static boolean passFileExists()
  {
    File file = getPgPassFile();
    return file != null && file.exists();
  }

  public String getPort()
  {
    return StringUtil.isEmpty(port) ? "5432" : port;
  }

  public String getPwd()
  {
    return pwd;
  }

  public String getHost()
  {
    return host;
  }

  public String getDatabase()
  {
    return database;
  }

  public void setUsername(String name)
  {
    this.username = name;
  }

  public String getPasswordFromFile()
  {
    File pgpass = getPgPassFile();
    return getPasswordFromFile(pgpass);
  }

  String getPasswordFromFile(File pgpass)
  {
    if (pgpass == null) return null;
    if (!pgpass.exists()) return null;

    BufferedReader reader = null;
    String password = null;

    try
    {
      reader = new BufferedReader(new FileReader(pgpass));
      String line = reader.readLine();
      while (line != null)
      {
        password = getPassword(line);
        if (password != null)
        {
          break;
        }
        line = reader.readLine();
      }
    }
    catch (IOException io)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not read pgpass file: " + pgpass.getAbsolutePath(), io);
    }
    finally
    {
      FileUtil.closeQuietely(reader);
    }
    return password;
  }

  private String getPassword(String line)
  {
    if (StringUtil.isEmpty(line)) return null;
    Matcher m = linePattern.matcher(line);
    if (!m.matches()) return null;

    String fileHost = m.group(1);
    String filePort = m.group(2);
    String fileDb = m.group(3);
    String fileUser = m.group(4);

    boolean hostnameEquals = entryEquals(fileHost, host);
    boolean portEquals = entryEquals(filePort, getPort());
    boolean dbEquals = entryEquals(fileDb, database);
    boolean userEquals = entryEquals(fileUser, username);

    if (hostnameEquals && portEquals && userEquals && dbEquals)
    {
      return m.group(5);
    }
    return null;
  }

  private boolean entryEquals(String entry, String compareTo)
  {
    if (entry == null || compareTo == null) return false;
    return entry.equals("*") || entry.equals(compareTo);
  }

  public static WbFile getPgPassFile()
  {
    String passFile = System.getenv("PGPASSFILE");
    WbFile result = null;
    if (passFile == null)
    {
      if (PlatformHelper.isWindows())
      {
        String home = System.getenv("APPDATA");
        result = new WbFile(home + "/postgresql/pgpass.conf");
      }
      else
      {
        String home = System.getenv("HOME");
        result = new WbFile(home + "/.pgpass");
      }
    }
    else
    {
      result = new WbFile(passFile);
    }
    return result;
  }

  public static boolean isPgUrl(String url)
  {
    if (url == null) return false;
    return url.startsWith("jdbc:postgresql:") || url.startsWith("jdbc:pgsql:");
  }

  public static boolean isGreenplumUrl(String url)
  {
    if (url == null) return false;
    return url.startsWith("jdbc:pivotal:greenplum");
  }

  private void parseUrl(String url)
  {
    if (isPgUrl(url))
    {
      parsePgUrl(url);
    }
    else if (isGreenplumUrl(url))
    {
      parseGreenplumUrl(url);
    }
  }

  private void parsePgUrl(String url)
  {
    if (!isPgUrl(url)) return;

    // look at the url after the prefix
    int pos = url.indexOf(':', 5);

    url = url.substring(pos+1);

    if (!url.startsWith("//"))
    {
      host = "localhost";
      database = url;
    }
    else
    {
      url = url.substring(2);
      final int stateHostname = 1;
      final int statePort = 2;
      final int stateDbName = 3;
      final int stateParams = 4;
      int currentState = stateHostname;

      host = "";
      database = "";
      port = "";

      for (int i=0; i < url.length(); i++)
      {
        char c = url.charAt(i);
        switch (c)
        {
          case ':':
            if (currentState == stateHostname)
            {
              currentState = statePort;
              continue;
            }
            break;
          case '/':
            currentState = stateDbName;
            continue;
          case '?':
          case '&':
            currentState = stateParams;
            break;
        }

        switch (currentState)
        {
          case stateHostname:
            host += c;
            break;
          case statePort:
            port += c;
            break;
          case stateDbName:
            database += c;
        }
      }
    }
  }

  private void parseGreenplumUrl(String url)
  {
    // look at the url after the prefix
    int pos = url.indexOf("greenplum") + "greenplum".length();

    url = url.substring(pos + 1);

    if (!url.startsWith("//"))
    {
      host = "localhost";
      database = url;
    }
    else
    {
      url = url.substring(2);
      final int stateHostname = 1;
      final int statePort = 2;
      final int stateDbName = 3;
      final int stateParams = 4;
      final int statePropertyName = 5;
      int currentState = stateHostname;
      String currentPropertyName = "";

      host = "";
      database = "";
      port = "";

      for (int i = 0; i < url.length(); i++)
      {
        char c = url.charAt(i);
        switch (c)
        {
          case ':':
            if (currentState == stateHostname)
            {
              currentState = statePort;
              continue;
            }
            break;
          case ';':
            currentState = statePropertyName;
            currentPropertyName = "";
            continue;
          case '=':
            if (currentState == statePropertyName && currentPropertyName.equals("DatabaseName"))
            {
              currentState = stateDbName;
            }
            continue;
          case '?':
          case '&':
            currentState = stateParams;
            break;
        }

        switch (currentState)
        {
          case stateHostname:
            host += c;
            break;
          case statePort:
            port += c;
            break;
          case stateDbName:
            database += c;
          case statePropertyName:
            currentPropertyName += c;
            break;
        }
      }
    }
  }


}
