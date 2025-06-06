/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
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
package workbench.db.mssql;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;

import workbench.db.CatalogChanger;
import workbench.db.DbObject;
import workbench.db.DomainIdentifier;
import workbench.db.JdbcUtils;
import workbench.db.ProcedureDefinition;
import workbench.db.ProcedureReader;
import workbench.db.RoutineType;
import workbench.db.TableIdentifier;
import workbench.db.TriggerDefinition;
import workbench.db.TriggerReader;
import workbench.db.WbConnection;
import workbench.db.dependency.DependencyReader;

import workbench.gui.dbobjects.objecttree.DbObjectSorter;

import workbench.util.CollectionUtil;
import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class SqlServerDependencyReader
  implements DependencyReader
{

  private final Set<String> supportedTypes = CollectionUtil.caseInsensitiveSet("table", "view", "procedure", "function", "trigger");

  private final String typeDesc =
      "       case ao.type_desc \n" +
      "          when 'USER_TABLE' then 'TABLE'\n" +
      "          when 'SYSTEM_TABLE' then 'SYSTEM TABLE'\n" +
      "          when 'INTERNAL_TABLE' then 'SYSTEM TABLE'\n" +
      "          when 'SQL_STORED_PROCEDURE' then 'PROCEDURE'\n" +
      "          when 'CLR_STORED_PROCEDURE' then 'PROCEDURE'\n" +
      "          when 'EXTENDED_STORED_PROCEDURE' then 'PROCEDURE'\n" +
      "          when 'SQL_SCALAR_FUNCTION' then 'FUNCTION'\n" +
      "          when 'CLR_SCALAR_FUNCTION' then 'FUNCTION'\n" +
      "          when 'SQL_TABLE_VALUED_FUNCTION' then 'FUNCTION'\n" +
      "          when 'SQL_INLINE_TABLE_VALUED_FUNCTION' then 'FUNCTION'\n" +
      "          when 'SQL_TRIGGER' then 'TRIGGER'\n" +
      "          when 'CLR_TRIGGER' then 'TRIGGER'\n" +
      "          else type_desc \n" +
      "        end as type";

  private final String searchUsesInfSchema =
      "SELECT vtu.TABLE_CATALOG, vtu.TABLE_SCHEMA, vtu.TABLE_NAME,\n" + typeDesc + "\n" +
      "FROM INFORMATION_SCHEMA.VIEW_TABLE_USAGE vtu \n" +
      "  JOIN sys.all_objects ao ON ao.name = vtu.TABLE_NAME and schema_name(ao.schema_id) = vtu.TABLE_SCHEMA\n" +
      "WHERE VIEW_CATALOG = ? \n" +
      "  AND VIEW_SCHEMA = ? \n" +
      "  AND VIEW_NAME = ?";

  private final String searchUsedBySqlInfSchema =
      "SELECT vtu.VIEW_CATALOG, vtu.VIEW_SCHEMA, vtu.VIEW_NAME,\n" + typeDesc  + "\n" +
      "FROM INFORMATION_SCHEMA.VIEW_TABLE_USAGE vtu \n" +
      "  JOIN sys.all_objects ao ON ao.name = vtu.VIEW_NAME and schema_name(ao.schema_id) = vtu.VIEW_SCHEMA\n" +
      "WHERE TABLE_CATALOG = ? \n" +
      "  AND TABLE_SCHEMA = ? \n" +
      "  AND TABLE_NAME = ?";

  private final String searchUsesDMView =
      "SELECT distinct db_name() as catalog_name,  \n" +
      "       coalesce(re.referenced_schema_name, schema_name()) as schema_name,  \n" +
      "       re.referenced_entity_name,  \n" + typeDesc  + "\n" +
      "FROM sys.dm_sql_referenced_entities(?, 'OBJECT') re \n" +
      "  JOIN sys.all_objects ao on ao.object_id = re.referenced_id";

  private final String searchDefaultConstraints =
      "SELECT db_name() as catalog_name, \n" +
      "       schema_name(ao.schema_id) as schema_name,\n" +
      "       ao.name as constraint_name, \n" +
      "       'DEFAULT' as type \n" +
      "from sys.columns c \n" +
      "  join sys.all_objects ao on ao.object_id = c.default_object_id \n" +
      "where c.object_id = object_id(?)\n" +
      "  and ao.type = 'D'\n" +
      "  and coalesce(ao.parent_object_id,0) = 0;";

  private final String searchColumnTypes =
      "select distinct db_name() as catalog_name, \n" +
      "       schema_name(t.schema_id) as schema_name,\n" +
      "       t.name as type_name, \n" +
      "       'TYPE' as type\n" +
      "from sys.types t \n" +
      "  join sys.columns c on c.user_type_id = t.user_type_id \n" +
      "where c.object_id = object_id(?)\n" +
      "and t.is_user_defined = 1";

  private final String searchUsedBySqlDMView =
      "SELECT db_name() as catalog,  \n" +
      "       coalesce(re.referencing_schema_name,schema_name()) as schema_name,  \n" +
      "       re.referencing_entity_name, \n" + typeDesc + ", \n" +
      "        case \n" +
      "          when ao.type_desc like '%TRIGGER%' then\n" +
      "             case\n" +
      "                when ObjectProperty(ao.object_id, 'ExecIsUpdateTrigger') = 1 then 'UPDATE'\n" +
      "                when ObjectProperty(ao.object_id, 'ExecIsDeleteTrigger') = 1 then 'DELETE'\n" +
      "                when ObjectProperty(ao.object_id, 'ExecIsInsertTrigger') = 1 then 'INSERT'\n" +
      "             end\n" +
      "        end as trigger_event,\n" +
      "        case \n" +
      "          when ao.type_desc like '%TRIGGER%' then\n" +
      "             case\n" +
      "                when ObjectProperty(ao.object_id, 'ExecIsAfterTrigger') = 1 then 'AFTER'\n" +
      "                when ObjectProperty(ao.object_id, 'ExecIsInsteadOfTrigger') = 1 then 'INSTEAD OF'\n" +
      "                else 'BEFORE'\n" +
      "             end \n" +
      "         end as trigger_type \n" +
      "FROM sys.dm_sql_referencing_entities(?, 'OBJECT') re \n" +
      "  JOIN sys.all_objects ao on ao.object_id = re.referencing_id";

  private final CatalogChanger catalogChanger = new CatalogChanger();

  public SqlServerDependencyReader()
  {
    catalogChanger.setFireEvents(false);
  }

  @Override
  public List<DbObject> getUsedObjects(WbConnection connection, DbObject base)
  {
    if (base == null || connection == null) return Collections.emptyList();

    String oldCatalog = changeDatabase(connection, base.getCatalog());
    List<DbObject> result = null;

    try
    {
      if (connection.getDbSettings().getBoolProperty("dependency.use.infoschema", false))
      {
        result = retrieveObjects(connection, base, searchUsesInfSchema, false);
      }
      else
      {
        result = retrieveObjects(connection, base, searchUsesDMView, true);
      }

      if (connection.getMetadata().isTableType(base.getObjectType()))
      {
        List<DbObject> defaults = retrieveObjects(connection, base, searchDefaultConstraints, true);
        result.addAll(defaults);

        List<DbObject> types = retrieveObjects(connection, base, searchColumnTypes, true);
        result.addAll(types);
      }
      if (SqlServerUtil.supportsPartitioning(connection) && base instanceof TableIdentifier)
      {
        SqlServerPartitionReader reader = new SqlServerPartitionReader(connection);
        PartitionScheme scheme = reader.getSchemeForTable((TableIdentifier)base);
        if (scheme != null)
        {
          result.add(scheme);
          PartitionFunction func = reader.getFunctionForTable((TableIdentifier)base);
          result.add(func);
        }
      }
    }
    finally
    {
      changeDatabase(connection, oldCatalog);
    }
    return result;
  }

  @Override
  public List<DbObject> getUsedBy(WbConnection connection, DbObject base)
  {
    if (base == null || connection == null) return Collections.emptyList();

    String oldCatalog = changeDatabase(connection, base.getCatalog());
    try
    {
      if (connection.getDbSettings().getBoolProperty("dependency.use.infoschema", false))
      {
        return retrieveObjects(connection, base, searchUsedBySqlInfSchema, false);
      }
      return retrieveObjects(connection, base, searchUsedBySqlDMView, true);
    }
    finally
    {
      changeDatabase(connection, oldCatalog);
    }
  }

  private String changeDatabase(WbConnection conn, String catalog)
  {
    if (catalog == null || conn == null) return catalog;

    String currentCatalog = conn.getCurrentCatalog();
    try
    {
      if (StringUtil.stringsAreNotEqual(catalog, currentCatalog))
      {
        catalogChanger.setCurrentCatalog(conn, catalog);
      }
    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not change database", ex);
    }
    return currentCatalog;
  }

  private List<DbObject> retrieveObjects(WbConnection connection, DbObject base, String sql, boolean useFQN)
  {
    PreparedStatement pstmt = null;
    ResultSet rs = null;

    List<DbObject> result = new ArrayList<>();

    String fqName = buildFQName(connection, base);

    if (useFQN)
    {
      LogMgr.logMetadataSql(new CallerInfo(){}, "dependent objects", sql, fqName);
    }
    else
    {
      LogMgr.logMetadataSql(new CallerInfo(){}, "dependent objects", sql, base.getCatalog(), base.getSchema(), base.getObjectName(), base.getObjectType());
    }

    try
    {
      pstmt = connection.getSqlConnection().prepareStatement(sql);
      if (useFQN)
      {
        pstmt.setString(1, fqName);
      }
      else
      {
        pstmt.setString(1, base.getCatalog());
        pstmt.setString(2, base.getSchema());
        pstmt.setString(3, base.getObjectName());
      }

      rs = pstmt.executeQuery();

      boolean hasTriggerDetails = rs.getMetaData().getColumnCount() > 4;

      while (rs.next())
      {
        String catalog = rs.getString(1);
        String schema = rs.getString(2);
        String name = rs.getString(3);
        String type = rs.getString(4);

        DbObject dbo = null;
        if (type.equals(ProcedureReader.TYPE_NAME_PROC))
        {
          dbo = new ProcedureDefinition(catalog, schema, name, RoutineType.procedure);
        }
        else if (type.equals(ProcedureReader.TYPE_NAME_FUNC))
        {
          dbo = new ProcedureDefinition(catalog, schema, name, RoutineType.function);
        }
        else if (type.equals(TriggerReader.TYPE_NAME))
        {
          TriggerDefinition trg = new TriggerDefinition(catalog, schema, name);
          if (hasTriggerDetails)
          {
            String event = rs.getString("trigger_event");
            String trgType = rs.getString("trigger_type");
            trg.setTriggerEvent(event);
            trg.setTriggerType(trgType);
          }
          dbo = trg;
        }
        else if (type.equals("DEFAULT"))
        {
          dbo = new NamedDefault(catalog, schema, name);
        }
        else if (type.equals("TYPE"))
        {
          DomainIdentifier domain = new DomainIdentifier(catalog, schema, name);
          domain.setObjectType("TYPE");
          dbo = domain;
        }
        else
        {
          TableIdentifier tbl = new TableIdentifier(catalog, schema, name);
          tbl.setNeverAdjustCase(true);
          tbl.setType(type);
          dbo = tbl;
        }
        result.add(dbo);
      }
    }
    catch (Exception ex)
    {
      if (useFQN)
      {
        LogMgr.logMetadataError(new CallerInfo(){}, ex, "dependent objects", sql, fqName);
      }
      else
      {
        LogMgr.logMetadataError(new CallerInfo(){}, ex, "dependent objects", sql, base.getCatalog(), base.getSchema(), base.getObjectName(), base.getObjectType());
      }
    }
    finally
    {
      JdbcUtils.closeAll(rs, pstmt);
    }

    DbObjectSorter sorter = new DbObjectSorter(true);
    sorter.setIncludeType(true);
    Collections.sort(result, sorter);

    return result;
  }

  private String buildFQName(WbConnection conn, DbObject dbo)
  {
    if (dbo == null) return null;
    String schema = conn.getMetadata().quoteObjectname(dbo.getSchema());
    String name = conn.getMetadata().quoteObjectname(dbo.getObjectName());
    if (StringUtil.isEmpty(schema))
    {
      schema = conn.getMetadata().quoteObjectname(conn.getCurrentSchema());
    }
    return schema + "." + name;
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

}
