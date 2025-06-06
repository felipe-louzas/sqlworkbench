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

import java.io.Serializable;

/**
 *
 * @author Thomas Kellerer
 */
public class ConstraintDefinition
  implements Serializable
{
  private String constraintName;
  private Boolean deferrable;
  private Boolean initiallyDeferred;
  private String comment;
  private boolean isSystemName;
  private ConstraintType type;

  // mainly for Oracle
  private boolean enabled = true;
  private boolean valid = true;

  public ConstraintDefinition(String name)
  {
    this.constraintName = name;
  }

  public static ConstraintDefinition createUniqueConstraint(String name)
  {
    ConstraintDefinition cons = new ConstraintDefinition(name);
    cons.setConstraintType(ConstraintType.Unique);
    return cons;
  }

  public ConstraintType getConstraintType()
  {
    return type;
  }

  public void setConstraintType(ConstraintType type)
  {
    this.type = type;
  }

  public String getConstraintName()
  {
    return constraintName;
  }

  public void setConstraintName(String constraintName)
  {
    this.constraintName = constraintName;
  }

  public boolean isEnabled()
  {
    return enabled;
  }

  public void setEnabled(boolean flag)
  {
    this.enabled = flag;
  }

  public Boolean isValid()
  {
    return valid;
  }

  public void setValid(boolean flag)
  {
    this.valid = flag;
  }

  public boolean isDeferred()
  {
    return deferrable == null ? false : deferrable;
  }

  public Boolean isDeferrable()
  {
    return deferrable;
  }

  public void setDeferrable(boolean deferrable)
  {
    this.deferrable = deferrable;
  }

  public Boolean isInitiallyDeferred()
  {
    return initiallyDeferred;
  }

  public void setInitiallyDeferred(boolean initiallyDeferred)
  {
    this.initiallyDeferred = initiallyDeferred;
  }

  public String getComment()
  {
    return comment;
  }

  public void setComment(String comment)
  {
    this.comment = comment;
  }

  public void setIsSystemName(boolean flag)
  {
    this.isSystemName = flag;
  }

  public boolean isSystemName()
  {
    return this.isSystemName;
  }

  @Override
  public String toString()
  {
    return (type != null ? type.toString() + " constraint: " : "") + getConstraintName();
  }

  @Override
  public int hashCode()
  {
    int hash = 3;
    hash = 79 * hash + (this.constraintName != null ? this.constraintName.hashCode() : 0);
    hash = 79 * hash + (this.type != null ? this.type.hashCode() : 0);
    return hash;
  }

  @Override
  public boolean equals(Object obj)
  {
    if (obj == null)
    {
      return false;
    }
    if (getClass() != obj.getClass())
    {
      return false;
    }
    final ConstraintDefinition other = (ConstraintDefinition) obj;
    if ((this.constraintName == null) ? (other.constraintName != null) : !this.constraintName.equals(other.constraintName))
    {
      return false;
    }
    if (this.type != other.type)
    {
      return false;
    }
    return true;
  }


}
