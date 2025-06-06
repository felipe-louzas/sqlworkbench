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
package workbench.sql.commands;

import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;

import workbench.db.ErrorInformationReader;
import workbench.db.ReaderFactory;
import workbench.db.TableIdentifier;

import workbench.sql.ErrorDescriptor;
import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;
import workbench.sql.lexer.SQLLexer;
import workbench.sql.lexer.SQLLexerFactory;
import workbench.sql.lexer.SQLToken;
import workbench.sql.parser.ParserType;

import workbench.util.CollectionUtil;
import workbench.util.DdlObjectInfo;
import workbench.util.SqlUtil;

/**
 * Run a DDL (CREATE, DROP, ALTER, GRANT, REVOKE) command.
 *
 * @author Thomas Kellerer
 */
public class DdlCommand
  extends SqlCommand
{
  // Firebird RECREATE VIEW command
  public static DdlCommand getRecreateCommand()
  {
    return new DdlCommand("RECREATE");
  }

  public static DdlCommand getCreateCommand()
  {
    return new DdlCommand("CREATE");
  }

  public static List<DdlCommand> getDdlCommands()
  {
    return CollectionUtil.readOnlyList(
      new DdlCommand("DROP"),
      getCreateCommand(),
      new DdlCommand("ALTER"),
      new DdlCommand("GRANT"),
      new DdlCommand("REVOKE"));
  }

  private final String verb;
  private final Set<String> typesToRemember = CollectionUtil.caseInsensitiveSet("procedure", "function", "trigger", "package", "package body", "type");
  private Pattern alterDropPattern;
  private Pattern pgDropOwned;

  private DdlCommand(String sqlVerb)
  {
    super();
    this.verb = sqlVerb;
    this.isUpdatingCommand = true;
    if ("ALTER".equals(verb))
    {
      // an ALTER ... statement might also be a DROP (e.g. ALTER TABLE someTable DROP PRIMARY KEY)
      alterDropPattern = Pattern.compile("DROP\\s+(PRIMARY\\s+KEY|CONSTRAINT)\\s+", Pattern.CASE_INSENSITIVE);
    }
    if ("DROP".equals(verb))
    {
      pgDropOwned = Pattern.compile("DROP\\s+OWNED\\s+BY\\s+", Pattern.CASE_INSENSITIVE);
    }
  }

  @Override
  public StatementRunnerResult execute(String sql)
    throws SQLException
  {
    StatementRunnerResult result = createResult(sql);
    result.ignoreUpdateCounts(true);

    boolean useSavepoint = runner.useSavepointForDDL();

    final CallerInfo ci = new CallerInfo(){};
    if (useSavepoint && !this.currentConnection.supportsSavepoints())
    {
      useSavepoint = false;
      LogMgr.logWarning(ci, "A savepoint should be used for this DDL command, but the driver does not support savepoints!");
    }

    DdlObjectInfo info = SqlUtil.getDDLObjectInfo(sql, currentConnection);
    if (info != null && verb.equals("ALTER") && info.getObjectType().equals("PACKAGE"))
    {
      // an "alter package .. compile"  will report errors for the package body, not the package
      info.setObjectType("PACKAGE BODY");
    }

    if (info != null && typesToRemember.contains(info.getObjectType()))
    {
      // This is only here to mimic SQL*Plus' behaviour for a "SHOW ERROR" without a parameter.
      // remember the last "object" in order to be able to show the errors but only for "PL/SQL" objects.
      // The last error is not overwritten by creating a table or a view
      currentConnection.setLastDDLObject(info);
    }

    boolean isDrop = false;
    boolean ignoreDropError = false;
    Savepoint ddlSavepoint = null;
    try
    {
      this.currentStatement = currentConnection.createStatement();

      if (currentConnection.getDbSettings().disableEscapesForDDL())
      {
        currentStatement.setEscapeProcessing(false);
      }

      sql = getSqlToExecute(sql);

      result.setSuccess();

      if (useSavepoint)
      {
        ddlSavepoint = currentConnection.setSavepoint(ci);
      }

      isDrop = isDropCommand(sql);
      ignoreDropError = isDrop && this.runner.getIgnoreDropErrors();

      boolean hasResult = this.currentStatement.execute(sql);

      // Using a generic execute and result processing ensures that DBMS that
      // can process more than one statement with a single SQL are treated correctly.
      // e.g. when sending a SELECT and other statements as a "batch" with SQL Server
      processResults(result, hasResult);

      // processMoreResults() will have added any warnings and set the warning flag
      if (result.hasWarning())
      {
        // if the warning is actually an error, addExtendErrorInfo() will set the result to "failure"
        this.addExtendErrorInfo(sql, info, result);
      }

      if (result.isSuccess())
      {
        result.addMessage(buildSuccessMessage(info, sql));
      }

      this.currentConnection.releaseSavepoint(ddlSavepoint, ci);

      if (info != null && result.isSuccess() && "database".equalsIgnoreCase(info.getObjectType()))
      {
        currentConnection.getObjectCache().flushCachedDatabases();
        currentConnection.catalogListChanged();
      }
      else if (isDrop && result.isSuccess())
      {
        removeFromCache(info);
      }
    }
    catch (Exception e)
    {
      this.currentConnection.rollback(ddlSavepoint, ci);

      if (ignoreDropError)
      {
        addDropWarning(info, result);
        addErrorPosition(result, sql, e);
        result.setSuccess(); // must be done after addErrorPosition!
      }
      else
      {
        result.setFailure();
        addErrorStatement(result, sql);

        // if we have a warning and an exception we need to use the extended error info
        if (result.hasWarning())
        {
          // if addExtendedErrorInfo() added something, then there is no need to add the error possition
          // (assuming the more verbose error message already contains that information)
          if (!addExtendErrorInfo(sql, info, result))
          {
            addErrorPosition(result, sql, e);
          }
        }
        else
        {
          addErrorPosition(result, sql, e);
        }

        LogMgr.logUserSqlError(ci, sql, e);
      }
    }
    finally
    {
      // we know that we don't need the statement any longer, so to make
      // sure everything is cleaned up, we'll close it here
      done();
    }

    return result;
  }

  private void addDropWarning(DdlObjectInfo info, StatementRunnerResult result)
  {
    String msg;
    if (info != null)
    {
      msg = ResourceMgr.getFormattedString("MsgDropWarningNamed", info.getObjectName());
    }
    else
    {
      msg = ResourceMgr.getString("MsgDropWarning");
    }
    result.addMessage(msg);
  }

  private void removeFromCache(DdlObjectInfo info)
  {
    if (info == null) return;
    if (info.getObjectNames().isEmpty()) return;
    if (currentConnection == null) return;

    if ("SCHEMA".equalsIgnoreCase(info.getObjectType()))
    {
      for (String name : info.getObjectNames())
      {
        currentConnection.getObjectCache().removeSchema(name);
      }
    }
    else
    {
      for (String name : info.getObjectNames())
      {
        currentConnection.getObjectCache().removeTable(new TableIdentifier(name, currentConnection));
      }
    }
  }

  @Override
  public void done()
  {
    super.done();
  }

  public boolean isDropCommand(String sql)
  {
    if ("DROP".equals(this.verb))
    {
      return true;
    }
    if (!"ALTER".equals(this.verb))
    {
      return false;
    }
    Matcher m = alterDropPattern.matcher(sql);
    return m.find();
  }

  private String buildSuccessMessage(DdlObjectInfo info, String sql)
  {
    if (isPgDropOwned(sql))
    {
      String owner = getOwner(sql);
      if (owner != null)
      {
        return ResourceMgr.getFormattedString("MsgDropOwned", owner);
      }
    }
    return getSuccessMessage(info, getVerb());
  }

  private String getOwner(String sql)
  {
    SQLLexer lexer = SQLLexerFactory.createLexer(ParserType.Postgres, sql);
    lexer.getNextToken(false, false); // skip the drop
    lexer.getNextToken(false, false); // skip the "owned by"

    SQLToken owner = lexer.getNextToken(false, false);
    if (owner != null)
    {
      return SqlUtil.removeObjectQuotes(owner.getText());
    }
    return null;
  }

  @Override
  protected String getSuccessMessage(DdlObjectInfo info, String verb)
  {
    String msg = super.getSuccessMessage(info, getVerb());
    if (msg == null)
    {
      return getDefaultSuccessMessage(null);
    }
    return msg;
  }

  private boolean isPgDropOwned(String sql)
  {
    if (pgDropOwned != null)
    {
      Matcher m = pgDropOwned.matcher(sql);
      return m.find();
    }
    return false;
  }

  /**
   * Retrieve extended error information if the DBMS supports this.
   *
   * @return true if an error was added, false otherwise
   *
   * @see ErrorInformationReader#getErrorInfo(java.lang.String, java.lang.String, java.lang.String, boolean)
   * @see ReaderFactory#getErrorInformationReader(workbench.db.WbConnection)
   */
  private boolean addExtendErrorInfo(String sql, DdlObjectInfo info , StatementRunnerResult result)
  {
    if (info == null) return false;
    if (currentConnection == null) return false;

    ErrorInformationReader reader = ReaderFactory.getErrorInformationReader(currentConnection);
    if (reader == null) return false;

    ErrorDescriptor error = reader.getErrorInfo(sql, null, info.getObjectName(), info.getObjectType(), true);
    if (error == null) return false;

    if (error.getErrorPosition() == -1 && error.getErrorColumn() > -1 && error.getErrorLine() > -1)
    {
      int startOffset = 0;

      if (!currentConnection.getDbSettings().getErrorPosIncludesLeadingComments())
      {
        startOffset = SqlUtil.getRealStart(sql);
        sql = sql.substring(startOffset);
      }
      int offset = SqlUtil.getErrorOffset(sql, error);
      error.setErrorOffset(offset + startOffset);
    }
    result.addMessageNewLine();
    result.setFailure(error);
    result.addMessage(error.getErrorMessage());
    return true;
  }

  @Override
  public String getVerb()
  {
    return verb;
  }

}
