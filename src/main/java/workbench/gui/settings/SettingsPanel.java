/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2024 Thomas Kellerer
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

import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import workbench.interfaces.QuickFilter;
import workbench.interfaces.ValidatingComponent;
import workbench.resource.GuiSettings;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.gui.WbSwingUtilities;
import workbench.gui.actions.EscAction;
import workbench.gui.actions.QuickFilterAction;
import workbench.gui.actions.WbAction;
import workbench.gui.components.DividerBorder;
import workbench.gui.components.WbButton;
import workbench.gui.components.WbScrollPane;
import workbench.gui.components.WbSplitPane;
import workbench.gui.components.WbToolbar;
import workbench.gui.help.HelpManager;

import workbench.util.StringUtil;

/**
 * @author Thomas Kellerer
 */
public class SettingsPanel
  extends JPanel
  implements ActionListener, ListSelectionListener, WindowListener, ValidatingComponent, QuickFilter, KeyListener
{
  private JButton cancelButton;
  private JButton helpButton;
  private WbSplitPane content;
  private JList pageList;
  private JButton okButton;
  private JDialog dialog;
  private EscAction escAction;
  private final PageListModel listModel;
  private JTextField filterValue;
  private WbAction resetFilter;
  private QuickFilterAction applyFilter;

  public SettingsPanel()
  {
    super(new BorderLayout());
    List<OptionPanelPage> pages = new ArrayList<>(30);
    // Remember to adjust the calculation of the width and height in showSettingsDialog()
    // when changing the order of pages, or adding new pages
    pages.add(new OptionPanelPage("GeneralOptionsPanel", "LblSettingsGeneral"));
    pages.add(new OptionPanelPage("EditorOptionsPanel", "LblSettingsEditor"));
    pages.add(new OptionPanelPage("EditorColorsPanel", "LblEditorColors"));
    pages.add(new OptionPanelPage("HighlightSettingsPanel", "LblHighlightOptions"));
    pages.add(new OptionPanelPage("CompletionOptionsPanel", "LblCompletionOptions"));
    pages.add(new OptionPanelPage("FontOptionsPanel", "LblSettingsFonts"));
    pages.add(new OptionPanelPage("SqlExecOptionsPanel", "LblSqlExecOptions"));
    pages.add(new OptionPanelPage("MacroOptionsPanel", "LblMacros"));
    pages.add(new OptionPanelPage("BookmarkOptionsPanel", "LblBookmarkOptions"));
    pages.add(new OptionPanelPage("WorkspaceOptions", "LblSettingsWorkspace"));
    pages.add(new OptionPanelPage("BackupOptions", "LblBackups"));
    pages.add(new OptionPanelPage("DataDisplayOptions", "LblSettingsDataDisplay"));
    pages.add(new OptionPanelPage("DataFormattingOptionsPanel", "LblSettingsDataFormat"));
    pages.add(new OptionPanelPage("DataColorOptions", "LblDataColors"));
    pages.add(new OptionPanelPage("DataEditOptionsPanel", "LblDataEdit"));
    pages.add(new OptionPanelPage("DbExplorerOptionsPanel", "LblSettingsDbExplorer"));
    pages.add(new OptionPanelPage("DbTreeOptionsPanel", "LblTreeOptions"));
    pages.add(new OptionPanelPage("FileTreeOptionsPanel", "LblFileTreeOptions"));
    pages.add(new OptionPanelPage("FormatterOptionsPanel", "LblSqlFormat"));
    pages.add(new OptionPanelPage("SqlGenerationOptionsPanel", "LblSqlGeneration"));
    pages.add(new OptionPanelPage("WindowTitleOptionsPanel", "LblSettingsWinTitle"));
    pages.add(new OptionPanelPage("GlobalSshHostsPanel", "LblSshGlobalCfg"));
    pages.add(new OptionPanelPage("ExternalToolsPanel", "LblExternalTools"));
    pages.add(new OptionPanelPage("LnFOptionsPanel", "LblLnFOptions"));
    pages.add(new OptionPanelPage("LoggingOptionsPanel", "LblLoggingOptions"));
    for (int i=0; i < pages.size(); i++)
    {
      pages.get(i).setDisplayIndex(i);
    }
    listModel = new PageListModel(pages);
    initComponents();
  }

  @Override
  public void valueChanged(ListSelectionEvent e)
  {
    if (e.getValueIsAdjusting()) return;

    int index = this.pageList.getSelectedIndex();
    if (index < 0) return;

    try
    {
      WbSwingUtilities.showWaitCursor(this);
      OptionPanelPage option = listModel.getElementAt(index);
      JPanel panel = option.getPanel();
      int divider = content.getDividerLocation();
      content.setRightComponent(panel);
      content.setDividerLocation(divider);
    }
    finally
    {
      WbSwingUtilities.showDefaultCursor(this);
    }
  }

  private void initComponents()
  {
    content = new WbSplitPane();
    content.setBorder(new EmptyBorder(2,2,2,2));
    content.setDividerBorder(new DividerBorder(DividerBorder.LEFT_RIGHT));
    content.setDividerSize(GuiSettings.getSplitPaneDividerWidth());

    pageList = new JList(listModel);
    pageList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    pageList.addListSelectionListener(this);

    JPanel optionList = new JPanel(new BorderLayout(0,2));

    JScrollPane scroll = new WbScrollPane(pageList);
    scroll.setBorder(new EmptyBorder(0,0,0,0));

    JPanel filterPanel = buildQuickFilterPanel();
    optionList.add(filterPanel, BorderLayout.NORTH);
    optionList.add(scroll, BorderLayout.CENTER);
    content.setLeftComponent(optionList);

    WbSwingUtilities.adjustSplitPane(pageList, content);

    okButton = new WbButton(ResourceMgr.getString("LblOK"));
    cancelButton = new WbButton(ResourceMgr.getString("LblCancel"));
    helpButton = new JButton(ResourceMgr.getString("LblHelp"));

    WbSwingUtilities.makeEqualWidth(okButton, cancelButton, helpButton);

    okButton.addActionListener(this);
    cancelButton.addActionListener(this);
    helpButton.addActionListener(this);

    JPanel buttonPanel = new JPanel(new GridBagLayout());

    GridBagConstraints gc = new GridBagConstraints();
    gc.gridx = 0;
    gc.gridy = 0;
    gc.anchor = GridBagConstraints.WEST;
    gc.insets = new Insets(0, 5, 0, 0);
    buttonPanel.add(helpButton, gc);

    gc.gridx++;
    gc.anchor = GridBagConstraints.EAST;
    gc.insets = new Insets(7, 0, 7, 10);
    gc.weightx = 1.0;
    buttonPanel.add(okButton, gc);

    gc.gridx++;
    gc.anchor = GridBagConstraints.EAST;
    gc.weightx = 0.0;
    gc.insets = new Insets(7, 0, 7, 4);
    buttonPanel.add(cancelButton, gc);
    buttonPanel.setBorder(DividerBorder.TOP_DIVIDER);

    add(content, BorderLayout.CENTER);
    add(buttonPanel, BorderLayout.PAGE_END);
  }

  private JPanel buildQuickFilterPanel()
  {
    applyFilter = new QuickFilterAction(this);
    resetFilter = new WbAction()
    {
      @Override
      public void executeAction(ActionEvent e)
      {
        resetFilter();
      }
    };
    resetFilter.setIcon("resetfilter");
    resetFilter.setEnabled(true);

    JPanel filterPanel = new JPanel(new GridBagLayout());
    filterPanel.setBorder(new DividerBorder(DividerBorder.TOP));
    filterValue = new JTextField();
    filterValue.addKeyListener(this);

    GridBagConstraints gc = new GridBagConstraints();
    gc.anchor = GridBagConstraints.LINE_START;
    gc.gridx = 0;
    gc.gridy = 0;
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 1.0;
    gc.weighty = 0.0;
    gc.insets = new Insets(0,0,0,5);
    filterPanel.add(filterValue, gc);

    WbToolbar filterBar = new WbToolbar();
    filterBar.add(applyFilter);
    filterBar.add(resetFilter);
    filterBar.setMargin(WbSwingUtilities.getEmptyInsets());
    filterBar.setBorder(new EmptyBorder(0,0,0,0));
    filterBar.setBorderPainted(true);

    gc.gridx ++;
    gc.weightx = 0.0;
    gc.fill = GridBagConstraints.NONE;
    filterPanel.add(filterBar, gc);

    return filterPanel;
  }

  private void saveSettings()
  {
    for (OptionPanelPage page : listModel.getAllPages())
    {
      page.saveSettings();
    }
  }

  /**
   * Display the Options dialog.
   *
   * This method has to be called on the EDT!
   *
   */
  public void showSettingsDialog(JFrame aReference)
  {
    dialog = new JDialog(aReference, true);
    dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    dialog.setTitle(ResourceMgr.getString("TxtSettingsDialogTitle"));
    dialog.getContentPane().add(this);
    dialog.addWindowListener(this);
    int width = Settings.getInstance().getWindowWidth(this.getGraphicsConfiguration(), this.getClass().getName());
    int height = Settings.getInstance().getWindowHeight(this.getGraphicsConfiguration(), this.getClass().getName());

    if (width > 0 && height > 0)
    {
      this.dialog.setSize(width, height);
    }
    else
    {
      // the "Data Display" page is the highest page
      pageList.setSelectedIndex(11);
      dialog.pack();
      int h = dialog.getSize().height;

      // the editor colors page is the widest page
      pageList.setSelectedIndex(2);
      dialog.pack();
      int w = dialog.getSize().width;

      dialog.setSize((int)(w * 1.03),(int)(h * 1.03));
    }

    dialog.getRootPane().setDefaultButton(this.okButton);

    escAction = new EscAction(dialog, this);

    WbSwingUtilities.center(this.dialog, aReference);

    pageList.setSelectedIndex(0);
    WbSwingUtilities.requestFocus(pageList);
    dialog.setVisible(true);
  }

  private void closeWindow()
  {
    Settings.getInstance().setWindowSize(this.getGraphicsConfiguration(), this.getClass().getName(), this.dialog.getWidth(),this.dialog.getHeight());
    for (OptionPanelPage page : listModel.getAllPages())
    {
      page.dispose();
    }
    this.dialog.setVisible(false);
    this.dialog.dispose();
    this.dialog = null;
  }

  @Override
  public void actionPerformed(java.awt.event.ActionEvent e)
  {
    if (e.getSource() == escAction || e.getSource() == cancelButton)
    {
      this.closeWindow();
    }
    else if (e.getSource() == okButton)
    {
      if (validateInput())
      {
        this.saveSettings();
        this.closeWindow();
      }
    }
    else if (e.getSource() == helpButton)
    {
      HelpManager.showOptionsHelp();
    }
  }

   @Override
   public void windowActivated(WindowEvent e)
   {
   }

   @Override
   public void windowClosed(WindowEvent e)
   {
   }

   @Override
   public void windowClosing(WindowEvent e)
   {
      closeWindow();
   }

   @Override
   public void windowDeactivated(WindowEvent e)
   {
   }

   @Override
   public void windowDeiconified(WindowEvent e)
   {
   }

   @Override
   public void windowIconified(WindowEvent e)
   {
   }

   @Override
   public void windowOpened(WindowEvent e)
   {
   }

  @Override
  public boolean validateInput()
  {
    resetFilter();
    List<OptionPanelPage> pages = listModel.getAllPages();
    for (int i = 0; i < pages.size(); i++)
    {
      OptionPanelPage page = pages.get(i);
      final int index = i;
      if (!page.validateInput())
      {
        SwingUtilities.invokeLater(() ->
        {
          pageList.setSelectedIndex(index);
        });
        return false;
      }
    }
    return true;
  }

  @Override
  public void applyQuickFilter()
  {
    String value = StringUtil.trimToNull(this.filterValue.getText());
    if (value == null)
    {
      resetFilter();
      return;
    }

    String selected = getSelectedPageTitle();
    int location = content.getDividerLocation();
    try
    {
      WbSwingUtilities.showWaitCursor(this);
      content.setRightComponent(new JPanel());
      pageList.clearSelection();
      listModel.applyFilter(value);
      selectPageByTitle(selected);
      content.setDividerLocation(location);
    }
    finally
    {
      WbSwingUtilities.showDefaultCursor(this);
    }
  }

  private String getSelectedPageTitle()
  {
    int index = pageList.getSelectedIndex();
    if (index < 0) return null;
    return listModel.getElementAt(index).getLabel();
  }

  @Override
  public void resetFilter()
  {
    String selected = getSelectedPageTitle();
    filterValue.setText("");
    pageList.clearSelection();
    listModel.resetFilter();
    selectPageByTitle(selected);
  }

  private void selectPageByTitle(String title)
  {
    if (listModel.getSize() == 0) return;
    int oldIndex = listModel.getIndexOf(title);
    if (oldIndex < 0)
    {
      pageList.setSelectedIndex(0);
    }
    else
    {
      pageList.setSelectedIndex(oldIndex);
    }

  }

  @Override
  public void componentWillBeClosed()
  {
  }

  @Override
  public void componentDisplayed()
  {
  }

  @Override
  public void keyTyped(KeyEvent e)
  {
  }

  @Override
  public void keyPressed(KeyEvent e)
  {
    if (e.getModifiersEx() != 0) return;
    switch (e.getKeyCode())
    {
      case KeyEvent.VK_ESCAPE:
        e.consume();
        resetFilter();
        break;
      case KeyEvent.VK_ENTER:
      e.consume();
      applyQuickFilter();
    }
  }

  @Override
  public void keyReleased(KeyEvent e)
  {
  }

}
