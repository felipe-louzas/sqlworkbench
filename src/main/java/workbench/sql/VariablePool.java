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
package workbench.sql;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.storage.DataStore;

import workbench.util.CaseInsensitiveComparator;
import workbench.util.CollectionUtil;
import workbench.util.StringUtil;
import workbench.util.WbProperties;


/**
 * A class to store workbench specific variables.
 *
 * This is a singleton that manages a pool of variables including a global
 * one that is always created. Additional pools are created on demand
 * if a non-null ID is passed to {@link #getInstance(java.lang.String)}
 *
 * When the Pool is created it looks for any variable definition
 * passed through the system properties.
 *
 * Any system property that starts with wbp. is used to define a variable.
 * The name of the variable is the part after the <tt>wbp.</tt> prefix.
 *
 * @see workbench.sql.wbcommands.WbDefineVar
 *
 * @author Thomas Kellerer
 */
public class VariablePool
  implements PropertyChangeListener
{
  public static final String VAR_NAME_LAST_ERROR_CODE= "wb_last_error_code";
  public static final String VAR_NAME_LAST_ERROR_STATE= "wb_last_error_state";
  public static final String VAR_NAME_LAST_ERROR_MSG = "wb_last_error_msg";

  public static final String PROP_PREFIX = "wbp.";
  private static final Map<String, VariablePool> POOLS = new HashMap<>(5);
  private final Map<String, String> data = new TreeMap<>(CaseInsensitiveComparator.INSTANCE);
  private final Map<String, List<String>> lookups = new HashMap<>();
  private final Set<String> globalVars = CollectionUtil.caseInsensitiveSet();
  private final Object lock = new Object();
  private String prefix;
  private String suffix;

  private final Pattern validNamePattern = Pattern.compile("[\\w\\.]+");
  private Pattern promptPattern;
  private Pattern variablePattern;
  private final String poolID;

  /**
   * Returns the global VariablePool.
   */
  public static VariablePool getInstance()
  {
    return InstanceHolder.GLOBAL_INSTANCE;
  }

  /**
   * Returns a named VariablePool.
   */
  public static VariablePool getInstance(String id)
  {
    if (StringUtil.isBlank(id)) return InstanceHolder.GLOBAL_INSTANCE;

    synchronized (getInstance().data)
    {
      VariablePool pool = POOLS.get(id);
      if (pool == null)
      {
        pool = new VariablePool(id);
        pool.data.putAll(getInstance().data);
        POOLS.put(id, pool);
        LogMgr.logDebug(new CallerInfo(){}, "New variable pool with ID=" + id +" created.");
      }
      return pool;
    }
  }

  public static void disposeInstance(String id)
  {
    if (StringUtil.isBlank(id)) return;

    synchronized (getInstance().data)
    {
      VariablePool old = POOLS.remove(id);
      if (old != null)
      {
        old.clear();
      }
    }
    LogMgr.logDebug(new CallerInfo(){}, "Removed variable pool with ID=" + id);
  }

  private static class InstanceHolder
  {
    protected static final VariablePool GLOBAL_INSTANCE = new VariablePool(null);
  }

  private VariablePool(String id)
  {
    this.poolID = id;
    initPromptPattern();
    initFromProperties(System.getProperties());
    Settings.getInstance().addPropertyChangeListener(this, Settings.PROPERTY_VAR_PREFIX, Settings.PROPERTY_VAR_SUFFIX);
  }

  private void initPromptPattern()
  {
    // The promptPattern is cached as this is evaluated each time a SQL is executed
    // rebuild the pattern each time would slow down execution of large SQL scripts too much.
    synchronized (lock)
    {
      String pre = getPrefix();
      String sfx = getSuffix();
      String expr = StringUtil.quoteRegexMeta(pre) + "[\\?&][\\w\\.]+" + StringUtil.quoteRegexMeta(sfx);
      promptPattern = Pattern.compile(expr, Pattern.CASE_INSENSITIVE);

      expr = StringUtil.quoteRegexMeta(pre) + "[\\?&]{0,1}[\\w\\.]+" + StringUtil.quoteRegexMeta(sfx);
      variablePattern = Pattern.compile(expr, Pattern.CASE_INSENSITIVE);
    }
  }

  private String getPrefix()
  {
    synchronized (lock)
    {
      if (prefix == null)
      {
        this.prefix = Settings.getInstance().getSqlParameterPrefix();
      }
      return prefix;
    }
  }

  private String getSuffix()
  {
    synchronized (lock)
    {
      if (suffix == null)
      {
        this.suffix = Settings.getInstance().getSqlParameterSuffix();
        if (StringUtil.isEmpty(this.suffix)) this.suffix = StringUtil.EMPTY_STRING;
      }
      return suffix;
    }
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt)
  {
    synchronized (lock)
    {
      this.prefix = null;
      this.suffix = null;
      initPromptPattern();
    }
  }

  /**
   * For testing purposes only.
   */
  void reset()
  {
    clear();
    synchronized (lock)
    {
      this.prefix = null;
      this.suffix = null;
      initPromptPattern();
    }
  }

  /**
   * Set new values for the prefix and the suffix.
   *
   * @param newPrefix the new prefix, if <tt>null</tt> the built-in default is used
   * @param newSuffix the new suffix, if <tt>null</tt> the built-in default is used
   */
  public void setPrefixSuffix(String newPrefix, String newSuffix)
  {
    synchronized (lock)
    {
      this.prefix = newPrefix;
      this.suffix = newSuffix;
      initPromptPattern();
    }
  }

  final void initFromProperties(Properties props)
  {
    synchronized (this.data)
    {
      this.data.clear();
      for (Map.Entry<Object, Object> entry : props.entrySet())
      {
        String key = (String)entry.getKey();
        if (key.startsWith(PROP_PREFIX))
        {
          String varName = key.substring(PROP_PREFIX.length());
          String value = (String)entry.getValue();
          try
          {
            this.setParameterValue(varName, value);
          }
          catch (IllegalArgumentException e)
          {
            LogMgr.logError(new CallerInfo(){}, "Error setting variable", e);
          }
        }
      }
    }
  }

  /**
   * Removes all variables from the pool.
   */
  public void clear()
  {
    synchronized (this.data)
    {
      this.data.clear();
      this.lookups.clear();
    }
  }

  public void setLastError(Exception ex)
  {
    if (ex instanceof SQLException)
    {
      SQLException sqle = (SQLException)ex;
      setParameterValue(VAR_NAME_LAST_ERROR_CODE, Integer.toString(sqle.getErrorCode()));
      setParameterValue(VAR_NAME_LAST_ERROR_STATE, sqle.getSQLState());
      setParameterValue(VAR_NAME_LAST_ERROR_MSG, sqle.getMessage());
    }
  }

  /**
   * Returns a set of prompt variables defined in the SQL string.
   * <p>
   * If a variable is not yet defined it will
   * be created in the internal pool with an empty value.
   * and returned in the result set.
   *
   * @return a Set containing variable names (String objects)
   */
  public Set<String> getVariablesNeedingPrompt(String sql)
  {
    switch (Settings.getInstance().getVariablePromptStrategy())
    {
      case always:
        return this.getAllUsedVariables(sql);
      case onlyUndefined:
        return this.getAllUndefinedVariables(sql);
      default:
        return this.getPromptVariables(sql, false);
    }
  }

  public DataStore getParametersToBePrompted(String sql)
  {
    Set<String> toPrompt = getVariablesNeedingPrompt(sql);
    if (toPrompt.isEmpty()) return null;
    return getVariablesDataStore(toPrompt, Settings.getInstance().getSortPromptVariables());
  }

  public boolean hasPrompt(String sql)
  {
    if (sql == null) return false;
    Matcher m = this.promptPattern.matcher(sql);
    if (m == null) return false;
    return m.find();
  }

  private Map<String, Pattern> getNamePatterns(Set<String> names)
  {
    HashMap<String, Pattern> result = new HashMap<>();

    for (String name : names)
    {
      String varPattern = buildVarNamePattern(name, false);
      Pattern p = Pattern.compile(varPattern, Pattern.CASE_INSENSITIVE);
      result.put(name, p);
    }
    return result;
  }

  public boolean containsVariable(CharSequence sql, Set<String> names)
  {
    if (StringUtil.isBlank(sql)) return false;
    synchronized (data)
    {
      return containsVariable(sql, names, getNamePatterns(names));
    }
  }

  private boolean containsVariable(CharSequence sql, Set<String> names, Map<String, Pattern> patterns)
  {
    if (StringUtil.isBlank(sql)) return false;

    for (String var : names)
    {
      Pattern p = patterns.get(var);
      if (p != null)
      {
        Matcher m = p.matcher(sql);
        if (m.find())
        {
          String found = m.group();
          if (found != null)
          {
            // check special case: value equals to parameter, including pre- and suffix
            String val = this.data.get(var);
            if (val != null && val.equals(found))
            {
              continue;
            }
          }

          return true;
        }
      }
    }
    return false;
  }

  public Set<String> getAllUsedVariables(String sql)
  {
    return getAllPlaceholder(sql, false);
  }

  public Set<String> getAllUndefinedVariables(String sql)
  {
    return getAllPlaceholder(sql, true);
  }

  private Set<String> getAllPlaceholder(String sql, boolean onlyUndefined)
  {
    if (sql == null) return Collections.emptySet();
    Matcher m = this.variablePattern.matcher(sql);
    if (m == null) return Collections.emptySet();
    Set<String> variables = new TreeSet<>();
    synchronized (this.data)
    {
      while (m.find())
      {
        int start = m.start() + this.getPrefix().length();
        int end = m.end() - this.getSuffix().length();
        String var = sql.substring(start, end);
        if (var.startsWith("?") || var.startsWith("&"))
        {
          var = var.substring(1);
        }
        boolean defined = this.data.containsKey(var);
        if (!defined && onlyUndefined || !onlyUndefined)
        {
          variables.add(var);
        }
      }
    }
    return Collections.unmodifiableSet(variables);
  }

  private Set<String> getPromptVariables(String sql, boolean includeConditional)
  {
    if (sql == null) return Collections.emptySet();
    Matcher m = this.promptPattern.matcher(sql);
    if (m == null) return Collections.emptySet();
    Set<String> variables = new TreeSet<>();
    synchronized (this.data)
    {
      while (m.find())
      {
        int start = m.start() + this.getPrefix().length();
        int end = m.end() - this.getSuffix().length();
        char type = sql.charAt(start);
        String var = sql.substring(start + 1, end);
        if (!includeConditional)
        {
          if ('&' == type)
          {
            String value = this.getParameterValue(var);
            if (value != null && value.length() > 0) continue;
          }
        }
        variables.add(var);
        if (!this.data.containsKey(var))
        {
          this.data.put(var, "");
        }
      }
    }
    return Collections.unmodifiableSet(variables);
  }

  public DataStore getVariablesDataStore()
  {
    synchronized (this.data)
    {
      return this.getVariablesDataStore(data.keySet(), true);
    }
  }

  public DataStore getVariablesDataStore(Set<String> varNames, boolean doSort)
  {
    DataStore vardata = new VariablesDataStore(this.poolID);

    synchronized (this.data)
    {
      for (String key : data.keySet())
      {
        if (varNames.contains(key))
        {
          String value = this.data.get(key);
          int row = vardata.addRow();
          vardata.setValue(row, 0, key);
          vardata.setValue(row, 1, value);
        }
      }
    }

    if (doSort)
    {
      vardata.sortByColumn(0, true);
    }
    vardata.resetStatus();
    return vardata;
  }

  public boolean isDefined(String varName)
  {
    synchronized (data)
    {
      return data.containsKey(varName);
    }
  }

  public String getParameterValue(String varName)
  {
    if (varName == null) return null;
    synchronized (this.data)
    {
      return data.get(varName);
    }
  }

  /**
   * Returns the number of parameters currently defined.
   */
  public int getParameterCount()
  {
    synchronized (this.data)
    {
      return data.size();
    }
  }

  public String replaceAllParameters(String sql)
  {
    if (data.isEmpty()) return sql;
    synchronized (data)
    {
      return replaceAllParameters(sql, data);
    }
  }

  public String replaceAllParameters(String sql, Map<String, String> variables)
  {
    if (variables.isEmpty()) return sql;
    return replaceParameters(variables, sql);
  }

  private String replaceParameters(Map<String, String> variables, String sql)
  {
    if (sql == null) return null;
    if (StringUtil.isBlank(sql)) return StringUtil.EMPTY_STRING;
    if (!sql.contains(this.getPrefix())) return sql;

    StringBuilder newSql = new StringBuilder(sql);
    Set<String> names = variables.keySet();
    Map<String, Pattern> patterns = getNamePatterns(names);

    while (containsVariable(newSql, names, patterns))
    {
      for (String name : names)
      {
        String value = variables.get(name);
        Pattern p = patterns.get(name);
        Matcher m = p.matcher(value);
        // Avoid endless loops if a value contains the variable name again
        if (m.find())
        {
          value = m.replaceAll("");
        }
        replaceVarValue(newSql, p, value);
      }
    }
    return newSql.toString();
  }

  public String removeVariables(String data)
  {
    if (data == null) return data;
    Matcher m = variablePattern.matcher(data);
    if (m == null) return data;
    return m.replaceAll("");
  }

  /**
   * Replaces the variable defined through pattern with the replacement string
   * inside the string original.
   * String.replaceAll() cannot be used, because it parses escape sequences
   */
  private boolean replaceVarValue(StringBuilder original, Pattern pattern, String replacement)
  {
    if (replacement == null || pattern == null) return false;

    Matcher m = pattern.matcher(original);
    int searchStart = 0;
    boolean replaced = false;
    while (m != null && m.find(searchStart))
    {
      int start = m.start();
      int end = m.end();
      original.replace(start, end, replacement);
      m = pattern.matcher(original.toString());
      searchStart = start + replacement.length();
      replaced = true;
      if (searchStart >= original.length()) break;
    }
    return replaced;
  }

  public String buildVarName(String varName, boolean forPrompt)
  {
    StringBuilder result = new StringBuilder(varName.length() + 5);
    result.append(this.getPrefix());
    if (forPrompt) result.append('?');
    result.append(varName);
    String sufx = getSuffix();
    if (StringUtil.isNotEmpty(sufx))
    {
      result.append(sufx);
    }
    return result.toString();
  }

  public String buildVarNamePattern(String varName, boolean forPrompt)
  {
    StringBuilder result = new StringBuilder(varName.length() + 5);
    result.append(StringUtil.quoteRegexMeta(getPrefix()));
    if (forPrompt)
    {
      result.append("[\\?\\&]{1}");
    }
    else
    {
      result.append("[\\?\\&]?");
    }
    result.append(StringUtil.quoteRegexMeta(varName));
    result.append(StringUtil.quoteRegexMeta(getSuffix()));
    return result.toString();
  }

  /**
   * Remove a variable from the pool.
   *
   * @param varName the variable to remove
   *
   * @return
   */
  public int removeVariable(String varName)
  {
    if (varName == null) return 0;

    List<String> toDelete = new ArrayList<>();

    try
    {
      String search = StringUtil.wildcardToRegex(varName, true);
      Pattern p = Pattern.compile(search, Pattern.CASE_INSENSITIVE);
      for (String name : data.keySet())
      {
        Matcher m = p.matcher(name);
        if (m.matches())
        {
          toDelete.add(name);
        }
      }
    }
    catch (Exception ex)
    {
      toDelete.add(varName);
    }

    int deletedCount = 0;
    synchronized (this.data)
    {
      for (String name : toDelete)
      {
        Object old = this.data.remove(name);
        if (old != null) deletedCount++;
      }
    }
    return deletedCount;
  }

  public List<String> getLookupValues(String varName)
  {
    if (varName == null) return null;

    synchronized (this.data)
    {
      return this.lookups.get(varName);
    }
  }

  public void setLookupValues(String varName, List<String> values)
  {
    synchronized (this.data)
    {
      this.lookups.put(varName, values);
      if (!this.data.containsKey(varName))
      {
        this.data.put(varName, "");
      }
    }
  }

  public void setParameterValue(String varName, String value)
    throws IllegalArgumentException
  {
    if (this.isValidVariableName(varName))
    {
      synchronized (this.data)
      {
        this.data.put(varName, value == null ? "" : value);
      }
    }
    else
    {
      String msg = ResourceMgr.getString("ErrIllegalVariableName");
      msg = StringUtil.replace(msg, "%varname%", varName);
      msg = msg + "\n" + ResourceMgr.getString("ErrVarDefWrongName");
      throw new IllegalArgumentException(msg);
    }
  }

  public boolean isValidVariableName(String varName)
  {
    return this.validNamePattern.matcher(varName).matches();
  }

  public void clearGlobalVariables()
  {
    this.globalVars.clear();
  }

  /**
   * Initialize the variables from a commandline parameter.
   * <p>
   * If the parameter starts with the # character
   * assumed that the parameter contains a list of variable definitions
   * enclosed in brackets. e.g. <tt>-vardef="#var1=value1,var2=value2"</tt>
   * The list needs to be quoted on the commandline!
   */
  public void readDefinition(String parameter, boolean asGlobalVars)
    throws Exception
  {
    if (StringUtil.isBlank(parameter)) return;
    if (parameter.charAt(0) == '#')
    {
      readNameList(parameter.substring(1), asGlobalVars);
    }
    else
    {
      readFromFile(parameter, null, asGlobalVars);
    }
  }

  public void parseSingleDefinition(String parameter, boolean asGlobalVar)
  {
    int pos = parameter.indexOf('=');
    if (pos == -1) return;
    String key = parameter.substring(0, pos);
    String value = parameter.substring(pos + 1);
    try
    {
      setParameterValue(key, value);
      if (asGlobalVar)
      {
        globalVars.add(key);
      }
    }
    catch (IllegalArgumentException e)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Ignoring definition: " + parameter);
    }
  }

  private void readNameList(String list, boolean asGlobalVars)
  {
    List<String> defs = StringUtil.stringToList(list, ",");
    for (String line : defs)
    {
      parseSingleDefinition(line, asGlobalVars);
    }
  }

  public Properties removeGlobalVars(Properties props)
  {
    if (props == null) return props;
    Properties applied = new Properties(props);
    for (String key : globalVars)
    {
      applied.remove(key);
    }
    return applied;
  }

  public void readFromProperties(Properties props, String source)
  {
    if (CollectionUtil.isEmpty(props)) return;

    synchronized (data)
    {
      for (String key : props.stringPropertyNames())
      {
        if (globalVars.contains(key))
        {
          LogMgr.logInfo(new CallerInfo(){}, "Not overriding global variable " + key + " with variable from " + source);
        }
        else
        {
          String value = props.getProperty(key);
          setParameterValue(key, value);
        }
      }
    }
  }

  public void removeVariables(Properties props)
  {
    if (props == null) return;

    synchronized (data)
    {
      for (String key : props.stringPropertyNames())
      {
        if (!globalVars.contains(key))
        {
          removeVariable(key);
        }
      }
    }
  }

  /**
   * Read the variable defintions from an external file.
   * The file has to be a regular Java properties file, but does not support
   * line continuation.
   */
  public void readFromFile(String filename, String encoding, boolean asGlobalVars)
    throws IOException
  {
    WbProperties props = new WbProperties(this);
    File f = new File(filename);
    if (!f.exists()) return;

    props.loadTextFile(f, encoding);
    for (Entry<Object, Object> entry : props.entrySet())
    {
      Object key = entry.getKey();
      Object value = entry.getValue();
      if (key != null && value != null)
      {
        this.setParameterValue((String)key, (String)value);
        if (asGlobalVars)
        {
          globalVars.add((String)key);
        }
      }
    }
    String msg = ResourceMgr.getFormattedString("MsgVarDefFileLoaded", f.getAbsolutePath());
    LogMgr.logInfo(new CallerInfo(){}, msg);
  }
}
