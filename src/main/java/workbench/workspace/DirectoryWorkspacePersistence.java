/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2023 Thomas Kellerer.
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
package workbench.workspace;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.util.FileUtil;
import workbench.util.FileVersioner;
import workbench.util.WbFile;
import workbench.util.ZipUtil;

/**
 * An implementation of WorkpacePersistence that saves a workspace into a directory.
 *
 * @author Thomas Kellerer
 */
public class DirectoryWorkspacePersistence
  extends WorkspacePersistence
{
  private final File workspaceDir;

  public DirectoryWorkspacePersistence(String directoryName)
  {
    this.workspaceDir = new File(directoryName);
  }

  @Override
  public void openForWriting()
    throws IOException
  {
    clearDirectoryContent();
  }

  @Override
  public void openForReading()
    throws IOException
  {
  }

  @Override
  public void close()
  {
  }

  @Override
  public void removeEntry(String entryName)
  {
    File toRemove = new File(workspaceDir, entryName);
    toRemove.delete();
  }

  @Override
  public List<String> getEntries()
  {
    List<String> entries = new ArrayList<>();
    for (File f : workspaceDir.listFiles())
    {
      entries.add(f.getName());
    }
    return entries;
  }

  @Override
  public boolean isOutputValid()
  {
    return true;
  }

  @Override
  protected OutputStream createOutputStream(String entryName)
    throws IOException
  {
    File toWrite = new File(workspaceDir, entryName);
    return new FileOutputStream(toWrite);
  }

  @Override
  protected void closeEntryOutputStream(OutputStream out)
  {
    FileUtil.closeQuietely(out);
  }

  @Override
  protected InputStream createInputStream(String entryName)
    throws IOException
  {
    File toRead = new File(workspaceDir, entryName);
    if (!toRead.exists()) return null;
    return new FileInputStream(toRead);
  }

  @Override
  protected void closeEntryInputStream(InputStream in)
  {
    FileUtil.closeQuietely(in);
  }

  @Override
  public File createBackup()
  {
    int maxVersions = Settings.getInstance().getMaxBackupFiles();
    File backupDir = Settings.getInstance().getBackupDir();
    char sep = Settings.getInstance().getFileVersionDelimiter();

    if (backupDir == null)
    {
      backupDir = workspaceDir.getParentFile();
    }
    else
    {
      backupDir = new File(backupDir, "workspaces");
    }
    FileVersioner version = new FileVersioner(maxVersions, backupDir, sep);
    File zip = new File(backupDir, workspaceDir.getName() + "_backup.zip");
    File backup = version.getNextBackupFile(zip);
    createBackup(backup);
    return backup;
  }

  public void createBackup(File backupTarget)
  {
    try
    {
      ZipUtil.zipDirectory(workspaceDir, backupTarget);
    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not create backup zip of workspace: " + WbFile.getPathForLogging(workspaceDir), ex);
    }
  }

  @Override
  public void restoreBackup(File backup)
    throws IOException
  {
    if (backup == null || backup.exists()) return;
    clearDirectoryContent();
    ZipUtil.unzipToDirectory(backup, workspaceDir);
  }

  private void clearDirectoryContent()
  {
    if (workspaceDir.exists())
    {
      FileUtil.deleteDirectoryContent(workspaceDir);
    }
    else
    {
      workspaceDir.mkdirs();
    }
  }
}
