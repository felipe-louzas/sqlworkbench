/*
 * WbDesCipher.java
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
package workbench.util;

import java.security.spec.KeySpec;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;


/**
 * @author  Thomas Kellerer
 */
public class WbAESCipher
  implements WbCipher
{
  private static final byte[] SALT = new byte[] {42,10,33,17,8,52,-17,19,26,65,-42,17,-88};
  private static final byte[] IV = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};

  private Cipher cipher;
  private IvParameterSpec ivspec;
  private SecretKeySpec secretKey;

  public WbAESCipher(String password)
  {
    try
    {
      this.cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
      this.ivspec = new IvParameterSpec(IV);
      SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
      KeySpec spec = new PBEKeySpec(password.toCharArray(), SALT, 65536, 256);
      SecretKey tmp = factory.generateSecret(spec);
      this.secretKey = new SecretKeySpec(tmp.getEncoded(), "AES");
    }
    catch (Exception e)
    {
      LogMgr.logWarning(new CallerInfo(){}, "No encryption available!", e);
    }
  }

  @Override
  public String decryptString(String toDecrypt)
  {
    if (toDecrypt == null) return toDecrypt;
    try
    {
      cipher.init(Cipher.DECRYPT_MODE, secretKey, ivspec);
      byte[] decoded = Base64.getDecoder().decode(toDecrypt);
      byte[] decrypted = cipher.doFinal(decoded);
      return new String(decrypted, "UTF-8");
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not decrypt", e);
      return toDecrypt;
    }
  }

  @Override
  public String encryptString(String toEncrypt)
  {
    if (toEncrypt == null) return null;
    if (cipher == null) return toEncrypt;

    try
    {
      cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivspec);
      byte[] encrypted = cipher.doFinal(toEncrypt.getBytes("UTF-8"));
      return Base64.getEncoder().encodeToString(encrypted);
    }
    catch (Exception e)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not encrypt", e);
      return toEncrypt;
    }
  }

}
