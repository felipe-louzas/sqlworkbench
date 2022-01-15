/*
 * This file is part of SQL Workbench/J, https://www.sql-workbench.eu
 *
 * Copyright 2002-2022 Thomas Kellerer.
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
package workbench.util.download;

import java.beans.XMLDecoder;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JProgressBar;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.Settings;

import workbench.gui.WbSwingUtilities;

import workbench.util.StringUtil;
import workbench.util.WbFile;

/**
 * A class to download JDBC drivers (or any artefact actually) from Maven central.
 *
 * @author Thomas Kellerer
 */
public class MavenDownloader
{
  private static final String BASE_SEARCH_URL = "https://search.maven.org/solrsearch/select?";
  private String lastHttpMsg = null;
  private int lastHttpCode = -1;
  private int contentLength = -1;
  private final List<MavenArtefact> knownArtefacts;
  private HttpURLConnection connection;
  private boolean cancelled = false;
  private JProgressBar progressBar;

  public MavenDownloader()
  {
    this.knownArtefacts = getKnownArtefacts();
  }

  public String getLastHttpMsg()
  {
    return lastHttpMsg;
  }

  public int getLastHttpCode()
  {
    return lastHttpCode;
  }

  public int getContentLength()
  {
    return contentLength;
  }

  public void setProgressBar(JProgressBar bar)
  {
    this.progressBar = bar;
  }

  public String searchForLatestVersion(String groupId, String artefactId)
  {
    String url = buildSearchUrl(groupId, artefactId);
    String searchResult = retrieveSearchResult(url);
    String version = getLatestVersion(searchResult);
    return version;
  }

  private String buildSearchUrl(String groupId, String artefactId)
  {
    return BASE_SEARCH_URL + "q=g:" + groupId + "+AND+a:" + artefactId + "&rows=2&wt=xml";
  }

  private String retrieveSearchResult(String searchUrl)
  {
    try
    {
      Duration timeout = Duration.ofMillis(Settings.getInstance().getIntProperty("workbench.maven.search.timeout", 2500));
      HttpClient client = HttpClient.newHttpClient();
      HttpRequest request = HttpRequest.
        newBuilder().
        uri(URI.create(searchUrl)).
        timeout(timeout).
        build();

      HttpResponse response = client.send(request, HttpResponse.BodyHandlers.ofString());
      lastHttpCode = response.statusCode();
      lastHttpMsg = "";
      Object body = response.body();
      if (body != null)
      {
        return body.toString();
      }
    }
    catch (Throwable th)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not search Maven using URL=" + searchUrl, th);
      lastHttpMsg = th.getMessage();
    }
    return null;
  }

  public void cancelDownload()
  {
    this.cancelled = true;
    if (this.connection == null) return;
    try
    {
      this.connection.disconnect();
      LogMgr.logDebug(new CallerInfo(){}, "Download cancelled");
    }
    catch (Throwable th)
    {
      LogMgr.logDebug(new CallerInfo(){}, "Error when closing connection");
    }
  }

  private void initProgressBar()
  {
    if (progressBar == null) return;
    if (contentLength > 0)
    {
      progressBar.setIndeterminate(false);
      progressBar.setMaximum(contentLength);
    }
    else
    {
      progressBar.setIndeterminate(true);
      progressBar.setValue(1);
    }
  }

  public void updateProgressBar(int length)
  {
    if (progressBar == null) return;
    if (contentLength < 0) return;
    if (length < 0) return;
    progressBar.setValue(length);
  }

  public long download(MavenArtefact artefact, File targetDir)
  {
    if (artefact == null) return -1;
    if (!artefact.isComplete()) return -1;

    this.cancelled = false;
    String downloadUrl = artefact.buildDownloadUrl();
    String fileName = artefact.buildFilename();
    WbFile target = new WbFile(targetDir, fileName);

    long bytes = -1;
    try
    {
      WbSwingUtilities.invoke(this::initProgressBar);
      long start = System.currentTimeMillis();

      connection = (HttpURLConnection)new URL(downloadUrl).openConnection();
      lastHttpCode = connection.getResponseCode();
      lastHttpMsg = connection.getResponseMessage();
      contentLength = connection.getContentLength();
      WbSwingUtilities.invoke(this::initProgressBar);

      LogMgr.logDebug(new CallerInfo(){},
        "URL: " + downloadUrl +
        ", HTTP status: " + lastHttpCode +
        ", message: " + lastHttpMsg +
        ", contentLength: " + contentLength);

      int filesize = 0;
      try (InputStream in = connection.getInputStream();
           OutputStream out = new BufferedOutputStream(new FileOutputStream(target));)
      {
        byte[] buffer = new byte[8192];
        int bytesRead = in.read(buffer);
        while (bytesRead != -1)
        {
          filesize += bytesRead;
          out.write(buffer, 0, bytesRead);
          bytesRead = in.read(buffer);
          if (cancelled) break;
          final int len = filesize;
          WbSwingUtilities.invokeLater(() -> {updateProgressBar(len);});
        }
      }
      long duration = System.currentTimeMillis() - start;

      if (!cancelled) bytes = filesize;
      LogMgr.logInfo(new CallerInfo(){}, "Downloaded " + bytes + " bytes from \"" + downloadUrl + "\" in "+ duration + "ms");
    }
    catch (Throwable th)
    {
      if (!cancelled)
      {
        LogMgr.logError(new CallerInfo(){}, "Error saving JAR file to " + target.getFullPath(), th);
      }
      bytes = -1;
    }
    finally
    {
      if (connection != null) connection.disconnect();
    }
    return bytes;
  }

  private String getLatestVersion(String xml)
  {
    if (StringUtil.isBlank(xml)) return null;
    String tag = "<str name=\"latestVersion\">";
    int start = xml.indexOf(tag);
    if (start < 0) return null;
    start += tag.length();
    int end = xml.indexOf("</str>", start);
    if (end < 0) return null;
    return xml.substring(start, end);
  }

  public MavenArtefact searchByClassName(String className)
  {
    if (StringUtil.isBlank(className)) return null;

    return knownArtefacts.
             stream().
             filter(a -> className.equals(a.getDriverClassName())).
             findFirst().orElse(null);
  }

  private List<MavenArtefact> getKnownArtefacts()
  {
    List<MavenArtefact> result = new ArrayList<>();
    try (InputStream in = this.getClass().getResourceAsStream("/workbench/db/MavenDrivers.xml");)
    {
      XMLDecoder d = new XMLDecoder(in);
      Object o = d.readObject();
      if (o instanceof List)
      {
        result.addAll((List)o);
      }
    }
    catch (Throwable ex)
    {
      LogMgr.logError(new CallerInfo(){}, "Could not load Maven definitions", ex);
    }
    return result;
  }
}
