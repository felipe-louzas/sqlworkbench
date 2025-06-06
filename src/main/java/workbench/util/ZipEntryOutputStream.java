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
package workbench.util;

import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.ZipOutputStream;

/**
 *
 * @author Thomas Kellerer
 */
public class ZipEntryOutputStream
  extends OutputStream
{

  private ZipOutputStream zout;

  public ZipEntryOutputStream(ZipOutputStream out)
  {
    zout = out;
  }

  @Override
  public void close()
    throws IOException
  {
    zout.closeEntry();
  }

  @Override
  public void flush()
    throws IOException
  {
    zout.flush();
  }

  @Override
  public void write(byte[] b, int off, int len)
    throws IOException
  {
    zout.write(b, off, len);
  }

  @Override
  public void write(byte[] b)
    throws IOException
  {
    zout.write(b);
  }

  @Override
  public void write(int b)
    throws IOException
  {
    zout.write(b);
  }
}
