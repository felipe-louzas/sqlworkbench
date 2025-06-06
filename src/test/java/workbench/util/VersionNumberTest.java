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

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class VersionNumberTest
{

  @Test
  public void testPatchLevel()
  {
    VersionNumber one = new VersionNumber("2.1");
    VersionNumber two = new VersionNumber("2.1.1");
    assertTrue(two.isNewerThan(one));
    assertFalse(one.isNewerThan(two));

    one = new VersionNumber("2.1.1");
    two = new VersionNumber("2.1.4");
    assertTrue(two.isNewerThan(one));
    assertFalse(one.isNewerThan(two));

    one = new VersionNumber("2.1.0");
    two = new VersionNumber("2.1.1");
    assertTrue(two.isNewerThan(one));
    assertFalse(one.isNewerThan(two));

    one = new VersionNumber("114.13");
    two = new VersionNumber("114.13.1");
    assertTrue(two.isNewerOrEqual(one));

    one = new VersionNumber("115");
    two = new VersionNumber("114.13.1");
    assertTrue(one.isNewerOrEqual(two));
  }

  @Test
  public void testVersion()
  {
    VersionNumber one = new VersionNumber("94");
    assertEquals(94, one.getMajorVersion());
    assertEquals(-1, one.getMinorVersion());

    VersionNumber two = new VersionNumber("94.2");
    assertEquals(94, two.getMajorVersion());
    assertEquals(2, two.getMinorVersion());

    assertTrue(two.isNewerThan(one));
    assertFalse(one.isNewerThan(two));

    VersionNumber na = new VersionNumber(null);
    assertFalse(na.isNewerThan(two));
    assertTrue(two.isNewerThan(na));

    VersionNumber dev = new VersionNumber("@BUILD_NUMBER@");
    assertFalse(one.isNewerThan(dev));
    assertTrue(dev.isNewerThan(one));

    assertTrue(dev.isNewerThan(two));
    assertFalse(two.isNewerThan(dev));

    VersionNumber current = new VersionNumber("96.8");
    VersionNumber stable = new VersionNumber("97");
    assertTrue(stable.isNewerThan(current));
    assertFalse(current.isNewerThan(stable));

    VersionNumber v2 = new VersionNumber("96.9");
    assertTrue(v2.isNewerOrEqual(current));
    VersionNumber v3 = new VersionNumber("96.8");
    assertTrue(v3.isNewerOrEqual(current));
  }

  @Test
  public void testCleanup()
  {
    VersionNumber vn = new VersionNumber("3.5-FINAL-20090928");
    assertEquals(3, vn.getMajorVersion());
    assertEquals(5, vn.getMinorVersion());

    vn = new VersionNumber("16-rc6");
    assertEquals(16, vn.getMajorVersion());

    vn = new VersionNumber("16rc4");
    assertEquals(16, vn.getMajorVersion());

    vn = new VersionNumber("16-rc-1");
    assertEquals(16, vn.getMajorVersion());

    vn = new VersionNumber("16-beta1");
    assertEquals(16, vn.getMajorVersion());
  }
}
