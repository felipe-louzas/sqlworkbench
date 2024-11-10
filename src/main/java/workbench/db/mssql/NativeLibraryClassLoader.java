/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2024 Thomas Kellerer
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
package workbench.db.mssql;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.util.ClasspathUtil;
import workbench.util.PlatformHelper;
import workbench.util.StringUtil;
import workbench.util.WbFile;

/**
 * A URL classloader that implements findLibrary() to find DLLs needed by the driver.
 *
 * This is mainly intended for SQL Servers's DLL that is needed for integreated security,
 * but can also be used for other drivers that need to load a DLL.
 *
 * @author Thomas Kellerer
 */
public class NativeLibraryClassLoader
  extends URLClassLoader
{
  private final File jardir;

  public NativeLibraryClassLoader(File jardir, URL[] urls, ClassLoader cl)
  {
    super(urls, cl);
    if (jardir.isDirectory())
    {
      this.jardir = jardir;
    }
    else
    {
      this.jardir = jardir.getParentFile();
    }
  }

  @Override
  protected String findLibrary(String libname)
  {
    String path = super.findLibrary(libname);
    if (path != null || libname == null)
    {
      return path;
    }

    String ext = PlatformHelper.isWindows() ? ".dll" : ".so";
    File dlldir = searchLibrary(libname, ext);
    if (dlldir != null)
    {
      WbFile f = new WbFile(dlldir, libname + ext);
      if (f != null) return f.getFullPath();
    }

    return null;
  }

  private File searchLibrary(String libName, String ext)
  {
    final CallerInfo ci = new CallerInfo(){};
    LogMgr.logInfo(ci, "Native library \"" + libName + "\" requested.");
    ClasspathUtil cp = new ClasspathUtil();

    String dllFile = libName + ext;

    List<File> searchPath = new ArrayList<>();
    searchPath.add(jardir); // this is the directory where the JDBC driver's jar is located

    // This is the directory layout that is created when extracting the downloaded ZIP file for SQL Server
    boolean is64Bit = System.getProperty("os.arch").equals("amd64");
    String archDir = is64Bit ? "x64" : "x86";

    WbFile authDir = new WbFile(jardir, "auth\\" + archDir);
    searchPath.add(authDir);
    searchPath.add(new WbFile(jardir, "..\\auth\\" + archDir));

    searchPath.add(cp.getExtDir());
    searchPath.add(cp.getJarDir()); // the directory where sqlworkbench.jar is located

    // for convenience add a subdirectory "dlls" of the directory where the jar file is located
    if (PlatformHelper.isWindows())
    {
      searchPath.add(new WbFile(jardir, Settings.getInstance().getProperty("workbench.driver.nativelibs.subdir.windows", "dlls")));
    }
    else if (PlatformHelper.isLinux())
    {
      searchPath.add(new WbFile(jardir, Settings.getInstance().getProperty("workbench.driver.nativelibs.subdir.linux", "so")));
    }

    // add directories from "java.library.path" at the end
    addLibraryPathDirectories(searchPath);

    for (File dir : searchPath)
    {
      if (dir.exists())
      {
        LogMgr.logDebug(ci, "Looking in \"" + dir.getAbsolutePath() + "\" for native library: " + libName);
        File lib = new File(dir, dllFile);
        if (lib.exists())
        {
          LogMgr.logInfo(ci, "Found native library: " + lib.getAbsolutePath());
          return dir;
        }
      }
    }
    return null;
  }

  private void addLibraryPathDirectories(List<File> searchPath)
  {
    String libPath = System.getProperty("java.library.path");
    if (StringUtil.isBlank(libPath)) return;

    String separator = StringUtil.quoteRegexMeta(File.pathSeparator);
    String[] pathElements = libPath.split(separator);
    for (String dir : pathElements)
    {
      if (StringUtil.isNotBlank(dir))
      {
        File fdir = new File(dir);
        if (fdir.exists())
        {
          searchPath.add(fdir);
        }
      }
    }
  }

}
