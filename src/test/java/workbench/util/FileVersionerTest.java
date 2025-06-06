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
import java.util.List;

import workbench.TestUtil;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class FileVersionerTest
{

  @Test
  public void testGetVersion()
  {
    File f = new File("mystuff.wksp.1");
    assertEquals(1, FileVersioner.getFileVersion(f, '.'));

    f = new File("mystuff.wksp;42");
    assertEquals(42, FileVersioner.getFileVersion(f, ';'));
  }

  @Test
  public void testBackupDir()
    throws Exception
  {
    TestUtil util = new TestUtil("FileVersioner");
    File bckDir = new File(util.getBaseDir(), "backup");
    FileVersioner v = new FileVersioner(3, bckDir, ';');
    util.emptyBaseDirectory();
    File main = new File(util.getBaseDir(), "mystuff.conf");
    TestUtil.writeFile(main, "this is a test\n");
    v.createBackup(main);
    File bck = new File(bckDir, "mystuff.conf;1");
    assertTrue(bck.exists());
  }

  @Test
  public void testCreateBackup()
    throws Exception
  {
    FileVersioner v = new FileVersioner(3);
    TestUtil util = new TestUtil("FileVersioner");
    util.emptyBaseDirectory();
    File main = new File(util.getBaseDir(), "mystuff.conf");
    TestUtil.writeFile(main, "this is a test\n");

    v.createBackup(main);
    TestUtil.writeFile(main, "1 this is a test\n");
    File bck = new File(util.getBaseDir(), "mystuff.conf.1");
    assertTrue(bck.exists());
    List<String> lines = TestUtil.readLines(bck);
    assertEquals("this is a test", lines.get(0));

    // second backup
    v.createBackup(main);
    TestUtil.writeFile(main, "2 this is a test\n");
    bck = new File(util.getBaseDir(), "mystuff.conf.2");
    assertTrue(bck.exists());
    lines = TestUtil.readLines(bck);
    assertEquals("1 this is a test", lines.get(0));

    // test that version 1 is still the original file
    bck = new File(util.getBaseDir(), "mystuff.conf.1");
    assertTrue(bck.exists());
    lines = TestUtil.readLines(bck);
    assertEquals("this is a test", lines.get(0));

    // third backup, still no rollover expected
    v.createBackup(main);
    TestUtil.writeFile(main, "3 this is a test\n");
    bck = new File(util.getBaseDir(), "mystuff.conf.3");
    assertTrue(bck.exists());
    lines = TestUtil.readLines(bck);
    assertEquals("2 this is a test", lines.get(0));

    // version two and one should still contain the same contents
    bck = new File(util.getBaseDir(), "mystuff.conf.1");
    assertTrue(bck.exists());
    lines = TestUtil.readLines(bck);
    assertEquals("this is a test", lines.get(0));

    bck = new File(util.getBaseDir(), "mystuff.conf.2");
    assertTrue(bck.exists());
    lines = TestUtil.readLines(bck);
    assertEquals("1 this is a test", lines.get(0));

    // forth backup, rollover expected
    v.createBackup(main);
    TestUtil.writeFile(main, "4 this is a test\n");
    for (int i=1; i <= 3; i++)
    {
      bck = new File(util.getBaseDir(), "mystuff.conf." + i);
      assertTrue(bck.exists());
      lines = TestUtil.readLines(bck);
      assertEquals(i + " this is a test", lines.get(0));
    }
    util.emptyBaseDirectory();
  }
}
