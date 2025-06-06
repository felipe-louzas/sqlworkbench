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
package workbench.db.firebird;

import workbench.db.AbstractConstraintReader;
import workbench.db.DBID;
import workbench.db.TableIdentifier;

/**
 * An implementation of {@link AbstractConstraintReader} for the
 * <a href="https://www.firebirdsql.org">Firebird</a> database server
 * @author  Thomas Kellerer
 */
public class FirebirdConstraintReader
  extends AbstractConstraintReader
{

  private final String TABLE_SQL =
     "select trim(cc.rdb$constraint_name), trg.rdb$trigger_source " +
     "from rdb$relation_constraints rc  \n" +
     "  join rdb$check_constraints cc on rc.rdb$constraint_name = cc.rdb$constraint_name \n" +
     "  join rdb$triggers trg on cc.rdb$trigger_name = trg.rdb$trigger_name \n" +
     "where rc.rdb$relation_name = ? \n" +
     "  and rc.rdb$constraint_type = 'CHECK' \n" +
     "  and trg.rdb$trigger_type = 1 \n";

  public FirebirdConstraintReader()
  {
    super(DBID.Firebird.getId());
  }

  @Override
  public String getColumnConstraintSql(TableIdentifier tbl)
  {
    return null;
  }

  @Override
  public String getTableConstraintSql(TableIdentifier tbl)
  {
    return TABLE_SQL;
  }
}
