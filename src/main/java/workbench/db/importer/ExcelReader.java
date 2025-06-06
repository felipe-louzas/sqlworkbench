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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.Set;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.util.CollectionUtil;
import workbench.util.DurationFormatter;
import workbench.util.FileUtil;
import workbench.util.MessageBuffer;
import workbench.util.StringUtil;
import workbench.util.WbFile;

import org.apache.poi.hssf.usermodel.HSSFFormulaEvaluator;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.openxml4j.util.ZipInputStreamZipEntrySource;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.usermodel.XSSFFormulaEvaluator;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openxmlformats.schemas.spreadsheetml.x2006.main.CTSheet;

/**
 *
 * @author Thomas Kellerer
 */
public class ExcelReader
  implements SpreadsheetReader
{
  /**
   * Amount of milliseconds in a day.
   */
  private static final long ONE_DAY = (24L * DurationFormatter.ONE_HOUR);

  private int sheetIndex = -1;
  private String sheetName;

  private final WbFile inputFile;
  private final List<String> sheetNames = new ArrayList<>();

  private Workbook workbook;
  private Sheet dataSheet;
  private List<String> headerColumns;
  private String nullString;
  private List<CellRangeAddress> mergedRegions;
  private final Set<String> tsFormats = CollectionUtil.treeSet("HH", "mm", "ss", "SSS", "KK", "kk");

  private final boolean useXLSX;
  private final MessageBuffer messages = new MessageBuffer();
  private boolean emptyStringIsNull;
  private boolean useStringDates;
  private boolean useStringNumbers;
  private final DataFormatter dataFormatter = new DataFormatter(true);
  private boolean recalcOnLoad = true;
  private boolean useSAXReader;
  private boolean checkNumericFormat = true;

  public ExcelReader(File excelFile, int sheetNumber, String name)
  {
    inputFile = new WbFile(excelFile);
    sheetIndex = sheetNumber > -1 ? sheetNumber : -1;
    if (sheetIndex < 0 && StringUtil.isNotBlank(name))
    {
      sheetName = name.trim();
    }
    else
    {
      sheetName = null;
    }
    useXLSX = inputFile.getExtension().equalsIgnoreCase("xlsx");
    useSAXReader = useXLSX && Settings.getInstance().getUseXLSXSaxReader();
    checkNumericFormat = Settings.getInstance().getUseFormattedExcelNumbers();
  }

  @Override
  public void enableRecalcOnLoad(boolean flag)
  {
    this.recalcOnLoad = flag;
    if (recalcOnLoad && useSAXReader)
    {
      LogMgr.logDebug(new CallerInfo(){}, "Disabling streaming SAX reader to support recalculation of formulas.");
      useSAXReader = false;
    }
  }

  @Override
  public void setReturnNumbersAsString(boolean flag)
  {
    useStringNumbers = flag;
  }

  @Override
  public void setReturnDatesAsString(boolean flag)
  {
    useStringDates = flag;
  }

  @Override
  public void setEmptyStringIsNull(boolean flag)
  {
    emptyStringIsNull = flag;
  }

  @Override
  public MessageBuffer getMessages()
  {
    return messages;
  }

  @Override
  public List<String> getSheets()
  {
    if (useXLSX)
    {
      if (sheetNames.isEmpty())
      {
        readSheetNamesFromIterator();
      }
    }
    else
    {
      readSheetNamesFromWorkbook();
    }
    return Collections.unmodifiableList(sheetNames);
  }

  private void readSheetNamesFromWorkbook()
  {
    if (workbook == null)
    {
      try
      {
        load();
      }
      catch (Exception io)
      {
        LogMgr.logError(new CallerInfo(){}, "Could not load Excel file: " + inputFile.getFullPath(), io);
      }
    }

    sheetNames.clear();
    int count = workbook.getNumberOfSheets();
    for (int i=0; i < count; i++)
    {
      sheetNames.add(workbook.getSheetName(i));
    }
  }

  private void readSheetNamesFromIterator()
  {
    OPCPackage xlsxPackage = null;
    try
    {
      sheetNames.clear();
      xlsxPackage = OPCPackage.open(inputFile, PackageAccess.READ);
      XSSFReader reader = new XSSFReader(xlsxPackage);
      XSSFReader.SheetIterator iter = (XSSFReader.SheetIterator)reader.getSheetsData();
      while (iter.hasNext())
      {
        iter.next();
        sheetNames.add(iter.getSheetName());
      }
    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not load Excel file", ex);
    }
    finally
    {
      FileUtil.closeQuietely(xlsxPackage);
    }
  }


  private boolean shouldLoadSheet()
  {
    if (dataSheet != null)
    {
      if (sheetIndex > -1 && dataSheet.getSheetName().equals(sheetNames.get(sheetIndex)))
      {
        return false;
      }
      else if (sheetName != null && sheetName.equals(dataSheet.getSheetName()))
      {
        return false;
      }
    }
    return sheetName != null || sheetIndex > -1;
  }

  /**
   * This applies memory thresholds for loading Excel files.
   *
   * @see IOUtils#setByteArrayMaxOverride(int)
   * @see ZipInputStreamZipEntrySource#setThresholdBytesForTempFiles(int)
   */
  private void setMemoryThresholds()
  {
    int maxArraySize = Settings.getInstance().getIntProperty("workbench.import.xlsx.zipstream.maxsize", -1);
    try
    {
      IOUtils.setByteArrayMaxOverride(maxArraySize);
    }
    catch (Throwable th)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not set byteArrayMaxOverride to " + maxArraySize, th);
    }
    int tempFileThreshold = Settings.getInstance().getIntProperty("workbench.import.xlsx.tempfile.threshold", -1);
    try
    {
      ZipInputStreamZipEntrySource.setThresholdBytesForTempFiles(tempFileThreshold);
    }
    catch (Throwable th)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not set thresholdBytesForTempFiles to " + tempFileThreshold, th);
    }
  }

  @Override
  public void load()
    throws IOException
  {
    setMemoryThresholds();

    if (useSAXReader)
    {
      if (sheetNames.isEmpty())
      {
        readSheetNamesFromIterator();
      }
      if (shouldLoadSheet())
      {
        initActiveSheet();
      }
      return;
    }

    if (workbook != null)
    {
      // do not load the file twice.
      return;
    }

    InputStream in = null;
    try
    {
      in = new FileInputStream(inputFile);
      if (useXLSX)
      {
        workbook = new XSSFWorkbook(in);
      }
      else
      {
        workbook = new HSSFWorkbook(in);
      }
    }
    finally
    {
      FileUtil.closeQuietely(in);
    }

    initActiveSheet();

    // TODO: find references to external files and update those as well
    // see: https://poi.apache.org/spreadsheet/eval.html
    try
    {
      if (recalcOnLoad)
      {
        if (useXLSX)
        {
          XSSFFormulaEvaluator.evaluateAllFormulaCells((XSSFWorkbook)workbook);
        }
        else
        {
          HSSFFormulaEvaluator.evaluateAllFormulaCells((HSSFWorkbook)workbook);
        }
      }
    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not refresh formulas!", ex);
    }
  }

  private void loadSheet(String toLoad)
  {
    XSSFWorkbook xssfWorkbook = null;
    try (InputStream in = new FileInputStream(inputFile))
    {
      xssfWorkbook = new XSSFWorkbook(in)
      {
        @Override
        public void parseSheet(Map<String, XSSFSheet> shIdMap, CTSheet ctSheet)
        {
          if (toLoad.equals(ctSheet.getName()))
          {
            super.parseSheet(shIdMap, ctSheet);
          }
        }
      };
      this.dataSheet = xssfWorkbook.getSheet(toLoad);
    }
    catch (Exception io)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not load sheet " + toLoad + " for file " + inputFile, io);
    }
    finally
    {
      FileUtil.closeQuietely(xssfWorkbook);
    }
  }

  private void initActiveSheet()
  {
    if (useSAXReader)
    {
      if (sheetIndex > -1)
      {
        loadSheet(sheetNames.get(sheetIndex));
      }
      else
      {
        loadSheet(sheetName);
      }
    }
    else if (workbook != null)
    {
      if (sheetIndex > -1)
      {
        dataSheet = workbook.getSheetAt(sheetIndex);
        if (dataSheet == null)
        {
          throw new IndexOutOfBoundsException("Sheet with index " + sheetIndex + " does not exist in file: " + inputFile.getFullPath());
        }
      }
      else if (sheetName != null)
      {
        dataSheet = workbook.getSheet(sheetName);
        if (dataSheet == null)
        {
          throw new IllegalArgumentException("Sheet with name '" + sheetName + "' does not exist in file: " + inputFile.getFullPath());
        }
      }
      else
      {
        int index = workbook.getActiveSheetIndex();
        dataSheet = workbook.getSheetAt(index);
      }
    }

    if (dataSheet != null)
    {
      headerColumns = null;
      int numMergedRegions = dataSheet.getNumMergedRegions();
      mergedRegions = new ArrayList<>(numMergedRegions);
      for (int i = 0; i < numMergedRegions; i++)
      {
        mergedRegions.add(dataSheet.getMergedRegion(i));
      }
    }
  }

  @Override
  public List<String> getHeaderColumns()
  {
    if (this.dataSheet == null)
    {
      initActiveSheet();
    }

    if (headerColumns == null)
    {
      headerColumns = new ArrayList<>();
      Row row = dataSheet.getRow(0);

      int colCount = row != null ? row.getLastCellNum() : 0;

      if (row == null || colCount == 0)
      {
        LogMgr.logError(new CallerInfo(){}, "Cannot retrieve column names because no data is available in the first row of the sheet: " + dataSheet.getSheetName(), null);
        String msg = ResourceMgr.getFormattedString("ErrExportNoCols", dataSheet.getSheetName());
        messages.append(msg);
        messages.appendNewLine();
        return headerColumns;
      }

      for (int i=0; i < colCount; i++)
      {
        Cell cell = row.getCell(i);
        Object value = getCellValue(cell);

        if (value != null)
        {
          headerColumns.add(value.toString());
        }
        else
        {
          headerColumns.add("Col" + Integer.toString(i));
        }
      }
    }
    return headerColumns;
  }

  @Override
  public void setActiveWorksheet(String name)
  {
    if (StringUtil.isNotBlank(name) && !StringUtil.equalStringIgnoreCase(name, sheetName))
    {
      this.sheetName = name;
      this.sheetIndex = -1;
      initActiveSheet();
    }
  }

  @Override
  public void setActiveWorksheet(int index)
  {
    if (index > -1 && index != sheetIndex)
    {
      sheetIndex = index;
      sheetName = null;
      initActiveSheet();
    }
  }

  private boolean isTimestampFormat(String format)
  {
    for (String key : tsFormats)
    {
      if (format.contains(key)) return true;
    }
    return false;
  }

  /**
   * This is a copy of the POI function DateUtil.getJavaDate().
   *
   * The POI function does not consider hours, minutes and seconds, which means
   * that columns with date <b>and</b> time are not retrieved correctly from an Excel file.
   *
   * @param date the "Excel" date
   * @return a properly initialized Java Date
   */
  private Date getJavaDate(double date)
  {
    int wholeDays = (int) Math.floor(date);
    int millisecondsInDay = (int)((date - wholeDays) * ONE_DAY + 0.5);
    Calendar calendar = new GregorianCalendar(); // using default time-zone

    int startYear = 1900;
    int dayAdjust = -1; // Excel thinks 2/29/1900 is a valid date, which it isn't
    if (wholeDays < 61)
    {
      // Date is prior to 3/1/1900, so adjust because Excel thinks 2/29/1900 exists
      // If Excel date == 2/29/1900, will become 3/1/1900 in Java representation
      dayAdjust = 0;
    }

    int hours = (int)(millisecondsInDay / DurationFormatter.ONE_HOUR);
    millisecondsInDay -= (hours * DurationFormatter.ONE_HOUR);
    int minutes = (int)(millisecondsInDay / DurationFormatter.ONE_MINUTE);
    millisecondsInDay -= (minutes * DurationFormatter.ONE_MINUTE);
    int seconds = (int)Math.floor(millisecondsInDay / DurationFormatter.ONE_SECOND);
    millisecondsInDay -= (seconds * DurationFormatter.ONE_SECOND);
    calendar.set(startYear, 0, wholeDays + dayAdjust, hours, minutes, seconds);
    calendar.set(GregorianCalendar.MILLISECOND, millisecondsInDay);
    return calendar.getTime();
  }

  private boolean isMerged(Cell cell)
  {
    if (cell == null) return false;
    for (CellRangeAddress range : mergedRegions)
    {
      if (range.isInRange(cell.getRowIndex(), cell.getColumnIndex())) return true;
    }
    return false;
  }

  @Override
  public List<Object> getRowValues(int rowIndex)
  {
    Row row = dataSheet.getRow(rowIndex);
    ArrayList<Object> values = new ArrayList<>();

    if (row == null) return values;

    int nullCount = 0;
    int colCount = row.getLastCellNum();

    for (int col=0; col < colCount; col++)
    {
      Cell cell = row.getCell(col);

      // treat rows with merged cells as "empty"
      if (isMerged(cell))
      {
        LogMgr.logDebug(new CallerInfo(){}, dataSheet.getSheetName() + ": column:" + cell.getColumnIndex() + ", row:" + cell.getRowIndex() + " is merged. Ignoring row!");
        return Collections.emptyList();
      }

      Object value = getCellValue(cell);

      if (value == null)
      {
        nullCount ++;
      }
      values.add(value);
    }

    if (nullCount == values.size())
    {
      // return an empty list if all columns are null
      values.clear();
    }

    return values;
  }

  private Object getCellValue(Cell cell)
  {
    if (cell == null) return null;
    CellType type = cell.getCellType();
    boolean isFormula = false;
    if (type == CellType.FORMULA)
    {
      isFormula = true;
      type = cell.getCachedFormulaResultType();
    }

    Object value;

    switch (type)
    {
      case BLANK:
      case ERROR:
        value = null;
        break;
      case NUMERIC:
        boolean isDate = DateUtil.isCellDateFormatted(cell);
        CellStyle style = cell.getCellStyle();
        String fmt = style != null ? style.getDataFormatString() : "";

        if (isDate)
        {
          if (!isFormula && useStringDates)
          {
            value = dataFormatter.formatCellValue(cell);
          }
          else
          {
            value = getDateValue(cell);
          }
        }
        else if (!isFormula && (useStringNumbers || (checkNumericFormat && isTextFormat(fmt))))
        {
          value = dataFormatter.formatCellValue(cell);
        }
        else
        {
          value = cell.getNumericCellValue();
        }
        break;
      default:
        String svalue = cell.getStringCellValue();
        if (isNullString(svalue))
        {
          value = null;
        }
        else
        {
          value = svalue;
        }
    }
    return value;
  }

  private boolean isTextFormat(String fmt)
  {
    if (fmt == null) return false;
    return "@".equals(fmt) || "text".equals(fmt);
  }

  private java.util.Date getDateValue(Cell cell)
  {
    java.util.Date dtValue = null;
    try
    {
      dtValue = cell.getDateCellValue();
    }
    catch (Exception ex)
    {
      // ignore
    }
    String fmt = cell.getCellStyle().getDataFormatString();
    double dv = cell.getNumericCellValue();
    if (dtValue == null)
    {
      dtValue = getJavaDate(dv);
    }

    if (dtValue != null)
    {
      if (isTimestampFormat(fmt))
      {
        return new java.sql.Timestamp(dtValue.getTime());
      }
      else
      {
        return new java.sql.Date(dtValue.getTime());
      }
    }
    return null;
  }

  private boolean isNullString(String value)
  {
    if (value == null) return true;
    if (emptyStringIsNull && StringUtil.isEmpty(value)) return true;
    return StringUtil.equalString(value, nullString);
  }

  @Override
  public void setNullString(String nullString)
  {
    this.nullString = nullString;
  }

  @Override
  public int getRowCount()
  {
    return dataSheet.getLastRowNum() + 1;
  }

  @Override
  public void done()
  {
    dataSheet = null;
    workbook = null;
  }

}
