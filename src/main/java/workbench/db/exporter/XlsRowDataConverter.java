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
package workbench.db.exporter;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.storage.RowData;

import workbench.util.FileUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;
import workbench.util.WbFile;

import org.apache.poi.hssf.usermodel.HSSFFormulaEvaluator;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ooxml.POIXMLProperties;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.RichTextString;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.xssf.streaming.SXSSFFormulaEvaluator;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbookType;

/**
 * Export data into an Excel spreadsheet using Apache's POI
 *
 * @author Alessandro Palumbo
 * @author Thomas Kellerer
 */
public class XlsRowDataConverter
  extends RowDataConverter
{
  public static final String INFO_SHEETNAME = "SQL";

  private Workbook workbook = null;
  private Sheet sheet = null;
  private ExcelDataFormat excelFormat = null;
  private boolean useXLSX;

  // controls the offset of the first row when a header is used
  private int firstRow = 0;

  private int rowOffset;
  private int columnOffset;

  private boolean optimizeCols = true;
  private boolean append;
  private int targetSheetIndex = -1;
  private String targetSheetName;
  private String outputSheetName;
  final private Map<Integer, CellStyle> styles = new HashMap<>(10);
  final private Map<Integer, CellStyle> headerStyles = new HashMap<>(10);

  public XlsRowDataConverter()
  {
    super();
  }

  public void setAppend(boolean flag)
  {
    this.append = flag;
  }

  /**
   * Switch to the new OOXML Format
   */
  public void setUseXLSX()
  {
    useXLSX = true;
  }

  public void setTargetSheetName(String name)
  {
    this.targetSheetName = name;
  }

  public void setTargetSheetIndex(int index)
  {
    this.targetSheetIndex = index;
  }

  public void setOptimizeColumns(boolean flag)
  {
    this.optimizeCols = flag;
  }

  public void setStartOffset(int startRow, int startColumn)
  {
    rowOffset = startRow;
    columnOffset = startColumn;
  }

  // This should not be called in the constructor as
  // at that point in time the formatters are not initialized
  private void createFormatters()
  {
    String dateFormat = this.defaultDateFormatter != null ? this.defaultDateFormatter.toPattern() : StringUtil.ISO_DATE_FORMAT;
    String tsFormat = this.defaultTimestampFormatter != null ? this.defaultTimestampFormatter.getPatternWithoutTimeZone() : StringUtil.ISO_TIMESTAMP_FORMAT;
    String numFormat = this.defaultNumberFormatter != null ? this.defaultNumberFormatter.toFormatterPattern() : "0.00";
    excelFormat = new ExcelDataFormat(numFormat, dateFormat, "0", tsFormat);
  }

  private void loadExcelFile()
  {
    InputStream in = null;
    try
    {
      WbFile file = new WbFile(getOutputFile());
      String extension = file.getExtension();
      useXLSX = extension.equalsIgnoreCase("xlsx") || extension.equalsIgnoreCase("xlsm");
      in = new FileInputStream(file);
      if (useXLSX)
      {
        workbook = new XSSFWorkbook(in);
      }
      else
      {
        workbook = new HSSFWorkbook(in);
      }
    }
    catch (IOException io)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not load Excel file", io);
      workbook = null;
    }
    finally
    {
      FileUtil.closeQuietely(in);
    }
  }

  private XSSFWorkbookType getTypeToUse()
  {
    if (enableMacros())
    {
      return XSSFWorkbookType.XLSM;
    }
    return XSSFWorkbookType.XLSX;
  }

  private boolean enableMacros()
  {
    if (getOutputFile() == null) return false;

    if (useXLSX)
    {
      WbFile out = new WbFile(getOutputFile());
      String ext = out.getExtension();
      if (ext != null && ext.toLowerCase().equals("xlsm"))
      {
        return true;
      }
    }
    return false;
  }

  @Override
  public StringBuilder getStart()
  {
    firstRow = 0;
    outputSheetName = null;

    boolean loadFile = append || this.targetSheetIndex > 0 || this.targetSheetName != null;

    if (loadFile && getOutputFile().exists())
    {
      loadExcelFile();
    }
    else
    {
      if (useXLSX)
      {
        XSSFWorkbook wb = new XSSFWorkbook(getTypeToUse());
        if (Settings.getInstance().useStreamingPOI() && (formulaColumns.isEmpty() || !optimizeCols))
        {
          LogMgr.logInfo(new CallerInfo(){}, "Using XSSF streaming API to write file: " + getOutputFile());
          this.workbook = new SXSSFWorkbook(wb, -1, true);
        }
        else
        {
          this.workbook = wb;
        }
        if (isTemplate())
        {
          makeTemplate();
        }
      }
      else
      {
        workbook = new HSSFWorkbook();
      }
    }

    createFormatters();
    excelFormat.setupWithWorkbook(workbook, getEnableAutoFilter());
    styles.clear();
    headerStyles.clear();

    String suppliedTitle = getPageTitle(null);

    if (this.targetSheetIndex > 0 && this.targetSheetIndex <= workbook.getNumberOfSheets())
    {
      // The user supplies a one based sheet index
      sheet = workbook.getSheetAt(targetSheetIndex - 1);
      if (suppliedTitle != null)
      {
        workbook.setSheetName(targetSheetIndex - 1, suppliedTitle);
      }
    }
    else if (this.targetSheetName != null)
    {
      sheet = workbook.getSheet(targetSheetName);
      if (sheet == null)
      {
        LogMgr.logWarning(new CallerInfo(){}, "Sheet '" + targetSheetName + "' not found!");
        targetSheetIndex = -1;
        targetSheetName = null;
      }
      else
      {
        targetSheetIndex = workbook.getSheetIndex(sheet);
      }
      if (sheet != null && suppliedTitle != null)
      {
        workbook.setSheetName(targetSheetIndex, suppliedTitle);
      }
    }
    else
    {
      this.targetSheetIndex = -1;
    }

    if (sheet == null)
    {
      String sheetTitle = getPageTitle("SQLExport");
      if (append)
      {
        sheetTitle = getUniqueSheetTitle(sheetTitle);
      }
      sheet = workbook.createSheet(sheetTitle);
    }

    if (optimizeCols && sheet instanceof SXSSFSheet)
    {
      ((SXSSFSheet)sheet).trackAllColumnsForAutoSizing();
    }

    if (includeColumnComments)
    {
      Row commentRow = createSheetRow(0);
      firstRow = 1;
      int column = 0;
      for (int c = 0; c < this.metaData.getColumnCount(); c++)
      {
        if (includeColumnInExport(c))
        {
          Cell cell = createSheetCell(commentRow, column);
          setCellValueAndStyle(cell, StringUtil.trimQuotes(this.metaData.getColumn(c).getComment()), true, false, c);
          column ++;
        }
      }
    }

    if (writeHeader)
    {
      // table header with column names
      Row headRow = createSheetRow(firstRow);
      firstRow ++;
      int column = 0;
      for (int c = 0; c < this.metaData.getColumnCount(); c++)
      {
        if (includeColumnInExport(c))
        {
          Cell cell = createSheetCell(headRow, column);
          setCellValueAndStyle(cell, SqlUtil.removeObjectQuotes(this.metaData.getColumnDisplayName(c)), true, false, c);
          column ++;
        }
      }
    }
    return null;
  }

  private String getUniqueSheetTitle(String title)
  {
    int numSheets = workbook.getNumberOfSheets();
    int maxNr = Integer.MIN_VALUE;
    String highestName = null;

    for (int i=0; i < numSheets; i++)
    {
      String name = workbook.getSheetName(i);
      if (name.startsWith(title))
      {
        int nr = StringUtil.getFirstNumber(name);
        if (nr > maxNr)
        {
          maxNr = nr;
          highestName = name;
        }
      }
    }
    if (highestName != null)
    {
      String newTitle = StringUtil.incrementCounter(highestName, 2);
      LogMgr.logInfo(new CallerInfo(){},
        "Using sheet name \"" + newTitle + "\" instead of \"" + title + "\" for outputfile \"" + getOutputFile().getAbsolutePath() + "\" to make it unique");
      return newTitle;
    }

    return title;
  }

  @Override
  public String getTargetFileDetails()
  {
    return outputSheetName;
  }

  private Cell createSheetCell(Row row, int cellIndex)
  {
    if (applyFormatting())
    {
      return row.createCell(cellIndex + columnOffset);
    }
    Cell cell = row.getCell(cellIndex + columnOffset);
    if (cell == null)
    {
      cell = row.createCell(cellIndex + columnOffset);
    }
    return cell;
  }

  private Row createSheetRow(int rowIndex)
  {
    // TODO: shift rows down in order to preserver possible formulas
    // int rows = sheet.getLastRowNum();
    // sheet.shiftRows(rowIndex + rowOffset, rows, 1);

    if (this.applyFormatting())
    {
      return sheet.createRow(rowIndex + rowOffset);
    }

    Row row = sheet.getRow(rowIndex + rowOffset);
    if (row == null)
    {
      row = sheet.createRow(rowIndex + rowOffset);
    }
    return row;
  }

  @Override
  public StringBuilder getEnd(long totalRows)
  {
    if (getAppendInfoSheet())
    {
      writeInfoSheet();
    }

    if (!formulaColumns.isEmpty())
    {
      if (workbook instanceof XSSFWorkbook)
      {
        XSSFFormulaEvaluator.evaluateAllFormulaCells((XSSFWorkbook)workbook);
      }
      else if (workbook instanceof SXSSFWorkbook)
      {
        SXSSFFormulaEvaluator.evaluateAllFormulaCells((SXSSFWorkbook)workbook, false);
      }
      else if (workbook instanceof HSSFWorkbook)
      {
        HSSFFormulaEvaluator.evaluateAllFormulaCells((HSSFWorkbook)workbook);
      }
    }

    if (getEnableFixedHeader() && writeHeader)
    {
      sheet.createFreezePane(0, firstRow);
    }

    if (getEnableAutoFilter() && writeHeader)
    {
      String lastColumn = CellReference.convertNumToColString(metaData.getColumnCount() - 1 + columnOffset);
      String firstColumn = CellReference.convertNumToColString(columnOffset);

      String rangeName = firstColumn + Integer.toString(rowOffset + 1) + ":" + lastColumn + Long.toString(totalRows + 1 + rowOffset);
      CellRangeAddress range = CellRangeAddress.valueOf(rangeName);
      sheet.setAutoFilter(range);
    }

    if (optimizeCols)
    {
      for (int col = 0; col < this.metaData.getColumnCount(); col++)
      {
        sheet.autoSizeColumn(col + columnOffset);
      }

      int addChars = Settings.getInstance().getIntProperty("workbench.export.xls.add.filterwidth.numchars", 2);
      if (getEnableAutoFilter() && addChars > 0 && writeHeader)
      {
        int charWidth = Settings.getInstance().getIntProperty("workbench.export.xls.defaultcharwidth", 200);
        int filterWidth = (charWidth * addChars);
        LogMgr.logDebug(new CallerInfo(){}, "Increasing column widths by " + filterWidth + " because of auto filter");
        for (int col = 0; col < this.metaData.getColumnCount(); col++)
        {
          int width = sheet.getColumnWidth(col + columnOffset)  + filterWidth;
          sheet.setColumnWidth(col + columnOffset, width);
        }
      }
    }

    FileOutputStream fileOut = null;
    try
    {
      fileOut = new FileOutputStream(getOutputFile());
      workbook.write(fileOut);
      outputSheetName = sheet.getSheetName();
    }
    catch (FileNotFoundException e)
    {
      throw new RuntimeException(e);
    }
    catch (IOException e)
    {
      throw new RuntimeException(e);
    }
    finally
    {
      FileUtil.closeQuietely(fileOut);
      disposeWorkbook();
      workbook = null;
      sheet = null;
      styles.clear();
      headerStyles.clear();
    }

    return null;
  }

  private void disposeWorkbook()
  {
    final CallerInfo ci = new CallerInfo(){};
    try
    {
      workbook.close();
    }
    catch (Throwable ex)
    {
      LogMgr.logWarning(ci, "Could not close workbook", ex);
    }

    try
    {
      if (workbook instanceof SXSSFWorkbook)
      {
        LogMgr.logDebug(ci, "Disposing streaming spreadsheet");
        ((SXSSFWorkbook)workbook).dispose();
      }
    }
    catch (Throwable th)
    {
      LogMgr.logWarning(ci, "Could not dispose SXSSFWorkbook", th);
    }
  }

  private void flushStreamingRows(long currentRow)
  {
    if (sheet instanceof SXSSFSheet)
    {
      int rows = Settings.getInstance().getStreamingPOIRows();
      if (currentRow % rows == 0)
      {
        try
        {
          ((SXSSFSheet)sheet).flushRows(rows);
        }
        catch (Throwable th)
        {
          LogMgr.logWarning(new CallerInfo(){}, "Could not flush streaming API rows", th);
        }
      }
    }
  }

  private void writeInfoSheet()
  {
    Sheet info = workbook.getSheet(INFO_SHEETNAME);

    if (info == null)
    {
      info = workbook.createSheet(INFO_SHEETNAME);
      Row headRow = info.createRow(0);
      Cell cell = headRow.createCell(0);
      cell.setCellStyle(excelFormat.headerCellStyle);
      cell.setCellValue(ResourceMgr.getString("TxtSheet"));

      cell = headRow.createCell(1);
      cell.setCellStyle(excelFormat.headerCellStyle);
      cell.setCellValue("Export date");

      cell = headRow.createCell(2);
      cell.setCellStyle(excelFormat.headerCellStyle);
      cell.setCellValue("JDBC URL");

      cell = headRow.createCell(3);
      cell.setCellStyle(excelFormat.headerCellStyle);
      cell.setCellValue("Database user");

      cell = headRow.createCell(4);
      cell.setCellStyle(excelFormat.headerCellStyle);
      cell.setCellValue("SQL");
    }
    else
    {
      // move the info sheet to the end
      int count = workbook.getNumberOfSheets();
      workbook.setSheetOrder(info.getSheetName(), count - 1);
    }

    if (info instanceof SXSSFSheet)
    {
      ((SXSSFSheet)info).trackAllColumnsForAutoSizing();
    }

    int rowNum = info.getLastRowNum() + 1;

    Row infoRow = info.createRow(rowNum);
    infoRow.setHeight((short)-1);
    Cell name = infoRow.createCell(0);
    CellStyle nameStyle = workbook.createCellStyle();
    nameStyle.setAlignment(HorizontalAlignment.LEFT);
    nameStyle.setVerticalAlignment(VerticalAlignment.TOP);
    nameStyle.setWrapText(false);
    name.setCellValue(sheet.getSheetName());
    name.setCellStyle(nameStyle);
    info.autoSizeColumn(0);

    if (this.originalConnection != null)
    {
      Cell dateCell = infoRow.createCell(1);
      CellStyle dateStyle = workbook.createCellStyle();
      dateStyle.setAlignment(HorizontalAlignment.LEFT);
      dateStyle.setVerticalAlignment(VerticalAlignment.TOP);
      dateStyle.setWrapText(false);
      dateCell.setCellValue(StringUtil.getCurrentTimestampWithTZString());

      Cell urlCell = infoRow.createCell(2);
      CellStyle urlStyle = workbook.createCellStyle();
      urlStyle.setAlignment(HorizontalAlignment.LEFT);
      urlStyle.setVerticalAlignment(VerticalAlignment.TOP);
      urlStyle.setWrapText(false);
      urlCell.setCellValue(this.originalConnection.getUrl());
      urlCell.setCellStyle(urlStyle);

      Cell userCell = infoRow.createCell(3);
      CellStyle userStyle = workbook.createCellStyle();
      userStyle.setAlignment(HorizontalAlignment.LEFT);
      userStyle.setVerticalAlignment(VerticalAlignment.TOP);
      userStyle.setWrapText(false);
      userCell.setCellValue(this.originalConnection.getCurrentUser());
      userCell.setCellStyle(userStyle);

      info.autoSizeColumn(1);
      info.autoSizeColumn(2);
      info.autoSizeColumn(3);
    }

    Cell sqlCell = infoRow.createCell(4);
    CellStyle sqlStyle = workbook.createCellStyle();
    sqlStyle.setAlignment(HorizontalAlignment.LEFT);
    sqlStyle.setVerticalAlignment(VerticalAlignment.TOP);
    sqlStyle.setWrapText(true);

    RichTextString s = workbook.getCreationHelper().createRichTextString(generatingSql.trim());
    sqlCell.setCellValue(s);
    sqlCell.setCellStyle(sqlStyle);
    info.autoSizeColumn(4);
  }

  @Override
  public StringBuilder convertRowData(RowData row, long rowIndex)
  {
    final int count = this.metaData.getColumnCount();
    final int rowNum = (int)rowIndex + firstRow;
    Row myRow = createSheetRow(rowNum);
    int column = 0;
    for (int c = 0; c < count; c++)
    {
      if (includeColumnInExport(c))
      {
        Cell cell = createSheetCell(myRow, column);

        Object value = row.getValue(c);
        boolean multiline = isMultiline(c);
        setCellValueAndStyle(cell, value, false, multiline, c);
        column ++;
      }
    }
    flushStreamingRows(rowIndex);
    return null;
  }

  private boolean isIntegerColumn(int column)
  {
    try
    {
      int type = metaData.getColumnType(column);
      int digits = metaData.getColumn(column).getDecimalDigits();
      return (SqlUtil.isIntegerType(type) || digits <= 0);
    }
    catch (Exception e)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not check data type for column " + column, e);
      return false;
    }
  }

  private boolean applyFormatting()
  {
    return this.targetSheetIndex < 0;
  }

  private boolean applyDateFormat()
  {
    if (this.targetSheetIndex < 0) return true;
    return exporter.dateFormatWasSet();
  }

  private boolean applyTimestampFormat()
  {
    if (this.targetSheetIndex < 0) return true;
    return exporter.timestampFormatWasSet();
  }

  private boolean applyDecimalFormat()
  {
    if (this.targetSheetIndex < 0) return true;
    return exporter.decimalFormatWasSet();
  }

  private void setCellValueAndStyle(Cell cell, Object value, boolean isHead, boolean multiline, int column)
  {
    CellStyle cellStyle = null;

    boolean useFormat = applyFormatting();

    if (value == null)
    {
      // this somewhat duplicates the following code, but the detection based
      // on the actual value class is a bit more accurate than just based
      // on the JDBC datatype, but if a NULL value is passed, then the detection
      // must be done based on the JDBC type
      cellStyle = getBaseStyleForColumn(column, isHead, multiline);
      int type = metaData.getColumnType(column);
      if (type == Types.TIMESTAMP || type == Types.TIMESTAMP_WITH_TIMEZONE)
      {
        useFormat = useFormat || applyTimestampFormat();
      }
      else if (type == Types.DATE)
      {
        useFormat = useFormat || applyDateFormat();
      }
    }
    else if (!isHead && isFormulaColumn(column))
    {
      try
      {
        cell.setCellFormula(value.toString());
        useFormat = false;
      }
      catch (Throwable th)
      {
        LogMgr.logError(new CallerInfo(){}, "Could not set formula \"" + value + "\" for column " + metaData.getColumnName(column), th);
        cell.setCellValue(value.toString());
      }
    }
    else if (value instanceof BigDecimal)
    {
      BigDecimal bd = (BigDecimal)value;

      // this is a workaround for exports using Oracle and NUMBER columns
      // which are essentially integer values. But it shouldn't hurt for other DBMS
      // either in case the driver returns a BigDecimal for "real" integer column
      if (bd.scale() == 0 && isIntegerColumn(column))
      {
        if (bd.precision() > 15)
        {
          // Excel can't handle numbers with more than 15 digits
          cellStyle = excelFormat.textCellStyle;
          cell.setCellValue(bd.toPlainString());
        }
        else
        {
          cellStyle = excelFormat.integerCellStyle;
          cell.setCellValue(bd.doubleValue());
        }
      }
      else
      {
        cellStyle = excelFormat.decimalCellStyle;
        useFormat = useFormat || applyDecimalFormat();
        cell.setCellValue(bd.doubleValue());
      }
    }
    else if (value instanceof Double || value instanceof Float)
    {
      cellStyle = excelFormat.decimalCellStyle;
      cell.setCellValue(((Number)value).doubleValue());
      useFormat = useFormat || applyDecimalFormat();
    }
    else if (value instanceof Number)
    {
      cellStyle = excelFormat.integerCellStyle;
      cell.setCellValue(((Number)value).doubleValue());
      useFormat = useFormat || applyDecimalFormat();
    }
    else if (value instanceof OffsetDateTime)
    {
      OffsetDateTime odt = (OffsetDateTime)value;
      cellStyle = excelFormat.tsCellStyle;
      cell.setCellValue(odt.toLocalDateTime());
      useFormat = useFormat || applyTimestampFormat();
    }
    else if (value instanceof ZonedDateTime)
    {
      ZonedDateTime zdt = (ZonedDateTime)value;
      cellStyle = excelFormat.tsCellStyle;
      cell.setCellValue(zdt.toLocalDateTime());
      useFormat = useFormat || applyTimestampFormat();
    }
    else if (value instanceof LocalDateTime)
    {
      cellStyle = excelFormat.tsCellStyle;
      cell.setCellValue((LocalDateTime)value);
      useFormat = useFormat || applyTimestampFormat();
    }
    else if (value instanceof LocalDate)
    {
      cellStyle = excelFormat.dateCellStyle;
      cell.setCellValue((LocalDate)value);
      useFormat = useFormat || applyDateFormat();
    }
    else if (value instanceof java.sql.Timestamp)
    {
      java.sql.Timestamp ts = (java.sql.Timestamp)value;
      cellStyle = excelFormat.tsCellStyle;
      cell.setCellValue(ts.toLocalDateTime());
      useFormat = useFormat || applyTimestampFormat();
    }
    else if (value instanceof java.util.Date)
    {
      cellStyle = excelFormat.dateCellStyle;
      cell.setCellValue((java.util.Date)value);
      useFormat = useFormat || applyDateFormat();
    }
    else
    {
      RichTextString s = workbook.getCreationHelper().createRichTextString(value.toString());
      cell.setCellValue(s);
      if (multiline)
      {
        cellStyle = excelFormat.multilineCellStyle;
      }
      else
      {
        cellStyle = excelFormat.textCellStyle;
      }
    }

    if (isHead)
    {
      cellStyle = excelFormat.headerCellStyle;
    }

    // do not mess with the formatting if we are writing to an existing sheet
    if (useFormat)
    {
      try
      {
        CellStyle style = geCachedStyle(cellStyle, column, isHead);
        cell.setCellStyle(style);
      }
      catch (IllegalArgumentException iae)
      {
        LogMgr.logWarning(new CallerInfo(){},
          "Could not set style for column: " + metaData.getColumnName(column) +
            ", row: " + cell.getRowIndex() + ", column: " + cell.getColumnIndex());
      }
    }
  }

  private CellStyle getBaseStyleForColumn(int column, boolean isHead, boolean multiline)
  {
    if (isHead)
    {
      return excelFormat.headerCellStyle;
    }

    CellStyle cellStyle = null;
    int type = metaData.getColumnType(column);

    if (SqlUtil.isNumberType(type))
    {
      if (isIntegerColumn(column))
      {
        cellStyle = excelFormat.integerCellStyle;
      }
      else
      {
        cellStyle = excelFormat.decimalCellStyle;
      }
    }
    else if (type == Types.TIMESTAMP)
    {
      cellStyle = excelFormat.tsCellStyle;
    }
    else if (type == Types.DATE)
    {
      cellStyle = excelFormat.dateCellStyle;
    }
    else
    {
      if (multiline)
      {
        cellStyle = excelFormat.multilineCellStyle;
      }
      else
      {
        cellStyle = excelFormat.textCellStyle;
      }
    }
    return cellStyle;
  }

  private CellStyle geCachedStyle(CellStyle baseStyle, int column, boolean isHeader)
  {
    Map<Integer, CellStyle> styleCache = isHeader ? headerStyles : styles;
    CellStyle style = style = styleCache.get(column);
    if (style == null)
    {
      style = workbook.createCellStyle();
      style.cloneStyleFrom(baseStyle);
      styleCache.put(column, style);
    }
    return style;
  }

  public boolean isTemplate()
  {
    return hasOutputFileExtension("xltx");
  }

  private void makeTemplate()
  {
    if (!useXLSX) return;
    POIXMLProperties props = ((XSSFWorkbook)workbook).getProperties();
    POIXMLProperties.ExtendedProperties ext =  props.getExtendedProperties();
    ext.setTemplate("XSSF");
  }
}
