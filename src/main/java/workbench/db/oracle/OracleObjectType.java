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
package workbench.db.oracle;

import java.io.Serializable;

import workbench.db.BaseObjectType;
import workbench.db.DbObject;
import workbench.db.WbConnection;

import workbench.util.CollectionUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class OracleObjectType
  extends BaseObjectType
  implements Serializable
{
  private int numMethods;
  private int numAttributes;

  public OracleObjectType(String owner, String objectName)
  {
    super(owner, objectName);
  }

  @Override
  public String getDropStatement(WbConnection con, boolean cascade)
  {
    return null;
  }

  public void setNumberOfMethods(int count)
  {
    this.numMethods = count;
  }

  public int getNumberOfMethods()
  {
    return numMethods;
  }

  public void setNumberOfAttributes(int count)
  {
    this.numAttributes = count;
  }

  @Override
  public int getNumberOfAttributes()
  {
    if (CollectionUtil.isEmpty(getAttributes()))
    {
      return numAttributes;
    }
    return getAttributes().size();
  }

  @Override
  public boolean isEqualTo(DbObject other)
  {
    boolean isEqual = super.isEqualTo(other);
    if (isEqual && (other instanceof OracleObjectType))
    {
      OracleObjectType otherType = (OracleObjectType)other;
      isEqual = this.getNumberOfMethods() == otherType.getNumberOfMethods();
    }
    return isEqual;
  }

}
