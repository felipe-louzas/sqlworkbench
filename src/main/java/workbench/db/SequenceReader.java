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
package workbench.db;

import java.sql.SQLException;
import java.util.List;

import workbench.storage.DataStore;

/**
 * Read the definition of sequences from the database
 * @author Thomas Kellerer
 */
public interface SequenceReader
{
  /**
   * The property name for the sequence increment.
   */
  public static final String PROP_INCREMENT = "increment";
  public static final String PROP_CACHE_SIZE = "cache";
  public static final String PROP_IS_CACHED = "is_cached";
  public static final String PROP_IS_GENERATED = "is_generated";
  public static final String PROP_CYCLE = "cycle";
  public static final String PROP_MIN_VALUE = "min_value";
  public static final String PROP_MAX_VALUE = "max_value";
  public static final String PROP_OWNED_BY = "owned_by";
  public static final String PROP_ORDERED = "ordered";
  public static final String PROP_START_VALUE = "start_value";
  public static final String PROP_DATA_TYPE = "data_type";
  public static final String PROP_USER_DATA_TYPE = "user_data_type";
  public static final String PROP_CURRENT_VALUE = "current_value";
  public static final String PROP_PRECISION = "precision";
  public static final String PROP_LAST_VALUE = "last_value";

  String DEFAULT_TYPE_NAME = "SEQUENCE";

  /**
   *  Return a SQL String to recreate the given sequence
   */
  CharSequence getSequenceSource(String catalog, String schema, String sequence, GenerationOptions options);

  default CharSequence getSequenceSource(SequenceDefinition seq, GenerationOptions options)
  {
    if (seq == null) return null;
    return getSequenceSource(seq.getCatalog(), seq.getSchema(), seq.getSequenceName(), options);
  };

  /**
   *  Get a list of sequences for the given owner.
   */
  List<SequenceDefinition> getSequences(String catalogPattern, String schemaPattern, String namePattern)
    throws SQLException;

  SequenceDefinition getSequenceDefinition(String catalog, String schema, String sequence);
  DataStore getRawSequenceDefinition(String catalog, String schema, String sequence);

  String getSequenceTypeName();

}
