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
package workbench.gui.dbobjects;

import java.awt.Frame;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import workbench.interfaces.DbExecutionListener;
import workbench.interfaces.ProgressReporter;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.DbMetadata;
import workbench.db.DbObject;
import workbench.db.TableIdentifier;
import workbench.db.TableSelectBuilder;
import workbench.db.WbConnection;
import workbench.db.exporter.DataExporter;
import workbench.db.exporter.ExportType;

import workbench.gui.WbSwingUtilities;
import workbench.gui.dialogs.export.ExportFileDialog;

import workbench.storage.ResultInfo;
import workbench.storage.RowActionMonitor;

import workbench.util.CollectionUtil;
import workbench.util.StringUtil;
import workbench.util.WbFile;
import workbench.util.WbThread;

/**
 *
 * @author Thomas Kellerer
 */
public class TableExporter
  implements DbExecutionListener
{
  private DataExporter exporter;
  private ProgressDialog progress;
  private List<TableIdentifier> toExport;
  private List<ColumnIdentifier> columnsToExport;
  private boolean openExportFile;

  // Can be a file name or directory name, depending on how many tables were selected
  private String selectedOutput;
  private String extension;

  public TableExporter(WbConnection conn)
  {
    exporter = new DataExporter(conn);
    exporter.setReportInterval(ProgressReporter.DEFAULT_PROGRESS_INTERVAL);
  }

  public DataExporter getExporter()
  {
    return exporter;
  }

  public boolean selectTables(List<? extends DbObject> tables, Frame caller)
  {
    if (CollectionUtil.isEmpty(tables)) return false;

    boolean singleTableExport = tables.size() == 1;

    ExportFileDialog dialog;
    if (singleTableExport)
    {
      ResultInfo info = null;
      if (tables.get(0) instanceof TableIdentifier)
      {
        try
        {
          info = new ResultInfo((TableIdentifier)tables.get(0), exporter.getConnection());
        }
        catch (Exception ex)
        {
          LogMgr.logError(new CallerInfo(){}, "Could not retrieve table columns", ex);
        }
      }
      dialog = new ExportFileDialog(caller, info);
      dialog.setSelectDirectoryOnly(false);
    }
    else
    {
      dialog = new ExportFileDialog(caller);
      dialog.setSelectDirectoryOnly(true);
    }
    dialog.restoreSettings();
    dialog.setAllowOpenFile(singleTableExport);
    String names = tables.stream().map(t -> t.getObjectName()).collect(Collectors.joining(", "));

    String title = ResourceMgr.getString("MnuTxtSpoolData").replace("&", "");
    WbConnection dbConnection = exporter.getConnection();
    DbMetadata meta = dbConnection.getMetadata();
    dialog.setExportInfo(names);
    boolean answer = dialog.selectOutput(title);
    if (!answer) return false;

    if (singleTableExport)
    {
      columnsToExport = dialog.getColumnsToExport();
    }
    this.openExportFile = dialog.doOpenFile();
    this.selectedOutput = dialog.getSelectedFilename();
    dialog.setExporterOptions(exporter);
    this.toExport = new ArrayList<>(tables.size());

    ExportType type = dialog.getExportType();
    this.extension = type.getDefaultFileExtension();

    for (DbObject dbo : tables)
    {
      String ttype = dbo.getObjectType();
      if (ttype == null) continue;
      if (!meta.objectTypeCanContainData(ttype)) continue;
      if (!(dbo instanceof TableIdentifier)) continue;
      this.toExport.add((TableIdentifier)dbo);
    }
    return answer;
  }

  public void startExport(final Frame parent)
  {
    if (toExport == null) return;

    progress = new ProgressDialog(ResourceMgr.getString("MsgSpoolWindowTitle"), parent, exporter);
    exporter.setRowMonitor(progress.getMonitor());
    progress.showProgressWindow();

    progress.getInfoPanel().setMonitorType(RowActionMonitor.MONITOR_PLAIN);
    progress.getInfoPanel().setCurrentObject(ResourceMgr.getString("MsgDiffRetrieveDbInfo"), -1, -1);

    // Creating the tableExportJobs should be done in a background thread
    // as this can potentially take some time (especially with Oracle) as for
    // each table that should be exported, the definition needs to be retrieved.
    WbThread th = new WbThread("Init export")
    {
      @Override
      public void run()
      {
        if (toExport.size() == 1)
        {
          exportSingleTable(parent);
        }
        else
        {
          exportTableList(parent);
        }
        progress.getInfoPanel().setMonitorType(RowActionMonitor.MONITOR_EXPORT);
        exporter.addExecutionListener(TableExporter.this);
        exporter.startBackgroundExport();
      }
    };
    th.start();
  }

  private void exportSingleTable(final Frame parent)
  {
    try
    {
      TableSelectBuilder builder = new TableSelectBuilder(exporter.getConnection());
      String query = builder.getSelectForColumns(toExport.get(0), columnsToExport, 0);
      exporter.addQueryJob(query, new WbFile(selectedOutput), null);
    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not create export query", ex);
      WbSwingUtilities.showMessage(parent, ex.getMessage());
    }
  }

  private void exportTableList(final Frame parent)
  {
    for (TableIdentifier tbl : toExport)
    {
      String fname = StringUtil.makeFilename(tbl.getObjectName());
      WbFile f = new WbFile(selectedOutput, fname + extension);
      try
      {
        exporter.addTableExportJob(f, tbl);
      }
      catch (SQLException e)
      {
        LogMgr.logError(new CallerInfo(){}, "Error adding ExportJob", e);
        WbSwingUtilities.showMessage(parent, e.getMessage());
      }
    }
  }

  @Override
  public void executionStart(WbConnection conn, Object source)
  {
  }

  @Override
  public void executionEnd(WbConnection conn, Object source)
  {
    if (progress != null)
    {
      progress.finished();
      progress.dispose();
    }
    if (exporter != null && !exporter.isSuccess())
    {
      CharSequence msg = exporter.getErrors();
      WbSwingUtilities.showErrorMessage(msg.toString());
    }
    if (openExportFile)
    {
      ExportFileDialog.openFile(exporter.getOutputFile());
    }
  }

}
