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
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class ClassFinderTest
{

  public ClassFinderTest()
  {
  }

  @Test
  public void testFindClass()
    throws Exception
  {
    String path = System.getProperty("java.class.path");
    List<String> elements = StringUtil.stringToList(path, System.getProperty("path.separator"));
    List<File> toSearch = new ArrayList<>();
    for (String entry : elements)
    {
      if (entry.endsWith(".jar") &&
        !entry.contains("poi") &&
        !entry.contains("jemmy") &&
        !entry.contains("log4j") &&
        !entry.contains("ant") &&
        !entry.contains("junit-4.8"))
      {
        toSearch.add(new File(entry));
      }
    }
    ClassFinder finder = new ClassFinder(java.sql.Driver.class);
    List<String> drivers = finder.findImplementations(toSearch);
    assertTrue(drivers.size() >= 5);
  }

}
