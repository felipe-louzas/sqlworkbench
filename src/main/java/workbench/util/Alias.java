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

import workbench.sql.lexer.SQLLexer;
import workbench.sql.lexer.SQLLexerFactory;
import workbench.sql.lexer.SQLToken;

/**
 * @author Thomas Kellerer
 */
public class Alias
{
  protected String objectName;
  private String alias;
  private String display;
  private String asKeyword;
  private int startPosInQuery = -1;
  private int endPosInQuery = -1;

  public Alias()
  {
  }

  public Alias(String name, String alias)
  {
    this.objectName = name;
    this.alias = StringUtil.trimToNull(alias);
  }

  public void setStartPositionInQuery(int pos)
  {
    this.startPosInQuery = pos;
  }

  public int getStartPositionInQuery()
  {
    return startPosInQuery;
  }

  public void setEndPositionInQuery(int pos)
  {
    this.endPosInQuery = pos;
  }

  public int getEndPositionInQuery()
  {
    return endPosInQuery;
  }

  /**
   * Create a new Alias
   * @param value  the SQL part that should be parsed
   */
  public Alias(String value)
  {
    if (StringUtil.isEmpty(value)) throw new IllegalArgumentException("Identifier must not be empty");

    SQLLexer lexer = SQLLexerFactory.createLexer(value);
    StringBuilder name = new StringBuilder(value.length());
    SQLToken t = lexer.getNextToken(false, true);
    boolean objectNamePart = true;
    while (t != null)
    {
      if (t.isWhiteSpace())
      {
        objectNamePart = false;
      }
      if (objectNamePart)
      {
        name.append(t.getText());
      }
      else if ("AS".equals(t.getContents()))
      {
        objectNamePart = false;
      }
      else
      {
        alias = t.getText();
      }
      t = lexer.getNextToken(false, true);
    }
    objectName = name.toString();
    display = value;
  }

  public void setAsKeyword(String text)
  {
    this.asKeyword = text;
  }

  public boolean isNotEmpty()
  {
    return StringUtil.isNotEmpty(objectName);
  }

  public void appendObjectName(String name)
  {
    if (objectName == null)
    {
      objectName = name;
    }
    else
    {
      objectName += name;
    }
  }

  public void setObjectName(String name)
  {
    this.objectName = name;
  }

  public void setAlias(String alias)
  {
    this.alias = StringUtil.trimToNull(alias);
  }

  /**
   * Returned the alias defined for the object.
   *
   * Might be null.
   *
   * @see #getAlias()
   */
  public final String getAlias()
  {
    return this.alias;
  }

  /**
   * Returns the object name that is aliased.
   *
   * @see #getAlias()
   */
  public final String getObjectName()
  {
    return objectName;
  }

  /**
   * Returns the alias expression.
   *
   * If the original expression was not an alias, this returns the same as {@link #getObjectName()}.
   *
   * @return
   */
  public String getName()
  {
    if (alias != null)
    {
      return StringUtil.concatWithSeparator(" ", objectName, asKeyword, alias);
    }
    return objectName;
  }

  /**
   * Returns the name that has to be used inside the SQL statement.
   * If an alias is defined, this will be the alias. The object name otherwise
   *
   * @see #getAlias()
   * @see #getObjectName()
   */
  public final String getNameToUse()
  {
    if (alias == null) return objectName;
    return alias;
  }

  @Override
  public String toString()
  {
    if (display == null)
    {
      if (alias == null) display = objectName;
      else display = alias + " (" + objectName + ")";
    }
    return display;
  }

  public String debugString()
  {
    return "Name: " + this.objectName + ", alias: " + StringUtil.coalesce(alias, "") + ", start: " + startPosInQuery + ", end: " + endPosInQuery;
  }

  @Override
  public int hashCode()
  {
    int hash = 7;
    hash = 47 * hash + (this.objectName != null ? this.objectName.hashCode() : 0);
    hash = 47 * hash + (this.alias != null ? this.alias.hashCode() : 0);
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
    final Alias other = (Alias) obj;
    if (StringUtil.equalStringIgnoreCase(this.objectName, other.objectName))
    {
      return StringUtil.equalStringIgnoreCase(this.alias, other.alias);
    }
    return false;
  }

}
