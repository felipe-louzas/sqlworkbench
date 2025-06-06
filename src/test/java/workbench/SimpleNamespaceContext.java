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
package workbench;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.xml.XMLConstants;
import javax.xml.namespace.NamespaceContext;


/**
 *
 * @author Thomas Kellerer
 */
public class SimpleNamespaceContext
  implements NamespaceContext
{

  private Map<String, String> namespaceMap;

  public SimpleNamespaceContext(Map<String, String> nameMap)
  {
    namespaceMap = new HashMap<>(nameMap);
    namespaceMap.put(XMLConstants.XML_NS_PREFIX, XMLConstants.XML_NS_URI);
    namespaceMap.put(XMLConstants.XMLNS_ATTRIBUTE, XMLConstants.XMLNS_ATTRIBUTE_NS_URI);
  }

  @Override
  public String getNamespaceURI(String prefix)
  {
    String uri = namespaceMap.get(prefix);
    return (uri == null ? XMLConstants.XML_NS_URI : uri);
  }

  @Override
  public String getPrefix(String namespaceURI)
  {
    if (namespaceURI == null) return null;

    for (Entry<String, String> entry : namespaceMap.entrySet())
    {
      if (entry.getValue().equals(namespaceURI)) return entry.getKey();
    }
    return null;
  }

  @Override
  public Iterator<String> getPrefixes(String namespaceURI)
  {
    List<String> prefixes = new ArrayList<>();

    for ( String prefix : namespaceMap.keySet())
    {
      if (namespaceMap.get(prefix).equals(namespaceURI))
      {
        prefixes.add( prefix);
      }
    }
    return prefixes.iterator();
  }

}
