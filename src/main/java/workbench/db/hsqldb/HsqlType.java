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
package workbench.db.hsqldb;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

import workbench.db.BaseObjectType;
import workbench.db.ColumnIdentifier;

/**
 *
 * @author Thomas Kellerer
 */
public class HsqlType
  extends BaseObjectType
  implements Serializable
{
  private String dataType;

  public HsqlType(String schema, String typeName)
  {
    super(schema, typeName);
  }

  public void setDataTypeName(String typeName)
  {
    dataType = typeName;
  }

  public String getDataTypeName()
  {
    return dataType;
  }

  @Override
  public List<ColumnIdentifier> getAttributes()
  {
    return Collections.emptyList();
  }

  @Override
  public void setAttributes(List<ColumnIdentifier> attr)
  {
  }
}
