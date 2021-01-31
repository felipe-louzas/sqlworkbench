/*
 * MacroDefinitionPanel.java
 *
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2021, Thomas Kellerer
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
package workbench.gui.macros;

import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeListener;

import javax.swing.KeyStroke;

import workbench.interfaces.StatusBar;
import workbench.resource.ResourceMgr;
import workbench.resource.ShortcutManager;
import workbench.resource.StoreableKeyStroke;

import workbench.db.WbConnection;

import workbench.gui.WbSwingUtilities;
import workbench.gui.actions.AutoCompletionAction;
import workbench.gui.components.BooleanPropertyEditor;
import workbench.gui.components.StringPropertyEditor;
import workbench.gui.components.WbTraversalPolicy;
import workbench.gui.settings.KeyboardMapper;
import workbench.gui.sql.EditorPanel;

import workbench.sql.macros.MacroDefinition;

/**
 * A panel displaying the definition of a macro.
 *
 * The following macro properties can be maintained with this panel:
 * <ul>
 *  <li>The macro name</li>
 *  <li>The macro text</li>
 *  <li>The flag to show the macro in the menu</li>
 *  <li>The keyboard shortcut for this macro</li>
 * </ul>
 * @author Thomas Kellerer
 */
public class MacroDefinitionPanel
  extends javax.swing.JPanel
  implements ActionListener
{
  private EditorPanel macroEditor;
  private MacroDefinition currentMacro;
  private AutoCompletionAction completeAction;

  /**
   * Create a new macro panel
   * @param l the listener to be notified when the macro name changes
   */
  public MacroDefinitionPanel(PropertyChangeListener l)
  {
    initComponents();
    macroEditor = EditorPanel.createSqlEditor();
    macroEditor.showFindOnPopupMenu();
    macroEditor.showFormatSql();
    GridBagConstraints c = new GridBagConstraints();
    c.gridx = 0;
    c.gridy = 4;
    c.gridwidth = GridBagConstraints.REMAINDER;
    c.gridwidth = GridBagConstraints.REMAINDER;
    c.fill = GridBagConstraints.BOTH;
    c.weightx = 1.0;
    c.weighty = 1.0;
    c.insets = new Insets(10, 2, 2, 5);
    add(macroEditor, c);

    WbSwingUtilities.setMinimumSize(macroEditor, 25, 70);

    WbTraversalPolicy policy = new WbTraversalPolicy();
    policy.addComponent(tfName);
    policy.addComponent(visibleInMenu);
    policy.addComponent(assignShortcutButton);
    policy.addComponent(clearShortcutButton);
    policy.addComponent(macroEditor);
    policy.setDefaultComponent(macroEditor);
    setFocusTraversalPolicy(policy);

    if (l != null)
    {
      tfName.addPropertyChangeListener(l);
    }
  }

  /**
   * Displays the passed macro.
   *
   * Updates to the macro properties are applied immediately to the
   * macro instance with the exception of the macro text (as
   * it uses the standard editor which does not implement the SimplePropertyEditor
   * interface).
   *
   * To make sure the macro instance is up-to-date, applyChanges() must
   * be called.
   *
   * @param macro
   * @see #applyChanges()
   */
  public void setMacro(MacroDefinition macro)
  {
    applyChanges();
    currentMacro = macro;
    assignShortcutButton.setEnabled(currentMacro != null);

    BooleanPropertyEditor menu = (BooleanPropertyEditor) visibleInMenu;
    menu.setSourceObject(macro, "visibleInMenu");
    menu.setImmediateUpdate(true);

    BooleanPropertyEditor popup = (BooleanPropertyEditor)visibleInPopup;
    popup.setSourceObject(macro, "visibleInPopup");
    popup.setImmediateUpdate(true);

    BooleanPropertyEditor expand = (BooleanPropertyEditor)doExpansion;
    expand.setSourceObject(macro, "expandWhileTyping");
    expand.setImmediateUpdate(true);

    BooleanPropertyEditor append = (BooleanPropertyEditor)appendResults;
    append.setSourceObject(macro, "appendResult");
    append.setImmediateUpdate(true);

    BooleanPropertyEditor dbTree = (BooleanPropertyEditor)dbTreeMacro;
    dbTree.setSourceObject(macro, "dbTreeMacro");
    dbTree.setImmediateUpdate(true);

    StringPropertyEditor name = (StringPropertyEditor) tfName;
    name.setSourceObject(macro, "name");
    name.setImmediateUpdate(true);

    StringPropertyEditor tip = (StringPropertyEditor) tfTooltip;
    tip.setSourceObject(macro, "tooltip");
    tip.setImmediateUpdate(true);

    updateShortcutDisplay();
    checkOptions();

    EventQueue.invokeLater(() ->
    {
      if (currentMacro != null)
      {
        macroEditor.setText(currentMacro.getText());
        macroEditor.setCaretPosition(0);
      }
      else
      {
        macroEditor.setText("");
      }
    });
  }

  public void setCurrentConnection(WbConnection conn)
  {
    macroEditor.setDatabaseConnection(conn);
    if (conn != null)
    {
      completeAction = new AutoCompletionAction(macroEditor, new StatusBar()
      {

        @Override
        public void setStatusMessage(String message, int duration)
        {
        }

        @Override
        public void setStatusMessage(String message)
        {
        }

        @Override
        public void clearStatusMessage()
        {
        }

        @Override
        public void doRepaint()
        {
        }

        @Override
        public String getText()
        {
          return "";
        }
      });

      completeAction.setConnection(conn);
      completeAction.setEnabled(true);
    }
    else if (completeAction != null)
    {
      completeAction.setConnection(null);
      completeAction.setEnabled(false);
      macroEditor.removeKeyBinding(completeAction.getAccelerator());
    }

  }

  private void checkOptions()
  {
    if (dbTreeMacro.isSelected())
    {
      appendResults.setEnabled(true);
      doExpansion.setEnabled(false);
      visibleInMenu.setEnabled(false);
      visibleInPopup.setEnabled(false);
      assignShortcutButton.setEnabled(false);
      clearShortcutButton.setEnabled(false);
    }
    else
    {
      appendResults.setEnabled(!doExpansion.isSelected());
      doExpansion.setEnabled(true);
      visibleInMenu.setEnabled(true);
      visibleInPopup.setEnabled(true);
      assignShortcutButton.setEnabled(true);
      clearShortcutButton.setEnabled(true);
    }
  }

  public void applyChanges()
  {
    if (currentMacro != null && macroEditor.isModified())
    {
      currentMacro.setText(macroEditor.getText());
    }
  }

  private void updateShortcutDisplay()
  {
    StoreableKeyStroke key = (currentMacro == null ? null : currentMacro.getShortcut());
    if (key != null)
    {
      shortcutLabel.setText("<html>" + ResourceMgr.getString("LblKeyDefKeyCol") + ": <span style=\"background-color:white;font-weight:bold\"><tt>&nbsp;" + key.toString() + "&nbsp;</tt></span></html>");
    }
    else
    {
      shortcutLabel.setText(ResourceMgr.getString("LblKeyDefKeyCol") + ": " + ResourceMgr.getString("LblNone"));
    }
  }

  /**
   * Puts the focus to the macro name input field.
   */
  public void selectMacroName()
  {
    EventQueue.invokeLater(tfName::requestFocusInWindow);
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

    jLabel1 = new javax.swing.JLabel();
    tfName = new StringPropertyEditor();
    jLabel2 = new javax.swing.JLabel();
    tfTooltip = new StringPropertyEditor();
    optionsPanel = new javax.swing.JPanel();
    visibleInMenu = new BooleanPropertyEditor();
    jSeparator1 = new javax.swing.JSeparator();
    visibleInPopup = new BooleanPropertyEditor();
    jSeparator2 = new javax.swing.JSeparator();
    doExpansion = new BooleanPropertyEditor();
    jSeparator3 = new javax.swing.JSeparator();
    appendResults = new BooleanPropertyEditor();
    jSeparator4 = new javax.swing.JSeparator();
    dbTreeMacro = new BooleanPropertyEditor();
    shortcutPanel = new javax.swing.JPanel();
    shortcutLabel = new javax.swing.JLabel();
    assignShortcutButton = new javax.swing.JButton();
    clearShortcutButton = new javax.swing.JButton();

    setLayout(new java.awt.GridBagLayout());

    jLabel1.setText(ResourceMgr.getString("LblMacroName")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
    add(jLabel1, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridwidth = 9;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(5, 4, 0, 5);
    add(tfName, gridBagConstraints);

    jLabel2.setText(ResourceMgr.getString("LblMacroToolTip")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
    add(jLabel2, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.gridwidth = 9;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(5, 4, 0, 5);
    add(tfTooltip, gridBagConstraints);

    optionsPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
    optionsPanel.setLayout(new java.awt.GridBagLayout());

    visibleInMenu.setText(ResourceMgr.getString("LblMacroGrpMenu")); // NOI18N
    visibleInMenu.setBorder(null);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 5);
    optionsPanel.add(visibleInMenu, gridBagConstraints);

    jSeparator1.setOrientation(javax.swing.SwingConstants.VERTICAL);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    optionsPanel.add(jSeparator1, gridBagConstraints);

    visibleInPopup.setText(ResourceMgr.getString("LblMacroGrpPop")); // NOI18N
    visibleInPopup.setBorder(null);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 5);
    optionsPanel.add(visibleInPopup, gridBagConstraints);

    jSeparator2.setOrientation(javax.swing.SwingConstants.VERTICAL);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    optionsPanel.add(jSeparator2, gridBagConstraints);

    doExpansion.setText(ResourceMgr.getString("LblExpandMacro")); // NOI18N
    doExpansion.setToolTipText(ResourceMgr.getString("d_LblExpandMacro")); // NOI18N
    doExpansion.addActionListener(this);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 4;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 3, 0, 3);
    optionsPanel.add(doExpansion, gridBagConstraints);

    jSeparator3.setOrientation(javax.swing.SwingConstants.VERTICAL);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 5;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    optionsPanel.add(jSeparator3, gridBagConstraints);

    appendResults.setText(ResourceMgr.getString("LblAppendMacroData")); // NOI18N
    appendResults.setToolTipText(ResourceMgr.getString("d_LblAppendMacroData")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 6;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 3, 0, 3);
    optionsPanel.add(appendResults, gridBagConstraints);

    jSeparator4.setOrientation(javax.swing.SwingConstants.VERTICAL);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 7;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
    optionsPanel.add(jSeparator4, gridBagConstraints);

    dbTreeMacro.setText(ResourceMgr.getString("LblDbTreeMacro")); // NOI18N
    dbTreeMacro.setToolTipText(ResourceMgr.getString("d_LblDbTreeMacro")); // NOI18N
    dbTreeMacro.addActionListener(this);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 8;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(0, 3, 0, 3);
    optionsPanel.add(dbTreeMacro, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(8, 4, 0, 5);
    add(optionsPanel, gridBagConstraints);

    shortcutPanel.setLayout(new java.awt.GridBagLayout());

    shortcutLabel.setText(ResourceMgr.getString("LblKeyDefKeyCol")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 5);
    shortcutPanel.add(shortcutLabel, gridBagConstraints);

    assignShortcutButton.setText(ResourceMgr.getString("LblAssignShortcut")); // NOI18N
    assignShortcutButton.addActionListener(this);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(6, 7, 0, 0);
    shortcutPanel.add(assignShortcutButton, gridBagConstraints);

    clearShortcutButton.setText(ResourceMgr.getString("LblClearShortcut")); // NOI18N
    clearShortcutButton.addActionListener(this);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(6, 10, 0, 0);
    shortcutPanel.add(clearShortcutButton, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(1, 4, 0, 5);
    add(shortcutPanel, gridBagConstraints);
  }

  // Code for dispatching events from components to event handlers.

  public void actionPerformed(java.awt.event.ActionEvent evt)
  {
    if (evt.getSource() == doExpansion)
    {
      MacroDefinitionPanel.this.doExpansionActionPerformed(evt);
    }
    else if (evt.getSource() == assignShortcutButton)
    {
      MacroDefinitionPanel.this.assignShortcutButtonActionPerformed(evt);
    }
    else if (evt.getSource() == clearShortcutButton)
    {
      MacroDefinitionPanel.this.clearShortcutButtonActionPerformed(evt);
    }
    else if (evt.getSource() == dbTreeMacro)
    {
      MacroDefinitionPanel.this.dbTreeMacroActionPerformed(evt);
    }
  }// </editor-fold>//GEN-END:initComponents

  private void assignShortcutButtonActionPerformed(ActionEvent evt)//GEN-FIRST:event_assignShortcutButtonActionPerformed
  {//GEN-HEADEREND:event_assignShortcutButtonActionPerformed
    if (this.currentMacro != null)
    {
      KeyStroke key = KeyboardMapper.getKeyStroke(this);
      if (key != null)
      {
        String clazz = ShortcutManager.getInstance().getActionClassForKey(key);
        String name = ShortcutManager.getInstance().getActionNameForClass(clazz);
        if (name != null)
        {
          String msg = ResourceMgr.getFormattedString("MsgShortcutAlreadyAssigned", name);
          boolean choice = WbSwingUtilities.getYesNo(this, msg);
          if (!choice)
          {
            return;
          }
          ShortcutManager.getInstance().removeShortcut(clazz);
          ShortcutManager.getInstance().updateActions();
        }
        StoreableKeyStroke stroke = new StoreableKeyStroke(key);
        currentMacro.setShortcut(stroke);
        updateShortcutDisplay();
      }
    }
  }//GEN-LAST:event_assignShortcutButtonActionPerformed

  private void clearShortcutButtonActionPerformed(ActionEvent evt)//GEN-FIRST:event_clearShortcutButtonActionPerformed
  {//GEN-HEADEREND:event_clearShortcutButtonActionPerformed
    if (this.currentMacro != null)
    {
      currentMacro.setShortcut(null);
      updateShortcutDisplay();
    }
  }//GEN-LAST:event_clearShortcutButtonActionPerformed

  private void doExpansionActionPerformed(ActionEvent evt)//GEN-FIRST:event_doExpansionActionPerformed
  {//GEN-HEADEREND:event_doExpansionActionPerformed
    appendResults.setEnabled(!doExpansion.isSelected());
  }//GEN-LAST:event_doExpansionActionPerformed

  private void dbTreeMacroActionPerformed(ActionEvent evt)//GEN-FIRST:event_dbTreeMacroActionPerformed
  {//GEN-HEADEREND:event_dbTreeMacroActionPerformed
    if (dbTreeMacro.isSelected())
    {
      // if the dbTreeMacro option is enabled,
      // clear the selected state for the others
      // to avoid a confusing display
      doExpansion.setSelected(false);
      visibleInMenu.setSelected(false);
      visibleInPopup.setSelected(false);
      assignShortcutButton.setSelected(false);
      clearShortcutButton.setSelected(false);
    }
    checkOptions();
  }//GEN-LAST:event_dbTreeMacroActionPerformed

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private javax.swing.JCheckBox appendResults;
  private javax.swing.JButton assignShortcutButton;
  private javax.swing.JButton clearShortcutButton;
  private javax.swing.JCheckBox dbTreeMacro;
  private javax.swing.JCheckBox doExpansion;
  private javax.swing.JLabel jLabel1;
  private javax.swing.JLabel jLabel2;
  private javax.swing.JSeparator jSeparator1;
  private javax.swing.JSeparator jSeparator2;
  private javax.swing.JSeparator jSeparator3;
  private javax.swing.JSeparator jSeparator4;
  private javax.swing.JPanel optionsPanel;
  private javax.swing.JLabel shortcutLabel;
  private javax.swing.JPanel shortcutPanel;
  private javax.swing.JTextField tfName;
  private javax.swing.JTextField tfTooltip;
  private javax.swing.JCheckBox visibleInMenu;
  private javax.swing.JCheckBox visibleInPopup;
  // End of variables declaration//GEN-END:variables
}
