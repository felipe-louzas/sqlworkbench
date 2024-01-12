/*
 * This file is part of SQL Workbench/J, http://www.sql-workbench.net
 *
 * Copyright 2002-2024 Thomas Kellerer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at.
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.net
 *
 */
package workbench.util;

import java.util.Collections;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 * @author Thomas Kellerer
 */
public class NaturalOrderComparatorTest
{

  @Test
  public void testDefaultSort()
  {
    NaturalOrderComparator comp = new NaturalOrderComparator(true);
    List<String> values = CollectionUtil.arrayList("zzz", "vvv", "bbb", "aaa");
    values.sort(comp);
    assertEquals("aaa", values.get(0));
    assertEquals("bbb", values.get(1));
    assertEquals("vvv", values.get(2));
    assertEquals("zzz", values.get(3));
  }

  @Test
  public void testNaturalSort()
  {
    NaturalOrderComparator comp = new NaturalOrderComparator(true);
    List<String> values = CollectionUtil.arrayList("a100", "a1", "A20");
    Collections.sort(values, comp);
    assertEquals("a1", values.get(0));
    assertEquals("A20", values.get(1));
    assertEquals("a100", values.get(2));
  }

}
