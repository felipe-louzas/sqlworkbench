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

/**
 *
 * @author  Thomas Kellerer
 */
public interface Replaceable

{
  /**
   *  Initiate the replace Dialog
   */
  void replace();

  /**
   *  Find and highlight the first occurance of the String.
   *
   *  @return true if an occurance was found
   */
  int findFirst(String aValue, boolean ignoreCase, boolean wholeWord, boolean useRegex);

  /**
   *  Find and highlight the next occurance of the expression initially found with findFirst().
   *
   *  If findFirst() has not been called before, -1 should be returned.
   */
  int findNext();

  /**
   *  Replace the currently highlighted (=found) text with the given value.
   */
  boolean replaceCurrent(String aReplacement, boolean useRegex);


  /**
   *  Find and replace the next occurance.
   *
   *  Only valid if findFirst() was called.
   *
   *  @return true if an occurance was found
   */
  boolean replaceNext(String aReplacement, boolean useRegex);

  /**
   *  Find and replace all occurances of the given valuewith the replacement.
   *
   * @param searchExpression   the value to find
   * @param replacement        the replacement value
   * @param selectedText       if true, only search and replace in the selected text
   * @param ignoreCase         if true, do a case-insensitive match
   * @param wholeWord          if true, only find whole words
   * @param useRegex           if true, <tt>searchExpression</tt> is treated as a regular expression
   *
   * @return the number of strings replaced
   */
  int replaceAll(String searchExpression, String replacement, boolean selectedText, boolean ignoreCase, boolean wholeWord, boolean useRegex);

  int countMatches(String searchExpression, boolean selectedText, boolean ignoreCase, boolean wholeWord, boolean useRegex);

  void setWrapSearch(boolean flag);

  boolean isTextSelected();
}
