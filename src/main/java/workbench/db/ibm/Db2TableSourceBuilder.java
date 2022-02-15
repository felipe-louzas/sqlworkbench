/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2022, Thomas Kellerer
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
package workbench.db.ibm;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.DBID;
import workbench.db.DropType;
import workbench.db.JdbcUtils;
import workbench.db.ObjectSourceOptions;
import workbench.db.TableIdentifier;
import workbench.db.TableSourceBuilder;
import workbench.db.WbConnection;

/**
 *
 * @author Thomas Kellerer
 */
public class Db2TableSourceBuilder
  extends TableSourceBuilder
{
  private boolean checkHistoryTable;
  private final DBID dbid;
  public Db2TableSourceBuilder(WbConnection con)
  {
    super(con);
    dbid = DBID.fromConnection(dbConnection);
    checkHistoryTable = (dbid == DBID.DB2_LUW && JdbcUtils.hasMinimumServerVersion(con, "10.1"));
  }

  @Override
  public void readTableOptions(TableIdentifier table, List<ColumnIdentifier> columns)
  {
    if (!checkHistoryTable) return;
    if (table == null) return;
    if (table.getSourceOptions().isInitialized()) return;

    String sql =
      "select periodname, \n" +
      "       begincolname, \n" +
      "       endcolname, \n" +
      "       historytabschema, \n" +
      "       historytabname \n " +
      "from syscat.periods \n" +
      "where tabschema = ? \n" +
      "  and tabname = ? ";
    PreparedStatement stmt = null;
    ResultSet rs = null;

    String tablename = table.getTableName();
    String schema = table.getSchema();


    LogMgr.logMetadataSql(new CallerInfo(){}, "table options", sql, schema, tablename);

    try
    {
      stmt = this.dbConnection.getSqlConnection().prepareStatement(sql);
      stmt.setString(1, schema);
      stmt.setString(2, tablename);
      rs = stmt.executeQuery();

      if (rs.next())
      {
        String period = rs.getString(1);
        String begin = rs.getString(2);
        String end = rs.getString(3);
        String histSchema = rs.getString(4);
        String histTab = rs.getString(5);
        TableIdentifier histTable = new TableIdentifier(histSchema, histTab);

        ObjectSourceOptions options = table.getSourceOptions();

        String inline = "PERIOD " + period + " (" + begin + ", " + end + ")";
        options.setInlineOption(inline);

        String addSql =
          "ALTER TABLE " + table.getTableExpression(dbConnection) + "\n" +
          "  ADD VERSIONING USE HISTORY TABLE " + histTable.getTableExpression(dbConnection)  + ";\n";
        options.setAdditionalSql(addSql);
      }
    }
    catch (Exception e)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, e, "table options", sql, schema, tablename);
    }
    finally
    {
      JdbcUtils.closeAll(rs, stmt);
    }
    table.getSourceOptions().setInitialized();
  }

  @Override
  public String getNativeTableSource(TableIdentifier table, DropType dropType)
  {
    if (useSystemProc())
    {
      return retrieveTableSource(table, dropType);
    }
    return super.getNativeTableSource(table, dropType);
  }

  public String retrieveTableSource(TableIdentifier tbl, DropType dropType)
  {
    if (tbl == null) return null;
    Db2GenerateSQL gen = new Db2GenerateSQL(dbConnection);
    gen.setGenerateRecreate(dropType != DropType.none);
    CharSequence source = gen.getTableSource(tbl.getRawSchema(), tbl.getRawTableName());
    return source == null ? null : source.toString();
  }

  private boolean useSystemProc()
  {
    if (dbConnection == null) return false;
    return dbConnection.getDbSettings().getBoolProperty("tablesource.use.systemproc", false);
  }

}
