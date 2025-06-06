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
package workbench.db;

import java.io.Serializable;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.objectcache.DbObjectCacheFactory;

import workbench.storage.DataStore;

import workbench.sql.wbcommands.CommandTester;
import workbench.sql.wbcommands.WbCall;

import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class ProcedureDefinition
  implements DbObject, Serializable
{
  private static final long serialVersionUID = DbObjectCacheFactory.CACHE_VERSION_UID;

  private String schema;
  private String catalog;
  private String procName;
  private String comment;
  private String displayName;

  private Object internalIdentifier;

  /**
   * The result type as returned by the JDBC driver.
   * Corresponds to:
   * <ul>
   *   <li>DatabaseMetaData.procedureNoResult</li>
   *   <li>DatabaseMetaData.procedureReturnsResult</li>
   *   <li>DatabaseMetaData.functionReturnsTable</li>
   *   <li>DatabaseMetaData.functionNoTable</li>
   * </ul>
   */
  private final int resultType;

  private ProcType procType;
  private String oracleOverloadIndex;

  private CharSequence source;
  private List<ColumnIdentifier> parameters;

  // The JDBC values  procedureReturnsResult or functionReturnsTable
  // have overlapping values, so the resultType is not enought
  // to detect if this is a procedure, function or table function
  private RoutineType routineType = RoutineType.unknown;

  /**
   * A DBMS specific name for the type of this procedure/function.
   *
   * Currently used to identify Postgres custom aggregate functions.
   */
  private String dbmsProcType;
  private String specificName;


  public ProcedureDefinition(String name, RoutineType rType)
  {
    procName = name;
    routineType = rType;
    resultType = routineType == RoutineType.function ? DatabaseMetaData.procedureReturnsResult : DatabaseMetaData.procedureNoResult;
  }

  public ProcedureDefinition(String name, RoutineType rType, int jdbcResultType)
    {
    procName = name;
    resultType = jdbcResultType;
    routineType = rType;
  }

  public ProcedureDefinition(String cat, String schem, String name, RoutineType rType, int jdbcResultType)
  {
    schema = schem;
    catalog = cat;
    procName = name;
    resultType = jdbcResultType;
    routineType = rType;
  }

  public ProcedureDefinition(String cat, String schem, String name, RoutineType type)
  {
    schema = schem;
    catalog = cat;
    procName = name;
    routineType = type;
    resultType = routineType == RoutineType.function ? DatabaseMetaData.procedureReturnsResult : DatabaseMetaData.procedureNoResult;
  }

  /**
   * Creates a new ProcedureDefinition.
   *
   * @param schema         the schema of the procedure
   * @param procedureName  the name of the procedure
   * @param packageName    the name of the Oracle package, may be null
   * @param type           the return type of the procedure (DatabaseMetaData.procedureNoResult or DatabaseMetaData.procedureReturnsResult)
   * @param remarks        the comment for this procedure
   * @return the new ProcedureDefinition
   */
  public static ProcedureDefinition createOracleDefinition(String schema, String procedureName, String packageName, int type, String remarks)
  {
    ProcedureDefinition def = new ProcedureDefinition(packageName, schema, procedureName, RoutineType.fromProcedureResult(type), type);
    if (StringUtil.isNotBlank(packageName))
    {
      if ("OBJECT TYPE".equals(remarks))
      {
        def.procType = ProcType.objectType;
      }
      else
      {
        def.procType = ProcType.packageType;
      }
    }
    return def;
  }

  public Object getInternalIdentifier()
  {
    return internalIdentifier;
  }

  public void setInternalIdentifier(Object internalName)
  {
    this.internalIdentifier = internalName;
  }

  public String getSpecificName()
  {
    return specificName;
  }

  public void setSpecificName(String specificName)
  {
    this.specificName = specificName;
  }

  public void setOracleOverloadIndex(String indicator)
  {
    oracleOverloadIndex = indicator;
  }

  public String getOracleOverloadIndex()
  {
    return oracleOverloadIndex;
  }

  public void setDbmsProcType(String type)
  {
    dbmsProcType = type;
  }

  public String getDbmsProcType()
  {
    return dbmsProcType;
  }

  @Override
  public void setSchema(String schema)
  {
    this.schema = schema;
  }

  @Override
  public void setCatalog(String catalog)
  {
    this.catalog = catalog;
  }

  @Override
  public void setName(String name)
  {
    procName = name;
  }

  public void setDisplayName(String name)
  {
    displayName = name;
  }

  public String getDisplayName()
  {
    if (displayName == null) return procName;
    return displayName;
  }

  @Override
  public String getComment()
  {
    return comment;
  }

  @Override
  public void setComment(String cmt)
  {
    comment = cmt;
  }

  @Override
  public boolean supportsGetSource()
  {
    return true;
  }

  public void setParameters(List<ColumnIdentifier> procParams)
  {
    synchronized (this)
    {
      if (procParams == null)
      {
        this.parameters = null;
      }
      else
      {
        this.parameters = new ArrayList<>(procParams);
      }
    }
  }

  public List<ColumnIdentifier> getParameters(WbConnection con)
  {
    if (con == null)
    {
      synchronized (this)
      {
        if (parameters == null)
        {
          return Collections.emptyList();
        }
        return Collections.unmodifiableList(parameters);
      }
    }
    readParameters(con);
    return Collections.unmodifiableList(parameters);
  }

  public void readParameters(WbConnection con)
  {
    synchronized (this)
    {
      if (parameters == null)
      {
        try
        {
          ProcedureReader reader = con.getMetadata().getProcedureReader();
          reader.readProcedureParameters(this);
        }
        catch (SQLException s)
        {
          LogMgr.logError(new CallerInfo(){}, "Could not read procedure parameters", s);
        }
      }
    }
  }

  public List<String> getParameterNames()
  {
    synchronized (this)
    {
      if (parameters == null) return Collections.emptyList();
      List<String> result = new ArrayList<>(parameters.size());
      for (ColumnIdentifier col : parameters)
      {
        result.add(col.getColumnName());
      }
      return result;
    }
  }

  public List<String> getParameterTypes()
  {
    synchronized (this)
    {
      if (parameters == null) return Collections.emptyList();
      List<String> result = new ArrayList<>(parameters.size());
      for (ColumnIdentifier col : parameters)
      {
        result.add(col.getDbmsType());
      }
      return result;
    }
  }

  @Override
  public String getDropStatement(WbConnection con, boolean cascade)
  {
    DbMetadata meta = con.getMetadata();
    if (isPackageProcedure())
    {
      return "DROP PACKAGE "  + meta.quoteObjectname(schema) + "." + meta.quoteObjectname(catalog);
    }
    if (isOracleObjectType())
    {
      String drop = "DROP TYPE " + meta.quoteObjectname(schema) + "." + meta.quoteObjectname(catalog);
      if (cascade)
      {
        drop += " FORCE";
      }
      return drop;
    }
    // Apply default statements
    return null;
  }

  @Override
  public String getObjectNameForDrop(WbConnection con)
  {
    boolean needParameters = con == null ? false : con.getDbSettings().needParametersToDropFunction();
    boolean includeOutParameters = con == null ? false : con.getDbSettings().includeOutParameterForDropFunction();
    boolean useSpecificName = con == null ? false : con.getDbSettings().useSpecificNameForDropFunction();
    return getObjectNameForDrop(con, needParameters, includeOutParameters, useSpecificName);
  }

  public String getObjectNameForDrop(WbConnection con, boolean needParameters, boolean includeOutParameters, boolean useSpecificName)
  {
    if (procType != null)
    {
      return catalog;
    }

    boolean nameContainsParameters = this.procName.indexOf('(') > -1;
    if (useSpecificName && specificName != null)
    {
      nameContainsParameters = specificName.indexOf('(') > -1;
    }

    if (!needParameters || (nameContainsParameters && includeOutParameters))
    {
      return buildObjectExpression(con, useSpecificName);
    }

    // we need the parameters, so retrieve them now
    readParameters(con);

    if (CollectionUtil.isEmpty(parameters)) return getObjectExpression(con) + "()";

    int paramCount = 0;
    for (ColumnIdentifier col : parameters)
    {
      boolean shouldInclude = includeOutParameters || col.getArgumentMode().startsWith("IN");
      if (shouldInclude)
      {
        paramCount ++;
      }
    }
    if (paramCount == 0) return getObjectExpression(con) + "()";

    StringBuilder result = new StringBuilder(procName.length() + paramCount * 5 + 5);

    result.append(SqlUtil.fullyQualifiedName(con, catalog, schema, getBasename(useSpecificName)));
    result.append('(');

    int colCount = 0;
    for (ColumnIdentifier parameter : parameters)
    {
      ColumnIdentifier col = parameter;
      boolean shouldInclude = includeOutParameters || col.getArgumentMode().startsWith("IN");
      if (shouldInclude)
      {
        if (colCount > 0) result.append(',');
        result.append(parameter.getDbmsType());
        colCount ++;
      }
    }
    result.append(')');
    return result.toString();
  }

  @Override
  public CharSequence getSource(WbConnection con)
    throws SQLException
  {
    if (con == null) return this.source;
    if (this.source == null)
    {
      try
      {
        con.getMetadata().getProcedureReader().readProcedureSource(this);
      }
      catch (NoConfigException e)
      {
        this.source = "N/A";
      }
    }
    return this.source;
  }

  protected String getBasename(boolean useSpecificName)
  {
    if (useSpecificName && specificName != null) return specificName;
    String name = procName;
    if (procName.indexOf('(') > -1)
    {
      name = procName.substring(0, name.indexOf('('));
    }
    return name;
  }

  @Override
  public String getObjectName(WbConnection conn)
  {
    String name = getBasename(false);
    return conn.getMetadata().quoteObjectname(name);
  }

  @Override
  public String getFullyQualifiedName(WbConnection conn)
  {
    return getObjectExpression(null);
  }

  @Override
  public String getObjectExpression(WbConnection conn)
  {
    return buildObjectExpression(conn, false);
  }

  private String buildObjectExpression(WbConnection conn, boolean useSpecificName)
  {
    String nameToUse = null;
    if (useSpecificName && specificName != null)
    {
      nameToUse = specificName;
    }
    else
    {
      nameToUse = procName;
    }

    String name = nameToUse;
    int pos = nameToUse.indexOf('(');

    if (pos > -1)
    {
      // remove the parameters for building the fully qualified name
      // otherwise buildExpression will quote the name due to the parantheses
      name = name.substring(0, pos);
    }

    String expr = SqlUtil.buildExpression(conn, catalog, schema, name);
    if (pos > -1)
    {
      // add the parameters back to the complete name
      expr = expr + nameToUse.substring(pos);
    }
    return expr;
  }


  @Override
  public String getObjectName()
  {
    return procName;
  }

  public void setSource(CharSequence s)
  {
    this.source = s;
  }

  public CharSequence getSource()
  {
    return this.source;
  }

  public void setPackageName(String packageName)
  {
    if (StringUtil.isNotEmpty(packageName))
    {
      procType = ProcType.packageType;
      catalog = packageName;
    }
  }

  public boolean isPackageProcedure()
  {
    return procType == ProcType.packageType;
  }

  public boolean isOracleObjectType()
  {
    return procType == ProcType.objectType;
  }

  /**
   * Returns the package or object type name of this definition
 This will return null if isPackageProcedure() == false or isOracleObjectType() == false
   */
  public String getPackageName()
  {
    if (isPackageProcedure() || isOracleObjectType()) return catalog;
    return null;
  }

  @Override
  public String getCatalog()
  {
    return this.catalog;
  }

  @Override
  public String getSchema()
  {
    return this.schema;
  }

  public String getProcedureName()
  {
    return getObjectName();
  }

  public void setRoutineType(RoutineType type)
  {
    this.routineType = type;
  }

  public RoutineType getRoutineType()
  {
    return this.routineType;
  }

  public int getResultType()
  {
    return this.resultType;
  }

  @Override
  public String getObjectType()
  {
    if (this.dbmsProcType != null)
    {
      return this.dbmsProcType;
    }

    if (this.isOracleObjectType())
    {
      return "TYPE";
    }

    if (isFunction())
    {
      return ProcedureReader.TYPE_NAME_FUNC;
    }
    return ProcedureReader.TYPE_NAME_PROC;
  }

  @Override
  public String toString()
  {
    String name = procType != null ? catalog + "." + procName : SqlUtil.buildExpression(null, null, schema, procName);
    if (CollectionUtil.isNonEmpty(parameters))
    {
      return name + "(" + getInputParameterNames() + ")";
    }
    else if (isFunction())
    {
      name = name + "()";
    }
    return name;
  }

  private String getInputParameterNames()
  {
    if (CollectionUtil.isEmpty(parameters)) return "";

    return this.parameters.stream().
        filter(c -> c.getArgumentMode().startsWith("IN")).
        map(c -> c.getColumnName()).
        collect(Collectors.joining(", "));
  }

  public String buildSelectable(WbConnection con)
  {
    QuoteHandler handler = SqlUtil.getQuoteHandler(con);

    String name;
    if (procType != null)
    {
      name = handler.quoteObjectname(catalog) + "." + handler.quoteObjectname(procName);
    }
    else
    {
      name = SqlUtil.buildExpression(con, catalog, schema, procName);
    }

    boolean alwaysNeedsParens = true;
    boolean needsTableKeyword = false;

    if (con != null)
    {
      alwaysNeedsParens = con.getDbSettings().getTableFunctionAlwaysNeedsParens();
      needsTableKeyword = con.getDbSettings().getTableFunctionNeedsTableKeyword();
    }

    if (parameters.size() > 0 || alwaysNeedsParens)
    {
      name = name + "(" + getInputParameterNames() + ")";
    }


    if (needsTableKeyword)
    {
      name = "table(" + name + ")";
    }
    return name;
  }

  public String createSql(WbConnection con)
  {
    boolean hasOutParameters = false;
    boolean returnsRefCursor = false;
    boolean isFunction = false;
    DataStore params = null;

    try
    {
      params = con.getMetadata().getProcedureReader().getProcedureColumns(this);
      returnsRefCursor = returnsRefCursor(con, params);
      int rows = params.getRowCount();

      for (int i = 0; i < rows; i++)
      {
        String type = params.getValueAsString(i, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_RESULT_TYPE);

        if (type.endsWith("OUT"))
        {
          hasOutParameters = true;
        }

        if (type.equals("RETURN"))
        {
          isFunction = true;
        }
      }

    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not read procedure definition", ex);
      return null;
    }

    if (isFunction && !returnsRefCursor && !hasOutParameters)
    {
      String sql = buildFunctionCall(con, params);
      if (sql != null)
      {
        return sql;
      }
    }
    return createWbCallStatement(params);
  }

  public String createWbCallStatement(DataStore params)
  {
    StringBuilder call = new StringBuilder(150);
    CommandTester c = new CommandTester();

    StringBuilder paramNames = new StringBuilder(50);

    call.append(c.formatVerb(WbCall.VERB));
    call.append(' ');
    call.append(procType != null ? catalog + "." + procName : procName);
    call.append("(");

    int numParams = 0;
    int rows = params.getRowCount();

    for (int i = 0; i < rows; i++)
    {
      String type = params.getValueAsString(i, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_RESULT_TYPE);
      String param = params.getValueAsString(i, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_COL_NAME);

      if (numParams > 0)
      {
        call.append(',');
      }

      // only append a ? for OUT or INOUT parameters, not for RETURN parameters
      if (type.equals("IN") || type.endsWith("OUT"))
      {
        if (numParams == 0)
        {
          paramNames.append("-- Parameters: ");
        }
        else
        {
          paramNames.append(", ");
        }
        paramNames.append(param);
        paramNames.append(" (");
        paramNames.append(type);
        paramNames.append(')');
        call.append('?');
        numParams ++;
      }
    }
    call.append(");");
    if (numParams > 0)
    {
      paramNames.append('\n');
      call.insert(0, paramNames);
    }

    return call.toString();
  }

  private String buildFunctionCall(WbConnection conn, DataStore params)
  {
    String template = conn.getDbSettings().getSelectForFunctionSQL();
    if (template == null)
    {
      return null;
    }

    StringBuilder call = new StringBuilder(150);
    call.append(procType != null ? catalog + "." + procName : procName);
    call.append("(");

    int rows = params.getRowCount();
    int numParams = 0;

    for (int i = 0; i < rows; i++)
    {
      String type = params.getValueAsString(i, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_RESULT_TYPE);
      String param = params.getValueAsString(i, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_COL_NAME);
      int dataType = params.getValueAsInt(i, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_JDBC_DATA_TYPE, Types.OTHER);

      if (numParams > 0)
      {
        call.append(", ");
      }

      if (!type.equals("RETURN"))
      {
        if (SqlUtil.isCharacterType(dataType))
        {
          call.append('\'');
        }
        call.append("$[?");
        call.append(param);
        call.append(']');
        if (SqlUtil.isCharacterType(dataType))
        {
          call.append('\'');
        }
        numParams ++;
      }
    }
    call.append(")");

    String sql = template.replace("%function%", call.toString());
    return sql;
  }

  public static boolean isRefCursor(WbConnection conn, String type)
  {
    Collection<String> refTypes = conn.getDbSettings().getRefCursorTypeNames();
    return refTypes.contains(type);
  }

  public boolean hasOutParameter(DataStore params)
  {
    for (int i=0; i < params.getRowCount(); i++)
    {
      String resultMode = params.getValueAsString(i, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_RESULT_TYPE);
      if (resultMode.endsWith("OUT")) return true;
    }
    return false;
  }

  public static boolean returnsRefCursor(WbConnection conn, DataStore params)
  {
    // A function in Postgres that returns a refcursor
    // must be called using {? = call('procname')} in order
    // to be able to retrieve the result set from the refcursor
    for (int i=0; i < params.getRowCount(); i++)
    {
      String typeName = params.getValueAsString(i, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_DATA_TYPE);
      String resultType = params.getValueAsString(i, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_RESULT_TYPE);
      if (isRefCursor(conn, typeName) && "RETURN".equals(resultType)) return true;
    }
    return false;
  }

  public static boolean isFunction(ProcedureDefinition def, DataStore params)
  {
    if (def.isFunction()) return true;

    // This is mainly for SQL Server which can have procedures that have a return value like a function.
    for (int i=0; i < params.getRowCount(); i++)
    {
      String resultType = params.getValueAsString(i, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_RESULT_TYPE);
      if ("RETURN".equals(resultType)) return true;
    }
    return false;
  }

  public boolean isTableFunction()
  {
    return this.routineType == RoutineType.tableFunction;
  }

  public boolean isFunction()
  {
    return this.routineType == RoutineType.function || this.routineType == RoutineType.tableFunction;
  }

  public boolean isFunction(DataStore params)
  {
    for (int i=0; i < params.getRowCount(); i++)
    {
      String resultMode = params.getValueAsString(i, ProcedureReader.COLUMN_IDX_PROC_COLUMNS_RESULT_TYPE);
      if ("RETURN".equals(resultMode)) return true;
    }
    return false;
  }

  public ProcedureDefinition createCopy()
  {
    ProcedureDefinition copy = new ProcedureDefinition(catalog, schema, procName, routineType, resultType);
    copy.setPackageName(getPackageName());
    if (this.parameters != null)
    {
      copy.parameters = new ArrayList<>(this.parameters.size());
      for (ColumnIdentifier p : parameters)
      {
        copy.parameters.add(p.createCopy());
      }
    }
    copy.source = this.source;
    copy.internalIdentifier = this.internalIdentifier;
    copy.specificName = this.specificName;
    copy.dbmsProcType = this.dbmsProcType;
    copy.oracleOverloadIndex = this.oracleOverloadIndex;
    return copy;
  }

  private static enum ProcType
    implements Serializable
  {
    packageType,
    objectType;
  }
}
