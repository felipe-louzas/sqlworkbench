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
package workbench.db.oracle;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
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
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.storage.ColumnRemover;
import workbench.storage.DataStore;

import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 *
 * @author  Thomas Kellerer
 */
public class OracleSequenceReader
  implements SequenceReader
{
  private final WbConnection connection;
  private final boolean is12c;
  private final boolean hasIdentitySeqName;
  private final boolean is19c;
  public static final String PROP_EXTEND = "extend";
  public static final String PROP_SCALE = "scale";

  public OracleSequenceReader(WbConnection conn)
  {
    connection = conn;
    is12c = JdbcUtils.hasMinimumServerVersion(connection, "12.1");
    hasIdentitySeqName = OracleUtils.is12_1_0_2(conn);
    is19c = JdbcUtils.hasMinimumServerVersion(connection, "19.0");
  }

  @Override
  public List<SequenceDefinition> getSequences(String catalog, String owner, String namePattern)
  {
    DataStore ds = getRawSequenceDefinition(catalog, owner, namePattern);
    if (ds == null || ds.getRowCount() == 0) return Collections.emptyList();
    ArrayList<SequenceDefinition> result = new ArrayList<>(ds.getRowCount());
    for (int row = 0; row < ds.getRowCount(); row ++)
    {
      result.add(createDefinition(ds, row));
    }
    return result;
  }

  @Override
  public SequenceDefinition getSequenceDefinition(String catalog, String owner, String sequence)
  {
    DataStore ds = getRawSequenceDefinition(catalog, owner, sequence);
    if (ds == null || ds.getRowCount() == 0) return null;
    SequenceDefinition def = createDefinition(ds, 0);
    return def;
  }

  @Override
  public DataStore getRawSequenceDefinition(String catalog, String owner, String sequence)
  {
    StringBuilder sql = new StringBuilder(100);
    sql.append(
      "-- SQL Workbench/J \n" +
      "select " + OracleUtils.getCacheHint() + "s.sequence_owner, \n" +
      "       s.sequence_name, \n" +
      "       s.min_value, \n" +
      "       s.max_value, \n" +
      "       s.increment_by, \n" +
      "       case when cycle_flag = 'Y' then 'CYCLE' else 'NOCYCLE' end as cycle_flag, \n" +
      "       case when order_flag = 'Y' then 'ORDER' else 'NOORDER' end as order_flag, \n" +
      "       s.cache_size, \n" +
      "       s.last_number, \n" +
      (is19c              ? "       s.scale_flag,  \n" : "       null as scale_flag,  \n") +
      (is19c              ? "       s.extend_flag, \n" : "       null as extend_flag, \n") +
      (hasIdentitySeqName ? "       ic.table_name, \n" : "       null as table_name,  \n") +
      (hasIdentitySeqName ? "       ic.column_name \n" : "       null as column_name  \n") +
      "from all_sequences s \n");

    if (hasIdentitySeqName)
    {
      sql.append("  left join all_tab_identity_cols ic on ic.owner = s.sequence_owner and ic.sequence_name = s.sequence_name \n");
    }

    sql.append("WHERE s.sequence_owner = '");

    if (is12c && connection.getDbSettings().hideOracleIdentitySequences() && StringUtil.isEmpty(sequence))
    {
      sql.append("  AND s.sequence_name NOT LIKE 'ISEQ$$%'"); // remove Oracle 12c sequences used for identity columns
    }

    sql.append(StringUtil.trimQuotes(owner));
    sql.append("'\n ");

    if (StringUtil.isNotEmpty(sequence))
    {
      SqlUtil.appendAndCondition(sql, "s.sequence_name", sequence, connection);
    }
    sql.append("\nORDER BY 1,2");

    LogMgr.logMetadataSql(new CallerInfo(){}, "sequence definition", sql);

    Statement stmt = null;
    ResultSet rs = null;
    DataStore result = null;
    try
    {
      stmt = OracleUtils.createStatement(connection);
      rs = stmt.executeQuery(sql.toString());
      result = new DataStore(rs, this.connection, true);
    }
    catch (Exception e)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, e, "sequence definition", sql);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }

    if (!hasIdentitySeqName)
    {
      ColumnRemover remover = new ColumnRemover(result);
      result = remover.removeColumnsByName("TABLE_NAME", "COLUMN_NAME");
    }
    return result;
  }

  @Override
  public CharSequence getSequenceSource(String catalog, String owner, String sequence, GenerationOptions option)
  {
    SequenceDefinition def = getSequenceDefinition(catalog, owner, sequence);
    if (def == null) return null;
    CharSequence source = def.getSource();
    if (option != null && option.getIncludeGrants())
    {
      OracleSequenceGrantReader reader = new OracleSequenceGrantReader();
      String grants = reader.getSequenceGrants(connection, def);
      if (grants != null)
      {
        return source + "\n" + grants;
      }
    }
    return source;
  }

  private SequenceDefinition createDefinition(DataStore ds, int row)
  {
    if (ds == null || row >= ds.getRowCount()) return null;
    String name = ds.getValueAsString(row, "SEQUENCE_NAME");
    String owner = ds.getValueAsString(row, "SEQUENCE_OWNER");
    SequenceDefinition result = new SequenceDefinition(owner, name);
    result.setSequenceProperty(PROP_MIN_VALUE, ds.getValue(row, "MIN_VALUE"));
    result.setSequenceProperty(PROP_MAX_VALUE, ds.getValue(row, "MAX_VALUE"));
    result.setSequenceProperty(PROP_INCREMENT, ds.getValue(row, "INCREMENT_BY"));
    result.setSequenceProperty(PROP_CYCLE, Boolean.toString("CYCLE".equalsIgnoreCase(ds.getValueAsString(row, "CYCLE_FLAG"))));
    result.setSequenceProperty(PROP_CACHE_SIZE, ds.getValue(row, "CACHE_SIZE"));
    result.setSequenceProperty(PROP_ORDERED, Boolean.toString("ORDER".equalsIgnoreCase(ds.getValueAsString(row, "ORDER_FLAG"))));
    result.setSequenceProperty(PROP_SCALE, ds.getValueAsString(row, "SCALE_FLAG"));
    result.setSequenceProperty(PROP_EXTEND, ds.getValueAsString(row, "EXTEND_FLAG"));

    int tblIndex = ds.getColumnIndex("TABLE_NAME");
    if (tblIndex > -1)
    {
      String table = ds.getValueAsString(row, "TABLE_NAME");
      String col = ds.getValueAsString(row, "COLUMN_NAME");
      if (table != null && col != null)
      {
        TableIdentifier seqTable = new TableIdentifier(owner, table);
        result.setRelatedTable(seqTable, col);
      }
    }
    buildSource(result);
    return result;
  }


  private void buildSource(SequenceDefinition def)
  {
    if (def == null) return;
    if (def.getSource() != null) return;

    if (OracleUtils.getUseOracleDBMSMeta(OracleUtils.DbmsMetadataTypes.sequence))
    {
      try
      {
        String source = DbmsMetadata.getDDL(connection, "SEQUENCE", def.getObjectName(), def.getSchema());
        def.setSource(source);
        return;
      }
      catch (SQLException sql)
      {
        // logging already done
      }
    }

    StringBuilder result = new StringBuilder(100);
    String nl = Settings.getInstance().getInternalEditorLineEnding();

    TableIdentifier tbl = def.getRelatedTable();
    String col = def.getRelatedColumn();
    if (tbl != null && col != null)
    {
      result.append("-- identity sequence for " + tbl.getTableName() + "." + col);
      result.append(nl);
    }

    result.append("CREATE SEQUENCE ");
    result.append(def.getSequenceName());

    Number minValue = (Number) def.getSequenceProperty(PROP_MIN_VALUE);
    Number maxValue = (Number) def.getSequenceProperty(PROP_MAX_VALUE);

    Number increment = (Number) def.getSequenceProperty(PROP_INCREMENT);

    String cycle = (String) def.getSequenceProperty(PROP_CYCLE);
    String order = (String) def.getSequenceProperty(PROP_ORDERED);
    Number cache = (Number) def.getSequenceProperty(PROP_CACHE_SIZE);

    result.append(nl).append("       INCREMENT BY ");
    result.append(increment);

    if (minValue != null && minValue.intValue() != 1)
    {
      result.append(nl).append("       MINVALUE ");
      result.append(minValue);
    }
    else
    {
      result.append(nl).append("       NOMINVALUE");
    }

    if (maxValue != null && !maxValue.toString().startsWith("999999999999999999999999999"))
    {
      result.append(nl).append("       MAXVALUE ");
      result.append(maxValue);
    }
    else
    {
      result.append(nl).append("       NOMAXVALUE");
    }

    if (cache != null && cache.longValue() > 0)
    {
      result.append(nl).append("       CACHE ");
      result.append(cache);
    }
    else
    {
      result.append(nl).append("       NOCACHE");
    }
    result.append(nl).append("       ");
    result.append(Boolean.parseBoolean(cycle) ? "CYCLE" : "NOCYCLE");

    result.append(nl).append("       ");
    result.append(Boolean.parseBoolean(order) ? "ORDER" : "NOORDER");

    if (is19c)
    {
      String scale = (String)def.getSequenceProperty(PROP_SCALE);
      String extend = (String)def.getSequenceProperty(PROP_EXTEND);
      if ("Y".equalsIgnoreCase(scale))
      {
        result.append(nl).append("       SCALE");
        if ("Y".equalsIgnoreCase(extend))
        {
          result.append(" EXTEND");
        }
      }
    }
    result.append(';');
    result.append(nl);

    def.setSource(result);
  }

  @Override
  public String getSequenceTypeName()
  {
    return SequenceReader.DEFAULT_TYPE_NAME;
  }
}
