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
package workbench.db.importer;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import workbench.interfaces.BatchCommitter;
import workbench.interfaces.Committer;
import workbench.interfaces.ImportFileParser;
import workbench.interfaces.Interruptable;
import workbench.interfaces.ProgressReporter;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.ArrayValueHandler;
import workbench.db.ColumnIdentifier;
import workbench.db.DBID;
import workbench.db.DbMetadata;
import workbench.db.DbObjectFinder;
import workbench.db.DbSettings;
import workbench.db.DmlExpressionBuilder;
import workbench.db.DmlExpressionType;
import workbench.db.JdbcUtils;
import workbench.db.PkDefinition;
import workbench.db.SequenceAdjuster;
import workbench.db.TableCreator;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;
import workbench.db.compare.BatchedStatement;

import workbench.storage.BlobLiteralType;
import workbench.storage.ColumnData;
import workbench.storage.RowActionMonitor;
import workbench.storage.SqlLiteralFormatter;
import workbench.storage.reader.TimestampTZHandler;

import workbench.util.BlobDecoder;
import workbench.util.CaseInsensitiveComparator;
import workbench.util.CollectionUtil;
import workbench.util.ConverterException;
import workbench.util.EncodingUtil;
import workbench.util.ExceptionUtil;
import workbench.util.FileUtil;
import workbench.util.MemoryWatcher;
import workbench.util.MessageBuffer;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * Import data that is provided from a {@link RowDataProducer} into
 * a table in the database.
 *
 * @see workbench.sql.wbcommands.WbImport
 * @see workbench.sql.wbcommands.WbCopy
 * @see workbench.db.datacopy.DataCopier
 *
 * @author  Thomas Kellerer
 */
public class DataImporter
  implements Interruptable, DataReceiver, ProgressReporter, BatchCommitter, ColumnFilter
{
  private WbConnection dbConn;

  private RowDataProducer source;
  private BatchedStatement insertStatement;
  private BatchedStatement updateStatement;

  private TableIdentifier targetTable;

  private int commitEvery = 0;

  private DeleteType deleteTarget = DeleteType.none;
  private boolean createTarget;
  private String createType;
  private boolean continueOnError;

  private boolean ignoreIdentityColumns;

  private long totalRows = 0;
  private long updatedRows = 0;
  private long insertedRows = 0;
  private long currentImportRow = 0;
  private ImportMode mode = ImportMode.insert;
  private boolean useBatch;
  private int batchSize = -1;
  private boolean commitBatch;

  private boolean hasErrors;
  private boolean hasWarnings;
  private int reportInterval = 10;
  private final MessageBuffer messages;

  private int totalTables = -1;
  private int currentTable = -1;
  private boolean transactionControl = true;
  private boolean useSetNull;

  private List<TableIdentifier> tablesToBeProcessed;
  private ImportTableDeleter tableDeleter;

  // this array will map the columns for updating the target table
  // the index into this array will be the index
  // from the row data array supplied by the producer.
  // (which should be the same order as the columns in targetColumns)
  // the value of that index position is the index
  // for the setXXX() method for the prepared statement
  // to update the table
  private int[] columnMap = null;

  private List<ColumnIdentifier> targetColumns;
  private List<ColumnIdentifier> keyColumns;

  // A map that stores constant values for the import.
  // e.g. for columns not part of the input file.
  private ConstantColumnValues columnConstants;

  private RowActionMonitor progressMonitor;
  private boolean isRunning;
  private ImportFileParser parser;

  // Use for partial imports
  private long startRow = 0;
  private long endRow = Long.MAX_VALUE;
  private boolean partialImportEnded;

  // Additional WHERE clause for UPDATE statements
  private String whereClauseForUpdate;
  private BadfileWriter badWriter;
  private String badfileName;

  private boolean useSavepoint;
  private Savepoint insertSavepoint;
  private Savepoint updateSavepoint;

  private boolean checkRealClobLength;
  private boolean useSetStringForClobs;
  private boolean useSetClob;
  private boolean useSetBlob;
  private boolean useSetBytes;
  private boolean isOracle;
  private SetObjectStrategy useSetObjectWithType;
  private int maxErrorCount = 1000;

  private boolean verifyTargetTable = true;
  private boolean adjustSequences;
  private String insertSqlStart;

  private OverrideIdentityType overrideIdentity;

  private ArrayValueHandler arrayHandler;
  private BlobDecoder blobDecoder;

  private final Map<String, String> columnExpressions = new TreeMap<>(CaseInsensitiveComparator.INSTANCE);

  /**
   * Indicates multiple imports run with this instance oft DataImporter.
   * Set via {@link #beginMultiTable() }
   */
  private boolean multiTable;

  private TableStatements tableStatements;

  private Map<Integer, Integer> typeMapping = new HashMap<>();
  private int errorCount;
  private boolean errorLimitAdded;
  private TimestampTZHandler tzHandler;

  public DataImporter()
  {
    this.messages = new MessageBuffer();
    maxErrorCount = Settings.getInstance().getIntProperty("workbench.import.maxerrors", 1000);
  }

  public void setConnection(WbConnection aConn)
  {
    this.dbConn = aConn;
    if (dbConn == null) return;

    this.checkRealClobLength = this.dbConn.getDbSettings().needsExactClobLength();
    this.isOracle = DBID.Oracle.isDB(dbConn);
    this.useSetNull = this.dbConn.getDbSettings().useSetNull();
    this.useSetStringForClobs = this.dbConn.getDbSettings().sendClobsAsStrings();
    this.useSetClob = this.dbConn.getDbSettings().sendClobAsClob();
    this.useSetBlob = this.dbConn.getDbSettings().sendBlobAsBlob();
    this.useSetBytes = this.dbConn.getDbSettings().sendBlobAsBytes();

    this.useSetObjectWithType = this.dbConn.getDbSettings().getUseTypeWithSetObject();
    this.typeMapping = this.dbConn.getDbSettings().getTypeMappingForPreparedStatement();
    this.arrayHandler = ArrayValueHandler.Factory.getInstance(aConn);
    if (this.isOracle)
    {
      blobDecoder = new BlobDecoder();
    }
    tzHandler = TimestampTZHandler.Factory.getHandler(aConn);
  }

  public void setOverrideIdentity(OverrideIdentityType overrideIdentity)
  {
    this.overrideIdentity = overrideIdentity;
  }

  /**
   * Define SQL expressions to be used in the generated INSERT statement.
   *
   * The expression has to contain a ? at the place where the import value
   * should be used.
   *
   * @param expressions
   */
  public void setColumnExpressions(Map<String,String> expressions)
  {
    this.columnExpressions.clear();
    if (expressions != null)
    {
      this.columnExpressions.putAll(expressions);
    }
  }

  public void setUseSavepoint(boolean flag)
  {

    this.useSavepoint = flag;
    if (dbConn != null)
    {
      // no need to use a savepoint with autocommit
      if (this.dbConn.getAutoCommit())
      {
        useSavepoint = false;
      }
      if (useSavepoint && !this.dbConn.supportsSavepoints())
      {
        LogMgr.logWarning(new CallerInfo(){}, "A savepoint should be used for each statement but the driver does not support savepoints!");
        this.useSavepoint = false;
      }
    }
    LogMgr.logInfo(new CallerInfo(){}, "Using savepoints for DML: " + useSavepoint);
  }

  public void setAdjustSequences(boolean flag)
  {
    this.adjustSequences = flag;
  }

  public void setIgnoreIdentityColumns(boolean flag)
  {
    this.ignoreIdentityColumns = flag;
  }

  public void setInsertStart(String sql)
  {
    if (StringUtil.isBlank(sql))
    {
      insertSqlStart = null;
    }
    else
    {
      insertSqlStart = sql;
    }
  }

  private boolean supportsBatch()
  {
    if (this.dbConn == null) return true;
    return dbConn.getMetadata().supportsBatchUpdates();
  }

  @Override
  public boolean isTransactionControlEnabled()
  {
    return this.transactionControl;
  }

  public void setTransactionControl(boolean flag)
  {
    this.transactionControl = flag;
  }

  public RowActionMonitor getRowActionMonitor()
  {
    return this.progressMonitor;
  }

  public void setRowActionMonitor(RowActionMonitor rowMonitor)
  {
    this.progressMonitor = rowMonitor;
    if (this.progressMonitor != null)
    {
      this.progressMonitor.setMonitorType(RowActionMonitor.MONITOR_INSERT);
    }
  }

  public void setProducer(RowDataProducer producer)
  {
    this.source = producer;
    this.source.setReceiver(this);
    this.source.setAbortOnError(!this.continueOnError);
    if (producer instanceof ImportFileParser)
    {
      this.parser = (ImportFileParser)producer;
    }
  }

  /**
   * Define statements that should be executed before an import
   * for a table starts and after the last record has been inserted.
   *
   * This is mainly intended to run e.g. "set identity_insert on/off" for SQL Server.
   *
   * @param stmt the statement definitions. May be null
   */
  public void setPerTableStatements(TableStatements stmt)
  {
    if (stmt != null && stmt.hasStatements())
    {
      this.tableStatements = stmt;
    }
    else
    {
      this.tableStatements = null;
    }
  }

  /**
   * Indicates that this DataImporter is used for multiple import files into one or more target tables.
   *
   * If setDeleteTarget() has been set to anything other than none, then all tables that have been defined
   * so far are deleted.
   *
   * @throws SQLException
   * @see #deleteTargetTables()
   * @see #setDeleteTarget(workbench.db.importer.DeleteType)
   * @see #setTableList(java.util.List)
   * @see #setTargetTable(workbench.db.TableIdentifier, java.util.List)
   */
  @Override
  public void beginMultiTable()
    throws SQLException
  {
    this.multiTable = true;
    // If more than one table is imported and those tables need to
    // be deleted before the import starts (due to FK constraints) the producer
    // has sent a list of tables that need to be deleted.
    if (this.deleteTarget != DeleteType.none && this.tablesToBeProcessed != null)
    {
      this.deleteTargetTables();
    }
  }

  @Override
  public void endMultiTable()
  {
    this.multiTable = false;
    if (this.progressMonitor != null) this.progressMonitor.jobFinished();
  }

  public void setStartRow(long row)
  {
    if (row >= 0) this.startRow = row;
    else this.startRow = 0;
  }

  public void setEndRow(long row)
  {
    if (row >= 0) this.endRow = row;
    else this.endRow = Long.MAX_VALUE;
  }

  @Override
  public void setCommitBatch(boolean flag)
  {
    this.commitBatch = flag;
    if (flag)
    {
      this.commitEvery = 0;
    }
  }

  /**
   * Do not commit any changes after finishing the import
   */
  @Override
  public void commitNothing()
  {
    this.commitBatch = false;
    this.commitEvery = Committer.NO_COMMIT_FLAG;
  }

  public RowDataProducer getProducer()
  {
    return this.source;
  }

  /**
   * Set the commit interval.
   * When this parameter is set, commitBatch is set to false.
   *
   * @param aCount the interval in which commits should be sent
   */
  @Override
  public void setCommitEvery(int aCount)
  {
    if (aCount > 0 || aCount == Committer.NO_COMMIT_FLAG)
    {
      this.commitBatch = false;
    }
    this.commitEvery = aCount;
  }

  public boolean getContinueOnError()
  {
    return this.continueOnError;
  }

  public void setContinueOnError(boolean flag)
  {
    this.continueOnError = flag;
  }

  private int getRealBatchSize()
  {
    if (!useBatch || batchSize < 1) return 1;

    switch (mode)
    {
      case insert:
      case update:
      case upsert:
        return batchSize;
      default:
        // can't use batching in the other modes
        return 1;
    }
  }
  @Override
  public int getBatchSize()
  {
    return batchSize;
  }

  @Override
  public void setBatchSize(int size)
  {
    this.batchSize = size;
  }

  @Override
  public void setUseBatch(boolean flag)
  {
    this.useBatch = flag;
  }

  public void setBadfileName(String fname)
  {
    this.badfileName = fname;
  }

  public void setWhereClauseForUpdate(String clause)
  {
    if (StringUtil.isEmpty(clause))
    {
      this.whereClauseForUpdate = null;
    }
    else
    {
      this.whereClauseForUpdate = clause;
    }
  }

  @Override
  public void setTableList(List<TableIdentifier> targetTables)
  {
    if (targetTables == null)
    {
      this.tablesToBeProcessed = null;
    }
    else
    {
      this.tablesToBeProcessed = new ArrayList<>(targetTables);
    }
  }

  @Override
  public void deleteTargetTables()
    throws SQLException
  {
    if (!this.isModeInsert())
    {
      LogMgr.logWarning(new CallerInfo(){}, "Target tables will not be deleted because import mode is not set to 'insert'");
      this.messages.appendMessageKey("ErrImpNoDeleteUpd");
      this.messages.appendNewLine();
      return;
    }

    try
    {
      // The instance of the tableDeleter is stored in an instance
      // variable in order to allow for cancel() during the initial
      // delete as well
      tableDeleter = new ImportTableDeleter(this.dbConn, true);
      tableDeleter.setRowMonitor(this.progressMonitor);
      tableDeleter.deleteRows(this.tablesToBeProcessed, true);
      this.messages.append(tableDeleter.getMessages());
    }
    finally
    {
      this.tableDeleter = null;
    }
  }

  /**
   * Controls creation of target table for imports where the producer can retrieve a full table definition.
   *
   * Currently this only works for XML files created with SQL Workbench.
   *
   * @see #createTarget()
   * @see #setTargetTable(workbench.db.TableIdentifier, java.util.List)
   */
  public void setCreateTarget(boolean flag)
  {
    this.createTarget = flag;
  }

  @Override
  public boolean getCreateTarget()
  {
    return createTarget;
  }

  /**
   *  Controls deletion of the target table.
   */
  public void setDeleteTarget(DeleteType deleteTarget)
  {
    this.deleteTarget = deleteTarget;
  }

  public boolean needsKeyColumnInformation()
  {
    return mode != ImportMode.insert;
  }

  public boolean isModeInsert() { return (this.mode == ImportMode.insert); }
  public boolean isModeUpsert() { return (this.mode == ImportMode.upsert); }
  public boolean isModeInsertIgnore() { return (this.mode == ImportMode.insertIgnore); }
  public boolean isModeUpdate() { return (this.mode == ImportMode.update); }
  public boolean isModeInsertUpdate() { return (this.mode == ImportMode.insertUpdate); }
  public boolean isModeUpdateInsert() { return (this.mode == ImportMode.updateInsert); }

  public static int estimateReportIntervalFromFileSize(File file)
  {
    try
    {
      long records = FileUtil.estimateRecords(file, 10);
      if (records < 100)
      {
        return 1;
      }
      else if (records < 10000)
      {
        return 10;
      }
      else if (records < 250000)
      {
        return 100;
      }
      else
      {
        return 1000;
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error when checking input file", e);
      return 10;
    }
  }

  public void setMode(ImportMode importMode)
  {
    this.mode = importMode;
  }

  /**
   *  Return the mode value based on keywords.
   *
   * The mode string is not case sensitive (INSERT is the same as insert)
   *
   * @return null if the value is not valid
   *
   * @see #getModeValue(String)
   * @see ImportMode
   */
  public static ImportMode getModeValue(String mode)
  {
    if (mode == null) return null;

    mode = mode.trim().toLowerCase();
    if (mode.indexOf(',') == -1)
    {
      // only one keyword supplied
      switch (mode)
      {
        case "insert":
          return ImportMode.insert;
        case "insertignore":
          return ImportMode.insertIgnore;
        case "updateinsert":
          return ImportMode.updateInsert;
        case "insertupdate":
          return ImportMode.insertUpdate;
        case "upsert":
          return ImportMode.upsert;
        case "update":
          return ImportMode.update;
        default:
          return null;
      }
    }
    else
    {
      List<String> l = StringUtil.stringToList(mode, ",", true, true);
      String first = l.size() > 0 ? l.get(0) : null;
      String second = l.size() > 1 ? l.get(1) : null;
      if ("insert".equals(first) && "update".equals(second))
      {
        return ImportMode.insertUpdate;
      }
      else if ("update".equals(first) && "insert".equals(second))
      {
        return ImportMode.updateInsert;
      }
      else
      {
        return null;
      }
    }

  }

  /**
   * Define the mode by supplying keywords.
   *
   * A null value or an empty string means "keep the current (default)" and is a valid mode.
   *
   * @return true if the passed string is valid, false otherwise
   *
   * @see #getModeValue(String)
   */
  public boolean setMode(String mode)
  {
    return setMode(mode, dbConn);
  }

  public boolean setMode(String mode, WbConnection conn)
  {
    if (StringUtil.isEmpty(mode)) return true;

    ImportMode modeValue = getModeValue(mode);
    if (modeValue == null) return false;

    if (modeValue == ImportMode.insertIgnore && !ImportDMLStatementBuilder.supportsInsertIgnore(conn))
    {
      return false;
    }

    if (modeValue == ImportMode.upsert && !ImportDMLStatementBuilder.supportsUpsert(conn))
    {
      return false;
    }

    setMode(modeValue);
    return true;
  }

  /**
   * Define column constants for the import.
   * It is expected that the value object is already converted to the correct
   * class. DataImporter will not convert the passed values in any way.
   */
  public void setConstantColumnValues(ConstantColumnValues constantValues)
  {
    this.columnConstants = null;
    if (constantValues != null && constantValues.getColumnCount() > 0)
    {
      this.columnConstants = constantValues;
    }
  }

  /**
   *  Define the key columns for the target table through a comma separated list of column names.
   */
  public void setKeyColumns(String aColumnList)
  {
    List cols = StringUtil.stringToList(aColumnList, ",");
    int count = cols.size();
    this.keyColumns = new ArrayList<>();
    for (int i=0; i < count; i++)
    {
      ColumnIdentifier col = new ColumnIdentifier((String)cols.get(i));
      keyColumns.add(col);
    }
  }

  public List<ColumnIdentifier> getKeyColumns()
  {
    if (this.keyColumns == null) return Collections.emptyList();
    return Collections.unmodifiableList(keyColumns);
  }

  /**
   *  Set the key columns for the target table to be used for update mode.
   *
   *  The list has to contain objects of type {@link workbench.db.ColumnIdentifier}
   */
  public void setKeyColumns(List<ColumnIdentifier> cols)
  {
    if (cols == null)
    {
      this.keyColumns = null;
    }
    else
    {
      this.keyColumns = new ArrayList<>(cols);
    }
  }

  private int getColCount()
  {
    if (targetColumns == null) return 0;
    return targetColumns.size();
  }

  private boolean hasKeyColumns()
  {
    return CollectionUtil.isNonEmpty(this.keyColumns);
  }

  @Override
  public boolean ignoreColumn(ColumnIdentifier col)
  {
    if (col == null) return true;
    if (!ignoreIdentityColumns) return false;
    return col.isAutoGenerated();
  }

  /**
   *  Start the import
   */
  public void startImport()
    throws IOException, SQLException, Exception
  {
    if (this.source == null) return;
    this.isRunning = true;

    this.source.setMessageBuffer(messages);

    try
    {
      this.source.start();
    }
    catch (CycleErrorException e)
    {
      this.hasErrors = true;
      messages.appendMessageKey("ErrImpCycle");
      messages.append(" (" + e.getRootTable() + ")");
      this.messages.append(this.source.getMessages());
      throw e;
    }
    catch (Exception e)
    {
      this.messages.append(this.source.getMessages());
      if (!source.wasCancelled())
      {
        this.hasErrors = true;
        if (parser != null)
        {
          String msg = ResourceMgr.getFormattedString("ErrFileNotImported", parser.getSourceFilename());
          messages.append(msg);
          messages.appendNewLine();
        }
        throw e;
      }
    }
  }

  public static boolean isDeleteTableAllowed(ImportMode importMode)
  {
    return importMode == ImportMode.insert;
  }

  /**
   *  Deletes the target table by issuing a DELETE FROM ...
   */
  private void deleteTarget()
    throws SQLException
  {
    if (this.deleteTarget == DeleteType.none) return;
    if (this.targetTable == null) return;
    String deleteSql = null;

    if (!this.isModeInsert())
    {
      LogMgr.logWarning(new CallerInfo(){}, "Target table will not be deleted because import mode is not set to 'insert'");
      this.messages.append(ResourceMgr.getString("ErrImpNoDeleteUpd"));
      this.messages.appendNewLine();
      return;
    }

    if (this.deleteTarget == DeleteType.truncate)
    {
      deleteSql = "TRUNCATE TABLE " + targetTable.getTableExpression(this.dbConn);
    }
    else
    {
      deleteSql = "DELETE FROM " + targetTable.getTableExpression(this.dbConn);
    }

    Statement stmt = null;
    try
    {
      stmt = this.dbConn.createStatement();
      LogMgr.logInfo(new CallerInfo(){}, "Executing: [" + deleteSql + "] to delete target table...");
      int rows = stmt.executeUpdate(deleteSql);
      if (this.deleteTarget == DeleteType.truncate)
      {
        String msg = ResourceMgr.getString("MsgImportTableTruncated").replace("%table%", targetTable.getTableExpression(this.dbConn));
        this.messages.append(msg);
        this.messages.appendNewLine();
      }
      else
      {
        this.messages.append(rows + " " + ResourceMgr.getString("MsgImporterRowsDeleted") + " " + targetTable.getTableExpression(this.dbConn) + "\n");
      }
    }
    finally
    {
      JdbcUtils.closeStatement(stmt);
    }
  }

  private void createTarget()
    throws SQLException
  {
    TableCreator creator = new TableCreator(this.dbConn, createType, this.targetTable, this.targetColumns);
    creator.useDbmsDataType(true);
    creator.createTable();
    String table = creator.getTable().getTableName();
    String msg = StringUtil.replace(ResourceMgr.getString("MsgImporterTableCreated"), "%table%", table);
    DbObjectFinder finder = new DbObjectFinder(dbConn);
    targetTable = finder.findTable(targetTable); // make sure we use the correct table name
    this.messages.append(msg);
    this.messages.appendNewLine();
  }

  public boolean isRunning()
  {
    return this.isRunning;
  }

  public boolean isSuccess()
  {
    return !hasErrors;
  }

  public boolean hasWarnings()
  {
    return this.hasWarnings;
  }

  public long getAffectedRows()
  {
    return this.totalRows;
  }

  public long getInsertedRows()
  {
    return this.insertedRows;
  }

  public long getUpdatedRows()
  {
    return this.updatedRows;
  }

  /**
   *  This method is called if cancelExecution() is called
   *  to check if the user should confirm the cancelling of the import
   */
  @Override
  public boolean confirmCancel()
  {
    return true;
  }

  @Override
  public void cancelExecution()
  {
    if (this.tableDeleter != null)
    {
      this.tableDeleter.cancel();
    }

    if (this.source != null) this.source.cancel();
    this.messages.append(ResourceMgr.getString("MsgImportCancelled") + "\n");
  }

  private void cancelStatement(BatchedStatement stmt)
  {
    if (stmt == null) return;
    try
    {
      stmt.cancel();
    }
    catch (Exception e)
    {
      LogMgr.logDebug(new CallerInfo(){}, "Error when cancelling statement", e);
    }
  }

  @Override
  public void setTableCount(int total)
  {
    this.totalTables = total;
  }

  @Override
  public void setCurrentTable(int current)
  {
    this.currentTable = current;
  }

  private void addError(String msg)
  {
    errorCount ++;
    if (errorCount < maxErrorCount)
    {
      this.messages.append(msg);
    }
    else if (!errorLimitAdded)
    {
      messages.appendNewLine();
      messages.append(ResourceMgr.getString("MsgImpTooManyError"));
      messages.appendNewLine();
      errorLimitAdded = true;
    }
  }

  @Override
  public void recordRejected(String record, long importRow, Throwable error)
  {
    if (record == null) return;

    if (badWriter != null)
    {
      badWriter.recordRejected(record);
    }
    else
    {
      this.addError(ResourceMgr.getString("ErrImportingRow") + " " + importRow + "\n");
      this.addError(ResourceMgr.getString("ErrImportErrorMsg") + " " + ExceptionUtil.getAllExceptions(error) + "\n");
      this.addError(ResourceMgr.getString("ErrImportValues") + " " + record + "\n");
      if (errorLimitAdded)
      {
        LogMgr.logError(new CallerInfo(){}, "Values: " + record, error);
      }
    }
  }

  @Override
  public boolean shouldProcessNextRow()
  {
    return currentImportRow + 1 >= startRow && currentImportRow + 1 <= endRow;
  }

  @Override
  public void nextRowSkipped()
  {
    this.currentImportRow ++;
    if (this.currentImportRow >= endRow)
    {
      endRowReached();
    }
  }

  private void endRowReached()
  {
    LogMgr.logInfo(new CallerInfo(){}, "Import limit (" + this.endRow + ") for table " + this.targetTable + " reached. Stopping import");
    String msg = ResourceMgr.getFormattedString("MsgPartialImportEnded", Long.toString(endRow));
    this.messages.append(msg);
    this.messages.appendNewLine();
    this.source.stop();
  }

  @Override
  public void processFile(StreamImporter stream)
    throws SQLException, IOException
  {
    if (stream != null)
    {
      try
      {
        insertedRows += stream.processStreamData();
        CharSequence warnings = SqlUtil.getWarnings(dbConn, null);
        if (StringUtil.isNotBlank(warnings))
        {
          if (this.messages.getLength() > 0)
          {
            this.messages.appendNewLine();
          }
          this.messages.append(warnings);
          this.messages.appendNewLine();
        }
        tableImportFinished();
      }
      catch (SQLException sql)
      {
        this.hasErrors = true;
        tableImportError();
        LogMgr.logError(new CallerInfo(){}, "Error during import:\n" + ExceptionUtil.getDisplay(sql), null);
        this.addError(sql.getLocalizedMessage()+ "\n");
      }
    }
  }

  private boolean shouldCommitRow(long rowNum)
  {
    if (!transactionControl) return false;
    if (commitEvery <= 0) return false;
    if (commitBatch) return false;
    if (dbConn.getAutoCommit()) return false;

    return rowNum % commitEvery == 0;
  }

  @Override
  public boolean isColumnExpression(String colName)
  {
    if (StringUtil.isBlank(colName)) return false;
    return columnExpressions.containsKey(colName);
  }

  /**
   *  Callback function for RowDataProducer. The order in the data array
   *  has to be the same as initially passed in the setTargetTable() method.
   */
  @Override
  public void processRow(Object[] row)
    throws SQLException
  {
    if (row == null) return;
    if (row.length != this.getColCount())
    {
      throw new SQLException("Invalid row data received. Size of row array does not match column count");
    }

    final CallerInfo ci = new CallerInfo(){};

    currentImportRow++;
    if (currentImportRow < startRow) return;
    if (this.currentImportRow >= endRow)
    {
      endRowReached();
    }

    if (this.progressMonitor != null && this.reportInterval > 0 && (currentImportRow == 1 || currentImportRow % reportInterval == 0))
    {
      if (this.totalTables > 0)
      {
        StringBuilder msg = new StringBuilder(targetTable.getTableName().length() + 20);
        msg.append(targetTable.getTableName());
        msg.append(" [");
        msg.append(this.currentTable);
        msg.append('/');
        msg.append(this.totalTables);
        msg.append(']');
        progressMonitor.setCurrentObject(msg.toString(), currentImportRow, -1);
      }
      else
      {
        progressMonitor.setCurrentObject(targetTable.getTableName(), currentImportRow, -1);
      }
    }

    long rows = 0;
    try
    {
      switch (this.mode)
      {
        case insert:
        case insertIgnore:
        case upsert:
          rows = this.insertRow(row, useSavepoint && continueOnError);
          break;

        case insertUpdate:
          boolean inserted = false;
          // in case of an Exception we are retrying the row
          // with an update. Theoretically the only expected
          // exception should indicate a primary key violation,
          // but as we don't analyze the exception, we will
          // try the update, for any exception. If the exception
          // was not a key violation, the update will most probably
          // fail as well.
          try
          {
            rows = this.insertRow(row, useSavepoint);
            // by checking the number of rows returned, Oracle IGNORE_ROW_ON_DUPKEY_INDEX could be used
            // with the insert statement which is a bit faster than catching the exception
            // this would also work with SQL Server's indexes that are defined as with IGNORE_DUP_KEY = ON
            inserted = rows > 0;
          }
          catch (SQLException sql)
          {
            if (shouldIgnoreInsertError(sql))
            {
              inserted = false;
            }
            else
            {
              throw sql;
            }
          }
          catch (Exception e)
          {
            LogMgr.logDebug(ci, "Unexpected error when inserting row in insert/update mode", e);
            throw new SQLException("Error during insert", e);
          }

          // The update statement might have been set to null
          // because an update is not possible (when only key columns
          // are present in the table). In this case we silently skip
          // the failed insert
          if (!inserted && this.updateStatement != null)
          {
            rows = this.updateRow(row, useSavepoint && continueOnError);
          }
          break;

        case updateInsert:
          // an exception is not expected when updating the row
          // if the row does not exist, the update counter should be
          // zero. If the update violates any constraints, then the
          // INSERT will fail as well, so any exception thrown, indicates
          // an error with this row, so we will not proceed with the insert

          if (this.updateStatement == null)
          {
            rows = this.insertRow(row, useSavepoint && continueOnError);
          }
          else
          {
            rows = this.updateRow(row, useSavepoint && continueOnError);
            if (rows <= 0)
            {
              rows = this.insertRow(row, useSavepoint && continueOnError);
            }
          }
          break;

        case update:
          rows = this.updateRow(row, useSavepoint && continueOnError);
          break;
      }

      this.totalRows += rows;

      if (shouldCommitRow(totalRows))
      {
        LogMgr.logInfo(ci, "Commit threshold (" + commitEvery + ") reached at " + totalRows + " rows. Committing changes.");
        this.dbConn.commit();
      }
    }
    catch (OutOfMemoryError oome)
    {
      this.hasErrors = true;
      closeStatements();
      this.messages.clear();
      System.gc();
      this.messages.append(ResourceMgr.getString("MsgOutOfMemoryGeneric"));
      this.messages.appendNewLine();
      if (this.batchSize > 0)
      {
        LogMgr.logError(ci, "Not enough memory to hold statement batch! Use the -batchSize parameter to reduce the batch size!", null);
        this.messages.append(ResourceMgr.getString("MsgOutOfMemoryJdbcBatch"));
        this.messages.appendNewLine();
        this.messages.appendNewLine();
      }
      else
      {
        LogMgr.logError(ci, "Not enough memory to run this import!", null);
      }
      throw new SQLException("Not enough memory!");
    }
    catch (SQLException e)
    {
      boolean debug = LogMgr.isDebugEnabled();
      LogMgr.logError(ci, "Error importing row " + currentImportRow + ": " + ExceptionUtil.getDisplay(e), debug ? e : null);
      String rec = this.source.getLastRecord();
      if (rec == null)
      {
        ValueDisplay display = new ValueDisplay(row);
        rec = display.toString();
      }
      recordRejected(rec, currentImportRow, e);
      if (this.continueOnError)
      {
        this.hasWarnings = true;
      }
      else
      {
        this.hasErrors = true;
        throw e;
      }
    }

    if (currentImportRow % 100 == 0 && MemoryWatcher.isMemoryLow(false))
    {
      this.hasErrors = true;
      closeStatements();
      this.messages.clear();
      this.messages.append(ResourceMgr.getString("MsgLowMemoryError"));
      this.messages.appendNewLine();
      throw new SQLException("Not enough memory!");
    }

  }

  /**
   * Check if the given SQL Exception identifies a primary (unique) key violation.
   *
   * If an error code or SQLState is configured for the current DBMS this is checked.
   * Otherwise this returns true.
   *
   * @param sql the exception
   * @return true if the error should be ignored in insert/update mode
   *
   * @see DbSettings#getUniqueKeyViolationErrorCode()
   * @see DbSettings#getUniqueKeyViolationErrorState()
   */
  private boolean shouldIgnoreInsertError(SQLException sql)
  {
    String state = dbConn.getDbSettings().getUniqueKeyViolationErrorState();
    int error = dbConn.getDbSettings().getUniqueKeyViolationErrorCode();
    if (state != null)
    {
      return state.equals(sql.getSQLState());
    }

    if (error > 0)
    {
      return sql.getErrorCode() == error;
    }

    // Nothing configured to detect a primary key violation --> ignore it
    return true;
  }

  private void setUpdateSavepoint()
  {
    try
    {
      this.updateSavepoint = this.dbConn.getSqlConnection().setSavepoint();
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not create pre-update Savepoint", e);
    }
  }

  private void setInsertSavepoint()
  {
    try
    {
      this.insertSavepoint = this.dbConn.getSqlConnection().setSavepoint();
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not set pre-insert Savepoint", e);
    }
  }

  private void rollbackUpdate()
  {
    rollbackToSavepoint(updateSavepoint);
    updateSavepoint = null;
  }

  private void rollbackInsert()
  {
    rollbackToSavepoint(insertSavepoint);
    insertSavepoint = null;
  }

  private void rollbackToSavepoint(Savepoint savepoint)
  {
    if (savepoint == null) return;
    try
    {
      this.dbConn.getSqlConnection().rollback(savepoint);
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error when performing rollback to savepoint", e);
    }
  }

  private void releaseInsertSavepoint()
  {
    releaseSavepoint(insertSavepoint);
    insertSavepoint = null;
  }

  private void releaseUpdateSavepoint()
  {
    releaseSavepoint(updateSavepoint);
    updateSavepoint = null;
  }

  private void releaseSavepoint(Savepoint savepoint)
  {
    if (savepoint == null) return;
    try
    {
      this.dbConn.getSqlConnection().releaseSavepoint(savepoint);
    }
    catch (Throwable th)
    {
      LogMgr.logError(new CallerInfo(){}, "Error when releasing savepoint", th);
    }
  }

  /**
   *  Insert a row of data into the target table.
   *  This method relies on insertStatement correctly initialized with
   *  all parameters at the correct location.
   */
  private long insertRow(Object[] row, boolean useSP)
    throws SQLException
  {
    try
    {
      if (useSP) setInsertSavepoint();
      long rows = processRowData(this.insertStatement, row, false);
      this.insertedRows += rows;
      releaseInsertSavepoint();
      return rows;
    }
    catch (SQLException e)
    {
      if (useSP)
      {
        rollbackInsert();
      }
      throw e;
    }
  }

  /**
   *  Update the data in the target table using the PreparedStatement
   *  available in updateStatement
   */
  private long updateRow(Object[] row, boolean useSP)
    throws SQLException
  {
    try
    {
      if (useSP) setUpdateSavepoint();
      long rows = processRowData(this.updateStatement, row, true);
      this.updatedRows += rows;
      releaseUpdateSavepoint();
      return rows;
    }
    catch (SQLException e)
    {
      if (useSP)
      {
        rollbackUpdate();
      }
      throw e;
    }
  }

  private long processRowData(BatchedStatement pstmt, Object[] row, boolean useColMap)
    throws SQLException
  {
    int colIndex = 0;

    for (int i=0; i < row.length; i++)
    {
      ColumnIdentifier column = targetColumns.get(i);
      if (ignoreColumn(column)) continue;
      colIndex++;

      if (useColMap)
      {
        // The colIndex points to the correct location in the PreparedStatement
        // when using UPDATE with different column names
        colIndex = this.columnMap[i] + 1;
      }

      boolean hasExpression = columnExpressions.containsKey(column.getColumnName());

      int jdbcType = column.getDataType();
      String dbmsType = column.getDbmsType();
      Object value = row[i];

      try
      {
        if (value == null)
        {
          if (useSetNull)
          {
            pstmt.setNull(colIndex, mapJdbcType(jdbcType));
          }
          else
          {
            pstmt.setObject(colIndex, null);
          }
        }
        else if (!hasExpression && SqlUtil.isClobType(jdbcType, dbmsType, dbConn.getDbSettings()) || SqlUtil.isXMLType(jdbcType, dbmsType))
        {
          handleClobValue(pstmt, colIndex, value);
        }
        else if (!hasExpression && SqlUtil.isBlobType(jdbcType) || "BLOB".equals(dbmsType))
        {
          handleBlobValue(pstmt, colIndex, value, dbmsType);
        }
        else if (!hasExpression && arrayHandler != null && jdbcType == java.sql.Types.ARRAY)
        {
          arrayHandler.setValue(pstmt, colIndex, value, column);
        }
        else if (isOracle && "DATE".equalsIgnoreCase(dbmsType) && value instanceof LocalDate)
        {
          java.sql.Timestamp ts = java.sql.Timestamp.valueOf(((LocalDate)value).atStartOfDay());
          pstmt.setTimestamp(colIndex, ts);
        }
        else if (isOracle && "DATE".equalsIgnoreCase(dbmsType) && value instanceof java.sql.Date)
        {
          java.sql.Timestamp ts = new java.sql.Timestamp(((java.sql.Date)value).getTime());
          pstmt.setTimestamp(colIndex, ts);
        }
        else
        {
          if (jdbcType == Types.TIMESTAMP_WITH_TIMEZONE && tzHandler != null)
          {
            value = tzHandler.convertTimestampTZ(value);
          }
          if (!hasExpression && useJdbcType(jdbcType))
          {
            pstmt.setObject(colIndex, value, mapJdbcType(jdbcType));
          }
          else
          {
            pstmt.setObject(colIndex, value);
          }
        }
      }
      catch (SQLException sql)
      {
        String msg = String.format("Could not set %s value for column=%s, type=%s, index=%d. Error: %s",
                                    value == null ? "null" : "\"" + value.getClass().getSimpleName() + "\"",
                                    column.getColumnName(),
                                    dbmsType,
                                    colIndex,
                                    sql.getMessage());
        LogMgr.logError(new CallerInfo(){}, msg, null);
        throw sql;
      }
    }

    if (columnConstants != null && pstmt == this.insertStatement)
    {
      int count = this.columnConstants.getColumnCount();
      int constIndex = row.length + 1;
      for (int i=0; i < count; i++)
      {
        if (columnConstants.isSelectStatement(i))
        {
          ValueStatement stmt = columnConstants.getStatement(i);

          Map<Integer, Object> values = source.getInputColumnValues(stmt.getInputColumnIndexes());
          Object data = stmt.getDatabaseValue(dbConn, values);
          pstmt.getStatement().setObject(constIndex, data);
          constIndex ++;
        }
        else if (columnConstants.isLineNumber(i))
        {
          pstmt.getStatement().setLong(constIndex, currentImportRow);
        }
        else if (!columnConstants.isFunctionCall(i))
        {
          columnConstants.setParameter(pstmt.getStatement(), constIndex, i);
          constIndex ++;
        }
      }
    }

    long rows = pstmt.executeUpdate();

    return rows;
  }

  private void handleBlobValue(BatchedStatement pstmt, int colIndex, Object value, String dbmsType)
    throws SQLException
  {
    InputStream in = null;
    int len = -1;

    if (value instanceof File)
    {
      // When importing files created by SQL Workbench/J
      // blobs will be "passed" as File objects pointing to the external file
      ImportFileHandler handler = (this.parser != null ? parser.getFileHandler() : null);
      File f = (File)value;
      try
      {
        if (handler != null)
        {
          in = new BufferedInputStream(handler.getAttachedFileStream(f));
          len = (int)handler.getLength(f);
        }
        else
        {
          if (!f.isAbsolute())
          {
            File sourcefile = new File(this.parser.getSourceFilename());
            f = new File(sourcefile.getParentFile(), f.getName());
          }
          in = new BufferedInputStream(new FileInputStream(f), 64 * 1024);
          len = (int)f.length();
        }
      }
      catch (IOException ex)
      {
        hasErrors = true;
        String msg = ResourceMgr.getFormattedString("ErrFileNotAccessible", f.getAbsolutePath(), ex.getMessage());
        messages.append(msg);
        throw new SQLException(ex.getMessage());
      }
    }
    else if (value instanceof UUID && blobDecoder != null && dbmsType.startsWith("RAW"))
    {
      // source DBMS uses real UUIDs, target DBMS is Oracle that does not have a proper UUID type
      UUID uuid = (UUID)value;
      String uuidString = uuid.toString();
      try
      {
        byte[] uuidBytes = blobDecoder.decodeString(uuidString, BlobLiteralType.uuid);
        pstmt.setBytes(colIndex, uuidBytes);
        return;
      }
      catch (IOException io)
      {
        // can not happen
      }
    }
    else if (value instanceof Blob)
    {
      Blob b = (Blob)value;
      if (useSetBlob)
      {
        pstmt.setBlob(colIndex, b);
        return;
      }

      if (useSetBytes)
      {
        byte[] data = b.getBytes(1, (int)b.length());
        pstmt.setBytes(colIndex, data);
        return;
      }

      in = b.getBinaryStream();
      len = (int)b.length();
    }
    else if (value instanceof byte[])
    {
      byte[] buffer = (byte[])value;
      if (useSetBlob)
      {
        Blob blob = dbConn.getSqlConnection().createBlob();
        blob.setBytes(1, buffer);
        pstmt.setBlob(colIndex, blob);
        return;
      }

      if (useSetBytes)
      {
        pstmt.setBytes(colIndex, buffer);
        return;
      }

      in = new ByteArrayInputStream(buffer);
      len = buffer.length;
    }

    if (in != null && len > -1)
    {
      pstmt.setBinaryStream(colIndex, in, len);
    }
    else
    {
      pstmt.setNull(colIndex, Types.BLOB);
      this.messages.append(ResourceMgr.getFormattedString("MsgBlobNotRead", Integer.valueOf(colIndex)));
      this.messages.appendNewLine();
    }
  }

  private void handleClobValue(BatchedStatement pstmt, int colIndex, Object value)
    throws SQLException
  {

    if (value instanceof Clob)
    {
      Clob clob = (Clob)value;
      if (useSetClob)
      {
        pstmt.setClob(colIndex, clob);
      }
      else
      {
        Reader in = clob.getCharacterStream();
        pstmt.setCharacterStream(colIndex, in, (int)clob.length());
      }
    }
    else if (value instanceof File)
    {
      int size = -1;
      ImportFileHandler handler = (this.parser != null ? parser.getFileHandler() : null);
      String encoding = (handler != null ? handler.getEncoding() : null);
      if (encoding == null)
      {
        encoding = (this.parser != null ? parser.getEncoding() : Settings.getInstance().getDefaultDataEncoding());
      }

      Reader in = null;
      File f = (File)value;
      try
      {
        if (handler != null)
        {
          in = EncodingUtil.createReader(handler.getAttachedFileStream(f), encoding);

          // Apache Derby needs the exact length in characters
          // which might not be the file size if a multi-byte encoding is used
          if (checkRealClobLength)
          {
            size = (int)handler.getCharacterLength(f);
          }
          else
          {
            size = (int)handler.getLength(f);
          }
        }
        else
        {
          if (!f.isAbsolute())
          {
            File sourcefile = new File(this.parser.getSourceFilename());
            f = new File(sourcefile.getParentFile(), f.getName());
          }
          in = EncodingUtil.createBufferedReader(f, encoding);

          // Apache Derby needs the exact length in characters
          // which might not be the file size if a multi-byte encoding is used
          if (checkRealClobLength)
          {
            size = (int)FileUtil.getCharacterLength(f, encoding);
          }
          else
          {
            size = (int)f.length();
          }
        }
        pstmt.setCharacterStream(colIndex, in, size);
      }
      catch (IOException ex)
      {
        hasErrors = true;
        String msg = ResourceMgr.getFormattedString("ErrFileNotAccessible", f.getAbsolutePath(), ex.getMessage());
        messages.append(msg);
        throw new SQLException(ex.getMessage());
      }
    }
    else if (useSetClob)
    {
      Clob clob = dbConn.getSqlConnection().createClob();
      clob.setString(1, value.toString());
      pstmt.setClob(colIndex, clob);
    }
    else if (useSetStringForClobs)
    {
      pstmt.setString(colIndex, value.toString());
    }
    else
    {
      // this assumes that the JDBC driver will actually
      // implement the toString() for whatever object
      // it created when reading that column!
      pstmt.setObject(colIndex, value);
    }

  }

  private int mapJdbcType(int jdbcType)
  {
    return typeMapping.getOrDefault(jdbcType, jdbcType);
  }

  private boolean useJdbcType(int jdbcType)
  {
    switch (useSetObjectWithType)
    {
      case Never:
        return false;
      case Always:
        return true;
      case KnownTypes:
        return jdbcType != Types.OTHER;
    }
    // can't happen
    return false;
  }

  private void checkConstantValues(File currentFile)
    throws SQLException
  {
    if (this.columnConstants == null) return;

    try
    {
      columnConstants.initFileVariables(targetTable, dbConn, currentFile);
    }
    catch (ConverterException ex)
    {
      throw new SQLException("Could not convert constant values", ex);
    }

    for (ColumnIdentifier col : this.targetColumns)
    {
      if (this.columnConstants.removeColumn(col))
      {
        String msg = ResourceMgr.getFormattedString("MsgImporterConstIgnored", col.getColumnName());
        this.messages.append(msg);
        this.messages.appendNewLine();
        if (this.continueOnError)
        {
          LogMgr.logWarning(new CallerInfo(){}, msg);
        }
        else
        {
          throw new SQLException(msg);
        }
      }
    }
  }

  @Override
  public void tableImportFinished()
    throws SQLException
  {
    // be prepared to import more then one table...
    if (this.targetTable != null)
    {
      try
      {
        finishTable();
        targetTable = null;
      }
      catch (SQLException e)
      {
        if (!this.continueOnError)
        {
          targetTable = null;
          hasErrors = true;
          throw e;
        }
      }
    }
  }

  public void skipTargetCheck(boolean flag)
  {
    verifyTargetTable = !flag;
  }

  /**
   *  Callback function from the RowDataProducer
   */
  @Override
  public void setTargetTable(TableIdentifier table, List<ColumnIdentifier> columnsToImport, File currentFile)
    throws SQLException
  {
    // be prepared to import more then one table...
    if (this.isRunning && this.targetTable != null)
    {
      try
      {
        this.finishTable();
      }
      catch (SQLException e)
      {
        if (!this.continueOnError)
        {
          this.hasErrors = true;
          throw e;
        }
      }
    }

    this.currentImportRow = 0;
    this.updatedRows = 0;
    this.insertedRows = 0;

    this.errorCount = 0;
    this.errorLimitAdded = false;

    final CallerInfo ci = new CallerInfo(){};

    this.targetTable = table.createCopy();
    String tname = this.targetTable.getTableExpression(dbConn);
    try
    {
      this.targetColumns = new ArrayList<>(columnsToImport);

      // Key columns might have been externally defined if
      // a single table import is run which is not possible
      // when using a multi-table import. So the keyColumns
      // should only be reset if a multi-table import is running!
      if (this.multiTable)
      {
        this.keyColumns = null;
      }

      String tableMsg = null;
      if (this.parser != null)
      {
        tableMsg = ResourceMgr.getFormattedString("MsgImportingFile", this.parser.getSourceFilename(), tname);
      }
      else
      {
        tableMsg = ResourceMgr.getFormattedString("MsgImportingTableData", tname);
      }
      this.messages.append(tableMsg);
      this.messages.appendNewLine();

      if (this.createTarget)
      {
        try
        {
          this.createTarget();
        }
        catch (SQLException e)
        {
          String msg = ResourceMgr.getFormattedString("ErrImportTableNotCreated", tname, ExceptionUtil.getDisplay(e));
          this.messages.append(msg);
          this.messages.appendNewLine();
          LogMgr.logError(ci, "Could not create target: " + tname, e);
          this.hasErrors = true;
          throw e;
        }
      }

      if (verifyTargetTable)
      {
        try
        {
          this.checkTable();
        }
        catch (SQLException e)
        {
          this.messages.append(ResourceMgr.getFormattedString("ErrTargetTableNotFound", tname));
          if (parser != null)
          {
            this.messages.appendNewLine();
            this.messages.append(ResourceMgr.getFormattedString("ErrImportFileNotProcessed", this.parser.getSourceFilename()));
          }
          this.hasErrors = true;
          this.messages.appendNewLine();
          this.targetTable = null;
          throw e;
        }
      }

      checkConstantValues(currentFile);

      this.currentImportRow = 0;
      this.totalRows = 0;

      if (this.mode != ImportMode.update)
      {
        this.prepareInsertStatement();
      }

      if (this.mode == ImportMode.update || mode == ImportMode.insertUpdate || mode == ImportMode.updateInsert)
      {
        this.prepareUpdateStatement();
      }

      if (this.deleteTarget != DeleteType.none && this.tablesToBeProcessed == null)
      {
        if (this.progressMonitor != null)
        {
          this.progressMonitor.saveCurrentType("importDelete");
          this.progressMonitor.setMonitorType(RowActionMonitor.MONITOR_PLAIN);
          String msg = ResourceMgr.getFormattedString("TxtDeletingTable", tname);
          this.progressMonitor.setCurrentObject(msg,-1,-1);
        }

        try
        {
          this.deleteTarget();
        }
        catch (SQLException e)
        {
          this.hasErrors = true;
          String msg = ResourceMgr.getString("ErrDeleteTableData");
          msg = msg.replace("%table%", tname);
          msg = msg.replace("%error%", ExceptionUtil.getDisplay(e));
          this.messages.append(msg);
          this.messages.appendNewLine();

          LogMgr.logError(ci, "Could not delete contents of table " + tname, e);
          if (!this.continueOnError)
          {
            throw e;
          }
        }
      }

      if (progressMonitor != null) this.progressMonitor.restoreType("importDelete");

      if (this.reportInterval == 0 && this.progressMonitor != null)
      {
        this.progressMonitor.setMonitorType(RowActionMonitor.MONITOR_PLAIN);
        this.progressMonitor.setCurrentObject(ResourceMgr.getFormattedString("MsgImportingTableData", tname + " (" + this.getModeString() + ")"),-1,-1);
      }

      if (LogMgr.isInfoEnabled())
      {
        LogMgr.logInfo(ci, "Starting import for table " + tname);
      }

      if (this.badfileName != null)
      {
        this.badWriter = new BadfileWriter(this.badfileName, targetTable, "UTF8");
      }
      else
      {
        this.badWriter = null;
      }
      runPreTableStatement();
    }
    catch (Exception th)
    {
      String msg = "Error initializing import for table " + tname;
      if (this.continueOnError)
      {
        this.hasWarnings = true;
      }
      else
      {
        this.hasErrors = true;
      }
      LogMgr.logError(ci, msg, th);
      throw th;
    }
  }

  private void runPreTableStatement()
    throws SQLException
  {
    try
    {
      if (this.tableStatements != null)
      {
        this.tableStatements.runPreTableStatement(dbConn, targetTable);
      }
    }
    catch (TableStatementError tse)
    {
      String err = ResourceMgr.getFormattedString("ErrTableStmt", tse.getTable(), tse.getMessage());
      messages.append(err);
      messages.appendNewLine();
      throw tse;
    }
  }

  private void runPostTableStatement()
    throws SQLException
  {
    try
    {
      if (this.tableStatements != null && this.tableStatements.wasSuccess())
      {
        this.tableStatements.runPostTableStatement(dbConn, targetTable);
      }
    }
    catch (TableStatementError tse)
    {
      String err = ResourceMgr.getFormattedString("ErrTableStmt", tse.getTable(), tse.getMessage());
      messages.append(err);
      messages.appendNewLine();
      throw tse;
    }
  }


  private String getModeString()
  {
    if (this.mode == null) return "";

    switch (mode)
    {
      case insert:
        return "insert";
      case insertIgnore:
        return "insertIgnore";
      case insertUpdate:
        return "insert/update";
      case updateInsert:
        return "update/insert";
      case upsert:
        return "upsert";
      case update:
        return "update";
    }
    return "";
  }

  private void checkTable()
    throws SQLException
  {
    if (this.dbConn == null) return;
    if (this.targetTable == null) return;

    DbMetadata meta = this.dbConn.getMetadata();
    DbObjectFinder finder = new DbObjectFinder(dbConn);
    boolean exists = finder.objectExists(targetTable, meta.getTablesAndViewTypes());
    if (!exists)
    {
      throw new SQLException("Table " +targetTable.getTableExpression(this.dbConn) + " not found!");
    }
  }

  private void checkBatchMode()
  {
    if (this.useBatch)
    {
      if (!supportsBatch())
      {
        LogMgr.logWarning(new CallerInfo(){}, "JDBC driver does not support batch updates. Ignoring request to use batch updates");
        messages.append(ResourceMgr.getString("MsgJDBCDriverNoBatch") + "\n");
        useBatch = false;
      }
      else if (this.isModeInsertUpdate() || this.isModeUpdateInsert())
      {
        // When using UPDATE/INSERT or INSERT/UPDATE
        // we cannot use batch mode as we immediately need
        // the result of the first statement to decide
        // whether we have to send another one
        useBatch = false;
        messages.appendMessageKey("ErrImportNoBatchMode");
        messages.appendNewLine();
      }
    }
  }

  /**
   *  Prepare the statement to be used for inserts.
   *  targetTable and targetColumns have to be initialized before calling this!
   */
  private void prepareInsertStatement()
    throws SQLException
  {
    // if the target table was not verified, we need to make
    // sure the default case for column names is used
    boolean adjustColumnNames = !verifyTargetTable;

    ImportDMLStatementBuilder builder = new ImportDMLStatementBuilder(dbConn, targetTable, targetColumns, this, adjustColumnNames);
    builder.setMessageBuffer(messages);
    builder.setColumnExpressions(columnExpressions);
    builder.setOverrideStrategy(overrideIdentity);

    // for anything other than a plain insert we need key columns
    if (mode != ImportMode.insert)
    {
      verifyKeyColumns();
      builder.setKeyColumns(keyColumns);
    }

    String insertSql = null;

    if (mode == ImportMode.upsert && builder.isModeSupported(mode))
    {
      insertSql = builder.createUpsertStatement(columnConstants, insertSqlStart);
    }
    else if (mode == ImportMode.insertIgnore && builder.isModeSupported(mode))
    {
      insertSql = builder.createInsertIgnore(columnConstants, insertSqlStart);
    }
    else if (mode == ImportMode.insertUpdate && builder.hasNativeInsertIgnore())
    {
      // using an "insert ignore" statement instead of catching an exception should be faster
      insertSql = builder.createInsertIgnore(columnConstants, insertSqlStart);
    }

    final CallerInfo ci = new CallerInfo(){};
    if (insertSql == null)
    {
      if (mode == ImportMode.upsert)
      {
        mode = ImportMode.insertUpdate;
        LogMgr.logInfo(ci, "Database does not support native UPSERT. Reverting to insert/update.");
      }
      insertSql = builder.createInsertStatement(columnConstants, insertSqlStart);
    }

    checkBatchMode();

    try
    {
      PreparedStatement stmt = this.dbConn.getSqlConnection().prepareStatement(insertSql);
      this.insertStatement = new BatchedStatement(stmt, dbConn, getRealBatchSize());
      this.insertStatement.setCommitBatch(this.commitBatch);
      LogMgr.logInfo(ci, "Statement for insert: " + insertSql);
    }
    catch (Exception e)
    {
      LogMgr.logError(ci, "Error when preparing INSERT statement: " + insertSql, e);
      this.messages.append(ResourceMgr.getString("ErrImportInitTargetFailed"));
      this.messages.append(ExceptionUtil.getDisplay(e));
      this.insertStatement = null;
      this.hasErrors = true;
      if (e instanceof SQLException)
      {
        throw (SQLException)e;
      }
      throw new SQLException("Could not prepare instert statement", e);
    }
  }

  private void verifyKeyColumns()
    throws SQLException
  {
    if (this.hasKeyColumns()) return;

    this.retrieveKeyColumns();

    if (!this.hasKeyColumns())
    {
      if (messages.getLength() > 0) this.messages.appendNewLine();
      this.messages.append(ResourceMgr.getFormattedString("ErrImportNoKeyForUpdate", targetTable.getTableExpression()));
      throw new SQLException("No key columns defined for update mode");
    }
  }
  /**
   *  Prepare the statement to be used for updates
   *  targetTable and targetColumns have to be initialized before calling this!
   */
  private void prepareUpdateStatement()
    throws SQLException, ModeNotPossibleException
  {
    verifyKeyColumns();

    DbMetadata meta = dbConn.getMetadata();
    DmlExpressionBuilder builder = DmlExpressionBuilder.Factory.getBuilder(dbConn);

    this.columnMap = new int[getColCount()];
    int pkIndex = getColCount() - this.keyColumns.size();
    int pkCount = 0;
    int colIndex = 0;
    StringBuilder sql = new StringBuilder(getColCount() * 20 + 80);
    StringBuilder where = new StringBuilder(this.keyColumns.size() * 10);
    sql.append("UPDATE ");
    sql.append(targetTable.getFullyQualifiedName(this.dbConn));
    sql.append(" SET ");
    where.append(" WHERE ");
    boolean pkAdded = false;
    boolean needComma = false;

    if (columnConstants != null && columnConstants.getColumnCount() > 0)
    {
      SqlLiteralFormatter formatter = new SqlLiteralFormatter(dbConn);
      for (ColumnIdentifier col : keyColumns)
      {
        ColumnData data = columnConstants.getColumnData(col.getColumnName());
        if (data != null)
        {
          String colname = meta.quoteObjectname(data.getIdentifier().getColumnName());
          if (pkAdded) where.append(" AND ");
          else pkAdded = true;
          where.append(colname);
          where.append(" = ");
          where.append(formatter.getDefaultLiteral(data));
          data.getIdentifier().setIsPkColumn(true); // just to be sure
          pkCount++;

          // this is a column that is not part of the input file
          // so it's not counted through colCount and therefor the index must be increased by one
          pkIndex++;
        }
      }
      int cols = columnConstants.getColumnCount();
      for (int i=0; i < cols; i++)
      {
        ColumnData data = columnConstants.getColumnData(i);
        if (data.getIdentifier().isPkColumn()) continue;

        if (needComma)
        {
          sql.append(", ");
        }
        else
        {
          needComma = true;
        }
        String colname = meta.quoteObjectname(data.getIdentifier().getColumnName());
        sql.append(colname);
        sql.append(" = ");
        String expr = columnExpressions.get(data.getIdentifier().getColumnName());
        if (StringUtil.isNotBlank(expr))
        {
          sql.append(expr);
        }
        else if (columnConstants.isFunctionCall(i))
        {
          sql.append(columnConstants.getFunctionLiteral(i));
        }
        else
        {
          sql.append(formatter.getDefaultLiteral(data));
        }
      }
    }

    for (int i=0; i < getColCount(); i++)
    {
      ColumnIdentifier col = this.targetColumns.get(i);
      if (ignoreColumn(col)) continue;
      String colname = col.getDisplayName();
      if (!verifyTargetTable)
      {
        // if the target table was not verified, we need to make
        // sure the default case for column names is used
        colname = meta.adjustObjectnameCase(meta.removeQuotes(colname));
      }
      colname = meta.quoteObjectname(colname);
      if (keyColumns.contains(col))
      {
        this.columnMap[i] = pkIndex;
        if (pkAdded) where.append(" AND ");
        else pkAdded = true;
        where.append(colname);
        where.append(" = ");
        where.append(builder.getDmlExpression(col, DmlExpressionType.Import));
        pkIndex ++;
        pkCount ++;
      }
      else
      {
        this.columnMap[i] = colIndex;
        if (needComma)
        {
          sql.append(", ");
        }
        else
        {
          needComma = true;
        }
        sql.append(colname);
        sql.append(" = ");
        sql.append(builder.getDmlExpression(col, DmlExpressionType.Import));
        colIndex ++;
      }
    }

    if (!pkAdded)
    {
      LogMgr.logError(new CallerInfo(){}, "No primary key columns defined! Update mode not available\n", null);
      this.messages.append(ResourceMgr.getString("ErrImportNoKeyForUpdate"));
      this.messages.appendNewLine();
      this.updateStatement = null;
      this.hasErrors = true;
      throw new SQLException("No key columns defined for update mode");
    }

    if (pkCount != this.keyColumns.size())
    {
      LogMgr.logError(new CallerInfo(){}, "At least one of the supplied primary key columns was not found in the target table!", null);
      this.messages.append(ResourceMgr.getString("ErrImportUpdateKeyColumnNotFound") + "\n");
      this.updateStatement = null;
      this.hasErrors = true;
      throw new SQLException("Not enough key columns defined for update mode");
    }

    if (colIndex == 0)
    {
      LogMgr.logError(new CallerInfo(){}, "Only PK columns defined! Update mode is not available!", null);
      this.messages.append(ResourceMgr.getString("ErrImportOnlyKeyColumnsForUpdate"));
      this.messages.appendNewLine();
      this.updateStatement = null;
      if (this.isModeUpdate())
      {
        // if only update mode was specified this is an error!
        this.hasErrors = true;
        throw new ModeNotPossibleException("Only key columns available. No update mode possible");
      }
      else
      {
        this.hasWarnings = true;
      }
      return;
    }

    sql.append(where);

    if (StringUtil.isNotEmpty(this.whereClauseForUpdate))
    {
      boolean addBracket = false;
      String whereClause = this.whereClauseForUpdate.trim().toUpperCase();
      if (!whereClause.startsWith("AND") && !whereClause.startsWith("OR"))
      {
        sql.append(" AND (");
        addBracket = true;
      }
      else
      {
        sql.append(' ');
      }
      sql.append(this.whereClauseForUpdate.trim());
      if (addBracket) sql.append(")");
    }

    String updateSql = sql.toString();
    try
    {
      LogMgr.logInfo(new CallerInfo(){}, "Statement for update: " + updateSql);
      PreparedStatement stmt = this.dbConn.getSqlConnection().prepareStatement(updateSql);
      this.updateStatement = new BatchedStatement(stmt, dbConn, getRealBatchSize());
      this.updateStatement.setCommitBatch(this.commitBatch);
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error when preparing UPDATE statement", e);
      this.messages.append(ResourceMgr.getString("ErrImportInitTargetFailed"));
      this.messages.append(ExceptionUtil.getDisplay(e));
      this.updateStatement = null;
      this.hasErrors = true;
      if (e instanceof SQLException)
      {
        throw (SQLException)e;
      }
      throw new SQLException("Could not prepare instert statement", e);
    }
  }

  /**
   *  If the key columns have not been defined externally through {@link #setKeyColumns(List)}
   *  this method  is used to retrieve the key columns for the target table
   */
  private void retrieveKeyColumns()
  {
    try
    {
      // The order of the PK columns might be important, e.g. when generating an IGNORE_DUP_KEY hint for Oracle
      // so we need to retrieve the PK definition and the list of columns
      List<ColumnIdentifier> cols = this.dbConn.getMetadata().getTableColumns(targetTable);
      PkDefinition pk = this.dbConn.getMetadata().getIndexReader().getPrimaryKey(targetTable);
      this.keyColumns = new ArrayList<>();
      for (String colName : pk.getColumns())
      {
        ColumnIdentifier pkCol = ColumnIdentifier.findColumnInList(cols, colName);
        this.keyColumns.add(pkCol);
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error when retrieving key columns", e);
      this.columnMap = null;
      this.keyColumns = null;
    }
  }

  private void finishTable()
    throws SQLException
  {
    if (this.targetTable == null) return;

    boolean commitNeeded = this.transactionControl && !dbConn.getAutoCommit() && (this.commitEvery != Committer.NO_COMMIT_FLAG);
    final CallerInfo ci = new CallerInfo(){};

    try
    {
      if (this.useBatch)
      {
        if (this.insertStatement != null)
        {
          this.insertedRows += insertStatement.flush();
        }

        if (this.updateStatement != null)
        {
          this.updatedRows += updateStatement.flush();
        }

        // If the batch is executed and committed, there is no
        // need to send another commit. In fact some DBMS don't like
        // a commit or rollback if no transaction was started.
        if (commitBatch) commitNeeded = false;
      }

      closeStatements();

      runPostTableStatement();

      String msg = targetTable.getTableName() + ": " + this.getInsertedRows() + " row(s) inserted. " + this.getUpdatedRows() + " row(s) updated.";
      if (!transactionControl)
      {
        msg += " Transaction control disabled. No commit sent to server.";
      }
      else
      {
        msg += " Committing changes.";
      }

      if (commitNeeded)
      {
         this.dbConn.commit();
      }

      if (adjustSequences)
      {
        SequenceAdjuster adjuster = SequenceAdjuster.Factory.getSequenceAdjuster(dbConn);
        if (adjuster != null)
        {
          int numSequences = adjuster.adjustTableSequences(dbConn, targetTable, transactionControl);
          LogMgr.logInfo(ci,  "Adjusted " + numSequences + " sequence(s) for table " + targetTable.getTableExpression(dbConn));
        }
      }

      LogMgr.logInfo(ci, msg);

      if (this.insertedRows > -1 || this.updatedRows > -1)
      {
        this.messages.appendNewLine();
      }

      if (this.insertedRows > -1)
      {
        this.messages.append(this.insertedRows + " " + ResourceMgr.getString("MsgCopyNumRowsInserted"));
        this.messages.appendNewLine();
      }

      if (this.updatedRows > -1)
      {
        this.messages.append(this.updatedRows + " " + ResourceMgr.getString("MsgCopyNumRowsUpdated"));
      }

      if (this.badWriter != null && badWriter.getRows() > 0)
      {
        this.messages.appendNewLine();
        this.messages.append(this.badWriter.getMessage());
      }
      this.messages.appendNewLine();
      this.hasErrors = hasErrors || this.source.hasErrors();
      this.hasWarnings = hasWarnings || this.source.hasWarnings();
    }
    catch (SQLException e)
    {
      if (commitNeeded)
      {
        this.dbConn.rollbackSilently(ci);
      }
      LogMgr.logError(ci, "Error commiting changes", e);
      this.hasErrors = true;
      this.messages.append(ExceptionUtil.getDisplay(e));
      this.messages.appendNewLine();
      throw e;
    }
  }

  /**
   * Return the messages generated during import.
   * Calling this, clears the message buffer
   * @return the message buffer.
   * @see workbench.util.MessageBuffer#getBuffer()
   */
  public CharSequence getMessages()
  {
    return messages.getBuffer();
  }

  public void copyMessages(MessageBuffer target)
  {
    target.append(this.messages);
    clearMessages();
  }

  public void clearMessages()
  {
    this.messages.clear();
  }

  /**
   *  Callback from the RowDataProducer
   */
  @Override
  public void importFinished()
  {
    if (!isRunning) return;
    try
    {
      this.finishTable();
    }
    catch (SQLException sql)
    {
      // already logged in finishTable()
    }
    catch (Exception e)
    {
      // log all others...
      LogMgr.logError(new CallerInfo(){}, "Error when commiting changes", e);
      this.messages.append(ExceptionUtil.getDisplay(e));
      this.hasErrors = true;
    }
    finally
    {
      this.isRunning = false;
      if (!multiTable)
      {
        if (this.progressMonitor != null) this.progressMonitor.jobFinished();
      }
    }

    this.hasErrors = this.hasErrors || this.source.hasErrors();
    this.hasWarnings = this.hasWarnings || this.source.hasWarnings();
  }

  private void cleanupRollback()
  {
    try
    {
      cancelStatement(insertStatement);
      cancelStatement(updateStatement);
      closeStatements();

      if (this.transactionControl && !this.dbConn.getAutoCommit())
      {
        LogMgr.logInfo(new CallerInfo(){}, "Rollback changes");
        this.dbConn.rollback();
        this.updatedRows = 0;
        this.insertedRows = 0;
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error on rollback", e);
      this.messages.append(ExceptionUtil.getDisplay(e));
      this.hasErrors = true;
    }
    this.isRunning = false;
    if (this.progressMonitor != null) this.progressMonitor.jobFinished();
  }

  @Override
  public void tableImportError()
  {
    cleanupRollback();
    if (this.tableStatements != null && this.tableStatements.getRunPostStatementAfterError() && targetTable != null)
    {
      try
      {
        this.tableStatements.runPostTableStatement(dbConn, targetTable);
      }
      catch (TableStatementError tse)
      {
        LogMgr.logWarning(new CallerInfo(){}, "Error running post-table statement", tse);
        String err = ResourceMgr.getFormattedString("ErrTableStmt", tse.getTable(), tse.getMessage());
        messages.append(err);
        messages.appendNewLine();
      }
      catch (Exception e)
      {
        LogMgr.logWarning(new CallerInfo(){}, "Error running post-table statement", e);
      }
    }
  }

  @Override
  public void importCancelled()
  {
    if (!isRunning) return;
    if (this.partialImportEnded)
    {
      this.importFinished();
      return;
    }

    cleanupRollback();
    this.hasErrors = this.hasErrors || this.source.hasErrors();
    this.hasWarnings = this.hasWarnings || this.source.hasWarnings();
  }

  private void closeStatements()
  {
    if (this.insertStatement != null)
    {
      try { this.insertStatement.close(); } catch (Throwable th) {}
    }
    if (this.updateStatement != null)
    {
      try { this.updateStatement.close(); } catch (Throwable th) {}
    }
    if (columnConstants != null)
    {
      columnConstants.done();
    }
  }

  public int getReportInterval()
  {
    return this.reportInterval;
  }

  @Override
  public void setReportInterval(int interval)
  {
    if (interval > 0)
    {
      this.reportInterval = interval;
    }
    else
    {
      this.reportInterval = 0;
    }
  }

}
