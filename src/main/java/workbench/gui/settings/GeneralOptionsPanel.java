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
package workbench.gui.settings;

import java.awt.Dialog;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Collection;
import java.util.Locale;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;

import workbench.interfaces.Disposable;
import workbench.interfaces.Restoreable;
import workbench.log.LogMgr;
import workbench.resource.GuiSettings;
import workbench.resource.IconMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.DividerBorder;
import workbench.gui.components.MasterPasswordDialog;
import workbench.gui.components.WbLabelField;
import workbench.gui.sql.IconHandler;

import workbench.util.GlobalPasswordManager;
import workbench.util.StringUtil;
import workbench.util.WbFile;
import workbench.util.WbLocale;

import static workbench.gui.settings.PlacementChooser.*;

/**
 *
 * @author  Thomas Kellerer
 */
public class GeneralOptionsPanel
  extends JPanel
  implements Restoreable, ActionListener, Disposable
{
  private boolean removeMasterPassword = false;
  private String newMasterPassword = null;

  public GeneralOptionsPanel()
  {
    super();
    initComponents();
    Border b = new CompoundBorder(DividerBorder.TOP_DIVIDER, new EmptyBorder(0, 0, 4, 0));
    tabOptionsPanel.setBorder(b);
  }

  @Override
  public void restoreSettings()
  {
    int days = Settings.getInstance().getUpdateCheckInterval();
    if (days <= 0)
    {
      updateInterval.setText("");
    }
    else
    {
      updateInterval.setText(Integer.toString(days));
    }
    languageDropDown.removeAllItems();
    String currentLang = Settings.getInstance().getLanguage().getLanguage();

    Collection<WbLocale> locales = Settings.getInstance().getLanguages();
    int index = 0;
    int currentIndex = -1;
    for (WbLocale l : locales)
    {
      languageDropDown.addItem(l);
      if (l.getLocale().getLanguage().equals(currentLang))
      {
        currentIndex = index;
      }
      index++;
    }
    if (currentIndex != -1)
    {
      languageDropDown.setSelectedIndex(currentIndex);
    }
    WbFile configFile = Settings.getInstance().getConfigFile();
    String s = ResourceMgr.getFormattedString("LblSettingsLocation", configFile.getFullPath());
    settingsfilename.setText(s);
    settingsfilename.setBorder(new EmptyBorder(0,0,0,0));

    WbFile logFile = LogMgr.getLogfile();
    logfileLabel.setText(ResourceMgr.getFormattedString("LblLogLocation", logFile == null ? "": logFile.getFullPath()));
    logfileLabel.setCaretPosition(0);
    logfileLabel.setBorder(new EmptyBorder(1, 0, 1, 0));

    singlePageHelp.setSelected(Settings.getInstance().useSinglePageHelp());
    int tabPolicy = Settings.getInstance().getIntProperty(Settings.PROPERTY_TAB_POLICY, JTabbedPane.WRAP_TAB_LAYOUT);
    scrollTabs.setSelected(tabPolicy == JTabbedPane.SCROLL_TAB_LAYOUT);
    confirmTabClose.setSelected(GuiSettings.getConfirmTabClose());
    confirmMultiTabClose.setSelected(GuiSettings.getConfirmMultipleTabClose());
    showTabCloseButton.setSelected(GuiSettings.getShowSqlTabCloseButton());
    showResultTabClose.setSelected(GuiSettings.getShowResultTabCloseButton());
    onlyActiveTab.setSelected(GuiSettings.getCloseActiveTabOnly());
    closeButtonRightSide.setSelected(GuiSettings.getShowCloseButtonOnRightSide());
    tabLRUclose.setSelected(GuiSettings.getUseLRUForTabs());
    autoSaveProfiles.setSelected(Settings.getInstance().getSaveProfilesImmediately());
    enableQuickFilter.setSelected(GuiSettings.enableProfileQuickFilter());
    focusToQuickFilter.setSelected(GuiSettings.focusToProfileQuickFilter());
    showMenuIcons.setSelected(GuiSettings.showMenuIcons());
    varsPerWindow.setSelected(Settings.getInstance().useWindowSpecificVariables());
    restoreExpandedGroups.setSelected(GuiSettings.getRestoreExpandedProfileGroups());
    ((PlacementChooser)editorTabPlacement).showPlacement();
    ((PlacementChooser)resultTabPlacement).showPlacement();
    String iconName = Settings.getInstance().getProperty(IconHandler.PROP_LOADING_IMAGE, IconHandler.DEFAULT_BUSY_IMAGE);
    LoadingImage img = new LoadingImage();
    img.setName(iconName);
    iconCombobox.setSelectedItem(img);

    iconName = Settings.getInstance().getProperty(IconHandler.PROP_CANCEL_IMAGE, IconHandler.DEFAULT_CANCEL_IMAGE);
    img = new LoadingImage();
    img.setName(iconName);
    cancelIconCombo.setSelectedItem(img);
    if (Settings.getInstance().getUseMasterPassword())
    {
      masterPwdButton.setIcon(IconMgr.getInstance().getLabelIcon("tick"));
    }
    else
    {
      masterPwdButton.setIcon(null);
    }
  }

  @Override
  public void saveSettings()
  {
    Settings set = Settings.getInstance();

    // General settings
    GuiSettings.setShowCloseButtonOnRightSide(closeButtonRightSide.isSelected());
    GuiSettings.setCloseActiveTabOnly(onlyActiveTab.isSelected());
    GuiSettings.setShowTabCloseButton(showTabCloseButton.isSelected());
    GuiSettings.setShowResultTabCloseButton(showResultTabClose.isSelected());
    GuiSettings.setShowTabIndex(showTabIndex.isSelected());
    GuiSettings.setConfirmTabClose(confirmTabClose.isSelected());
    GuiSettings.setConfirmMultipleTabClose(confirmMultiTabClose.isSelected());
    GuiSettings.setEnableProfileQuickFilter(enableQuickFilter.isSelected());
    GuiSettings.setFocusToProfileQuickFilter(focusToQuickFilter.isSelected());
    GuiSettings.setShowMenuIcons(showMenuIcons.isSelected());
    GuiSettings.setRestoreExpandedProfileGroups(restoreExpandedGroups.isSelected());
    set.setConsolidateLogMsg(this.consolidateLog.isSelected());
    set.setExitOnFirstConnectCancel(exitOnConnectCancel.isSelected());
    set.setShowConnectDialogOnStartup(autoConnect.isSelected());
    set.setUpdateCheckInterval(StringUtil.getIntValue(updateInterval.getText(), -1));
    set.setLanguage(getSelectedLanguage());
    set.setUseSinglePageHelp(singlePageHelp.isSelected());
    if (scrollTabs.isSelected())
    {
      set.setProperty(Settings.PROPERTY_TAB_POLICY, JTabbedPane.SCROLL_TAB_LAYOUT);
    }
    else
    {
      set.setProperty(Settings.PROPERTY_TAB_POLICY, JTabbedPane.WRAP_TAB_LAYOUT);
    }
    GuiSettings.setUseLRUForTabs(tabLRUclose.isSelected());
    set.setSaveProfilesImmediately(autoSaveProfiles.isSelected());
    LoadingImage img = (LoadingImage)iconCombobox.getSelectedItem();
    set.setProperty(IconHandler.PROP_LOADING_IMAGE, img.getName());

    LoadingImage cancelImg = (LoadingImage)cancelIconCombo.getSelectedItem();
    set.setProperty(IconHandler.PROP_CANCEL_IMAGE, cancelImg.getName());
    set.setUseWindowSpecificVariables(varsPerWindow.isSelected());
    applyMasterPassword();
    ((PlacementChooser)editorTabPlacement).saveSelection();
    ((PlacementChooser)resultTabPlacement).saveSelection();
  }

  private void applyMasterPassword()
  {
    if (!removeMasterPassword && newMasterPassword == null) return;

    try
    {
      WbSwingUtilities.showWaitCursorOnWindow(this);
      if (removeMasterPassword)
      {
        GlobalPasswordManager.getInstance().applyNewPassword(null);
      }
      else
      {
        GlobalPasswordManager.getInstance().applyNewPassword(newMasterPassword);
      }
    }
    finally
    {
      WbSwingUtilities.showDefaultCursorOnWindow(this);
    }
  }

  @Override
  public void dispose()
  {
    ((IconListCombobox)iconCombobox).done();
    ((IconListCombobox)cancelIconCombo).done();
  }

  private Locale getSelectedLanguage()
  {
    WbLocale wl = (WbLocale)languageDropDown.getSelectedItem();
    return wl.getLocale();
  }

  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents()
  {
    GridBagConstraints gridBagConstraints;

    jPanel2 = new JPanel();
    consolidateLog = new JCheckBox();
    exitOnConnectCancel = new JCheckBox();
    autoConnect = new JCheckBox();
    singlePageHelp = new JCheckBox();
    autoSaveProfiles = new JCheckBox();
    enableQuickFilter = new JCheckBox();
    showMenuIcons = new JCheckBox();
    focusToQuickFilter = new JCheckBox();
    masterPwdButton = new JButton();
    varsPerWindow = new JCheckBox();
    restoreExpandedGroups = new JCheckBox();
    tabOptionsPanel = new JPanel();
    showTabIndex = new JCheckBox();
    scrollTabs = new JCheckBox();
    confirmTabClose = new JCheckBox();
    confirmMultiTabClose = new JCheckBox();
    showTabCloseButton = new JCheckBox();
    showResultTabClose = new JCheckBox();
    onlyActiveTab = new JCheckBox();
    closeButtonRightSide = new JCheckBox();
    tabLRUclose = new JCheckBox();
    imagePanel = new JPanel();
    iconCombobox = new IconListCombobox();
    busyIconLabel = new JLabel();
    cancelIconCombo = new IconListCombobox();
    cancelIconLabel = new JLabel();
    jPanel1 = new JPanel();
    jLabel1 = new JLabel();
    editorTabPlacement = new PlacementChooser(MAINWIN_TAB_PLACEMENT_PROPERTY);
    jLabel2 = new JLabel();
    resultTabPlacement = new PlacementChooser(RESULT_TAB_PLACEMENT_PROPERTY);
    jPanel5 = new JPanel();
    langLabel = new JLabel();
    languageDropDown = new JComboBox();
    checkUpdatesLabel = new JLabel();
    updateInterval = new JTextField();
    daysLabel = new JLabel();
    settingsfilename = new WbLabelField();
    logfileLabel = new WbLabelField();

    setLayout(new GridBagLayout());

    jPanel2.setLayout(new GridBagLayout());

    consolidateLog.setSelected(Settings.getInstance().getConsolidateLogMsg());
    consolidateLog.setText(ResourceMgr.getString("LblConsolidateLog")); // NOI18N
    consolidateLog.setToolTipText(ResourceMgr.getString("d_LblConsolidateLog")); // NOI18N
    consolidateLog.setBorder(null);
    consolidateLog.setHorizontalAlignment(SwingConstants.LEFT);
    consolidateLog.setHorizontalTextPosition(SwingConstants.RIGHT);
    consolidateLog.setIconTextGap(5);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 10, 4, 0);
    jPanel2.add(consolidateLog, gridBagConstraints);

    exitOnConnectCancel.setSelected(Settings.getInstance().getExitOnFirstConnectCancel());
    exitOnConnectCancel.setText(ResourceMgr.getString("LblExitOnConnectCancel")); // NOI18N
    exitOnConnectCancel.setToolTipText(ResourceMgr.getString("d_LblExitOnConnectCancel")); // NOI18N
    exitOnConnectCancel.setBorder(null);
    exitOnConnectCancel.setHorizontalAlignment(SwingConstants.LEFT);
    exitOnConnectCancel.setHorizontalTextPosition(SwingConstants.RIGHT);
    exitOnConnectCancel.setIconTextGap(5);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 0, 4, 0);
    jPanel2.add(exitOnConnectCancel, gridBagConstraints);

    autoConnect.setSelected(Settings.getInstance().getShowConnectDialogOnStartup());
    autoConnect.setText(ResourceMgr.getString("LblShowConnect")); // NOI18N
    autoConnect.setToolTipText(ResourceMgr.getString("d_LblShowConnect")); // NOI18N
    autoConnect.setBorder(null);
    autoConnect.setHorizontalAlignment(SwingConstants.LEFT);
    autoConnect.setHorizontalTextPosition(SwingConstants.RIGHT);
    autoConnect.setIconTextGap(5);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 0, 4, 0);
    jPanel2.add(autoConnect, gridBagConstraints);

    singlePageHelp.setText(ResourceMgr.getString("LblHelpSingle")); // NOI18N
    singlePageHelp.setToolTipText(ResourceMgr.getString("d_LblHelpSingle")); // NOI18N
    singlePageHelp.setBorder(null);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 10, 4, 0);
    jPanel2.add(singlePageHelp, gridBagConstraints);

    autoSaveProfiles.setSelected(Settings.getInstance().getConsolidateLogMsg());
    autoSaveProfiles.setText(ResourceMgr.getString("LblAutoSaveProfiles")); // NOI18N
    autoSaveProfiles.setToolTipText(ResourceMgr.getString("d_LblAutoSaveProfiles")); // NOI18N
    autoSaveProfiles.setBorder(null);
    autoSaveProfiles.setHorizontalAlignment(SwingConstants.LEFT);
    autoSaveProfiles.setHorizontalTextPosition(SwingConstants.RIGHT);
    autoSaveProfiles.setIconTextGap(5);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 0, 4, 0);
    jPanel2.add(autoSaveProfiles, gridBagConstraints);

    enableQuickFilter.setText(ResourceMgr.getString("LblProfileQuickFilter")); // NOI18N
    enableQuickFilter.setToolTipText(ResourceMgr.getString("d_LblProfileQuickFilter")); // NOI18N
    enableQuickFilter.setBorder(null);
    enableQuickFilter.setHorizontalAlignment(SwingConstants.LEFT);
    enableQuickFilter.setHorizontalTextPosition(SwingConstants.RIGHT);
    enableQuickFilter.setIconTextGap(5);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new Insets(0, 0, 4, 0);
    jPanel2.add(enableQuickFilter, gridBagConstraints);

    showMenuIcons.setText(ResourceMgr.getString("LblShowMenuIcons")); // NOI18N
    showMenuIcons.setToolTipText(ResourceMgr.getString("d_LblShowMenuIcons")); // NOI18N
    showMenuIcons.setBorder(null);
    showMenuIcons.setHorizontalAlignment(SwingConstants.LEFT);
    showMenuIcons.setHorizontalTextPosition(SwingConstants.RIGHT);
    showMenuIcons.setIconTextGap(5);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 10, 4, 0);
    jPanel2.add(showMenuIcons, gridBagConstraints);

    focusToQuickFilter.setText(ResourceMgr.getString("LblProfileQuickFilterFocus")); // NOI18N
    focusToQuickFilter.setToolTipText(ResourceMgr.getString("d_LblProfileQuickFilterFocus")); // NOI18N
    focusToQuickFilter.setBorder(null);
    focusToQuickFilter.setHorizontalAlignment(SwingConstants.LEFT);
    focusToQuickFilter.setHorizontalTextPosition(SwingConstants.RIGHT);
    focusToQuickFilter.setIconTextGap(5);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new Insets(0, 0, 4, 0);
    jPanel2.add(focusToQuickFilter, gridBagConstraints);

    masterPwdButton.setText(ResourceMgr.getString("LblMasterPwd")); // NOI18N
    masterPwdButton.setHorizontalTextPosition(SwingConstants.LEADING);
    masterPwdButton.setIconTextGap(8);
    masterPwdButton.addActionListener(this);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.gridheight = 2;
    gridBagConstraints.anchor = GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new Insets(0, 21, 0, 0);
    jPanel2.add(masterPwdButton, gridBagConstraints);

    varsPerWindow.setText(ResourceMgr.getString("LblVarsPerWindow")); // NOI18N
    varsPerWindow.setBorder(null);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new Insets(0, 10, 4, 0);
    jPanel2.add(varsPerWindow, gridBagConstraints);

    restoreExpandedGroups.setText(ResourceMgr.getString("LblRestoreExpandedGroups")); // NOI18N
    restoreExpandedGroups.setBorder(null);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new Insets(0, 0, 4, 0);
    jPanel2.add(restoreExpandedGroups, gridBagConstraints);

    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.gridwidth = 4;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.NORTH;
    gridBagConstraints.insets = new Insets(10, 0, 0, 0);
    add(jPanel2, gridBagConstraints);

    tabOptionsPanel.setLayout(new GridBagLayout());

    showTabIndex.setSelected(GuiSettings.getShowTabIndex());
    showTabIndex.setText(ResourceMgr.getString("LblShowTabIndex")); // NOI18N
    showTabIndex.setToolTipText(ResourceMgr.getString("d_LblShowTabIndex")); // NOI18N
    showTabIndex.setBorder(null);
    showTabIndex.setHorizontalAlignment(SwingConstants.LEFT);
    showTabIndex.setHorizontalTextPosition(SwingConstants.RIGHT);
    showTabIndex.setIconTextGap(5);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(4, 16, 1, 0);
    tabOptionsPanel.add(showTabIndex, gridBagConstraints);

    scrollTabs.setText(ResourceMgr.getString("LblScrolTabs")); // NOI18N
    scrollTabs.setToolTipText(ResourceMgr.getString("d_LblScrolTabs")); // NOI18N
    scrollTabs.setBorder(null);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(4, 16, 0, 0);
    tabOptionsPanel.add(scrollTabs, gridBagConstraints);

    confirmTabClose.setText(ResourceMgr.getString("LblConfirmTabClose")); // NOI18N
    confirmTabClose.setToolTipText(ResourceMgr.getString("d_LblConfirmTabClose")); // NOI18N
    confirmTabClose.setBorder(null);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new Insets(4, 0, 1, 0);
    tabOptionsPanel.add(confirmTabClose, gridBagConstraints);

    confirmMultiTabClose.setText(ResourceMgr.getString("LblConfirmMultiTabClose")); // NOI18N
    confirmMultiTabClose.setToolTipText(ResourceMgr.getString("d_LblConfirmMultiTabClose")); // NOI18N
    confirmMultiTabClose.setBorder(null);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(4, 0, 0, 0);
    tabOptionsPanel.add(confirmMultiTabClose, gridBagConstraints);

    showTabCloseButton.setText(ResourceMgr.getString("LblShowTabClose")); // NOI18N
    showTabCloseButton.setToolTipText(ResourceMgr.getString("d_LblShowTabClose")); // NOI18N
    showTabCloseButton.setBorder(null);
    showTabCloseButton.setHorizontalAlignment(SwingConstants.LEFT);
    showTabCloseButton.setHorizontalTextPosition(SwingConstants.RIGHT);
    showTabCloseButton.setIconTextGap(5);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new Insets(6, 0, 2, 0);
    tabOptionsPanel.add(showTabCloseButton, gridBagConstraints);

    showResultTabClose.setText(ResourceMgr.getString("LblShowResultClose")); // NOI18N
    showResultTabClose.setToolTipText(ResourceMgr.getString("d_LblShowResultClose")); // NOI18N
    showResultTabClose.setBorder(null);
    showResultTabClose.setHorizontalAlignment(SwingConstants.LEFT);
    showResultTabClose.setHorizontalTextPosition(SwingConstants.RIGHT);
    showResultTabClose.setIconTextGap(5);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new Insets(6, 16, 2, 0);
    tabOptionsPanel.add(showResultTabClose, gridBagConstraints);

    onlyActiveTab.setText(ResourceMgr.getString("LblCloseActive")); // NOI18N
    onlyActiveTab.setToolTipText(ResourceMgr.getString("d_LblCloseActive")); // NOI18N
    onlyActiveTab.setBorder(null);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(3, 0, 5, 0);
    tabOptionsPanel.add(onlyActiveTab, gridBagConstraints);

    closeButtonRightSide.setText(ResourceMgr.getString("LblCloseOnRight")); // NOI18N
    closeButtonRightSide.setToolTipText(ResourceMgr.getString("d_LblCloseOnRight")); // NOI18N
    closeButtonRightSide.setBorder(null);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new Insets(3, 16, 5, 0);
    tabOptionsPanel.add(closeButtonRightSide, gridBagConstraints);

    tabLRUclose.setText(ResourceMgr.getString("LblTabOrderLRU")); // NOI18N
    tabLRUclose.setToolTipText(ResourceMgr.getString("d_LblTabOrderLRU")); // NOI18N
    tabLRUclose.setBorder(null);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(4, 0, 0, 0);
    tabOptionsPanel.add(tabLRUclose, gridBagConstraints);

    imagePanel.setLayout(new GridBagLayout());

    iconCombobox.setModel(IconListCombobox.getBusyIcons());
    iconCombobox.setToolTipText(ResourceMgr.getString("d_LblBusyIcon")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(0, 4, 0, 0);
    imagePanel.add(iconCombobox, gridBagConstraints);

    busyIconLabel.setLabelFor(iconCombobox);
    busyIconLabel.setText(ResourceMgr.getString("LblBusyIcon")); // NOI18N
    busyIconLabel.setToolTipText(ResourceMgr.getString("d_LblBusyIcon")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    imagePanel.add(busyIconLabel, gridBagConstraints);

    cancelIconCombo.setModel(IconListCombobox.getCancelIcons());
    cancelIconCombo.setToolTipText(ResourceMgr.getString("d_LblBusyIcon")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(0, 4, 0, 0);
    imagePanel.add(cancelIconCombo, gridBagConstraints);

    cancelIconLabel.setLabelFor(cancelIconCombo);
    cancelIconLabel.setText(ResourceMgr.getString("LblCancelIcon")); // NOI18N
    cancelIconLabel.setToolTipText(ResourceMgr.getString("d_LblCancelIcon")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    imagePanel.add(cancelIconLabel, gridBagConstraints);

    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.gridheight = 2;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(0, 12, 1, 0);
    tabOptionsPanel.add(imagePanel, gridBagConstraints);

    jPanel1.setLayout(new GridBagLayout());

    jLabel1.setText(ResourceMgr.getString("LblEditorTabPos")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    jPanel1.add(jLabel1, gridBagConstraints);

    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new Insets(0, 9, 0, 0);
    jPanel1.add(editorTabPlacement, gridBagConstraints);

    jLabel2.setText(ResourceMgr.getString("LblResultTabPos")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new Insets(7, 0, 0, 0);
    jPanel1.add(jLabel2, gridBagConstraints);

    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new Insets(7, 9, 0, 0);
    jPanel1.add(resultTabPlacement, gridBagConstraints);

    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.gridwidth = 3;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new Insets(7, 0, 0, 0);
    tabOptionsPanel.add(jPanel1, gridBagConstraints);

    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.gridwidth = 4;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new Insets(4, 0, 0, 0);
    add(tabOptionsPanel, gridBagConstraints);

    jPanel5.setLayout(new GridBagLayout());

    langLabel.setText(ResourceMgr.getString("LblLanguage")); // NOI18N
    langLabel.setToolTipText(ResourceMgr.getString("d_LblLanguage")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(1, 0, 0, 0);
    jPanel5.add(langLabel, gridBagConstraints);

    languageDropDown.setModel(new DefaultComboBoxModel(new String[] { "English", "German" }));
    languageDropDown.setToolTipText(ResourceMgr.getDescription("LblLanguage"));
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(1, 10, 0, 8);
    jPanel5.add(languageDropDown, gridBagConstraints);

    checkUpdatesLabel.setText(ResourceMgr.getString("LblCheckForUpdate")); // NOI18N
    checkUpdatesLabel.setToolTipText(ResourceMgr.getString("d_LblCheckForUpdate")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(0, 7, 0, 1);
    jPanel5.add(checkUpdatesLabel, gridBagConstraints);

    updateInterval.setColumns(6);
    updateInterval.setToolTipText(ResourceMgr.getString("d_LblCheckForUpdate")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(0, 6, 0, 5);
    jPanel5.add(updateInterval, gridBagConstraints);

    daysLabel.setText(ResourceMgr.getString("LblUpdDays")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.weightx = 1.0;
    jPanel5.add(daysLabel, gridBagConstraints);

    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridwidth = 4;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.weightx = 1.0;
    add(jPanel5, gridBagConstraints);

    settingsfilename.setText("Settings");
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.gridwidth = 4;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.SOUTHWEST;
    gridBagConstraints.insets = new Insets(10, 0, 0, 0);
    add(settingsfilename, gridBagConstraints);

    logfileLabel.setText("Logfile");
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.gridwidth = 4;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.SOUTHWEST;
    gridBagConstraints.insets = new Insets(2, 0, 0, 0);
    add(logfileLabel, gridBagConstraints);
  }

  // Code for dispatching events from components to event handlers.

  public void actionPerformed(ActionEvent evt)
  {
    if (evt.getSource() == masterPwdButton)
    {
      GeneralOptionsPanel.this.masterPwdButtonActionPerformed(evt);
    }
  }// </editor-fold>//GEN-END:initComponents

  private void masterPwdButtonActionPerformed(ActionEvent evt)//GEN-FIRST:event_masterPwdButtonActionPerformed
  {//GEN-HEADEREND:event_masterPwdButtonActionPerformed
    // If a master password is defined, don't allow changing it without entering the current one again
    if (!GlobalPasswordManager.getInstance().showPasswordPrompt(true))
    {
      return;
    }
    Dialog parent = (Dialog)WbSwingUtilities.getWindowAncestor(this);
    MasterPasswordDialog pwdDialog = new MasterPasswordDialog(parent);
    Settings.getInstance().restoreWindowSize(pwdDialog);
    WbSwingUtilities.center(pwdDialog, parent);
    pwdDialog.setVisible(true);

    if (pwdDialog.wasCancelled()) return;

    if (pwdDialog.doRemoveMasterPassword())
    {
      removeMasterPassword = true;
      newMasterPassword = null;
      masterPwdButton.setIcon(null);
      return;
    }

    masterPwdButton.setIcon(IconMgr.getInstance().getLabelIcon("tick"));
    newMasterPassword = pwdDialog.getMasterPassword();
    removeMasterPassword = false;
  }//GEN-LAST:event_masterPwdButtonActionPerformed

  // Variables declaration - do not modify//GEN-BEGIN:variables
  private JCheckBox autoConnect;
  private JCheckBox autoSaveProfiles;
  private JLabel busyIconLabel;
  private JComboBox cancelIconCombo;
  private JLabel cancelIconLabel;
  private JLabel checkUpdatesLabel;
  private JCheckBox closeButtonRightSide;
  private JCheckBox confirmMultiTabClose;
  private JCheckBox confirmTabClose;
  private JCheckBox consolidateLog;
  private JLabel daysLabel;
  private JComboBox<String> editorTabPlacement;
  private JCheckBox enableQuickFilter;
  private JCheckBox exitOnConnectCancel;
  private JCheckBox focusToQuickFilter;
  private JComboBox iconCombobox;
  private JPanel imagePanel;
  private JLabel jLabel1;
  private JLabel jLabel2;
  private JPanel jPanel1;
  private JPanel jPanel2;
  private JPanel jPanel5;
  private JLabel langLabel;
  private JComboBox languageDropDown;
  private JTextField logfileLabel;
  private JButton masterPwdButton;
  private JCheckBox onlyActiveTab;
  private JCheckBox restoreExpandedGroups;
  private JComboBox<String> resultTabPlacement;
  private JCheckBox scrollTabs;
  private JTextField settingsfilename;
  private JCheckBox showMenuIcons;
  private JCheckBox showResultTabClose;
  private JCheckBox showTabCloseButton;
  private JCheckBox showTabIndex;
  private JCheckBox singlePageHelp;
  private JCheckBox tabLRUclose;
  private JPanel tabOptionsPanel;
  private JTextField updateInterval;
  private JCheckBox varsPerWindow;
  // End of variables declaration//GEN-END:variables

}
