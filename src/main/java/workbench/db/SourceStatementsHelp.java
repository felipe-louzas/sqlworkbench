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

import workbench.util.ClasspathUtil;
import workbench.util.WbFile;

/**
 *
 * @author Thomas Kellerer
 */
public class SourceStatementsHelp
{

  public static final String VIEW_ERROR_START = "Support for displaying view source is currently not configured for: ";
  public static final String PROC_ERROR_START = "Support for displaying procedure source is currently not configured for: ";

  private final String product;
  public SourceStatementsHelp(MetaDataSqlManager mgr)
  {
    product = mgr.getProductName();
  }

  public String explainMissingViewSourceSql()
  {

    ClasspathUtil cp = new ClasspathUtil();
    String jarDir = cp.getJarPath();
    WbFile xmlfile = new WbFile(jarDir, "ViewSourceStatements.xml");

    String explain = VIEW_ERROR_START + product +
      "\n\nTo enable this, create the file:\n" + xmlfile.getFullPath() + "\n" +
      "using the supplied sample below, filling out the necessary SELECT statement where indicated, \n" +
      "to retrieve the source from the DBMS:\n\n" +
      "--- Example ViewSourceStatements.xml starts here ---\n" +
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>  \n" +
      "<java version=\"1.5\" class=\"java.beans.XMLDecoder\">  \n" +
      " \n" +
      " <object class=\"java.util.HashMap\">  \n" +
      "  <void method=\"put\">  \n" +
      "   <string>" + product + "</string>  \n" +
      "   <object class=\"workbench.db.GetMetaDataSql\">  \n" +
      "    <void property=\"baseSql\">  \n" +
      "     <string>The SELECT statement to retrieve the source for a view</string>  \n" +
      "    </void>  \n" +
      "    <void property=\"objectNameField\">  \n" +
      "     <string>The column name from the above SELECT that identifies the view name</string>  \n" +
      "    </void>  \n" +
      "    <void property=\"catalogField\">  \n" +
      "     <string>The column from the above SELECT that identifies the view's catalaog (aka database) - optional</string>  \n" +
      "    </void>  \n" +
      "    <void property=\"schemaField\">  \n" +
      "     <string>The column from the above SELECT that identifies the view's schema - optional</string>  \n" +
      "    </void>  \n" +
      "    <void property=\"orderBy\">  \n" +
      "     <string>Define an order by clause for the select statement in case the source is returned in more than one row.</string>  \n" +
      "    </void>  \n" +
      "   </object>  \n" +
      "  </void>  \n" +
      "   \n" +
      " </object>  \n" +
      "</java>\n";
    return explain;
  }

  public String explainMissingProcSourceSql()
  {
    ClasspathUtil cp = new ClasspathUtil();
    String jarDir = cp.getJarPath();
    WbFile xmlfile = new WbFile(jarDir, "ProcSourceStatements.xml");

    String explain = PROC_ERROR_START + product +
      "\n\nTo enable this, create the file\n" + xmlfile.getFullPath() + "\n" +
      "using the supplied sample below, filling out the necessary SELECT statement where indicated, \n" +
      "to retrieve the source from the DBMS:\n\n" +
      "--- Example ProcSourceStatements.xml starts here ---\n" +
      "<?xml version=\"1.0\" encoding=\"UTF-8\"?>  \n" +
      "<java version=\"1.5\" class=\"java.beans.XMLDecoder\">  \n" +
      " <object class=\"java.util.HashMap\">  \n" +
      "  <void method=\"put\">  \n" +
      "   <string>" + product + "</string>  \n" +
      "   <object class=\"workbench.db.GetMetaDataSql\">  \n" +
      "    <void property=\"baseSql\">  \n" +
      "     <string>The SELECT statement to retrieve the source for a procedure or function</string>  \n" +
      "    </void>  \n" +
      "    <void property=\"objectNameField\">  \n" +
      "     <string>The column name from the select that identifies the procedure name</string>  \n" +
      "    </void>  \n" +
      "    <void property=\"catalogField\">  \n" +
      "     <string>The column from the above SELECT that identifies the procedure's catalaog (aka database) - optional</string>  \n" +
      "    </void>  \n" +
      "    <void property=\"schemaField\">  \n" +
      "     <string>The column name from the select that identifies the procedure's schema - optional</string>  \n" +
      "    </void>  \n" +
      "    <void property=\"orderBy\">  \n" +
      "     <string>Define an order by clause for the above SELECT statement in case the source is returned in more than one row.</string>  \n" +
      "    </void>  \n" +
      "   </object>  \n" +
      "  </void>  \n" +
      "   \n" +
      " </object>  \n" +
      "</java>\n";
    return explain;
  }

}
