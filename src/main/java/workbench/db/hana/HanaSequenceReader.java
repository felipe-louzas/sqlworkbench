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
package workbench.db.hana;


import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.GenerationOptions;
import workbench.db.JdbcUtils;
import workbench.db.SequenceDefinition;
import workbench.db.SequenceReader;
import workbench.db.WbConnection;

import workbench.storage.DataStore;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;


/**
 * SequenceReader for <a href="https://help.sap.com/hana_platform/">SAP Hana</a>
 *
 * @author  Thomas Kellerer
 */
public class HanaSequenceReader
  implements SequenceReader
{
  private WbConnection dbConn;
  private String baseQuery;
  private static final String PROP_RESET_QUERY = "reset_by_query";

  public HanaSequenceReader(WbConnection conn)
  {
    this.dbConn = conn;
    baseQuery =
      "SELECT schema_name, \n" +
      "       sequence_name, \n" +
      "       start_number, \n" +
      "       min_value, \n" +
      "       max_value, \n" +
      "       increment_by, \n" +
      "       is_cycled, \n" +
      "       cache_size, \n" +
      "       reset_by_query \n" +
      "FROM sys.sequences";
  }

  @Override
  public DataStore getRawSequenceDefinition(String catalog, String schema, String namePattern)
  {
    StringBuilder query = new StringBuilder(baseQuery.length() + 20);
    query.append(baseQuery);
    boolean whereAdded = false;
    final CallerInfo ci = new CallerInfo(){};
    if (StringUtil.isNotBlank(namePattern))
    {
      whereAdded = true;
      query.append(" WHERE ");
      SqlUtil.appendExpression(query, "sequence_name", StringUtil.trimQuotes(namePattern), dbConn);
    }

    if (StringUtil.isNotBlank(schema))
    {
      if (!whereAdded)
      {
        query.append(" WHERE ");
      }
      else
      {
        query.append(" AND ");
      }
      SqlUtil.appendExpression(query, "schema_name", StringUtil.trimQuotes(schema), null);
    }


    LogMgr.logMetadataSql(ci, "sequence definition", query);

    PreparedStatement stmt = null;
    ResultSet rs = null;
    DataStore result = null;
    try
    {
      stmt = this.dbConn.getSqlConnection().prepareStatement(query.toString());
      rs = stmt.executeQuery();
      result = new DataStore(rs, true);
    }
    catch (Throwable e)
    {
      LogMgr.logMetadataError(ci, e, "sequence definition", query);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }

    return result;
  }


  @Override
  public List<SequenceDefinition> getSequences(String catalog, String owner, String namePattern)
  {
    DataStore ds = getRawSequenceDefinition(catalog, owner, namePattern);
    if (ds == null) return Collections.emptyList();

    List<SequenceDefinition> result = new ArrayList<>();

    for (int row = 0; row < ds.getRowCount(); row++)
    {
      result.add(createSequenceDefinition(ds, row));
    }
    return result;
  }

  private SequenceDefinition createSequenceDefinition(DataStore ds, int row)
  {
    SequenceDefinition result = null;

    if (ds == null || ds.getRowCount() == 0) return null;

    String catalog = null;
    String name = ds.getValueAsString(row, "sequence_name");
    String schema = ds.getValueAsString(row, "schema_name");
    result = new SequenceDefinition(schema, name);
    result.setCatalog(catalog);

    result.setSequenceProperty(PROP_START_VALUE, ds.getValue(row, "start_number"));
    result.setSequenceProperty(PROP_MAX_VALUE, ds.getValue(row, "max_value"));
    result.setSequenceProperty(PROP_MIN_VALUE, ds.getValue(row, "min_value"));
    result.setSequenceProperty(PROP_INCREMENT, ds.getValue(row, "increment_by"));
    result.setSequenceProperty(PROP_CACHE_SIZE, ds.getValue(row, "cache_size"));
    result.setSequenceProperty(PROP_RESET_QUERY, ds.getValue(row, "reset_by_query"));
    result.setSequenceProperty(PROP_CYCLE, Boolean.valueOf(StringUtil.stringToBool(ds.getValueAsString(row, "is_cycled"))));

    result.setSource(buildSource(result));
    return result;
  }

  @Override
  public SequenceDefinition getSequenceDefinition(String catalog, String owner, String sequence)
  {
    DataStore ds = getRawSequenceDefinition(catalog, owner, sequence);
    if (ds == null) return null;
    return createSequenceDefinition(ds, 0);
  }

  @Override
  public CharSequence getSequenceSource(String catalog, String owner, String sequence, GenerationOptions options)
  {
    SequenceDefinition def = getSequenceDefinition(catalog, owner, sequence);
    return buildSource(def);
  }

  protected CharSequence buildSource(SequenceDefinition def)
  {
    if (def == null) return StringUtil.EMPTY_STRING;

    StringBuilder result = new StringBuilder(100);
    result.append("CREATE SEQUENCE ");
    String nl = Settings.getInstance().getInternalEditorLineEnding();
    result.append(SqlUtil.quoteObjectname(def.getSchema()));
    result.append('.');
    result.append(SqlUtil.quoteObjectname(def.getSequenceName()));

    Number start = (Number)def.getSequenceProperty(PROP_START_VALUE);
    result.append(nl);
    result.append("       START WITH ");
    result.append(start);

    long increment = ((Number)def.getSequenceProperty(PROP_INCREMENT)).longValue();
    result.append(nl);
    result.append("       INCREMENT BY ");
    result.append(increment);

    Number min = (Number)def.getSequenceProperty(PROP_MIN_VALUE);
    result.append(nl);
    if (min == null || min.intValue() == 1 || (increment < 0 && min.toString().equals("-4611686018427387903")))
    {
      result.append("       NO MINVALUE");
    }
    else
    {
      result.append("       MINVALUE");
      result.append(min);
    }

    Number max = (Number)def.getSequenceProperty(PROP_MAX_VALUE);
    result.append(nl);
    if (max == null || max.toString().equals("4611686018427387903") || (increment < 0 && max.intValue() == -1))
    {
      result.append("       NO MAXVALUE");
    }
    else
    {
      result.append("       MAXVALUE");
      result.append(max);
    }

    Boolean cycle = (Boolean)def.getSequenceProperty(PROP_CYCLE);
    result.append(nl);
    result.append("       ");
    if (cycle == null || !cycle)
    {
      result.append("NO");
    }
    result.append(" CYCLE");

    Number cache = (Number)def.getSequenceProperty(PROP_CACHE_SIZE);
    result.append(nl);
    if (cache != null)
    {
      result.append("       CACHE ");
      result.append(cache);
    }
    else
    {
      result.append("       NO CACHE ");
    }

    String resetQuery = (String)def.getSequenceProperty(PROP_RESET_QUERY);
    if (StringUtil.isNotBlank(resetQuery))
    {
      result.append(nl);
      result.append("       RESET BY ");
      result.append(resetQuery);
    }

    result.append(';');
    result.append(nl);
    return result;
  }

  @Override
  public String getSequenceTypeName()
  {
    return SequenceReader.DEFAULT_TYPE_NAME;
  }
}
