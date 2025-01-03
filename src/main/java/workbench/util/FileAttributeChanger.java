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
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;


/**
 *
 * @author Thomas Kellerer
 */
public class FileAttributeChanger
{

  public void removeHidden(File dir)
  {
    if (PlatformHelper.isWindows())
    {
      removeAttribute(dir);
    }
  }

  private void removeAttribute(File dir)
  {
    try
    {
      Path file = dir.toPath();
      Files.setAttribute(file, "dos:hidden", false, LinkOption.NOFOLLOW_LINKS);
    }
    catch (Throwable th)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not remove hidden attribute of config dir: " + dir.getAbsolutePath(), th);
    }
  }

}
