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
package workbench.storage;

import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.db.exporter.BlobMode;

import workbench.util.FileUtil;
import workbench.util.NumberStringCache;

/**
 * A class to format a byte[] array to be used as a literal in a SQL statement for PostgreSQL.
 * <br/>
 * The literal is constructed using the decode() function and a hex representation
 * of the value (as that is the most compact form)
 * <br/>
 * The actual format is controlled through the configuration property workbench.db.postgresql.blobformat
 * valid values are <tt>decode</tt>, and <tt>escape</tt>
 * For escape an binary "escape" syntax is used, e.g.: E'\\001'::bytea
 *
 * See also: https://www.postgresql.org/docs/current/static/datatype-binary.html
 *
 * @author Thomas Kellerer
 */
public class PostgresBlobFormatter
  implements BlobLiteralFormatter
{
  private BlobLiteralType blobLiteral;
  private DefaultBlobFormatter defaultFormatter;

  public PostgresBlobFormatter()
  {
    String type = Settings.getInstance().getProperty("workbench.db.postgresql.blobformat", "decode");
    if ("escape".equalsIgnoreCase(type))
    {
      blobLiteral = BlobLiteralType.pgEscape;
    }
    else
    {
      blobLiteral = BlobLiteralType.pgDecode;
    }
  }

  public PostgresBlobFormatter(BlobMode mode)
  {
    switch (mode)
    {
      case pgEscape:
        setLiteralType(BlobLiteralType.pgEscape);
        break;
      case pgHex:
        setLiteralType(BlobLiteralType.pgHex);
        break;
      case UUID:
        setLiteralType(BlobLiteralType.uuid);
        break;
      default:
        setLiteralType(BlobLiteralType.pgDecode);
    }
  }

  public PostgresBlobFormatter(BlobLiteralType mode)
  {
    setLiteralType(mode);
  }

  private void setLiteralType(BlobLiteralType mode)
  {
    this.blobLiteral = mode;
    if (blobLiteral == BlobLiteralType.uuid)
    {
      defaultFormatter = new DefaultBlobFormatter();
      defaultFormatter.setLiteralType(BlobLiteralType.uuid);
    }
  }

  @Override
  public String getBlobLiteral(Object value)
    throws SQLException
  {
    switch (blobLiteral)
    {
      case pgEscape:
        return getEscapeString(value);
      case pgHex:
        return getHexString(value);
      case uuid:
        return getUUIDString(value);
      default:
        return getDecodeString(value);
    }
  }

  private String getUUIDString(Object value)
  {
    if (value == null) return null;
    byte[] buffer = getBytes(value);
    if (buffer == null) return value.toString();
    try
    {
      return defaultFormatter.getBlobLiteral(buffer);
    }
    catch (SQLException sql)
    {
      // can not happen
      return value.toString();
    }
  }

  private String getDecodeString(Object value)
  {
    if (value == null) return null;
    byte[] buffer = getBytes(value);
    if (buffer == null) return value.toString();

    StringBuilder result = new StringBuilder(buffer.length * 2 + 20);
    result.append("decode('");
    appendBuffer(buffer, result);
    result.append("', 'hex')");
    return result.toString();
  }

  private String getHexString(Object value)
  {
    if (value == null) return null;
    byte[] buffer = getBytes(value);
    if (buffer == null) return value.toString();

    StringBuilder result = new StringBuilder(buffer.length * 2 + 5);
    result.append("\\\\x");
    appendBuffer(buffer, result);
    return result.toString();
  }

  private void appendBuffer(byte[] buffer, StringBuilder result)
  {
    for (int i = 0; i < buffer.length; i++)
    {
      int c = (buffer[i] < 0 ? 256 + buffer[i] : buffer[i]);
      result.append(NumberStringCache.getHexString(c));
    }
  }

  private String getEscapeString(Object value)
  {
    if (value == null) return null;
    byte[] buffer = getBytes(value);
    if (buffer == null) return value.toString();

    StringBuilder result = new StringBuilder(buffer.length * 5 + 12);
    result.append("E'");
    for (int i = 0; i < buffer.length; i++)
    {
      result.append("\\\\");
      int c = (buffer[i] < 0 ? 256 + buffer[i] : buffer[i]);
      String s = Integer.toOctalString(c);
      int l = s.length();
      if (l == 1)
      {
        result.append("00");
      }
      else if (l == 2)
      {
        result.append('0');
      }
      result.append(s);
    }
    result.append("'::bytea");
    return result.toString();
  }

  private byte[] getBytes(Object value)
  {
    if (value instanceof byte[])
    {
      return (byte[])value;
    }

    if (value instanceof InputStream)
    {
      // When doing an export the Blobs might be returned as InputStreams
      InputStream in = (InputStream)value;
      try
      {
        return FileUtil.readBytes(in);
      }
      catch (IOException io)
      {
        LogMgr.logError(new CallerInfo(){}, "Could not read input stream", io);
      }
    }
    return null;
  }

  @Override
  public BlobLiteralType getType()
  {
    return blobLiteral;
  }

}

