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

import java.io.File;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import workbench.interfaces.ImportFileParser;
import workbench.interfaces.JobErrorHandler;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.ColumnIdentifier;
import workbench.db.DBID;
import workbench.db.DbObjectFinder;
import workbench.db.DbSearchPath;
import workbench.db.TableDefinition;
import workbench.db.TableIdentifier;
import workbench.db.TableSelectBuilder;
import workbench.db.WbConnection;
import workbench.db.exporter.BlobMode;
import workbench.db.importer.modifier.ImportValueModifier;

import workbench.storage.RowActionMonitor;

import workbench.util.BlobDecoder;
import workbench.util.CaseInsensitiveComparator;
import workbench.util.MessageBuffer;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;
import workbench.util.ValueConverter;
import workbench.util.WbFile;

/**
 *
 * @author  Thomas Kellerer
 */
public abstract class AbstractImportFileParser
  implements ImportFileParser
{
  protected File inputFile;
  protected ImportFileLister sourceFiles;

  protected String tableName;
  protected String targetSchema;
  protected TableDefinition targetTable;
  protected String encoding;

  // this indicates an import of several files from a single
  // directory into one table
  protected boolean multiFileImport;

  protected boolean trimValues;
  protected List<ImportFileColumn> importColumns;

  protected volatile boolean cancelImport;
  protected boolean regularStop;

  protected DataReceiver receiver;
  protected boolean abortOnError;
  protected WbConnection connection;

  protected MessageBuffer messages = new MessageBuffer();
  protected boolean sharedMessages;
  protected boolean hasErrors;
  protected boolean hasWarnings;

  protected ImportFileHandler fileHandler = new ImportFileHandler();

  protected List<String> currentRowValues;

  protected ImportValueModifier valueModifier;
  protected ValueConverter converter = new ValueConverter();

  protected JobErrorHandler errorHandler;

  protected List<File> filesProcessed = new ArrayList<>(25);

  protected final BlobDecoder blobDecoder = new BlobDecoder();
  private BlobMode defaultBlobMode = BlobMode.SaveToFile;
  private final Map<String, BlobMode> columnBlobModes = new TreeMap<>(CaseInsensitiveComparator.INSTANCE);

  protected RowActionMonitor rowMonitor;
  protected boolean ignoreMissingColumns;
  protected boolean clobsAreFilenames;
  protected boolean ignoreAllNullRows;
  protected boolean checkTargetWithQuery;

  public AbstractImportFileParser()
  {
    ignoreAllNullRows = Settings.getInstance().getBoolProperty("workbench.import.ignore.all_nulls", true);
  }

  public AbstractImportFileParser(File aFile)
  {
    this();
    this.inputFile = aFile;
  }

  @Override
  public void setMessageBuffer(MessageBuffer buffer)
  {
    if (buffer != null)
    {
      messages = buffer;
      sharedMessages = true;
    }
  }

  @Override
  public void setIgnoreMissingColumns(boolean flag)
  {
    ignoreMissingColumns = flag;
  }

  @Override
  public void setRowMonitor(RowActionMonitor rowMonitor)
  {
    this.rowMonitor = rowMonitor;
  }

  @Override
  public List<File> getProcessedFiles()
  {
    return filesProcessed;
  }

  @Override
  public ImportFileHandler getFileHandler()
  {
    return this.fileHandler;
  }

  public ImportValueModifier getValueModifier()
  {
    return valueModifier;
  }

  @Override
  public void setValueModifier(ImportValueModifier mod)
  {
    this.valueModifier = mod;
  }

  @Override
  public void setTargetSchema(String schema)
  {
    this.targetSchema = schema;
  }

  @Override
  public void setReceiver(DataReceiver rec)
  {
    this.receiver = rec;
  }

  @Override
  public void setInputFile(File file)
  {
    this.sourceFiles = null;
    this.inputFile = file;
  }

  @Override
  public void setCheckTargetWithQuery(boolean flag)
  {
    this.checkTargetWithQuery = flag;
  }

  /**
   * Enables or disables multi-file import. If multi file
   * import is enabled, all files that are defined will be imported into the same
   * table defined by {@link #setTableName(java.lang.String) }
   *
   * @param flag
   * @see #setSourceFiles(workbench.db.importer.ImportFileLister)
   * @see #setTableName(java.lang.String)
   */
  @Override
  public void setMultiFileImport(boolean flag)
  {
    this.multiFileImport = flag;
  }

  @Override
  public boolean isMultiFileImport()
  {
    return this.multiFileImport;
  }

  @Override
  public void setSourceFiles(ImportFileLister source)
  {
    this.inputFile = null;
    this.sourceFiles = source;
  }

  @Override
  public void setTableName(String aName)
  {
    this.tableName = aName;
    this.targetTable = null;
    this.importColumns = null;
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
  public String getSourceFilename()
  {
    if (this.inputFile == null) return null;
    if (this.fileHandler != null)
    {
      return fileHandler.getInputFilename();
    }
    return this.inputFile.getAbsolutePath();
  }

  @Override
  public String getLastRecord()
  {
    return StringUtil.listToString(currentRowValues, ',');
  }

  @Override
  public void setValueConverter(ValueConverter newConverter)
  {
    if (newConverter != null)
    {
      this.converter = newConverter;
      if (this.targetTable != null)
      {
        checkDateTimeParsing(targetTable);
      }
    }
  }

  public void setBlobModeForColumn(String colName, BlobMode mode)
  {
    if (StringUtil.isNotEmpty(colName) && mode != null)
    {
      columnBlobModes.put(colName, mode);
    }
  }

  public BlobMode getBlobMode(String colName)
  {
    return columnBlobModes.getOrDefault(colName, defaultBlobMode);
  }

  public BlobMode getDefaultBlobMode()
  {
    return defaultBlobMode;
  }

  @Override
  public void setDefaultBlobMode(BlobMode mode)
  {
    defaultBlobMode = mode;
  }

  @Override
  public void setColumnBlobModes(Map<String, BlobMode> modes)
  {
    if (modes != null)
    {
      columnBlobModes.clear();
      columnBlobModes.putAll(modes);
    }
  }

  @Override
  public abstract void setColumns(List<ColumnIdentifier> columnList)
    throws SQLException;

  /**
   * Defines the mapping from input to target columns in case no target table is available.
   *
   * This is used when importing from the clipboard (as the target is a datastore not a table)
   *
   * @param fileColumns the columns in the order as they appear in the file
   * @param targetColumns the columns in the order as they appear in the target.
   */
  public void setColumnMap(List<ColumnIdentifier> fileColumns, List<ColumnIdentifier> targetColumns)
  {
    importColumns = ImportFileColumn.createList();
    for (ColumnIdentifier sourceCol : fileColumns)
    {
      int index = targetColumns.indexOf(sourceCol);
      if (index > -1)
      {
        ColumnIdentifier col = targetColumns.get(index);
        ImportFileColumn importCol = new ImportFileColumn(col);
        importCol.setTargetIndex(index);
        importColumns.add(importCol);
      }
      else
      {
        importColumns.add(ImportFileColumn.SKIP_COLUMN);
      }
    }
  }

  protected TableIdentifier createTargetTableId()
  {
    TableIdentifier table = new TableIdentifier(this.getTablename(), this.connection);
    table.setPreserveQuotes(true);
    if (this.targetSchema != null)
    {
      table.setSchema(this.targetSchema);
    }
    if (this.connection != null && !checkTargetWithQuery)
    {
      table.adjustCase(this.connection);
      if (table.getSchema() == null)
      {
        table.setSchema(table.getSchemaToUse(connection));
      }
      if (table.getCatalog() == null)
      {
        table.setCatalog(table.getCatalogToUse(connection));
      }
    }
    return table;
  }

  protected String getTablename()
  {
    return this.tableName;
  }

  protected TableDefinition getTargetTable()
    throws SQLException
  {
    if (getTablename() == null) return null;
    if (this.targetTable != null) return targetTable;

    TableIdentifier table = createTargetTableId();

    // we can't verify the table without a connection
    // not having one isn't an error if we are importing the clipboard into a DataStore
    if (this.connection == null)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Can't verify target: " + table.getTableExpression());
      return null;
    }

    try
    {
      if (checkTargetWithQuery)
      {
        try
        {
          TableSelectBuilder builder = new TableSelectBuilder(connection, TableSelectBuilder.TABLEDATA_TEMPLATE_NAME);

          // make sure the table name is used as specified by the user
          builder.setNeverUseFQN(true);

          String query = builder.getSelectForColumns(table, null, 1);
          LogMgr.logDebug(new CallerInfo(){}, "Using query to detect table columns:\n" + query);

          List<ColumnIdentifier> columns = SqlUtil.getResultSetColumns(query, connection);
          targetTable = new TableDefinition(table, columns);
        }
        catch (Exception ex)
        {
          LogMgr.logError(new CallerInfo(){}, "Error trying to detect the target table using a query", ex);
        }
      }
      else
      {
        if (table.getSchema() == null)
        {
          // if no schema was specified make sure we search the table on the search path (if supported)
          // otherwise e.g. temporary tables in Postgres wouldn't be found
          DbSearchPath handler = DbSearchPath.Factory.getSearchPathHandler(connection);
          if (handler.isRealSearchPath())
          {
            DbObjectFinder finder = new DbObjectFinder(connection);
            table = finder.searchObjectOnPath(table, connection.getMetadata().getTableTypesArray());
          }
        }
        targetTable = connection.getMetadata().getTableDefinition(table, true);
      }
    }
    catch (SQLException ex)
    {
      String msg = ResourceMgr.getFormattedString("ErrTargetTableNotFound", table.getTableExpression(connection));
      this.messages.append(msg);
      this.messages.appendNewLine();
      this.messages.append(ex.getMessage());
      this.messages.appendNewLine();
      this.hasErrors = true;
      throw ex;
    }
    checkDateTimeParsing(targetTable);
    return targetTable;
  }

  private void checkDateTimeParsing(TableDefinition targetTable)
  {
    converter.setCompareDateTimeValuesToPatterns(false);
    if (DBID.Oracle.isDB(connection))
    {
      long dateColumnCount = targetTable.getColumns().stream().filter(col -> "DATE".equalsIgnoreCase(col.getDbmsType())).count();
      long timestampColumnCount =
        targetTable.getColumns().stream().filter(col -> col.getDbmsType().startsWith("TIMESTAMP")).count();
      if (dateColumnCount > 0 && timestampColumnCount > 0)
      {
        converter.setCompareDateTimeValuesToPatterns(true);
      }
    }
  }

  @Override
  public void setConnection(WbConnection aConn)
  {
    this.connection = aConn;
  }

  @Override
  public String getEncoding()
  {
    return (this.encoding == null ? Settings.getInstance().getDefaultDataEncoding() : this.encoding);
  }

  public void setEncoding(String enc)
  {
    if (enc == null) return;
    this.encoding = enc;
  }

  @Override
  public MessageBuffer getMessages()
  {
    if (this.sharedMessages) return null;
    return this.messages;
  }

  @Override
  public void setAbortOnError(boolean flag)
  {
    this.abortOnError = flag;
  }

  /**
   * Stop processing the current input file.
   *
   * This is used by the DataImporter to signal that all selected rows
   * were imported (in case not all rows should be imported).
   *
   * This sets cancelImport to true, but to distinguish this
   * from a "normal" cancel, it also sets regularStop to true.
   */
  @Override
  public void stop()
  {
    LogMgr.logDebug(new CallerInfo(){}, "Stopping import");
    this.cancelImport = true;
    this.regularStop = true;
  }

  @Override
  public boolean wasCancelled()
  {
    return cancelImport;
  }

  @Override
  public void cancel()
  {
    LogMgr.logDebug(new CallerInfo(){}, "Cancelling import");
    this.cancelImport = true;
    this.regularStop = false;
  }

  @Override
  public void start()
    throws Exception
  {
    this.receiver.setTableCount(-1); // clear multi-table flag in receiver
    this.receiver.setCurrentTable(-1);

    try
    {
      if (this.sourceFiles != null)
      {
        processDirectory();
      }
      else
      {
        processOneFile();
      }
    }
    finally
    {
      if (this.sourceFiles != null)
      {
        this.receiver.endMultiTable();
      }

      if (this.cancelImport && !regularStop)
      {
        this.receiver.importCancelled();
      }
      else
      {
        this.receiver.importFinished();
      }
      try { this.fileHandler.done(); } catch (Throwable th) {}
    }
  }

  /**
   * Reset any variables that are needed to keep the status
   * for importing a single file.
   *
   * This will be called by processDirectory() before processing each file.
   */
  protected void resetForFile()
  {
    this.cancelImport = false;
    this.regularStop = false;
    if (!sharedMessages) messages.clear();
    if (!multiFileImport) tableName = null;
  }

  protected TablenameResolver getTableNameResolver()
  {
    return new DefaultTablenameResolver();
  }

  protected void processDirectory()
    throws Exception
  {
    if (this.sourceFiles == null) throw new IllegalStateException("Cannot process source directory without FileNameSorter");

    this.sourceFiles.setTableNameResolver(getTableNameResolver());
    if (!sourceFiles.containsFiles())
    {
      String msg = ResourceMgr.getFormattedString("ErrImpNoFiles", sourceFiles.getExtension(), sourceFiles.getDirectory());
      this.messages.append(msg);
      this.hasErrors = true;
      throw new SQLException("No files with extension '" + sourceFiles.getExtension() + "' in directory " + sourceFiles.getDirectory());
    }

    List<WbFile> toProcess = null;
    try
    {
      toProcess = sourceFiles.getFiles();
    }
    catch (CycleErrorException e)
    {
      cancelImport = true;
      LogMgr.logError(new CallerInfo(){}, "Error when checking dependencies", e);
      throw e;
    }

    // The receiver only needs to pre-process the full table list
    // if checkDependencies is turned on, otherwise a possible
    // table delete can be done during the single table import
    this.receiver.setTableList(sourceFiles.getTableList());

    int count = toProcess == null ? 0 : toProcess.size();
    if (count == 0)
    {
      String msg = ResourceMgr.getFormattedString("ErrImpNoMatch", sourceFiles.getDirectory());
      this.messages.append(msg);
      this.hasErrors = true;
      throw new SQLException("No matching tables found for files in directory " + sourceFiles.getDirectory());
    }

    this.receiver.setTableCount(count);
    if (!multiFileImport)
    {
      this.receiver.beginMultiTable();
    }

    int currentFile = 0;

    for (WbFile sourceFile : toProcess)
    {
      if (this.cancelImport) break;

      try
      {
        currentFile++;
        resetForFile();
        this.receiver.setCurrentTable(currentFile);
        if (!multiFileImport)
        {
          // Only reset the import columns and table if multiple files
          // are imported into multiple tables.
          // If multifileimport is true, then all files are imported into the same table!
          TableIdentifier tbl = sourceFiles.getTableForFile(sourceFile);
          setTableName(tbl.getTableExpression());
        }
        this.inputFile = sourceFile;
        this.processOneFile();
      }
      catch (Exception e)
      {
        this.hasErrors = true;
        this.receiver.tableImportError();
        if (this.abortOnError) throw e;
      }
    }

  }

  protected List<ColumnIdentifier> getColumnsToImport()
  {
    List<ColumnIdentifier> result = new ArrayList<>();
    for (ImportFileColumn col : importColumns)
    {
      if (col.getTargetIndex() >= 0)
      {
        result.add(col.getColumn());
      }
    }
    return result;
  }

  protected void setupFileHandler()
    throws IOException
  {
    this.fileHandler.setMainFile(this.inputFile, this.getEncoding());
  }

  protected abstract void processOneFile()
    throws Exception;

  @Override
  public abstract Map<Integer, Object> getInputColumnValues(Collection<Integer> inputFileIndexes);

  /**
   *  Returns the column list as a comma separated string
   *  that can be used for the WbImport command
   */
  @Override
  public String getColumns()
  {
    StringBuilder result = new StringBuilder();

    int colCount = 0;
    for (ImportFileColumn col : importColumns)
    {
      if (colCount > 0) result.append(',');
      if (col != null && col.getTargetIndex() != -1)
      {
        result.append(col.getColumn().getColumnName());
      }
      else
      {
        result.append(RowDataProducer.SKIP_INDICATOR);
      }
      colCount ++;
    }
    return result.toString();
  }

  @Override
  public void setErrorHandler(JobErrorHandler handler)
  {
    this.errorHandler = handler;
  }

  @Override
  public void setTrimValues(boolean trim)
  {
    this.trimValues = trim;
  }

  protected boolean isColumnFiltered(int colIndex, String importValue)
  {
    if (importColumns == null) return false;
    if (colIndex < 0 || colIndex > importColumns.size()) return false;

    ImportFileColumn fileCol = importColumns.get(colIndex);
    if (fileCol.getColumnFilter() == null) return false;

    int type = fileCol.getColumn().getDataType();

    if (SqlUtil.isBlobType(type)) return false;
    if (SqlUtil.isClobType(type) && clobsAreFilenames) return false;

    if (importValue == null)
    {
      return true;
    }
    Matcher m = fileCol.getColumnFilter().matcher(importValue);
    return !m.matches();
  }

  /**
   * Return the index of the specified column
   * in the import file.
   *
   * @param colName the column to search for
   * @return the index of the named column or -1 if the column was not found
   */
  protected int getColumnIndex(String colName)
  {
    if (colName == null) return -1;
    if (importColumns == null) return -1;
    return this.importColumns.indexOf(colName);
  }

  protected ColumnIdentifier getColumn(String colName)
  {
    if (colName == null) return null;
    if (importColumns == null) return null;
    for (ImportFileColumn col : importColumns)
    {
      if (col.getColumn().getColumnName().equalsIgnoreCase(colName))
      {
        return col.getColumn();
      }
    }
    return null;
  }

  @Override
  public void addColumnFilter(String colname, String regex)
  {
    int index = this.getColumnIndex(colname);
    if (index == -1) return;

    try
    {
      Pattern p = Pattern.compile(regex);
      importColumns.get(index).setColumnFilter(p);
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error compiling regular expression " + regex + " for column " + colname, e);
      String msg = ResourceMgr.getString("ErrImportBadRegex");
      msg = StringUtil.replace(msg, "%regex%", regex);
      this.messages.append(msg);
      this.messages.appendNewLine();
      this.hasWarnings = true;
      importColumns.get(index).setColumnFilter(null);
    }
  }

  protected boolean isOnlyNull(Object[] row)
  {
    if (row == null) return true;
    for (Object obj : row)
    {
      if (obj != null) return false;
    }
    return true;
  }


}
