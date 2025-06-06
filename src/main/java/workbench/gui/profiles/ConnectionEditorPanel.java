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
package workbench.gui.profiles;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;

import workbench.interfaces.SimplePropertyEditor;
import workbench.interfaces.ValidatingComponent;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.GuiSettings;
import workbench.resource.IconMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;
import workbench.ssh.SshConfig;
import workbench.ssh.UrlParser;

import workbench.db.ConnectionMgr;
import workbench.db.ConnectionProfile;
import workbench.db.DbDriver;
import workbench.db.ObjectNameFilter;
import workbench.db.TransactionChecker;

import workbench.gui.WbSwingUtilities;
import workbench.gui.actions.WbAction;
import workbench.gui.components.BooleanPropertyEditor;
import workbench.gui.components.DividerBorder;
import workbench.gui.components.ExtensionFileFilter;
import workbench.gui.components.IntegerPropertyEditor;
import workbench.gui.components.MapEditor;
import workbench.gui.components.PasswordPropertyEditor;
import workbench.gui.components.StringPropertyEditor;
import workbench.gui.components.ValidatingDialog;
import workbench.gui.components.WbColorPicker;
import workbench.gui.components.WbFileChooser;
import workbench.gui.components.WbTraversalPolicy;
import workbench.gui.help.HelpManager;
import workbench.gui.renderer.ColorUtils;

import workbench.sql.DelimiterDefinition;
import workbench.sql.macros.MacroFileSelector;
import workbench.sql.macros.MacroManager;

import workbench.util.CollectionUtil;
import workbench.util.FileDialogUtil;
import workbench.util.ImageUtil;
import workbench.util.StringUtil;
import workbench.util.WbFile;
import workbench.util.WorkspaceSelector;

/**
 *
 * @author  Thomas Kellerer
 */
public class ConnectionEditorPanel
  extends JPanel
  implements PropertyChangeListener, ActionListener, ValidatingComponent, KeyListener
{
  private ConnectionProfile currentProfile;
  private final List<ProfileChangeListener> changeListener = new ArrayList<>();
  private boolean init;
  private final List<SimplePropertyEditor> editors = new ArrayList<>();
  private Set<String> allTags;
  private final char echoChar;

  public ConnectionEditorPanel()
  {
    super();
    this.initComponents();
    Border b = new CompoundBorder(DividerBorder.TOP_BOTTOM_DIVIDER, new EmptyBorder(8, 0, 6, 0));
    wbOptionsPanel.setBorder(b);

    WbSwingUtilities.makeBold(groupNameLabel);
    groupNameLabel.setBackground(UIManager.getColor("TextArea.background"));
    groupNameLabel.setForeground(UIManager.getColor("TextArea.foreground"));
    echoChar = tfPwd.getEchoChar();
    String text = altDelimLabel.getText();
    altDelimLabel.setText("<html><u>" + text + "</u></html>");

    altDelimLabel.addMouseListener(new MouseAdapter()
    {
      @Override
      public void mouseClicked(MouseEvent e)
      {
        HelpManager.showHelpFile(HelpManager.TOPIC_ALTERNATE_DELIMITER);
      }
    });

    tagList.addKeyListener(this);
    mainPanel.setBorder(DividerBorder.TOP_DIVIDER);

    WbTraversalPolicy policy = new WbTraversalPolicy();
    policy.addComponent(tfProfileName);
    policy.addComponent(cbDrivers);
    policy.addComponent(tfURL);
    policy.addComponent(tfUserName);
    policy.addComponent(asSysDBA);
    policy.addComponent(tfPwd);
    policy.addComponent(showPassword);
    policy.addComponent(cbAutocommit);
    policy.addComponent(tfFetchSize);
    policy.addComponent(tfTimeout);
    policy.addComponent(extendedProps);
    policy.addComponent(cbxPromptUsername);
    policy.addComponent(rollbackBeforeDisconnect);
    policy.addComponent(cbStorePassword);
    policy.addComponent(confirmUpdates);
    policy.addComponent(readOnly);
    policy.addComponent(cbSeparateConnections);
    policy.addComponent(preventNoWhere);
    policy.addComponent(cbIgnoreDropErrors);
    policy.addComponent(includeNull);
    policy.addComponent(emptyStringIsNull);
    policy.addComponent(rememberExplorerSchema);
    policy.addComponent(trimCharData);
    policy.addComponent(removeComments);
    policy.addComponent(hideWarnings);
    policy.addComponent(checkOpenTrans);
    policy.addComponent(altDelimiter);
    policy.addComponent(editConnectionScriptsButton);
    policy.addComponent(tfWorkspaceFile);
    policy.addComponent(selectWkspButton);
    policy.addComponent(tfScriptDir);
    policy.addComponent(selectScriptDirButton);
    policy.addComponent(icon);
    policy.addComponent(selectIconButton);
    policy.addComponent(macroFile);
    policy.addComponent(selectMacroFileButton);
    policy.addComponent(tagList);
    policy.addComponent(editConnectionScriptsButton);
    policy.addComponent(editFilterButton);

    policy.setDefaultComponent(tfProfileName);

    this.setFocusCycleRoot(false);
    this.setFocusTraversalPolicy(policy);

    this.selectWkspButton.addActionListener(this);
    this.selectIconButton.addActionListener(this);
    this.selectMacroFileButton.addActionListener(this);
    this.selectScriptDirButton.addActionListener(this);
    this.showPassword.addActionListener(this);

    this.infoColor.setActionListener(this);
    this.confirmUpdates.addActionListener(this);
    this.readOnly.addActionListener(this);
    this.cbxPromptUsername.addActionListener(this);
    WbSwingUtilities.calculatePreferredSize(tfFetchSize, 5);
    WbSwingUtilities.calculatePreferredSize(tfTimeout, 5);
    WbSwingUtilities.calculatePreferredSize(altDelimiter, 5);

    showPassword.setText(null);
    showPassword.setIcon(IconMgr.getInstance().getLabelIcon("eye"));
    showPassword.setMargin(tfPwd.getMargin());

    alignHeight(tfPwd, showPassword);
    alignHeight(tfWorkspaceFile, selectWkspButton);
    alignHeight(icon, selectIconButton);
    alignHeight(macroFile, selectMacroFileButton);

    this.initEditorList();

    String wkspTooltip = ResourceMgr.getFormattedString("d_LblOpenWksp", Settings.getInstance().getWorkspaceDir());
    tfWorkspaceFile.setToolTipText(wkspTooltip);
    workspaceFileLabel.setToolTipText(wkspTooltip);

    String macroTooltip = ResourceMgr.getFormattedString("d_LblMacroFile", Settings.getInstance().getMacroBaseDirectory());
    macroFile.setToolTipText(macroTooltip);
    macroFileLabel.setToolTipText(macroTooltip);
  }

  private void alignHeight(JTextField text, JButton button)
  {
    Dimension pf = text.getPreferredSize();
    Dimension pwdSize = button.getPreferredSize();
    Dimension size = new Dimension(pwdSize.width, pf.height + 2);
    button.setSize(size);
    button.setPreferredSize(size);
  }

  public JComponent getInitialFocusComponent()
  {
    return tfProfileName;
  }

  public void setFocusToTitle()
  {
    EventQueue.invokeLater(() ->
    {
      tfProfileName.requestFocusInWindow();
      tfProfileName.selectAll();
    });
  }

  private void initEditorList()
  {
    this.editors.clear();
    initEditorList(mainPanel);
  }

  private void initEditorList(Container parent)
  {
    for (int i = 0; i < parent.getComponentCount(); i++)
    {
      Component c = parent.getComponent(i);
      if (c instanceof SimplePropertyEditor)
      {
        SimplePropertyEditor ed = (SimplePropertyEditor)c;
        this.editors.add(ed);
        String name = c.getName();
        c.addPropertyChangeListener(name, this);
        ed.setImmediateUpdate(true);
      }
      else if (c instanceof JPanel)
      {
        initEditorList((JPanel) c);
      }
    }
  }

  private void showTagSearch()
  {
    TagSearchPopup search = new TagSearchPopup(tagList, allTags);
    search.showPopup();
  }

  public void setAllTags(Set<String> tags)
  {
    allTags = CollectionUtil.caseInsensitiveSet(tags);
  }

  @Override
  public void keyTyped(KeyEvent e)
  {
  }

  @Override
  public void keyPressed(KeyEvent e)
  {
    if (e.getKeyCode() == KeyEvent.VK_SPACE && WbAction.isCtrlPressed(e))
    {
      e.consume();
      EventQueue.invokeLater(this::showTagSearch);
    }
  }

  @Override
  public void keyReleased(KeyEvent e)
  {
  }

  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents() {
    java.awt.GridBagConstraints gridBagConstraints;

    groupNameLabel = new javax.swing.JLabel();
    mainPanel = new javax.swing.JPanel();
    lblPwd = new javax.swing.JLabel();
    asSysDBA = new BooleanPropertyEditor();
    jPanel3 = new javax.swing.JPanel();
    workspaceFileLabel = new javax.swing.JLabel();
    infoColor = new WbColorPicker(true);
    infoColorLabel = new javax.swing.JLabel();
    altDelimLabel = new javax.swing.JLabel();
    jPanel1 = new javax.swing.JPanel();
    tfWorkspaceFile = new StringPropertyEditor();
    selectWkspButton = new javax.swing.JButton();
    scriptDirLabel = new javax.swing.JLabel();
    jPanel7 = new javax.swing.JPanel();
    tfScriptDir = new StringPropertyEditor();
    selectScriptDirButton = new javax.swing.JButton();
    jLabel3 = new javax.swing.JLabel();
    jPanel4 = new javax.swing.JPanel();
    icon = new StringPropertyEditor();
    selectIconButton = new javax.swing.JButton();
    macroFileLabel = new javax.swing.JLabel();
    jPanel5 = new javax.swing.JPanel();
    macroFile = new StringPropertyEditor();
    selectMacroFileButton = new javax.swing.JButton();
    altDelimiter = new StringPropertyEditor();
    jLabel2 = new javax.swing.JLabel();
    tagList = new StringPropertyEditor();
    tfPwd = new PasswordPropertyEditor();
    lblUsername = new javax.swing.JLabel();
    timeoutpanel = new javax.swing.JPanel();
    jPanel6 = new javax.swing.JPanel();
    editConnectionScriptsButton = new javax.swing.JButton();
    editFilterButton = new javax.swing.JButton();
    editVariablesButton = new javax.swing.JButton();
    filler2 = new javax.swing.Box.Filler(new java.awt.Dimension(0, 0), new java.awt.Dimension(0, 0), new java.awt.Dimension(32767, 0));
    testConnectionButton = new javax.swing.JButton();
    lblDriver = new javax.swing.JLabel();
    cbDrivers = new javax.swing.JComboBox();
    lblUrl = new javax.swing.JLabel();
    tfUserName = new StringPropertyEditor();
    jPanel2 = new javax.swing.JPanel();
    tfFetchSize = new IntegerPropertyEditor();
    cbAutocommit = new BooleanPropertyEditor();
    extendedProps = new javax.swing.JButton();
    timeoutLabel = new javax.swing.JLabel();
    tfTimeout = new IntegerPropertyEditor();
    jLabel1 = new javax.swing.JLabel();
    fetchSizeLabel = new javax.swing.JLabel();
    sshConfig = new javax.swing.JButton();
    tfURL = new StringPropertyEditor();
    wbOptionsPanel = new javax.swing.JPanel();
    cbStorePassword = new BooleanPropertyEditor();
    rollbackBeforeDisconnect = new BooleanPropertyEditor();
    cbIgnoreDropErrors = new BooleanPropertyEditor();
    cbSeparateConnections = new BooleanPropertyEditor();
    emptyStringIsNull = new BooleanPropertyEditor();
    includeNull = new BooleanPropertyEditor();
    removeComments = new BooleanPropertyEditor();
    rememberExplorerSchema = new BooleanPropertyEditor();
    trimCharData = new BooleanPropertyEditor();
    controlUpdates = new javax.swing.JPanel();
    confirmUpdates = new BooleanPropertyEditor();
    readOnly = new BooleanPropertyEditor();
    hideWarnings = new BooleanPropertyEditor();
    checkOpenTrans = new BooleanPropertyEditor();
    preventNoWhere = new BooleanPropertyEditor();
    cbxPromptUsername = new BooleanPropertyEditor();
    jCheckBox1 = new BooleanPropertyEditor();
    showPassword = new javax.swing.JButton();
    tfProfileName = new StringPropertyEditor();

    FormListener formListener = new FormListener();

    setMinimumSize(new java.awt.Dimension(220, 200));
    setOpaque(false);
    setLayout(new java.awt.BorderLayout());

    groupNameLabel.setBackground(javax.swing.UIManager.getDefaults().getColor("EditorPane.background"));
    groupNameLabel.setText(ResourceMgr.getString("LblGroupName")); // NOI18N
    groupNameLabel.setBorder(javax.swing.BorderFactory.createEmptyBorder(3, 6, 3, 6));
    groupNameLabel.setOpaque(true);
    add(groupNameLabel, java.awt.BorderLayout.NORTH);

    mainPanel.setLayout(new java.awt.GridBagLayout());

    lblPwd.setLabelFor(tfPwd);
    lblPwd.setText(ResourceMgr.getString("LblPassword")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(2, 5, 2, 0);
    mainPanel.add(lblPwd, gridBagConstraints);

    asSysDBA.setText(ResourceMgr.getString("LblSysDba")); // NOI18N
    asSysDBA.setToolTipText(ResourceMgr.getString("d_LblSysDba")); // NOI18N
    asSysDBA.setBorder(null);
    asSysDBA.setName("oracleSysDBA"); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(2, 4, 0, 5);
    mainPanel.add(asSysDBA, gridBagConstraints);

    jPanel3.setLayout(new java.awt.GridBagLayout());

    workspaceFileLabel.setLabelFor(tfWorkspaceFile);
    workspaceFileLabel.setText(ResourceMgr.getString("LblOpenWksp")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
    jPanel3.add(workspaceFileLabel, gridBagConstraints);

    infoColor.setToolTipText(ResourceMgr.getDescription("LblInfoColor"));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 4, 1, 0);
    jPanel3.add(infoColor, gridBagConstraints);

    infoColorLabel.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
    infoColorLabel.setText(ResourceMgr.getString("LblInfoColor")); // NOI18N
    infoColorLabel.setToolTipText(ResourceMgr.getDescription("LblInfoColor")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 0, 1, 0);
    jPanel3.add(infoColorLabel, gridBagConstraints);

    altDelimLabel.setText(ResourceMgr.getString("LblAltDelimit")); // NOI18N
    altDelimLabel.setToolTipText(ResourceMgr.getString("d_LblAltDelimit")); // NOI18N
    altDelimLabel.setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 23, 1, 0);
    jPanel3.add(altDelimLabel, gridBagConstraints);

    jPanel1.setLayout(new java.awt.GridBagLayout());

    tfWorkspaceFile.setHorizontalAlignment(javax.swing.JTextField.LEFT);
    tfWorkspaceFile.setName("workspaceFile"); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 1.0;
    jPanel1.add(tfWorkspaceFile, gridBagConstraints);

    selectWkspButton.setText("...");
    selectWkspButton.setMaximumSize(null);
    selectWkspButton.setMinimumSize(null);
    selectWkspButton.setPreferredSize(null);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 0);
    jPanel1.add(selectWkspButton, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.gridwidth = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(5, 4, 0, 6);
    jPanel3.add(jPanel1, gridBagConstraints);

    scriptDirLabel.setLabelFor(tfScriptDir);
    scriptDirLabel.setText(ResourceMgr.getString("LblEditorDefaultDir")); // NOI18N
    scriptDirLabel.setToolTipText(ResourceMgr.getString("d_LblWkspDefDir")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
    jPanel3.add(scriptDirLabel, gridBagConstraints);

    jPanel7.setLayout(new java.awt.GridBagLayout());

    tfScriptDir.setHorizontalAlignment(javax.swing.JTextField.LEFT);
    tfScriptDir.setToolTipText(ResourceMgr.getString("d_LblWkspDefDir")); // NOI18N
    tfScriptDir.setName("defaultDirectory"); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 1.0;
    jPanel7.add(tfScriptDir, gridBagConstraints);

    selectScriptDirButton.setText("...");
    selectScriptDirButton.setMaximumSize(null);
    selectScriptDirButton.setMinimumSize(null);
    selectScriptDirButton.setPreferredSize(null);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
    gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 0);
    jPanel7.add(selectScriptDirButton, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.gridwidth = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(5, 4, 0, 6);
    jPanel3.add(jPanel7, gridBagConstraints);

    jLabel3.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
    jLabel3.setText(ResourceMgr.getString("LblIcon")); // NOI18N
    jLabel3.setToolTipText(ResourceMgr.getString("d_LblIcon")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
    jPanel3.add(jLabel3, gridBagConstraints);

    jPanel4.setLayout(new java.awt.GridBagLayout());

    icon.setHorizontalAlignment(javax.swing.JTextField.LEFT);
    icon.setToolTipText(ResourceMgr.getString("d_LblIcon")); // NOI18N
    icon.setName("icon"); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 1.0;
    jPanel4.add(icon, gridBagConstraints);

    selectIconButton.setText("...");
    selectIconButton.setMaximumSize(null);
    selectIconButton.setMinimumSize(null);
    selectIconButton.setPreferredSize(null);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 0);
    jPanel4.add(selectIconButton, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.gridwidth = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(5, 4, 0, 6);
    jPanel3.add(jPanel4, gridBagConstraints);

    macroFileLabel.setHorizontalAlignment(javax.swing.SwingConstants.LEFT);
    macroFileLabel.setText(ResourceMgr.getString("LblMacros")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
    jPanel3.add(macroFileLabel, gridBagConstraints);

    jPanel5.setLayout(new java.awt.GridBagLayout());

    macroFile.setHorizontalAlignment(javax.swing.JTextField.LEFT);
    macroFile.setToolTipText(ResourceMgr.getString("d_LblMacroFile")); // NOI18N
    macroFile.setName("macroFilename"); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 1.0;
    jPanel5.add(macroFile, gridBagConstraints);

    selectMacroFileButton.setText("...");
    selectMacroFileButton.setMaximumSize(null);
    selectMacroFileButton.setMinimumSize(null);
    selectMacroFileButton.setPreferredSize(null);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 0);
    jPanel5.add(selectMacroFileButton, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.gridwidth = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(5, 4, 0, 6);
    jPanel3.add(jPanel5, gridBagConstraints);

    altDelimiter.setName("alternateDelimiterString"); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new java.awt.Insets(0, 4, 0, 0);
    jPanel3.add(altDelimiter, gridBagConstraints);

    jLabel2.setText("Tags");
    jLabel2.setToolTipText(ResourceMgr.getString("d_LblConnTags")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(8, 0, 0, 0);
    jPanel3.add(jLabel2, gridBagConstraints);

    tagList.setToolTipText(ResourceMgr.getDescription("LblConnTagsInput", true)); // NOI18N
    tagList.setName("tagList"); // NOI18N
    tagList.setVerifyInputWhenFocusTarget(false);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.gridwidth = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(5, 4, 0, 6);
    jPanel3.add(tagList, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 14;
    gridBagConstraints.gridwidth = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new java.awt.Insets(0, 8, 6, 5);
    mainPanel.add(jPanel3, gridBagConstraints);

    tfPwd.setName("password"); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(2, 6, 4, 2);
    mainPanel.add(tfPwd, gridBagConstraints);

    lblUsername.setLabelFor(tfUserName);
    lblUsername.setText(ResourceMgr.getString("LblUsername")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(2, 5, 2, 0);
    mainPanel.add(lblUsername, gridBagConstraints);

    timeoutpanel.setLayout(new java.awt.GridBagLayout());
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(3, 6, 0, 5);
    mainPanel.add(timeoutpanel, gridBagConstraints);

    jPanel6.setLayout(new java.awt.GridBagLayout());

    editConnectionScriptsButton.setText(ResourceMgr.getString("LblConnScripts")); // NOI18N
    editConnectionScriptsButton.setToolTipText(ResourceMgr.getString("d_LblConnScripts")); // NOI18N
    editConnectionScriptsButton.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);
    editConnectionScriptsButton.addActionListener(formListener);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    jPanel6.add(editConnectionScriptsButton, gridBagConstraints);

    editFilterButton.setText(ResourceMgr.getString("LblSchemaFilterBtn")); // NOI18N
    editFilterButton.setToolTipText(ResourceMgr.getString("d_LblSchemaFilterBtn")); // NOI18N
    editFilterButton.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);
    editFilterButton.addActionListener(formListener);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 12, 0, 0);
    jPanel6.add(editFilterButton, gridBagConstraints);

    editVariablesButton.setText(ResourceMgr.getString("TxtVariables")); // NOI18N
    editVariablesButton.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);
    editVariablesButton.addActionListener(formListener);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 12, 0, 0);
    jPanel6.add(editVariablesButton, gridBagConstraints);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    jPanel6.add(filler2, gridBagConstraints);

    testConnectionButton.setText(ResourceMgr.getString("LblTestConn")); // NOI18N
    testConnectionButton.addActionListener(formListener);
    jPanel6.add(testConnectionButton, new java.awt.GridBagConstraints());

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 16;
    gridBagConstraints.gridwidth = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(5, 6, 14, 10);
    mainPanel.add(jPanel6, gridBagConstraints);

    lblDriver.setLabelFor(cbDrivers);
    lblDriver.setText(ResourceMgr.getString("LblDriver")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 5, 2, 0);
    mainPanel.add(lblDriver, gridBagConstraints);

    cbDrivers.setMaximumSize(new java.awt.Dimension(32767, 64));
    cbDrivers.setName("driverclass"); // NOI18N
    cbDrivers.setVerifyInputWhenFocusTarget(false);
    cbDrivers.addItemListener(formListener);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 0.5;
    gridBagConstraints.insets = new java.awt.Insets(0, 6, 2, 5);
    mainPanel.add(cbDrivers, gridBagConstraints);

    lblUrl.setLabelFor(tfURL);
    lblUrl.setText(ResourceMgr.getString("LblDbURL")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(1, 5, 2, 0);
    mainPanel.add(lblUrl, gridBagConstraints);

    tfUserName.setHorizontalAlignment(javax.swing.JTextField.LEFT);
    tfUserName.setName("username"); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(2, 6, 2, 2);
    mainPanel.add(tfUserName, gridBagConstraints);

    jPanel2.setLayout(new java.awt.GridBagLayout());

    tfFetchSize.setToolTipText(ResourceMgr.getString("d_LblFetchSize")); // NOI18N
    tfFetchSize.setName("defaultFetchSize"); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 3, 0, 0);
    jPanel2.add(tfFetchSize, gridBagConstraints);

    cbAutocommit.setText("Autocommit");
    cbAutocommit.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);
    cbAutocommit.setName("autocommit"); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    jPanel2.add(cbAutocommit, gridBagConstraints);

    extendedProps.setText(ResourceMgr.getString("LblConnExtendedProps")); // NOI18N
    extendedProps.setToolTipText(ResourceMgr.getString("d_LblConnExtendedProps")); // NOI18N
    extendedProps.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);
    extendedProps.addActionListener(formListener);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 7;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    jPanel2.add(extendedProps, gridBagConstraints);

    timeoutLabel.setLabelFor(tfTimeout);
    timeoutLabel.setText(ResourceMgr.getString("LblConnTimeout")); // NOI18N
    timeoutLabel.setToolTipText(ResourceMgr.getString("d_LblConnTimeout")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 3;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(1, 16, 1, 0);
    jPanel2.add(timeoutLabel, gridBagConstraints);

    tfTimeout.setToolTipText(ResourceMgr.getString("d_LblConnTimeout")); // NOI18N
    tfTimeout.setName("connectionTimeout"); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 4;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 3, 0, 0);
    jPanel2.add(tfTimeout, gridBagConstraints);

    jLabel1.setText(ResourceMgr.getString("LblSeconds")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 5;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(0, 3, 0, 0);
    jPanel2.add(jLabel1, gridBagConstraints);

    fetchSizeLabel.setLabelFor(tfFetchSize);
    fetchSizeLabel.setText(ResourceMgr.getString("LblFetchSize")); // NOI18N
    fetchSizeLabel.setToolTipText(ResourceMgr.getString("d_LblFetchSize")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(1, 15, 1, 0);
    jPanel2.add(fetchSizeLabel, gridBagConstraints);

    sshConfig.setText(ResourceMgr.getString("LblSshConfig")); // NOI18N
    sshConfig.setToolTipText(ResourceMgr.getString("d_LblSshConfig")); // NOI18N
    sshConfig.setHorizontalTextPosition(javax.swing.SwingConstants.LEADING);
    sshConfig.addActionListener(formListener);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 6;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_END;
    gridBagConstraints.insets = new java.awt.Insets(0, 0, 0, 5);
    jPanel2.add(sshConfig, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.gridwidth = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(1, 1, 0, 5);
    mainPanel.add(jPanel2, gridBagConstraints);

    tfURL.setHorizontalAlignment(javax.swing.JTextField.LEFT);
    tfURL.setName("url"); // NOI18N
    tfURL.addFocusListener(formListener);
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(1, 6, 2, 5);
    mainPanel.add(tfURL, gridBagConstraints);

    wbOptionsPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));
    wbOptionsPanel.setLayout(new java.awt.GridBagLayout());

    cbStorePassword.setSelected(true);
    cbStorePassword.setText(ResourceMgr.getString("LblSavePassword")); // NOI18N
    cbStorePassword.setToolTipText(ResourceMgr.getString("d_LblSavePassword")); // NOI18N
    cbStorePassword.setBorder(null);
    cbStorePassword.setName("storePassword"); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 0, 6, 0);
    wbOptionsPanel.add(cbStorePassword, gridBagConstraints);

    rollbackBeforeDisconnect.setText(ResourceMgr.getString("LblRollbackBeforeDisconnect")); // NOI18N
    rollbackBeforeDisconnect.setToolTipText(ResourceMgr.getString("d_LblRollbackBeforeDisconnect")); // NOI18N
    rollbackBeforeDisconnect.setBorder(null);
    rollbackBeforeDisconnect.setMargin(new java.awt.Insets(2, 0, 2, 2));
    rollbackBeforeDisconnect.setName("rollbackBeforeDisconnect"); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 8, 6, 0);
    wbOptionsPanel.add(rollbackBeforeDisconnect, gridBagConstraints);

    cbIgnoreDropErrors.setSelected(true);
    cbIgnoreDropErrors.setText(ResourceMgr.getString("LblIgnoreDropErrors")); // NOI18N
    cbIgnoreDropErrors.setToolTipText(ResourceMgr.getString("d_LblIgnoreDropErrors")); // NOI18N
    cbIgnoreDropErrors.setBorder(null);
    cbIgnoreDropErrors.setName("ignoreDropErrors"); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 0, 6, 0);
    wbOptionsPanel.add(cbIgnoreDropErrors, gridBagConstraints);

    cbSeparateConnections.setText(ResourceMgr.getString("LblSeparateConnections")); // NOI18N
    cbSeparateConnections.setToolTipText(ResourceMgr.getString("d_LblSeparateConnections")); // NOI18N
    cbSeparateConnections.setBorder(null);
    cbSeparateConnections.setName("useSeparateConnectionPerTab"); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 0, 6, 0);
    wbOptionsPanel.add(cbSeparateConnections, gridBagConstraints);

    emptyStringIsNull.setText(ResourceMgr.getString("LblEmptyStringIsNull")); // NOI18N
    emptyStringIsNull.setToolTipText(ResourceMgr.getString("d_LblEmptyStringIsNull")); // NOI18N
    emptyStringIsNull.setBorder(null);
    emptyStringIsNull.setName("emptyStringIsNull"); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 8, 6, 0);
    wbOptionsPanel.add(emptyStringIsNull, gridBagConstraints);

    includeNull.setText(ResourceMgr.getString("LblIncludeNullInInsert")); // NOI18N
    includeNull.setToolTipText(ResourceMgr.getString("d_LblIncludeNullInInsert")); // NOI18N
    includeNull.setBorder(null);
    includeNull.setMargin(new java.awt.Insets(2, 0, 2, 2));
    includeNull.setName("includeNullInInsert"); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 8, 6, 0);
    wbOptionsPanel.add(includeNull, gridBagConstraints);

    removeComments.setText(ResourceMgr.getString("LblRemoveComments")); // NOI18N
    removeComments.setToolTipText(ResourceMgr.getString("d_LblRemoveComments")); // NOI18N
    removeComments.setBorder(null);
    removeComments.setMargin(new java.awt.Insets(2, 0, 2, 2));
    removeComments.setName("removeComments"); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(0, 8, 6, 9);
    wbOptionsPanel.add(removeComments, gridBagConstraints);

    rememberExplorerSchema.setText(ResourceMgr.getString("LblRememberSchema")); // NOI18N
    rememberExplorerSchema.setToolTipText(ResourceMgr.getString("d_LblRememberSchema")); // NOI18N
    rememberExplorerSchema.setBorder(null);
    rememberExplorerSchema.setMargin(new java.awt.Insets(2, 0, 2, 2));
    rememberExplorerSchema.setName("storeExplorerSchema"); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 8, 6, 9);
    wbOptionsPanel.add(rememberExplorerSchema, gridBagConstraints);

    trimCharData.setText(ResourceMgr.getString("LblTrimCharData")); // NOI18N
    trimCharData.setToolTipText(ResourceMgr.getString("d_LblTrimCharData")); // NOI18N
    trimCharData.setBorder(null);
    trimCharData.setName("trimCharData"); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 0, 6, 0);
    wbOptionsPanel.add(trimCharData, gridBagConstraints);

    controlUpdates.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));

    confirmUpdates.setText(ResourceMgr.getString("LblConfirmDbUpdates")); // NOI18N
    confirmUpdates.setToolTipText(ResourceMgr.getString("d_LblConfirmDbUpdates")); // NOI18N
    confirmUpdates.setBorder(null);
    confirmUpdates.setMargin(new java.awt.Insets(2, 0, 2, 5));
    confirmUpdates.setName("confirmUpdates"); // NOI18N
    controlUpdates.add(confirmUpdates);

    readOnly.setText(ResourceMgr.getString("LblConnReadOnly")); // NOI18N
    readOnly.setToolTipText(ResourceMgr.getString("d_LblConnReadOnly")); // NOI18N
    readOnly.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 5, 0, 0));
    readOnly.setMargin(new java.awt.Insets(2, 5, 2, 2));
    readOnly.setName("readOnly"); // NOI18N
    controlUpdates.add(readOnly);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 8, 6, 0);
    wbOptionsPanel.add(controlUpdates, gridBagConstraints);

    hideWarnings.setText(ResourceMgr.getString("LblHideWarn")); // NOI18N
    hideWarnings.setToolTipText(ResourceMgr.getString("d_LblHideWarn")); // NOI18N
    hideWarnings.setBorder(null);
    hideWarnings.setName("hideWarnings"); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 8, 6, 9);
    wbOptionsPanel.add(hideWarnings, gridBagConstraints);

    checkOpenTrans.setText(ResourceMgr.getString("LblCheckUncommitted")); // NOI18N
    checkOpenTrans.setToolTipText(ResourceMgr.getString("d_LblCheckUncommitted")); // NOI18N
    checkOpenTrans.setBorder(null);
    checkOpenTrans.setMargin(new java.awt.Insets(2, 0, 2, 2));
    checkOpenTrans.setName("detectOpenTransaction"); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 8, 6, 9);
    wbOptionsPanel.add(checkOpenTrans, gridBagConstraints);

    preventNoWhere.setText(ResourceMgr.getString("LblConnPreventNoWhere")); // NOI18N
    preventNoWhere.setToolTipText(ResourceMgr.getString("d_LblConnPreventNoWhere")); // NOI18N
    preventNoWhere.setBorder(null);
    preventNoWhere.setName("preventDMLWithoutWhere"); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 8, 6, 0);
    wbOptionsPanel.add(preventNoWhere, gridBagConstraints);

    cbxPromptUsername.setText(ResourceMgr.getString("LblPrompUsername")); // NOI18N
    cbxPromptUsername.setToolTipText(ResourceMgr.getString("d_LblPrompUsername")); // NOI18N
    cbxPromptUsername.setBorder(null);
    cbxPromptUsername.setName("promptForUsername"); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new java.awt.Insets(0, 0, 6, 0);
    wbOptionsPanel.add(cbxPromptUsername, gridBagConstraints);

    jCheckBox1.setText(ResourceMgr.getString("LblLocalStorageType")); // NOI18N
    jCheckBox1.setBorder(null);
    jCheckBox1.setName("storeCacheLocally"); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(0, 8, 6, 9);
    wbOptionsPanel.add(jCheckBox1, gridBagConstraints);

    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 9;
    gridBagConstraints.gridwidth = 3;
    gridBagConstraints.gridheight = 4;
    gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(8, 5, 8, 5);
    mainPanel.add(wbOptionsPanel, gridBagConstraints);

    showPassword.setText(ResourceMgr.getString("LblShowPassword")); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.insets = new java.awt.Insets(2, 2, 4, 5);
    mainPanel.add(showPassword, gridBagConstraints);

    tfProfileName.setHorizontalAlignment(javax.swing.JTextField.LEFT);
    tfProfileName.setName("name"); // NOI18N
    gridBagConstraints = new java.awt.GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.gridwidth = 3;
    gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new java.awt.Insets(7, 5, 6, 5);
    mainPanel.add(tfProfileName, gridBagConstraints);

    add(mainPanel, java.awt.BorderLayout.CENTER);
  }

  // Code for dispatching events from components to event handlers.

  private class FormListener implements java.awt.event.ActionListener, java.awt.event.FocusListener, java.awt.event.ItemListener {
    FormListener() {}
    public void actionPerformed(java.awt.event.ActionEvent evt) {
      if (evt.getSource() == editConnectionScriptsButton) {
        ConnectionEditorPanel.this.editConnectionScriptsButtonActionPerformed(evt);
      }
      else if (evt.getSource() == editFilterButton) {
        ConnectionEditorPanel.this.editFilterButtonActionPerformed(evt);
      }
      else if (evt.getSource() == editVariablesButton) {
        ConnectionEditorPanel.this.editVariablesButtonActionPerformed(evt);
      }
      else if (evt.getSource() == testConnectionButton) {
        ConnectionEditorPanel.this.testConnectionButtonActionPerformed(evt);
      }
      else if (evt.getSource() == extendedProps) {
        ConnectionEditorPanel.this.extendedPropsActionPerformed(evt);
      }
      else if (evt.getSource() == sshConfig) {
        ConnectionEditorPanel.this.sshConfigActionPerformed(evt);
      }
    }

    public void focusGained(java.awt.event.FocusEvent evt) {
    }

    public void focusLost(java.awt.event.FocusEvent evt) {
      if (evt.getSource() == tfURL) {
        ConnectionEditorPanel.this.tfURLFocusLost(evt);
      }
    }

    public void itemStateChanged(java.awt.event.ItemEvent evt) {
      if (evt.getSource() == cbDrivers) {
        ConnectionEditorPanel.this.cbDriversItemStateChanged(evt);
      }
    }
  }// </editor-fold>//GEN-END:initComponents

  private void cbDriversItemStateChanged(java.awt.event.ItemEvent evt)//GEN-FIRST:event_cbDriversItemStateChanged
  {//GEN-HEADEREND:event_cbDriversItemStateChanged
    if (this.init)
    {
      return;
    }

    boolean changed = false;
    if (evt.getStateChange() == ItemEvent.SELECTED)
    {
      String oldDriver = null;
      DbDriver newDriver = null;
      try
      {
        oldDriver = this.currentProfile.getDriverclass();
        newDriver = (DbDriver)this.cbDrivers.getSelectedItem();
        if (this.currentProfile != null)
        {
          this.currentProfile.setDriver(newDriver);
        }
        if (oldDriver == null || !oldDriver.equals(newDriver.getDriverClass()))
        {
          changed = true;
          this.tfURL.setText(newDriver.getSampleUrl());
        }
      }
      catch (Exception e)
      {
        LogMgr.logError(new CallerInfo(){}, "Error changing driver", e);
      }

      if (changed)
      {
        checkAutoCommit();
      }
      checkOracle();
      checkUncommitted();

      if (newDriver != null && !newDriver.canReadLibrary())
      {
        final Frame parent = (Frame)(SwingUtilities.getWindowAncestor(this).getParent());
        final DbDriver toSelect = newDriver;

        EventQueue.invokeLater(() ->
        {
          if (WbSwingUtilities.getYesNo(ConnectionEditorPanel.this, ResourceMgr.getString("MsgDriverLibraryNotReadable")))
          {
            DriverEditorDialog.showDriverDialog(parent, toSelect);
          }
        });
      }
    }
  }//GEN-LAST:event_cbDriversItemStateChanged

  private void editConnectionScriptsButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_editConnectionScriptsButtonActionPerformed
    Dialog d = (Dialog)SwingUtilities.getWindowAncestor(this);
    EditConnectScriptsPanel.editScripts(d, this.getProfile());
    checkScriptsAndFilters();
  }//GEN-LAST:event_editConnectionScriptsButtonActionPerformed

  private void editFilterButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_editFilterButtonActionPerformed
  {//GEN-HEADEREND:event_editFilterButtonActionPerformed
    Dialog d = (Dialog)SwingUtilities.getWindowAncestor(this);
    EditConnectionFiltersPanel.editFilter(d, this.getProfile());
    checkScriptsAndFilters();
  }//GEN-LAST:event_editFilterButtonActionPerformed

  private void tfURLFocusLost(java.awt.event.FocusEvent evt)//GEN-FIRST:event_tfURLFocusLost
  {//GEN-HEADEREND:event_tfURLFocusLost
    checkOracle();
  }//GEN-LAST:event_tfURLFocusLost

  private void editVariablesButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_editVariablesButtonActionPerformed
  {//GEN-HEADEREND:event_editVariablesButtonActionPerformed
    ConnectionProfile profile = getProfile();

    Properties variables = profile.getConnectionVariables();
    if (variables == null)
    {
      variables = new Properties();
    }

    MapEditor editor = new MapEditor(variables);
    Dialog d = (Dialog)SwingUtilities.getWindowAncestor(this);
    ValidatingDialog dialog = ValidatingDialog.createDialog(d, editor, ResourceMgr.getString("TxtEditConnVars"), null, 0, false);

    if (!Settings.getInstance().restoreWindowSize(dialog, "workbench.gui.edit.profile.variables"))
    {
      dialog.setSize(400, 300);
    }
    editor.optimizeColumnWidths();
    dialog.setVisible(true);

    if (!dialog.isCancelled())
    {
      profile.setConnectionVariables(editor.getProperties());
      checkScriptsAndFilters();
    }
  }//GEN-LAST:event_editVariablesButtonActionPerformed

  private void testConnectionButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_testConnectionButtonActionPerformed
  {//GEN-HEADEREND:event_testConnectionButtonActionPerformed
    ConnectionGuiHelper.testConnection(this, getProfile());
  }//GEN-LAST:event_testConnectionButtonActionPerformed

  private void sshConfigActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_sshConfigActionPerformed
  {//GEN-HEADEREND:event_sshConfigActionPerformed
    ConnectionProfile profile = getProfile();

    SshConfigPanel editor = new SshConfigPanel();
    editor.setConfig(profile.getSshConfig(), tfURL.getText());
    Dialog d = (Dialog)SwingUtilities.getWindowAncestor(this);
    ValidatingDialog dialog = ValidatingDialog.createDialog(d, editor, "SSH Configuration", null, 0, false);

    String settingsId = "workbench.gui.edit.profile.sshconfig";
    if (!Settings.getInstance().restoreWindowSize(dialog, settingsId))
    {
      dialog.pack();
      dialog.setSize((int)(dialog.getWidth() * 1.5), (int)(dialog.getHeight() * 1.05));
    }
    WbSwingUtilities.center(dialog, d);
    dialog.setVisible(true);

    Settings.getInstance().storeWindowSize(dialog, settingsId);

    if (!dialog.isCancelled())
    {
      SshConfig config = editor.getConfig();
      profile.setSshConfig(config);

      if (config != null && editor.rewriteURL())
      {
        UrlParser parser = new UrlParser(profile.getUrl());
        String newUrl = parser.getLocalUrl(config.getLocalPort());
        this.tfURL.setText(newUrl);
      }
      checkSSHIcon();
    }
  }//GEN-LAST:event_sshConfigActionPerformed

  private void extendedPropsActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_extendedPropsActionPerformed
  {//GEN-HEADEREND:event_extendedPropsActionPerformed
    this.editExtendedProperties();
  }//GEN-LAST:event_extendedPropsActionPerformed

  // Variables declaration - do not modify//GEN-BEGIN:variables
  protected javax.swing.JLabel altDelimLabel;
  protected javax.swing.JTextField altDelimiter;
  protected javax.swing.JCheckBox asSysDBA;
  protected javax.swing.JCheckBox cbAutocommit;
  protected javax.swing.JComboBox cbDrivers;
  protected javax.swing.JCheckBox cbIgnoreDropErrors;
  protected javax.swing.JCheckBox cbSeparateConnections;
  protected javax.swing.JCheckBox cbStorePassword;
  protected javax.swing.JCheckBox cbxPromptUsername;
  protected javax.swing.JCheckBox checkOpenTrans;
  protected javax.swing.JCheckBox confirmUpdates;
  protected javax.swing.JPanel controlUpdates;
  protected javax.swing.JButton editConnectionScriptsButton;
  protected javax.swing.JButton editFilterButton;
  protected javax.swing.JButton editVariablesButton;
  protected javax.swing.JCheckBox emptyStringIsNull;
  protected javax.swing.JButton extendedProps;
  protected javax.swing.JLabel fetchSizeLabel;
  protected javax.swing.Box.Filler filler2;
  protected javax.swing.JLabel groupNameLabel;
  protected javax.swing.JCheckBox hideWarnings;
  protected javax.swing.JTextField icon;
  protected javax.swing.JCheckBox includeNull;
  protected workbench.gui.components.WbColorPicker infoColor;
  protected javax.swing.JLabel infoColorLabel;
  protected javax.swing.JCheckBox jCheckBox1;
  protected javax.swing.JLabel jLabel1;
  protected javax.swing.JLabel jLabel2;
  protected javax.swing.JLabel jLabel3;
  protected javax.swing.JPanel jPanel1;
  protected javax.swing.JPanel jPanel2;
  protected javax.swing.JPanel jPanel3;
  protected javax.swing.JPanel jPanel4;
  protected javax.swing.JPanel jPanel5;
  protected javax.swing.JPanel jPanel6;
  protected javax.swing.JPanel jPanel7;
  protected javax.swing.JLabel lblDriver;
  protected javax.swing.JLabel lblPwd;
  protected javax.swing.JLabel lblUrl;
  protected javax.swing.JLabel lblUsername;
  protected javax.swing.JTextField macroFile;
  protected javax.swing.JLabel macroFileLabel;
  protected javax.swing.JPanel mainPanel;
  protected javax.swing.JCheckBox preventNoWhere;
  protected javax.swing.JCheckBox readOnly;
  protected javax.swing.JCheckBox rememberExplorerSchema;
  protected javax.swing.JCheckBox removeComments;
  protected javax.swing.JCheckBox rollbackBeforeDisconnect;
  protected javax.swing.JLabel scriptDirLabel;
  protected javax.swing.JButton selectIconButton;
  protected javax.swing.JButton selectMacroFileButton;
  protected javax.swing.JButton selectScriptDirButton;
  protected javax.swing.JButton selectWkspButton;
  protected javax.swing.JButton showPassword;
  protected javax.swing.JButton sshConfig;
  protected javax.swing.JTextField tagList;
  protected javax.swing.JButton testConnectionButton;
  protected javax.swing.JTextField tfFetchSize;
  protected javax.swing.JTextField tfProfileName;
  protected javax.swing.JPasswordField tfPwd;
  protected javax.swing.JTextField tfScriptDir;
  protected javax.swing.JTextField tfTimeout;
  protected javax.swing.JTextField tfURL;
  protected javax.swing.JTextField tfUserName;
  protected javax.swing.JTextField tfWorkspaceFile;
  protected javax.swing.JLabel timeoutLabel;
  protected javax.swing.JPanel timeoutpanel;
  protected javax.swing.JCheckBox trimCharData;
  protected javax.swing.JPanel wbOptionsPanel;
  protected javax.swing.JLabel workspaceFileLabel;
  // End of variables declaration//GEN-END:variables

  public void setDrivers(List<DbDriver> aDriverList)
  {
    if (aDriverList != null)
    {
      this.init = true;
      Object currentDriver = this.cbDrivers.getSelectedItem();
      try
      {
        Comparator<DbDriver> comparator = (DbDriver o1, DbDriver o2) -> StringUtil.compareStrings(o1.getName(), o2.getName(), true);

        Collections.sort(aDriverList, comparator);
        this.cbDrivers.setModel(new DefaultComboBoxModel(aDriverList.toArray()));
        if (currentDriver != null)
        {
          this.cbDrivers.setSelectedItem(currentDriver);
        }
      }
      catch (Exception e)
      {
        LogMgr.logError(new CallerInfo(){}, "Error when setting new driver list", e);
      }
      finally
      {
        this.init = false;
      }
    }
  }

  public void editExtendedProperties()
  {
    EventQueue.invokeLater(() ->
    {
      ConnectionPropertiesEditor.editProperties(SwingUtilities.getWindowAncestor(ConnectionEditorPanel.this), currentProfile);
      checkExtendedProps();
    });
  }

  public void selectMacroFile()
  {
    MacroFileSelector selector = new MacroFileSelector();
    File file = selector.selectStorageForLoad(this, MacroManager.DEFAULT_STORAGE);
    if (file != null)
    {
      String path;
      if (Settings.getInstance().shortenMacroFileName())
      {
        path = FileDialogUtil.removeMacroDir(file);
      }
      else
      {
        path = file.getAbsolutePath();
      }
      macroFile.setText(path);
      macroFile.setCaretPosition(0);
    }
  }

  public void selectIcon()
  {
    String last = Settings.getInstance().getProperty("workbench.iconfile.lastdir", null);
    File lastDir = null;

    if (StringUtil.isNotBlank(last))
    {
      lastDir = new File(last);
    }
    else
    {
      lastDir = Settings.getInstance().getConfigDir();
    }

    JFileChooser fc = new WbFileChooser(lastDir);
    ExtensionFileFilter ff = new ExtensionFileFilter(ResourceMgr.getString("TxtFileFilterIcons"), CollectionUtil.arrayList("png","gif"), true);
    fc.setMultiSelectionEnabled(true);
    fc.setFileSelectionMode(JFileChooser.FILES_ONLY);
    fc.removeChoosableFileFilter(fc.getAcceptAllFileFilter());
    fc.addChoosableFileFilter(ff);
    fc.setFileFilter(ff);
    fc.setDialogTitle(ResourceMgr.getString("MsgSelectIcon"));

    int answer = fc.showOpenDialog(SwingUtilities.getWindowAncestor(this));

    if (answer == JFileChooser.APPROVE_OPTION)
    {
      File[] files = fc.getSelectedFiles();
      String fnames = "";
      String sep = System.getProperty("path.separator");
      int imgCount = 0;

      for (File sf : files)
      {
        WbFile fl = new WbFile(sf);
        if (ImageUtil.isPng(sf) || ImageUtil.isGifIcon(sf))
        {
          if (imgCount > 0) fnames += sep;
          fnames += fl.getFullPath();
          imgCount ++;
        }
        else
        {
          String msg = ResourceMgr.getFormattedString("ErrInvalidIcon", fl.getName());
          WbSwingUtilities.showErrorMessage(this, msg);
          fnames = null;
          break;
        }
      }

      if (fnames != null)
      {
        this.icon.setText(fnames);
        this.icon.setCaretPosition(0);
      }
      WbFile dir = new WbFile(fc.getCurrentDirectory());
      Settings.getInstance().setProperty("workbench.iconfile.lastdir", dir.getFullPath());
    }
  }

  public void selectScriptDirectory()
  {
    String currentDir = StringUtil.trimToNull(tfScriptDir.getText());
    JFileChooser jf = new WbFileChooser();
    jf.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    jf.setMultiSelectionEnabled(false);
    jf.setDialogTitle(ResourceMgr.getString("MsgSelectScriptDir"));
    if (currentDir != null)
    {
      File f = new File(currentDir);
      if (f.exists())
      {
        jf.setCurrentDirectory(f);
      }
    }
    int answer = jf.showOpenDialog(SwingUtilities.getWindowAncestor(this));
    if (answer == JFileChooser.APPROVE_OPTION)
    {
      WbFile dir = new WbFile(jf.getSelectedFile());
      tfScriptDir.setText(dir.getFullPath());
    }
  }

  public void selectWorkspace()
  {
    WorkspaceSelector selector = new WorkspaceSelector(SwingUtilities.getWindowAncestor(this));
    String filename = selector.showLoadDialog(false);
    if (filename == null)
    {
      return;
    }
    this.tfWorkspaceFile.setText(filename);
  }

  public void addProfileChangeListener(ProfileChangeListener listener)
  {
    this.changeListener.add(listener);
  }

  public void updateProfile()
  {
    if (this.init) return;
    if (this.currentProfile == null) return;
    if (this.editors == null) return;

    boolean changed = false;

    for (SimplePropertyEditor editor : editors)
    {
      changed = changed || editor.isChanged();
      editor.applyChanges();
    }

    DbDriver current = getCurrentDriver();
    String driverName = currentProfile.getDriverName();
    String drvClass = currentProfile.getDriverclass();
    if (current != null && (!current.getName().equals(driverName) || !current.getDriverClass().equals(drvClass)))
    {
      // an alternate driver was chosen, because the original driver was not available.
      LogMgr.logDebug(new CallerInfo(){}, "Adjusting selected driver name for non-existing driver: " + currentProfile.getIdString());
      currentProfile.setDriver(current);
      changed = true;
    }

    if (changed)
    {
      fireProfileChanged(this.currentProfile);
    }
  }

  public DbDriver getCurrentDriver()
  {
    DbDriver drv = (DbDriver)cbDrivers.getSelectedItem();
    return drv;
  }

  public ConnectionProfile getProfile()
  {
    this.updateProfile();
    return this.currentProfile;
  }

  private void initPropertyEditors()
  {
    if (this.editors == null) return;
    if (this.currentProfile == null) return;

    for (SimplePropertyEditor editor : editors)
    {
      Component c = (Component)editor;
      String property = c.getName();
      if (property != null)
      {
        editor.setSourceObject(this.currentProfile, property);
      }
    }
  }

  private int getFilterSize(ObjectNameFilter f)
  {
    if (f == null) return 0;
    return f.getSize();
  }

  private void checkPromptUsername()
  {
    boolean prompt = cbxPromptUsername.isSelected();
    tfUserName.setEnabled(!prompt);
    tfPwd.setEnabled(!prompt);
    cbStorePassword.setEnabled(!prompt);
  }

  private void checkSSHIcon()
  {
    sshConfig.setIcon(null);
    SshConfig config = getProfile().getSshConfig();
    if (config != null)
    {
      sshConfig.setIcon(IconMgr.getInstance().getLabelIcon("tick"));
    }
  }

  private void checkScriptsAndFilters()
  {
    editConnectionScriptsButton.setIcon(null);
    editFilterButton.setIcon(null);
    editVariablesButton.setIcon(null);

    int f1 = currentProfile == null ? 0 : getFilterSize(currentProfile.getSchemaFilter());
    int f2 = currentProfile == null ? 0 : getFilterSize(currentProfile.getCatalogFilter());

    boolean hasFilter = (f1 + f2) > 0;

    if (hasFilter)
    {
      editFilterButton.setIcon(IconMgr.getInstance().getLabelIcon("tick"));
    }

    boolean hasScript = (currentProfile == null ? false : currentProfile.hasConnectScript());
    if (hasScript)
    {
      editConnectionScriptsButton.setIcon(IconMgr.getInstance().getLabelIcon("tick"));
    }

    if (CollectionUtil.isNonEmpty(currentProfile.getConnectionVariables()))
    {
      editVariablesButton.setIcon(IconMgr.getInstance().getLabelIcon("tick"));
    }
  }

  private void checkExtendedProps()
  {
    Properties props = (currentProfile == null ? null : currentProfile.getConnectionProperties());
    if (props != null && props.size() > 0)
    {
      extendedProps.setIcon(IconMgr.getInstance().getLabelIcon("tick"));
    }
    else
    {
      extendedProps.setIcon(null);
    }
  }

  public void setProfile(ConnectionProfile aProfile)
  {
    if (aProfile == null)
    {
      return;
    }

    this.currentProfile = aProfile;

    try
    {
      this.init = true;

      groupNameLabel.setText(currentProfile.getGroup());
      this.initPropertyEditors();

      String drvClass = aProfile.getDriverclass();
      DbDriver drv = null;
      if (drvClass != null)
      {
        String name = aProfile.getDriverName();
        drv = ConnectionMgr.getInstance().findDriverByName(drvClass, name);
      }

      cbDrivers.setSelectedItem(drv);

      Color c = this.currentProfile.getInfoDisplayColor();
      this.infoColor.setSelectedColor(c);
      checkExtendedProps();
      checkScriptsAndFilters();
      checkSSHIcon();
      checkOracle();
      checkUncommitted();
      checkPromptUsername();
      syncColor();
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error setting profile", e);
    }
    finally
    {
      this.init = false;
    }
  }

  private void checkUncommitted()
  {
    String drvClass = getCurrentDriver() == null ? null : getCurrentDriver().getDriverClass();
    boolean canCheck = TransactionChecker.Factory.supportsTransactionCheck(drvClass);
    if (canCheck && drvClass != null)
    {
      checkOpenTrans.setEnabled(true);
      checkOpenTrans.setSelected(currentProfile.getDetectOpenTransaction());
      if (drvClass.contains("oracle"))
      {
        checkOpenTrans.setToolTipText(ResourceMgr.getDescription("LblCheckUncommittedOra"));
      }
      else
      {
        checkOpenTrans.setToolTipText(ResourceMgr.getDescription("LblCheckUncommitted"));
      }
    }
    else
    {
      checkOpenTrans.setEnabled(false);
      checkOpenTrans.setSelected(false);
      checkOpenTrans.setToolTipText(ResourceMgr.getDescription("LblCheckUncommittedNA"));
    }
  }

  private void checkAutoCommit()
  {
    if (!Settings.getInstance().adjustNewProfileAutoCommit()) return;

    if (this.currentProfile == null) return;
    if (!this.currentProfile.isNew()) return;
    if (this.currentProfile.getAutocommit()) return;

    String currentDriver = this.currentProfile.getDriverclass();
    if (currentDriver == null) return;

    Set<String> classes = Settings.getInstance().getUseAutoCommitForNewProfile();
    if (classes.contains(currentDriver))
    {
      this.cbAutocommit.setSelected(true);
    }
  }

  private void checkOracle()
  {
    String url = this.tfURL.getText();
    GridBagLayout layout = (GridBagLayout)mainPanel.getLayout();
    GridBagConstraints cons = layout.getConstraints(tfUserName);
    if (url.startsWith("jdbc:oracle:"))
    {
      cons.gridwidth = 1;
      cons.insets = new Insets(0, 6, 2, 2);
      layout.setConstraints(tfUserName, cons);
      asSysDBA.setVisible(true);
      asSysDBA.setEnabled(true);
      asSysDBA.setSelected(currentProfile.getOracleSysDBA());
    }
    else
    {
      asSysDBA.setVisible(false);
      asSysDBA.setEnabled(false);
      asSysDBA.setSelected(false);
      cons.gridwidth = 2;
      cons.insets = new Insets(0, 6, 2, 5);
      layout.setConstraints(tfUserName, cons);
    }
  }

  /**
   * This method gets called when a bound property is changed.
   *
   * @param evt A PropertyChangeEvent object describing the event source
   * and the property that has changed.
   *
   */
  @Override
  public void propertyChange(PropertyChangeEvent evt)
  {
    if (!this.init)
    {
      fireProfileChanged(this.currentProfile);
    }
  }

  private void fireProfileChanged(ConnectionProfile profile)
  {
    for (ProfileChangeListener listener : changeListener)
    {
      listener.profileChanged(profile);
    }
  }

  private void syncColor()
  {
    Color color = infoColor.getSelectedColor();
    if (color == null)
    {
      this.groupNameLabel.setBackground(GuiSettings.getEditorBackground());
      this.groupNameLabel.setForeground(GuiSettings.getEditorForeground());
    }
    else
    {
      this.groupNameLabel.setBackground(color);
      if (GuiSettings.useContrastColor())
      {
        this.groupNameLabel.setForeground(ColorUtils.getContrastColor(color));
      }
    }
  }

  @Override
  public void actionPerformed(java.awt.event.ActionEvent e)
  {
    if (e.getSource() == this.readOnly)
    {
      if (readOnly.isSelected())
      {
        confirmUpdates.setSelected(false);
      }
    }
    else if (e.getSource() == this.confirmUpdates)
    {
      if (confirmUpdates.isSelected())
      {
        this.readOnly.setSelected(false);
      }
    }
    else if (e.getSource() == this.selectScriptDirButton)
    {
      this.selectScriptDirectory();
    }
    else if (e.getSource() == this.selectWkspButton)
    {
      this.selectWorkspace();
    }
    else if (e.getSource() == this.selectIconButton)
    {
      this.selectIcon();
    }
    else if (e.getSource() == this.selectMacroFileButton)
    {
      this.selectMacroFile();
    }
    else if (e.getSource() == this.showPassword)
    {
      if (tfPwd.getEchoChar() == (char)0)
      {
        tfPwd.setEchoChar(echoChar);
        tfPwd.putClientProperty("JPasswordField.cutCopyAllowed", false);
      }
      else
      {
        tfPwd.setEchoChar((char)0);
        tfPwd.putClientProperty("JPasswordField.cutCopyAllowed", true);
      }
    }
    else if (e.getSource() == this.infoColor && this.currentProfile != null)
    {
      this.currentProfile.setInfoDisplayColor(this.infoColor.getSelectedColor());
      syncColor();
    }
    else if (e.getSource() == cbxPromptUsername)
    {
      checkPromptUsername();
    }
  }

  @Override
  public boolean validateInput()
  {
    DelimiterDefinition delim = getProfile().getAlternateDelimiter();
    if (delim != null && delim.isStandard())
    {
      WbSwingUtilities.showErrorMessageKey(this, "ErrWrongAltDelim");
      return false;
    }
    return getProfile().isConfigured();
  }

  @Override
  public void componentWillBeClosed()
  {
    // nothing to do
  }

  @Override
  public void componentDisplayed()
  {
  // nothing to do
  }
}
