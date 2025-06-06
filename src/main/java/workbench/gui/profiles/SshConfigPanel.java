/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0 (the "License")
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
package workbench.gui.profiles;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.ComboBoxModel;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;

import workbench.resource.ResourceMgr;
import workbench.ssh.SshConfig;
import workbench.ssh.SshConfigMgr;
import workbench.ssh.SshHostConfig;
import workbench.ssh.UrlParser;

import workbench.gui.components.DividerBorder;

import workbench.util.StringUtil;

/**
 *
 * @author Thomas Kellerer
 */
public class SshConfigPanel
  extends JPanel
  implements ActionListener
{

  public SshConfigPanel()
  {
    initComponents();
    hostConfigPanel.setBorder(new CompoundBorder(DividerBorder.BOTTOM_DIVIDER, new EmptyBorder(0,0,8,0)));
    jPanel1.setBorder(new CompoundBorder(DividerBorder.BOTTOM_DIVIDER, new EmptyBorder(0,0,8,0)));
    List<SshHostConfig> configs = new ArrayList<>();
    configs.add(null);
    configs.addAll(SshConfigMgr.getDefaultInstance().getGlobalConfigs());
    SshHostConfig[] cfgs = configs.toArray(SshHostConfig[]::new);

    DefaultComboBoxModel<SshHostConfig> model = new DefaultComboBoxModel<>(cfgs);
    globalConfigDD.setModel(model);
    hostConfigPanel.checkAgentUsage();
    globalConfigDD.addActionListener(this);
  }

  public void setConfig(SshConfig config, String url)
  {
    clear();

    if (config != null)
    {
      if (!selectHostConfig(config.getSshHostConfigName()))
      {
        hostConfigPanel.setConfig(config.getSshHostConfig());
      }
      dbHostname.setText(StringUtil.coalesce(config.getDbHostname(), ""));
      int localPortNr = config.getLocalPort();
      if (localPortNr > 0)
      {
        localPort.setText(Integer.toString(localPortNr));
      }

      int dbPortNr = config.getDbPort();
      if (dbPortNr > 0)
      {
        dbPort.setText(Integer.toString(dbPortNr));
      }
      checkHostConfig();
    }

    UrlParser parser = new UrlParser(url);
    if (parser.isLocalURL() == false)
    {
      rewriteUrl.setSelected(true);
    }
  }

  private boolean selectHostConfig(String name)
  {
    if (name == null)
    {
      globalConfigDD.setSelectedIndex(0);
      return false;
    }

    ComboBoxModel<SshHostConfig> model = globalConfigDD.getModel();
    for (int i=0; i < model.getSize(); i++)
    {
      SshHostConfig config = model.getElementAt(i);
      if (config != null && config.getConfigName().equals(name))
      {
        globalConfigDD.setSelectedItem(config);
        return true;
      }
    }
    return false;
  }

  @Override
  public void actionPerformed(ActionEvent e)
  {
    if (e.getSource() == globalConfigDD)
    {
      checkHostConfig();
    }
  }

  private void checkHostConfig()
  {
    SshHostConfig config = getSelectedGlobalConfig();
    if (config != null)
    {
      hostConfigPanel.setConfig(config);
      hostConfigPanel.setEnabled(false);
    }
    else
    {
      hostConfigPanel.setEnabled(true);
    }
  }

  private SshHostConfig getSelectedGlobalConfig()
  {
    // The first index is always the "empty" config indicating that no global config should be used.
    int index = globalConfigDD.getSelectedIndex();
    if (index <= 0) return null;
    return (SshHostConfig)globalConfigDD.getSelectedItem();
  }

  private void clear()
  {
    hostConfigPanel.clear();
    dbPort.setText("");
    dbHostname.setText("");
    localPort.setText("");
    rewriteUrl.setSelected(false);
  }

  public boolean rewriteURL()
  {
    return rewriteUrl.isSelected();
  }

  public SshConfig getConfig()
  {
    boolean globalConfig = false;
    SshHostConfig hostConfig = getSelectedGlobalConfig();
    if (hostConfig == null)
    {
      hostConfig = hostConfigPanel.getConfig();
      if (hostConfig == null || !hostConfig.isValid()) return null;
    }
    else
    {
      globalConfig = true;
    }

    String localPortNr = StringUtil.trimToNull(localPort.getText());

    SshConfig config = new SshConfig();
    if (globalConfig)
    {
      config.setSshHostConfigName(hostConfig.getConfigName());
    }
    else
    {
      config.setHostConfig(hostConfig);
    }
    config.setLocalPort(StringUtil.getIntValue(localPortNr, 0));
    config.setDbHostname(StringUtil.trimToNull(dbHostname.getText()));
    config.setDbPort(StringUtil.getIntValue(dbPort.getText(), 0));
    return config;
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
    GridBagConstraints gridBagConstraints;

    sshHostConfigPanel1 = new SshHostConfigPanel();
    labelLocalPort = new JLabel();
    localPort = new JTextField();
    labelDbPort = new JLabel();
    labelDbHostname = new JLabel();
    dbHostname = new JTextField();
    dbPort = new JTextField();
    rewriteUrl = new JCheckBox();
    hostConfigPanel = new SshHostConfigPanel();
    jPanel1 = new JPanel();
    globalConfigDD = new JComboBox<>();
    globalConfigLabel = new JLabel();
    jLabel1 = new JLabel();
    jLabel2 = new JLabel();

    setLayout(new GridBagLayout());

    labelLocalPort.setLabelFor(localPort);
    labelLocalPort.setText(ResourceMgr.getString("LblSshLocalPort")); // NOI18N
    labelLocalPort.setToolTipText(ResourceMgr.getString("d_LblSshLocalPort")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(5, 5, 0, 0);
    add(labelLocalPort, gridBagConstraints);

    localPort.setToolTipText(ResourceMgr.getString("d_LblSshLocalPort")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(5, 5, 0, 11);
    add(localPort, gridBagConstraints);

    labelDbPort.setLabelFor(dbHostname);
    labelDbPort.setText(ResourceMgr.getString("LblSshDbHostname")); // NOI18N
    labelDbPort.setToolTipText(ResourceMgr.getString("d_LblSshDbHostname")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(5, 5, 0, 0);
    add(labelDbPort, gridBagConstraints);

    labelDbHostname.setLabelFor(dbPort);
    labelDbHostname.setText(ResourceMgr.getString("LblSshDbPort")); // NOI18N
    labelDbHostname.setToolTipText(ResourceMgr.getString("d_LblSshDbPort")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(5, 5, 0, 0);
    add(labelDbHostname, gridBagConstraints);

    dbHostname.setToolTipText(ResourceMgr.getString("d_LblSshDbHostname")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(5, 5, 0, 11);
    add(dbHostname, gridBagConstraints);

    dbPort.setToolTipText(ResourceMgr.getString("d_LblSshDbPort")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(5, 5, 0, 11);
    add(dbPort, gridBagConstraints);

    rewriteUrl.setSelected(true);
    rewriteUrl.setText(ResourceMgr.getString("LblSshRewriteUrl")); // NOI18N
    rewriteUrl.setToolTipText(ResourceMgr.getString("d_LblSshRewriteUrl")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 7;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new Insets(7, 1, 0, 0);
    add(rewriteUrl, gridBagConstraints);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = GridBagConstraints.BOTH;
    gridBagConstraints.weightx = 1.0;
    add(hostConfigPanel, gridBagConstraints);

    jPanel1.setLayout(new GridBagLayout());

    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new Insets(0, 7, 0, 0);
    jPanel1.add(globalConfigDD, gridBagConstraints);

    globalConfigLabel.setText("Global SSH Host Configuration");
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    jPanel1.add(globalConfigLabel, gridBagConstraints);

    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new Insets(1, 5, 0, 0);
    add(jPanel1, gridBagConstraints);

    jLabel1.setText(ResourceMgr.getString("LblSshTunnel")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.insets = new Insets(5, 5, 2, 0);
    add(jLabel1, gridBagConstraints);

    jLabel2.setText(ResourceMgr.getString("LblSshConn")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.anchor = GridBagConstraints.FIRST_LINE_START;
    gridBagConstraints.insets = new Insets(12, 5, 3, 0);
    add(jLabel2, gridBagConstraints);
  }// </editor-fold>//GEN-END:initComponents


  // Variables declaration - do not modify//GEN-BEGIN:variables
  private JTextField dbHostname;
  private JTextField dbPort;
  private JComboBox<SshHostConfig> globalConfigDD;
  private JLabel globalConfigLabel;
  private SshHostConfigPanel hostConfigPanel;
  private JLabel jLabel1;
  private JLabel jLabel2;
  private JPanel jPanel1;
  private JLabel labelDbHostname;
  private JLabel labelDbPort;
  private JLabel labelLocalPort;
  private JTextField localPort;
  private JCheckBox rewriteUrl;
  private SshHostConfigPanel sshHostConfigPanel1;
  // End of variables declaration//GEN-END:variables
}
