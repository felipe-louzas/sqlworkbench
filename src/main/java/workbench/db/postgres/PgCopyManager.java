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
package workbench.db.postgres;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;
import java.util.List;

import workbench.WbManager;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.ColumnIdentifier;
import workbench.db.ConnectionMgr;
import workbench.db.JdbcUtils;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;
import workbench.db.importer.StreamImporter;
import workbench.db.importer.TextImportOptions;

import workbench.storage.DataStore;
import workbench.storage.DataStoreWriter;

import workbench.util.FileUtil;

/**
 * A class to use PostgreSQL's CopyManager API.
 *
 * @author Thomas Kellerer
 */
public class PgCopyManager
  implements StreamImporter
{
  private final Object lock = new Object();
  private final WbConnection connection;
  private String sql;
  private Reader data;
  private Object copyManager;
  private Method copyIn;
  private final boolean useDefaultClassloader;
  private final boolean is9_0;
  private final boolean is17;
  private boolean ignoreErrors;

  public PgCopyManager(WbConnection conn)
  {
    this.connection = conn;
    this.is9_0 = conn == null ? true : JdbcUtils.hasMinimumServerVersion(conn, "9.0");
    this.is17 = conn == null ? true : JdbcUtils.hasMinimumServerVersion(conn, "17.0");
    // During unit testing the classloader in the ConnectionMgr is not initialized because all drivers are alread on the classpath.
    // Therefor we need to load the CopyManager class from the default classpath
    useDefaultClassloader = WbManager.isTest();
  }

  public boolean supportsInsertIgnore()
  {
    return this.is17;
  }

  public boolean getIgnoreErrors()
  {
    return ignoreErrors;
  }

  public void setIgnoreErrors(boolean flag)
  {
    this.ignoreErrors = flag;
  }

  @Override
  public void cancel()
  {
    if (this.data != null)
    {
      try
      {
        data.close();
      }
      catch (IOException io)
      {
        // ignore
      }
    }
  }

  public boolean isSupported()
  {
    try
    {
      initialize();
      return true;
    }
    catch (Throwable th)
    {
      LogMgr.logDebug(new CallerInfo(){}, "Error", th);
      return false;
    }
  }

  private void initialize()
    throws ClassNotFoundException
  {
    synchronized (lock)
    {
      try
      {
        Class baseConnClass = null;
        Class copyMgrClass = null;
        String baseConnName = "org.postgresql.core.BaseConnection";
        String copyMgrName = "org.postgresql.copy.CopyManager";
        if (useDefaultClassloader)
        {
          baseConnClass = Class.forName(baseConnName);
          copyMgrClass = Class.forName(copyMgrName);
        }
        else
        {
          baseConnClass = ConnectionMgr.getInstance().loadClassFromDriverLib(connection.getProfile(), baseConnName);
          copyMgrClass = ConnectionMgr.getInstance().loadClassFromDriverLib(connection.getProfile(), copyMgrName);
        }

        Constructor constr = copyMgrClass.getConstructor(baseConnClass);
        copyManager = constr.newInstance(connection.getSqlConnection());
        copyIn = copyManager.getClass().getMethod("copyIn", String.class, Reader.class);
      }
      catch (Throwable t)
      {
        LogMgr.logError(new CallerInfo(){}, "Could not create CopyManager", t);
        throw new ClassNotFoundException("CopyManager");
      }
    }
  }

  private void skipOneLine(Reader in)
  {
    try
    {
      if (in instanceof BufferedReader)
      {
        ((BufferedReader) in).readLine();
      }
      else
      {
        BufferedReader r = new BufferedReader(in);
        r.readLine();
      }
    }
    catch (IOException ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not read line", ex);
    }
  }

  public void copyFromStdin(String copyStatement, Reader stdin)
  {
    sql = copyStatement;
    data = stdin;
  }

  @Override
  public void setup(TableIdentifier table, List<ColumnIdentifier> columns, Reader in, TextImportOptions options, String encoding)
  {
    sql = createCopyStatement(table, columns, options);
    if (options.getContainsHeader() && sql.contains("format text"))
    {
      skipOneLine(in);
    }
    data = in;
  }

  @Override
  public long processStreamData()
    throws SQLException, IOException
  {
    if (data == null || sql == null)
    {
      throw new IllegalStateException("CopyImporter not initialized");
    }

    try
    {
      // As the CopyManager is loaded through the ClassLoader of the DbDriver
      // we cannot have any "hardcoded" references to the PostgreSQL classes
      // that will throw a ClassNotFoundException even though the class was actually loaded
      if (copyManager == null)
      {
        initialize();
      }

      LogMgr.logDebug(new CallerInfo(){}, "Sending file contents using: " + this.sql);

      if (copyIn != null)
      {
        Object rows = copyIn.invoke(copyManager, sql, data);
        if (rows instanceof Number)
        {
          return ((Number)rows).longValue();
        }
      }
      throw new SQLException("CopyAPI not available");
    }
    catch (ClassNotFoundException e)
    {
      throw new SQLException("CopyAPI not available", e);
    }
    catch (Exception e)
    {
      Throwable realException = e;
      if (e instanceof InvocationTargetException)
      {
        realException = e.getCause();
      }
      if (realException instanceof SQLException)
      {
        throw (SQLException)realException;
      }
      if (realException instanceof IOException)
      {
        throw (IOException)realException;
      }
      throw new SQLException("Could not copy data", e.getCause() == null ? e : e.getCause());
    }
    finally
    {
      FileUtil.closeQuietely(data);
      data = null;
    }
  }

  public final String createCopyStatement(TableIdentifier table, List<ColumnIdentifier> columns, TextImportOptions options)
  {
    if (!is9_0)
    {
      return createCopyStatement84(table, columns, options);
    }
    // The encoding is not needed in the generated SQL, as this is handle by the Reader we are using
    return createCopyStatement90(table, columns, options);
  }

  private String createCopyStatement84(TableIdentifier table, List<ColumnIdentifier> columns, TextImportOptions options)
  {
    StringBuilder copySql = new StringBuilder(100);
    copySql.append("COPY ");
    copySql.append(table.getTableExpression(connection));
    copySql.append(" (");
    for (int i=0; i < columns.size(); i++)
    {
      if (i > 0) copySql.append(',');
      copySql.append(columns.get(i).getColumnName());
    }
    boolean useText = options.getTextQuoteChar() == null && options.getDecode();
    if  (useText)
    {
      copySql.append(") FROM stdin");
      return copySql.toString();
    }
    copySql.append(") FROM stdin WITH ");

    String nullString = options.getNullString();

    copySql.append("csv");
    if (options.getContainsHeader())
    {
      copySql.append(" header ");
    }
    String quote = options.getTextQuoteChar();
    if (quote != null)
    {
      copySql.append(" quote '");
      copySql.append(quote);
      copySql.append('\'');
    }

    copySql.append(" delimiter ");
    String delim = options.getTextDelimiter();
    if (delim.equals("\t") || delim.equals("\\t"))
    {
      copySql.append("E'\\t'");
    }
    else
    {
      copySql.append('\'');
      copySql.append(delim);
      copySql.append('\'');
    }

    if (nullString == null) nullString = "";
    copySql.append(" NULL '");
    copySql.append(nullString);
    copySql.append('\'');
    return copySql.toString();
  }

  private String createCopyStatement90(TableIdentifier table, List<ColumnIdentifier> columns, TextImportOptions options)
  {
    StringBuilder copySql = new StringBuilder(100);
    copySql.append("COPY ");
    copySql.append(table.getTableExpression(connection));
    copySql.append(" (");
    for (int i=0; i < columns.size(); i++)
    {
      if (i > 0) copySql.append(',');
      copySql.append(columns.get(i).getColumnName());
    }
    copySql.append(") FROM stdin WITH (format ");

    boolean useText = options.getTextQuoteChar() == null && options.getDecode();
    String nullString = options.getNullString();

    if (useText)
    {
      copySql.append("text");
    }
    else
    {
      copySql.append("csv");
      copySql.append(", header ");
      copySql.append(Boolean.toString(options.getContainsHeader()));
      String quote = options.getTextQuoteChar();
      if (quote != null)
      {
        copySql.append(", quote '");
        copySql.append(quote);
        copySql.append('\'');
      }
    }

    copySql.append(", delimiter ");
    String delim = options.getTextDelimiter();
    if (delim.equals("\t") || delim.equals("\\t"))
    {
      copySql.append("E'\\t'");
    }
    else
    {
      copySql.append('\'');
      copySql.append(delim);
      copySql.append('\'');
    }

    if (nullString == null) nullString = "";
    copySql.append(", NULL '");
    copySql.append(nullString);
    copySql.append('\'');

    if (ignoreErrors && is17)
    {
      copySql.append(", ON_ERROR ignore, LOG_VERBOSITY verbose");
    }
    copySql.append(")");

    return copySql.toString();
  }

  public DataStore copyStdOutToDataStore(String sql)
    throws SQLException
  {
    DataStoreWriter writer = new DataStoreWriter("output");
    runCopyToStdOut(sql, writer);
    DataStore ds = writer.getResult();
    ds.setPrintHeader(false);
    ds.setResultName(null);
    return ds;
  }

  public String copyStdOutToString(String sql)
    throws SQLException
  {
    StringWriter writer = new StringWriter(1000);
    runCopyToStdOut(sql, writer);
    return writer.toString();
  }

  private void runCopyToStdOut(String sql, Writer output)
    throws SQLException
  {
    try
    {
      if (copyManager == null)
      {
        initialize();
      }
      Method copyOut = copyManager.getClass().getMethod("copyOut", String.class, Writer.class);
      copyOut.invoke(copyManager, sql, output);
      output.flush();
    }
    catch (Throwable th)
    {
      Throwable realError = th;
      if (th instanceof InvocationTargetException)
      {
        realError = th.getCause();
      }
      if (realError instanceof SQLException)
      {
        throw (SQLException)realError;
      }
      LogMgr.logError(new CallerInfo(){}, "Could not call copyOut()", th);
      throw new SQLException("Error running COPY command", realError);
    }
  }

}
