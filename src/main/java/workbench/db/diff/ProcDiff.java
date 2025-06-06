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

import workbench.db.report.ReportProcedure;
import workbench.db.report.TagWriter;

import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class ProcDiff
{
  public static final String TAG_CREATE_PROC = "create-proc";
  public static final String TAG_UPDATE_PROC = "update-proc";

  private ReportProcedure reference;
  private ReportProcedure target;
  private final TagWriter writer = new TagWriter();
  private StringBuilder indent = StringUtil.emptyBuilder();

  public ProcDiff(ReportProcedure ref, ReportProcedure tar)
  {
    reference = ref;
    target = tar;
  }

  public StringBuilder getMigrateTargetXml()
  {
    StringBuilder result = new StringBuilder(500);

    boolean isDifferent = true;
    String tagToUse = TAG_CREATE_PROC;

    CharSequence refSource = reference.getSource();
    CharSequence targetSource = target.getSource();

    if (targetSource != null)
    {
      isDifferent = !refSource.toString().trim().equals(targetSource.toString().trim());
      tagToUse = TAG_UPDATE_PROC;
    }

    if (isDifferent)
    {
      StringBuilder myIndent = new StringBuilder(indent);
      myIndent.append("  ");
      writer.appendOpenTag(result, this.indent, tagToUse);
      result.append('\n');
      reference.setIndent(myIndent);
      reference.setFullname(reference.getProcedure().getDisplayName());
      result.append(reference.getXml());
      writer.appendCloseTag(result, this.indent, tagToUse);
    }

    return result;
  }

  /**
   *  Set an indent for generating the XML
   */
  public void setIndent(StringBuilder ind)
  {
    if (ind == null)
    {
      this.indent = StringUtil.emptyBuilder();
    }
    else
    {
      this.indent = ind;
    }
  }

}
