/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
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
package workbench.db.hana;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.DbObject;
import workbench.db.JdbcUtils;
import workbench.db.ProcedureDefinition;
import workbench.db.RoutineType;
import workbench.db.SequenceDefinition;
import workbench.db.TableIdentifier;
import workbench.db.TriggerDefinition;
import workbench.db.WbConnection;
import workbench.db.dependency.DependencyReader;

import workbench.util.CollectionUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class HanaDependencyReader
  implements DependencyReader
{
  private final Set<String> supportedTypes = CollectionUtil.caseInsensitiveSet("table", "view", "sequence", "procedure", "function", "trigger");

  // dependency_type is documented as:
  // 0: NORMAL (default)
  // 1: EXTERNAL_DIRECT (direct dependency between dependent object and base object)
  // 2: EXTERNAL_INDIRECT (indirect dependency between dependent object und base object)
  // 5: REFERENTIAL_DIRECT (foreign key dependency between tables)
  private final String searchUsedBy =
    "select dependent_schema_name, dependent_object_name, dependent_object_type  \n" +
    "from sys.object_dependencies\n" +
    "where dependent_object_name not like '\\_SYS\\_TRIGGER\\_%' escape '\\' \n" +
    "  and dependency_type in (0,1) \n" +
    "  and base_schema_name = ? \n" +
    "  and base_object_name = ?";

  private final String searchIsUsing =
    "select base_schema_name, base_object_name, base_object_type \n" +
    "from sys.object_dependencies\n" +
    "where base_object_name not like '\\_SYS\\_TRIGGER\\_%' escape '\\' \n" +
    "  and dependency_type in (0,1) \n" +
    "  and dependent_schema_name = ? \n" +
    "  and dependent_object_name = ? ";

  @Override
  public List<DbObject> getUsedObjects(WbConnection connection, DbObject base)
  {
    return retrieveObjects(connection, base, searchIsUsing);
  }

  @Override
  public List<DbObject> getUsedBy(WbConnection connection, DbObject base)
  {
    return retrieveObjects(connection, base, searchUsedBy);
  }

  @Override
  public boolean supportsUsedByDependency(String objectType)
  {
    return supportedTypes.contains(objectType);
  }

  @Override
  public boolean supportsIsUsingDependency(String objectType)
  {
    return supportedTypes.contains(objectType);
  }

  private List<DbObject> retrieveObjects(WbConnection connection, DbObject base, String sql)
  {
    PreparedStatement pstmt = null;
    ResultSet rs = null;
    List<DbObject> result = new ArrayList<>();

    LogMgr.logMetadataSql(new CallerInfo(){}, "dependent objects", sql, base.getSchema(), base.getObjectName());

    boolean isTable = connection.getMetadata().isTableType(base.getObjectType());

    try
    {
      pstmt = connection.getSqlConnection().prepareStatement(sql);
      pstmt.setString(1, base.getSchema());
      pstmt.setString(2, base.getObjectName());

      rs = pstmt.executeQuery();
      while (rs.next())
      {
        String schema = rs.getString(1);
        String name = rs.getString(2);
        String type = rs.getString(3);

        // don't add triggers for tables, they will be shown seperately in the GUI
        if (isTable && "TRIGGER".equalsIgnoreCase(type)) continue;

        DbObject dbo = null;
        if (type.equals("PROCEDURE"))
        {
          dbo = new ProcedureDefinition(null, schema, name, RoutineType.procedure);
        }
        else if (type.equals("FUNCTION"))
        {
          dbo = new ProcedureDefinition(null, schema, name, RoutineType.function);
        }
        else if (type.equals("TRIGGER"))
        {
          dbo = new TriggerDefinition(null, schema, name);
        }
        else if (type.equals("SEQUENCE"))
        {
          dbo = new SequenceDefinition(schema, name);
        }
        else
        {
          TableIdentifier tbl = new TableIdentifier(null, schema, name);
          tbl.setNeverAdjustCase(true);
          tbl.setType(type);
          dbo = tbl;
        }
        result.add(dbo);
      }
    }
    catch (Exception ex)
    {
      LogMgr.logMetadataError(new CallerInfo(){}, ex, "dependent objects", sql, base.getSchema(), base.getObjectName());
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }

    return result;
  }

}
