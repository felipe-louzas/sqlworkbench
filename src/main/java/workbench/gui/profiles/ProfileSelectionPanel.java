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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTextField;
import javax.swing.JTree;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import workbench.interfaces.FileActions;
import workbench.interfaces.QuickFilter;
import workbench.interfaces.ValidatingComponent;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.GuiSettings;
import workbench.resource.IconMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.ConnectionMgr;
import workbench.db.ConnectionProfile;
import workbench.db.DbDriver;

import workbench.gui.WbSwingUtilities;
import workbench.gui.actions.CollapseTreeAction;
import workbench.gui.actions.CopyProfileAction;
import workbench.gui.actions.DeleteListEntryAction;
import workbench.gui.actions.ExpandTreeAction;
import workbench.gui.actions.NewListEntryAction;
import workbench.gui.actions.QuickFilterAction;
import workbench.gui.actions.SaveListFileAction;
import workbench.gui.actions.WbAction;
import workbench.gui.components.DividerBorder;
import workbench.gui.components.WbLabel;
import workbench.gui.components.WbSplitPane;
import workbench.gui.components.WbToolbar;
import workbench.gui.components.WbTraversalPolicy;

import workbench.util.CollectionUtil;
import workbench.util.StringUtil;
import workbench.util.WbFile;

/**
 *
 * @author  Thomas Kellerer
 */
public class ProfileSelectionPanel
  extends JPanel
  implements FileActions, ValidatingComponent, PropertyChangeListener, KeyListener, QuickFilter
{
  private ProfileListModel model;
  private final WbToolbar toolbar;
  protected ConnectionEditorPanel connectionEditor;
  private Consumer selectionListener;
  private final NewListEntryAction newItem;
  private final CopyProfileAction copyItem;
  private JTextField filterValue;
  protected boolean dummyAdded;
  private List<String> orgExpandedGroups;
  private ConnectionProfile initialProfile;
  private WbAction resetFilter;
  private QuickFilterAction applyFilter;

  public ProfileSelectionPanel(String lastProfileKey)
  {
    super();
    initComponents(); // will initialize the model!

    WbSplitPane split = (WbSplitPane)jSplitPane;
    split.setDividerBorder(new DividerBorder(DividerBorder.LEFT_RIGHT));
    split.setDividerSize(GuiSettings.getSplitPaneDividerWidth());
    this.setBorder(DividerBorder.BOTTOM_DIVIDER);
    this.connectionEditor = new ConnectionEditorPanel();
    JPanel dummy = new JPanel(new BorderLayout());
    dummy.add(connectionEditor, BorderLayout.CENTER);
    this.jSplitPane.setRightComponent(dummy);
    this.fillDrivers();

    JPanel p = new JPanel(new BorderLayout());
    this.toolbar = new WbToolbar();
    newItem = new NewListEntryAction(this, "LblNewProfile");
    newItem.setIcon("new-profile");
    this.toolbar.add(newItem);

    copyItem = new CopyProfileAction(this);
    copyItem.setEnabled(false);
    toolbar.add(copyItem);
    ProfileTree tree = (ProfileTree)profileTree;
    treeScroll.setBorder(WbSwingUtilities.EMPTY_BORDER);

    this.toolbar.add(new NewGroupAction(tree, "LblNewProfileGroup"));

    this.toolbar.addSeparator();
    this.toolbar.add(getDeleteAction());

    this.toolbar.addSeparator();
    SaveListFileAction saveAction = new SaveListFileAction(this);
    String tip = saveAction.getToolTipText();
    List<WbFile> files = ConnectionMgr.getInstance().getProfileSources();
    String names = files.stream().map(f -> f.getFullPath()).collect(Collectors.joining(",\n"));
    tip = tip + "\n" + names;
    saveAction.setTooltip(tip);
    this.toolbar.add(saveAction);

    this.toolbar.addSeparator();
    this.toolbar.add(new ExpandTreeAction(tree));
    this.toolbar.add(new CollapseTreeAction(tree));

    p.add(toolbar, BorderLayout.PAGE_START);

    if (GuiSettings.enableProfileQuickFilter())
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
      WbLabel lbl = new WbLabel();
      lbl.setTextByKey("LblConnFilter");
      lbl.setLabelFor(filterValue);
      //lbl.setBorder(new EmptyBorder(0, 5, 0, 5));

      GridBagConstraints gc = new GridBagConstraints();
      gc.anchor = GridBagConstraints.LINE_START;
      gc.gridx = 0;
      gc.gridy = 0;
      gc.fill = GridBagConstraints.NONE;
      gc.weightx = 0.0;
      gc.weighty = 0.0;
      gc.insets = new Insets(0,0,0,5);
      filterPanel.add(lbl, gc);

      gc.gridx ++;
      gc.fill = GridBagConstraints.HORIZONTAL;
      gc.weightx = 1.0;
      filterPanel.add(filterValue, gc);

      WbToolbar filterBar = new WbToolbar();
      filterBar.add(applyFilter);
      filterBar.add(resetFilter);
      filterBar.setMargin(WbSwingUtilities.getEmptyInsets());
      filterBar.setBorder(new EmptyBorder(0,0,0,0));
      filterBar.setBorderPainted(true);

      gc.gridx ++;
      gc.fill = GridBagConstraints.NONE;
      gc.weightx = 0.0;
      filterPanel.add(filterBar, gc);

      p.add(filterPanel, BorderLayout.PAGE_END);
      filterValue.setToolTipText(ResourceMgr.getDescription("LblConnTagFilter", true));
    }

    this.listPanel.add(p, BorderLayout.NORTH);

    WbTraversalPolicy policy = new WbTraversalPolicy();
    this.setFocusCycleRoot(false);
    policy.addComponent(this.profileTree);
    policy.addComponent(this.connectionEditor);
    policy.setDefaultComponent(this.profileTree);
    this.setFocusTraversalPolicy(policy);

    buildTree();
    restoreSettings();

    final ProfileKey last = Settings.getInstance().getLastConnection(lastProfileKey);
    ((ProfileTree)profileTree).selectProfile(last);
    ConnectionMgr.getInstance().addDriverChangeListener(this);

    if (filterValue != null)
    {
      filterValue.addKeyListener(this);
    }
  }

  private DeleteListEntryAction getDeleteAction()
  {
    return ((ProfileTree)profileTree).getDeleteAction();
  }

  private void showTagPopup()
  {
    TagSearchPopup search = new TagSearchPopup(filterValue, model.getAllTags(), this);
    search.showPopup();
  }

  @Override
  public void keyTyped(final KeyEvent e)
  {
    EventQueue.invokeLater(this::applyQuickFilter);
  }

  @Override
  public void keyPressed(KeyEvent e)
  {
    if (e.getKeyCode() == KeyEvent.VK_SPACE && WbAction.isCtrlPressed(e))
    {
      e.consume();
      EventQueue.invokeLater(this::showTagPopup);
      return;
    }

    if (e.getModifiersEx() != 0) return;

    switch (e.getKeyCode())
    {
      // vertical cursor movement can always be forwared to the tree
      case KeyEvent.VK_UP:
      case KeyEvent.VK_DOWN:
      case KeyEvent.VK_PAGE_DOWN:
      case KeyEvent.VK_PAGE_UP:
        profileTree.dispatchEvent(e);
        break;

      // horizontal cursor movement should only be forwarded to the tree
      // if no filter value has been entered by the user
      // otherwise it's not possible to move the cursor in the filterValue field in order to change the text
      case KeyEvent.VK_HOME:
      case KeyEvent.VK_END:
      case KeyEvent.VK_LEFT:
      case KeyEvent.VK_RIGHT:
        if (StringUtil.isBlank(filterValue.getText()))
        {
          profileTree.dispatchEvent(e);
        }
        break;

      case KeyEvent.VK_ESCAPE:
        if (StringUtil.isNotBlank(filterValue.getText()))
        {
          e.consume();
          resetFilter();
        }
    }
  }

  public void initDivider()
  {
    this.jSplitPane.setDividerLocation(0.3);
  }

  @Override
  public void keyReleased(KeyEvent e)
  {
  }

  @Override
  public void resetFilter()
  {
    ConnectionProfile selectedProfile = null;
    if (GuiSettings.restoreProfileSelectionBeforeFilter())
    {
      selectedProfile = initialProfile;
    }
    else
    {
      selectedProfile = getSelectedProfile();
    }

    filterValue.setText("");

    model.resetFilter();

    ProfileTree tree = (ProfileTree)profileTree;

    if (orgExpandedGroups != null)
    {
      tree.expandGroups(orgExpandedGroups);
    }

    if (selectedProfile != null)
    {
      tree.selectProfile(selectedProfile.getKey());
    }
    else
    {
      tree.selectFirstProfile();
    }
  }

  @Override
  public void applyQuickFilter()
  {
    if (orgExpandedGroups == null)
    {
      // save the groups that were expanded the first time the filter is applied
      // in order to be able to restore this once the filter is reset
      // or the settings are stored.
      orgExpandedGroups = ((ProfileTree)profileTree).getExpandedGroupNames();
    }
    ProfileTree tree = (ProfileTree) profileTree;
    ConnectionProfile profile = tree.getSelectedProfile();
    if (initialProfile == null)
    {
      initialProfile = profile;
    }

    boolean isNameFilter = true;
    String text = filterValue.getText().trim();

    if (text.isEmpty())
    {
      resetFilter();
    }
    else
    {
      // first try to filter on tags
      if (text.length() >= GuiSettings.getMinTagLength())
      {
        Set<String> tags = CollectionUtil.caseInsensitiveSet();
        tags.addAll(StringUtil.stringToList(text, ",", true, true, false, false));
        Set<String> allTags = model.getAllTags();
        if (CollectionUtil.containsAny(allTags, tags))
        {
          model.applyTagFilter(tags);
          isNameFilter = false;
        }
      }

      // apparently not a tag --> filter on the name
      if (isNameFilter)
      {
        model.applyNameFilter(text);
      }

      if (model.isFiltered())
      {
        tree.expandAll();
        if (profile != null)
        {
          tree.selectProfile(profile.getKey());
        }
        else
        {
          tree.selectFirstProfile();
        }
      }
    }
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt)
  {
    if (evt.getPropertyName().equals("drivers"))
    {
      EventQueue.invokeLater(this::fillDrivers);
    }
  }

  public void done()
  {
    ConnectionMgr.getInstance().removeDriverChangeListener(this);
  }

  public JComponent getInitialFocusComponent()
  {
    if (dummyAdded)
    {
      return connectionEditor.getInitialFocusComponent();
    }
    else if (filterValue != null && GuiSettings.focusToProfileQuickFilter())
    {
      return filterValue;
    }
    else
    {
      return profileTree;
    }
  }

  public void addSelectionListener(TreeSelectionListener listener)
  {
    this.profileTree.addTreeSelectionListener(listener);
  }

  public DbDriver getCurrentDriver()
  {
    return this.connectionEditor.getCurrentDriver();
  }

  public final void fillDrivers()
  {
    List<DbDriver> drivers = ConnectionMgr.getInstance().getDrivers();
    this.connectionEditor.setDrivers(drivers);
  }

  public final void restoreSettings()
  {
    int pos = GuiSettings.getProfileDividerLocation();
    if (pos < 200)
    {
      pos = 200; // make sure the whole toolbar for the tree is visible!
    }
    this.jSplitPane.setDividerLocation(pos);

    if (GuiSettings.getRestoreExpandedProfileGroups())
    {
      String groups = Settings.getInstance().getProperty("workbench.profiles.expandedgroups", null);
      List<String> l = StringUtil.stringToList(groups, ",", true, true);
      ((ProfileTree)profileTree).expandGroups(l);
    }
  }

  public boolean profilesChanged()
  {
    ProfileListModel list = (ProfileListModel)profileTree.getModel();
    return list.profilesAreModified();
  }

  public boolean groupsChanged()
  {
    ProfileListModel list = (ProfileListModel)profileTree.getModel();
    return list.groupsChanged();
  }

  /**
   * Hands the complete ProfileList over to the ConnectionMgr.
   */
  public void applyProfiles()
  {
    this.connectionEditor.updateProfile();
    ProfileListModel list = (ProfileListModel)profileTree.getModel();
    list.applyProfiles();
  }

  public void saveSettings()
  {
    GuiSettings.setProfileDividerLocation(this.jSplitPane.getDividerLocation());
    List<String> expandedGroups = orgExpandedGroups;
    if (expandedGroups == null)
    {
      expandedGroups = ((ProfileTree)profileTree).getExpandedGroupNames();
    }
    Settings.getInstance().setProperty("workbench.profiles.expandedgroups", StringUtil.listToString(expandedGroups, ',', true));
  }

  private void buildTree()
  {
    this.dummyAdded = false;
    this.model = new ProfileListModel(ConnectionMgr.getInstance().getProfiles());
    if (model.getSize() < 1)
    {
      this.model.addEmptyProfile();
      this.dummyAdded = true;
    }
    this.profileTree.setModel(this.model);
    this.connectionEditor.addProfileChangeListener(this.model);
  }

  public void addProfileChangelistener(ProfileChangeListener listener)
  {
    this.connectionEditor.addProfileChangeListener(listener);
  }

  public ConnectionProfile getSelectedProfile()
  {
    return ((ProfileTree)profileTree).getSelectedProfile();
  }

  public void addProfileSelectionListener(Consumer<MouseEvent> aListener)
  {
    this.selectionListener = aListener;
  }

  /**
   *  Remove an item from the listmodel.
   *  This will also remove the profile from the ConnectionMgr's
   *  profile list.
   */
  @Override
  public void deleteItem()
    throws Exception
  {
    ((ProfileTree)profileTree).deleteSelectedItem();
  }

  private boolean shouldPromptForStorage()
  {
    return ConnectionMgr.getInstance().getProfileSources().size() > 1;
  }

  private WbFile promptForStorage()
  {
    List<WbFile> sources = ConnectionMgr.getInstance().getProfileSources();
    JPanel p = new JPanel(new GridBagLayout());


    JComboBox<WbFile> files = new JComboBox<>(sources.toArray(WbFile[]::new));

    GridBagConstraints gc = new GridBagConstraints();
    gc.fill = GridBagConstraints.NONE;
    gc.gridx = 0;
    gc.gridy = 0;
    gc.anchor = GridBagConstraints.FIRST_LINE_START;
    gc.weightx = 0.0;
    gc.weighty = 0.0;
    p.add(new JLabel(ResourceMgr.getString("LblProfileFiles1")), gc);

    int h = IconMgr.getInstance().getSizeForLabel()/ 4;
    gc.insets = new Insets(h, 0, h, 0);
    gc.gridy ++;
    p.add(new JLabel(ResourceMgr.getString("LblProfileFiles2")), gc);

    gc.gridy ++;
    gc.fill = GridBagConstraints.HORIZONTAL;
    gc.weightx = 1.0;
    gc.weighty = 1.0;
    p.add(files, gc);
    p.setBorder(new EmptyBorder(0, 0, IconMgr.getInstance().getToolbarIconSize(), 0));
    boolean ok = WbSwingUtilities.getOKCancel(ResourceMgr.getString("TxtSelectProfileStorage"), this, p);
    if (ok)
    {
      int index = files.getSelectedIndex();
      return sources.get(index);
    }
    return null;
  }

  /**
   *  Create a new profile. This will be added to the ListModel and the
   *  ConnectionMgr's profile list.
   */
  @Override
  public void newItem(boolean createCopy)
  {
    ConnectionProfile cp = null;

    WbFile source = null;
    if (shouldPromptForStorage())
    {
      source = promptForStorage();
      if (source == null)
      {
        return;
      }
    }

    if (createCopy)
    {
      ConnectionProfile current = getSelectedProfile();
      if (current != null)
      {
        cp = current.createCopy();
        cp.setName(ResourceMgr.getString("TxtCopyOf") + " " + current.getName());
      }
    }

    if (cp == null)
    {
      cp = ConnectionProfile.createEmptyProfile();
      cp.setGroups(getCurrentGroupPath());
      if (Settings.getInstance().getDefaultAutoCommit())
      {
        cp.setAutocommit(true);
      }
    }
    cp.setNew();

    TreePath newPath = this.model.addProfile(cp);
    if (source != null)
    {
      ConnectionMgr.getInstance().setProfileSource(cp, source);
    }
    ((ProfileTree)profileTree).selectPath(newPath);
  }

  private List<String> getCurrentGroupPath()
  {
    TreePath path = profileTree.getSelectionPath();
    if (path == null)
    {
      return null;
    }

    DefaultMutableTreeNode node = (DefaultMutableTreeNode)path.getLastPathComponent();
    GroupNode currentGroup = null;
    if (node instanceof GroupNode)
    {
      currentGroup = (GroupNode)node;
    }
    else if (node instanceof ProfileNode)
    {
      currentGroup = (GroupNode)node.getParent();
    }
    if (currentGroup != null)
    {
      return currentGroup.getGroupPath();
    }
    return null;
  }

  @Override
  public void saveItem()
    throws Exception
  {
    // Synchronize the current profile with the list
    this.connectionEditor.updateProfile();
    ProfileListModel list = (ProfileListModel)profileTree.getModel();
    list.saveProfiles();
  }

  public int getProfileCount()
  {
    return this.profileTree.getRowCount();
  }

  @Override
  public boolean validateInput()
  {
    return this.connectionEditor.validateInput();
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

  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents()
  {

    jSplitPane = new WbSplitPane();

    listPanel = new JPanel();
    treeScroll = new JScrollPane();
    profileTree = new ProfileTree();

    FormListener formListener = new FormListener();

    setLayout(new BorderLayout());

    jSplitPane.setBorder(null);
    jSplitPane.setDividerLocation(110);
    jSplitPane.setMinimumSize(new Dimension(400, 400));

    listPanel.setLayout(new BorderLayout());

    treeScroll.setPreferredSize(null);

    DefaultMutableTreeNode treeNode1 = new DefaultMutableTreeNode("Profiles");
    DefaultMutableTreeNode treeNode2 = new DefaultMutableTreeNode("Postgres");
    DefaultMutableTreeNode treeNode3 = new DefaultMutableTreeNode("Dev");
    treeNode2.add(treeNode3);
    treeNode3 = new DefaultMutableTreeNode("QA");
    treeNode2.add(treeNode3);
    treeNode3 = new DefaultMutableTreeNode("Production");
    treeNode2.add(treeNode3);
    treeNode3 = new DefaultMutableTreeNode("yellow");
    treeNode2.add(treeNode3);
    treeNode1.add(treeNode2);
    treeNode2 = new DefaultMutableTreeNode("Oracle");
    treeNode3 = new DefaultMutableTreeNode("Dev");
    treeNode2.add(treeNode3);
    treeNode3 = new DefaultMutableTreeNode("QA");
    treeNode2.add(treeNode3);
    treeNode3 = new DefaultMutableTreeNode("Production");
    treeNode2.add(treeNode3);
    treeNode1.add(treeNode2);
    profileTree.setModel(new DefaultTreeModel(treeNode1));
    profileTree.setMinimumSize(new Dimension(150, 400));
    profileTree.setName("profileTree"); // NOI18N
    profileTree.addMouseListener(formListener);
    profileTree.addTreeSelectionListener(formListener);
    treeScroll.setViewportView(profileTree);

    listPanel.add(treeScroll, BorderLayout.CENTER);

    jSplitPane.setLeftComponent(listPanel);

    add(jSplitPane, BorderLayout.CENTER);
  }

  // Code for dispatching events from components to event handlers.

  private class FormListener implements MouseListener, TreeSelectionListener
  {
    FormListener() {}
    public void mouseClicked(MouseEvent evt)
    {
      if (evt.getSource() == profileTree)
      {
        ProfileSelectionPanel.this.profileTreeMouseClicked(evt);
      }
    }

    public void mouseEntered(MouseEvent evt)
    {
    }

    public void mouseExited(MouseEvent evt)
    {
    }

    public void mousePressed(MouseEvent evt)
    {
    }

    public void mouseReleased(MouseEvent evt)
    {
    }

    public void valueChanged(TreeSelectionEvent evt)
    {
      if (evt.getSource() == profileTree)
      {
        ProfileSelectionPanel.this.profileTreeValueChanged(evt);
      }
    }
  }// </editor-fold>//GEN-END:initComponents
  private void profileTreeMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_profileTreeMouseClicked

    if (this.selectionListener != null && evt.getButton() == MouseEvent.BUTTON1 && evt.getClickCount() == 2)
    {
      Point p = evt.getPoint();

      TreePath selected = profileTree.getSelectionPath();

      // Ignore double clicks if nothing is selected.
      if (selected == null) return;

      TreePath path = profileTree.getPathForLocation((int)p.getX(), (int)p.getY());
      if (path == null) return;

      // ignore double clicks outside of the selected profile
      if (!path.isDescendant(selected)) return;

      TreeNode n = (TreeNode)path.getLastPathComponent();
      if (n.isLeaf())
      {
        this.selectionListener.accept(evt);
      }
    }
  }//GEN-LAST:event_profileTreeMouseClicked

  private void profileTreeValueChanged(javax.swing.event.TreeSelectionEvent evt) {//GEN-FIRST:event_profileTreeValueChanged

    if (this.connectionEditor == null)
    {
      return;
    }

    if (evt.getSource() == this.profileTree)
    {
      try
      {
        ConnectionProfile newProfile = getSelectedProfile();
        if (newProfile != null)
        {
          if (!this.connectionEditor.isVisible())
          {
            this.connectionEditor.setVisible(true);
          }
          this.connectionEditor.setProfile(newProfile);
          this.connectionEditor.setAllTags(model.getAllTags());
          getDeleteAction().setEnabled(true);
          this.copyItem.setEnabled(true);
        }
        else
        {
          this.connectionEditor.setVisible(false);
          getDeleteAction().setEnabled(true);
          this.copyItem.setEnabled(false);
        }
      }
      catch (Exception e)
      {
        LogMgr.logError(new CallerInfo(){}, "Error selecting new profile", e);
      }
    }
  }//GEN-LAST:event_profileTreeValueChanged

  // Variables declaration - do not modify//GEN-BEGIN:variables
  protected JSplitPane jSplitPane;
  protected JPanel listPanel;
  protected JTree profileTree;
  protected JScrollPane treeScroll;
  // End of variables declaration//GEN-END:variables
}
