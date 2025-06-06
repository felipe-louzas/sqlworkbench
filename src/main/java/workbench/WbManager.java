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
package workbench;

import java.awt.EventQueue;
import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.FocusManager;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.UIManager;

import workbench.console.SQLConsole;
import workbench.interfaces.FontChangedListener;
import workbench.interfaces.ToolWindow;
import workbench.interfaces.ToolWindowManager;
import workbench.log.CallerInfo;
import workbench.log.LogMgr;
import workbench.resource.GuiSettings;
import workbench.resource.ResourceMgr;
import workbench.resource.Settings;
import workbench.ssh.SshConfigMgr;

import workbench.db.ConnectionMgr;
import workbench.db.ConnectionProfile;

import workbench.gui.MainWindow;
import workbench.gui.WbKeyDispatcher;
import workbench.gui.WbSwingUtilities;
import workbench.gui.YesNoCancel;
import workbench.gui.bookmarks.BookmarkManager;
import workbench.gui.components.ColumnOrderMgr;
import workbench.gui.components.FeedbackWindow;
import workbench.gui.dbobjects.DbExplorerWindow;
import workbench.gui.filter.FilterDefinitionManager;
import workbench.gui.lnf.LnFHelper;
import workbench.gui.profiles.ProfileKey;
import workbench.gui.tools.DataPumper;
import workbench.gui.tools.ObjectSourceSearchPanel;

import workbench.sql.BatchRunner;
import workbench.sql.CommandRegistry;
import workbench.sql.VariablePool;
import workbench.sql.macros.MacroManager;
import workbench.sql.wbcommands.InvalidConnectionDescriptor;

import workbench.util.ClasspathCheck;
import workbench.util.CollectionUtil;
import workbench.util.DeadlockMonitor;
import workbench.util.FileUtil;
import workbench.util.FileWatcherFactory;
import workbench.util.MacOSHelper;
import workbench.util.MemoryWatcher;
import workbench.util.StringUtil;
import workbench.util.UpdateCheck;
import workbench.util.VersionNumber;
import workbench.util.WbFile;
import workbench.util.WbThread;

/**
 * The main application "controller" for SQL Workbench/J.
 *
 * @author Thomas Kellerer
 */
public final class WbManager
  implements FontChangedListener, Runnable, Thread.UncaughtExceptionHandler, ToolWindowManager
{
  private static WbManager wb;
  private final List<MainWindow> mainWindows = Collections.synchronizedList(new ArrayList<>(5));
  private final List<ToolWindow> toolWindows = Collections.synchronizedList(new ArrayList<>(5));

  private RunMode runMode;
  private boolean inShutdown = false;
  private boolean writeSettings = true;
  private boolean overWriteGlobalSettingsFile = true;
  private boolean outOfMemoryOcurred;
  private WbThread shutdownHook;
  private DeadlockMonitor deadlockMonitor;

  private final AppArguments cmdLine = new AppArguments();
  private JDialog closeMessage;

  private WbManager()
  {
    Thread.setDefaultUncaughtExceptionHandler(this);
  }

  public static WbManager getInstance()
  {
    return wb;
  }

  @Override
  public void uncaughtException(Thread thread, Throwable error)
  {
    error.printStackTrace();
    LogMgr.logError(new CallerInfo(){}, "Thread '" + thread.getName() + "' caused an exception!", error);
  }

  public AppArguments getCommandLine()
  {
    return cmdLine;
  }

  public boolean getSettingsShouldBeSaved()
  {
    return this.writeSettings;
  }

  public void setOutOfMemoryOcurred()
  {
    this.outOfMemoryOcurred = true;
  }

  public boolean outOfMemoryOcurred()
  {
    return this.outOfMemoryOcurred;
  }

  public void showOutOfMemoryError()
  {
    outOfMemoryOcurred = true;
    showLowMemoryError();
  }

  public void showLowMemoryError()
  {
    WbSwingUtilities.showErrorMessageKey(getCurrentWindow(), "MsgLowMemoryError");
  }

  public boolean isDevBuild()
  {
    VersionNumber buildNumber = ResourceMgr.getBuildNumber();
    return buildNumber.getMajorVersion() == 999 || buildNumber.getMinorVersion() != -1;
  }

  public JFrame getCurrentWindow()
  {
    if (this.mainWindows == null) return getCurrentToolWindow();

    if (this.mainWindows.size() == 1)
    {
      return this.mainWindows.get(0);
    }

    synchronized (mainWindows)
    {
      for (MainWindow w : mainWindows)
      {
        if (w == null) continue;
        if (w.hasFocus() || w.isActive()) return w;
      }
    }
    return null;
  }

  private JFrame getCurrentToolWindow()
  {
    if (this.toolWindows == null) return null;
    if (this.toolWindows.size() == 1)
    {
      ToolWindow w = toolWindows.get(0);
      if (w != null) return w.getWindow();
    }

    for (ToolWindow t : toolWindows)
    {
      if (t != null)
      {
        JFrame f = t.getWindow();
        if (f.hasFocus()) return f;
      }
    }

    return null;
  }

  @Override
  public void registerToolWindow(ToolWindow aWindow)
  {
    toolWindows.add(aWindow);
  }

  @Override
  public void unregisterToolWindow(ToolWindow toolWindow)
  {
    if (toolWindow == null) return;
    toolWindows.remove(toolWindow);

    if (this.toolWindows.isEmpty() && this.mainWindows.isEmpty())
    {
      this.exitWorkbench(toolWindow.getWindow(), false);
    }
  }

  private void closeToolWindows()
  {
    synchronized (toolWindows)
    {
      for (ToolWindow w : toolWindows)
      {
        w.closeWindow();
      }
      toolWindows.clear();
    }
  }

  @Override
  public void fontChanged(String aFontKey, Font newFont)
  {
    if (aFontKey.equals(Settings.PROPERTY_DATA_FONT))
    {
      UIManager.put("Table.font", newFont);
      UIManager.put("TableHeader.font", newFont);
    }
  }

  private void initUI()
  {
    LnFHelper helper = new LnFHelper();
    helper.initUI();
    Settings.getInstance().addFontChangedListener(this);
    if (GuiSettings.installFocusManager())
    {
      EventQueue.invokeLater(() ->
      {
        FocusManager.getCurrentManager().addKeyEventDispatcher(WbKeyDispatcher.getInstance());
      });
    }
  }

  /**
   * Saves the preferences of all open MainWindows.
   *
   * @return true if the preferences were saved successfully
   *         false if at least on MainWindow "refused" to close
   */
  private boolean storeWindowSettings()
  {
    // no settings should be saved, pretend everything was done.
    if (!this.writeSettings) return true;

    boolean settingsSaved = false;

    if (!this.checkProfiles(getCurrentWindow())) return false;

    boolean result = true;
    synchronized (mainWindows)
    {
      for (MainWindow win : mainWindows)
      {
        if (win == null) continue;

        if (!settingsSaved && win.hasFocus())
        {
          win.saveSettings();
          settingsSaved = true;
        }

        if (win.isBusy())
        {
          if (!this.checkAbort(win)) return false;
        }
        result = win.saveWorkspace(true);
        if (!result) return false;
      }

      // No window with focus found, saveAs the size and position of the last opened window
      if (!settingsSaved && mainWindows.size() > 0)
      {
        mainWindows.get(mainWindows.size() - 1).saveSettings();
      }
    }
    return result;
  }

  public RunMode getRunMode()
  {
    assert runMode != null;
    return runMode;
  }

  public boolean isGUIMode()
  {
    assert runMode != null;
    return runMode == RunMode.GUI;
  }

  public boolean isConsoleMode()
  {
    assert runMode != null;
    return runMode == RunMode.Console;
  }

  public boolean isBatchMode()
  {
    assert runMode != null;
    return runMode == RunMode.Batch;
  }

  public boolean canExit()
  {
    if (this.storeWindowSettings())
    {
      if (Settings.getInstance().wasExternallyModified())
      {
        String msg = ResourceMgr.getFormattedString("MsgSettingsChanged", Settings.getInstance().getConfigFile().getFullPath());
        YesNoCancel choice = WbSwingUtilities.getYesNoCancel(getCurrentWindow(), msg);
        this.overWriteGlobalSettingsFile = (choice == YesNoCancel.yes);
        return choice != YesNoCancel.cancel;
      }
      return true;
    }
    else
    {
      LogMgr.logDebug(new CallerInfo(){}, "saveWindowSettings() returned false!");
      return false;
    }
  }

  public void exitWorkbench(boolean forceAbort)
  {
    JFrame w = this.getCurrentWindow();
    this.exitWorkbench(w, forceAbort);
  }

  public void exitWorkbench(final JFrame window, final boolean forceAbort)
  {
    // canExit() will also prompt if any modified files should be changed
    if (!canExit())
    {
      return;
    }

    inShutdown = true;
    FileWatcherFactory.getInstance().stopAllWatchers();

    if (window == null)
    {
      ConnectionMgr.getInstance().disconnectAll();
      this.doShutdown(0);
      return;
    }

    WbSwingUtilities.showWaitCursor(window);

    // When disconnecting it can happen that the disconnect itself
    // takes some time. Because of this, a small window is displayed
    // that the disconnect takes place, and the actual disconnect is
    // carried out in a different thread to not block the AWT thread.
    // If it takes too long the user can still abort the JVM ...
    WbSwingUtilities.invoke(() ->
    {
      createCloseMessageWindow(window);
      if (closeMessage != null) closeMessage.setVisible(true);
    });

    MacroManager.getInstance().save();
    Thread t = new WbThread("WbManager disconnect")
    {
      @Override
      public void run()
      {
        disconnectWindows(forceAbort);
        ConnectionMgr.getInstance().disconnectAll();
        disconnected();
      }
    };
    t.start();
  }

  private void createCloseMessageWindow(JFrame parent)
  {
    if (parent == null) return;
    ActionListener abort = (ActionEvent evt) ->
    {
      doShutdown(0);
    };

    this.closeMessage = new FeedbackWindow(parent, ResourceMgr.getString("MsgClosingConnections"), abort, "MsgAbortImmediately");
    WbSwingUtilities.center(this.closeMessage, parent);
  }

  private void disconnectWindows(boolean forceAbort)
  {
    ArrayList<MainWindow> win = new ArrayList<>(mainWindows);
    for (MainWindow w : win)
    {
      if (w == null) continue;
      if (forceAbort)
      {
        w.forceDisconnect();
      }
      else
      {
        w.abortAll();
        w.disconnect(false, true, false, false);
      }
    }
  }

  /*
   *	This gets called from exitWorkbench() when disconnecting everything
   */
  private void disconnected()
  {
    WbSwingUtilities.invoke(() ->
    {
      if (closeMessage != null)
      {
        closeMessage.setVisible(false);
        closeMessage.dispose();
        closeMessage = null;
      }
    });
    doShutdown(0);
  }

  private void closeAllWindows()
  {
    if (!this.isGUIMode()) return;

    LogMgr.logDebug(new CallerInfo(){}, "Closing all open windows");
    synchronized (mainWindows)
    {
      for (MainWindow w : mainWindows)
      {
        if (w != null)
        {
          try { w.setVisible(false); } catch (Throwable th) {}
          try { w.dispose(); } catch (Throwable th) {}
        }
      }
      mainWindows.clear();
    }
    closeToolWindows();
  }

  public void saveConfigSettings()
  {
    if (this.writeSettings && !this.isBatchMode())
    {
      if (overWriteGlobalSettingsFile)
      {
        Settings.getInstance().saveSettings(outOfMemoryOcurred);
      }
      else
      {
        LogMgr.logInfo(new CallerInfo(){}, "Not overwritting global settings!");
      }

      SshConfigMgr.getDefaultInstance().saveGlobalConfig();
      FilterDefinitionManager.getDefaultInstance().saveSettings(Settings.getInstance());
      try
      {
        ColumnOrderMgr.getInstance().saveSettings();
      }
      catch (Exception e)
      {
        LogMgr.logError(new CallerInfo(){}, "Could not write column order storage", e);
      }
    }
    else
    {
      LogMgr.logDebug(new CallerInfo(){}, "Settings not saved. writeSettings=" + writeSettings + ", runMode=" + runMode);
    }
  }

  private void installShutdownHook()
  {
    shutdownHook = new WbThread(this, "ShutdownHook");
    Runtime.getRuntime().addShutdownHook(shutdownHook);
  }

  public void removeShutdownHook()
  {
    if (this.shutdownHook != null)
    {
      try
      {
        Runtime.getRuntime().removeShutdownHook(this.shutdownHook);
      }
      catch (Throwable ex)
      {
        // ignore, we can't do anything about it anyway
      }
      this.shutdownHook = null;
    }
    if (this.deadlockMonitor != null)
    {
      this.deadlockMonitor.cancel();
    }
  }

  public void doShutdown(int errorCode)
  {
    removeShutdownHook();
    closeAllWindows();
    saveConfigSettings();
    LogMgr.logInfo(new CallerInfo(){}, "Stopping " + ResourceMgr.TXT_PRODUCT_NAME + ", Build " + ResourceMgr.getString("TxtBuildNumber"));
    LogMgr.shutdown();
    inShutdown = false;
    // The property workbench.system.doexit can be used to embedd the sqlworkbench.jar
    // in other applications and still be able to call doShutdown()
    if (shouldDoSystemExit()) System.exit(errorCode);
  }

  public static boolean shouldDoSystemExit()
  {
    return "true".equals(System.getProperty("workbench.system.doexit", "true"));
  }

  private boolean checkAbort(MainWindow win)
  {
    return WbSwingUtilities.getYesNo(win, ResourceMgr.getString("MsgAbortRunningSql"));
  }

  private boolean checkProfiles(JFrame win)
  {
    if (ConnectionMgr.getInstance().profilesAreModified())
    {
      int answer = JOptionPane.showConfirmDialog(win, ResourceMgr.getString("MsgConfirmUnsavedProfiles"), ResourceMgr.TXT_PRODUCT_NAME, JOptionPane.YES_NO_CANCEL_OPTION);
      if (answer == JOptionPane.OK_OPTION)
      {
        ConnectionMgr.getInstance().saveProfiles();
        return true;
      }
      else
      {
        return answer == JOptionPane.NO_OPTION;
      }
    }
    return true;
  }

  /**
   * Called whenever a MainWindow is closed.
   *
   * @param win the window to close
   *
   * @see workbench.gui.MainWindow#windowClosing(java.awt.event.WindowEvent)
   * @see workbench.gui.MainWindow#connectCancelled()
   */
  public synchronized void closeMainWindow(final MainWindow win)
  {
    if (this.mainWindows.size() == 1)
    {
      // If only one window is present, shut down the application
      if (!this.inShutdown)
      {
        this.exitWorkbench(win, win.isBusy());
      }
      else
      {
        LogMgr.logWarning(new CallerInfo(){}, "Ignoring second attempt to shutdown the application", new Exception("Backtrace"));
      }
    }
    else if (win != null)
    {
      if (win.isBusy())
      {
        if (!checkAbort(win)) return;
      }

      if (!win.saveWorkspace()) return;

      this.mainWindows.remove(win);
      BookmarkManager.getInstance().clearBookmarksForWindow(win.getWindowId());

      WbThread t = new WbThread(win.getWindowId() + " Disconnect")
      {
        @Override
        public void run()
        {
          // First parameter tells the window to disconnect in the
          // current thread as we are already in a background thread
          // second parameter tells the window not to close the workspace
          // third parameter tells the window not to saveAs the workspace
          // this does not need to happen on the EDT
          win.disconnect(false, false, false, false);
          win.setVisible(false);
          win.dispose();
          ConnectionMgr.getInstance().dumpConnections();
        }
      };
      t.start();
    }
  }

  /**
   * Open a new main window, but do not check any command line parameters.
   *
   * This method will be called from the GUI
   * when the user requests a new window
   *
   * @see workbench.gui.actions.FileNewWindowAction
   */
  public void openNewWindow()
  {
    WbSwingUtilities.invoke(() ->
    {
      openNewWindow(false);
    });
  }

  private void openNewWindow(boolean checkCmdLine)
  {
    GraphicsConfiguration screenToUse = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();

    if (checkCmdLine)
    {
      // checkCmdLine will be true for the first window that is opened
      // check the screen on which to open the window only then
      String key = MainWindow.class.getName() + ".screen";
      String lastScreen = Settings.getInstance().getProperty(key, null);
      if (lastScreen != null)
      {
        GraphicsDevice[] screens = GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices();
        for (GraphicsDevice screen : screens)
        {
          if (lastScreen.equals(screen.getIDstring()))
          {
            screenToUse = screen.getDefaultConfiguration();
            break;
          }
        }
      }
    }

    LogMgr.logDebug(new CallerInfo(){},
        "Using screen: " + screenToUse.getDevice().getIDstring() + " " +
          WbSwingUtilities.displayString(screenToUse.getBounds()));

    final MainWindow main = new MainWindow(screenToUse);
    mainWindows.add(main);
    main.display(screenToUse);

    ClasspathCheck check = new ClasspathCheck();
    check.checAll();

    StartupMessages.getInstance().showMessages();

    boolean connected = false;

    if (checkCmdLine)
    {
      // get profile name from commandline
      String profilename = cmdLine.getValue(AppArguments.ARG_PROFILE);
      String group = cmdLine.getValue(AppArguments.ARG_PROFILE_GROUP);
      ConnectionProfile prof;
      if (StringUtil.isNotBlank(profilename))
      {
        ProfileKey def = new ProfileKey(profilename, group);
        prof = ConnectionMgr.getInstance().getProfile(def);
      }
      else
      {
        try
        {
          prof = BatchRunner.createCmdLineProfile(this.cmdLine);
        }
        catch (InvalidConnectionDescriptor icd)
        {
          LogMgr.logError(new CallerInfo(){}, "Invalid connection descriptor specified", icd);
          prof = null;
        }
      }

      if (prof != null)
      {
        LogMgr.logDebug(new CallerInfo(){}, "Connecting to " + prof.getName());
        // try to connect to the profile passed on the
        // command line. If this fails the connection
        // dialog will be show to the user
        main.connectTo(prof, true, true);

        // the main window will take care of displaying the connection dialog
        // if the connection to the requested profile fails.
        connected = true;
      }
    }

    boolean autoSelect = Settings.getInstance().getShowConnectDialogOnStartup();
    final boolean exitOnCancel = Settings.getInstance().getExitOnFirstConnectCancel();

    // no connection? then display the connection dialog
    if (!connected && autoSelect)
    {
      // Should be done later, so that the main window
      // has enough time to initialize
      EventQueue.invokeLater(() ->
      {
        main.selectConnection(exitOnCancel);
      });
    }
  }

  public void readParameters(String[] args, RunMode mode)
  {
    CallerInfo callerInfo = new CallerInfo(){};

    try
    {
      cmdLine.parse(args);

      String lang = cmdLine.getValue(AppArguments.ARG_LANG);
      if (StringUtil.isNotEmpty(lang))
      {
        System.setProperty("workbench.gui.language", lang);
      }

      if (cmdLine.isArgPresent(AppArguments.ARG_LOG_ALL_STMT))
      {
        boolean logAllStmts = cmdLine.getBoolean(AppArguments.ARG_LOG_ALL_STMT, false);
        System.setProperty(Settings.PROPERTY_LOG_ALL_SQL, Boolean.toString(logAllStmts));
      }

      String configDir = cmdLine.getValue(AppArguments.ARG_CONFIGDIR);
      if (StringUtil.isNotEmpty(configDir))
      {
        System.setProperty("workbench.configdir", configDir);
      }

      String libdir = cmdLine.getValue(AppArguments.ARG_LIBDIR);
      if (StringUtil.isNotEmpty(libdir))
      {
        System.setProperty(Settings.PROP_LIBDIR, libdir);
      }

      String logfile = cmdLine.getValue(AppArguments.ARG_LOGFILE);
      if (StringUtil.isNotEmpty(logfile))
      {
        WbFile file = new WbFile(logfile);
        System.setProperty("workbench.log.filename", file.getFullPath());
      }

      String logLevel = cmdLine.getValue(AppArguments.ARG_LOGLEVEL);
      if (StringUtil.isNotEmpty(logLevel))
      {
        System.setProperty("workbench.log.level", logLevel);
      }

      if (cmdLine.isArgPresent(AppArguments.ARG_NOSETTNGS))
      {
        this.writeSettings = false;
      }

      List<String> list = cmdLine.getList(AppArguments.ARG_PROP);
      for (String propDef : list)
      {
        String[] elements = propDef.split("=");
        if (elements.length == 2)
        {
          System.setProperty(elements[0], elements[1]);
        }
      }

      // Make sure the Settings object is (re)initialized properly now that
      // some system properties have been read from the commandline
      // this is especially necessary during JUnit tests to make
      // sure a newly passed commandline overrules the previously initialized
      // Settings instance
      Settings.getInstance().initialize();

      String scriptname = cmdLine.getValue(AppArguments.ARG_SCRIPT);
      String cmd = cmdLine.getValue(AppArguments.ARG_COMMAND);

      if (StringUtil.isEmpty(cmd) && cmdLine.isArgPresent(AppArguments.ARG_COMMAND))
      {
        cmd = FileUtil.getSystemIn();
        cmdLine.setCommandString(cmd);
      }

      boolean readDriverTemplates = true;
      boolean showHelp = cmdLine.isArgPresent("help");
      boolean hasScript = StringUtil.isNotBlank(scriptname) || StringUtil.isNotBlank(cmd) ;

      if (mode == null)
      {
        if (hasScript || showHelp)
        {
          this.runMode = RunMode.Batch;
        }
        else
        {
          this.runMode = RunMode.GUI;
        }
      }
      else
      {
        this.runMode = mode;
      }

      if (BatchRunner.hasConnectionArgument(cmdLine) || runMode != RunMode.GUI)
      {
        // Do not read the driver templates in batchmode
        readDriverTemplates = false;
      }

      readVariablesFromCommandline();

      List<String> profileNames = cmdLine.getListValue(AppArguments.ARG_PROFILE_STORAGE);
      List<WbFile> storageFiles = new ArrayList<>(profileNames.size());
      for (String name : profileNames)
      {
        File baseDir = Settings.getInstance().getConfigDir();
        if (StringUtil.isNotEmpty(name))
        {
          name = StringUtil.replaceProperties(name);

          // If a file was specified without a directory
          // then the config directory should be used.
          WbFile prof = new WbFile(name);
          if (prof.getParentFile() == null)
          {
            // A filename without directory was specified
            // In this case this should default to the config directory
            prof = new WbFile(baseDir, name);
          }
          // do not check the file existence. The ProfileManager will log any non-existing file
          storageFiles.add(prof);
        }
      }
      Settings.getInstance().setProfileStorage(storageFiles);

      if (cmdLine.isArgPresent(AppArguments.ARG_NOTEMPLATES))
      {
        readDriverTemplates = false;
      }

      if (!readDriverTemplates)
      {
        // temporarily set the property to disable template loading
        // Settings.setProperty() would persist this flag in workbench.settings
        // every workbench property can be overwritten through a system property
        System.setProperty(Settings.PROP_READ_DRIVER_TEMPLATES, "false");
      }

      String macros = cmdLine.getValue(AppArguments.ARG_MACRO_STORAGE);
      if (StringUtil.isNotEmpty(macros))
      {
        WbFile prof = new WbFile(macros);
        if (prof.exists())
        {
          macros = prof.getFullPath();
        }
      }
      Settings.getInstance().setMacroStorage(macros);

      LogMgr.logInfo(callerInfo, "Starting " + ResourceMgr.TXT_PRODUCT_NAME + ", " + ResourceMgr.getBuildInfo() + " in " + runMode + " mode");
      LogMgr.logInfo(callerInfo, ResourceMgr.getFullJavaInfo());
      LogMgr.logInfo(callerInfo, ResourceMgr.getOSInfo());

      long maxMem = MemoryWatcher.MAX_MEMORY / (1024*1024);
      LogMgr.logInfo(callerInfo, "Available memory: " + maxMem + "MB");
      LogMgr.logInfo(callerInfo, "Classpath: " + WbFile.getPathForLogging(System.getProperty("java.class.path")));

      if (cmdLine.isArgPresent(AppArguments.ARG_NOSETTNGS))
      {
        LogMgr.logInfo(callerInfo, "The '" + AppArguments.ARG_NOSETTNGS + "' option was specified on the commandline. Global settings will not be saved.");
      }

      if (Settings.getInstance().getBoolProperty("workbench.batch.log.arguments", true))
      {
        String fullArgs = StringUtil.arrayToString(args, ' ');
        LogMgr.logDebug(callerInfo, "Arguments provided: " + fullArgs);
      }
    }
    catch (Exception e)
    {
      LogMgr.logError(callerInfo, "Error initializing command line arguments!", e);
    }
  }

  private void readVariablesFromCommandline()
  {
    if (cmdLine.isArgPresent(AppArguments.ARG_VARDEF))
    {
      String msg = "Using -" + AppArguments.ARG_VARDEF + " is deprecated. Please use -" + AppArguments.ARG_VARIABLE + " or -" + AppArguments.ARG_VAR_FILE + " instead";
      LogMgr.logWarning(new CallerInfo(){}, msg);
    }

    boolean globalVars = Settings.getInstance().getCommandLineVarsAreGlobal();
    List<String> vars = cmdLine.getList(AppArguments.ARG_VARDEF);
    for (String var : vars)
    {
      try
      {
        VariablePool.getInstance().readDefinition(StringUtil.trimQuotes(var), globalVars);
      }
      catch (Exception e)
      {
        LogMgr.logError(new CallerInfo(){}, "Error reading variable definition from file: " + var, e);
      }
    }

    List<String> files = cmdLine.getListValue(AppArguments.ARG_VAR_FILE);
    if (CollectionUtil.isNonEmpty(files))
    {
      for (String file : files)
      {
        try
        {
          VariablePool.getInstance().readFromFile(StringUtil.trimQuotes(file), null, true);
        }
        catch (Exception e)
        {
          LogMgr.logError(new CallerInfo(){}, "Error reading variable definition from file: " + WbFile.getPathForLogging(file), e);
        }
      }
    }

    vars = cmdLine.getList(AppArguments.ARG_VARIABLE);
    for (String var : vars)
    {
      try
      {
        VariablePool.getInstance().parseSingleDefinition(var, globalVars);
      }
      catch (Exception e)
      {
        LogMgr.logError(new CallerInfo(){}, "Error parsing variable definition: " + var, e);
      }
    }
  }

  public void startApplication()
  {
    // batchMode flag is set by readParameters()
    if (isBatchMode())
    {
      CommandRegistry.getInstance().scanForExtensions();
      runBatch();
    }
    else
    {
      initRegistry();

      boolean doWarmup = Settings.getInstance().getBoolProperty("workbench.gui.warmup", false);

      // if the connection dialog is not shown, pre-load the profiles
      doWarmup = doWarmup || (Settings.getInstance().getShowConnectDialogOnStartup() == false);

      if (doWarmup)
      {
        warmUp();
      }

      // This will install the application listener if running under MacOS
      MacOSHelper m = new MacOSHelper();
      m.installApplicationHandler();

      // make sure runGui() is called on the AWT Thread
      EventQueue.invokeLater(this::runGui);
    }
  }

  private void initRegistry()
  {
    WbThread t1 = new WbThread("ExtensionScannerThread")
    {
      @Override
      public void run()
      {
        CommandRegistry registry = CommandRegistry.getInstance();
        registry.scanForExtensions();
      }
    };
    t1.start();
  }

  private void warmUp()
  {
    WbThread t1 = new WbThread("BackgroundProfilesLoader")
    {
      @Override
      public void run()
      {
        ConnectionMgr.getInstance().getProfiles();
      }
    };
    t1.start();

    WbThread t2 = new WbThread("BackgroundMacrosLoader")
    {
      @Override
      public void run()
      {
        MacroManager.getInstance(); // get instance will trigger loading the default macros
      }
    };
    t2.start();
  }

  public void runGui()
  {
    initUI();

    boolean pumper = cmdLine.isArgPresent(AppArguments.ARG_SHOW_PUMPER);
    boolean explorer = cmdLine.isArgPresent(AppArguments.ARG_SHOW_DBEXP);
    boolean searcher = cmdLine.isArgPresent(AppArguments.ARG_SHOW_SEARCHER);
    String extension = cmdLine.getValue(AppArguments.ARG_EXTENSION);

    if (pumper)
    {
      new DataPumper().showWindow();
    }
    else if (explorer)
    {
      DbExplorerWindow.showWindow();
    }
    else if (searcher)
    {
      new ObjectSourceSearchPanel().showWindow();
    }
    else if (extension != null)
    {
      CommandRegistry registry = CommandRegistry.getInstance();
      registry.scanForGuiExtensions();
      ToolWindow gui = registry.getGuiExtension(extension);
      if (gui != null)
      {
        gui.getWindow();
      }
      else
      {
        LogMgr.logWarning(new CallerInfo(){}, "could not find extension " + extension);
        openNewWindow(true);
      }
    }
    else
    {
      openNewWindow(true);
    }

    if (Settings.getInstance().getBoolProperty("workbench.gui.debug.deadlockmonitor.enabled", false))
    {
      LogMgr.logInfo(new CallerInfo(){}, "Starting DeadlockMonitor");
      deadlockMonitor = new DeadlockMonitor();
      deadlockMonitor.start();
    }

    UpdateCheck upd = new UpdateCheck();
    upd.startUpdateCheck();
  }

  // Package visible for testing purposes
  int exitCode = 0;

  private void runBatch()
  {
    exitCode = 0;

    boolean saveCaches = Settings.getInstance().getBoolProperty("workbench.batch.objectcache.save", false);
    BatchRunner runner = BatchRunner.createBatchRunner(cmdLine);

    if (runner != null)
    {
      try
      {
        runner.connect();
      }
      catch (Exception e)
      {
        exitCode = 1;
        // no need to log connect errors, already done by BatchRunner and ConnectionMgr
        // runner.isSuccess() will also be false for the next step
      }

      runner.setTraceOutput(System.out::println);

      try
      {
        // Do not check for runner.isConnected() as in batch mode
        // the application might be started without a profile
        // (e.g. for a single WbCopy command)
        if (runner.isSuccess())
        {
          runner.execute();
          // Not all exceptions will be re-thrown by the batch runner
          // in order to be able to run the error script, so it is important
          // to check isSuccess() in order to return the correct status
          if (!runner.isSuccess()) exitCode = 2;
        }
      }
      catch (OutOfMemoryError e)
      {
        LogMgr.logError(new CallerInfo(){}, "Not enough memory to finish the operation. Aborting execution!", null);
        System.err.println("Not enough memory to finish the operation. Aborting execution!");
        exitCode = 10;
      }
      catch (Exception e)
      {
        exitCode = 2;
      }
      finally
      {
        ConnectionMgr mgr = ConnectionMgr.getInstance();
        if (mgr != null) mgr.disconnectAll(saveCaches);
      }
    }
    else
    {
      exitCode = 3;
    }
    this.doShutdown(exitCode);
  }

  public static void initConsoleMode()
  {
    System.setProperty("workbench.log.console", "false");
    wb = new WbManager();
    wb.cmdLine.removeArgument(AppArguments.ARG_SHOW_PUMPER);
    wb.cmdLine.removeArgument(AppArguments.ARG_SHOW_DBEXP);
    wb.cmdLine.removeArgument(AppArguments.ARG_SHOW_SEARCHER);
    wb.cmdLine.removeArgument(AppArguments.ARG_CONN_SEPARATE);
    wb.cmdLine.removeArgument(AppArguments.ARG_WORKSPACE);
    wb.runMode = RunMode.Console;
    wb.writeSettings = false; // SQLConsole will save the settings explicitely
  }

  /**
   * Prepare the Workbench "environment" to be used inside another
   * application (e.g. for Unit testing)
   */
  public static void prepareForEmbedded()
  {
    runEmbedded(null, false);
  }

  /**
   * Run SQL Workbench in embedded mode supplying all parameters.
   *
   * @param args
   */
  public static void runEmbedded(String[] args)
  {
    runEmbedded(args, true);
  }

  private static void runEmbedded(String[] args, boolean doStart)
  {
    wb = new WbManager();
    String[] realArgs = null;
    String embeddedArgs = "-notemplates -nosettings";
    if (args == null)
    {
      realArgs = new String[] { embeddedArgs };
    }
    else
    {
      realArgs = new String[args.length + 1];
      System.arraycopy(args, 0, realArgs, 0, args.length);
      realArgs[args.length] = embeddedArgs;
    }
    System.setProperty("workbench.system.doexit", "false");
    System.setProperty(Settings.TEST_MODE_PROPERTY, "true");
    wb.readParameters(realArgs, null);
    if (doStart)
    {
      wb.startApplication();
    }
  }

  public static boolean isTest()
  {
    return "true".equals(System.getProperty(Settings.TEST_MODE_PROPERTY, "false"));
  }

  public static void prepareForTest(String[] args)
  {
    wb = new WbManager();

    // The test mode is used by DbDriver to skip the test if a driver library
    // is accessible because in test mode the drivers are not loaded
    // through our own class loader as they are already present
    // on the classpath.
    // It is also used by Settings.initLogging() to allow a second
    // initialization of the LogMgr
    System.setProperty(Settings.TEST_MODE_PROPERTY, "true");

    System.setProperty("workbench.log.console", "false");
    System.setProperty("workbench.log.log4j", "false");
    System.setProperty("workbench.gui.language", "en");
    wb.readParameters(args, null);
  }

  public static void main(String[] args)
  {
    final String headlessCheckProperty = "workbench.gui.checkheadless";
    boolean runConsole = false;
    boolean checkHeadless = StringUtil.stringToBool(System.getProperty(headlessCheckProperty, "true"));

    if (checkHeadless && GraphicsEnvironment.isHeadless())
    {
      // no gui available --> default to console mode
      initConsoleMode();
      runConsole = true;
    }
    else
    {
      wb = new WbManager();
    }

    wb.readParameters(args, null);

    if (runConsole)
    {
      LogMgr.logInfo(new CallerInfo(){}, "Forcing console mode because the Java runtime claims this is a headless system. Use -D" + headlessCheckProperty + "=false to disable the check");
    }

    boolean hasScripts = wb.cmdLine.isArgPresent(AppArguments.ARG_SCRIPT) || wb.cmdLine.isArgPresent(AppArguments.ARG_COMMAND);
    boolean showHelp = wb.cmdLine.isArgPresent("help");
    boolean showVersion = wb.cmdLine.isArgPresent("version");

    if (showHelp || showVersion)
    {
      if (showHelp) System.out.println(wb.cmdLine.getHelp());
      if (showVersion) System.out.println(ResourceMgr.TXT_PRODUCT_NAME + " " + ResourceMgr.getBuildInfo());

      System.exit(0);
    }

    if (runConsole && !hasScripts)
    {
      SQLConsole.runConsole();
    }
    else
    {
      wb.installShutdownHook();
      wb.startApplication();
    }
  }

  /**
   *  This is the callback method for the shutdownhook.
   */
  @Override
  public void run()
  {
    LogMgr.logWarning(new CallerInfo(){}, "SQL Workbench/J process has been interrupted.");
    saveConfigSettings();

    boolean exitImmediately = Settings.getInstance().getBoolProperty("workbench.exitonbreak", true);
    if (exitImmediately)
    {
      LogMgr.logWarning(new CallerInfo(){}, "Aborting process...");
      LogMgr.shutdown();
      Runtime.getRuntime().halt(15); // exit() doesn't work properly from inside a shutdownhook!
    }
    else
    {
      ConnectionMgr.getInstance().disconnectAll();
      LogMgr.shutdown();
    }
  }

}
