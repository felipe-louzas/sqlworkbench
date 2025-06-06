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

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.util.SqlParsingUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class SelectIntoVerifier
{
  private Pattern selectIntoPattern;

  public SelectIntoVerifier(String dbId)
  {
    String alias = Settings.getInstance().getProperty("workbench.db." + dbId + ".aliasid", null);
    String pattern = Settings.getInstance().getProperty("workbench.db." + dbId + ".selectinto.pattern", null);
    if (pattern == null && alias != null)
    {
      pattern = Settings.getInstance().getProperty("workbench.db." + alias + ".selectinto.pattern", null);
    }
    
    if (pattern != null)
    {
      try
      {
        this.selectIntoPattern = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
      }
      catch (Exception e)
      {
        LogMgr.logError(new CallerInfo(){}, "Incorrect Pattern for detecting SELECT ... INTO <new table> specified", e);
        this.selectIntoPattern = null;
      }
    }
  }

  /**
   * Checks if the given SQL string is actually some kind of table
   * creation "disguised" as a SELECT.
   * <br/>
   * Whether a statement is identified as a SELECT into a new table
   * is defined through the regular expression that can be set for
   * the DBMS using the property:
   * <tt>workbench.sql.[dbid].selectinto.pattern</tt>
   *
   * This method returns true if a Regex has been defined and matches the given SQL
   */
  public boolean isSelectIntoNewTable(String sql)
  {
    if (this.selectIntoPattern == null) return false;
    if (StringUtil.isEmpty(sql)) return false;

    int pos = SqlParsingUtil.getInstance(null).getKeywordPosition("SELECT", sql);
    if (pos > -1)
    {
      sql = sql.substring(pos);
    }
    Matcher m = selectIntoPattern.matcher(sql);
    return m.find();
  }

  /**
   * Returns true if the current DBMS supports a SELECT syntax
   * which creates a new table (e.g. SELECT .. INTO new_table FROM old_table)
   *
   * It simply checks if a regular expression has been defined to
   * detect this kind of statements
   *
   * @see #isSelectIntoNewTable(String)
   */
  public boolean supportsSelectIntoNewTable()
  {
    return this.selectIntoPattern != null;
  }

}
