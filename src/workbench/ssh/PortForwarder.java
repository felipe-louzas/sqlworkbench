/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2016 Thomas Kellerer.
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
package workbench.ssh;

import java.io.File;
import java.util.Properties;
import java.util.Vector;

import workbench.WbManager;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;

import workbench.gui.WbSwingUtilities;

import workbench.util.WbFile;

import com.jcraft.jsch.Identity;
import com.jcraft.jsch.IdentityRepository;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;
import com.jcraft.jsch.agentproxy.Connector;
import com.jcraft.jsch.agentproxy.ConnectorFactory;
import com.jcraft.jsch.agentproxy.RemoteIdentityRepository;

/**
 *
 * @author Thomas Kellerer
 */
public class PortForwarder
  implements UserInfo
{
  public static final int DEFAULT_SSH_PORT = 22;

  private String sshHost;
  private String sshUser;
  private String password;
  private String passphrase;
  private String privateKeyFile;

  private Session session;
  private int localPort;

  private boolean tryAgent;

  public PortForwarder(SshHostConfig config)
  {
    this.sshHost = config.getHostname();
    this.sshUser = config.getUsername();
    this.password = config.getDecryptedPassword();
    this.tryAgent = config.getTryAgent();
    setPrivateKeyFile(config.getPrivateKeyFile());
  }

  private void setPrivateKeyFile(String keyFile)
  {
    this.privateKeyFile = null;
    if (keyFile != null)
    {
      File f = new File(keyFile);
      if (f.exists())
      {
        privateKeyFile = f.getAbsolutePath();
      }
    }
  }

  /**
   * Forwards a local port to a remote port.
   *
   * @param remoteHost  the remote host (as seen from the SSH host, typically the DB server)
   * @param remotePort  the port of the remote host
   *
   * @return the local port used for forwarding
   */
  public int startFowarding(String remoteDbServer, int remoteDbPort)
    throws JSchException
  {
    return startForwarding(remoteDbServer, remoteDbPort, 0, DEFAULT_SSH_PORT);
  }

  /**
   * Forwards a local port to a remote port.
   *
   * @param remoteHost      the remote host (as seen from the SSH host, typically the DB server)
   * @param remotePort      the port of the remote host
   * @param localPortToUse  the local port to use. If 0 choose a free port
   *
   * @return the local port  used for forwarding
   */
  public synchronized int startForwarding(String remoteDbServer, int remoteDbPort, int localPortToUse, int sshPort)
    throws JSchException
  {
    Properties props = new Properties();
    props.put("StrictHostKeyChecking", "no");
    JSch jsch = new JSch();

    final CallerInfo ci = new CallerInfo(){};

    long start = System.currentTimeMillis();
    LogMgr.logDebug(ci, "Connecting to SSH host: " + sshHost + ":" + sshPort + " using username: " + sshUser);

    boolean useAgent = tryAgent && tryAgent(jsch);

    if (!useAgent && privateKeyFile != null)
    {
      jsch.addIdentity(privateKeyFile, password);
    }

    session = jsch.getSession(sshUser, sshHost, sshPort);
    session.setUserInfo(this);

    if (!useAgent && privateKeyFile == null)
    {
      props.put("PreferredAuthentications", "password,keyboard-interactive");
      session.setPassword(password);
    }

    session.setConfig(props);
    session.connect();
    long duration = System.currentTimeMillis() - start;
    LogMgr.logInfo(ci, "Connected to SSH host: " + sshHost + ":" + sshPort + " using username: " + sshUser + " (" + duration + "ms)");

    if (localPortToUse < 0) localPortToUse = 0;

    localPort = session.setPortForwardingL(localPortToUse, remoteDbServer, remoteDbPort);
    LogMgr.logInfo(ci, "Port forwarding established: localhost:"  + localPort + " -> " + remoteDbServer + ":" + remoteDbPort + " through host " + sshHost);

    return localPort;
  }

  private boolean tryAgent(JSch jsh)
  {
    try
    {
      Connector connector = ConnectorFactory.getDefault().createConnector();
      if (connector == null) return false;

      IdentityRepository irepo = new RemoteIdentityRepository(connector);
      Vector<Identity> identities = irepo.getIdentities();
      if (identities.size() > 0)
      {
        LogMgr.logInfo(new CallerInfo(){}, "Using " + identities.size() + " identities from agent: " + connector.getName());
        jsh.setIdentityRepository(irepo);
        return true;
      }
    }
    catch (Throwable th)
    {
      LogMgr.logError(new CallerInfo(){}, "Error when accessing agent", th);
    }
    return false;
  }

  @Override
  public String toString()
  {
    return this.sshUser + "@" + this.sshHost + " localport: " + this.localPort;
  }

  public synchronized boolean isConnected()
  {
    return session != null && session.isConnected();
  }

  public int getLocalPort()
  {
    return localPort;
  }

  public synchronized void close()
  {
    if (isConnected())
    {
      LogMgr.logDebug(new CallerInfo(){}, "Disconnecting ssh session to host: " + session.getHost());
      session.disconnect();
    }
    session = null;
    localPort = -1;
  }

  @Override
  public String getPassphrase()
  {
    LogMgr.logDebug(new CallerInfo(){}, "UserInfo.getPassphrase() called.");
    return this.passphrase;
  }

  @Override
  public String getPassword()
  {
    LogMgr.logDebug(new CallerInfo(){}, "UserInfo.getPassword() called.");
    return password;
  }

  @Override
  public boolean promptPassword(String message)
  {
    LogMgr.logDebug(new CallerInfo(){}, "UserInfo.promptPassword() called with message: " + message);

    String title = ResourceMgr.getString("MsgInputSshPwd");
    String dest = message.replace("Password for ", "");
    String msg = ResourceMgr.getFormattedString("MsgInputPwd", dest);
    String pwd = WbSwingUtilities.passwordPrompt(WbManager.getInstance().getCurrentWindow(), title, msg);
    if (pwd == null) return false;
    this.password = pwd;
    return true;
  }

  @Override
  public boolean promptPassphrase(String message)
  {
    LogMgr.logDebug(new CallerInfo(){}, "UserInfo.promptPassphrase() called with message: " + message);

    String title = ResourceMgr.getString("MsgInputSshPassPhrase");
    WbFile f = new WbFile(this.privateKeyFile);
    String msg = ResourceMgr.getFormattedString("MsgInputPwd", f.getFileName());
    String pwd = WbSwingUtilities.passwordPrompt(WbManager.getInstance().getCurrentWindow(), title, msg);
    if (pwd == null) return false;
    this.passphrase = pwd;
    return true;
  }

  @Override
  public boolean promptYesNo(String message)
  {
    LogMgr.logDebug(new CallerInfo(){}, "UserInfo.promptYesNo() called with message: " + message);
    return true;
  }

  @Override
  public void showMessage(String message)
  {
    LogMgr.logDebug(new CallerInfo(){}, "UserInfo.showMessage() called with message: " + message);
  }

}
