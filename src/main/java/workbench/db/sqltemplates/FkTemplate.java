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
package workbench.db.sqltemplates;

/**
 *
 * @author Thomas Kellerer
 */
public class FkTemplate
  extends TemplateHandler
{
  private final String defaultSQL =
    "ALTER TABLE %table_name%\n" +
    "  ADD CONSTRAINT %constraint_name% FOREIGN KEY (%columnlist%)\n" +
    "  REFERENCES %targettable% (%targetcolumnlist%) %fk_match_type%\n" +
    "  %fk_update_rule%\n" +
    "  %fk_delete_rule%\n" +
    "  %deferrable%";

  private final String defaultInlineSQL =
    "CONSTRAINT %constraint_name% FOREIGN KEY (%columnlist%) REFERENCES %targettable% (%targetcolumnlist%) %fk_match_type%\n" +
    "    %fk_update_rule%%fk_delete_rule% %deferrable%";

  private String sql;

  public FkTemplate(String dbid, boolean forInlineUse)
  {
    if (forInlineUse)
    {
      this.sql = getStringProperty("workbench.db." + dbid + ".fk.inline.sql", defaultInlineSQL);
    }
    else
    {
      this.sql = getStringProperty("workbench.db." + dbid + ".fk.sql", defaultSQL);
    }
  }

  public String getSQLTemplate()
  {
    return sql;
  }
}
