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
package workbench.interfaces;

import workbench.resource.ResourceMgr;

/**
 *
 * @author  Thomas Kellerer
 */
public interface ExecutionController
{

  /**
   * Confirm the execution of passed SQL command.
   *
   * @return true if the user chose to continue
   */
  boolean confirmStatementExecution(String command);

  /**
   * Confirm the execution of the statements with a user visible prompt.
   * This is similar to the "pause" command in a Windows batch file.
   *
   * @param prompt the prompt to be displayed to the user
   * @param yesText the text to display for the "Yes" option, may be null
   * @param noText the text to display for the "No" option, may be null
   * @return true if the user chose to continue
   */
  boolean confirmExecution(String prompt, String yesText, String noText);

  String getPassword(String title, String prompt);

  default String getPassword(String prompt)
  {
    return getPassword(ResourceMgr.TXT_PRODUCT_NAME, prompt);
  }

  String getInput(String prompt);
}
