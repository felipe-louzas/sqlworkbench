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

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.AbstractListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListModel;
import javax.swing.ListSelectionModel;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import workbench.interfaces.FileActions;
import workbench.interfaces.Restoreable;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;

import workbench.gui.WbSwingUtilities;
import workbench.gui.actions.DeleteListEntryAction;
import workbench.gui.actions.NewListEntryAction;
import workbench.gui.actions.WbAction;
import workbench.gui.components.ClassFinderGUI;
import workbench.gui.components.DividerBorder;
import workbench.gui.components.WbScrollPane;
import workbench.gui.components.WbToolbar;
import workbench.gui.lnf.LnFDefinition;
import workbench.gui.lnf.LnFManager;

import workbench.util.ClassFinder;
import workbench.util.ClassInfo;
import workbench.util.ClasspathUtil;
import workbench.util.WbThread;

/**
 *
 * @author Thomas Kellerer
 */
public class LnFOptionsPanel
  extends JPanel
  implements Restoreable, ListSelectionListener, FileActions,
             PropertyChangeListener
{
  private final JList lnfList;
  private final LnFDefinitionPanel definitionPanel;
  private final LnFManager manager = new LnFManager();
  private final WbToolbar toolbar;
  private final DeleteListEntryAction deleteEntry;

  public LnFOptionsPanel()
  {
    super();
    setLayout(new BorderLayout());

    lnfList = new JList();
    lnfList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    lnfList.setBorder(new EmptyBorder(2,1,2,1));

    lnfList.setMinimumSize(new Dimension(100, 100));
    JScrollPane scroll = new WbScrollPane(lnfList);

    WbAction search = new WbAction()
    {
      @Override
      public void executeAction(ActionEvent e)
      {
        WbThread th = new WbThread("LnF Searcher")
        {
          @Override
          public void run()
          {
            startLnFSearch();
          }
        };
        th.start();
      }
    };
    search.setIcon("find-lnf");

    deleteEntry = new DeleteListEntryAction(this);
    this.toolbar = new WbToolbar();
    this.toolbar.add(new NewListEntryAction(this));
    this.toolbar.add(search);
    this.toolbar.addSeparator();
    this.toolbar.add(deleteEntry);
    toolbar.setBorder(DividerBorder.BOTTOM_DIVIDER);

    definitionPanel = new LnFDefinitionPanel();
    definitionPanel.setPropertyListener(this);

    add(scroll, BorderLayout.WEST);
    add(toolbar, BorderLayout.NORTH);
    add(definitionPanel, java.awt.BorderLayout.CENTER);

    ListModel model = new LnfList();
    lnfList.setModel(model);
    lnfList.addListSelectionListener(this);
  }

  private void startLnFSearch()
  {
    try
    {
      WbSwingUtilities.showWaitCursor(this);
      definitionPanel.setStatusMessage(ResourceMgr.getString("TxtSearchingLnF"));
      ClasspathUtil util = new ClasspathUtil();
      ClassFinder finder = new ClassFinder(LookAndFeel.class);
      List<File> classPath = util.getExtLibs();
      List<ClassInfo> available = finder.findImplementingClasses(classPath);
      if (LogMgr.isDebugEnabled())
      {
        String lnfMessage = available.stream().map(ci -> ci.toString()).collect(Collectors.joining(","));
        LogMgr.logDebug(new CallerInfo(){}, "Found Look and Feel implementations: " + lnfMessage);
      }
      WbSwingUtilities.showDefaultCursor(this);
      definitionPanel.setStatusMessage("");
      addItemFromClasspath(available);
    }
    catch (Exception ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not read Look and Feel implementations", ex);
    }
    finally
    {
      definitionPanel.setStatusMessage("");
      WbSwingUtilities.showDefaultCursor(this);
    }
  }

  private void addItemFromClasspath(List<ClassInfo> available)
  {
    final List<ClassInfo> newClasses =
      available.stream().filter(cls -> !manager.isRegistered(cls.getClassName())).collect(Collectors.toList());

    if (LogMgr.isDebugEnabled())
    {
      String lnfMessage = newClasses.stream().map(ci -> ci.toString()).collect(Collectors.joining(","));
      LogMgr.logDebug(new CallerInfo(){}, "Found new Look And Feel implementations: " + lnfMessage);
    }

    if (newClasses.isEmpty())
    {
      WbSwingUtilities.showMessageKey(this, "MsgNoLaF");
    }
    else
    {
      final List<String> classNames = newClasses.stream().map(ci -> ci.getClassName()).collect(Collectors.toList());
      final String newLnFClass = ClassFinderGUI.selectEntry(classNames, null, "TxtSelectLnF", SwingUtilities.getWindowAncestor(this));
      if (newLnFClass != null)
      {
        WbSwingUtilities.invokeLater(() ->
        {
          int idx = classNames.indexOf(newLnFClass);
          createNewItem(newClasses.get(idx));
        });
      }
    }
  }

  @Override
  public void saveSettings()
  {
    manager.saveLookAndFeelDefinitions();
  }

  @Override
  public void restoreSettings()
  {
    LnFDefinition clnf = manager.getCurrentLnF();
    lnfList.setSelectedValue(clnf, true);
    definitionPanel.setCurrentLookAndFeeld(clnf);
  }

  @Override
  public void valueChanged(ListSelectionEvent evt)
  {
    LnFDefinition def = (LnFDefinition)lnfList.getSelectedValue();
    definitionPanel.setDefinition(def);
    if (def != null)
    {
      this.deleteEntry.setEnabled(!def.isBuiltIn());
    }
  }

  @Override
  public void saveItem() throws Exception
  {
  }

  @Override
  public void deleteItem() throws Exception
  {
    LnFDefinition def = (LnFDefinition)lnfList.getSelectedValue();
    int index = lnfList.getSelectedIndex();
    if (def != null)
    {
      manager.removeDefinition(def);
    }
    if (lnfList.getModel().getSize() == 0)
    {
      definitionPanel.setDefinition(null);
    }
    if (index >= lnfList.getModel().getSize())
    {
      index = lnfList.getModel().getSize() - 1;
    }
    lnfList.setSelectedIndex(index);
    valueChanged(null);
    lnfList.repaint();
  }

  private void createNewItem(ClassInfo lnfClass)
  {
    String className = lnfClass.getClassName();
    List<String> libs = Collections.singletonList(lnfClass.getJarFile().getAbsolutePath());
    LnFDefinition def = new LnFDefinition(className.substring(className.lastIndexOf('.') + 1), className, libs);
    int index = manager.addDefinition(def);
    lnfList.setSelectedIndex(index);
    definitionPanel.setDefinition(def);
    lnfList.updateUI();
  }

  @Override
  public void newItem(boolean copyCurrent)
  {
    try
    {
      LnFDefinition def = null;
      if (copyCurrent)
      {
        int index = lnfList.getSelectedIndex();
        LnFDefinition current = manager.getAvailableLookAndFeels().get(index);
        def = current.createCopy();
      }
      else
      {
        String d = ResourceMgr.getString("TxtLnFSample");
        def = new LnFDefinition(d);
      }
      int index = manager.addDefinition(def);
      lnfList.setSelectedIndex(index);
      definitionPanel.setDefinition(def);
      lnfList.updateUI();
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Error creating new item", e);
    }
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt)
  {
    if (evt.getPropertyName().equals("name"))
    {
      lnfList.repaint();
    }
  }

  private class LnfList
    extends AbstractListModel
  {
    @Override
    public Object getElementAt(int index)
    {
      return manager.getAvailableLookAndFeels().get(index);
    }

    @Override
    public int getSize()
    {
      return manager.getAvailableLookAndFeels().size();
    }
  }

}
