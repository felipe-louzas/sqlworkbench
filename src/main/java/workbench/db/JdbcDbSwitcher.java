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

import java.sql.SQLException;
import java.util.List;

/**
 *
 * @author Thomas Kellerer
 */
public class JdbcDbSwitcher
  implements DbSwitcher
{
  public JdbcDbSwitcher()
  {
  }

  @Override
  public boolean supportsSwitching(WbConnection connection)
  {
    return true;
  }

  @Override
  public boolean needsReconnect()
  {
    return false;
  }

  @Override
  public boolean switchDatabase(WbConnection connection, String dbName)
    throws SQLException
  {
      CatalogChanger changer = new CatalogChanger();
      return changer.setCurrentCatalog(connection, dbName);
  }

  @Override
  public String getUrlForDatabase(String originalUrl, String dbName)
  {
    return null;
  }

  @Override
  public List<String> getAvailableDatabases(WbConnection connection)
  {
    return connection.getMetadata().getAllCatalogs();
  }

  @Override
  public String getCurrentDatabase(WbConnection connection)
  {
    return connection.getMetadata().getCurrentCatalog();
  }

}
