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
package workbench.db.report;

import java.util.Collection;
import java.util.Collections;

import workbench.db.GrantItem;
import workbench.db.TableGrantReader;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;


/**
 * Generate XML report information about table grants.
 *
 * @see workbench.db.TableGrantReader#getTableGrants(workbench.db.WbConnection, workbench.db.TableIdentifier)
 * @author Thomas Kellerer
 */
public class ReportTableGrants
{
  public static final String TAG_GRANT = "grant";
  public static final String TAG_GRANT_GRANTEE = "grantee";
  public static final String TAG_GRANT_PRIV = "privilege";
  public static final String TAG_GRANT_GRANTABLE = "grantable";
  private Collection<GrantItem> grants;

  public ReportTableGrants(WbConnection con, TableIdentifier tbl)
  {
    TableGrantReader reader = TableGrantReader.createReader(con);
    grants = reader.getTableGrants(con, tbl);
  }

  public ReportTableGrants(Collection<GrantItem> tableGrants)
  {
    this.grants = tableGrants;
  }

  public void appendXml(StringBuilder result, StringBuilder indent)
  {
    if (grants.isEmpty()) return;

    TagWriter tagWriter = new TagWriter();

    StringBuilder indent1 = new StringBuilder(indent);
    indent1.append("  ");

    for (GrantItem grant : grants)
    {
      tagWriter.appendOpenTag(result, indent, TAG_GRANT);
      result.append('\n');
      tagWriter.appendTag(result, indent1, TAG_GRANT_PRIV, grant.getPrivilege());
      tagWriter.appendTag(result, indent1, TAG_GRANT_GRANTEE, grant.getGrantee());
      tagWriter.appendTag(result, indent1, TAG_GRANT_GRANTABLE, grant.isGrantable());
      tagWriter.appendCloseTag(result, indent, TAG_GRANT);
    }
  }

  public Collection<GrantItem> getGrants()
  {
    return Collections.unmodifiableCollection(grants);
  }

}

