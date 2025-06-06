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
import java.io.Writer;

/**
 *
 * @author Thomas Kellerer
 */
public class DefaultOutputFactory
  implements OutputFactory
{

  @Override
  public boolean isArchive()
  {
    return false;
  }

  @Override
  public OutputStream createOutputStream(File output)
    throws IOException
  {
    return new FileOutputStream(output);
  }

  @Override
  public Writer createWriter(File output, String encoding)
    throws IOException
  {
    OutputStream out = createOutputStream(output);
    return EncodingUtil.createWriter(out, encoding);
  }

  @Override
  public Writer createWriter(String filename, String encoding)
    throws IOException
  {
    return createWriter(new File(filename), encoding);
  }

  @Override
  public void done()
    throws IOException
  {
  }

  @Override
  public void writeUncompressedString(String name, String content)
    throws IOException
  {
    try (Writer w = createWriter(name, "UTF-8"))
    {
      w.write(content);
    }
  }

}
