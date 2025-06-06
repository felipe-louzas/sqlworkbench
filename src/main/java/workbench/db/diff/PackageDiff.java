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

import workbench.db.report.ReportPackage;
import workbench.db.report.TagWriter;

import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class PackageDiff
{
  public static final String TAG_CREATE_PKG = "create-package";
  public static final String TAG_UPDATE_PKG = "update-package";

  private ReportPackage referencePckg;
  private ReportPackage targetPckg;

  private final TagWriter writer = new TagWriter();
  private StringBuilder indent = StringUtil.emptyBuilder();

  public PackageDiff(ReportPackage reference, ReportPackage target)
  {
    this.referencePckg = reference;
    this.targetPckg = target;
  }

  public StringBuilder getMigrateTargetXml()
  {
    StringBuilder result = new StringBuilder(500);

    boolean isDifferent = true;
    String tagToUse = TAG_CREATE_PKG;

    CharSequence refSource = referencePckg.getSource();
    CharSequence targetSource = targetPckg == null ? null : targetPckg.getSource();

    if (targetSource != null)
    {
      isDifferent = !refSource.toString().trim().equals(targetSource.toString().trim());
      tagToUse = TAG_UPDATE_PKG;
    }

    StringBuilder myIndent = new StringBuilder(indent);
    myIndent.append("  ");
    if (isDifferent)
    {
      writer.appendOpenTag(result, this.indent, tagToUse);
      result.append('\n');
      referencePckg.setIndent(myIndent);
      result.append(referencePckg.getXml(true));
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
