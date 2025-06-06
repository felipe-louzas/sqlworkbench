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
package workbench.sql.wbcommands;

import java.sql.CallableStatement;
import java.sql.ParameterMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import workbench.WbManager;
import workbench.console.ConsolePrompter;
import workbench.interfaces.StatementParameterPrompter;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.ColumnIdentifier;
import workbench.db.DBID;
import workbench.db.DbMetadata;
import workbench.db.JdbcUtils;
import workbench.db.ProcedureDefinition;
import workbench.db.ProcedureReader;
import workbench.db.RoutineType;
import workbench.db.oracle.DbmsOutput;
import workbench.db.oracle.OracleProcedureReader;
import workbench.db.oracle.OracleUtils;

import workbench.gui.preparedstatement.ParameterEditor;

import workbench.storage.DataStore;
import workbench.storage.ResultInfo;
import workbench.storage.reader.CallableStmtResultHolder;
import workbench.storage.reader.RowDataReader;
import workbench.storage.reader.RowDataReaderFactory;

import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;
import workbench.sql.lexer.SQLLexer;
import workbench.sql.lexer.SQLLexerFactory;
import workbench.sql.lexer.SQLToken;
import workbench.sql.preparedstatement.ParameterDefinition;
import workbench.sql.preparedstatement.StatementParameters;

import workbench.util.CollectionUtil;
import workbench.util.ExceptionUtil;
import workbench.util.SqlParsingUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 * Support for running stored procedures that have OUT parameters. For this
 * command to work properly the JDBC driver needs to either implement
 * CallableStatement.getParameterMetaData() correctly, or return proper information
 * about the columns of a procedure using DatabaseMetaData.getProcedureColumns()
 *
 * @author Thomas Kellerer
 */
public class WbCall
  extends SqlCommand
{
  public static final String EXEC_VERB_SHORT = "EXEC";
  public static final String EXEC_VERB_LONG = "EXECUTE";
  public static final String VERB = "WbCall";
  private Map<Integer, ParameterDefinition> refCursor = null;

  // Stores all parameters that need an input
  private final List<ParameterDefinition> inputParameters = new ArrayList<>(5);
  private String sqlUsed = null;

  private StatementParameterPrompter parameterPrompter;

  public WbCall()
  {
    super();
    if (WbManager.getInstance().isConsoleMode())
    {
      parameterPrompter = new ConsolePrompter();
    }
    else
    {
      parameterPrompter = ParameterEditor.GUI_PROMPTER;
    }
  }

  @Override
  public String getVerb()
  {
    return VERB;
  }

  private String getSqlToPrepare(String cleanSql, boolean funcCall)
  {
    if (funcCall) return "{? =  call " + cleanSql + "}";
    return "{call " + cleanSql + "}";
  }

  /**
   * Converts the passed sql to JDBC compliant call and runs the statement.
   */
  @Override
  public StatementRunnerResult execute(String sql)
    throws SQLException, Exception
  {
    StatementRunnerResult result = new StatementRunnerResult(sql);

    SqlParsingUtil util = getParsingUtil();
    String verbUsed = util.getSqlVerb(sql).toUpperCase();
    String cleanSql = util.stripVerb(sql);
    if (OracleUtils.shouldTrimContinuationCharacter(currentConnection))
    {
      cleanSql = OracleUtils.trimSQLPlusLineContinuation(cleanSql);
    }
    sqlUsed = getSqlToPrepare(cleanSql, false);

    this.inputParameters.clear();

    final CallerInfo ci = new CallerInfo(){};
    List<ParameterDefinition> outParameters = null;

    try
    {
      refCursor = null;

      CallableStatement cstmt = currentConnection.getSqlConnection().prepareCall(sqlUsed);
      this.currentStatement = cstmt;

      boolean hasParameters = (sqlUsed.indexOf('?') > -1);
      boolean namesAvailable = false;

      Savepoint sp = null;
      if (hasParameters)
      {
        try
        {
          if (currentConnection.getDbSettings().useSavePointForDML())
          {
            sp = currentConnection.setSavepoint();
          }
          outParameters = checkParametersFromStatement(cstmt);

          // The JDBC ParameterMetaData class does not expose parameter names
          // so they cannot be displayed in the dialog

          // TODO: another call to retrieve the parameter names through JDBC
          // in order to be able to display them, even if ParameterMetaData is used.
          namesAvailable = false;
          currentConnection.releaseSavepoint(sp);
        }
        catch (Throwable e)
        {
          // Some drivers do not work properly if this happens, so
          // we have to close and re-open the statement
          LogMgr.logWarning(ci, "Could not get parameters from statement!", e);
          JdbcUtils.closeStatement(cstmt);
          currentConnection.rollback(sp);
        }
        finally
        {
          sp = null;
        }
      }

      // only check for out parameters when using the (documented) WbCall syntax
      // when using EXEC without parameters this saves a (costly - especially for Oracle) roundtrip to the database.
      if (CollectionUtil.isEmpty(outParameters) && verbUsed.equals("WBCALL"))
      {
        try
        {
          if (currentConnection.getDbSettings().useSavePointForDML())
          {
            sp = currentConnection.setSavepoint();
          }

          outParameters = checkParametersFromDatabase(cleanSql);

          // When retrieving the actual procedure parameters we do have
          // the parameter names available, so we can show them in the dialog
          namesAvailable = true;

          // checkParametersFromDatabase will re-create the callable statement
          // and assign it to currentStatement
          // This is necessary to avoid having two statements open on the same
          // connection as some jdbc drivers do not like this
          if (this.currentStatement != null)
          {
            cstmt = (CallableStatement)currentStatement;
          }

          currentConnection.releaseSavepoint(sp);
        }
        catch (Throwable e)
        {
          LogMgr.logError(ci, "Error during procedure check", e);
          currentConnection.rollback(sp);
        }
        finally
        {
          sp = null;
        }
      }

      if (hasParameters && this.inputParameters.size() > 0 && this.parameterPrompter != null)
      {
        StatementParameters input = new StatementParameters(this.inputParameters);
        boolean ok = parameterPrompter.showParameterDialog(input, namesAvailable);
        if (!ok)
        {
          result.addErrorMessageByKey("MsgStatementCancelled");
          return result;
        }

        for (ParameterDefinition paramDefinition : this.inputParameters)
        {
          int type = paramDefinition.getType();
          int index = paramDefinition.getIndex();
          Object value = paramDefinition.getValue();
          cstmt.setObject(index, value, type);
        }
      }

      int fetchSize = currentConnection.getFetchSize();
      if (fetchSize > 0 && cstmt != null)
      {
        cstmt.setFetchSize(fetchSize);
      }
      boolean hasResult = (cstmt != null ? cstmt.execute() : false);
      result.setSuccess();

      if (refCursor != null)
      {
        for (Map.Entry<Integer, ParameterDefinition> refs : refCursor.entrySet())
        {
          try
          {
            ResultSet rs = (ResultSet)cstmt.getObject(refs.getKey().intValue());

            // processResults will close the result set
            if (rs != null) processResults(result, true, rs);
            List<DataStore> results = result.getDataStores();
            if (CollectionUtil.isNonEmpty(results) && refs.getValue() != null)
            {
              DataStore ds = results.get(results.size() - 1);
              ds.setGeneratingSql(sql);
              if (ds.getResultName() == null)
              {
                String name = refs.getValue().getParameterName();
                if (StringUtil.isNotBlank(name))
                {
                  ds.setResultName(name);
                }
              }
            }
          }
          catch (Exception e)
          {
            result.addMessage(ExceptionUtil.getDisplay(e));
          }
        }
      }
      else
      {
        processResults(result, hasResult);
      }

      // Now process all single-value out parameters
      if (CollectionUtil.isNonEmpty(outParameters))
      {
        String[] cols = new String[]{"PARAMETER", "VALUE"};
        int[] types = new int[]{Types.VARCHAR, Types.VARCHAR};
        int[] sizes = new int[]{35, 35};

        DataStore resultData = new DataStore(cols, types, sizes);
        ParameterDefinition.sortByIndex(outParameters);

        RowDataReader reader = createReader(outParameters);
        CallableStmtResultHolder data = new CallableStmtResultHolder(cstmt);


        for (ParameterDefinition def : outParameters)
        {
          if (refCursor != null && refCursor.containsKey(Integer.valueOf(def.getIndex()))) continue;

          Object parmValue = cstmt.getObject(def.getIndex());
          if (parmValue instanceof ResultSet)
          {
            processResults(result, true, (ResultSet)parmValue);
          }
          else
          {
            LogMgr.logDebug(ci, "Reading parameter=" + def.getParameterName() +
              ", mode=" + def.getModeString() +
              ", index=" + def.getIndex() +
              ", type=" + SqlUtil.getTypeName(def.getType()) + " (" + def.getType() + ")");

            Object value = reader.readColumnData(data, def.getType(), def.getIndex(), true);
            int row = resultData.addRow();
            resultData.setValue(row, 0, def.getParameterName());
            resultData.setValue(row, 1, value == null ? "NULL" : value);
          }
        }
        resultData.resetStatus();
        result.addDataStore(resultData);
      }
    }
    catch (Exception e)
    {
      // SQL Server seems to return results from a stored procedure even if an error occurred.
      processResults(result, false);
      LogMgr.logError(new CallerInfo(){}, "Error calling stored procedure using: " + sqlUsed, e);
      result.addMessageByKey("MsgExecuteError");
      result.addErrorMessage(ExceptionUtil.getDisplay(e));
    }
    finally
    {
      done();
    }

    if (result.isSuccess())
    {
      LogMgr.logDebug(new CallerInfo(){}, "Converted procedure call to JDBC syntax: " + sqlUsed);
      String procname = null;
      SQLLexer l = SQLLexerFactory.createLexer(currentConnection, cleanSql);
      SQLToken t = l.getNextToken(false, false);
      if (t != null)
      {
        procname = t.getText();
      }
      result.addMessageByKey("MsgKnownStatementOK", procname);
    }

    return result;
  }

  private RowDataReader createReader(List<ParameterDefinition> parameters)
  {
    ColumnIdentifier[] cols = new ColumnIdentifier[parameters.size()];
    for (int i=0; i < parameters.size(); i++)
    {
      ParameterDefinition def = parameters.get(i);
      ColumnIdentifier col = new ColumnIdentifier(def.getParameterName(), def.getType());
      cols[i] = col;
    }
    ResultInfo info = new ResultInfo(cols);
    return RowDataReaderFactory.createReader(info, currentConnection);
  }

  @Override
  protected void appendOutput(StatementRunnerResult result)
  {
    super.appendOutput(result);

    // when calling a stored procedure always retrieve DBMS_OUTPUT messages
    // regardless if "set serveroutput" was called or not
    if (retrieveDbmsOutput())
    {
      try
      {
        DbmsOutput output = new DbmsOutput(currentConnection.getSqlConnection());
        String msg = output.retrieveOutput();
        if (StringUtil.isNotEmpty(msg))
        {
          result.addMessage(msg);
        }
      }
      catch (Exception ex)
      {
      }
    }
  }

  private boolean retrieveDbmsOutput()
  {
    if (!DBID.Oracle.isDB(currentConnection)) return false;
    if (currentConnection.getMetadata().isDbmsOutputEnabled()) return false;
    return Settings.getInstance().retrieveDbmsOutputAfterExec();
  }

  @Override
  public void done()
  {
    super.done();
    if (this.refCursor != null) this.refCursor.clear();
    this.refCursor = null;
    this.inputParameters.clear();
  }

  private List<ParameterDefinition> checkParametersFromStatement(CallableStatement cstmt)
    throws SQLException
  {
    if (!currentConnection.getDbSettings().supportsParameterMetaDataForCallableStatement())
    {
      return null;
    }

    ArrayList<ParameterDefinition> parameterNames = null;

    ParameterMetaData parmData = cstmt.getParameterMetaData();
    if (parmData != null)
    {
      parameterNames = new ArrayList<>();

      for (int i = 0; i < parmData.getParameterCount(); i++)
      {
        int mode = parmData.getParameterMode(i + 1);
        int type = parmData.getParameterType(i + 1);

        ParameterDefinition def = new ParameterDefinition(i + 1, type);
        def.setParameterMode(mode);

        if (mode == ParameterMetaData.parameterModeOut || mode == ParameterMetaData.parameterModeInOut)
        {
          cstmt.registerOutParameter(i + 1, type);
          parameterNames.add(def);
        }

        if (mode == ParameterMetaData.parameterModeIn || mode == ParameterMetaData.parameterModeInOut)
        {
          inputParameters.add(def);
        }
      }
    }

    return parameterNames;
  }

  private List<ParameterDefinition> checkParametersFromDatabase(String sql)
    throws SQLException
  {

    List<String> sqlParams = SqlUtil.getFunctionParameters(sql);

    DbMetadata meta = this.currentConnection.getMetadata();

    // Detect the name/schema of the called procedure
    SQLLexer l = SQLLexerFactory.createLexer(currentConnection, sql);

    // The WbCall verb has already been removed from the sql string
    // so the first token is the actual procedure name (but could contain a package and/or schema name)
    SQLToken t = l.getNextToken(false, false);

    String schema = null;
    String catalog = null;
    String procname = (t == null ? "" : t.getContents());
    if (procname == null) return null;

    String[] items = procname.split("\\.");
    if (DBID.Oracle.isDB(currentConnection))
    {
      if (items.length == 3)
      {
        // Packaged procedure with a schema prefix
        schema = items[0];
        catalog = items[1].toUpperCase();
        procname = items[2]; // package name
      }
      if (items.length == 2)
      {
        procname = items[1];

        // this can either be a packaged procedure or a standalone procedure with a schema prefix
        // if the first item is a valid schema name, then it's not a packaged function
        // this will not cover the situation where a package for the current user exists
        // that has the same name as a schema
        List<String> schemas = currentConnection.getMetadata().getSchemas();
        if (schemas.contains(items[0].toUpperCase()))
        {
          schema = items[0];
        }
        else
        {
          schema = null;
          catalog = items[0].toUpperCase(); // package name
        }
      }

      // Now resolve possible public Synonyms
      OracleProcedureReader reader = (OracleProcedureReader)currentConnection.getMetadata().getProcedureReader();
      ProcedureDefinition def = reader.resolveSynonym(catalog, schema, procname);
      if (def != null)
      {
        schema = def.getSchema();
        catalog = def.getCatalog();
      }
    }
    else
    {
      if (items.length == 2)
      {
        schema = items[0];
        procname = items[1];
      }
    }

    ArrayList<ParameterDefinition> parameterNames = null;

    String schemaToUse = SqlUtil.removeObjectQuotes(meta.adjustSchemaNameCase(schema));
    if (schemaToUse == null)
    {
      schemaToUse = meta.getCurrentSchema();
    }
    String nameToUse = SqlUtil.removeObjectQuotes(meta.adjustObjectnameCase(procname));

    ProcedureDefinition procDef = null;
    DataStore params = null;

    List<ProcedureDefinition> procs = meta.getProcedureReader().getProcedureList(catalog, schemaToUse, nameToUse);

    if (procs.size() == 1)
    {
      procDef = procs.get(0);
    }
    else if (procs.size() > 1)
    {
      List<DataStore> procParams = new ArrayList<>(procs.size());

      // if more than one procedure was found this could be an overloaded one
      // so loop through all definitions and compare the number of parameters
      for (ProcedureDefinition proc : procs)
      {
        params = meta.getProcedureReader().getProcedureColumns(proc);

        // temporarily store the retrieved parameters, in order to avoid a second retrieval
        // for the first procedure in case none matched
        procParams.add(params);

        int paramCount = params.getRowCount();

        // if a function is found, it will contain at least one parameter for the return value
        // the "return parameter" should not be considered when checking comparing the procedure/function
        // parameters against the number of parameters supplied by the user
        if (proc.isFunction())
        {
          paramCount --;
        }

        if (paramCount == sqlParams.size())
        {
          procDef = proc;
          break;
        }
      }

      if (procDef == null)
      {
        // Fallback in case nothing matched
        procDef = procs.get(0);
        params = procParams.get(0);
      }
    }
    else
    {
      procDef = new ProcedureDefinition(catalog, schemaToUse, nameToUse, RoutineType.procedure);
    }

    if (params == null)
    {
      params = meta.getProcedureReader().getProcedureColumns(procDef);
    }

    int parameterIndexOffset = 0;
    boolean needFuncCall = ProcedureDefinition.returnsRefCursor(currentConnection, params);
    if (!needFuncCall && isFunction(procDef, params))
    {
      needFuncCall = true;
      parameterIndexOffset = 1;
    }

    sqlUsed = getSqlToPrepare(sql, needFuncCall);
    boolean isOracle = DBID.Oracle.isDB(currentConnection);
    if (isOracle && !needFuncCall && !hasPlaceHolder(sqlParams))
    {
      // Workaround for Oracle packages that define optional OUT parameters.
      // If no ? is specified, and this is not a function call, there is no need
      // to retrieve any possible OUT parameter.
      return null;
    }

    if (currentStatement != null)
    {
      JdbcUtils.closeStatement(currentStatement);
    }

    CallableStatement cstmt = currentConnection.getSqlConnection().prepareCall(sqlUsed);
    this.currentStatement = cstmt;

    int definedParamCount = params.getRowCount();

    if (isOracle && definedParamCount != sqlParams.size() && !needFuncCall)
    {
      // if not all parameters are specified, and this is not a function returning a refCursor
      // there is no way to find the correct parameters or register them
      return null;
    }

    if (definedParamCount != sqlParams.size())
    {
      // if this is a function call (using {? = call()} then an additional
      // Parameter is needed which is not part of the provided parameters
      if (procDef.isFunction() && definedParamCount -1 != sqlParams.size())
      {
        sqlParams = null;
      }
    }

    inputParameters.clear();

    if (definedParamCount > 0)
    {
      int realParamIndex = 1 + parameterIndexOffset;
      int inputIndex = 0;

      parameterNames = new ArrayList<>(definedParamCount);
      for (int i = 0; i < definedParamCount; i++)
      {
        int dataType = params.getValueAsInt(i, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_JDBC_DATA_TYPE, -1);

        String typeName = params.getValueAsString(i, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_DATA_TYPE);
        String resultType = params.getValueAsString(i, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_RESULT_TYPE);
        String paramName = params.getValueAsString(i, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_COL_NAME);

        int indexToUse = realParamIndex;
        if (resultType.equals("RETURN"))
        {
          // the result is always the first parameter because of the {? = call(?,?)} syntax
          indexToUse = 1;
        }
        ParameterDefinition def = new ParameterDefinition(indexToUse, dataType);
        def.setParameterName(paramName == null ? resultType : paramName);

        boolean needsInput = resultType.equals("IN");
        boolean isRefCursorParam = ProcedureDefinition.isRefCursor(currentConnection, typeName);

        if (resultType.equals("INOUT"))
        {
          // an INOUT paramter needs to be presented in the variables dialog only if it's not a ref cursor
          // (a ref cursor cannot be entered by the user)
          needsInput = !isRefCursorParam;
        }

        // Only real input parameters need to be added to the dialog
        if (needsInput)
        {
          if (sqlParams != null)
          {
            // only add the parameter as an input parameter
            // if a place holder was specified
            if (sqlParams.get(inputIndex).equals("?"))
            {
              inputParameters.add(def);
              if (resultType.equals("INOUT"))
              {
                // INOUT paramters need to ber registered as well to get the returned value
                parameterNames.add(def);
                cstmt.registerOutParameter(realParamIndex, dataType);
              }
              realParamIndex ++;
            }
          }
          else
          {
            // for some reason parsing the argument list did
            // not give the correct numbers. We cannot know if
            // the parameter was supplied or not.
            inputParameters.add(def);
            realParamIndex ++;
          }
        }
        else if (resultType.endsWith("OUT") || (needFuncCall && StringUtil.equalString(resultType, "RETURN")))
        {
          if (isRefCursorParam)
          {
            // these parameters should not be added to the regular parameter list
            // as they have to be retrieved in a different manner.
            // type == -10 is Oracles CURSOR Datatype
            int dbmsTypeOverride = currentConnection.getDbSettings().getRefCursorDataType();
            if (dbmsTypeOverride != Integer.MIN_VALUE) dataType = dbmsTypeOverride;
            if (refCursor == null)
            {
              refCursor = new HashMap<>();
            }
            refCursor.put(Integer.valueOf(realParamIndex), def);
          }
          else
          {
            parameterNames.add(def);
          }

          if (needFuncCall && StringUtil.equalString(resultType, "RETURN") && parameterIndexOffset == 1)
          {
            cstmt.registerOutParameter(parameterIndexOffset, dataType);
          }
          else
          {
            cstmt.registerOutParameter(realParamIndex, dataType);
            realParamIndex++;
          }
        }

        if (!resultType.equals("RETURN"))
        {
          inputIndex ++;
        }
      }
    }
    return parameterNames;
  }

  private boolean isFunction(ProcedureDefinition def, DataStore params)
  {
    if (DBID.Postgres.isDB(currentConnection))
    {
      // for Postgres only functions that do _really_ return something
      // should be prepared with the "{? = call function_name(...)}" syntax
      // in all other cases the return value has to be treated like an OUT parameter
      for (int row=0; row < params.getRowCount(); row++)
      {
        // this check relies on PostgresProcedureReader using our own statement.
        // see: PostgresProcedureReader.getColumns()
        String paramType = params.getValueAsString(row, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_RESULT_TYPE);
        if ("RETURN".equalsIgnoreCase(paramType))
        {
          return true;
        }
      }
      return false;
    }
    else
    {
      return ProcedureDefinition.isFunction(def, params);
    }
  }

  private boolean hasPlaceHolder(List<String> params)
  {
    if (CollectionUtil.isEmpty(params)) return false;
    for (String p : params)
    {
      if (p.indexOf('?') > -1) return true;
    }
    return false;
  }

  /**
   * For testing purposes
   * @param prompter
   */
  public void setParameterPrompter(StatementParameterPrompter prompter)
  {
    parameterPrompter = prompter;
  }

  /**
   * For testing purposes
   */
  public String getSqlUsed()
  {
    return sqlUsed;
  }

  @Override
  public boolean isWbCommand()
  {
    return true;
  }

}
