/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2025 Thomas Kellerer
 *
 * Licensed under a modified Apache License, Version 2.0
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

import workbench.db.ConnectionProfile;
import workbench.db.IniProfileStorage;
import workbench.db.ProfileStorage;
import workbench.db.XmlProfileStorage;

/**
 *
 * @author Thomas Kellerer
 */
public class ProfileConverter
{
  public static void main(String[] args)
  {
    if (args.length != 1)
    {
      System.err.println("Usage: ProfileConverter inputfile");
      System.exit(1);
    }

    WbFile in = new WbFile(args[0]);

    if (!in.exists())
    {
      System.out.println("File " + in.getFullPath() + " not found!");
      System.exit(2);
    }

    WbFile out = null;
    ProfileStorage reader = null;
    ProfileStorage writer = null;

    if (in.getExtension().equalsIgnoreCase(IniProfileStorage.EXTENSION))
    {
      out = new WbFile(in.getParentFile(), XmlProfileStorage.DEFAULT_FILE_NAME);
      reader = new IniProfileStorage();
      writer = new XmlProfileStorage();
    }
    else
    {
      out = new WbFile(in.getParentFile(), IniProfileStorage.DEFAULT_FILE_NAME);
      reader = new XmlProfileStorage();
      writer = new IniProfileStorage();
    }

    if (out.exists())
    {
      out.makeBackup();
    }

    System.out.println("Converting " + in.getFullPath() + " to " + out.getFullPath());

    List<ConnectionProfile> profiles = reader.readProfiles(in);
    writer.saveProfiles(profiles, out);
  }

}
