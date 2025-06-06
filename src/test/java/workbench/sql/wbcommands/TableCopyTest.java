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
package workbench.sql.wbcommands;

import java.util.Map;
import workbench.util.ArgumentParser;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Thomas Kellerer
 */
public class TableCopyTest
{

  @Test
  public void testParseMapping()
  {
    TableCopy copy = new TableCopy();
    String cmdline = "-columns='Time/\"Time\", Intrvl/\"Intrvl\"'";

    ArgumentParser parser = new ArgumentParser();
    parser.addArgument(WbCopy.PARAM_COLUMNS);
    parser.parse(cmdline);

    Map<String, String> map = copy.parseMapping(parser);
    for (Map.Entry<String, String> entry : map.entrySet())
    {
      assertEquals("\"" + entry.getKey() + "\"", entry.getValue());
    }

  }
}
