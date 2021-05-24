/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2021 Thomas Kellerer.
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
import java.util.Date;
import java.util.List;

import workbench.TestUtil;
import workbench.WbTestCase;

import workbench.gui.sql.PanelType;
import workbench.gui.sql.SqlHistory;

import workbench.util.FileVersioner;
import workbench.util.WbFile;
import workbench.util.WbProperties;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class WorkspaceBackupListTest
  extends WbTestCase
{

  public WorkspaceBackupListTest()
  {
    super("WorkspaceBackupListTest");
  }

  @Test
  public void testListBackups()
    throws Exception
  {
    TestUtil util = getTestUtil();
    util.emptyBaseDirectory();

    String baseDir = util.getBaseDir();
    File backupDir = new File(baseDir, "backup");
    backupDir.mkdirs();

    WbFile workspaceFile = new WbFile(baseDir, "testworkspace.wksp");
    try ( WbWorkspace wksp1 = createWorkspace(2, workspaceFile.getFullPath()))
    {
      wksp1.openForWriting();
      wksp1.save();
    }

    FileVersioner version = new FileVersioner(5, backupDir.getAbsolutePath(), '.');
    File backup1 = version.createBackup(workspaceFile);

    try ( WbWorkspace wksp2 = createWorkspace(3, workspaceFile.getFullPath()))
    {
      wksp2.openForWriting();
      wksp2.save();
    }

    version.createBackup(workspaceFile);

    long time = new Date().getTime();
    time -= 1000 * 60 * 10;
    backup1.setLastModified(time);

    WorkspaceBackupList list = new WorkspaceBackupList(workspaceFile, backupDir);
    List<File> backups = list.getBackups();
    assertEquals(2, backups.size());
    assertTrue(backups.get(0).getName().endsWith(".2"));
  }

  private WbWorkspace createWorkspace(int entries, String fname)
  {
    WbWorkspace wksp = new WbWorkspace(fname);
    wksp.setEntryCount(entries);
    wksp.setSelectedTab(0);
    WbProperties props = wksp.getSettings();

    for (int i = 0; i < entries; i++)
    {
      SqlHistory h1 = new SqlHistory(10);
      h1.addContent("-- entry one", 1, 0, 0);
      h1.addContent("-- entry two", 1, 0, 0);
      wksp.addHistoryEntry(i, h1);
      wksp.setTabTitle(i, "Tab " + (i + 1));
      props.setProperty(WbWorkspace.TAB_PROP_PREFIX + i + ".type", PanelType.sqlPanel.toString());
    }
    return wksp;
  }

}
