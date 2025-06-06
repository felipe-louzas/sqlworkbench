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

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.oracle.OracleTableGrantReader;
import workbench.db.redshift.RedshiftTableGrantReader;

import workbench.util.StringUtil;

/**
 * @author Thomas Kellerer
 */
public class TableGrantReader
{

  public static TableGrantReader createReader(WbConnection conn)
  {
    switch (DBID.fromConnection(conn))
    {
      case Oracle:
        return new OracleTableGrantReader();
      case Redshift:
        return new RedshiftTableGrantReader();
    }
    return new TableGrantReader();
  }

  /**
   *  Return the GRANTs for the given table
   *
   *  Some JDBC drivers return all GRANT privileges separately even if the original
   *  GRANT was a GRANT ALL ON object TO user.
   *
   *  @return a List with TableGrant objects.
   */
  public Collection<GrantItem> getTableGrants(WbConnection dbConnection, TableIdentifier table)
  {
    Collection<GrantItem> result = new HashSet<>();
    ResultSet rs = null;
    Set<String> ignoreGrantors = dbConnection.getDbSettings().getGrantorsToIgnore();
    Set<String> ignoreGrantees = dbConnection.getDbSettings().getGranteesToIgnore();

    long start = System.currentTimeMillis();
    try
    {
      TableIdentifier tbl = table.createCopy();
      tbl.adjustCase(dbConnection);
      if (Settings.getInstance().getDebugMetadataSql())
      {
        LogMgr.logInfo(new CallerInfo(){}, "Calling DatabaseMetaData.getTablePrivileges() using: " + tbl.getRawCatalog() + ", " + tbl.getRawSchema() + ", " + tbl.getRawTableName());
      }
      rs = dbConnection.getSqlConnection().getMetaData().getTablePrivileges(tbl.getRawCatalog(), tbl.getRawSchema(), tbl.getRawTableName());
      boolean useColumnNames = dbConnection.getDbSettings().useColumnNameForMetadata();
      while (rs.next())
      {
        String from = useColumnNames ? rs.getString("GRANTOR") : rs.getString(4);
        if (ignoreGrantors.contains(from)) continue;

        String to = useColumnNames ? rs.getString("GRANTEE") : rs.getString(5);
        if (ignoreGrantees.contains(to)) continue;

        String what = useColumnNames ? rs.getString("PRIVILEGE") : rs.getString(6);
        boolean grantable = StringUtil.stringToBool(useColumnNames ? rs.getString("IS_GRANTABLE") : rs.getString(7));
        GrantItem grant = new GrantItem(to, what, grantable);
        result.add(grant);
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error when retrieving table privileges",e);
    }
    finally
    {
      try { rs.close(); } catch (Throwable th) {}
    }
    long duration = System.currentTimeMillis() - start;
    LogMgr.logDebug(new CallerInfo(){}, "Calling DatabaseMetaData.getTablePrivileges() took: " + duration + "ms");
    return result;
  }

  /**
   *  Creates an SQL Statement which can be used to re-create the GRANTs on the
   *  given table.
   *
   *  @return SQL script to GRANT access to the table.
   */
  public StringBuilder getTableGrantSource(WbConnection dbConnection, TableIdentifier table)
  {
    Collection<GrantItem> grantList = this.getTableGrants(dbConnection, table);
    StringBuilder result = new StringBuilder(200);
    int count = grantList.size();

    // as several grants to several users can be made, we need to collect them
    // first, in order to be able to build the complete statements
    Map<String, List<String>> grants = new HashMap<>(count);

    for (GrantItem grant : grantList)
    {
      String grantee = grant.getGrantee();
      String priv = grant.getPrivilege();
      if (priv == null) continue;
      List<String> privs = grants.get(grantee);
      if (privs == null)
      {
        privs = new ArrayList<>();
        grants.put(grantee, privs);
      }
      privs.add(priv.trim());
    }
    Iterator<Entry<String, List<String>>> itr = grants.entrySet().iterator();

    String user = dbConnection.getCurrentUser();
    while (itr.hasNext())
    {
      Entry<String, List<String>> entry = itr.next();
      String grantee = entry.getKey();
      // Ignore grants to ourself
      if (user != null && user.equalsIgnoreCase(grantee)) continue;

      List<String> privs = entry.getValue();
      result.append("GRANT ");
      result.append(StringUtil.listToString(privs, ", ", false));
      result.append(" ON ");
      result.append(table.getTableExpression(dbConnection));
      result.append(" TO ");
      result.append(grantee);
      result.append(";\n");
    }
    return result;
  }
}
