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

import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

import workbench.resource.GuiSettings;
import workbench.resource.ResourceMgr;

import workbench.gui.WbSwingUtilities;
import workbench.gui.components.ClassFinderGUI;
import workbench.gui.components.ClasspathEditor;
import workbench.gui.components.DividerBorder;
import workbench.gui.components.ExtensionFileFilter;
import workbench.gui.components.FlatButton;
import workbench.gui.components.StringPropertyEditor;
import workbench.gui.components.TextComponentMouseListener;
import workbench.gui.components.WbButton;
import workbench.gui.components.WbFilePicker;
import workbench.gui.components.WbStatusLabel;
import workbench.gui.lnf.LnFDefinition;
import workbench.gui.lnf.LnFLoader;

import workbench.util.ClassFinder;

/**
 *
 * @author  Thomas Kellerer
 */
public class LnFDefinitionPanel
  extends JPanel
  implements ActionListener, PropertyChangeListener, DocumentListener
{
  private LnFDefinition currentLnF;
  private PropertyChangeListener changeListener;
  private boolean ignoreChange;

  public LnFDefinitionPanel()
  {
    super();
    initComponents();
    infoText.setBorder(new CompoundBorder(DividerBorder.TOP_DIVIDER, new EmptyBorder(4,0,0,0)));
    statusLabel.setText("");
    lblLibrary.setToolTipText(ResourceMgr.getDescription("LblLnFLib"));
    classpathEditor.setLastDirProperty("workbench.lnf.lastdir");
    themeFileSelector.setLastDirProperty("workbench.lnf.themes.lastdir");
    themeFileSelector.setPropertyName("themeFileName");
    themeFileSelector.setDialogTitleByKey("MsgSelectFlatLafTheme");
    themeFileSelector.setFileFilter(ExtensionFileFilter.getJsonFilterFilter());

    tfClassName.getDocument().addDocumentListener(this);
    tfName.addPropertyChangeListener(this);
    tfName.addFocusListener(new FocusAdapter()
    {
      @Override
      public void focusLost(FocusEvent evt)
      {
        nameFieldFocusLost(evt);
      }
    });

    WbSwingUtilities.makeBold(infoText);
    String button = changeLnfButton.getText();
    String info = ResourceMgr.getString("TxtChangeLnFInfo").replace("%button%", button);
    infoText.setText(info);
    infoText.setWrapStyleWord(true);
    infoText.setLineWrap(true);
    infoText.setBackground(this.getBackground());
    Font font = currentLabel.getFont();
    Font bigger = font.deriveFont((float)(font.getSize() * 1.10));
    currentLabel.setFont(bigger);
    classpathEditor.addActionListener((ActionEvent e) -> { selectClass(); });
    Dimension p1 = changeLnfButton.getPreferredSize();
    Dimension p2 = new Dimension((int)(p1.width * 1.25), (int)(p1.height * 1.25));
    changeLnfButton.setPreferredSize(p2);
  }

  public void setStatusMessage(String message)
  {
    this.statusLabel.setText(message);
  }

  public void setPropertyListener(PropertyChangeListener l)
  {
    this.changeListener = l;
  }

  public void nameFieldFocusLost(FocusEvent evt)
  {
    if (this.changeListener != null)
    {
      PropertyChangeEvent pEvt = new PropertyChangeEvent(this.currentLnF, "name", null, tfName.getText());
      this.changeListener.propertyChange(pEvt);
    }
  }

  @Override
  public void setEnabled(boolean flag)
  {
    this.tfClassName.setEnabled(flag);
    this.classpathEditor.setFileSelectionEnabled(flag);
    this.tfName.setEnabled(flag);
    this.themeFileSelector.setEnabled(flag);
    this.selectClass.setEnabled(flag);
  }

  private boolean testLnF(LnFDefinition lnf)
  {
    LnFLoader loader = new LnFLoader(lnf);
    return loader.isAvailable();
  }

  private void selectClass()
  {
    ClassFinder finder = new ClassFinder(LookAndFeel.class);
    finder.setSearchForServices(false);
    List<String> libs = classpathEditor.getLibraries();

    // this method is called when the library definition changes
    // so we need to update the current LnF definition
    currentLnF.setLibraries(libs);
    ClassFinderGUI gui = new ClassFinderGUI(finder, tfClassName, statusLabel);
    gui.setStatusBarKey("TxtSearchingLnF");
    gui.setWindowTitleKey("TxtSelectLnF");
    gui.setClassPath(libs.stream().map(fname -> new File(fname)).collect(Collectors.toList()));
    gui.startCheck();
  }

  @Override
  public void insertUpdate(DocumentEvent e)
  {
    setThemeSelectionVisible(currentLnF.getSupportsThemes());
  }

  @Override
  public void removeUpdate(DocumentEvent e)
  {
    setThemeSelectionVisible(currentLnF.getSupportsThemes());
  }

  @Override
  public void changedUpdate(DocumentEvent e)
  {
    setThemeSelectionVisible(currentLnF.getSupportsThemes());
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt)
  {
    if (ignoreChange) return;
    if (evt.getSource() == this.themeFileSelector && currentLnF != null)
    {
      currentLnF.setThemeFileName(themeFileSelector.getFilename());
    }
    else if (evt.getSource() == tfClassName && tfClassName.getName().equals(evt.getPropertyName()))
    {
      selectClass();
    }
  }

  private void setThemeSelectionVisible(boolean flag)
  {
    if (themeLabel.isVisible() == flag) return;

    this.themeLabel.setVisible(flag);
    this.themeFileSelector.setVisible(flag);
    if (flag)
    {
      themeFileSelector.addPropertyChangeListener(this);
    }
    else
    {
      themeFileSelector.removePropertyChangeListener(this);
    }
  }

  /** This method is called from within the constructor to
   * initialize the form.
   * WARNING: Do NOT modify this code. The content of this method is
   * always regenerated by the Form Editor.
   */
  // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
  private void initComponents()
  {
    GridBagConstraints gridBagConstraints;

    lblName = new JLabel();
    tfName = new StringPropertyEditor();
    lblClassName = new JLabel();
    tfClassName = new StringPropertyEditor();
    lblLibrary = new JLabel();
    infoText = new JTextArea();
    changeLnfButton = new WbButton();
    currentLabel = new HtmlLabel();
    statusLabel = new WbStatusLabel();
    selectClass = new FlatButton();
    classpathEditor = new ClasspathEditor();
    themeLabel = new JLabel();
    themeFileSelector = new WbFilePicker();

    setLayout(new GridBagLayout());

    lblName.setLabelFor(tfName);
    lblName.setText(ResourceMgr.getString("LblLnFName")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(4, 10, 1, 7);
    add(lblName, gridBagConstraints);

    tfName.setHorizontalAlignment(JTextField.LEFT);
    tfName.setName("name"); // NOI18N
    tfName.addMouseListener(new TextComponentMouseListener());
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 0;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.EAST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new Insets(4, 3, 1, 3);
    add(tfName, gridBagConstraints);

    lblClassName.setLabelFor(tfClassName);
    lblClassName.setText(ResourceMgr.getString("LblLnFClass")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(5, 10, 0, 7);
    add(lblClassName, gridBagConstraints);

    tfClassName.setColumns(10);
    tfClassName.setHorizontalAlignment(JTextField.LEFT);
    tfClassName.setName("className"); // NOI18N
    tfClassName.addMouseListener(new TextComponentMouseListener());
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.weightx = 1.0;
    gridBagConstraints.insets = new Insets(5, 3, 0, 2);
    add(tfClassName, gridBagConstraints);

    lblLibrary.setLabelFor(lblLibrary);
    lblLibrary.setText(ResourceMgr.getString("LblLnFLib")); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new Insets(3, 10, 0, 7);
    add(lblLibrary, gridBagConstraints);

    infoText.setLineWrap(true);
    infoText.setText("Please click on the \"Make current\" button to switch the current Look and Feel");
    infoText.setWrapStyleWord(true);
    infoText.setDisabledTextColor(infoText.getForeground());
    infoText.setEnabled(false);
    infoText.setFocusable(false);
    infoText.setOpaque(false);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 5;
    gridBagConstraints.gridwidth = 3;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new Insets(5, 10, 0, 10);
    add(infoText, gridBagConstraints);

    changeLnfButton.setText(ResourceMgr.getString("LblActivateLnf")); // NOI18N
    ((WbButton)changeLnfButton).setResourceKey("LblSwitchLnF");
    changeLnfButton.addActionListener(this);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 6;
    gridBagConstraints.gridwidth = 3;
    gridBagConstraints.anchor = GridBagConstraints.NORTHWEST;
    gridBagConstraints.insets = new Insets(10, 8, 0, 0);
    add(changeLnfButton, gridBagConstraints);

    currentLabel.setText("Current Theme: What you see"); // NOI18N
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 8;
    gridBagConstraints.gridwidth = 3;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.SOUTH;
    gridBagConstraints.weighty = 1.0;
    gridBagConstraints.insets = new Insets(7, 8, 5, 8);
    add(currentLabel, gridBagConstraints);

    statusLabel.setText("Statuslabel");
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 4;
    gridBagConstraints.gridwidth = 3;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(5, 10, 0, 7);
    add(statusLabel, gridBagConstraints);

    selectClass.setText("...");
    selectClass.setMaximumSize(new Dimension(22, 22));
    selectClass.setMinimumSize(new Dimension(22, 22));
    selectClass.setPreferredSize(new Dimension(22, 22));
    selectClass.addActionListener(this);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 2;
    gridBagConstraints.gridy = 2;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(3, 0, 0, 3);
    add(selectClass, gridBagConstraints);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 1;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.insets = new Insets(6, 3, 0, 3);
    add(classpathEditor, gridBagConstraints);

    themeLabel.setText("Theme");
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 0;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.anchor = GridBagConstraints.WEST;
    gridBagConstraints.insets = new Insets(5, 10, 0, 7);
    add(themeLabel, gridBagConstraints);
    gridBagConstraints = new GridBagConstraints();
    gridBagConstraints.gridx = 1;
    gridBagConstraints.gridy = 3;
    gridBagConstraints.gridwidth = 2;
    gridBagConstraints.fill = GridBagConstraints.HORIZONTAL;
    gridBagConstraints.anchor = GridBagConstraints.LINE_START;
    gridBagConstraints.insets = new Insets(5, 3, 0, 3);
    add(themeFileSelector, gridBagConstraints);
  }

  // Code for dispatching events from components to event handlers.

  public void actionPerformed(ActionEvent evt)
  {
    if (evt.getSource() == changeLnfButton)
    {
      LnFDefinitionPanel.this.changeLnfButtonActionPerformed(evt);
    }
    else if (evt.getSource() == selectClass)
    {
      LnFDefinitionPanel.this.selectClassActionPerformed(evt);
    }
  }// </editor-fold>//GEN-END:initComponents

  private void changeLnfButtonActionPerformed(java.awt.event.ActionEvent evt)//GEN-FIRST:event_changeLnfButtonActionPerformed
  {//GEN-HEADEREND:event_changeLnfButtonActionPerformed
    if (!checkThemeFile()) return;

    LnFDefinition lnf = getDefinition();
    if (testLnF(lnf))
    {
      String className = lnf.getClassName();
      this.currentLabel.setText(lnf.getName());
      GuiSettings.setLookAndFeelClass(className);
      WbSwingUtilities.showMessage(SwingUtilities.getWindowAncestor(this), ResourceMgr.getString("MsgLnFChanged"));
    }
    else
    {
      WbSwingUtilities.showErrorMessageKey(this, "MsgLnFNotLoaded");
    }
  }//GEN-LAST:event_changeLnfButtonActionPerformed

  private void selectClassActionPerformed(ActionEvent evt)//GEN-FIRST:event_selectClassActionPerformed
  {//GEN-HEADEREND:event_selectClassActionPerformed
    selectClass();
  }//GEN-LAST:event_selectClassActionPerformed

  public void setCurrentLookAndFeeld(LnFDefinition lnf)
  {
    if (lnf != null) currentLabel.setText(lnf.getName());
  }

  private boolean checkThemeFile()
  {
    if (!this.currentLnF.getSupportsThemes()) return true;
    currentLnF.setThemeFileName(themeFileSelector.getFilename());
    if (currentLnF.getThemeFile() == null)
    {
      WbSwingUtilities.showErrorMessage(this, "A theme is required");
      return false;
    }
    return true;
  }

  public void setDefinition(LnFDefinition lnf)
  {
    try
    {
      ignoreChange = true;
      this.currentLnF = lnf;
      if (this.currentLnF != null)
      {
        WbSwingUtilities.initPropertyEditors(this.currentLnF, this);
        classpathEditor.setLibraries(currentLnF.getLibraries());
        this.setEnabled(!currentLnF.isBuiltIn());
        if (lnf.isExt())
        {
          selectClass.setEnabled(true);
        }
        setThemeSelectionVisible(currentLnF.getSupportsThemes());
        themeFileSelector.setFilename(currentLnF.getThemeFileName());
      }
      else
      {
        setThemeSelectionVisible(false);
        this.classpathEditor.setLibraries(null);
        this.setEnabled(false);
        this.selectClass.setEnabled(false);
      }
    }
    finally
    {
      ignoreChange = false;
    }
  }

  public LnFDefinition getDefinition()
  {
    return this.currentLnF;
  }

  // Variables declaration - do not modify//GEN-BEGIN:variables
  public JButton changeLnfButton;
  public ClasspathEditor classpathEditor;
  public JLabel currentLabel;
  public JTextArea infoText;
  public JLabel lblClassName;
  public JLabel lblLibrary;
  public JLabel lblName;
  public JButton selectClass;
  public JLabel statusLabel;
  public JTextField tfClassName;
  public JTextField tfName;
  public WbFilePicker themeFileSelector;
  public JLabel themeLabel;
  // End of variables declaration//GEN-END:variables

  private static class HtmlLabel
    extends JLabel
  {
    @Override
    public void setText(String name)
    {
      super.setText("<html>" + ResourceMgr.getString("LblCurrLnf") + " <b>" + name + "</b></html>");
    }
  }

}
