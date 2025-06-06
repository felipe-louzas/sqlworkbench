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

import java.io.IOException;
import java.io.Writer;

import workbench.db.NoConfigException;
import workbench.db.ProcedureDefinition;
import workbench.db.WbConnection;

import workbench.sql.DelimiterDefinition;

import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class ReportProcedure
{
  public static final String TAG_PROC_DEF = "proc-def";
  public static final String TAG_PROC_NAME = "proc-name";
  public static final String TAG_PROC_FULL_NAME = "proc-full-name";
  public static final String TAG_PROC_CATALOG = "proc-catalog";
  public static final String TAG_PROC_SCHEMA = "proc-schema";
  public static final String TAG_PROC_TYPE = "proc-type";
  public static final String TAG_PROC_SOURCE = "proc-source";
  public static final String TAG_PROC_COMMENT = "proc-comment";

  private ProcedureDefinition procDef;
  private WbConnection dbConn;
  private final TagWriter tagWriter = new TagWriter();
  private StringBuilder indent = new StringBuilder("  ");
  private StringBuilder indent2 = new StringBuilder("    ");
  private String schemaToUse;
  private String fullName;

  public ReportProcedure(ProcedureDefinition def, WbConnection conn)
  {
    this.procDef = def;
    this.dbConn = conn;
    fullName = def != null ? def.getDisplayName() : null;
  }

  public void setFullname(String name)
  {
    this.fullName = name;
  }

  public void setSchemaToUse(String targetSchema)
  {
    schemaToUse = targetSchema;
  }

  public String getSchema()
  {
    if (procDef == null) return null;
    return schemaToUse == null ? procDef.getSchema() : schemaToUse;
  }

  public CharSequence getSource()
  {
    if (this.procDef == null) return null;
    if (this.procDef.getSource() == null)
    {
      try
      {
        this.dbConn.getMetadata().getProcedureReader().readProcedureSource(this.procDef, null, schemaToUse);
      }
      catch (NoConfigException e)
      {
        procDef.setSource("n/a");
      }
    }
    return this.procDef.getSource();
  }

  public void writeXml(Writer out)
    throws IOException
  {
    StringBuilder xml = getXml();
    out.append(xml);
  }

  public ProcedureDefinition getProcedure()
  {
    return procDef;
  }

  public String getProcedureName()
  {
    return procDef.getProcedureName();
  }

  public void setIndent(StringBuilder ind)
  {
    this.indent = ind == null ? new StringBuilder(0) : ind;
    this.indent2 = new StringBuilder(indent);
    this.indent2.append("  ");
  }

  public StringBuilder getXml()
  {
    return getXml(true);
  }

  public StringBuilder getXml(boolean includeSource)
  {
    StringBuilder result = new StringBuilder(500);
    String objectName = procDef.getProcedureName();

    tagWriter.appendOpenTag(result, indent, TAG_PROC_DEF);

    result.append('\n');
    if (!procDef.isPackageProcedure() && procDef.getCatalog() != null)
    {
      tagWriter.appendTag(result, indent2, TAG_PROC_CATALOG, procDef.getCatalog());
    }
    else if (procDef.isPackageProcedure())
    {
      objectName = procDef.getPackageName();
    }
    tagWriter.appendTag(result, indent2, TAG_PROC_SCHEMA, getSchema());
    tagWriter.appendTag(result, indent2, TAG_PROC_NAME, objectName);
    if (StringUtil.isNotBlank(fullName) && StringUtil.stringsAreNotEqual(fullName, objectName))
    {
      tagWriter.appendTag(result, indent2, TAG_PROC_FULL_NAME, fullName);
    }

    if (StringUtil.isNotBlank(procDef.getComment()))
    {
      tagWriter.appendTag(result, indent2, TAG_PROC_COMMENT, procDef.getComment());
    }

    tagWriter.appendTag(result, indent2, TAG_PROC_TYPE, procDef.getObjectType(), "jdbcResultType", Integer.toString(procDef.getResultType()));
    if (includeSource)
    {
      String src = getSource().toString().trim();
      DelimiterDefinition delim = this.dbConn.getAlternateDelimiter();
      if (delim != null)
      {
        src = delim.removeFromEnd(src);
      }
      tagWriter.appendTag(result, indent2, TAG_PROC_SOURCE, src, true);
      //result.append('\n');
    }
    tagWriter.appendCloseTag(result, indent, TAG_PROC_DEF);
    return result;
  }
}
