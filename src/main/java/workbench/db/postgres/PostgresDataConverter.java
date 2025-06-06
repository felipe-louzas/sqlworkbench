/*
 * Copyright 2002-2025 Thomas Kellerer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at.
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
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
package workbench.db.postgres;

import java.sql.Types;
import java.util.Map;

import workbench.storage.DataConverter;

/**
 *
 * @author Thomas Kellerer
 */
public class PostgresDataConverter
  implements DataConverter
{
  private static class LazyInstanceHolder
  {
    protected static final PostgresDataConverter INSTANCE = new PostgresDataConverter();
  }

  public static PostgresDataConverter getInstance()
  {
    return LazyInstanceHolder.INSTANCE;
  }

  private PostgresDataConverter()
  {
  }

  @Override
  public boolean convertsType(int jdbcType, String dbmsType)
  {
    return jdbcType == Types.OTHER && HstoreSupport.isHstoreType(dbmsType);
  }

  @Override
  public Object convertValue(int jdbcType, String dbmsType, Object originalValue)
  {
    if (originalValue == null) return null;

    if (convertsType(jdbcType, dbmsType))
    {
      // some driver versions return hstore values as a Map
      if (originalValue instanceof Map)
      {
        // HstoreMap implements toString() so that a valid hstore literal is generated
        return new HstoreMap((Map)originalValue);
      }
      else
      {
        return new HstoreMap(HstoreSupport.parseLiteral(originalValue.toString()));
      }
    }
    return originalValue;
  }

  @Override
  public Class getConvertedClass(int jdbcType, String dbmsType)
  {
    if (convertsType(jdbcType, dbmsType)) return Map.class;
    return null;
  }

}
