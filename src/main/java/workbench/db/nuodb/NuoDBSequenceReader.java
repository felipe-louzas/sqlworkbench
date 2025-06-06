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
package workbench.db.nuodb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.GenerationOptions;
import workbench.db.SequenceDefinition;
import workbench.db.SequenceReader;
import workbench.db.WbConnection;

import workbench.storage.DataStore;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;


/**
 * SequenceReader for <a href="https://www.nuodb">NuoDB</a>
 *
 * @author  Thomas Kellerer
 */
public class NuoDBSequenceReader
  implements SequenceReader
{
  private WbConnection dbConn;
  private String baseQuery;

  public NuoDBSequenceReader(WbConnection conn)
  {
    this.dbConn = conn;
    baseQuery =
      "SELECT schema, \n" +
      "       sequencename\n " +
      "FROM system.sequences";
  }

  @Override
  public DataStore getRawSequenceDefinition(String catalog, String schema, String namePattern)
  {
    StringBuilder query = new StringBuilder(baseQuery.length() + 20);
    query.append(baseQuery);
    boolean whereAdded = false;

    if (StringUtil.isNotBlank(namePattern))
    {
      whereAdded = true;
      query.append(" WHERE ");
      SqlUtil.appendExpression(query, "SEQUENCENAME", StringUtil.trimQuotes(namePattern), dbConn);
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
      SqlUtil.appendExpression(query, "SCHEMA", StringUtil.trimQuotes(schema), null);
    }

    LogMgr.logMetadataSql(new CallerInfo(){}, "sequences", query);

    DataStore result = null;
    try
    {
      result = SqlUtil.getResultData(dbConn, query.toString(), false);
    }
    catch (Throwable e)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, e, "sequences", query);
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

    String name = ds.getValueAsString(row, "SEQUENCENAME");
    String schema = ds.getValueAsString(row, "SCHEMA");
    result = new SequenceDefinition(schema, name);
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
  public CharSequence getSequenceSource(String catalog, String owner, String sequence, GenerationOptions option)
  {
    SequenceDefinition def = getSequenceDefinition(catalog, owner, sequence);
    return buildSource(def);
  }

  protected CharSequence buildSource(SequenceDefinition def)
  {
    if (def == null) return StringUtil.EMPTY_STRING;

    StringBuilder result = new StringBuilder(100);
    result.append("CREATE SEQUENCE ");
    result.append(SqlUtil.buildExpression(dbConn, null, def.getSchema(), def.getSequenceName()));
    result.append(';');
    return result;
  }

  @Override
  public String getSequenceTypeName()
  {
    return SequenceReader.DEFAULT_TYPE_NAME;
  }
}
