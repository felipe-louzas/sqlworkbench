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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.util.CaseInsensitiveComparator;
import workbench.util.CollectionUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class ObjectNameFilter
  implements Serializable
{
  public static final String PARAM_CURRENT_USER = "${current_user}";
  public static final String PARAM_CURRENT_SCHEMA = "${current_schema}";
  public static final String PARAM_CURRENT_CATALOG = "${current_catalog}";
  private static final Set<String> PARAMETERS = CollectionUtil.caseInsensitiveSet(PARAM_CURRENT_USER, PARAM_CURRENT_SCHEMA, PARAM_CURRENT_CATALOG);

  private Map<String, String> variables = new TreeMap<>(CaseInsensitiveComparator.INSTANCE);
  private final Set<Pattern> filterPatterns = new HashSet<>();
  private final Set<String> patternSource = CollectionUtil.caseInsensitiveSet();
  private boolean modified;

  /**
   * If true, the filter defines the names to include instead of names to exclude.
   */
  private boolean inclusionFilter;
  /**
   * If true, the filter defines SQL LIKE expressions to be used during schema retrieval.
   */
  private boolean isRetrievalFilter;

  public ObjectNameFilter()
  {
  }

  public boolean isRetrievalFilter()
  {
    return isRetrievalFilter;
  }

  /**
   * Controls if the filter expressions should be treated as SQL LIKE conditions to be used in a JDBC call.
   *
   * Only valid if {@link #isInclusionFilter()} is true.
   *
   * @see #isInclusionFilter()
   */
  public void setRetrievalFilter(boolean flag)
  {
    modified = flag != this.isRetrievalFilter;
    isRetrievalFilter = flag;
  }

  /**
   * Controls if this filter defines object names to be included (flag == true) or excluded (flag == false).
   *
   * @see #setIsRetrievalFilter(boolean)
   */
  public void setInclusionFilter(boolean flag)
  {
    modified = flag != inclusionFilter;
    inclusionFilter = flag;
  }

  /**
   * If true, the filter defines the names to include (=display) instead of names to exclude.
   *
   * @see #setInclusionFilter(boolean)
   */
  public boolean isInclusionFilter()
  {
    return inclusionFilter;
  }

  /**
   * Define the expressions to be used.
   * <br/>
   * This will replace any existing filter definitions and reset the modified flag
   * Empy expressions (null, "") in the collection will be ignored.
   * <br/>
   * If the list is empty the current filter definitions are not changed
   * <br/>
   *
   * This will set the modified flag to false as this method is called when XMLDecoder reads
   * a connection profile.
   *
   * To modify the filter expressions and update the modified flag, use {@link #addExpression(java.lang.String)}
   *
   * @param expressions
   * @see #setExpressionList(java.lang.String)
   * @see #addExpression(java.lang.String)
   */
  public void setFilterExpressions(Collection<String> expressions)
  {
    if (CollectionUtil.isEmpty(expressions)) return;

    patternSource.clear();
    filterPatterns.clear();

    for (String exp : expressions)
    {
      String s = StringUtil.trim(exp);
      if (StringUtil.isNotBlank(s))
      {
        patternSource.add(s);
      }
    }

    for (String exp : expressions)
    {
      addExpression(exp);
    }
    modified = false;
  }

  private boolean usesVariables()
  {
    if (CollectionUtil.isEmpty(patternSource)) return false;
    for (String exp : patternSource)
    {
      if (PARAMETERS.contains(exp)) return true;
    }
    return false;
  }

  public void setReplacements(Map<String, String> replacements)
  {
    if (CollectionUtil.isEmpty(replacements)) return;
    if (!usesVariables()) return;

    filterPatterns.clear();
    variables = new HashMap<>(replacements);

    for (String exp : patternSource)
    {
      String expression = exp;
      if (PARAMETERS.contains(exp))
      {
        expression = replaceVariable(replacements, exp);
      }
      if (StringUtil.isNotBlank(expression))
      {
        addPattern(expression);
      }
    }
  }

  private String replaceVariable(Map<String, String> variables, String expression)
  {
    for (Map.Entry<String, String> entry : variables.entrySet())
    {
      if (expression.equalsIgnoreCase(entry.getKey())) return entry.getValue();
    }
    return expression;
  }

  /**
   * Returns the defined expression values.
   * <br/>
   * The values will be sorted alphabetically
   */
  public Collection<String> getFilterExpressions()
  {
    if (usesVariables() && isRetrievalFilter())
    {
      List<String> result = new ArrayList<>(patternSource.size());
      for (String expression : patternSource)
      {
        String exp = expression;
        if (variables != null)
        {
          if (PARAMETERS.contains(exp))
          {
            exp = replaceVariable(variables, expression);
          }
        }
        result.add(exp);
      }
      return result;
    }
    return Collections.unmodifiableCollection(patternSource);
  }

  public void resetModified()
  {
    modified = false;
  }

  public void removeExpressions()
  {
    modified = CollectionUtil.isNonEmpty(filterPatterns);
    patternSource.clear();
    filterPatterns.clear();
  }

  /**
   * Defines a list of expressions for this filter.
   * <br/>
   * The expressions can be separated by a semicolon, optionally enclosed with double quotes
   * This will replace any existing filter definitions.
   * <br/>
   * If the list is empty the current filter definitions are not changed
   *
   * @param list a semicolon separated list of expressions
   * @see #setFilterExpressions(java.util.Collection)
   */
  public void setExpressionList(String list)
  {
    List<String> items = StringUtil.stringToList(list, ";", true, true);
    setFilterExpressions(items);
  }

  public void addExpression(String exp)
  {
    if (StringUtil.isBlank(exp)) return;

    try
    {
      patternSource.add(exp);
      // parameters can't be compiled right now, we have to wait until the parameters are set
      if (!PARAMETERS.contains(exp))
      {
        addPattern(exp);
      }
    }
    catch (PatternSyntaxException p)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not compile expression: " + exp , p);
    }
    modified = true;
  }

  private void addPattern(String expression)
  {
    try
    {
      filterPatterns.add(Pattern.compile(expression.trim(), Pattern.CASE_INSENSITIVE));
    }
    catch (PatternSyntaxException pse)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not compile expression: " + expression, pse);
    }
  }

  public boolean isModified()
  {
    return modified;
  }

  public boolean isExcluded(String name)
  {
    if (name == null) return inclusionFilter;

    if (CollectionUtil.isEmpty(filterPatterns)) return inclusionFilter;

    for (Pattern p : filterPatterns)
    {
      if (p.matcher(name).matches()) return !inclusionFilter;
    }
    return inclusionFilter;
  }

  public int getSize()
  {
    return (patternSource == null ? 0 : patternSource.size());
  }

  public ObjectNameFilter createCopy()
  {
    ObjectNameFilter copy = new ObjectNameFilter();
    copy.setFilterExpressions(this.patternSource);
    copy.modified = this.modified;
    copy.inclusionFilter = this.inclusionFilter;
    copy.isRetrievalFilter = this.isRetrievalFilter;
    return copy;
  }

  @Override
  public boolean equals(Object obj)
  {
    if (obj == null)
    {
      return false;
    }
    if (getClass() != obj.getClass())
    {
      return false;
    }
    ObjectNameFilter other = (ObjectNameFilter) obj;

    Collection<String> myPatterns = getFilterExpressions();
    Collection<String> otherPatterns = other.getFilterExpressions();
    for (String s : myPatterns)
    {
      if (!otherPatterns.contains(s)) return false;
    }

    for (String s : otherPatterns)
    {
      if (!myPatterns.contains(s)) return false;
    }
    return true;
  }

  @Override
  public int hashCode()
  {
    return 7 + this.filterPatterns.hashCode();
  }

  public void applyFilter(Collection<String> elements)
  {
    if (elements == null) return;
    elements.removeIf(element -> isExcluded(element));
  }

  public String getFilterString()
  {
    Collection<String> expressions = getFilterExpressions();
    if (CollectionUtil.isEmpty(expressions)) return null;
    String result = "";
    for (String exp : expressions)
    {
      if (result.length() > 0) result += ";";

      if (exp.indexOf(';') > -1)
      {
        result += "\"" + exp + "\"";
      }
      else
      {
        result += exp;
      }
    }
    return result;
  }
}
