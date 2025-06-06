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
package workbench.db.firebird;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.DbMetadata;
import workbench.db.IndexColumn;
import workbench.db.IndexDefinition;
import workbench.db.JdbcIndexReader;
import workbench.db.JdbcUtils;
import workbench.db.ReaderFactory;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;


/**
 * A class to retrieve the index information for Firebird 2.5.
 *
 * The Firebird driver does not return information about function based indexes correctly.
 *
 * This class will not work with earlier Firebird releases and should not be instantiated for them.
 *
 * @author Thomas Kellerer
 * @see ReaderFactory#getIndexReader(workbench.db.DbMetadata)
 */
public class FirebirdIndexReader
  extends JdbcIndexReader
{
  private PreparedStatement indexStatement;

  // This is the basic statement from the Jaybird driver, enhanced to support
  // function based indexes.
  private static final String GET_INDEX_INFO =
    "SELECT NULL as TABLE_CAT, \n" +
    "       NULL as TABLE_SCHEM, \n" +
    "       trim(ind.RDB$RELATION_NAME) AS TABLE_NAME, \n" +
    "       case  \n" +
    "           when ind.RDB$UNIQUE_FLAG is null then 1  \n" +
    "           when ind.RDB$UNIQUE_FLAG = 1 then 0 \n" +
    "           else 1 \n" +
    "        end AS NON_UNIQUE, \n" +
    "       NULL as INDEX_QUALIFIER, \n" +
    "       trim(ind.RDB$INDEX_NAME) as INDEX_NAME, \n" +
    "       NULL as \"TYPE\", \n" +
    "       coalesce(ise.rdb$field_position,0) +1 as ORDINAL_POSITION, \n" +
    "       trim(coalesce(ise.rdb$field_name, 'COMPUTED BY '|| ind.rdb$expression_source)) as COLUMN_NAME, \n" +
    "       case \n" +
    "           when ind.RDB$INDEX_TYPE = 1 then 'D'  \n" +
    "           else 'A' \n" +
    "        end as ASC_OR_DESC, \n" +
    "       0 as CARDINALITY, \n" +
    "       0 as \"PAGES\", \n" +
    "       null as FILTER_CONDITION, \n" +
    "       ind.RDB$FOREIGN_KEY \n" +
    "FROM rdb$indices ind \n" +
    " LEFT JOIN rdb$index_segments ise ON ind.rdb$index_name = ise.rdb$index_name \n" +
    "WHERE ind.rdb$relation_name = ? \n" +
    "ORDER BY 4, 6, 8";

  public FirebirdIndexReader(DbMetadata meta)
  {
    super(meta);
  }

  @Override
  public ResultSet getIndexInfo(TableIdentifier table, boolean unique)
    throws SQLException
  {
    if (this.indexStatement != null)
    {
      LogMgr.logWarning(new CallerInfo(){}, "getIndexInfo() called with pending results!");
      indexInfoProcessed();
    }
    WbConnection con = this.metaData.getWbConnection();
    String query = getSQL();

    LogMgr.logMetadataSql(new CallerInfo(){}, "index info", query, table.getTableName());

    this.indexStatement = con.getSqlConnection().prepareStatement(query);
    this.indexStatement.setString(1, table.getRawTableName());
    ResultSet rs = this.indexStatement.executeQuery();
    return rs;
  }

  private String getSQL()
  {
    if (JdbcUtils.hasMinimumServerVersion(this.metaData.getWbConnection(), "5.0"))
    {
      return GET_INDEX_INFO.replace("null as FILTER_CONDITION", "ind.RDB$CONDITION_SOURCE as FILTER_CONDITION");
    }
    return GET_INDEX_INFO;
  }

  @Override
  public void processIndexList(Collection<IndexDefinition> indexList)
  {
    for (IndexDefinition index : indexList)
    {
      List<IndexColumn> columns = index.getColumns();
      String dir = null;
      String computed = null;
      for (IndexColumn col : columns)
      {
        if (dir == null)
        {
          dir = col.getDirection();
        }
        col.setDirection(null);
        if (col.getColumn().startsWith("COMPUTED") && computed == null)
        {
          computed = col.getColumn();
        }
      }
      index.setDirection(dir);
      index.setIndexExpression(computed);
    }
    for (IndexDefinition idx : indexList)
    {
      if (idx.isUnique() && idx.getName().startsWith("RDB$PRIM"))
      {
        idx.setPrimaryKeyIndex(true);
      }
    }
  }

  @Override
  protected String quoteIndexColumn(String colName)
  {
    if (colName.startsWith("COMPUTED")) return colName;
    return super.quoteIndexColumn(colName);
  }

  @Override
  protected void processIndexResultRow(ResultSet rs, IndexDefinition index, TableIdentifier tbl)
    throws SQLException
  {
    String pk = rs.getString("RDB$FOREIGN_KEY");
    if (pk != null)
    {
      index.setAutoGenerated(true);
    }
  }


  @Override
  public void indexInfoProcessed()
  {
    JdbcUtils.closeStatement(indexStatement);
    indexStatement = null;
  }

}
