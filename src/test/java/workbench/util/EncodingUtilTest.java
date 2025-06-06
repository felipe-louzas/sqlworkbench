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

import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author tkellerer
 */
public class EncodingUtilTest
{
  @Test
  public void testCleanupEncoding()
  {
    assertEquals("UTF-8", EncodingUtil.cleanupEncoding("utf"));
    assertEquals("UTF-8", EncodingUtil.cleanupEncoding("utf-8"));
    assertEquals("UTF-8", EncodingUtil.cleanupEncoding("UTF-8"));
    assertEquals("UTF-16", EncodingUtil.cleanupEncoding("UTF-16"));
    assertEquals("UTF-32", EncodingUtil.cleanupEncoding("UTF-32"));
    assertEquals("UTF-8", EncodingUtil.cleanupEncoding("utf8"));
    assertEquals("UTF-16", EncodingUtil.cleanupEncoding("utf16"));
    assertEquals("UTF-16BE", EncodingUtil.cleanupEncoding("utf16be"));
    assertEquals("UTF-16LE", EncodingUtil.cleanupEncoding("utf16LE"));
    assertEquals("UTF-32", EncodingUtil.cleanupEncoding("utf32"));
    assertEquals("UTF-32BE", EncodingUtil.cleanupEncoding("utf32be"));
    assertEquals("UTF-32LE", EncodingUtil.cleanupEncoding("utf32Le"));
    assertEquals("UTF-32LE", EncodingUtil.cleanupEncoding("utf-32Le"));
    assertEquals("ISO-8859-1", EncodingUtil.cleanupEncoding("iso88591"));
    assertEquals("ISO-8859-15", EncodingUtil.cleanupEncoding("iso885915"));
    assertEquals("WIN1251", EncodingUtil.cleanupEncoding("win1251"));
  }
}
