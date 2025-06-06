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
package workbench.db.datacopy;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.Collection;
import java.util.Map;

import workbench.interfaces.JobErrorHandler;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.JdbcUtils;
import workbench.db.ResultBufferingController;
import workbench.db.WbConnection;
import workbench.db.importer.DataReceiver;
import workbench.db.importer.RowDataProducer;

import workbench.storage.ResultInfo;
import workbench.storage.RowData;
import workbench.storage.reader.ResultHolder;
import workbench.storage.reader.ResultSetHolder;
import workbench.storage.reader.RowDataReader;
import workbench.storage.reader.RowDataReaderFactory;

import workbench.util.MessageBuffer;
import workbench.util.SqlUtil;
import workbench.util.ValueConverter;

/**
 * Acts as a row data producer to copy the data from a SQL query
 * to another table (and database).
 *
 * When copying a single table, {@link DataCopier} will create the approriate
 * <tt>SELECT</tt> statement to retrieve all rows (and columns) from the source
 * table.
 *
 * @author  Thomas Kellerer
 */
public class QueryCopySource
  implements RowDataProducer
{
  private DataReceiver receiver;
  private volatile boolean keepRunning = true;
  private boolean regularStop = false;
  private final WbConnection sourceConnection;
  private Statement retrieveStatement;
  private final String retrieveSql;
  private boolean abortOnError;
  private final boolean hasErrors = false;
  private final boolean hasWarnings = false;
  private RowData currentRow;
  private final ResultBufferingController resultBuffer;
  private boolean trimCharData;
  private int maxRows;
  private boolean wasCancelled;

  public QueryCopySource(WbConnection source, String sql)
  {
    this.sourceConnection = source;
    this.retrieveSql = SqlUtil.trimSemicolon(sql);
    this.resultBuffer = new ResultBufferingController(source);
  }

  public void setTrimCharData(boolean trim)
  {
    this.trimCharData = trim;
  }

  public void setMaxRows(int maxRows)
  {
    this.maxRows = maxRows;
  }

  @Override
  public void setMessageBuffer(MessageBuffer messages)
  {
  }

  @Override
  public boolean hasErrors()
  {
    return this.hasErrors;
  }

  @Override
  public boolean hasWarnings()
  {
    return this.hasWarnings;
  }

  @Override
  public void setValueConverter(ValueConverter converter)
  {
  }

  @Override
  public void setReceiver(DataReceiver rec)
  {
    this.receiver = rec;
  }

  @Override
  public void start()
    throws Exception
  {
    LogMgr.logInfo(new CallerInfo(){}, "Retrieving source data using:\n" + this.retrieveSql);

    ResultSet rs = null;
    this.keepRunning = true;
    this.regularStop = false;
    this.wasCancelled = false;

    Savepoint sp = null;
    RowDataReader reader = null;

    try
    {
      resultBuffer.disableDriverBuffering();
      sp = DataCopier.setSourceSavepoint(sourceConnection);
      this.retrieveStatement = this.sourceConnection.createStatementForQuery();
      resultBuffer.initializeStatement(retrieveStatement);

      if (this.maxRows > -1)
      {
        this.retrieveStatement.setMaxRows(maxRows);
      }
      rs = this.retrieveStatement.executeQuery(this.retrieveSql);
      ResultInfo info = new ResultInfo(rs.getMetaData(), this.sourceConnection);
      reader = RowDataReaderFactory.createReader(info, sourceConnection);

      // make sure the data is retrieved "as is" from the source. Do not convert it to something readable.
      reader.setConverter(null);
      ResultHolder rh = new ResultSetHolder(rs);

      while (this.keepRunning && rs.next())
      {
        // RowDataReader will make some transformation
        // on the data read from the database
        // which works around some bugs in the Oracle
        // JDBC driver. Especially it will supply
        // CLOB data as a String which I hope will be
        // more flexible when copying from Oracle
        // to other systems
        // That's why I'm reading the result set into a RowData object
        currentRow = reader.read(rh, trimCharData);
        if (!keepRunning) break;

        try
        {
          this.receiver.processRow(currentRow.getData());
        }
        catch (SQLException e)
        {
          if (abortOnError) throw e;
        }
        reader.closeStreams();
      }

      // if keepRunning == false, cancel() was
      // called and we have to tell that the Importer
      // in order to do a rollback
      if (this.keepRunning || regularStop)
      {
        // When copying a schema, we should not send an importFinished()
        // so that the DataImporter reports the table counts correctly
        this.receiver.importFinished();
      }
      else
      {
        this.receiver.importCancelled();
      }
      sourceConnection.releaseSavepoint(sp);
    }
    catch (Exception ex)
    {
      sourceConnection.rollback(sp);
      this.receiver.tableImportError();
      throw ex;
    }
    finally
    {
      resultBuffer.disableDriverBuffering();
      JdbcUtils.closeAll(rs, retrieveStatement);
      if (reader != null)
      {
        reader.closeStreams();
      }
    }
  }

  @Override
  public String getLastRecord()
  {
    if (currentRow == null) return null;
    return currentRow.toString();
  }

  @Override
  public boolean wasCancelled()
  {
    return wasCancelled;
  }

  @Override
  public void stop()
  {
    this.regularStop = true;
    cancel();
    this.wasCancelled = false;
  }

  @Override
  public void cancel()
  {
    this.keepRunning = false;
    this.wasCancelled = true;
    try
    {
      this.retrieveStatement.cancel();
    }
    catch (Exception e)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Error when cancelling retrieve", e);
    }
  }

  @Override
  public Map<Integer, Object> getInputColumnValues(Collection<Integer> inputFileIndexes)
  {
    return null;
  }

  @Override
  public MessageBuffer getMessages()
  {
    return null;
  }

  @Override
  public void setAbortOnError(boolean flag)
  {
    this.abortOnError = flag;
  }

  @Override
  public void setErrorHandler(JobErrorHandler handler)
  {
  }
}
