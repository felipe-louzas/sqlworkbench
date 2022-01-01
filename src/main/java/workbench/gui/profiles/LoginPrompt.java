/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2022, Thomas Kellerer
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
package workbench.gui.profiles;

import java.awt.event.ActionListener;

import javax.swing.JPanel;
import javax.swing.JTextField;

import workbench.interfaces.ValidatingComponent;
import workbench.log.CallerInfo;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.HistoryTextField;

import workbench.log.LogMgr;

/**
 *
 * @author Thomas Kellerer
 */
public class LoginPrompt
  extends JPanel
  implements ValidatingComponent, ActionListener
{

  public LoginPrompt(String historyKey)
  {
    initComponents();
    getUsernameField().setSettingsProperty(historyKey);
    WbSwingUtilities.setMinimumSize(tfUsername, 25);
    WbSwingUtilities.setMinimumSize(tfPwd, 25);
  }

  public String getUserName()
  {
    return getUsernameField().getText().trim();
  }

  public String getPassword()
  {
    return tfPwd.getText();
  }

  @Override
  public boolean validateInput()
  {
    getUsernameField().addToHistory(getUserName());
    getUsernameField().saveSettings(Settings.getInstance(), "workbench.loginprompt.");
    return true;
  }

  private HistoryTextField getUsernameField()
  {
    return (HistoryTextField)tfUsername;
  }

  @Override
  public void componentWillBeClosed()
  {
    // nothing to do
  }

  @Override
  public void componentDisplayed()
  {
    getUsernameField().restoreSettings(Settings.getInstance(), "workbench.loginprompt.");
    getUsernameField().setText("");
    WbSwingUtilities.requestFocus(tfUsername);
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
    java.awt.GridBagConstraints gridBagConstraints;

    lblUsername = new javax.swing.JLabel();
    tfPwd = new javax.swing.JPasswordField();
    lblPwd = new javax.swing.JLabel();
    tfUsername = new HistoryTextField("usernames");

    setLayout(new java.awt.GridBagLayout());

    lblUsername.setText(ResourceMgr.getString("LblUsername")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(2, 5, 2, 0);
    add(lblUsername, gridBagConstraints);

    tfPwd.setName("password"); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(2, 6, 4, 2);
    add(tfPwd, gridBagConstraints);

    lblPwd.setText(ResourceMgr.getString("LblPassword")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(2, 5, 2, 0);
    add(lblPwd, gridBagConstraints);

    tfUsername.setEditable(true);
    tfUsername.setMaximumRowCount(10);
    tfUsername.addActionListener(this);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(2, 6, 4, 2);
    add(tfUsername, gridBagConstraints);
  }

  // Code for dispatching events from components to event handlers.

  public void actionPerformed(java.awt.event.ActionEvent evt)
  {
    if (evt.getSource() == tfUsername)
    {
      LoginPrompt.this.tfUsernameActionPerformed(evt);
    }
  }// </editor-fold>//GEN-END:initComponents

  private void tfUsernameActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_tfUsernameActionPerformed
  {//GEN-HEADEREND:event_tfUsernameActionPerformed
    try
    {
      JTextField text = (JTextField)tfUsername.getEditor().getEditorComponent();
      text.selectAll();
    }
    catch (Throwable th)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Could not select text of username combobox", th);
    }

  }//GEN-LAST:event_tfUsernameActionPerformed

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JLabel lblPwd;
  private javax.swing.JLabel lblUsername;
  private javax.swing.JPasswordField tfPwd;
  private javax.swing.JComboBox tfUsername;
  // End of variables declaration//GEN-END:variables
}
