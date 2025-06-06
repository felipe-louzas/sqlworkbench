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
package workbench.gui.settings;

import javax.swing.JPanel;

import workbench.interfaces.Restoreable;

import workbench.gui.components.WbTabbedPane;

/**
 *
 * @author Thomas Kellerer
 */
public class FormatterOptionsPanel
  extends JPanel
  implements Restoreable
{

  private WbFormatterOptionsPanel wbOptions;
  private ExternalFormatterOptions extOptions;

  /** Creates new form FormatterOptionsPanel */
  public FormatterOptionsPanel()
  {
    initComponents();
    wbOptions = new WbFormatterOptionsPanel();
    wbOptions.setBorder(OptionPanelPage.PAGE_PADDING);
    extOptions = new ExternalFormatterOptions();
    extOptions.setBorder(OptionPanelPage.PAGE_PADDING);
    tabbedPane.add("Built-in", wbOptions);
    tabbedPane.add("External", extOptions);
  }

  @Override
  public void restoreSettings()
  {
    wbOptions.restoreSettings();
    extOptions.restoreSettings();
  }

  @Override
  public void saveSettings()
  {
    wbOptions.saveSettings();
    extOptions.saveSettings();
  }


  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  @SuppressWarnings("unchecked")
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents()
  {

    tabbedPane = new WbTabbedPane();

    setLayout(new java.awt.BorderLayout());

    tabbedPane.setTabLayoutPolicy(javax.swing.JTabbedPane.SCROLL_TAB_LAYOUT);
    add(tabbedPane, java.awt.BorderLayout.CENTER);
  }// </editor-fold>//GEN-END:initComponents


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JTabbedPane tabbedPane;
  // End of variables declaration//GEN-END:variables
}
