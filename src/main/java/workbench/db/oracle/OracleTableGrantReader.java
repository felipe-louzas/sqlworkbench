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
package workbench.db.oracle;

import workbench.db.TableGrantReader;
import workbench.db.TableIdentifier;
import workbench.db.WbConnection;

/**
 *
 * @author Thomas Kellerer
 */
public class OracleTableGrantReader
  extends TableGrantReader
{
  public OracleTableGrantReader()
  {
  }

  @Override
  public StringBuilder getTableGrantSource(WbConnection dbConnection, TableIdentifier table)
  {
    if (OracleUtils.getUseOracleDBMSMeta(OracleUtils.DbmsMetadataTypes.grant))
    {
      String grants = DbmsMetadata.getDependentDDL(dbConnection, "OBJECT_GRANT", table.getRawTableName(), table.getRawSchema());
      return new StringBuilder(grants == null ? "" : grants);
    }
    return super.getTableGrantSource(dbConnection, table);
  }

}
