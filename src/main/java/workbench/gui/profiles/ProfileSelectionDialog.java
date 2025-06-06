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
import java.awt.Color;
import java.awt.Cursor;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;

import workbench.interfaces.EventDisplay;
import workbench.resource.IconMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.ConnectionMgr;
import workbench.db.ConnectionProfile;
import workbench.db.DbDriver;

import workbench.gui.WbSwingUtilities;
import workbench.gui.actions.EscAction;
import workbench.gui.components.WbButton;
import workbench.gui.help.HelpManager;

import workbench.util.EventNotifier;
import workbench.util.NotifierEvent;
import workbench.util.StringUtil;

/**
 *
 * @author  Thomas Kellerer
 */
public class ProfileSelectionDialog
  extends JDialog
  implements ActionListener, WindowListener, TreeSelectionListener, EventDisplay, ProfileChangeListener
{
  private JPanel okCancelPanel;
  private JButton okButton;
  private JButton cancelButton;
  private WbButton helpButton;
  private WbButton manageDriversButton;
  private ProfileSelectionPanel profiles;
  private ConnectionProfile selectedProfile;
  private boolean cancelled;
  private final String escActionCommand;
  private JLabel versionInfo;
  private boolean processEscKey;
  private static boolean firstDisplay = true;

  public ProfileSelectionDialog(Frame parent, boolean modal, String lastProfileKey)
  {
    super(parent, modal);
    initComponents(lastProfileKey);
    if (firstDisplay)
    {
      EventNotifier.getInstance().addEventDisplay(this);
      firstDisplay = false;
    }
    enableDefaultButtons();
    EscAction esc = new EscAction(this, this);
    escActionCommand = esc.getActionName();
  }

  public void enableDefaultButtons()
  {
    JRootPane root = this.getRootPane();
    root.setDefaultButton(okButton);
    processEscKey = true;
  }

  public void disableDefaultButtons()
  {
    JRootPane root = this.getRootPane();
    root.setDefaultButton(null);
    processEscKey = false;
  }

  private void initComponents(String lastProfileKey)
  {
    profiles = new ProfileSelectionPanel(lastProfileKey);

    JPanel buttonPanel = new JPanel();
    buttonPanel.setLayout(new BorderLayout(0, 0));

    JPanel toolsButtonPanel = new JPanel();
    toolsButtonPanel.setLayout(new FlowLayout(FlowLayout.LEFT));

    manageDriversButton = new WbButton();
    manageDriversButton.setResourceKey("LblEditDrivers");
    manageDriversButton.addActionListener(this);

    helpButton = new WbButton();
    helpButton.setResourceKey("LblHelp");
    helpButton.addActionListener(this);
    toolsButtonPanel.add(manageDriversButton);
    toolsButtonPanel.add(helpButton);

    okCancelPanel = new JPanel();
    buttonPanel.add(okCancelPanel, BorderLayout.EAST);
    buttonPanel.add(toolsButtonPanel, BorderLayout.WEST);
    versionInfo = new JLabel("  ");
    versionInfo.setForeground(Color.RED);
    versionInfo.setBorder(new EmptyBorder(0, 15, 0, 0));
    buttonPanel.add(versionInfo, BorderLayout.CENTER);

    okButton = new WbButton(ResourceMgr.getString(ResourceMgr.TXT_OK));
    okButton.setEnabled(profiles.getSelectedProfile() != null);

    cancelButton = new WbButton(ResourceMgr.getString(ResourceMgr.TXT_CANCEL));

    WbSwingUtilities.makeEqualWidth(manageDriversButton, helpButton);
    WbSwingUtilities.makeEqualWidth(okButton, cancelButton);

    addWindowListener(this);
    okCancelPanel.setLayout(new FlowLayout(FlowLayout.RIGHT));

    okCancelPanel.add(okButton);
    okButton.addActionListener(this);

    okCancelPanel.add(cancelButton);
    cancelButton.addActionListener(this);

    profiles.addProfileSelectionListener( (evt -> profileListClicked(evt))  );
    profiles.addSelectionListener(this);
    profiles.addProfileChangelistener(this);

    BorderLayout bl = new BorderLayout();
    this.getContentPane().setLayout(bl);
    getContentPane().add(profiles, BorderLayout.CENTER);
    getContentPane().add(buttonPanel, BorderLayout.SOUTH);

    setTitle(ResourceMgr.getString("LblSelectProfile"));
    this.restoreSize();

    WbSwingUtilities.requestComponentFocus(this, profiles.getInitialFocusComponent());
  }

  @Override
  public void showAlert(final NotifierEvent event)
  {
    if (versionInfo == null) return;

    versionInfo.addMouseListener(
      new MouseAdapter()
      {
        @Override
        public void mouseClicked(MouseEvent e)
        {
          if (e.getButton() == MouseEvent.BUTTON1)
          {
            ActionEvent evt = new ActionEvent(ProfileSelectionDialog.this, -1, event.getType());
            versionInfo.removeMouseListener(this);
            event.getHandler().actionPerformed(evt);
          }
        }
      });

    WbSwingUtilities.invokeLater(() ->
    {
      versionInfo.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      versionInfo.setIcon(IconMgr.getInstance().getLabelIcon(event.getIconKey()));
      versionInfo.setText("<html><b>" + event.getMessage() + "</b></html>");
      String tip = event.getTooltip();
      if (StringUtil.isNotEmpty(tip))
      {
        versionInfo.setToolTipText(tip);
      }
      versionInfo.getParent().doLayout();
    });
  }

  @Override
  public void removeAlert()
  {
    if (versionInfo == null) return;

    WbSwingUtilities.invoke(() ->
    {
      versionInfo.setCursor(null);
      versionInfo.setIcon(null);
      versionInfo.setText("");
      versionInfo.getParent().doLayout();
    });
  }

  private void closeDialog()
  {
    this.saveSize();
    this.profiles.saveSettings();
    this.setVisible(false);
    dispose();
  }

  @Override
  public void profileChanged(ConnectionProfile profile)
  {
    this.okButton.setEnabled(profile != null && profile.isConfigured());
  }

  public ConnectionProfile getSelectedProfile()
  {
    return this.selectedProfile;
  }

  public void restoreSize()
  {
    if (!Settings.getInstance().restoreWindowSize(this))
    {
      this.pack();
      // for some reason pack() doesn't calculate the width correctly
      this.setSize((int)(getWidth() * 1.25), (int)(getHeight() * 1.2));
      this.profiles.initDivider();
    }
  }

  public void saveSize()
  {
    Settings s = Settings.getInstance();
    s.storeWindowSize(this);
  }

  public void selectProfile()
  {
    if (this.profiles.validateInput())
    {
      this.selectedProfile = this.profiles.getSelectedProfile();
      boolean ok = ConnectionGuiHelper.doPrompt(this, selectedProfile);

      if (ok)
      {
        this.cancelled = false;
        this.closeDialog();
        if (Settings.getInstance().getSaveProfilesImmediately())
        {
          ConnectionMgr.getInstance().saveProfiles();
        }
      }
    }
  }

  public void profileListClicked(MouseEvent evt)
  {
    if (evt.getButton() == MouseEvent.BUTTON1 && evt.getClickCount() == 2)
    {
      if (profiles.getSelectedProfile() != null)
      {
        profiles.applyProfiles();
        selectProfile();
      }
    }
  }

  @Override
  public void actionPerformed(ActionEvent e)
  {
    if (e.getSource() == this.okButton)
    {
      profiles.applyProfiles();
      selectProfile();
    }
    else if (e.getSource() == this.cancelButton || (processEscKey && e.getActionCommand().equals(escActionCommand)))
    {
      this.selectedProfile = null;
      this.cancelled = true;
      this.closeDialog();
    }
    else if (e.getSource() == this.manageDriversButton)
    {
      showDriverEditorDialog();
    }
    else if (e.getSource() == this.helpButton)
    {
      HelpManager.showProfileHelp();
    }
  }

  public boolean isCancelled()
  {
    return this.cancelled;
  }

  @Override
  public void windowActivated(WindowEvent e)
  {
  }

  @Override
  public void windowClosed(WindowEvent e)
  {
    this.profiles.done();
  }

  @Override
  public void windowClosing(WindowEvent e)
  {
    this.cancelled = true;
    this.selectedProfile = null;
    this.closeDialog();
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
    this.cancelled = true;
    this.selectedProfile = null;
  }

  @Override
  public void valueChanged(TreeSelectionEvent e)
  {
    this.okButton.setEnabled(profiles.getSelectedProfile() != null && profiles.getSelectedProfile().isConfigured());
  }

  private void showDriverEditorDialog()
  {
    final Frame parent = (Frame)this.getParent();
    final DbDriver drv = this.profiles.getCurrentDriver();
    DriverEditorDialog.showDriverDialog(parent, drv);
  }
}
