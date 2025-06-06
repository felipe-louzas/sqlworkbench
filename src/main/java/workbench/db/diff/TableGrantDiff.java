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
package workbench.db.diff;

import java.util.ArrayList;
import java.util.Collection;

import workbench.db.GrantItem;
import workbench.db.report.ReportTableGrants;
import workbench.db.report.TagWriter;

import workbench.util.StringUtil;


/**
 * @author Thomas Kellerer
 */
public class TableGrantDiff
{
  public static final String TAG_ADD_GRANTS = "add-grants";
  public static final String TAG_REVOKE_GRANTS = "revoke-grants";

  private Collection<GrantItem> referenceGrants;
  private Collection<GrantItem> targetGrants;

  public TableGrantDiff(ReportTableGrants reference, ReportTableGrants target)
  {
    if (reference != null)
    {
      this.referenceGrants = reference.getGrants();
    }

    if (target != null)
    {
      this.targetGrants = target.getGrants();
    }
  }

  public StringBuilder getMigrateTargetXml(TagWriter writer, StringBuilder indent)
  {
    Collection<GrantItem> grantsToAdd = new ArrayList<>();
    if (this.referenceGrants != null)
    {
      grantsToAdd.addAll(this.referenceGrants);
    }
    if (this.targetGrants != null)
    {
      grantsToAdd.removeAll(targetGrants);
    }

    Collection<GrantItem> grantsToRemove = new ArrayList<>();
    if (this.targetGrants != null)
    {
      grantsToRemove.addAll(targetGrants);
    }
    if (this.referenceGrants != null)
    {
      grantsToRemove.removeAll(referenceGrants);
    }

    if (grantsToAdd.isEmpty() && grantsToRemove.isEmpty()) return StringUtil.emptyBuilder();

    StringBuilder result = new StringBuilder(grantsToAdd.size() * 50 + grantsToRemove.size() * 50);
    StringBuilder indent2 = new StringBuilder(indent);
    indent2.append("  ");
    StringBuilder indent3 = new StringBuilder(indent2);
    indent3.append("  ");
    if (grantsToAdd.size() > 0)
    {
      ReportTableGrants report = new ReportTableGrants(grantsToAdd);
      writer.appendOpenTag(result, indent2, TAG_ADD_GRANTS);
      result.append('\n');
      report.appendXml(result, indent3);
      writer.appendCloseTag(result, indent2, TAG_ADD_GRANTS);
      result.append('\n');
    }

    if (grantsToRemove.size() > 0)
    {
      ReportTableGrants report = new ReportTableGrants(grantsToRemove);
      writer.appendOpenTag(result, indent2, TAG_REVOKE_GRANTS);
      result.append('\n');
      report.appendXml(result, indent3);
      writer.appendCloseTag(result, indent2, TAG_REVOKE_GRANTS);
      result.append('\n');
    }
    return result;
  }

}
