/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2020 Thomas Kellerer.
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
package workbench.util;

import java.util.List;

import workbench.WbManager;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

import workbench.db.ConnectionMgr;
import workbench.db.ConnectionProfile;

import workbench.gui.components.MasterPwdInputForm;
import workbench.gui.components.ValidatingDialog;

/**
 *
 * @author Thomas Kellerer
 */
public class GlobalPasswordManager
{
  private WbAESCipher masterCipher;
  private String masterPassword;

  private static class InstanceHolder
  {
    private static final GlobalPasswordManager INSTANCE = new GlobalPasswordManager();
  }

  public static final GlobalPasswordManager getInstance()
  {
    return InstanceHolder.INSTANCE;
  }

  private GlobalPasswordManager()
  {
  }

  public String encrypt(String plaintext)
  {
    if (masterCipher == null) return plaintext;
    return masterCipher.encryptString(plaintext);
  }

  public String decrypt(String toDecrypt)
  {
    if (Settings.getInstance().getUseMasterPassword())
    {
      initCipher();
    }
    else
    {
      return toDecrypt;
    }

    return masterCipher.decryptString(toDecrypt);
  }

  public synchronized boolean showPasswordPromptIfNeeded()
  {
    return showPasswordPrompt(false);
  }

  /**
   * Applies a new password to all connection profiles.
   *
   * <p>If currently a master password is set, it is used to decrypt the existing password</p>
   * If the new password is null, then the master password is removed.
   *
   * @param newPassword  the new, unencrypted password, may be null
   */
  public synchronized void applyNewPassword(String newPassword)
  {
    List<ConnectionProfile> profiles = ConnectionMgr.getInstance().getProfiles();

    WbAESCipher newCipher = null;
    if (newPassword != null)
    {
      LogMgr.logDebug(new CallerInfo(){}, "Applying new master password");
      newCipher = new WbAESCipher(newPassword);
    }
    else
    {
      LogMgr.logDebug(new CallerInfo(){}, "Removing master password");
    }

    for (ConnectionProfile profile : profiles)
    {
      if (profile.getStorePassword())
      {
        String pwd = decryptProfilePassword(profile);
        if (newCipher != null && StringUtil.isNonEmpty(pwd))
        {
          String encrypted = newCipher.encryptString(pwd);
          profile.setEncryptedPassword(ConnectionProfile.MASTER_CRYPT_PREFIX + encrypted);
        }
        else
        {
          profile.setEncryptedPassword(pwd);
        }
      }
    }
    this.masterCipher = newCipher;
    if (masterCipher != null)
    {
      Settings.getInstance().setEncryptedMasterPassword(masterCipher.encryptString(newPassword));
    }
    else
    {
      Settings.getInstance().setEncryptedMasterPassword(null);
    }
  }


  private String decryptProfilePassword(ConnectionProfile profile)
  {
    String input = profile.getPassword();
    if (StringUtil.isEmptyString(input)) return input;
    if (input.startsWith(ConnectionProfile.CRYPT_PREFIX))
    {
      WbCipher des = WbDesCipher.getInstance();
      return des.decryptString(input.substring(ConnectionProfile.CRYPT_PREFIX.length()));
    }
    else if (input.startsWith(ConnectionProfile.MASTER_CRYPT_PREFIX) && this.masterCipher != null)
    {
      return masterCipher.decryptString(input.substring(ConnectionProfile.MASTER_CRYPT_PREFIX.length()));
    }
    return input;
  }

  /**
   * Show the password prompt for the master password if needed and initialize this instance with it.
   * <p>
   * Once the master password was entered correctly, this method will always return true.
   *
   * @return true if the user entered the correct master password
   */
  public synchronized boolean showPasswordPrompt(boolean promptAlways)
  {
    if (Settings.getInstance().getUseMasterPassword() && (promptAlways || this.masterCipher == null))
    {
      return promptForPassword();
    }
    return true;
  }

  private void initCipher()
  {
    if (masterCipher != null) return;
    showPasswordPrompt(false);
  }

  private boolean promptForPassword()
  {
    this.masterCipher = null;
    MasterPwdInputForm input = new MasterPwdInputForm(this);

    boolean ok = ValidatingDialog.showConfirmDialog(WbManager.getInstance().getCurrentWindow(), input, ResourceMgr.getString("MsgEnterMasterPwd"));
    return ok;
  }

  public boolean validateMasterPassword(String userInput)
  {
    String encrypted = Settings.getInstance().getEncryptedMasterPassword();
    if (encrypted == null)
    {
      return false;
    }
    WbAESCipher cipher = new WbAESCipher(userInput);

    String inputEncrypted = cipher.encryptString(userInput);
    boolean result = encrypted.equals(inputEncrypted);
    if (result)
    {
      this.masterPassword = userInput;
      this.masterCipher = cipher;
    }
    else
    {
      this.masterPassword = null;
      this.masterCipher = null;
    }
    return result;
  }
}
