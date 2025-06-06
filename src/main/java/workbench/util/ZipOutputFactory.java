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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 *
 * @author Thomas Kellerer
 */
public class ZipOutputFactory
  implements OutputFactory
{
  protected File archive;
  protected OutputStream baseOut;
  protected ZipOutputStream zout;

  public ZipOutputFactory(File zip)
  {
    this.archive = zip;
  }

  private void initArchive()
    throws IOException
  {
    baseOut = new FileOutputStream(archive);
    zout = new ZipOutputStream(baseOut);
    zout.setLevel(9);
  }

  @Override
  public boolean isArchive()
  {
    return true;
  }

  @Override
  public OutputStream createOutputStream(File output)
    throws IOException
  {
    String filename = output.getName();
    return createOutputStream(filename);
  }

  public OutputStream createOutputStream(String filename)
    throws IOException
  {
    if (this.zout == null) initArchive();

    ZipEntry currentEntry = new ZipEntry(filename);
    this.zout.putNextEntry(currentEntry);
    return new ZipEntryOutputStream(zout);
  }

  @Override
  public void writeUncompressedString(String name, String content)
    throws IOException
  {
    if (this.zout == null) initArchive();

    try
    {
      ZipEntry mime = new ZipEntry(name);
      mime.setMethod(ZipEntry.STORED);
      byte[] bytes = content.getBytes("UTF-8");
      mime.setSize(bytes.length);
      mime.setCompressedSize(bytes.length);
      CRC32 crc = new CRC32();
      crc.update(bytes);
      mime.setCrc(crc.getValue());
      zout.putNextEntry(mime);
      zout.write(bytes);
    }
    catch (UnsupportedEncodingException ue)
    {
      // cannot happen
    }
  }

  @Override
  public Writer createWriter(String output, String encoding)
    throws IOException
  {
    OutputStream out = createOutputStream(output);
    return EncodingUtil.createWriter(out, encoding);
  }

  @Override
  public Writer createWriter(File output, String encoding)
    throws IOException
  {
    OutputStream out = createOutputStream(output);
    return EncodingUtil.createWriter(out, encoding);
  }

  @Override
  public void done() throws IOException
  {
    if (this.zout != null)
    {
      zout.close();
    }
    if (baseOut != null)
    {
      baseOut.close();
    }
  }

}
