/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0 (the "License")
 * that restricts the use for certain governments.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.sql-workbench.eu/manual/license.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.eu
 */
package workbench.util;

import java.io.File;

/**
 *
 * @author Thomas Kellerer
 */
public class ClassInfo
{
  private final String className;
  private final File jarFile;

  public ClassInfo(String className, File jarFile)
  {
    this.className = className;
    this.jarFile = jarFile;
  }

  public String getClassName()
  {
    return className;
  }

  public File getJarFile()
  {
    return jarFile;
  }

  @Override
  public String toString()
  {
    return "ClassInfo{" + "className=" + className + ", jarFile=" + jarFile + '}';
  }

}
