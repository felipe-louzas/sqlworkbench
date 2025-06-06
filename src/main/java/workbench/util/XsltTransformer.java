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
package workbench.util;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.URIResolver;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;

/**
 *  Xslt transformer using the JDK built-in XSLT support
 *
 * @author Thomas Kellerer
 */
public class XsltTransformer
  implements URIResolver, ErrorListener
{
  private Exception nestedError;
  private File xsltBasedir;
  private File xsltUsed;

  private final MessageBuffer messages = new MessageBuffer();
  private final List<String> ignoredMessages = new ArrayList<>();

  /**
   * The directory where the initially defined XSLT is stored.
   * Will be set by transform() in order to be able to
   * resolve includes or imports from the same directory.
   */
  private File sourceDir;

  public XsltTransformer()
  {
    ignoredMessages.add("'https://javax.xml.XMLConstants/property/accessExternalDTD'");
    ignoredMessages.add("'https://javax.xml.XMLConstants/feature/secure-processing'");
    ignoredMessages.add("'https://www.oracle.com/xml/jaxp/properties/entityExpansionLimit'");
  }

  /**
   * Define a directory to be searched for when looking for an xstl file
   * @param dir
   */
  public void setXsltBaseDir(File dir)
  {
    this.xsltBasedir = dir;
  }

  public void transform(String inputFileName, String outputFileName, String xslFileName)
    throws IOException, TransformerException
  {
    transform(inputFileName, outputFileName, xslFileName, null);
  }

  public File getXsltUsed()
  {
    return xsltUsed;
  }

  public void transform(String inputFileName, String outputFileName, String xslFileName, Map<String, String> parameters)
    throws IOException, TransformerException
  {
    File inputFile = new File(inputFileName);
    File outputFile = new File(outputFileName);
    File xslFile = findStylesheet(xslFileName);
    transform(inputFile, outputFile, xslFile, parameters);
  }

  public void transform(File inputFile, File outputFile, File xslfile, Map<String, String> parameters)
    throws IOException, TransformerException
  {
    if (!xslfile.exists())
    {
      throw new FileNotFoundException("File " + xslfile.getAbsolutePath() + " doesn't exist");
    }

    xsltUsed = xslfile;

    InputStream in = null;
    OutputStream out = null;
    InputStream xlsInput = null;
    Transformer transformer = null;

    try
    {
      xlsInput = new FileInputStream(xslfile);
      sourceDir = xslfile.getAbsoluteFile().getParentFile();
      Source sxslt = new StreamSource(xlsInput);
      sxslt.setSystemId(xslfile.getName());
      TransformerFactory factory = TransformerFactory.newInstance();
      factory.setURIResolver(this);

      transformer = factory.newTransformer(sxslt);
      transformer.setErrorListener(this);
      transformer.setURIResolver(this);

      if (parameters != null)
      {
        for (Map.Entry<String, String> entry : parameters.entrySet())
        {
          String value = StringUtil.decodeUnicode(entry.getValue());
          transformer.setParameter(entry.getKey(), value);
        }
      }
      File outDir = outputFile.getAbsoluteFile().getParentFile();
      transformer.setParameter("wb-basedir", outDir.getAbsolutePath());

      File scriptDir = inputFile.getAbsoluteFile().getParentFile();
      transformer.setParameter("wb-scriptdir", scriptDir.getAbsolutePath());

      in = new BufferedInputStream(new FileInputStream(inputFile),32*1024);
      out = new BufferedOutputStream(new FileOutputStream(outputFile), 32*1024);
      Source xmlSource = new StreamSource(in);
      StreamResult result = new StreamResult(out);
      transformer.transform(xmlSource, result);
    }
    finally
    {
      FileUtil.closeQuietely(xlsInput, in, out);
    }
  }

  @Override
  public void warning(TransformerException exception)
    throws TransformerException
  {
    appendExceptionMessage(exception);
  }

  @Override
  public void error(TransformerException exception)
    throws TransformerException
  {
    LogMgr.logError(new CallerInfo(){}, "Error during XSLT processing: " + exception.getMessage(), null);
    appendExceptionMessage(exception);
  }

  @Override
  public void fatalError(TransformerException exception)
    throws TransformerException
  {
    LogMgr.logError(new CallerInfo(){}, "FatalError during XSLT processing", exception);
    appendExceptionMessage(exception);
  }

  private void appendExceptionMessage(Exception ex)
  {
    if (messages.getLength() > 0)
    {
      messages.appendNewLine();
    }
    messages.append(ex.getMessage());
  }

  public Exception getNestedError()
  {
    return nestedError;
  }

  public String getAllOutputs()
  {
    return getAllOutputs(null);
  }

  public String getAllOutputs(Exception e)
  {
    StringBuilder result = new StringBuilder();
    if (messages.getLength() > 0)
    {
      result.append(messages.getBuffer());
    }

    if (nestedError != null)
    {
      result.append('\n');
      if (e != null)
      {
        result.append(e.getMessage());
        result.append(": ");
      }
      result.append(ExceptionUtil.getDisplay(nestedError));
    }

    if (result.length() == 0 && e != null)
    {
      result.append(ResourceMgr.getFormattedString("ErrXsltProcessing", xsltUsed.getAbsolutePath(), ExceptionUtil.getDisplay(e)));
    }
    return result.toString();
  }

  @Override
  public Source resolve(String href, String base)
    throws TransformerException
  {
    File referenced = new File(base);

    try
    {
      if (referenced.exists())
      {
        return new StreamSource(new FileInputStream(referenced));
      }
      File toUse = new File(sourceDir, href);
      if (toUse.exists())
      {
        return new StreamSource(new FileInputStream(toUse));
      }
      toUse = findStylesheet(href);
      return new StreamSource(new FileInputStream(toUse));
    }
    catch (FileNotFoundException e)
    {
      nestedError = e;
      throw new TransformerException(e);
    }
  }

  /**
   * Searches for a stylesheet. <br/>
   * If the filename is an absolute filename, no searching takes place.
   * <br/>
   * If the filename does not include a directory then the directory where the main Stylesheet is located
   * is checked first (in case that is an absolute file)
   * of the installation directory is checked first. <br/>
   * Then the supplied base directory is checked, if nothing is found
   * the confid directory is checked and finally the installation directory.
   * <br/>
   * If the supplied filename does not have the .xslt extension, it is added
   * before searching for the file.
   */
  public File findStylesheet(String fname)
  {
    WbFile f = new WbFile(fname);
    if (StringUtil.isEmpty(f.getExtension()))
    {
      fname += ".xslt";
      f = new WbFile(fname);
    }

    if (f.exists()) return f;

    if (f.getParentFile() == null)
    {
      // findStylesheet is also used to resolve the path to the main file
      // so it might not yet be initialized
      if (xsltUsed != null && xsltUsed.getParentFile() != null && xsltUsed.exists())
      {
        File mainDir = xsltUsed.getParentFile();
        File toTest = new File(mainDir, fname);
        if (toTest.exists()) return toTest;
      }

      // This is the default directory layout in the distribution archive
      File xsltdir = Settings.getInstance().getDefaultXsltDirectory();

      File toTest = new File(xsltdir, fname);
      if (toTest.exists()) return toTest;
    }

    if (this.xsltBasedir != null)
    {
      File totest = new File(xsltBasedir, fname);
      if (totest.exists()) return totest;
    }

    File configdir = Settings.getInstance().getConfigDir();
    File totest = new File(configdir, fname);
    if (totest.exists()) return totest;
    return new File(fname);
  }

}
