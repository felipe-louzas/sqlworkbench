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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Blob;
import java.sql.ResultSet;
import java.sql.SQLException;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;

import workbench.db.BlobAccessType;
import workbench.db.JdbcUtils;

import workbench.sql.SqlCommand;
import workbench.sql.StatementRunnerResult;
import workbench.sql.lexer.SQLLexer;
import workbench.sql.lexer.SQLLexerFactory;
import workbench.sql.lexer.SQLToken;

import workbench.util.ExceptionUtil;
import workbench.util.FileUtil;
import workbench.util.StringUtil;
import workbench.util.WbFile;

/**
 * A SQL statement that can retrieve the data from a blob column into a file
 * on the client.
 * <br/>
 * It is intended to store the contents of a single row/column into a file.
 * For that it accepts an "INTO &lt;filename&gt;" syntax.
 * <br/>
 * If the generated SELECT returns more than one row, additional files
 * are created with a sequence counter.
 * <br/>
 * As WbExport can also retrieve BLOB data and allows automated control
 * over the generated filenames (e.g. by using a different column's content)
 * WbExport should be preferred over this command.
 *
 * @author Thomas Kellerer
 * @see WbExport
 */
public class WbSelectBlob
  extends SqlCommand
{
  public static final String VERB = "WbSelectBlob";

  public WbSelectBlob()
  {
    super();
    this.isUpdatingCommand = false;
  }

  @Override
  public String getVerb()
  {
    return VERB;
  }

  @Override
  public StatementRunnerResult execute(final String sqlCommand)
    throws SQLException
  {
    StatementRunnerResult result = new StatementRunnerResult(sqlCommand);
    SQLLexer lexer = SQLLexerFactory.createLexer(currentConnection, sqlCommand);

    StringBuilder sql = new StringBuilder(sqlCommand.length());

    WbFile outputFile = null;

    SQLToken token  = lexer.getNextToken(false, false);
    if (token == null)
    {
      result.addErrorMessageByKey("ErrSelectBlobSyntax");
      return result;
    }
    else if (!token.getContents().equals("WBSELECTBLOB"))
    {
      result.addMessageByKey("ErrSelectBlobSyntax");
      result.setFailure();
      return result;
    }
    sql.append("SELECT ");
    while (token != null)
    {
      token = lexer.getNextToken(false, true);
      if (token == null) break;

      if (token.getContents().equals("INTO"))
      {
        break;
      }
      sql.append(token.getContents());
    }

    if (token != null && !token.getContents().equals("INTO"))
    {
      result.addMessageByKey("ErrSelectBlobSyntax");
      result.setFailure();
      return result;
    }
    else
    {
      // Next token must be the filename
      token = lexer.getNextToken(false, false);
      String filename = token.getContents();
      outputFile = new WbFile(StringUtil.trimQuotes(filename));
      sql.append(' ');
      sql.append(sqlCommand.substring(token.getCharEnd() + 1));
    }

    LogMgr.logDebug(new CallerInfo(){}, "Using SQL=" + sql + " for file: " + outputFile.getFullPath());
    ResultSet rs = null;
    OutputStream out = null;
    InputStream in = null;
    long filesize = 0;

    File outputDir = outputFile.getParentFile();
    String baseFilename = outputFile.getFileName();
    String extension = outputFile.getExtension();
    if (StringUtil.isEmpty(extension)) extension = "";
    else extension = "." + extension;

    try
    {
      currentStatement = currentConnection.createStatementForQuery();
      rs = currentStatement.executeQuery(sql.toString());
      int row = 0;
      while (rs.next())
      {
        WbFile currentFile = null;

        BlobAccessType method = currentConnection.getDbSettings().getBlobReadMethod();
        switch (method)
        {
          case byteArray:
            byte[] data = rs.getBytes(1);
            in = new ByteArrayInputStream(data);
            break;
          case jdbcBlob:
            Blob blob = rs.getBlob(1);
            in = blob.getBinaryStream();
          default:
            in = rs.getBinaryStream(1);
        }

        if (in == null)
        {
          //result.setFailure();
          String msg = ResourceMgr.getString("ErrSelectBlobNoStream");
          result.addWarning(StringUtil.replace(msg, "%row%", Integer.toString(row)));
          continue;
        }

        if (row == 0)
        {
          currentFile = outputFile;
        }
        else
        {
          currentFile = new WbFile(outputDir, baseFilename + "_" + Integer.toString(row) + extension);
        }

        out = new FileOutputStream(currentFile);
        filesize = FileUtil.copy(in, out);
        String msg = ResourceMgr.getString("MsgBlobSaved");
        msg = msg.replace("%filename%", currentFile.getFullPath());
        msg = msg.replace("%filesize%", Long.toString(filesize));
        result.addMessage(msg);
        result.setSuccess();
        row ++;
      }
      this.appendSuccessMessage(result);
    }
    catch (IOException e)
    {
      String msg = StringUtil.replace(ResourceMgr.getString("ErrSelectBlobFileError"), "%filename%", outputFile.getFullPath());
      result.addMessage(msg);
      result.setFailure();
      return result;
    }
    catch (SQLException e)
    {
      String msg = StringUtil.replace(ResourceMgr.getString("ErrSelectBlobSqlError"), "%filename%", outputFile.getFullPath());
      result.addMessage(msg);
      result.addMessage(ExceptionUtil.getDisplay(e));
      result.setFailure();
      return result;
    }
    finally
    {
      JdbcUtils.closeAll(rs, currentStatement);
    }

    return result;
  }

  @Override
  public boolean isWbCommand()
  {
    return true;
  }

  @Override
  public boolean shouldEndTransaction()
  {
    return true;
  }

}
