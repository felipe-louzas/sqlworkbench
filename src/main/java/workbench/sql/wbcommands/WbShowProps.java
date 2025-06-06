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
package workbench.sql.wbcommands;

import java.sql.SQLException;
import java.sql.Types;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import workbench.interfaces.JobErrorHandler;
import workbench.resource.Settings;

import workbench.db.DbSettings;
import workbench.db.WbConnection;

import workbench.storage.DataStore;
import workbench.storage.DmlStatement;
import workbench.storage.RowData;

import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;

import workbench.util.ArgumentParser;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class WbShowProps
  extends SqlCommand
{
  public static final String VERB = "WbProps";
  public static final String ALTERNATE_VERB = "WbShowProps";

  public WbShowProps()
  {
    super();
    cmdLine = new ArgumentParser();
    cmdLine.addArgument("type", StringUtil.stringToList("wb,system,db"));
  }

  @Override
  public StatementRunnerResult execute(String sql)
    throws SQLException, Exception
  {
    StatementRunnerResult result = new StatementRunnerResult(sql);

    String args = getCommandLine(sql);
    cmdLine.parse(args);

    if (cmdLine.hasArguments() || StringUtil.isBlank(args))
    {
      List<String> types = cmdLine.getListValue("type");

      if (types.contains("wb"))
      {
        result.addDataStore(getWbProperties((String)null));
      }

      if (types.isEmpty() || types.contains("system"))
      {
        result.addDataStore(getSystemProperties());
      }

      if (types.contains("db") && currentConnection != null)
      {
        result.addDataStore(getDbProperties("workbench.db." + currentConnection.getDbId()));
      }
    }
    else if (StringUtil.isNotBlank(args))
    {
      if (currentConnection != null)
      {
        args = args.toLowerCase().replace(DbSettings.DBID_PLACEHOLDER, currentConnection.getDbId());
      }
      Map<String, String> shortNames = WbSetProp.getAbbreviations();
      args = shortNames.getOrDefault(args, args);

      DataStore ds = getWbProperties(args);
      if (ds.getRowCount() > 0)
      {
        result.addDataStore(ds);
      }
    }

    result.setSuccess();
    return result;
  }

  static DataStore getWbProperties(String prefix)
  {
    return getWbProperties((s -> prefix == null || s.startsWith(prefix)));
  }

  static DataStore getDbProperties(String prefix)
  {
    Pattern p = Pattern.compile("^" + StringUtil.quoteRegexMeta(prefix) + "_{0,1}[0-9]*\\..+");
    return getWbProperties((s -> prefix == null || p.matcher(s).matches()));
  }

  static DataStore getWbProperties(Predicate<String> filter)
  {
    DataStore data = new PropertyDataStore(true);
    Set<String> keys = Settings.getInstance().getKeys();

    for (String key : keys)
    {
      if (isWorkbenchProperty(key) && filter.test(key))
      {
        int row = data.addRow();
        data.setValue(row, 0, key);
        data.setValue(row, 1, Settings.getInstance().getProperty(key, null));
      }
    }
    data.sortByColumn(0, true);
    data.setResultName("Workbench Properties");
    data.resetStatus();
    return data;
  }

  private static boolean isWorkbenchProperty(String key)
  {
    return key.startsWith("workbench.db")
          || key.startsWith("workbench.console")
          || key.startsWith("workbench.settings")
          || (key.startsWith("workbench.sql") && !key.startsWith("workbench.sql.replace.")
                                              && !key.startsWith("workbench.sql.formatter.")
                                              && !key.startsWith("workbench.sql.search."));
  }

  private DataStore getSystemProperties()
  {
    DataStore data = new PropertyDataStore(false);
    Set<Entry<Object, Object>> entries = System.getProperties().entrySet();
    for (Map.Entry<Object, Object> entry : entries)
    {
      int row = data.addRow();
      data.setValue(row, 0, entry.getKey().toString());
      data.setValue(row, 1, entry.getValue().toString());
    }
    data.sortByColumn(0, true);
    data.setResultName("System Properties");
    data.resetStatus();
    return data;
  }

  @Override
  public String getVerb()
  {
    return VERB;
  }

  @Override
  public String getAlternateVerb()
  {
    return ALTERNATE_VERB;
  }

  @Override
  protected boolean isConnectionRequired()
  {
    return false;
  }

  private static class PropertyDataStore
    extends DataStore
  {
    private final boolean wbProps;
    PropertyDataStore(boolean isWbProps)
    {
      super(new String[] { "PROPERTY", "VALUE"}, new int[] { Types.VARCHAR, Types.VARCHAR} );
      wbProps = isWbProps;
      getColumns()[0].setIsPkColumn(true);
    }

    @Override
    public boolean checkUpdateTable()
    {
      return true;
    }

    @Override
    public boolean checkUpdateTable(WbConnection aConn)
    {
      return true;
    }

    @Override
    public boolean hasPkColumns()
    {
      return true;
    }

    @Override
    public boolean hasUpdateableColumns()
    {
      return true;
    }

    @Override
    public boolean isUpdateable()
    {
      return true;
    }

    @Override
    public boolean needPkForUpdate()
    {
      return true;
    }

    @Override
    public boolean pkColumnsComplete()
    {
      return true;
    }

    @Override
    public synchronized int updateDb(WbConnection aConnection, JobErrorHandler errorHandler)
      throws SQLException
    {
      int rows = 0;
      this.resetUpdateRowCounters();

      for (int row=0; row < getRowCount(); row++)
      {
        RowData rowData = getRow(row);
        if (!rowData.isOriginal())
        {
          String key = getValueAsString(row, 0);
          String value = getValueAsString(row, 1);
          if (wbProps)
          {
            Settings.getInstance().setProperty(key, value);
          }
          else
          {
            System.setProperty(key, value);
          }
          getRow(row).resetStatus();
          rows ++;
        }
      }

      resetUpdateRowCounters();
      RowData row = this.getNextDeletedRow();
      while (row != null)
      {
        String key = row.getValue(0).toString();
        if (wbProps)
        {
          Settings.getInstance().removeProperty(key);
        }
        else
        {
          System.clearProperty(key);
        }
        row = this.getNextDeletedRow();
        rows ++;
      }
      resetStatus();
      return rows;
    }

    @Override
    public List<DmlStatement> getUpdateStatements(WbConnection aConnection)
      throws SQLException
    {
      return Collections.emptyList();
    }
  }

  @Override
  public boolean isWbCommand()
  {
    return true;
  }

}


