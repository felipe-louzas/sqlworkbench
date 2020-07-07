/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2020 Thomas Kellerer.
 *
 * Licensed under a modified Apache License, Version 2.0 (the "License")
 * that restricts the use for certain governments.
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.sql-workbench.eu/manual/license.html
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * To contact the author please send an email to: support@sql-workbench.eu
 */
package workbench.db.postgres;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import workbench.db.DbObject;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

import workbench.util.CollectionUtil;
import workbench.util.SqlUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class PgPublication
  implements DbObject, Serializable
{
  public static final String TYPE_NAME = "PUBLICATION";
  private String name;
  private String owner;
  private String comment;
  private boolean replicatesInserts;
  private boolean replicatesUpdates;
  private boolean replicatesTruncate;
  private boolean replicatesDeletes;
  private boolean includeAllTables;
  private boolean tablesInitialized;
  private List<TableIdentifier> tables = new ArrayList<>();

  public PgPublication(String name, String owner)
  {
    this.name = name;
    this.owner = owner;
  }

  @Override
  public String getCatalog()
  {
    return null;
  }

  @Override
  public String getSchema()
  {
    return null;
  }

  @Override
  public String getObjectType()
  {
    return TYPE_NAME;
  }

  @Override
  public String getObjectName(WbConnection conn)
  {
    return name;
  }

  @Override
  public String getObjectExpression(WbConnection conn)
  {
    return name;
  }

  @Override
  public String getFullyQualifiedName(WbConnection conn)
  {
    return name;
  }

  @Override
  public CharSequence getSource(WbConnection con)
    throws SQLException
  {
    String source = "CREATE PUBLICATION " + SqlUtil.quoteObjectname(name);
    if (includeAllTables)
    {
      source += "\n  FOR ALL TABLES";
    }
    else
    {
      String options = tables.stream().map(t -> t.getTableExpression(con)).collect(Collectors.joining(", "));
      source += "\n  FOR TABLE " + options;
    }

    if (!replicatesDeletes || !replicatesInserts || !replicatesTruncate || !replicatesUpdates)
    {
      source += "\n WITH (publish = '";
      int option = 0;
      if (replicatesInserts)
      {
        source += "insert";
        option ++;
      }
      if (replicatesUpdates)
      {
        if (option > 0) source += ", ";
        source += "update";
        option ++;
      }
      if (replicatesDeletes)
      {
        if (option > 0) source += ", ";
        source += "delete";
        option ++;
      }
      if (replicatesTruncate)
      {
        if (option > 0) source += ", ";
        source += "truncate";
        option ++;
      }
      source += "')";
    }
    return source;
  }

  @Override
  public String getObjectNameForDrop(WbConnection con)
  {
    return name;
  }

  @Override
  public String getComment()
  {
    return comment;
  }

  @Override
  public void setComment(String cmt)
  {
    this.comment = cmt;
  }

  @Override
  public String getDropStatement(WbConnection con, boolean cascade)
  {
    return "DROP PUBLICATION " + SqlUtil.quoteObjectname(name);
  }

  @Override
  public boolean supportsGetSource()
  {
    return true;
  }

  @Override
  public String getObjectName()
  {
    return name;
  }

  @Override
  public void setName(String name)
  {
    this.name = name;
  }

  public void setOwner(String owner)
  {
    this.owner = owner;
  }

  public void setReplicatesInserts(boolean replicatesInserts)
  {
    this.replicatesInserts = replicatesInserts;
  }

  public void setReplicatesUpdates(boolean replicatesUpdates)
  {
    this.replicatesUpdates = replicatesUpdates;
  }

  public void setReplicatesTruncate(boolean replicatesTruncate)
  {
    this.replicatesTruncate = replicatesTruncate;
  }

  public void setReplicatesDeletes(boolean replicatesDeletes)
  {
    this.replicatesDeletes = replicatesDeletes;
  }

  public void setIncludeAllTables(boolean includeAllTables)
  {
    this.includeAllTables = includeAllTables;
  }

  public boolean getTablesInitialized()
  {
    return tablesInitialized;
  }
  
  public void setTables(List<TableIdentifier> pubTables)
  {
    if (CollectionUtil.isEmpty(pubTables)) return;
    this.tables.clear();
    this.tables.addAll(pubTables);
    this.tablesInitialized = true;
  }

}
