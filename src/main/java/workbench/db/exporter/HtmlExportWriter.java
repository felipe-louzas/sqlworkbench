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
package workbench.db.exporter;

/**
 * An ExportWriter to generate HTML output.
 *
 * @author  Thomas Kellerer
 * @see HtmlRowDataConverter
 */
public class HtmlExportWriter
  extends ExportWriter
{

  public HtmlExportWriter(DataExporter exp)
  {
    super(exp);
  }

  @Override
  public RowDataConverter createConverter()
  {
    return new HtmlRowDataConverter();
  }

  @Override
  public void configureConverter()
  {
    super.configureConverter();
    HtmlRowDataConverter conv = (HtmlRowDataConverter)this.converter;
    conv.setCreateFullPage(exporter.getCreateFullHtmlPage());
    conv.setEscapeHtml(exporter.getEscapeHtml());
    conv.setHeading(exporter.getHtmlHeading());
    conv.setTrailer(exporter.getHtmlTrailer());
    conv.setNullString(exporter.getNullString());
    conv.setIncludeColumnComments(exporter.getIncludeColumnComments());
  }
}
